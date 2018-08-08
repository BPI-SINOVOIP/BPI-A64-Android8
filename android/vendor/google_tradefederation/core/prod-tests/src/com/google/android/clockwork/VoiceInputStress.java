// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.CompanionAwareTest;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.SimpleStats;
import com.android.tradefed.util.StreamUtil;

import junit.framework.TestCase;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stress test for voice command recognition on clockwork.
 */
@OptionClass(alias = "voice-input-stress")
public class VoiceInputStress extends CompanionAwareTest implements IBuildReceiver {

    private static final String DUMP_LOG_CMD = "dumpsys activity service SearchService";
    private static final int IDLE_TIME_AFTER_SET_LANGUAGE = 10 * 1000; // 10 sec
    private static final int IDLE_TIME_BEFORE_STATS = 10 * 1000; // 10 sec
    private static final int IDLE_TIME_BEFORE_TEST = 20 * 1000; // 20 sec
    private static final String METRIC_HEADER = "SearchService";
    // MetaTest at end of this class demonstrates the expected pattern input
    private static final Pattern METRIC_PATTERN = Pattern.compile(
            "\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d\\d\\d \\((\\d+)\\): (\\D+)");
    private static final String OK_TEST = "OK (1 test)";
    private static final String RUN_VOICE_TEST_CMD =
            "am instrument -r -e class \"android.test.clockwork.voice.ClockworkVoice\""
                    + " -e audios \"%s\""
                    + " %s" // prompts argument only for follow-on queries
                    + " -e verification \"%s\""
                    + " -e mode \"%s\""
                    + " -w \"android.test.clockwork.voice/android.test.InstrumentationTestRunner\"";
    private static final int SHELL_COMMAND_RETRIES = 2;
    private static final int SHELL_COMMAND_TIMEOUT = 360;
    private static final String START_SESSION_TO_FIRST_TRANSCR_METRIC =
            "start session to first transcr";
    private static final String LAST_AUDIO_TO_SEARCH_RESULT_METRIC =
            "last audio bytes sent to search result";

    @SuppressWarnings("unused")
    private IBuildInfo mBuildInfo;
    private ITestDevice mDevice;

    @Option(name = "audios", description = "audio file location(s) for test")
    private List<String> mAudio = new ArrayList<>();

    @Option(name = "iteration", description = "number of repetitions for audio test")
    private int mIteration = 1;

    @Option(name = "language", description = "abbreviation of language for test")
    private String mLanguage = "en";

    @Option(name = "latency", description = "collect latency measurements")
    private boolean mLatency = true;

    @Option(name = "prompts", description = "expected text of prompts for follow-on actions")
    private List<String> mPrompts = new ArrayList<>();

    @Option(name = "verification", description =
            "verification string to be verified for each test")
    private List<String> mVerification = new ArrayList<>();

    @Option(name = "mode", description = "voice action mode")
    private List<VoiceSearchMode> mMode = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    private void setLanguage(String language) throws DeviceNotAvailableException {
        getCompanion().executeShellCommand("setprop persist.sys.language " + language);
        getCompanion().reboot();
        RunUtil.getDefault().sleep(IDLE_TIME_AFTER_SET_LANGUAGE);
        getCompanion().executeShellCommand("am broadcast -a android.intent.action.LOCALE_CHANGED");
        RunUtil.getDefault().sleep(IDLE_TIME_AFTER_SET_LANGUAGE);
    }

