// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link ITargetPreparer} that configures device launcher as specified by package name.
 *
 * <p>Note that user of this {@link ITargetPreparer} is responsible for installing the launcher
 * package in question prior to using this preparer. This is because there might be different ways
 * of installing the launcher package, e.g. fixed location or pulled from build artifact.
 *
 * <p>Source of SystemUtils.apk: &lt;platform source&gt;/vendor/google/tests/SystemUtils
 */
@OptionClass(alias = "launcher-config")
public class DeviceLauncherConfigurator implements ITargetPreparer {

    private static final String UTIL_PKG_NAME = "com.android.testing.systemutils";
    private static final String CHECK_INSTRUMENTATION_CMD =
            "pm list instrumentation " + UTIL_PKG_NAME;
    private static final String UTIL_APK_NAME = "SystemUtils";
    private static final String SET_LAUNCHER_CMD = "am instrument -w -e command set-launcher "
            + "-e force-wait true -e package \"%s\" %s/.CommandRunner";
    private static final String INSTR_SUCCESS = "INSTRUMENTATION_CODE: -1";

    @Option(name = "launcher-package",
            description = "the package name of the launcher to set as default")
    private String mLauncherPackageName = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        // automatically disable if no package name provided
        if (mLauncherPackageName == null || mLauncherPackageName.isEmpty()) {
            return;
        }
        // installs the util for configuring launcher
        if (!installUtil(device)) {
            throw new TargetSetupError(String.format("Failed to install account util on device %s",
                    device.getSerialNumber()), device.getDeviceDescriptor());
        }
        try {
            if (!runSetLauncherInstrumentation(device, mLauncherPackageName)) {
                throw new TargetSetupError(String.format("Failed to set device launcher to %s",
                        mLauncherPackageName), device.getDeviceDescriptor());
            }
        } finally {
            device.uninstallPackage(UTIL_PKG_NAME);
        }
    }

    /**
     * Utility function to run the set launcher instrumentation
     * @param device device to run the instrumentation on
     * @param packageName package name of the launcher to set
     * @return <code>true</code> if no errors from set launcher instrumentation
     */
    boolean runSetLauncherInstrumentation(ITestDevice device, String packageName)
            throws DeviceNotAvailableException {
        String result = device.executeShellCommand(String.format(
                SET_LAUNCHER_CMD, packageName, UTIL_PKG_NAME));
        if (result != null && result.contains(INSTR_SUCCESS)) {
            return true;
        } else {
            CLog.w("Failed to set launcher to %s, command output: %s", packageName, result);
            return false;
        }
    }

    /**
     * Install the utility apk contained in jar if necessary
     */
    boolean installUtil(ITestDevice device) throws DeviceNotAvailableException {
        //TODO: This is directly lifted from GoogleAccountPreparer, and similar code exists in
        // WiFiHelper, so refactoring a util class for installing embedded apks is needed
        final String inst = device.executeShellCommand(CHECK_INSTRUMENTATION_CMD);
        if ((inst != null) && inst.contains(UTIL_PKG_NAME)) {
            // Good to go
            return true;
        } else {
            // Attempt to install utility
            File apkTempFile = null;
            try {
                apkTempFile = FileUtil.createTempFile(UTIL_APK_NAME, ".apk");
                InputStream apkStream = getClass().getResourceAsStream(
                        String.format("/apks/%s.apk", UTIL_APK_NAME));
                FileUtil.writeToFile(apkStream, apkTempFile);

                CLog.i("Installing %s on %s", UTIL_APK_NAME, device.getSerialNumber());
                final String result = device.installPackage(apkTempFile, false);
                if (result != null) {
                    CLog.e("Unable to install utility: %s", result);
                    return false;
                }
            } catch (IOException e) {
                CLog.e("Failed to unpack utility: %s", e.getMessage());
                CLog.e(e);
                return false;
            } finally {
                FileUtil.deleteFile(apkTempFile);
            }
            return true;
        }

    }
}
