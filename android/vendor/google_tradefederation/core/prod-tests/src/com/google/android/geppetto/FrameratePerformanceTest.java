// Copyright 2012 Google Inc. All Rights Reserved.
package com.google.android.geppetto;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestResult;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.UiAutomatorRunner;
import com.android.tradefed.testtype.UiAutomatorTest;

import org.junit.Assert;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Run UI Gesture tests and measures the Framerate Performance Tests.
 *
 */
public class FrameratePerformanceTest extends UiAutomatorTest {
    @Option(name = "test-case",
            description = "Map of the benchmark to the test class name. Can be repeated.",
            mandatory = true)
    private Map<String, String> mTestCases = new HashMap<String, String>();

    @Option(name = "app-fps-test-label", description = "Test label for App FPS test.")
    private String mAppFpsLabel = "AppFramerateBenchmark";

    @Option(name = "fps-test-label", description = "Test label for FPS test.")
    private String mFpsLabel = "UserActionFramerateBenchmark";

    private static final String AVERAGE_FPS = "AVG_FPS";
    private static final String AVERAGE_APP_FPS = "AVG_APP_FPS";
    private static final String FRAME_RATE_TEST =
            "com.android.test.uiautomator.platform.framerate.FrameRateTests";
    private static final String GAME_TEST =
            "com.android.test.uiautomator.platform.framerate.GameTests";

    private Map<String, String> mAppFpsResults = new HashMap<String, String>();
    private Map<String, String> mFpsResults = new HashMap<String, String>();

    private IRemoteAndroidTestRunner mUiRunner = null;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(getDevice());
        Iterator<Entry<String, String>> it = mTestCases.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<String, String> pairs = it.next();
            // The testLabel is also the test method inside the test class.
            String testLabel = pairs.getKey();
            String testClass = pairs.getValue();
            runTest(listener, testLabel, testClass);
            it.remove();
        }
        reportMetrics(listener, mAppFpsLabel, mAppFpsResults);
        reportMetrics(listener, mFpsLabel, mFpsResults);
    }

    @Override
    public IRemoteAndroidTestRunner getTestRunner() {
        return mUiRunner;
    }

    void setTestRunner(IRemoteAndroidTestRunner runner) {
        mUiRunner = runner;
    }

    /**
     * Run an individual benchmark and collect the results from the instrumentation output.
     * @param listener {@link ITestInvocationListener}
     * @param benchmarkLabel {@link String} label of the benchmark being run.
     * @param testClass {@link String} the test class name for the benchmark.
     * @throws DeviceNotAvailableException
     */
    void runTest(ITestInvocationListener listener, String benchmarkLabel, String testClass)
            throws DeviceNotAvailableException {
        setTestRunner(new UiAutomatorRunner(getDevice().getIDevice(),
                getTestJarPaths().toArray(new String[]{}), null));
        getTestRunner().setMethodName(testClass, benchmarkLabel);
        preTestSetup();
        getRunUtil().sleep(getSyncTime());
        for (Map.Entry<String, String> entry : getTestRunArgMap().entrySet()) {
            getTestRunner().addInstrumentationArg(entry.getKey(), entry.getValue());
        }
        CollectingTestListener collectingListener = new CollectingTestListener();
        getDevice().runInstrumentationTests(getTestRunner(), listener, collectingListener);

        Collection<TestResult> testResults =
                collectingListener.getCurrentRunResults().getTestResults().values();
        Map<String, String> metrics = new HashMap<String, String>();
        if (testResults != null && testResults.iterator().hasNext()) {
            Map<String, String> testMetrics = testResults.iterator().next().getMetrics();
            if (testMetrics != null) {
                metrics.putAll(testMetrics);
            }
        }
        String avgAppFps = metrics.get(AVERAGE_APP_FPS);
        String avgFps = metrics.get(AVERAGE_FPS);
        CLog.d("Got %s for appfps: %s fps: %s", benchmarkLabel, avgAppFps, avgFps);
        // Only report results for FrameRateTests for AppFrameRateBenchmark
        if (testClass.equals(FRAME_RATE_TEST) && avgAppFps != null) {
            mAppFpsResults.put(benchmarkLabel, avgAppFps);
        }
        // TODO: remove this when all the UIGesture tests are ported over.
        // only report the game results for the UserActionFramerateBenchmark
        if (testClass.equals(GAME_TEST) && avgFps != null) {
            mFpsResults.put(benchmarkLabel, avgFps);
        }
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     */
    void reportMetrics(ITestInvocationListener listener, String testLabel,
            Map<String, String> metrics) {
        CLog.d("About to report metrics: %s", metrics);
        listener.testRunStarted(testLabel, 0);
        listener.testRunEnded(0, metrics);
    }
}
