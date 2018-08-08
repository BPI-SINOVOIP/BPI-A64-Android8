// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.clusterfuzz;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;

/**
 * ClusterFuzz test harness that runs fuzz tests on devices.
 * Fuzzing is a form of stress testing that applies random mutations to
 * existing tests to make the system crash.
 *
 */
public class ClusterFuzzTest implements IRemoteTest, IDeviceTest {
    private ITestDevice mDevice;

    // Maximum time a startup script is supposed to run. Default is 7 days.
    private static final Long COMMAND_TIMEOUT = 7L * 24L * 60L * 60L * 1000L;

    @Option(name = "startup-script", description =
            "path to script that starts a fuzz bot for this device.")
    private String mStartupScript = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
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
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mStartupScript == null) {
            throw new NullPointerException("Option startup-script not set.");
        }

        String deviceSerial = getDevice().getSerialNumber();
        RunUtil.getDefault().runTimedCmdSilently(
            COMMAND_TIMEOUT, "python", mStartupScript, "--device", deviceSerial);
    }
}
