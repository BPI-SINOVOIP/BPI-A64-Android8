// Copyright 2015 Google Inc. All Rights Reserved.
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
import com.android.tradefed.util.BinaryState;
import com.android.tradefed.util.BuildTestsZipUtils;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ITargetPreparer} that supports various setup options for Google apps
 * <p>
 * Note: a device must have an account configured first, see {@link GoogleAccountPreparer}
 */
@OptionClass(alias = "google-apps-setup")
public class GoogleAppsSetup implements ITargetPreparer {

    private static final String UTIL_PKG_NAME = "com.google.android.apps.platformutils";
    private static final String UTIL_APK_NAME = "GoogleAppsTestUtils.apk";
    private static final String VENDING_INSTR = ".FinskyInstrumentation";
    private static final String NOW_INSTR = ".NowInstrumentation";
    private static final String INSTR_SUCCESS = "INSTRUMENTATION_CODE: -1";
    private static final String CHECK_INSTRUMENTATION_CMD =
            "pm list instrumentation " + UTIL_PKG_NAME;
    /**
     * format string for the util instrumentation
     * <p>
     * Parameters in order: command name, extra options, instrumentation name
     */
    private static final String CMD_FMT =
            "am instrument -w -r -e command %s %s " + UTIL_PKG_NAME + "/%s";

    // constants for supported commands
    private static final String CMD_AUTO_UPDATE = "auto_update";
    private static final String CMD_NOW_OPTIN = "opt-in";

    @Option(name = "vending-auto-update", description = "Turn Play Store auto update on or off")
    private BinaryState mVendingAutoUpdate = BinaryState.IGNORE;

    @Option(name = "now-opt-in", description = "Turn Google Now on or off")
    private BinaryState mNowOptIn = BinaryState.IGNORE;

    @Option(name = "disable", description = "Disables this preparer")
    private boolean mDisable = false;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (mDisable) {
            return;
        }
        List<String> commands = new ArrayList<>();
        if (!BinaryState.IGNORE.equals(mVendingAutoUpdate)) {
            commands.add(String.format(CMD_FMT, CMD_AUTO_UPDATE,
                    /* extra args */
                    String.format("-e value %s",
                            BinaryState.ON.equals(mVendingAutoUpdate) ? "true" : "false"),
                    VENDING_INSTR));
        }
        if (!BinaryState.IGNORE.equals(mNowOptIn)) {
            commands.add(String.format(CMD_FMT, CMD_NOW_OPTIN,
                    /* extra args */
                    String.format("-e version %d",
                            /* version 2 for enable, version -1 for disable */
                            BinaryState.ON.equals(mNowOptIn) ? 2 : -1),
                    NOW_INSTR));
        }
        if (commands.isEmpty()) {
            CLog.d("No options configured, no actions taken");
            return;
        }
        // install util package
        if (!installTestUtil(buildInfo, device)) {
            throw new TargetSetupError(String.format("Failed to install Google apps util on "
                    + "device %s", device.getSerialNumber()), device.getDeviceDescriptor());
        }
        // run commands
        try {
            CLog.i("Setting up Google apps options.");
            for (String command : commands) {
                String result = device.executeShellCommand(command);
                if (result == null || !result.contains(INSTR_SUCCESS)) {
                    CLog.w("Failed to run util, output: %s", result);
                    throw new TargetSetupError("failure in Google apps util",
                            device.getDeviceDescriptor());
                }
            }
        } finally {
            device.uninstallPackage(UTIL_PKG_NAME);
        }

        // reboot the device and wait for adb connection
        if (BinaryState.OFF.equals(mVendingAutoUpdate)) {
            CLog.d("Rebooting device after Google apps setup");
            device.reboot();
        }
    }

    /**
     * Install the utility
     */
    boolean installTestUtil(IBuildInfo buildInfo, ITestDevice device)
            throws DeviceNotAvailableException {
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
                apkTempFile = BuildTestsZipUtils.getApkFile(buildInfo, UTIL_APK_NAME,
                        null, null, /* alt dir settings */
                        true, /* also look up in test harness resource as fallback */
                        device.getBuildSigningKeys());
                CLog.i("Installing %s on %s",
                        apkTempFile.getAbsolutePath(), device.getSerialNumber());
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
