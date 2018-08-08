// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.FastbootDeviceFlasher;
import com.android.tradefed.targetprep.TargetSetupError;

import java.io.File;

/**
 * A {@link FastbootDeviceFlasher} for flashing Samsung Prime GSM devices
 */
public class PrimeGsmDeviceFlasher extends FastbootDeviceFlasher {
    // This is here in case we need to hard-code another SBL flash at some point.
    //static final String SBL_IMAGE_NAME = "sbl";

    /** The minimum bootloader version that we support flashing to/from */
    static final String MIN_BOOTLOADER_VERSION = "PRIMEKF05";

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
     * <p />
     * {@inheritDoc}
     */
    @Override
    protected boolean checkAndFlashBootloader(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String curVersion = getImageVersion(device, "bootloader");
        String newVersion = deviceBuild.getBootloaderVersion();

        if (newVersion.equals(curVersion)) {
            CLog.i("Device already has bootloader version %s; skipping flash.", curVersion);
            return false;  // false -> skipped bootloader flash
        }

        if (!newVersion.matches("^PRIME[K-Z][A-Z]\\d\\d$")) {
            throw new TargetSetupError(String.format("TF flasher only supports devices with " +
                    "bootloader version PRIME[K-Z][A-Z][0-9][0-9].  Current version, %s, should " +
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

    // This is here in case we need to hard-code another SBL flash at some point.
    /*void flashSblAndReboot(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String newVersion = deviceBuild.getBootloaderVersion();
        File sblImage = deviceBuild.getImageFile(SBL_IMAGE_NAME);
        if (sblImage == null) {
            throw new TargetSetupError(String.format("sbl image was required and is missing " +
                    "for bootloader upgrade to version %s", newVersion));
        }
        flashPartition(device, sblImage, "sbl");
        device.rebootIntoBootloader();
    }*/

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
        device.rebootIntoBootloader();
    }

    // SBL flashing is currently disabled
    /* (not javadoc) *
     * Downloads the sbl image and stores it in the provided {@link IDeviceBuildInfo}
     */
    /*@Override
    protected void downloadExtraImageFiles(IFlashingResourcesParser resourceParser,
            IFlashingResourcesRetriever retriever, IDeviceBuildInfo localBuild)
            throws TargetSetupError {
        // sbl is tied to the bootloader, so the versions should also match
        // Attempt to download the sbl image and ignore failures
        // FIXME: do something smarter once we have a better way to do so
        String blVersion = resourceParser.getRequiredBootloaderVersion();
        try {
            File sblFile = retriever.retrieveFile(SBL_IMAGE_NAME, blVersion);
            localBuild.setImageFile(SBL_IMAGE_NAME, sblFile, blVersion);
        } catch (TargetSetupError e) {
            // ignore missing sbl image; will throw later if needed
            CLog.i("Caught missing sbl file; continuing anyway: %s", e);
        }
    }*/
}
