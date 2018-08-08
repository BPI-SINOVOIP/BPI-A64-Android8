// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.asit;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IInvocationContextReceiver;

import com.google.android.tradefed.targetprep.GsiMultiFlashPreparer;

import java.io.IOException;
import java.util.Map;

/** Device boot test for a GSI build on a physical device. */
@OptionClass(alias = "device-boot-gsi-test")
public class DeviceBootGsiTest extends DeviceBootTest implements IInvocationContextReceiver {
    private IInvocationContext mContext = null;
    /** Ignore the assigned build as we will determine it ourselves. */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        // Ignore the call.
    }

    /** Ignore the assigned device as we will determine it ourselves. */
    @Override
    public void setDevice(ITestDevice device) {
        // Ignore the call.
    }

    /** Use the context to determine the device and build to use. */
    @Override
    public void setInvocationContext(IInvocationContext context) {
        mContext = context;
    }

    @Override
    public long bringUp(ITestInvocationListener listener, Map<String, String> result)
            throws DeviceNotAvailableException {
        ITestDevice realDevice = null;
        ITestDevice nullDevice = null;

        for (ITestDevice device : mContext.getDevices()) {
            if (device.getIDevice() instanceof StubDevice) {
                nullDevice = device;
                CLog.i("Selected null-device %s", nullDevice.getSerialNumber());
            } else {
                realDevice = device;
                CLog.i("Selected device %s", realDevice.getSerialNumber());
            }
        }

        if ((realDevice == null) || (nullDevice == null)) {
            String msg =
                    String.format("No %s device found", (nullDevice == null) ? "null" : "non-null");
            listener.testRunFailed(msg);
            throw new RuntimeException(msg);
        }

        IBuildInfo deviceBuild = mContext.getBuildInfo(realDevice);
        IBuildInfo systemBuild = mContext.getBuildInfo(nullDevice);
        IBuildInfo gsiBuild;

        try {
            gsiBuild = GsiMultiFlashPreparer.createGsiBuild(deviceBuild, systemBuild);
        } catch (IOException e) {
            String msg = "Failed to get GSI build";
            listener.testRunFailed(msg);
            throw new RuntimeException(msg, e);
        }

        // Set the device and build to be used.
        super.setDevice(realDevice);
        super.setBuild(gsiBuild);

        long bringUpTime;
        try {
            // Run the test.
            bringUpTime = super.bringUp(listener, result);
        } finally {
            gsiBuild.cleanUp();
        }

        return bringUpTime;
    }
}
