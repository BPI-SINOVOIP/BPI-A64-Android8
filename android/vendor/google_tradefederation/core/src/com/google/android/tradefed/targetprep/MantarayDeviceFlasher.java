// Copyright 2012 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.FastbootDeviceFlasher;
import com.android.tradefed.targetprep.TargetSetupError;

/**
 * A {@link FastbootDeviceFlasher} for flashing Samsung Mantaray devices
 */
public class MantarayDeviceFlasher extends FastbootDeviceFlasher {
    /** The minimum bootloader version that we support flashing to/from */
    static final String MIN_BOOTLOADER_VERSION = "MANTALE01";

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
    public void setUserDataFlashOption(UserDataFlashOption flashOption) {
        super.setUserDataFlashOption(flashOption);
        if (getUserDataFlashOption().equals(UserDataFlashOption.FLASH)) {
            CLog.w("Overriding userdata-flash to %s: current setting of %s is not supported",
                    UserDataFlashOption.TESTS_ZIP, UserDataFlashOption.FLASH);
            super.setUserDataFlashOption(UserDataFlashOption.TESTS_ZIP);
        }
    }

    /**
     * This specialization of {@link #checkAndFlashBootloader} checks that we're only flashing
     * devices that aren't too old to be supported.
     * <p>
     * {@inheritDoc}
     * </p>
     */
    @Override
    protected boolean checkAndFlashBootloader(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String curVersion = getImageVersion(device, getBootPartitionName());
        String newVersion = deviceBuild.getBootloaderVersion();
        if (newVersion == null) {
            throw new TargetSetupError(String.format("Cannot find bootloader version for build %s",
                    deviceBuild.getBuildId()), device.getDeviceDescriptor());
        }

        if (newVersion.equals(curVersion)) {
            CLog.i("Device already has bootloader version %s; skipping flash.", curVersion);
            return false;  // false -> skipped bootloader flash
        }

        if (!newVersion.matches("^MANTA[L-Z][A-Z]\\d\\d$")) {
            throw new TargetSetupError(String.format("TF flasher only supports devices with " +
                    "bootloader version MANTA[L-Z][A-Z][0-9][0-9].  Current version, %s, should " +
                    "be flashed from the flashstation.", newVersion), device.getDeviceDescriptor());
        }
        if (less(newVersion, MIN_BOOTLOADER_VERSION)) {
            throw new TargetSetupError(String.format("The bootloader version to be flashed, %s, " +
                    "is lower than our minimum supported version %s.  Try the flashstation, but " +
                    "this downgrade likely isn't supported at all.", newVersion,
                    MIN_BOOTLOADER_VERSION), device.getDeviceDescriptor());
        }
        if (less(curVersion, MIN_BOOTLOADER_VERSION)) {
            throw new DeviceNotAvailableException(String.format("The bootloader version " +
                    "currently on the device, %s, is lower than our minimum supported version " +
                    "%s.  Take the device to a flashstation.", curVersion, MIN_BOOTLOADER_VERSION),
                    device.getSerialNumber());
        }

        return super.checkAndFlashBootloader(device, deviceBuild);
    }

    private boolean less(String x, String y) {
        return x.compareTo(y) < 0;
    }

    @Override
    protected void checkAndFlashBaseband(ITestDevice device, IDeviceBuildInfo deviceBuild) {
        // ignore, Mantaray has no baseband
    }
}
