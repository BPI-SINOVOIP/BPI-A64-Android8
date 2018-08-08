// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.ITestDevice;

/**
 * A {@link DefaultDeviceFlasher} for flashing Avko devices
 */
public class AvkoDeviceFlasher extends DefaultDeviceFlasher {

    /**
     * {@inheritDoc}
     */
    @Override
    protected void checkAndFlashBaseband(ITestDevice device, IDeviceBuildInfo deviceBuild) {
        // ignore, Avko has no baseband
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String getMinBootloaderVersion() {
        // This min bootloader version has been valid since build id# LEB49
        return "v1.06-71-g2f77b25";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getBootPartitionName() {
        return "bootloader";
    }
}

