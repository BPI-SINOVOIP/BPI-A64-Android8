// Copyright 2013 Google Inc. All Rights Reserved.
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
 * A {@link FastbootDeviceFlasher} for flashing Wolfie devices
 */
public class WolfieDeviceFlasher extends FastbootDeviceFlasher {
    private static final String MLO_IMAGE_NAME = "MLO";
    private static final String MIN_BOOTLOADER = "sheepsheadC1D03";

    @Override
    protected boolean checkAndFlashBootloader(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String requiredBootloader = deviceBuild.getBootloaderVersion();
        String currentBootloaderVersion = getImageVersion(device, "bootloader");
        if (requiredBootloader.equals(currentBootloaderVersion)) {
            CLog.i("Bootloader is already version %s, skipping flashing", currentBootloaderVersion);
            return false;
        }
        // make user use flashstation for the hard upgrades
        if (requiredBootloader.compareTo(MIN_BOOTLOADER) < 0) {
            throw new TargetSetupError(String.format(
                    "Required bootloader '%s' for build '%s' is too old.",
                    requiredBootloader, deviceBuild.getDeviceBuildId()),
                    device.getDeviceDescriptor());
        }
        if (currentBootloaderVersion.compareTo(MIN_BOOTLOADER) < 0) {
            throw new DeviceNotAvailableException(String.format(
                    "Device %s bootloader '%s' is too old. Upgrade at flashstation",
                    device.getSerialNumber(), currentBootloaderVersion), device.getSerialNumber());
        }

        executeFastbootCmd(device, "flash", "xloader",
                deviceBuild.getFile(MLO_IMAGE_NAME).getAbsolutePath());
        executeFastbootCmd(device, "flash", "bootloader",
                deviceBuild.getBootloaderImageFile().getAbsolutePath());
        device.rebootIntoBootloader();

        executeFastbootCmd(device, "flash", "bootloader2",
                deviceBuild.getBootloaderImageFile().getAbsolutePath());
        device.rebootIntoBootloader();
        return true;
    }

    @Override
    protected void checkAndFlashBaseband(ITestDevice device, IDeviceBuildInfo deviceBuild) {
        // ignore, Tungsten has no baseband
    }

    /**
     * Flashing and wiping user data is not supported
     */
    @Override
    public void setUserDataFlashOption(UserDataFlashOption flashOption) {
        super.setUserDataFlashOption(flashOption);
        if (getUserDataFlashOption().equals(UserDataFlashOption.FLASH)) {
            CLog.w("Overriding userdata-flash to %s: current setting of %s is not supported",
                    UserDataFlashOption.TESTS_ZIP, UserDataFlashOption.FLASH);
            super.setUserDataFlashOption(UserDataFlashOption.TESTS_ZIP);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getBootPartitionName() {
        return "u-boot";
    }

    /**
     * Downloads the MLO image and stores it in the provided {@link IDeviceBuildInfo}
     */
    @Override
    protected void downloadExtraImageFiles(IFlashingResourcesParser resourceParser,
            IFlashingResourcesRetriever retriever, IDeviceBuildInfo localBuild)
            throws TargetSetupError {
        // MLO uses the bootloader version
        String mloVersion = resourceParser.getRequiredBootloaderVersion();
        if (mloVersion != null) {
            File mloFile = retriever.retrieveFile(MLO_IMAGE_NAME, mloVersion);
            localBuild.setFile(MLO_IMAGE_NAME, mloFile, mloVersion);
        }
    }
}
