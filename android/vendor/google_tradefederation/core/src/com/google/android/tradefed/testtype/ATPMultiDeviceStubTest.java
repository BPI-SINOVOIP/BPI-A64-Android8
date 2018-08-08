// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.testtype;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.testtype.IMultiDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.StreamUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Stub multiple Devices for ATP.
 */
public class ATPMultiDeviceStubTest implements IRemoteTest, IMultiDeviceTest,
        IInvocationContextReceiver {

    @Option(name = "num-succeeded-tests", description = "number of succeeded stub"
            + " tests per test run.")
    private int mNumSucceededTests = 0;

    @Option(name = "num-failed-tests", description = "number of failed stub tests per test run.")
    private int mNumFailedTests = 0;

    @Option(name = "num-test-runs", description = "number of succeeded test runs.")
    private int mNumTestRuns = 1;

    private Map<ITestDevice, IBuildInfo> mDevicesInfos;
    private IInvocationContext mContext;

    @Override
    public void setDeviceInfos(Map<ITestDevice, IBuildInfo> deviceInfos) {
        mDevicesInfos = deviceInfos;
    }

    @Override
    public void setInvocationContext(IInvocationContext invocationContext) {
        mContext = invocationContext;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        for (Entry<ITestDevice, IBuildInfo> entry : mDevicesInfos.entrySet()) {
            CLog.i("Device '%s' with build id '%s'", entry.getKey().getSerialNumber(),
                    entry.getValue().getBuildId());
        }

        for (ITestDevice device : mContext.getDevices()) {
            CLog.i("Device '%s' from context with build '%s'",
                    device.getSerialNumber(), mContext.getBuildInfo(device));
        }

        for (String deviceName : mContext.getDeviceConfigNames()) {
            CLog.i(
                    "device '%s' has the name '%s' in the config.",
                    mContext.getDevice(deviceName).getSerialNumber(), deviceName);
        }

        for (int i = 0; i < mNumTestRuns; ++i) {
            runTestRun(listener, i);
        }
    }

    private void runTestRun(ITestInvocationListener listener, int testRunIndex) {
        String className = String.format("%s_succeeded_test_run%d",
                ATPMultiDeviceStubTest.class.getSimpleName(),
                testRunIndex);
        listener.testRunStarted(className, mNumSucceededTests + mNumFailedTests);
        if (mNumSucceededTests > 0 || mNumFailedTests > 0) {
            for (int i = 0; i < mNumFailedTests; ++i) {
                TestIdentifier stubTest = new TestIdentifier(className, "failed_stub_test" + i);
                listener.testStarted(stubTest);
                listener.testFailed(stubTest, StreamUtil.getStackTrace(new Throwable()));
                listener.testEnded(stubTest, new HashMap<String, String>());
            }
            for (int i = 0; i < mNumSucceededTests; ++i) {
                TestIdentifier stubTest = new TestIdentifier(className, "succeeded_stub_test" + i);
                listener.testStarted(stubTest);
                listener.testEnded(stubTest, new HashMap<String, String>());
            }
        } else {
            CLog.i("nothing to test!");
        }
        listener.testRunEnded(0, new HashMap<String, String>());
    }
}
