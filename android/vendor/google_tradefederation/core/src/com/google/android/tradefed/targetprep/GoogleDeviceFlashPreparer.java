// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.DeviceFlashPreparer;
import com.android.tradefed.targetprep.IDeviceFlasher;

import com.google.android.tradefed.device.StaticDeviceInfo;
import com.google.android.tradefed.device.StaticCompatibleBuildFlavor;

import java.io.File;
import java.util.List;

/**
 * A specialization of {@link DeviceFlashPreparer} that supports custom steps needed to flash
 * Google devices.
 * <p/>
 * It handles this by creating the appropriate {@link IDeviceFlasher} based on product type
 */
@OptionClass(alias = "gdevice-flash")
public class GoogleDeviceFlashPreparer extends DeviceFlashPreparer {
    protected static final String KEY_FILE_PATH =
            "/google/data/ro/teams/tradefed/configs/"
                    + "e006c97067dbad4ecff47a965821946d2d04be57-privatekey.p12";
    protected static final String SERVICE_ACCOUNT =
            "717267004052-o19a1ouu8025j2iumfkniem897nu9gj8@developer.gserviceaccount.com";

    @Option(name = "force-product-type", description =
        "Blindly assume that the device has the specified product type")
    private String mForceProductType = null;

    @Option(name = "force-product-variant", description =
        "Blindly assume that the device has the specified variant")
    private String mForceProductVariant = null;

    @Option(name="allow-secure-flash-stingray", description =
        "Allows flashing stingray devices even if they are secure.")
    private boolean mAllowSecureFlashStingray = false;

    @Option(name="use-build-api", description = "Fetch resource from android build apiary.")
    private boolean mUseBuildApi = true;

    @Option(name="build-api-key-file-path", description = "Key file used to access build apiary."
            + "Checkout go/android-test-infrastructure/android-build-api"
            + " to see how to create the key file.")
    private String mBuildApiKeyFilePath = KEY_FILE_PATH;

    @Option(name="build-api-service-account",
            description = "Service account used to access build apiary."
                    + "Checkout go/android-test-infrastructure/android-build-api"
                    + " to see how to create the service account.")
    private String mBuildApiServiceAccount = SERVICE_ACCOUNT;

    @Option(name = "download-cache-dir", description = "the directory for caching "
            + "downloaded flashing files."
            + "Should be on the same filesystem as java.io.tmpdir.  Consider changing the " +
            "java.io.tmpdir property if you want to move downloads to a different filesystem.")
    private File mDownloadCacheDir = new File(System.getProperty("java.io.tmpdir"), "lc_cache");

    @Option(
        name = "skip-pre-flash-product-check",
        description = "Specify if device product type should be checked before flashing"
    )
    private boolean mSkipPreFlashProductType = false;

    /**
     * Create {@link IDeviceFlasher} to use
     * @throws DeviceNotAvailableException
     */
    @Override
    protected IDeviceFlasher createFlasher(ITestDevice device) throws DeviceNotAvailableException {
        String productType = mForceProductType;
        if (productType == null) {
            productType = device.getProductType();
        }
        String productVariant = mForceProductVariant;
        if (productVariant == null) {
            productVariant = getProductVariant(device, productType);
        }

        if (productVariant == null) {
            // StaticDeviceInfo.getFlasherClass() does not support null productVariant.
            productVariant = "";
        }

        Class<? extends IDeviceFlasher> flasherClass =
                StaticDeviceInfo.getFlasherClass(productType, productVariant);
        if (flasherClass == null) {
            throw new DeviceNotAvailableException(String.format(
                    "Unrecognized product '%s:%s' for device %s: Cannot determine how to flash.",
                    productType, productVariant, device.getSerialNumber()),
                    device.getSerialNumber());
        }

        IDeviceFlasher flasher = null;
        try {
            flasher = flasherClass.newInstance();
            if (StingrayDeviceFlasher.class.equals(flasherClass)) {
              StingrayDeviceFlasher stingray = (StingrayDeviceFlasher) flasher;
              stingray.setAllowFlashingWhenSecure(mAllowSecureFlashStingray);
            }
        } catch (InstantiationException e) {
            throw new DeviceNotAvailableException(String.format(
                    "Failed to instantiate flasher for '%s' device %s: %s", productType,
                    device.getSerialNumber(), e.getMessage()), device.getSerialNumber());
        } catch (IllegalAccessException e) {
            throw new DeviceNotAvailableException(String.format(
                    "Failed to instantiate flasher for '%s' device %s: %s", productType,
                    device.getSerialNumber(), e.getMessage()), device.getSerialNumber());
        }
        if (!mUseBuildApi) {
            flasher.setFlashingResourcesRetriever(new NfsFlashingResourcesRetriever(productType));
        } else {
            flasher.setFlashingResourcesRetriever(
                    new FlashingResourcesRetrieverCacheWrapper(
                            mDownloadCacheDir,
                            new BuildApiFlashingResourcesRetriever(
                                    productType, mBuildApiKeyFilePath, mBuildApiServiceAccount)));
        }
        return flasher;
    }

    private String getProductVariant(ITestDevice device, String productType)
            throws DeviceNotAvailableException {
        if (StaticDeviceInfo.CRESPO_PRODUCT_TYPE.equals(productType)) {
            // Special case for herring: determine the variant by examining the bootloader string
            String bootloader = device.getBootloaderVersion();
            return StaticDeviceInfo.getCrespoVariantFromBootloader(bootloader);
        }

        return device.getProductVariant();
    }

    /** {@inheritDoc} */
    @Override
    protected void checkDeviceProductType(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws BuildError, DeviceNotAvailableException {
        if (mSkipPreFlashProductType) {
            return;
        }
        String buildFlavor = deviceBuild.getBuildFlavor();
        String deviceTypeFromBuild = buildFlavor;
        // handle a case where build flavor format is not really quite as expected.
        // <'device board'>-<user|userdebug>
        if (buildFlavor.indexOf("-") != -1) {
            deviceTypeFromBuild = buildFlavor.substring(0, buildFlavor.indexOf("-"));
        }
        String deviceProduct = device.getProductType(); // ro.hardware of the device
        String deviceVariant = device.getProductVariant(); // ro.product.device of the device
        String lcFlavor = StaticDeviceInfo.getDefaultLcFlavor(deviceProduct, deviceVariant);

        if (!deviceTypeFromBuild.equals(lcFlavor)) {
            List<String> possibleFlavors =
                    StaticCompatibleBuildFlavor.getPossibleFlavors(deviceProduct);
            // We attempt to match compatible known flavors to the build one.
            if (possibleFlavors != null) {
                if (possibleFlavors.contains(deviceTypeFromBuild)) {
                    CLog.d(
                            "Build flavor: %s is compatible with product type: %s",
                            deviceTypeFromBuild, deviceProduct);
                    return;
                }
            }
            // Partial matching since device product type is part of the device product type
            // TODO: Remove this partial match to enforce full checking.
            if (deviceTypeFromBuild.contains(lcFlavor)) {
                CLog.w(
                        "Partial check of product type. Device allocated is a '%s:%s' while build "
                                + "is meant for a '%s'. Please update StaticCompatibilityBuildFlavor.",
                        deviceProduct, deviceVariant, deviceTypeFromBuild);
                return;
            }

            throw new BuildError(
                    String.format(
                            "Device allocated is a '%s:%s' while build is meant for a '%s'",
                            deviceProduct, deviceVariant, deviceTypeFromBuild),
                    device.getDeviceDescriptor());
        }
    }
}