    /**
     * {@inheritDoc}
     *
     * @throws DeviceNotAvailableException exception of device not available
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertEquals(
                "Size of verifications list does not match size of audio list.",
                mVerification.size(), mAudio.size());
        Assert.assertEquals(
                "Size of verifications list does not match size of mode list.",
                mVerification.size(), mMode.size());
        Assert.assertEquals(
                "Size of verifications list does not match size of prompts list.",
                mVerification.size(), mPrompts.size());
        String testKey = "VoiceInputStress";
        if (mIteration == 1) {
            testKey = "VoiceInput";
            // Use a unique test key per language
            if (!mLanguage.equals("en")) {
                testKey += "_" + mLanguage;
            }
        }
        // Set to language specified for test
        setLanguage(mLanguage);
        // Verbose logging for RPC
        getCompanion().executeShellCommand("setprop log.tag.rpcs VERBOSE");
        getCompanion().executeShellCommand("setprop log.tag.rpctransport VERBOSE");
        getCompanion().executeShellCommand("setprop log.tag.HostWithRpcCallback VERBOSE");

        Map<String, Integer> failureCount = new HashMap<>();
        for (String s : mAudio) {
            failureCount.put(s, 0);
        }
        // Fill in overall metrics with cumulative latency times and calculate averages later
        Map<String, SimpleStats> overallMetrics = new HashMap<>();
        listener.testRunStarted(testKey, mIteration * mAudio.size());
        for (int i = 0; i < mIteration; i++) {
            for (int m = 0; m < mAudio.size(); m++) {
                String tag = String.format("%s-%d", mAudio.get(m), i);
                TestIdentifier id = new TestIdentifier(getClass().getCanonicalName(),
                        String.format("iteration%s", tag));
                CLog.d("Starting iteration %s", tag);
                listener.testStarted(id);
                CollectingOutputReceiver receiver = new CollectingOutputReceiver();
                // Idle time so that watch is stable before executing test
                getDevice().clearErrorDialogs();
                RunUtil.getDefault().sleep(IDLE_TIME_BEFORE_TEST);
                String mPrompt = "";
                if (mPrompts.get(m).length() > 1) {
                    mPrompt = String.format("-e prompts \"%s\"", mPrompts.get(m));
                }
                getDevice().executeShellCommand(
                        String.format(RUN_VOICE_TEST_CMD, mAudio.get(m),
                                mPrompt, mVerification.get(m), mMode.get(m).toString()),
                        receiver, SHELL_COMMAND_TIMEOUT, TimeUnit.SECONDS, SHELL_COMMAND_RETRIES);
                String output = receiver.getOutput();
                CLog.i(output);
                Map<String, Integer> testMetrics = new HashMap<>();
                if (output.contains(OK_TEST)) {
                    RunUtil.getDefault().sleep(IDLE_TIME_BEFORE_STATS);
                    getDevice().executeShellCommand(DUMP_LOG_CMD, receiver);
                    if (mLatency) {
                        testMetrics = reportStats(receiver.getOutput());
                    }
                } else {
                    failureCount.put(mAudio.get(m), failureCount.get(mAudio.get(m)) + 1);
                    listener.testFailed(id, String.format(
                            "Clockwork iteration: %s: %s", tag, output));
                    screenshot(getDevice(), listener, tag);
                    // Clockwork bug report
                    bugreport(getDevice(), listener, tag + "_clockwork");
                    // Companion bug report
                    bugreport(getCompanion(), listener, tag + "_companion");
                }
                listener.testEnded(id, Collections.emptyMap());
                // Verify that complete metrics were collected
                if (testMetrics.size() == 2) {
                    mergeMetrics(overallMetrics, testMetrics);
                    // Log the latency metrics
                    CLog.i("Latency metrics from iteration: %s\n%s",
                            tag, Arrays.toString(testMetrics.entrySet().toArray()));
                } else {
                    CLog.w("Log was missing some latency measurements.  Will not report any "
                            + "for this iteration so that averages are not affected.");
                }
            }
        }
        Map<String, String> avgMetrics = calculateAverageMetrics(overallMetrics);
        for (Map.Entry<String, Integer> entry : failureCount.entrySet()) {
            avgMetrics.put(String.format("%s_failure", entry.getKey()),
                    Integer.toString(entry.getValue()));
        }
        // Log the average latency metrics
        CLog.i("Latency metrics from iteration: average\n%s",
                Arrays.toString(avgMetrics.entrySet().toArray()));
        // Reset to English
        setLanguage("en");
        listener.testRunEnded(0, avgMetrics);
    }

    /**
     * Helper method for collecting metrics from data dump of HomeActivity
     *
     * @param output a string dump of HomeActivity
     * @return a map to hold metrics extracted from output
     */
    private Map<String, Integer> reportStats(String output) {
        Map<String, Integer> testMetrics = new HashMap<>();

        String[] lines = output.split("\n");

        int startSession = -1;
        int firstRecvTranscription = -1;
        int lastAudioBytesSent = -1;
        int receivedSearchResult = -1;
        boolean foundMetrics = false;

        for (String line : lines) {
            line = line.trim();
            if (!foundMetrics) {
                if (line.equals(METRIC_HEADER)) {
                    foundMetrics = true;
                } else {
                    continue;
                }
            }
            Matcher matcher = METRIC_PATTERN.matcher(line);
            if (matcher.matches()) {
                String name = matcher.group(2);
                final int value = Integer.parseInt(matcher.group(1));

                switch (name) {
                    case "Home - Start Session":
                        startSession = value;
                        break;
                    case "Home - Received Transcription":
                        if (firstRecvTranscription == -1) {
                            // only want the first one
                            firstRecvTranscription = value;
                        }
                        break;
                    case "Home - Audio Bytes Sent":
                        lastAudioBytesSent = value;
                        break;
                    case "Home - Received Search Result":
                        receivedSearchResult = value;
                        break;
                }
            }
        }

        if (startSession != -1 && firstRecvTranscription != -1) {
            testMetrics.put(START_SESSION_TO_FIRST_TRANSCR_METRIC,
                    firstRecvTranscription - startSession);
        }
        if (lastAudioBytesSent != -1 && receivedSearchResult != -1) {
            testMetrics.put(LAST_AUDIO_TO_SEARCH_RESULT_METRIC,
                    receivedSearchResult - lastAudioBytesSent);
        }
        return testMetrics;
    }

