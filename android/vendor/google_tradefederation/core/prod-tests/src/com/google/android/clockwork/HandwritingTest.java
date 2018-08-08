// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.clockwork;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.SimpleStats;

import junit.framework.TestCase;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A test to measure performance on handwriting input method on wear devices.
 * Performance metrics will be collected and aggregated.
 */
public class HandwritingTest implements IRemoteTest, IDeviceTest {
    private static final String OK_TEST = "OK (1 test)";
    private static final String RUN_HANDWRITING_CMD =
            "am instrument -r -e class \"android.test.clockwork.ime.Handwriting\"" +
            " -e stroke \"%s\"" +
            " -w \"android.test.clockwork.ime/android.test.InstrumentationTestRunner\"";
    private static final String RUN_FILENAME_CMD = "ls %s";
    private static final String TEST_FAILED_MSG =
            " Handwriting performance test failed at iteration %s." +
            " Stroke file: %s. Tradefed output: %s.";

    private ITestDevice mDevice;
    private HandWritingPerfMetrics mHandwritingPerfMetrics;

    @Option(name = "folder", description = "folder of users handwriting strokes",
            importance = Importance.ALWAYS, mandatory = true)
    private String mFolder;

    @Option(name = "test-key", description = "Test key to use when posting to the dashboard")
    private String mTestKey = "CwHandwritingTest";

    @Option(name = "usercase-iteration",
            description = "Number of iterations to run each use cases.")
    private int mUsercaseIteration = 10;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        List<String> strokes = getStrokesFileNames();
        CLog.d("Get file list: %s", strokes.toString());
        mHandwritingPerfMetrics = new HandWritingPerfMetrics(strokes);
        listener.testRunStarted(mTestKey, strokes.size() * mUsercaseIteration);
        for (int i = 0; i < mUsercaseIteration; i ++) {
            for (int j = 0; j < strokes.size(); j ++) {
                String strokeFile = strokes.get(j);
                String tag = String.format("%s.%s", strokeFile, i);
                TestIdentifier id = new TestIdentifier(getClass().getCanonicalName(), tag);
                CLog.d(tag);
                listener.testStarted(id);
                CollectingOutputReceiver receiver = new CollectingOutputReceiver();
                getDevice().clearErrorDialogs();
                getDevice().executeShellCommand(
                        String.format(RUN_HANDWRITING_CMD, strokeFile), receiver);
                String output = receiver.getOutput();
                if (output.contains(OK_TEST)) {
                    collectMetrics(strokeFile, output);
                } else {
                    listener.testFailed(id, String.format(TEST_FAILED_MSG, i, strokeFile, output));
                }
                listener.testEnded(id, Collections.<String, String>emptyMap());
            }
        }

