// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.driveabout;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Run driveabout to stress the graphics framework.
 * Monitor maps crash, capture bugreport and report total running time
 */
public class DriveaboutTest implements IDeviceTest, IRemoteTest {
    /* if test device has sdcard parition, the directory to hold event.log file */
    private final static String REMOTE_DIR = "Android/data/com.google.android.apps.maps/debug";
    /* if test device doesn't have sdcard parition, the event.log file location, */
    private final static String LOCAL_PATH =
            "/data/data/com.google.android.apps.maps/files/event.log";
    /* predefined event.log file */
    private final static String EVENT_LOG =
            "/google/data/ro/teams/tradefed/testdata/driveabout/burnin-fullspeed.xml";
    /* driveabout with vector log */
    private final static String MAP_ACTIVITY =
            "com.google.android.maps.driveabout.REPLAY_VECTOR_LOG";
    private final static String NAVIGATION_ACTIVITY =
            "com.google.android.maps.driveabout.REPLAY_LOG";
    /* returned act when driveabout is started successfully */
    private final static String DRIVEABOUT_START_STRING =
            "act=com.google.android.maps.driveabout.REPLAY_LOG";
    /* result file to hold results from multiple devices */
    private final static String RESULT_PATH = "/tmp/driveabout.txt";
    /* maximum attempts for adb shell commands */
    private final static int MAX_ATTEMPS = 3;
    /* maximum time to wait for the test activity to launch */
    private final static int MAX_LAUNCH_TIME = 5 * 60 * 1000;
    /* time to wait for maps activity to be launched */
    private final static int ACTIVITY_START_TIME = 60 * 1000;
    /* maximum time to wait for ps command to return */
    private final static int MAX_PS_TIME = 20 * 1000;
    /* report unit key */
    private final static String RU_KEY = "driveabout_stress";
    /* item key for short test */
    private final static String SHORT_ITEM_KEY = "short";
    /* item key for long test */
    private final static String LONG_ITEM_KEY = "long";
    private final static String DRIVEABOUT_PROCESS = "com.google.android.apps.maps";

    private String mEventLogDest;
    private String mEventLogDestTemp;
    private String mAction = null;
    private ITestDevice mTestDevice = null;

    @Option (name = "time", description = "how long will the test run (in hours)")
    private int mTestTime = 4;

    @Option (name = "short_test", description = "flag to set short test run")
    private boolean mShortTestFlag = true;

    @Option (name="vector", description = "if the vector flag is set, the event log should be " +
            "played with the MapActivity. Otherwise, the NavigationActivity should be used.")
    private boolean mVectorFlag = false;

    @Option (name = "feature_test", description = "if feature test flag is set, the event log " +
            "should be played back as feature test (with UI events and assertion validation.")
    private boolean mFeatureTestFlag = false;

    @Option (name = "nosdcard", description = "if nosdcard flag is set, the event log is saved " +
            "locally, otherwise, it is saved in remote dir.")
    private boolean mNoSdcardFlag = false;

    @Option (name = "extras", description = "add any extras passed by the caller")
    private String mExtras = null;

    @Option (name = "remote_dir", description = "set remote directory for storing event log")
    private String mRemoteDir = REMOTE_DIR;

    @Option (name = "result_file", description = "file name to record test results")
    private String mResultPath = RESULT_PATH;

    @Option (name = "monitor_timer",
            description = "timer to check driveabout still alive (in seconds)")
    private int mMonitorTimer = 10 * 60;

    @Option (name = "bugreport", description = "flag to capture bugreport every monitor timer")
    private boolean mBugreportFlag = false;

    @Override
    public void setDevice(ITestDevice testDevice) {
        mTestDevice = testDevice;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    @Override
    public void run(ITestInvocationListener standardListener)
            throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);