    /**
     * Helper method for combining metrics from several tests to form averages later.
     *
     * @param overallMetrics metrics map for all tests
     * @param testMetrics metrics map for single test which just finished
     */
    private void mergeMetrics(
            Map<String, SimpleStats> overallMetrics,
            Map<String, Integer> testMetrics) {
        for (Entry<String, Integer> entry : testMetrics.entrySet()) {
            String key = entry.getKey();
            int value = entry.getValue();
            SimpleStats stats = overallMetrics.get(key);
            if (stats == null) {
                stats = new SimpleStats();
                overallMetrics.put(key, stats);
            }
            stats.add(value);
        }
    }

    /**
     * Helper method for calculating average metrics for reporting
     *
     * @param overallMetrics metrics map for all tests
     * @return Map of average metrics
     */
    private Map<String, String> calculateAverageMetrics(Map<String, SimpleStats> overallMetrics) {
        Map<String, String> avgMetrics = new HashMap<>();
        for (Map.Entry<String, SimpleStats> entry : overallMetrics.entrySet()) {
            String key = entry.getKey();
            SimpleStats value = entry.getValue();
            avgMetrics.put(key, Double.toString(value.mean()));
        }
        return avgMetrics;
    }

    /**
     * Helper method for taking a screen shot
     *
     * @param device instance of device
     * @param listener instance of test listener
     * @param tag a custom description to be used in the screenshot file name
     */
    private void screenshot(ITestDevice device, ITestInvocationListener listener, String tag)
            throws DeviceNotAvailableException {
        InputStreamSource data = null;
        try {
            data = device.getScreenshot();
            listener.testLog(tag + "_screenshot", LogDataType.PNG, data);
        } finally {
            StreamUtil.cancel(data);
        }
    }

