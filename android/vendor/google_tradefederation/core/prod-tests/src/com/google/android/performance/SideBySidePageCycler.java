// Copyright 2013 Google Inc. All Rights Reserved.
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
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.PropertyChanger;
import com.android.tradefed.util.StreamUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test harness for running WebView performance tests with SxS comparison
 *
 * Note: as is, this test class is not meant to be used with rdb reporting
 */
public class SideBySidePageCycler  implements IDeviceTest, IRemoteTest {

    private static final String RESULTS_FILE = "load_test_result.txt";
    private static final String TEST_CLASS_NAME = "com.android.pagecycler.PageCyclerTest";
    private static final String TEST_PACKAGE_NAME = " com.android.pagecycler";
    private static final String WEBVIEW_PROVIDER_PROP = "webview.force_provider";
    private static final String LOCAL_PROP = "/data/local.prop";
    private static final String MIN_METRICS_NAME = "min";

    @Option(name = "suite", description = "List of suites to run")
    private List<String> mSuites = new ArrayList<String>();

    @Option(name = "iterations", description = "How many times to run each test")
    private int mIterations = 5;

    @Option(name = "test-timeout", description = "Sets timeout for each test. "
            + "The value indicates the maximum time to wait for a test to finish in ms.")
    private int mTimeout = 500000;

    @Option(name = "ru-key", description =
            "Reporting unit key to use when posting to the release dashboard")
    private String mRuKey = "WebViewPerformance";

    @Option(name = "use-default-provider", description = "Run tests with default provider only")
    private boolean mUseDefaultProvider = false;

    private ITestDevice mDevice;
    private String[] mWebViewProviders = {"classic", "chromium"};

    @Override
    public void run(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        BugreportCollector collector = new BugreportCollector(listener, getDevice());
        collector.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
        if (mUseDefaultProvider) {
            // special case for default provider mode
            mWebViewProviders = new String[]{null};
        }
        for (String webViewProvider : mWebViewProviders) {
            if (webViewProvider != null) {
                try {
                    changeWebViewProvider(webViewProvider);
                } catch (IOException ioe) {
                    CLog.e("exception while changing device property file");
                    CLog.e(ioe);
                    listener.testRunFailed(ioe.getMessage());
                    listener.testRunEnded(0, null);
                    continue;
                }
            }
            long start = System.currentTimeMillis();
            WebViewResultsListener proxy = new WebViewResultsListener(collector);
            proxy.setWebViewProvider(webViewProvider);
            listener.testRunStarted(proxy.mangleTestRunName(null), mSuites.size());
            cleanupResults();
            for (String suite : mSuites) {
                // prefix suite name with webview provider name
                runSuite(proxy, suite);
            }
            listener.testRunEnded(System.currentTimeMillis() - start,
                    proxy.getCollectedRunMetrics());
        }
    }

    private void runSuite(WebViewResultsListener proxy, String suite)
            throws DeviceNotAvailableException {
        InstrumentationTest instrumentation = new InstrumentationTest();
        instrumentation.setDevice(getDevice());
        instrumentation.setPackageName(TEST_PACKAGE_NAME);
        instrumentation.setClassName(TEST_CLASS_NAME);
        instrumentation.setTestTimeout(mTimeout);
        instrumentation.addInstrumentationArg("suite", suite);
        proxy.setSuite(suite);
        instrumentation.run(proxy);
        cleanupResults();
    }

    private void changeWebViewProvider(String provider)
            throws DeviceNotAvailableException, IOException {
        File original = getDevice().pullFile(LOCAL_PROP);
        Map<String, String> prop = new HashMap<String, String>();
        prop.put(WEBVIEW_PROVIDER_PROP, provider);
        File updated = PropertyChanger.changeProperties(original, prop);
        getDevice().pushFile(updated, LOCAL_PROP);
        original.delete();
        updated.delete();
        getDevice().executeShellCommand("chmod 644 " + LOCAL_PROP);
        getDevice().reboot();
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

        private String mWebViewProvider;
        private String mCurrentSuite;
        private Map<String, String> mRunMetrics = new HashMap<String, String>();

        public WebViewResultsListener(ITestInvocationListener listener) {
            super(listener);
        }

        public void setSuite(String suite) {
            mCurrentSuite = suite;
        }

        public void setWebViewProvider(String webViewProvider) {
            mWebViewProvider = webViewProvider;
        }

        @Override
        protected TestIdentifier mangleTestId(TestIdentifier test) {
            return new TestIdentifier(mangleTestRunName(null), mCurrentSuite);
        }

        @Override
        protected String mangleTestRunName(String name) {
            return mWebViewProvider == null ? mRuKey : mWebViewProvider + ':' + mRuKey;
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
            if (testMetrics.containsKey(MIN_METRICS_NAME)) {
                mRunMetrics.put(mCurrentSuite, formatForRdb(testMetrics.get(MIN_METRICS_NAME)));
            }
            // lie about what happened
            super.testEnded(test, Collections.<String, String>emptyMap());
        }

        @Override
        public void testRunEnded(long elapsed, Map<String, String> metrics) {
            File resultsFile = null;
            InputStreamSource source = null;
            try {
                resultsFile = saveResults();
                if (resultsFile != null) {
                    source = new FileInputStreamSource(resultsFile);
                    String fileName = null;
                    if (mWebViewProvider == null) {
                        fileName = "result:" + mCurrentSuite;
                    } else {
                        fileName = String.format("result:%s:%s", mCurrentSuite, mWebViewProvider);
                    }
                    // prefix the dataName as a hint to PageCyclerResultReporter
                    // also suffix the dataName to indicate which WebViewProvider was used
                    testLog(fileName, LogDataType.TEXT, source);
                }
            } catch (IOException e) {
                CLog.e("Unable to obtain test results from device");
                CLog.e(e);
            } catch (DeviceNotAvailableException e) {
                CLog.e("Unable to obtain test results from device");
                CLog.e(e);
            } finally {
                StreamUtil.cancel(source);
                FileUtil.deleteFile(resultsFile);
            }
            // suppress all test run related status
        }

        private File saveResults() throws IOException,
                DeviceNotAvailableException {
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
