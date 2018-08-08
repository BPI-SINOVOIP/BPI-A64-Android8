// Copyright 2012 Google Inc. All Rights Reserved.
package com.google.android.performance;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.NameMangleListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.InstrumentationTest;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test harness for running WebView performance tests.
 */
public class WebViewTest implements IDeviceTest, IRemoteTest {

    private static final String RESULTS_FILE = "load_test_result.txt";
    private static final String TEST_METHOD = "runPageCyclerTest";
    private static final String TEST_CLASS_NAME = "com.android.dumprendertree.LoadTestsAutoTest";
    private static final String TEST_RUNNER = ".LayoutTestsAutoRunner";
    private static final String TEST_PACKAGE_NAME = "com.android.dumprendertree";
    private static final String MEAN_METRICS_NAME = "mean";

    @Option(name = "pagecycler-url",
            description = "Url that points to the location of test suites")
    private String mUrl = "file:///sdcard/webkit/page_cycler/";

    @Option(name = "suite", description = "List of suites to run")
    private List<String> mSuites = new ArrayList<String>();

    @Option(name = "iterations", description = "How many times to run each test")
    private int mIterations = 5;

    @Option(name = "test-timeout", description = "Sets timeout for each test. "
            + "The value indicates the maximum time to wait for a test to finish in ms.")
    private int mTimeout = 300000;

    @Option(name = "ru-key", description =
            "Reporting unit key to use when posting to the release dashboard")
    private String mRuKey = "WebViewPerformance";

    private ITestDevice mDevice;

    @Override
    public void run(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        BugreportCollector collector = new BugreportCollector(listener,
                getDevice());
        collector.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
        WebViewResultsListener proxy = new WebViewResultsListener(collector);
        cleanupResults();
        listener.testRunStarted(mRuKey, mSuites.size());
        long start = System.currentTimeMillis();
        for (String suite : mSuites) {
            runSuite(proxy, suite);
        }
        listener.testRunEnded(System.currentTimeMillis() - start, proxy.getCollectedRunMetrics());
    }

    private void runSuite(WebViewResultsListener proxy, String suite)
            throws DeviceNotAvailableException {
        InstrumentationTest instrumentation = new InstrumentationTest();
        instrumentation.setDevice(mDevice);
        instrumentation.setPackageName(TEST_PACKAGE_NAME);
        instrumentation.setRunnerName(TEST_RUNNER);
        instrumentation.setClassName(TEST_CLASS_NAME);
        instrumentation.setMethodName(TEST_METHOD);
        instrumentation.setTestTimeout(mTimeout);
        instrumentation.addInstrumentationArg("path", String.format(
                "\"%s%s/start.html?auto=1\\&iterations=%d\"", mUrl, suite,
                mIterations));
        proxy.setSuite(suite);
        instrumentation.run(proxy);
        cleanupResults();
    }

    private void cleanupResults() throws DeviceNotAvailableException {
        getDevice().executeShellCommand(String.format("rm %s/%s",
                getDevice().getMountPoint(IDevice.MNT_EXTERNAL_STORAGE), RESULTS_FILE));
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    private class WebViewResultsListener extends NameMangleListener {

        private String mCurrentSuite;
        private Map<String, String> mRunMetrics = new HashMap<String, String>();

        public WebViewResultsListener(ITestInvocationListener listener) {
            super(listener);
        }

        public void setSuite(String suite) {
            mCurrentSuite = suite;
        }

        @Override
        protected TestIdentifier mangleTestId(TestIdentifier test) {
            return new TestIdentifier(mRuKey, mCurrentSuite);
        }

        @Override
        protected String mangleTestRunName(String name) {
            return mRuKey;
        }

        @Override
        public void testRunStarted(String runName, int testCount) {
            // suppress all test run related status
        }

        @Override
        public void testRunStopped(long elapsedTime) {
            CLog.v("test run stopped, not sure why we get this, elapsedTime=%d", elapsedTime);
            // suppress all test run related status
        }

        @Override
        public void testRunFailed(String errorMessage) {
            CLog.e("Suite failure: suite: %s, error message: %s", mCurrentSuite, errorMessage);
            // suppress all test run related status
        }

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            // stash test metrics
            if (testMetrics.containsKey(MEAN_METRICS_NAME)) {
                mRunMetrics.put(mCurrentSuite, formatForRdb(testMetrics.get(MEAN_METRICS_NAME)));
            }
            // lie about what happened
            super.testEnded(test, Collections.<String, String>emptyMap());
        }

        @Override
        public void testRunEnded(long elapsed, Map<String, String> metrics) {
            try {
                File resultsFile = saveResults();
                if (resultsFile == null) {
                    CLog.w("no result file found for suite: " + mCurrentSuite);
                    return;
                }
                try (InputStreamSource source = new FileInputStreamSource(resultsFile)) {
                    // prefix the dataName as a hint to PageCyclerResultReporter
                    testLog("result:" + mCurrentSuite, LogDataType.TEXT, source);
                }
                resultsFile.delete();
            } catch (DeviceNotAvailableException e) {
                CLog.e("Unable to obtain test results from device");
                CLog.e(e);
            }
            // suppress all test run related status
        }

        private File saveResults() throws DeviceNotAvailableException {
            File resultsFile = getDevice().pullFileFromExternal(RESULTS_FILE);
            if (resultsFile != null) {
                CLog.i("Saving results to %s",
                        resultsFile.getAbsolutePath());
            }
            return resultsFile;
        }

        private String formatForRdb(String metric) {
            float f = Float.parseFloat(metric);
            return Float.toString(f/1000);
        }

        public Map<String, String> getCollectedRunMetrics() {
            return mRunMetrics;
        }
    }
}
