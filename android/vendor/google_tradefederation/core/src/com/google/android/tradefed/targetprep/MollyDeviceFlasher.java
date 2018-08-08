// Copyright 2013 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.FastbootDeviceFlasher;
import com.android.tradefed.targetprep.TargetSetupError;

/**
 * A {@link FastbootDeviceFlasher} for flashing Molly devices
 */
public class MollyDeviceFlasher extends FastbootDeviceFlasher {
    private static final int MIN_BOOTLOADER = 944402;
    private static final int PROD_KEY_CODE = 1346;
    private static final int FIRST_PROD_KEY_BUILD = 946578;
    private static final String RESOURCE_PATH_ROOT =
            "/home/android-build/www/flashstation_images/molly/";

    @Override
    protected boolean checkAndFlashBootloader(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {

        // Previously, bootloader versions were alphanumeric and lexicographically increasing.
        // Now, all builds after FIRST_PROD_KEY_BUILD have purely numeric-based bootloader version
        // that are monotonically increasing. So make user flash by hand if build predates the first
        // build with productions keys, and assume all builds going forward have numeric bootloader
        // build #'s.
        try {
            int buildId = Integer.parseInt(deviceBuild.getBuildId());
            if (buildId < FIRST_PROD_KEY_BUILD) {
                throw new DeviceNotAvailableException(String.format(
                        "Current build %d predates production bootloader builds. Please "
                        + "manually update at the flashstation.", buildId),
                        device.getSerialNumber());
            }
        } catch (NumberFormatException e) {
            throw new DeviceNotAvailableException(String.format(
                    "Unable to determine build ID from '%s'", deviceBuild.getBuildId()),
                    device.getSerialNumber());
        }

        int requiredBootloaderId;
        int currentBootloaderVersion;
        try {
            requiredBootloaderId = Integer.parseInt(deviceBuild.getBootloaderVersion());
            currentBootloaderVersion = Integer.parseInt(getImageVersion(device, "bootloader"));
        } catch (NumberFormatException e) {
            throw new DeviceNotAvailableException(String.format(
                    "Unable to numerically parse required bootloader version '%s' and/or "
                     + "current bootloader version '%s'.",
                    deviceBuild.getBootloaderVersion(),
                    getImageVersion(device, "bootloader")), device.getSerialNumber());
        }

        if (requiredBootloaderId == currentBootloaderVersion) {
            CLog.i("Bootloader is already version %d, skipping flashing", currentBootloaderVersion);
            return false;
        }
        // also make user use flashstation for upgrades where bootloader builds are too old
        if (requiredBootloaderId < MIN_BOOTLOADER) {
            throw new TargetSetupError(
                    String.format(
                            "Required bootloader '%d' for build '%s' is " + "too old.",
                            requiredBootloaderId, deviceBuild.getDeviceBuildId()),
                    device.getDeviceDescriptor());
        }
        if (currentBootloaderVersion < MIN_BOOTLOADER) {
            throw new DeviceNotAvailableException(String.format(
                    "Device %s bootloader '%d' is too old. Upgrade at flashstation.",
                    device.getSerialNumber(), currentBootloaderVersion), device.getSerialNumber());
        }

        executeFastbootCmd(device, "flash", "bootloader",
                deviceBuild.getBootloaderImageFile().getAbsolutePath());
        device.rebootIntoBootloader();

        return true;
    }

    @Override
    protected void checkAndFlashBaseband(ITestDevice device, IDeviceBuildInfo deviceBuild) {
        // ignore, Molly has no baseband
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
    protected void downloadFlashingResources(ITestDevice device, IDeviceBuildInfo localBuild)
            throws TargetSetupError, DeviceNotAvailableException {
        // This is based on a recent change to molly bootloaders. We've split into production and
        // test-keys bootloaders (which are keyed off of a substring in the device's serial) so we
        // need to flash the device bootloaders differently. We adjust the path based on whether
        // the device should be flashed with the testkeys or prodkeys bootloader.
        // See https://critique.corp.google.com/#review/58121836 for reference.
        NfsFlashingResourcesRetriever retriever =
                (NfsFlashingResourcesRetriever)getFlashingResourcesRetriever();
        String deviceSerial = device.getSerialNumber();
        String keyCode = deviceSerial.substring(4, 8);  // 5th thru 8th character
        try {
            int code = Integer.parseInt(keyCode);
            if (code < PROD_KEY_CODE) {
                CLog.i("Based on the serial (%s), this is a test-key device.", deviceSerial);
                retriever.setResourcePath(RESOURCE_PATH_ROOT + "testkeys/");
            } else {
                CLog.i("Based on the serial (%s), this is a prod-key device.", deviceSerial);
                int build = Integer.parseInt(localBuild.getDeviceBuildId());
                if (build < FIRST_PROD_KEY_BUILD) {
                    throw new TargetSetupError(String.format("Your device requires build # %d " +
                            "or greater. Cannot test this build using this device.",
                            FIRST_PROD_KEY_BUILD), device.getDeviceDescriptor());
                }
                retriever.setResourcePath(RESOURCE_PATH_ROOT + "prodkeys/");
            }
        }
        catch (NumberFormatException e) {
            throw new TargetSetupError(String.format(
                    "Could not determine if device (%s) is test-keys or production bootloader",
                    deviceSerial), device.getDeviceDescriptor());
        }
        super.downloadFlashingResources(device, localBuild);
    }

}
