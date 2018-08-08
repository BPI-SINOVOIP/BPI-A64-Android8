// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;

import java.io.File;

/**
 * A {@link ITargetPreparer} that installs the specified version of GmsCore.
 */
@OptionClass(alias = "gmscore")
public class GmsCoreInstaller implements ITargetPreparer, ITargetCleaner {

    private static final String GMSCORE_PKG_NAME = "com.google.android.gms";

    /**
     * enum representing which gmscore version to install
     */
    private static enum GmsCoreVersion {
        NONE, PROD, DOGFOOD, ALPHA
    }

    @Option(name = "apk-root-path", description =
        "the filesystem path of the directory containing gmscore apks")
    private File mApkRootPath = new File("/auto/android-test/testdata/gcore/");

    @Option(name = "version", description =
            "the GmsCore version to install.")
    private GmsCoreVersion mVersion = GmsCoreVersion.PROD;

    @Option(name = "uninstall", description =
            "uninstall gmscore on teardown.")
    private boolean mUninstall = true;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        if (mVersion.equals(GmsCoreVersion.NONE)) {
            CLog.d("skipping GmsCore install");
            return;
        }
        // attempt to uninstall gmscore first just to be safe
        device.uninstallPackage(GMSCORE_PKG_NAME);
        // find apk file based on pattern
        // <rootpath>/<mVersion>/<arch>/[signed-]GmsCore[-<density>][-<version>].apk
        String archFolder = getCpuArchFolderName(device);
        String signedQualifer = getSignedBuildPrefix(device);
        String density = getScreenDensityPostfix(device);
        String version = getGmsCoreVersion(device);
        String gmsCoreFileName = String.format(
                "%sGmsCore%s%s.apk", signedQualifer, density, version);

        File gmsCoreFile = FileUtil.getFileForPath(mApkRootPath, mVersion.name().toLowerCase(),
                archFolder, gmsCoreFileName);
        if (!gmsCoreFile.exists()) {
            // FIXME: this is a hack. Remove after manchego has been pushed to prod.
            if (!version.isEmpty()) {
                // revert back to default apk and try that.
                gmsCoreFileName = String.format("%sGmsCore%s.apk", signedQualifer, density);
                gmsCoreFile = FileUtil.getFileForPath(mApkRootPath, mVersion.name().toLowerCase(),
                        archFolder, gmsCoreFileName);
            }
            if (!gmsCoreFile.exists()) {
                throw new TargetSetupError(String.format("Could not find GmsCore apk %s",
                        gmsCoreFile.getAbsolutePath()), device.getDeviceDescriptor());
            }
        }
        CLog.i("Installing %s on %s", gmsCoreFile.getAbsolutePath(), device.getSerialNumber());
        String result = device.installPackage(gmsCoreFile, true);
        if (result != null) {
            throw new TargetSetupError(String.format("Failed to install GmsCore. Reason: %s",
                    result), device.getDeviceDescriptor());
        }
    }

    /**
     * Get the version postfix to use in gmscore apk file name.
     * Returns one of <empty string>|-[lmp]
     * @param device
     *
     * @return the version or empty
     * @throws DeviceNotAvailableException
     */
    private String getGmsCoreVersion(ITestDevice device) throws DeviceNotAvailableException {
        // For L and above
        if (device.getApiLevel() >= 21) {
            return "-lmp";
        }
        return "";
    }

    /**
     * Get the screen density postfix to use in gmscore apk file name.
     * Returns one of <empty string>|-[l|m|h|xh|dpi]
     *
     * @return the device resolution or null
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    private String getScreenDensityPostfix(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        String lcdDensity = device.getProperty("ro.sf.lcd_density");
        if (lcdDensity == null) {
            CLog.w("Failed to get resolution of device %s. ro.sf.lcd_density prop does not exist",
                    device.getSerialNumber());
            return "";
        }
        try {
            int intDensity = Integer.parseInt(lcdDensity);
            switch (intDensity) {
                case 160:
                    return "-mdpi";
                case 240:
                    return "-hdpi";
                case 320:
                    return "-xhdpi";
                case 480:
                    return "-xxhdpi";
                default:
                    CLog.w("Could not determine resolution of %s. Received %s. " +
                            "Using apk with all densities.", device.getSerialNumber(), lcdDensity);
                    return "";
            }

        } catch (NumberFormatException e) {
            throw new TargetSetupError(String.format(
                    "Failed to get resolution of device %s. Expected int, received %s",
                    device.getSerialNumber(), lcdDensity), device.getDeviceDescriptor());
        }
    }

    /**
     * Get the sub folder name string used to represent required cpu arch of the gmsCore apk
     * path.
     *
     * @param device
     * @return the cpu arch of device
     * @throws DeviceNotAvailableException
     */
    private String getCpuArchFolderName(ITestDevice device) throws DeviceNotAvailableException {
        return device.getProperty("ro.product.cpu.abi");
    }

    /**
     * Return the apk file name prefix used for distinguishing apk signed with different keys.
     * If device has a release-key build, will return 'signed-'. Otherwise will return empty string.
     *
     * @param device
     * @throws DeviceNotAvailableException
     */
    private String getSignedBuildPrefix(ITestDevice device) throws DeviceNotAvailableException {
       return isSignedBuildRequired(device) ? "signed-" : "";
    }

    /**
     * Determine if signed build is required.
     * @param device
     * @return True if a signed build is required, false otherwise.
     * @throws DeviceNotAvailableException
     */
    private boolean isSignedBuildRequired(ITestDevice device) throws DeviceNotAvailableException {
        // TODO: determine if this works on non-Nexus devices
        String tags = device.getProperty("ro.build.tags");
        return tags != null && tags.contains("release-keys");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (e instanceof DeviceNotAvailableException) {
            return;
        }
        if (mUninstall && !mVersion.equals(GmsCoreVersion.NONE)) {
            device.uninstallPackage(GMSCORE_PKG_NAME);
        }
    }
}
