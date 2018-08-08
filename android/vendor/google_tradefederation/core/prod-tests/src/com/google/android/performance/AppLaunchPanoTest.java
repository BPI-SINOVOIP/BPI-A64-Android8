// Copyright 2013 Google Inc. All Rights Reserved.
package com.google.android.performance;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A test to measure the start up time of apps. The test will launch all the apps and reboot
 * the device several times. Report the average start up time for each app.
 */
public class AppLaunchPanoTest implements IRemoteTest, IDeviceTest {
    private static final String PACKAGE_NAME = "com.android.tests.applaunchpano";

    private ITestDevice mDevice;

    @Option(name = "apps", shortName = 'a', description = "List of apps to start on the device." +
            " They key is the application to launch on the device. The value is how it will " +
            "be reported in the results.",
            importance = Importance.ALWAYS, mandatory = true)
    private Map<String, String> mApps = new LinkedHashMap<String, String>();

    @Option(name = "ru-key", description = "Result key to use when posting to the dashboard")
    private String mRuKey = "ApplicationStartupTimeAAH";

    @Option(name = "idle-time", description = "Time to sleep before starting app launches. "
            + "Default is 30s")
    private long mIdleTime = 30 * 1000;

    @Option(name = "launch-iteration", description = "Iterations for launching each app.")
    private int mLaunchIteration = 10;

    @Option(name = "prefer-pano", description = "Default is true; set to false if we should "
            + "NOT prefer Pano apps.")
    private boolean mPreferPano = true;

    @Option(name = "required-account", description = "Accounts that must exist as a prequisite.")
    private List<String> mReqAccounts = new ArrayList<String>();

    private int mTestTimeout = 5 * 60 * mLaunchIteration * 1000; //5 minutes timeout per iteration

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        RunUtil.getDefault().sleep(mIdleTime);
        ProxyListener proxy = new ProxyListener(listener);
        doTestRun(proxy);
        proxy.postResults();
    }

    private void doTestRun(ITestInvocationListener listener) throws DeviceNotAvailableException {
        InstrumentationTest instr = new InstrumentationTest();
        instr.setDevice(getDevice());

        instr.setPackageName(PACKAGE_NAME);

        List<String> appsKey = new ArrayList<String>();
        for (Map.Entry<String, String> entry : mApps.entrySet()) {
            appsKey.add(entry.getKey() + "^" + entry.getValue());
        }

        instr.addInstrumentationArg("apps", String.format("\"%s\"", ArrayUtil.join("|", appsKey)));
        instr.addInstrumentationArg("launch_iterations", Integer.toString(mLaunchIteration));
        instr.addInstrumentationArg("required_accounts", ArrayUtil.join(",", mReqAccounts));
        instr.addInstrumentationArg("prefer_pano", Boolean.toString(mPreferPano));
        instr.setShellTimeout(mTestTimeout);
        instr.run(listener);
    }

    /**
     * A listener to collect the metrics from all the iterations, average them and
     * post the final result.
     */
    private class ProxyListener implements ITestInvocationListener {
        private ITestInvocationListener mListener;
        private Map<String, String> mFinalMetrics = new HashMap<String, String>();

        public ProxyListener(ITestInvocationListener listener) {
            mListener = listener;
        }

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            for (Map.Entry<String, String> metric : testMetrics.entrySet()) {
                try {
                    String app = metric.getKey();
                    Double.parseDouble(metric.getValue());
                    mFinalMetrics.put(app, metric.getValue());
                } catch (NumberFormatException e) {
                    CLog.w("App: %s did not launch successfully", metric.getKey());
                }
            }
        }

        /**
         * Forward the final results to other listeners.
         */
        public void postResults() {
            mListener.testRunStarted(mRuKey, 1);
            TestIdentifier testId = new TestIdentifier(mRuKey, "AppLaunchPanoTest");
            mListener.testStarted(testId);
            mListener.testEnded(testId, Collections.<String, String> emptyMap());
            mListener.testRunEnded(0, mFinalMetrics);
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
}
