// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.FastbootDeviceFlasher;
import com.android.tradefed.targetprep.TargetSetupError;

public class SproutDeviceFlasher extends FastbootDeviceFlasher {
    private static final String MIN_BOOTLOADER = "0.5.08";

    @Override
    protected boolean checkAndFlashBootloader(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String requiredBootloader = deviceBuild.getBootloaderVersion();
        String currentBootloaderVersion = getImageVersion(device, "bootloader");
        if (currentBootloaderVersion == null) {
            throw new TargetSetupError(String.format("Cannot find bootloader version for build %s",
                    deviceBuild.getBuildId()), device.getDeviceDescriptor());
        }
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

       return super.checkAndFlashBootloader(device, deviceBuild);
    }

    @Override
    protected void checkAndFlashBaseband(ITestDevice device, IDeviceBuildInfo deviceBuild) {
        // baseband is inside system partition
        // nothing to do here
    }

    /**
     * Flashing of user data is not supported.
     */
    @Override
    public void setUserDataFlashOption(UserDataFlashOption flashOption) {
        if (UserDataFlashOption.FLASH.equals(flashOption)) {
            CLog.w("Overriding userdata-flash to %s: current setting of %s is not supported",
                    UserDataFlashOption.TESTS_ZIP, UserDataFlashOption.FLASH);
            super.setUserDataFlashOption(UserDataFlashOption.TESTS_ZIP);
        } else {
            super.setUserDataFlashOption(flashOption);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getBootPartitionName() {
        return "bootloader";
    }
}
