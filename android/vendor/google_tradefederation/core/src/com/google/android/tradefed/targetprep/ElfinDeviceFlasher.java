// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.TargetSetupError;

/**
 * A {@link DefaultDeviceFlasher} for flashing Elfin devices
 */
public class ElfinDeviceFlasher extends DefaultDeviceFlasher {

    /**
     * {@inheritDoc}
     */
    @Override
    protected void checkAndFlashBaseband(ITestDevice device, IDeviceBuildInfo deviceBuild) {
        // ignore, Elfin has no baseband
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String getMinBootloaderVersion() {
        // This min bootloader version has been valid since OC.
        return "v1.32-207-gafae18a";
    }

    /**
     * Flash builds on Elfin.
     * This requires some customized steps such as extra reboot and erasing misc partition
     * to make sure that it reliably boots up with 'a' boot partition.
     */
    @Override
    public void flash(ITestDevice device, IDeviceBuildInfo deviceBuild) throws TargetSetupError,
            DeviceNotAvailableException {

        CLog.i("Flashing device %s with build %s", device.getSerialNumber(),
                deviceBuild.getDeviceBuildId());

        // get system build id and build flavor before booting into fastboot
        String systemBuildId = device.getBuildId();
        String systemBuildFlavor = device.getBuildFlavor();

        device.rebootIntoBootloader();

        downloadFlashingResources(device, deviceBuild);
        preFlashSetup(device, deviceBuild);
        handleUserDataFlashing(device, deviceBuild);
        checkAndFlashBootloader(device, deviceBuild);

        // Elfin specific flashing steps start.
        // TODO(b/65594928): Remove extra flashing steps once the issue is resolved.
        CLog.i("Fastboot reboot to apply gpt changes.");
        device.rebootIntoBootloader();
        CLog.i("Erasing misc partition");
        wipePartition(device, "misc");
        // End.

        checkAndFlashBaseband(device, deviceBuild);
        flashExtraImages(device, deviceBuild);
        checkAndFlashSystem(device, systemBuildId, systemBuildFlavor, deviceBuild);
    }
}