        // Set device properties before running the test
        if (mNoSdcardFlag) {
            mEventLogDest = LOCAL_PATH;
            mEventLogDestTemp = LOCAL_PATH;
        } else {
            mEventLogDest = String.format("%s/%s/event.log",
                    mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE), mRemoteDir);
            mEventLogDestTemp =  String.format("/sdcard/%s/event.log",mRemoteDir);
        }
        CLog.d(mEventLogDest);
        if (mVectorFlag) {
            mAction = MAP_ACTIVITY;
        } else {
            mAction = NAVIGATION_ACTIVITY;
        }
        String cmd = String.format("am start -a %s -e event_log \"%s\"",
                mAction, mEventLogDestTemp);
        if (mFeatureTestFlag) {
            cmd += " --ez is_feature_test true ";
        }
        if (mExtras != null) {
            cmd += mExtras;
        }

        if (mShortTestFlag) {
            cmd += " --ei random_ui_seed 0";
        }
        CLog.v("cmd is: %s", cmd);

        mTestDevice.pushFile(new File(EVENT_LOG), mEventLogDest);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();

        // capture bugreport before starting the test
        captureBugreport("bugreport_before_test", standardListener);

        int running_time;
        mTestDevice.executeShellCommand(cmd, receiver,
                MAX_LAUNCH_TIME, TimeUnit.MILLISECONDS, MAX_ATTEMPS);
        String output = receiver.getOutput();
        CLog.v(String.format("Command \"%s\" returned \"%s\"", cmd, output));
        if (output.indexOf(DRIVEABOUT_START_STRING) > 0) {
            // wait for the activity to be fully launched
            getRunUtil().sleep(ACTIVITY_START_TIME);
            running_time = testMonitor(standardListener);
        } else {
            running_time = 0;
        }
        // capture bugreport after running the test
        captureBugreport("bugreport_after_test", standardListener);
        // save and report results
        reportTestResults(standardListener, running_time);
    }

    /**
     * Capture a bugreport for a given name
     * @param bugreportName
     * @param listener
     */
    private void captureBugreport(String bugreportName, ITestInvocationListener listener) {
        // capture another bugreport after running the test
        InputStreamSource bugreport = getDevice().getBugreport();
        listener.testLog(bugreportName, LogDataType.BUGREPORT, bugreport);
        StreamUtil.cancel(bugreport);
    }

    /**
     * Monitor driveabout test for crash, ANR, and other failures
     * @param listener
     * @return total running time
     * @throws DeviceNotAvailableException
     */
    private int testMonitor(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        int expected_time = mTestTime * 60 * 60 * 1000; // convert to millisecond
        long startTime = System.currentTimeMillis();
        // get the process id when the test is started
        String pid = getTestActivityProcessId();
        if (pid == null) {
            CLog.v("Didn't find maps process id, device offline?");
            return 0;
        }
        CLog.v("maps pid:<%s>", pid);
        while (System.currentTimeMillis() - startTime < expected_time) {
            getRunUtil().sleep(mMonitorTimer * 1000);
            String curPid = getTestActivityProcessId();
            if (curPid == null) {
                // either device is offline, driveabout crashed or device rebooted
                CLog.v("no process id! adb offline, driveabout crashed, or device rebooted?");
                if (mTestDevice.getDeviceState() != TestDeviceState.ONLINE) {
                    CLog.v("device is not online");
                }
                break;
            } else if (curPid.compareTo(pid) != 0) {
                CLog.v("current pid for maps: <%s>", curPid);
                CLog.v("Driveabout crashed and restarted?");
                break;
            }
            if (mBugreportFlag) {
                captureBugreport(String.format("bugreport_%s", System.currentTimeMillis()),
                        listener);
            }
        }
        int running_hours = (int) (System.currentTimeMillis() - startTime) / 3600000;
        return running_hours;
    }

    private String getTestActivityProcessId() throws DeviceNotAvailableException {
        String cmd = " ps";
        String pid = null;
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        mTestDevice.executeShellCommand(cmd, receiver,
                MAX_PS_TIME, TimeUnit.MILLISECONDS, MAX_ATTEMPS);
        String output = receiver.getOutput();
        final String lineSeparator = System.getProperty ("line.separator");
        String[] outputs = output.split(lineSeparator);
        for (String line: outputs) {
            if (line.trim().endsWith(DRIVEABOUT_PROCESS)){
                CLog.v("%s", line);
                pid = line.trim().split("\\s+")[1];
                break;
            }
        }
        if (pid == null) {
            CLog.v("Didn't find the process id");
        }
        return pid;
    }

    /**
     * Save results to data file and report test results
     * @param listener
     * @throws DeviceNotAvailableException
     */
    private void reportTestResults(ITestInvocationListener listener, int running_time)
            throws DeviceNotAvailableException {
        // save the running time
        String testType;
        if (mShortTestFlag) {
            testType = SHORT_ITEM_KEY;
        } else {
            testType = LONG_ITEM_KEY;
        }
        File testFile = new File(String.format("%s_%s_%s_%s", mResultPath,
                mTestDevice.getProductType(), mTestDevice.getBuildId(), testType));
        BufferedWriter bw = null;
        try {
            // append to the end of the file if file exists
            bw = new BufferedWriter(new FileWriter(testFile, true));
            bw.newLine();
            String message = String.format("%s %d", mTestDevice.getSerialNumber(), running_time);
            CLog.v("message: %s", message);
            bw.write(message);
            bw.close();
        } catch (IOException e) {
            CLog.v("write to %s exception: %s", testFile, e.getMessage());
        }

        // read results
        int total_time = 0;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(testFile));
            String line = null;
            while ((line = br.readLine()) != null) {
                CLog.v("line: %s", line);
                if (line.trim().length() > 0) {
                    int hour = Integer.parseInt(line.trim().split("\\s+")[1]);
                    total_time += hour;
                }
            }
        } catch (IOException e) {
            CLog.v("read from %s exception: %s", testFile, e.getMessage());
        } finally {
            StreamUtil.close(br);
        }

        // grab a snapshot of the results
        InputStreamSource outputSource = null;
        try {
            // Save a copy of the output file
            CLog.d("Sending %d byte file %s into the logosphere!",
                    testFile.length(), testFile);
            outputSource = new FileInputStreamSource(testFile);
            listener.testLog(testFile.getName(), LogDataType.TEXT,
                    outputSource);
        } finally {
            StreamUtil.cancel(outputSource);
        }

        // report results
        reportMetrics(listener, total_time);
    }
    /**
     * Report run metrics by creating an empty test run to stick them in
     */
    private void reportMetrics(ITestInvocationListener listener, int value) {
        Map<String, String> metrics = new HashMap<>(1);
        if (mShortTestFlag) {
            metrics.put(SHORT_ITEM_KEY, Integer.toString(value));
        } else {
            metrics.put(LONG_ITEM_KEY, Integer.toString(value));
        }
        // Create an empty testRun to report the parsed runMetrics
        CLog.d("About to report metrics to %s: %s", RU_KEY, metrics);
        listener.testRunStarted(RU_KEY, 0);
        listener.testRunEnded(0, metrics);
    }

    /**
     * Get the {@link RunUtil} instance to use.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
