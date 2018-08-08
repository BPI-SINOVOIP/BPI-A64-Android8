// Copyright 2012 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.FastbootDeviceFlasher;
import com.android.tradefed.targetprep.IFlashingResourcesParser;
import com.android.tradefed.targetprep.TargetSetupError;

import java.util.concurrent.Semaphore;

/**
 * A {@link FastbootDeviceFlasher} for flashing Asus Nakasi devices
 */
public class Nakasi3gDeviceFlasher extends FastbootDeviceFlasher {

    /** Completely serialize flashing -- only flash one device at a time */
    private static final int N_SIMULTANEOUS_DEVICES = 1;

    private static final Semaphore FLASH_LOCK = new Semaphore(N_SIMULTANEOUS_DEVICES, true);

    /**
     * We serialize flashing for the Nakasi3G devices in this method
     * <p />
     * {@inheritDoc}
     */
    @Override
    public void flash(ITestDevice device, IDeviceBuildInfo deviceBuild) throws TargetSetupError,
            DeviceNotAvailableException {
        try {
            FLASH_LOCK.acquire();
        } catch (InterruptedException e) {
            throw new TargetSetupError(String.format(
                    "Interrupted while waiting for Nakasi3G flashing semaphore: %s",
                    e.getMessage()), device.getDeviceDescriptor());
        }

        try {
            // we're already in the critical section
            super.flash(device, deviceBuild);
        } finally {
            FLASH_LOCK.release();
        }
    }

    @Override
    protected boolean checkAndFlashBootloader(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String currentBootloaderVersion = getImageVersion(device, getBootPartitionName());
        String desiredBootloaderVersion = deviceBuild.getBootloaderVersion();
        if (desiredBootloaderVersion == null) {
            throw new TargetSetupError(String.format("Cannot find bootloader version for build %s",
                    deviceBuild.getBuildId()), device.getDeviceDescriptor());
        }
        // check special bootloader upgrade restrictions
        if ((currentBootloaderVersion.compareTo("3.27") < 0) &&
                desiredBootloaderVersion.compareTo("3.28") >= 0) {
            // don't allow upgrades from older than 3.27 to 3.28 or newer bootloaders
            throw new TargetSetupError(String.format("Current bootloader %s of device %s" +
                    "is too old. Reflash to JRN45B (355446) first.", currentBootloaderVersion,
                    device.getSerialNumber()), device.getDeviceDescriptor());
        } else if ((currentBootloaderVersion.compareTo("3.27") == 0) &&
                desiredBootloaderVersion.compareTo("3.28") > 0) {
            // don't allow upgrades from 3.27 to newer than 3.28 bootloaders
            // ie the 3.28 bootloader can only be flashed on top of the 3.27 bootloader
            throw new TargetSetupError(String.format(
                    "Device %s has bootloader %s, which cannot be upgraded directly to %s." +
                    "Reflash to JRN45B (355446) first.", device.getSerialNumber(),
                    currentBootloaderVersion, desiredBootloaderVersion),
                    device.getDeviceDescriptor());
        } else if ((currentBootloaderVersion.compareTo("3.28") >= 0) &&
            (desiredBootloaderVersion.compareTo("3.28") < 0)) {
            // don't allow downgrading of 3.28 bootloader
            throw new TargetSetupError(String.format(
                    "Device %s has bootloader version %s, and cannot be downgraded." +
                    "Use '--min-build-id 355446' in your config to prevent this.",
                    device.getSerialNumber(), desiredBootloaderVersion),
                    device.getDeviceDescriptor());
        }
        return super.checkAndFlashBootloader(device, deviceBuild);
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
        return "bootloader";
    }

   /**
     * {@inheritDoc}
     */
    @Override
    protected void verifyRequiredBoards(ITestDevice device,
            IFlashingResourcesParser resourceParser, String deviceProductType)
            throws TargetSetupError {
        // FIXME: Need to have a matching fastboot and userspace vars.
        if (!(resourceParser.getRequiredBoards().contains("tilapia") || resourceParser
                .getRequiredBoards().contains("grouper"))) {
            throw new TargetSetupError(String.format("Device %s is %s. Expected %s",
                    device.getSerialNumber(), deviceProductType,
                    resourceParser.getRequiredBoards()), device.getDeviceDescriptor());
        }
    }
}
