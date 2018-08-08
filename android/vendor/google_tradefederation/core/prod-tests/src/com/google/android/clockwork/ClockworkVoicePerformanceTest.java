// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.clockwork;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.LogcatReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.SimpleStats;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@OptionClass(alias = "cw-voice-performance")
public class ClockworkVoicePerformanceTest implements IDeviceTest, IRemoteTest {

    // Logcat command to set which log tag we are interested in.
    // Set ActivityManager and EventLoggerStores up, suppress all the other output.
    private static final String LOGCAT_CMD =
            "logcat -v threadtime ActivityManager:* EventLoggerStores:* *:s";

    // The open voice command to run on device.
    private static final String OPEN_VOICE_APP_CMD =
            "am start -a android.intent.action.VOICE_ASSIST";

    // Force stop voice app to make it as coldstart.
    private static final String STOP_VOICE_APP_CMD =
            "am force-stop com.google.android.googlequicksearchbox";

    // Compiled regex matcher to match ActivityManager launch entry
    private static final Pattern LAUNCH_ENTRY =
            Pattern.compile(
                    "^\\d*-\\d*\\s*(\\d*:\\d*:\\d*.\\d*)\\s*\\d*\\s*\\d*\\s*I ActivityManager: "
                            + "Displayed\\s*com.google.android.googlequicksearchbox.*?\\+(.*?)"
                            + "ms.*?\\s*$");

    // Compiled regex matcher to match EventLogger start microphone event
    private static final Pattern START_MIC_ENTRY =
            Pattern.compile(
                    "^\\d*-\\d*\\s*(\\d*:\\d*:\\d*.\\d*)\\s*\\d*\\s*\\d*\\s*I EventLoggerStores: "
                            + "recordEvent APP_EVENT_START_RECORDING_USER_SPEECH .*$");
    private static final int LOGCAT_SIZE = 20971520; //20 mb
    private ITestDevice mDevice;
    private LogcatReceiver mLogcat;

    @Option(
        name = "iterations",
        description =
                "Number of tests to run in sequence to get an averaged result. "
                        + "Default to be 5.",
        importance = Importance.ALWAYS
    )
    private int mIterations = 5;

    @Option(name = "test-key", description = "Test key string for test run")
    private String mTestKey = "ClockworkVoiceAppPerformance";

    @Option(name = "iteration-sleep-time-ms", description = "The timeout between each iterations.")
    private int mIterationTimeout = 5 * 1000;

    @Override
    public void setDevice(ITestDevice iTestDevice) {
        mDevice = iTestDevice;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mLogcat = new LogcatReceiver(getDevice(), LOGCAT_CMD, LOGCAT_SIZE, 0);
        mLogcat.start();

        try {
            listener.testRunStarted(mTestKey, mIterations);
            for (int i = 0; i < mIterations; i++) {
                TestIdentifier id =
                        new TestIdentifier(
                                getClass().getCanonicalName(), String.format("iteration-%d", i));
                listener.testStarted(id);
                mDevice.executeShellCommand(STOP_VOICE_APP_CMD);
                mDevice.executeShellCommand(OPEN_VOICE_APP_CMD);
                RunUtil.getDefault().sleep(mIterationTimeout);
                listener.testEnded(id, Collections.emptyMap());
            }
            Map<String, String> ret = analyzeLogCatData();
            listener.testRunEnded(0, ret);
        } finally {
            mLogcat.stop();
        }
    }

    public Map<String, String> analyzeLogCatData() {
        Map<String, String> ret = new HashMap<>();
        List<Double> launchTimeList = new ArrayList<>();
        List<Double> startMicTimeList = new ArrayList<>();
        String line;
        Matcher match;
        double launchTimestamp = 0;
        try (InputStreamSource input = mLogcat.getLogcatData();
                BufferedReader br =
                        new BufferedReader(new InputStreamReader(input.createInputStream()))) {
            while ((line = br.readLine()) != null) {
                match = LAUNCH_ENTRY.matcher(line);
                if (match.matches()) {
                    launchTimestamp = getTimeFromDateString(match.group(1));
                    Double launchTime = parseDurationString(match.group(2));
                    launchTimeList.add(launchTime);
                    continue;
                }
                match = START_MIC_ENTRY.matcher(line);
                if (match.matches()) {
                    double startMicTimestamp = getTimeFromDateString(match.group(1));
                    if (launchTimestamp != 0 && startMicTimestamp != 0) {
                        startMicTimeList.add(startMicTimestamp - launchTimestamp);
                        launchTimestamp = 0;
                    } else {
                        CLog.e("Failed to parse timestamp. Metric discarded.");
                    }
                }
            }
        } catch (IOException io) {
            CLog.e(io);
        }
        while (launchTimeList.size() > mIterations) {
            launchTimeList.remove(0);
        }
        while (startMicTimeList.size() > mIterations) {
            startMicTimeList.remove(0);
        }
        CLog.d("Activity start results collected: %s", launchTimeList.toString());
        CLog.d("Microphone start results collected: %s", startMicTimeList.toString());
        SimpleStats activityStats = new SimpleStats();
        activityStats.addAll(launchTimeList);
        SimpleStats microphoneStats = new SimpleStats();
        microphoneStats.addAll(startMicTimeList);
        ret.put(
                "activityStart",
                activityStats.mean() == null ? "0" : activityStats.mean().toString());
        ret.put(
                "microphoneStart",
                microphoneStats.mean() == null ? "0" : microphoneStats.mean().toString());
        return ret;
    }

    private double getTimeFromDateString(String dateString) {
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss.SSS");
            Date date = formatter.parse(dateString);
            return date.getTime();
        } catch (ParseException e) {
            CLog.e("Parser exception thrown for dateString: %s", dateString);
            return 0;
        }
    }

    private double parseDurationString(String durationString) {
        // To see if the time string contains s, e.g. 1s360
        int idx = durationString.indexOf("s");
        if (idx == -1) {
            // If no seconds, then directly parse to double
            return Double.parseDouble(durationString);
        } else {
            // If there is second string, parse seconds first, then milliseconds.
            String secondString = durationString.substring(0, idx);
            String millisecondString = durationString.substring(idx + 1);
            return 1000 * Double.parseDouble(secondString) + Double.parseDouble(millisecondString);
        }
    }
}
