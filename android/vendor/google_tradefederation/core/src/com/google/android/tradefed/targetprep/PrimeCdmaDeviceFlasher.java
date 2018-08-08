// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.CdmaDeviceFlasher;
import com.android.tradefed.targetprep.FlashingResourcesParser;
import com.android.tradefed.targetprep.FlashingResourcesParser.Constraint;
import com.android.tradefed.targetprep.IFlashingResourcesParser;
import com.android.tradefed.targetprep.IFlashingResourcesRetriever;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.IRunUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link CdmaDeviceFlasher} for flashing Prime CDMA
 */
public class PrimeCdmaDeviceFlasher extends CdmaDeviceFlasher {
    static final String CDMA_IMAGE_NAME = "radio-cdma";
    static final String CDMA_VERSION_KEY = "version-cdma";
    static final String CDMA_SHORT_KEY = "cdma";

    /** The minimum bootloader version that we support flashing to/from */
    static final String MIN_BOOTLOADER_VERSION = "PRIMEKF05";

    /**
     * Temporary hack to reject Sprint baseband/bootloader version
     */
    static class SupportedImageConstraint implements Constraint {
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean shouldAccept(String version) {
            if (version.contains("L700")) {
                CLog.d("Rejecting image version %s; not supported", version);
                return false;
            }
            return true;
        }
    }

    /**
     * Create and return a {@link IFlashingResourcesParser} that only contains image entries
     * that are compatible with this particular device.
     * <p />
     * {@inheritDoc}
     */
    @Override
    protected IFlashingResourcesParser createFlashingResourcesParser(IDeviceBuildInfo localBuild,
            DeviceDescriptor descriptor) throws TargetSetupError {
        Map<String, Constraint> constraintMap = new HashMap<String, Constraint>(1);
        constraintMap.put(FlashingResourcesParser.BASEBAND_VERSION_KEY,
                new SupportedImageConstraint());
        constraintMap.put(FlashingResourcesParser.BOOTLOADER_VERSION_KEY,
                new SupportedImageConstraint());
        constraintMap.put(CDMA_VERSION_KEY, new SupportedImageConstraint());
        try {
            return new FlashingResourcesParser(localBuild.getDeviceImageFile(), constraintMap);
        } catch (TargetSetupError e) {
            // rethrow the exception with serial since flashing resource parser doesn't have it.
            throw new TargetSetupError(e.getMessage(), e, descriptor);
        }
    }

    /**
     * Downloads the CDMA baseband image and stores it in the provided {@link IDeviceBuildInfo}
     */
    @Override
    protected void downloadExtraImageFiles(IFlashingResourcesParser resourceParser,
            IFlashingResourcesRetriever retriever, IDeviceBuildInfo localBuild)
            throws TargetSetupError {
        // FIXME: don't hardcode the product type.  Not possible without significant refactor since
        // FIXME: the desired product type is only knowable _after_ the bootloader has been flashed
        String cdmaVersion = resourceParser.getRequiredImageVersion(CDMA_VERSION_KEY);
        if (cdmaVersion != null) {
            File cdmaFile = retriever.retrieveFile(CDMA_IMAGE_NAME, cdmaVersion);
            localBuild.setFile(CDMA_IMAGE_NAME, cdmaFile, cdmaVersion);
        }
    }

    /**
     * Gracefully handle the case where the bootloader doesn't (yet) know about version-cdma
     */
    @Override
    protected String getImageVersion(ITestDevice device, String imageName)
            throws DeviceNotAvailableException, TargetSetupError {
        try {
            return super.getImageVersion(device, imageName);
        } catch (TargetSetupError e) {
            if (CDMA_SHORT_KEY.equals(imageName)) {
                // expected to fail if the bootloader is too old to know about version-cdma
                return "";
            } else {
                throw e;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean checkShouldFlashBaseband(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String currentBasebandVersion = getImageVersion(device, "baseband");
        boolean shouldFlashBaseband = (deviceBuild.getBasebandVersion() != null &&
                !deviceBuild.getBasebandVersion().equals(currentBasebandVersion));
        return shouldFlashBaseband || checkShouldFlashCdmaBaseband(device, deviceBuild);
    }

    private boolean checkShouldFlashCdmaBaseband(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String currentCdmaVersion = getImageVersion(device, CDMA_SHORT_KEY);
        String buildCdmaVersion = deviceBuild.getVersion(CDMA_IMAGE_NAME);

        // FIXME: if we need to flash cdma and lte to equivalent versions, this is where that goes
        return buildCdmaVersion != null && !buildCdmaVersion.equals(currentCdmaVersion);
    }

    /**
     * {@inheritDoc}
     * <p />
     * passthrough method for test visibility (yay protected!)
     */
    @Override
    protected void checkAndFlashBaseband(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        super.checkAndFlashBaseband(device, deviceBuild);
    }

    /**
     * {@inheritDoc}
     * <p />
     * passthrough method for test visibility (yay protected!)
     */
    @Override
    protected File extractSystemZip(IDeviceBuildInfo deviceBuild) throws IOException {
        return super.extractSystemZip(deviceBuild);
    }

    /**
     * {@inheritDoc}
     * <p />
     * passthrough method for test visibility (yay protected!)
     */
    @Override
    protected void flashPartition(ITestDevice device, File dir, String partition)
            throws DeviceNotAvailableException, TargetSetupError {
        super.flashPartition(device, dir, partition);
    }

    /**
     * {@inheritDoc}
     * <p />
     * passthrough method for test visibility (yay protected!)
     */
    @Override
    protected IRunUtil getRunUtil() {
        return super.getRunUtil();
    }

    /**
     * Flashes the CDMA baseband image if necessary
     * @param device
     * @param deviceBuild
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    protected void checkAndFlashCdmaBaseband(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String currentBasebandVersion = getImageVersion(device, CDMA_SHORT_KEY);
        if (checkShouldFlashCdmaBaseband(device, deviceBuild)) {
            CLog.i("Flashing CDMA baseband %s", deviceBuild.getVersion(CDMA_IMAGE_NAME));
            flashPartition(device, deviceBuild.getFile(CDMA_IMAGE_NAME), CDMA_IMAGE_NAME);

            // Do the fancy double-reboot
            // Don't use device.reboot() the first time because radio flash can take 5+ minutes
            device.executeFastbootCommand("reboot");
            device.waitForDeviceOnline(BASEBAND_FLASH_TIMEOUT);
            device.waitForDeviceAvailable();
            // Wait for radio version updater to do its thing
            getRunUtil().sleep(5000);
            // CdmaDeviceFlasher#flash will take care of the second reboot
        } else {
            CLog.i("CDMA is either unsupported or up-to-date; current version is: %s",
                    currentBasebandVersion);
        }
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
     * Piggyback CDMA baseband flashing off of flashSystem (in terms of ordering)
     * <p />
     * {@inheritDoc}
     */
    @Override
    protected void flashSystem(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        super.flashSystem(device, deviceBuild);

        checkAndFlashCdmaBaseband(device, deviceBuild);
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

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getBootPartitionName() {
        return "bootloader";
    }
}

