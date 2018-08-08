// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.ddmlib.Log;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.CdmaDeviceFlasher;
import com.android.tradefed.targetprep.IFlashingResourcesParser;
import com.android.tradefed.targetprep.IFlashingResourcesRetriever;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.IRunUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link CdmaDeviceFlasher} for flashing Stingray
 */
public class StingrayDeviceFlasher extends CdmaDeviceFlasher {
    private static final String LOG_TAG = "StingrayDeviceFlasher";

    public static final String[] STINGRAY_PRODUCT_TYPES = new String[] {"stingray", "trygon",
            "xoom-cdma", "xoom-wifi", "wingray"};

    /** Minimum bootloader version that we can flash _from_; older ones use flashstation */
    static final int MIN_SUPPORTED_BOOTLOADER = 1013;

    static final String LTE_VERSION_KEY = "version-baseband-2";
    static final String LTE_IMAGE_NAME = "radio";
    static final String LTE_BUILD_IMAGE_NAME = "lte";

    static final File STINGRAY_EMPTY_CACHE_IMAGE =
            new File("/auto/android-test/www/stingray/cache_stingray_empty.img");

    private Set<String> mAllowedProductTypes;
    private boolean mAllowFlashingWhenSecure = false;

    public StingrayDeviceFlasher() {
        setAllowedProductTypes(STINGRAY_PRODUCT_TYPES);
    }

    /**
     * Convenience constructor for testing that allows setting the allowed product types on-the-fly
     *
     * @param allowedProductTypes allowed product types; See {@link #setAllowedProductTypes}.
     */
    StingrayDeviceFlasher(String[] allowedProductTypes) {
        setAllowedProductTypes(allowedProductTypes);
    }

    /**
     * Set the list of allowed product types.  This lets us support Stingray flashes that cause
     * the reported product type to change.
     */
    private void setAllowedProductTypes(String[] allowedProductTypes) {
        mAllowedProductTypes = new HashSet<String>(allowedProductTypes.length);
        mAllowedProductTypes.addAll(Arrays.asList(allowedProductTypes));
    }

    /**
     * Downloads the LTE baseband radio image and stores it in the provided {@link IDeviceBuildInfo}
     */
    @Override
    protected void downloadExtraImageFiles(IFlashingResourcesParser resourceParser,
            IFlashingResourcesRetriever retriever, IDeviceBuildInfo localBuild)
            throws TargetSetupError {
        // FIXME: don't hardcode the product type.  Not possible without significant refactor since
        // FIXME: the desired product type is only knowable _after_ the bootloader has been flashed
        String lteVersion = resourceParser.getRequiredImageVersion(LTE_VERSION_KEY, "xoom-cdma-lte");
        if (lteVersion != null) {
            File lteFile = retriever.retrieveFile(LTE_IMAGE_NAME, lteVersion);
            localBuild.setFile(LTE_BUILD_IMAGE_NAME, lteFile, lteVersion);
        }
    }

