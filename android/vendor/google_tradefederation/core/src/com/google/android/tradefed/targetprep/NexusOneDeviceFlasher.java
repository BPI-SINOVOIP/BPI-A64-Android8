// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.FastbootDeviceFlasher;
import com.android.tradefed.targetprep.IFlashingResourcesParser;
import com.android.tradefed.targetprep.IFlashingResourcesRetriever;
import com.android.tradefed.targetprep.TargetSetupError;

import java.io.File;

/**
 * A {@link FastbootDeviceFlasher} for flashing passion/nexusone/mahimahi devices
 */
public class NexusOneDeviceFlasher extends FastbootDeviceFlasher {
    static final String MICROP_VERSION_KEY = "version-microp";
    static final String MICROP_IMAGE_NAME = "microp";

    @Override
    protected boolean checkAndFlashBootloader(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        // only want to reboot bootloader once after either hboot or microp or both were flashed
        if (super.checkAndFlashBootloader(device, deviceBuild) ||
                checkAndFlashMicrop(device, deviceBuild)) {
            device.rebootIntoBootloader();
            return true;
        }
        return false;
    }

    /**
     * Flashes the given bootloader image. Relies on
     * {@link #checkAndFlashBootloader(ITestDevice, IDeviceBuildInfo)} to do reboot if necessary.
     *
     * @param device the {@link ITestDevice} to flash
     * @param bootloaderImageFile the bootloader image {@link File}
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash
     */
    @Override
    protected void flashBootloader(ITestDevice device, File bootloaderImageFile)
            throws DeviceNotAvailableException, TargetSetupError {
        // bootloader images are small, and flash quickly. so use the 'normal' timeout
        executeFastbootCmd(device, "flash", getBootPartitionName(),
                bootloaderImageFile.getAbsolutePath());
    }

    /**
     * Flashes the microp image if necessary
     * @param device
     * @param deviceBuild
     * @return <code>true</true> if microp was flashed, <code>false</code> otherwise.
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    protected boolean checkAndFlashMicrop(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String currentMicropVersion = getImageVersion(device, MICROP_IMAGE_NAME);
        String buildMicropVersion = deviceBuild.getVersion(MICROP_IMAGE_NAME);
        if (!buildMicropVersion.equals(currentMicropVersion)) {
            CLog.i("Flashing %s %s", MICROP_IMAGE_NAME, buildMicropVersion);
            executeFastbootCmd(device, "flash", MICROP_IMAGE_NAME,
                    deviceBuild.getFile(MICROP_IMAGE_NAME).getAbsolutePath());
            return true;
        } else {
            CLog.d("%s is already version %s, skipping flashing", MICROP_IMAGE_NAME,
                    currentMicropVersion);
            return false;
        }
    }

    /**
     * Downloads the microp image and stores it in the provided {@link IDeviceBuildInfo}
     */
    @Override
    protected void downloadExtraImageFiles(IFlashingResourcesParser resourceParser,
            IFlashingResourcesRetriever retriever, IDeviceBuildInfo localBuild)
            throws TargetSetupError {
        String micropVersion = resourceParser.getRequiredImageVersion(MICROP_VERSION_KEY);
        if (micropVersion != null) {
            File micropFile = retriever.retrieveFile(MICROP_IMAGE_NAME, micropVersion);
            localBuild.setFile(MICROP_IMAGE_NAME, micropFile, micropVersion);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void overrideDeviceOptions(ITestDevice device) {
        if (!device.getUseFastbootErase()) {
            CLog.w("Overriding use-fastboot-erase to true. Fastboot format is not supported on " +
                    "Nexus One");
            device.setUseFastbootErase(true);
        }
    }
}
