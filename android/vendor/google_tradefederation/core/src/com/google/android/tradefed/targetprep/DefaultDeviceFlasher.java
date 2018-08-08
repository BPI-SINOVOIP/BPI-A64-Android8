// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.FastbootDeviceFlasher;
import com.android.tradefed.targetprep.TargetSetupError;

import java.util.Comparator;

/**
 * A {@link FastbootDeviceFlasher} for flashing a typical Nexus device
 */
abstract class DefaultDeviceFlasher extends FastbootDeviceFlasher {

    /**
     * Implementation should return a minimally supported bootloader version
     */
    abstract String getMinBootloaderVersion();

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
        if (UserDataFlashOption.FLASH.equals(flashOption)) {
            CLog.w("Overriding userdata-flash to %s: current setting of %s is not supported",
                    UserDataFlashOption.TESTS_ZIP, UserDataFlashOption.FLASH);
            super.setUserDataFlashOption(UserDataFlashOption.TESTS_ZIP);
        } else {
            super.setUserDataFlashOption(flashOption);
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

        String minBootloaderVersion = getMinBootloaderVersion();
        if (compareBootloaderVersion(newVersion, minBootloaderVersion) < 0) {
            throw new TargetSetupError(String.format("The bootloader version to be flashed, %s, " +
                    "is lower than our minimum supported version %s.  Try the flashstation, but " +
                    "this downgrade likely isn't supported at all.", newVersion,
                    minBootloaderVersion), device.getDeviceDescriptor());
        }
        if (compareBootloaderVersion(curVersion, minBootloaderVersion) < 0) {
            throw new DeviceNotAvailableException(String.format("The bootloader version " +
                    "currently on the device, %s, is lower than our minimum supported version " +
                    "%s.  Take the device to a flashstation.", curVersion, minBootloaderVersion),
                    device.getSerialNumber());
        }

        return super.checkAndFlashBootloader(device, deviceBuild);
    }

    /**
     * Compare two bootloader version strings, use the same convention as
     * {@link Comparator#compare(Object, Object)}.
     * <p>
     * Subclass may override for customized interpertation of bootloader version string
     * @param x version string to be compared
     * @param y version string to be compared
     * @return follow the same convention as {@link Comparator#compare(Object, Object)}
     */
    protected int compareBootloaderVersion(String x, String y) {
        return x.compareTo(y);
    }
}