        listener.testRunEnded(0, mHandwritingPerfMetrics.getMetricsReport());
    }

    /**
     * Parse and collect metrics values from instrumentation output
     *
     * @param strokeFile The name of user stroke
     * @param output Instrumentation test output
     *
     */
    private void collectMetrics(String strokeFile, String output) {
        for (HandWritingMetricsTag tag : HandWritingMetricsTag.values()) {
            Double value = tag.parseFromString(output);
            mHandwritingPerfMetrics.addMetrics(strokeFile, tag, value);
        }
    }

    /**
     * Get all files in provided folder
     *
     * @return {@link String} with absolute path for each use case
     * @throws DeviceNotAvailableException if connection with device is lost and cannot
     * be recovered
     */
    private List<String> getStrokesFileNames()
            throws DeviceNotAvailableException {
        List<String> ret = new ArrayList<String>();
        String[] lines = getDevice().getChildren(mFolder);
        for (String s : lines) {
            ret.add(String.format("%s/%s", mFolder, s).trim());
        }
        return ret;
    }

    /**
     * Enum to hold all available handwriting performance metrics
     * <p/>
     * If to add more metrics, the corresponding string should be the same as
     * defined in test apk, so that it can be parsed from instrumentation output.
     */
    private enum HandWritingMetricsTag {
        E2ELATENCY("EndToEndLatency"),
        AVGJANK("AverageJank");

        private final String text;
        private final String pattern;

        private HandWritingMetricsTag(final String text) {
            this.text = text;
            this.pattern = this.text+"=(\\d+\\.\\d+)";
        }

        @Override
        public String toString() {
            return text;
        }

        public Double parseFromString(String output) {
            Matcher m = Pattern.compile(this.pattern).matcher(output);
            Double res = Double.valueOf(0.0);
            if (m.find()) {
                String[] parts = m.group().split("=");
                res = Double.parseDouble(parts[1].trim());
            }
            return res;
        }
    }

    /**
     * A wrapper of {@link SimpleStats} to hold all handwriting performance metrics
     * <p/>
     * Data model: Map<"StrokeFileName", Map<"MetricsTag", RealMetrics> >
     */
    private static class HandWritingPerfMetrics {

        private Map<String, Map<String, SimpleStats>> mOverallMetrics;

        public HandWritingPerfMetrics(List<String> strokes) {
            mOverallMetrics = new HashMap<>();
            for (String fileName : strokes) {
                Map<String, SimpleStats> strokeMetrics = new HashMap<>();
                for (HandWritingMetricsTag tag : HandWritingMetricsTag.values()) {
                    strokeMetrics.put(tag.toString(), new SimpleStats());
                }
                mOverallMetrics.put(fileName, strokeMetrics);
            }
        }

        /**
         * Add metrics to underlying data structure
         * <p/>
         * @param strokeFile name of use case
         * @param tag HandWritingMetricsTag to indicate which metrics is adding
         * @param d the metric value (double)
         */
        public void addMetrics(String strokeFile, HandWritingMetricsTag tag, double d) {
            mOverallMetrics.get(strokeFile).get(tag.toString()).add(d);
        }

        /**
         * Prepare metrics for listener
         * <p/>
         * If a null value is found, "0.0" is inserted into the map
         * @return Map<"filename"_"metricsTag", "metrics">, which can directly feed the listener
         */
        public Map<String, String> getMetricsReport() {
            Map<String, String> res = new HashMap<>();
            for (Map.Entry<String, Map<String, SimpleStats>> entry : mOverallMetrics.entrySet()) {
                for (Map.Entry<String, SimpleStats> e : entry.getValue().entrySet()) {
                    Double mean = e.getValue().mean();
                    res.put(String.format("%s_%s", entry.getKey(), e.getKey()),
                            (mean == null) ? "0.0" : mean.toString());
                }
            }
            CLog.d(res.toString());
            return res;
        }
    }

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

    /**
     * A meta-test to ensure that the metric parsing and collection is working as intended
     * To run the meta test, run command
     * tradefed.sh run singleCommand host -n --class
     * 'com.google.android.clockwork.HandwritingTest$HandwritingTestMetaTest'
     */
    public static class HandwritingTestMetaTest extends TestCase {

        private HandWritingMetricsTag mE2E = null;
        private HandWritingMetricsTag mJank = null;
        private HandWritingPerfMetrics mHandwritingPerfMetrics = null;

        @Override
        public void setUp() throws Exception {
            mE2E = HandWritingMetricsTag.E2ELATENCY;
            mJank = HandWritingMetricsTag.AVGJANK;
            List<String> sampleFileNames = new ArrayList<String>();
            for (int i = 1; i <= 3; i ++) {
                sampleFileNames.add(String.format("File_%d", i));
            }
            mHandwritingPerfMetrics = new HandWritingPerfMetrics(sampleFileNames);
        }
        /**
         * A sample instrumentation output of successful test
         */
        private static final String TAG_EXISTING_INSTRUMENTATION_OUTPUT =
            "INSTRUMENTATION_STATUS: numtests=1\n" +
            "INSTRUMENTATION_STATUS: stream=\n" +
            "android.test.clockwork.ime.Handwriting:\n" +
            "INSTRUMENTATION_STATUS: id=InstrumentationTestRunner\n" +
            "INSTRUMENTATION_STATUS: test=testHandwriting\n" +
            "INSTRUMENTATION_STATUS: class=android.test.clockwork.ime.Handwriting\n" +
            "INSTRUMENTATION_STATUS: current=1\n" +
            "INSTRUMENTATION_STATUS_CODE: 1\n" +
            "INSTRUMENTATION_STATUS: AverageJank=13.76\n" +
            "INSTRUMENTATION_STATUS: EndToEndLatency=1640.0\n" +
            "INSTRUMENTATION_STATUS_CODE: -1\n" +
            "INSTRUMENTATION_STATUS: numtests=1\n" +
            "INSTRUMENTATION_STATUS: stream=.\n" +
            "INSTRUMENTATION_STATUS: id=InstrumentationTestRunner\n" +
            "INSTRUMENTATION_STATUS: test=testHandwriting\n" +
            "INSTRUMENTATION_STATUS: class=android.test.clockwork.ime.Handwriting\n" +
            "INSTRUMENTATION_STATUS: current=1\n" +
            "INSTRUMENTATION_STATUS_CODE: 0\n" +
            "INSTRUMENTATION_RESULT: stream=\n" +
            "Test results for InstrumentationTestRunner=.\n" +
            "Time: 43.289\n";

        /**
         * A sample instrumentation output of failed test
         * <p/>
         * Although the string will hardly be passed into parse logic because not
         * having "OK (1 test)", it is worth testing when no pattern found, in this case
         * 0.0 will be returned.
         */
        private static final String TAG_NONEXISTING_INSTRUMENTATION_OUTPUT =
            "INSTRUMENTATION_STATUS: numtests=1\n" +
            "INSTRUMENTATION_STATUS: stream=\n" +
            "android.test.clockwork.ime.Handwriting:\n" +
            "INSTRUMENTATION_STATUS: id=InstrumentationTestRunner\n" +
            "INSTRUMENTATION_STATUS: test=testHandwriting\n" +
            "INSTRUMENTATION_STATUS: class=android.test.clockwork.ime.Handwriting\n" +
            "INSTRUMENTATION_STATUS: current=1\n" +
            "INSTRUMENTATION_STATUS_CODE: 1\n" +
            "INSTRUMENTATION_STATUS: numtests=1\n" +
            "INSTRUMENTATION_STATUS: stream=\n" +
            "Failure in testHandwriting:\n" +
            "junit.framework.AssertionFailedError: Parse result failed\n" +
            "    at android.test.clockwork.ime.IMETestHelper.parseMetricsFromFile(IMETestHelper.java:180)\n" +
            "    at android.test.clockwork.ime.Handwriting.testHandwriting(Handwriting.java:50)\n" +
            "    at android.test.InstrumentationTestCase.runMethod(InstrumentationTestCase.java:220)\n" +
            "    at android.test.InstrumentationTestCase.runTest(InstrumentationTestCase.java:205)\n" +
            "    at android.test.AndroidTestRunner.runTest(AndroidTestRunner.java:198)\n" +
            "    at android.test.AndroidTestRunner.runTest(AndroidTestRunner.java:183)\n" +
            "    at android.test.InstrumentationTestRunner.onStart(InstrumentationTestRunner.java:560)\n" +
            "    at android.app.Instrumentation$InstrumentationThread.run(Instrumentation.java:1932)\n" +
            "INSTRUMENTATION_STATUS: id=InstrumentationTestRunner\n" +
            "INSTRUMENTATION_STATUS: test=testHandwriting\n" +
            "INSTRUMENTATION_STATUS: class=android.test.clockwork.ime.Handwriting\n" +
            "INSTRUMENTATION_STATUS: stack=junit.framework.AssertionFailedError: Parse result failed\n" +
            "    at android.test.clockwork.ime.IMETestHelper.parseMetricsFromFile(IMETestHelper.java:180)\n" +
            "    at android.test.clockwork.ime.Handwriting.testHandwriting(Handwriting.java:50)\n" +
            "    at android.test.InstrumentationTestCase.runMethod(InstrumentationTestCase.java:220)\n" +
            "    at android.test.InstrumentationTestCase.runTest(InstrumentationTestCase.java:205)\n" +
            "    at android.test.AndroidTestRunner.runTest(AndroidTestRunner.java:198)\n" +
            "    at android.test.AndroidTestRunner.runTest(AndroidTestRunner.java:183)\n" +
            "    at android.test.InstrumentationTestRunner.onStart(InstrumentationTestRunner.java:560)\n" +
            "    at android.app.Instrumentation$InstrumentationThread.run(Instrumentation.java:1932)\n" +
            "INSTRUMENTATION_STATUS: current=1\n" +
            "INSTRUMENTATION_STATUS_CODE: -2\n" +
            "INSTRUMENTATION_RESULT: stream=\n" +
            "Test results for InstrumentationTestRunner=.F\n" +
            "Time: 43.321\n" +
            "FAILURES!!!\n" +
            "Tests run: 1,  Failures: 1,  Errors: 0\n";

        public void testParseExistingMetricsTag() {
            Double endToendLatency = mE2E.parseFromString(TAG_EXISTING_INSTRUMENTATION_OUTPUT);
            assertTrue("Failed to parse metrics from output",
                    endToendLatency.doubleValue() == 1640.0);
            Double avgJank = mJank.parseFromString(TAG_EXISTING_INSTRUMENTATION_OUTPUT);
            assertTrue("Failed to parse metrics from output", avgJank.doubleValue() == 13.76);
        }

        public void testParseNonExistingMetricsTag() {
            Double endToendLatency = mE2E.parseFromString(TAG_NONEXISTING_INSTRUMENTATION_OUTPUT);
            Assert.assertTrue("Failed to parse metrics from output",
                    endToendLatency.doubleValue() == 0.0);
            Double avgJank = mJank.parseFromString(TAG_NONEXISTING_INSTRUMENTATION_OUTPUT);
            Assert.assertTrue("Failed to parse metrics from output", avgJank.doubleValue() == 0.0);
        }

        public void testHandWritingPerfMetrics() {
            mHandwritingPerfMetrics.addMetrics("File_1", mE2E, 2.0);
            mHandwritingPerfMetrics.addMetrics("File_1", mE2E, 4.0);
            mHandwritingPerfMetrics.addMetrics("File_2", mJank, 3.1415);
            Map<String, String> metricsReport = mHandwritingPerfMetrics.getMetricsReport();
            // File_1 E2E should be 3.0
            Assert.assertEquals("3.0", metricsReport.get("File_1_EndToEndLatency"));
            // File_2 Jank should be 3.1415
            Assert.assertEquals("3.1415", metricsReport.get("File_2_AverageJank"));
            // File_2 E2E should be 0.0
            Assert.assertEquals("0.0", metricsReport.get("File_2_EndToEndLatency"));
            // File_3 Jank should be 0.0
            Assert.assertEquals("0.0", metricsReport.get("File_3_EndToEndLatency"));
        }
    }
}