    /**
     * Gracefully handle the case where the bootloader doesn't (yet) know about version-baseband-2
     */
    @Override
    protected String getImageVersion(ITestDevice device, String imageName)
            throws DeviceNotAvailableException, TargetSetupError {
        try {
            return super.getImageVersion(device, imageName);
        } catch (TargetSetupError e) {
            if ("baseband-2".equals(imageName)) {
                // expected to fail if the bootloader is too old to know about version-baseband-2
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
        return shouldFlashBaseband || checkShouldFlashLteBaseband(device, deviceBuild);
    }

    private boolean checkShouldFlashLteBaseband(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        if(!"xoom-cdma-lte".equals(device.getFastbootProductType())) {
            // we should've flashed the bootloader by now, so this should be accurate at this point
            return false;
        }

        String currentLteVersion = getImageVersion(device, "baseband-2");
        String buildLteVersion = deviceBuild.getVersion(LTE_BUILD_IMAGE_NAME);

        return buildLteVersion != null && !buildLteVersion.equals(currentLteVersion);
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
     * Flashes the microp image if necessary
     * @param device
     * @param deviceBuild
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    protected void checkAndFlashLteBaseband(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String currentBasebandVersion = getImageVersion(device, "baseband-2");
        if (checkShouldFlashLteBaseband(device, deviceBuild)) {
            Log.i(LOG_TAG, String.format("Flashing LTE baseband %s",
                    deviceBuild.getVersion(LTE_BUILD_IMAGE_NAME)));
            // Note: this flashes to the same partition as the primary baseband.  WAI
            flashBaseband(device, deviceBuild.getFile(LTE_BUILD_IMAGE_NAME));

            // Do the fancy double-reboot
            // Don't use device.reboot() the first time because radio flash can take 5+ minutes
            device.executeFastbootCommand("reboot");
            device.waitForDeviceOnline(BASEBAND_FLASH_TIMEOUT);
            device.waitForDeviceAvailable();
            // Wait for radio version updater to do its thing
            getRunUtil().sleep(5000);
            // CdmaDeviceFlasher#flash will take care of the second reboot
        } else {
            Log.i(LOG_TAG, String.format(
                    "LTE is either unsupported or up-to-date; current version is: %s",
                    currentBasebandVersion));
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
        if (getUserDataFlashOption().equals(UserDataFlashOption.WIPE)) {
            CLog.w("Overriding userdata-flash to %s: current setting of %s is not advised.  Specify"
                    + " %s instead of %s to skip this override.",
                    UserDataFlashOption.WIPE_RM, UserDataFlashOption.WIPE,
                    UserDataFlashOption.FORCE_WIPE, UserDataFlashOption.WIPE);
            super.setUserDataFlashOption(UserDataFlashOption.WIPE_RM);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void flashSystem(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        super.flashSystem(device, deviceBuild);

        checkAndFlashLteBaseband(device, deviceBuild);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkAndFlashSystem(ITestDevice device, String systemBuildId,
            String systemBuildFlavor, IDeviceBuildInfo deviceBuild)
                    throws DeviceNotAvailableException, TargetSetupError {
        boolean result = super.checkAndFlashSystem(device, systemBuildId, systemBuildFlavor,
                deviceBuild);

        // When a wingray is used with a factory cable, we need to set the
        // persist.sys.usb.config property to properly detect it. We can still detect it as it
        // boots up, but shortly thereafter it switches to the usbnet,adb mode which causes it to
        // become inaccessible. Motorola filled an OEM-specific map (usually empty on other
        // devices), with values for wingray only. The code in UsbDeviceManager takes the value of
        // two properties: ro.bootmode + persist.sys.usb.config and maps it to a new value for
        // sys.usb.config. The ro.bootmode is "factorycable". The initial USB config is "mtp,adb",
        // and it gets translated to "usbnet,adb", after which the device completely falls off USB
        // and even stops showing in lsusb -v.
        // https://code.google.com/p/android-source-browsing/source/detail?
        // spec=svn.device--moto--wingray.06de9d985cee83de199c734f0a536bf4d47196a7
        // &name=jb-mr0-release&r=06de9d985cee83de199c734f0a536bf4d47196a7
        // &repo=device--moto--wingray.
        if (device.getProductVariant().equals("wingray")) {
            device.executeShellCommand("setprop persist.sys.usb.config adb");
        }
        return result;
    }

    /**
     * Wipes cache by flashing an empty cache filesystem image. This is to keep
     * http://b/issue?id=5010597 from causing corruption on the cache partition.
     * <p />
     * {@inheritDoc}
     */
    @Override
    protected void wipeCache(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        // If the device is secure, then wipeCache will fail, so we make this a no-op only if the
        // user requested flashing on a secure device.
        if (isDeviceSecure(device) && mAllowFlashingWhenSecure) {
            return;
        }
        flashPartition(device, STINGRAY_EMPTY_CACHE_IMAGE, "cache");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean checkAndFlashBootloader(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        // Bootloader versions under 1013 need special flashstation help
        // Testing substring since some versions may have appended characters, like "1007x"
        // FIXME: Add support for more bootloader versions with more than four digits
        String stringBootVersion = getImageVersion(device, "bootloader");
        int bootVersion = Integer.parseInt(stringBootVersion.substring(0, 4));
        if (bootVersion < MIN_SUPPORTED_BOOTLOADER) {
            throw new TargetSetupError(String.format("Current bootloader version %s is below the " +
                    "minimum supported bootloader version %d.  Flash your device at a " +
                    "flashstation.", bootVersion, MIN_SUPPORTED_BOOTLOADER),
                    device.getDeviceDescriptor());
        }

        String stringReqBootVersion = deviceBuild.getBootloaderVersion();
        int reqBootVersion = Integer.parseInt(stringReqBootVersion.substring(0, 4));
        if (reqBootVersion < MIN_SUPPORTED_BOOTLOADER) {
            throw new TargetSetupError(String.format("Requested bootloader version %s for build " +
                    "%s is below the minimum supported bootloader version %d.  Flash your device " +
                    "to the desired build at a bootloader.", stringReqBootVersion,
                    deviceBuild.getDeviceBuildId(), MIN_SUPPORTED_BOOTLOADER),
                    device.getDeviceDescriptor());
        }

        // Bootloaders 1025 and 1026 are special; bail out for now
        if ((bootVersion == 1025) || (bootVersion == 1026) || (reqBootVersion == 1025) ||
                (reqBootVersion == 1026)) {
            if (bootVersion == reqBootVersion) {
                Log.i(LOG_TAG, String.format("Bootloader is already at specially-handled " +
                        "version %s; yay.", reqBootVersion));
                return false;
            }

            // Structure the code so that we can't possibly escape from this "if" block
            throw new TargetSetupError(String.format("Flashing to or from bootloaders " +
                    "1025 or 1026 is not currently supported."), device.getDeviceDescriptor());
        }

        // Flashing a device when it is secure is possible, but the bootloader should not be
        // flashed. By default, we do not allow this case unless the user overrides the default
        // option in which case we simply skip flashing the bootloader but otherwise continue
        // as long as the device has a bootloader version compatible with the requested image.
        if (isDeviceSecure(device) && mAllowFlashingWhenSecure) {
          // TODO: Since an image allows a set of allowed bootloaders, consider refactoring
          // IDeviceBuildInfo to return a collection of allowed bootloader versions so that we are
          // not just checking against the most recent bootloader version.
          if (reqBootVersion != bootVersion) {
            throw new TargetSetupError(String.format("The requested image requires bootloader "
                + "version %s but your device has a bootloader version %s. Because this device is "
                + "secure, flashing will abort because the bootloader cannot be flashed on a "
                + "secure device. Consider changing the \"require version-bootloader=XXXX\" line "
                + "in android-info.txt if you are sure that bootloader is compatible",
                stringReqBootVersion, stringBootVersion), device.getDeviceDescriptor());
          }
          return false;
        }

        return super.checkAndFlashBootloader(device, deviceBuild);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getBootPartitionName() {
        return "motoboot";
    }

    /**
     * Get the bootloader file prefix based on device secure setting.
     * @throws DeviceNotAvailableException
     */
    @Override
    protected String getBootloaderFilePrefix(ITestDevice device) throws TargetSetupError,
            DeviceNotAvailableException {
        if (isDeviceSecure(device)) {
          if (mAllowFlashingWhenSecure) {
            return getBootPartitionName();
          }
          throw new TargetSetupError("By default, flasher only supports \"unsecure\" stingray "
              + "devices. The --allow-secure-flash-stingray option can bypass this check, but "
              + "please be aware that your device may be bricked if you use this option!",
              device.getDeviceDescriptor());
        } else {
          return getBootPartitionName();
        }
    }

    /**
     * Special case the {@link #verifyRequiredBoards(ITestDevice, IFlashingResourcesParser, String)}
     * check to allow cross-product-type flashes for stingray hardware
     * {@inheritDoc}
     */
    @Override
    protected void verifyRequiredBoards(ITestDevice device, IFlashingResourcesParser resourceParser,
            String deviceProductType) throws TargetSetupError {
        // special case - allow cross-product flashes for stingray, as long as both the current
        // device product type and the requiredBoards for the build are recognized as valid
        // Stingray product types
        if (mAllowedProductTypes.contains(deviceProductType)) {
            for (String boardType : resourceParser.getRequiredBoards()) {
                if (mAllowedProductTypes.contains(boardType)) {
                    // allowed
                    return;
                }
            }
        }
        super.verifyRequiredBoards(device, resourceParser, deviceProductType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void overrideDeviceOptions(ITestDevice device) {
        if (!device.getUseFastbootErase()) {
            CLog.w("Overriding use-fastboot-erase to true. Fastboot format is not supported on " +
                    "Stingray");
            device.setUseFastbootErase(true);
        }
    }

    /**
     * Toggle ability to allow flashing a secure device.
     * @param allowFlashingWhenSecure
     */
    public void setAllowFlashingWhenSecure(boolean allowFlashingWhenSecure) {
      this.mAllowFlashingWhenSecure = allowFlashingWhenSecure;
    }

    /**
     * Returns whether or not the bootloader is secure.
     * @param device
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    private boolean isDeviceSecure(ITestDevice device)
        throws TargetSetupError, DeviceNotAvailableException{
        String secureOutput = executeFastbootCmd(device, "getvar", "secure");
        Pattern secureOutputPattern = Pattern.compile("secure:\\s*(.*)\\s*");
        Matcher matcher = secureOutputPattern.matcher(secureOutput);
        String secureVal = null;
        if (matcher.find()) {
            secureVal = matcher.group(1);
        }
        if ("yes".equals(secureVal)) {
            return true;
        } else if ("no".equals(secureVal)) {
            return false;
        } else {
            // device can be bricked if wrong bootloader is flashed, so be overly cautious
            throw new TargetSetupError("Could not determine 'secure' value for moto bootloader",
                    device.getDeviceDescriptor());
        }
    }
}