    /**
     * Helper method for taking a bug report
     *
     * @param device instance of device
     * @param listener instance of test listener
     * @param tag a custom description to be used in the bug report file name
     */
    private void bugreport(ITestDevice device, ITestInvocationListener listener, String tag) {
        InputStreamSource data = null;
        try {
            data = device.getBugreport();
            listener.testLog(tag + "_bugreport", LogDataType.BUGREPORT, data);
        } finally {
            StreamUtil.cancel(data);
        }
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    private static enum VoiceSearchMode {
        VOICESEARCH("search"),
        ACTIONCONFIRMATION("action"),
        OPENAPP("open");

        private final String name;

        private VoiceSearchMode(String s) {
            this.name = s;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * A meta-test to ensure that the metric regular expression is working as intended To run the
     * meta test, run command tradefed.sh run singleCommand host -n --class
     * 'com.google.android.clockwork.VoiceInputStress$MetaTest'
     */
    public static class MetaTest extends TestCase {

        private VoiceInputStress mTestInstance = null;

        @Override
        public void setUp() throws Exception {
            mTestInstance = new VoiceInputStress();
        }

        public void testParseMetrics() throws Exception {
            String output = ArrayUtil.join("\n",
                    "SearchService",
                    "07-07 14:03:50.318 (0): Home - Start Session",
                    "07-07 14:03:50.860 (542): Home - Audio Stream Opened",
                    "07-07 14:03:50.926 (608): Home - Audio Bytes Read",
                    "07-07 14:03:51.073 (755): Home - Audio Bytes Sent",
                    "07-07 14:03:51.076 (758): Home - Audio Bytes Read",
                    "07-07 14:03:51.107 (789): Home - Audio Bytes Sent",
                    "07-07 14:03:51.110 (792): Home - Audio Bytes Read",
                    "07-07 14:03:51.124 (806): Home - Audio Bytes Sent",
                    "07-07 14:03:51.126 (808): Home - Audio Bytes Read",
                    "07-07 14:03:51.139 (821): Home - Audio Bytes Sent",
                    "07-07 14:03:51.141 (823): Home - Audio Bytes Read",
                    "07-07 14:03:51.153 (835): Home - Audio Bytes Sent",
                    "07-07 14:03:51.154 (836): Home - Audio Bytes Read",
                    "07-07 14:03:51.159 (841): Home - Audio Bytes Sent",
                    "07-07 14:03:51.160 (842): Home - Audio Bytes Read",
                    "07-07 14:03:51.172 (854): Home - Audio Bytes Sent",
                    "07-07 14:03:51.188 (870): Home - Audio Bytes Read",
                    "07-07 14:03:51.205 (887): Home - Audio Bytes Sent",
                    "07-07 14:03:51.228 (910): Companion - Received Session Start",
                    "07-07 14:03:51.252 (934): Home - Audio Bytes Read",
                    "07-07 14:03:51.257 (939): Home - Audio Bytes Sent",
                    "07-07 14:03:51.351 (1033): Home - Audio Bytes Read",
                    "07-07 14:03:51.373 (1055): Home - Audio Bytes Sent",
                    "07-07 14:03:51.475 (1157): Home - Audio Bytes Read",
                    "07-07 14:03:51.487 (1169): Home - Audio Bytes Sent",
                    "07-07 14:03:51.601 (1283): Home - Audio Bytes Read",
                    "07-07 14:03:51.607 (1289): Home - Audio Bytes Sent",
                    "07-07 14:03:51.725 (1407): Home - Audio Bytes Read",
                    "07-07 14:03:51.738 (1420): Home - Audio Bytes Sent",
                    "07-07 14:03:51.757 (1439): Companion - Audio Bytes Received",
                    "07-07 14:03:51.757 (1439): Companion - Audio Bytes Sent",
                    "07-07 14:03:51.808 (1490): Companion - Audio Bytes Received",
                    "07-07 14:03:51.808 (1490): Companion - Audio Bytes Sent",
                    "07-07 14:03:51.826 (1508): Home - Audio Bytes Read",
                    "07-07 14:03:51.839 (1521): Home - Audio Bytes Sent",
                    "07-07 14:03:51.841 (1523): Companion - Audio Bytes Received",
                    "07-07 14:03:51.841 (1523): Companion - Audio Bytes Sent",
                    "07-07 14:03:51.859 (1541): Companion - Audio Bytes Received",
                    "07-07 14:03:51.859 (1541): Companion - Audio Bytes Sent",
                    "07-07 14:03:51.895 (1577): Companion - Audio Bytes Received",
                    "07-07 14:03:51.895 (1577): Companion - Audio Bytes Sent",
                    "07-07 14:03:51.917 (1599): Companion - Audio Bytes Received",
                    "07-07 14:03:51.917 (1599): Companion - Audio Bytes Sent",
                    "07-07 14:03:51.942 (1624): Companion - Audio Bytes Received",
                    "07-07 14:03:51.942 (1624): Companion - Audio Bytes Sent",
                    "07-07 14:03:51.955 (1637): Home - Audio Bytes Read",
                    "07-07 14:03:51.961 (1643): Home - Audio Bytes Sent",
                    "07-07 14:03:51.972 (1654): Companion - Audio Bytes Received",
                    "07-07 14:03:51.973 (1655): Companion - Audio Bytes Sent",
                    "07-07 14:03:52.000 (1682): Companion - Audio Bytes Received",
                    "07-07 14:03:52.001 (1683): Companion - Audio Bytes Sent",
                    "07-07 14:03:52.046 (1728): Companion - Audio Bytes Received",
                    "07-07 14:03:52.046 (1728): Companion - Audio Bytes Sent",
                    "07-07 14:03:52.075 (1757): Home - Audio Bytes Read",
                    "07-07 14:03:52.088 (1770): Home - Audio Bytes Sent",
                    "07-07 14:03:52.124 (1806): Home - Received Transcription",
                    "07-07 14:03:52.126 (1808): Companion - Audio Bytes Received",
                    "07-07 14:03:52.126 (1808): Companion - Audio Bytes Sent",
                    "07-07 14:03:52.205 (1887): Home - Audio Bytes Read",
                    "07-07 14:03:52.212 (1894): Home - Audio Bytes Sent",
                    "07-07 14:03:52.247 (1929): Companion - Audio Bytes Received",
                    "07-07 14:03:52.247 (1929): Companion - Audio Bytes Sent",
                    "07-07 14:03:52.305 (1987): Home - Received Transcription",
                    "07-07 14:03:52.339 (2021): Home - Audio Bytes Read",
                    "07-07 14:03:52.374 (2056): Home - Audio Bytes Sent",
                    "07-07 14:03:52.385 (2067): Companion - Audio Bytes Received",
                    "07-07 14:03:52.385 (2067): Companion - Audio Bytes Sent",
                    "07-07 14:03:52.389 (2071): Home - Received Transcription",
                    "07-07 14:03:52.440 (2122): Home - Audio Bytes Read",
                    "07-07 14:03:52.473 (2155): Home - Audio Bytes Sent",
                    "07-07 14:03:52.493 (2175): Companion - Audio Bytes Received",
                    "07-07 14:03:52.493 (2175): Companion - Audio Bytes Sent",
                    "07-07 14:03:52.558 (2240): Home - Audio Bytes Read",
                    "07-07 14:03:52.572 (2254): Home - Audio Bytes Sent",
                    "07-07 14:03:52.611 (2293): Companion - Audio Bytes Received",
                    "07-07 14:03:52.611 (2293): Companion - Audio Bytes Sent",
                    "07-07 14:03:52.625 (2307): Home - Received Transcription",
                    "07-07 14:03:52.690 (2372): Home - Audio Bytes Read",
                    "07-07 14:03:52.697 (2379): Home - Audio Bytes Sent",
                    "07-07 14:03:52.707 (2389): Companion - Received Transcription",
                    "07-07 14:03:52.744 (2426): Home - Received Transcription",
                    "07-07 14:03:52.744 (2426): Companion - Audio Bytes Received",
                    "07-07 14:03:52.745 (2427): Companion - Audio Bytes Sent",
                    "07-07 14:03:52.795 (2477): Home - Received Transcription",
                    "07-07 14:03:52.823 (2505): Home - Audio Bytes Read",
                    "07-07 14:03:52.829 (2511): Home - Audio Bytes Sent",
                    "07-07 14:03:52.838 (2520): Companion - Received Transcription",
                    "07-07 14:03:52.921 (2603): Companion - Audio Bytes Received",
                    "07-07 14:03:52.923 (2605): Companion - Audio Bytes Sent",
                    "07-07 14:03:52.945 (2627): Home - Audio Bytes Read",
                    "07-07 14:03:52.958 (2640): Home - Audio Bytes Sent",
                    "07-07 14:03:52.978 (2660): Companion - Received Transcription",
                    "07-07 14:03:53.020 (2702): Companion - Audio Bytes Received",
                    "07-07 14:03:53.020 (2702): Companion - Audio Bytes Sent",
                    "07-07 14:03:53.038 (2720): Home - Audio Bytes Read",
                    "07-07 14:03:53.045 (2727): Home - Audio Bytes Sent",
                    "07-07 14:03:53.131 (2813): Companion - Audio Bytes Received",
                    "07-07 14:03:53.132 (2814): Companion - Audio Bytes Sent",
                    "07-07 14:03:53.164 (2846): Home - Audio Bytes Read",
                    "07-07 14:03:53.179 (2861): Home - Audio Bytes Sent",
                    "07-07 14:03:53.208 (2890): Companion - Received Transcription",
                    "07-07 14:03:53.257 (2939): Companion - Audio Bytes Received",
                    "07-07 14:03:53.258 (2940): Companion - Audio Bytes Sent",
                    "07-07 14:03:53.295 (2977): Home - Audio Bytes Read",
                    "07-07 14:03:53.309 (2991): Home - Audio Bytes Sent",
                    "07-07 14:03:53.315 (2997): Companion - Received Transcription",
                    "07-07 14:03:53.356 (3038): Companion - Audio Bytes Received",
                    "07-07 14:03:53.356 (3038): Companion - Audio Bytes Sent",
                    "07-07 14:03:53.366 (3048): Companion - Received Transcription",
                    "07-07 14:03:53.409 (3091): Home - Received Final Transcription",
                    "07-07 14:03:53.425 (3107): Home - Audio Bytes Read",
                    "07-07 14:03:53.432 (3114): Home - Audio Bytes Sent",
                    "07-07 14:03:53.500 (3182): Companion - Audio Bytes Received",
                    "07-07 14:03:53.500 (3182): Companion - Audio Bytes Sent",
                    "07-07 14:03:53.557 (3239): Home - Finish Sending Audio",
                    "07-07 14:03:53.680 (3362): Companion - Audio Bytes Received",
                    "07-07 14:03:53.681 (3363): Companion - Audio Bytes Sent",
                    "07-07 14:03:53.742 (3424): Companion - Audio Bytes Received",
                    "07-07 14:03:53.742 (3424): Companion - Audio Bytes Sent",
                    "07-07 14:03:53.822 (3504): Companion - Audio Bytes Received",
                    "07-07 14:03:53.823 (3505): Companion - Server Endpoint",
                    "07-07 14:03:53.944 (3626): Companion - Audio Bytes Received",
                    "07-07 14:03:53.961 (3643): Companion - Received Final Transcription",
                    "07-07 14:03:54.143 (3825): Home - Received Search Result",
                    "07-07 14:03:54.158 (3840): Home - Mic Cancel",
                    "07-07 14:03:54.172 (3854): Home - Rendering Search Results",
                    "07-07 14:03:54.239 (3921): Home - Rendered Search Result",
                    "07-07 14:03:54.710 (4392): Companion - Received Search Result");
            Map<String, Integer> metrics = mTestInstance.reportStats(output);
            Assert.assertEquals(1806,
                    (int) metrics.get(VoiceInputStress.START_SESSION_TO_FIRST_TRANSCR_METRIC));
            Assert.assertEquals(711,
                    (int) metrics.get(VoiceInputStress.LAST_AUDIO_TO_SEARCH_RESULT_METRIC));
        }
    }
}
