// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.asit;

import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.DeviceRecoveryModeUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Boot tests that tests a device can boot into recovery mode
 */
public class DeviceBootRecoveryTest implements IRemoteTest, IDeviceTest {

    @Option(name = "max-recovery-time", description = "max time to wait for recovery to start",
            isTimeVal = true)
    private long mRecoveryMs = 5 * 60 * 1000;

    @Option(name = "recovery-boot-buffer", description = "buffer time to allow recovery to boot",
            isTimeVal = true)
    private long mRecoveryBuffer = 3000;

    private static final String TAG = "DeviceBootRecoveryTest";
    private ITestDevice mDevice;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        TestIdentifier test = new TestIdentifier(DeviceBootRecoveryTest.class.getName(), TAG);
        Map<String, String> metrics = new HashMap<String, String>();
        long end = -1;
        try {
            listener.testRunStarted(TAG, 1);
            listener.testStarted(test);
            mDevice.rebootIntoRecovery();
            long start = System.currentTimeMillis();
            boolean success = false;
            success = mDevice.waitForDeviceInRecovery(mRecoveryMs);
            end = System.currentTimeMillis() - start;
            metrics.put("recovery_time", Double.toString(end / 1000.0));
            if (!success) {
                DeviceState currState = mDevice.getIDevice().getState();
                String failMsg = String.format(
                        "Did not boot into recovery after %d ms; state is %s",
                        mRecoveryMs, currState);
                listener.testFailed(test, failMsg);
                listener.testEnded(test, metrics);
                listener.testRunFailed(failMsg);
            } else {
                listener.testEnded(test, metrics);
            }
        } finally {
            listener.testRunEnded(end, metrics);
            DeviceRecoveryModeUtil.bootOutOfRecovery((IManagedTestDevice) mDevice,
                    mRecoveryMs, mRecoveryBuffer);
        }
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }
}
