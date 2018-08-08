// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.tradefed.device;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;

import org.junit.Assert;

import java.io.File;

/**
 * A utility class that can change the settings of a device.
 * <p/>
 * This class is temporarily in vendor/google, because it relies on a not-ready-for open source
 * mechanism to change secure settings. Once a open-source safe method for changing secure settings
 * is available, this class will be migrated to the core tradefed project
 */
public class SettingsUtil {

    private static final String SETTING_CMD_PKG = "com.android.settingscmd";
    // file system path to the settings cmd apks
    private static final String TEST_KEY_SETTINGS_CMD_APK_PATH =
            "/google/data/ro/teams/tradefed/utils/SettingsCmd.apk";
    private static final String DEV_KEY_SETTINGS_CMD_APK_PATH =
            "/google/data/ro/teams/tradefed/utils/SettingsCmd-devkey.apk";
    private boolean mUseSettingCmdApk = true;
    private File mSettingCmdApk = null;
    private static final String OVERRIDE_GSERVICES_INTENT =
        "com.google.gservices.intent.action.GSERVICES_OVERRIDE";

    private final ITestDevice mDevice;

    /**
     * Default constructor for SettingsUtil - provides ability to configure device-side settings.
     * A default apk is used to configure device settings, and will be chosen based on the device's
     * build signature.
     *
     * @param device Device under test
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    public SettingsUtil(ITestDevice device) throws DeviceNotAvailableException, TargetSetupError {
        this(device, null);
    }

    /**
     * Constructor that allows overriding the default apk that is used to configure device settings.
     *
     * @param device Device under test
     * @param settingApk File object referring to the path where SettingCmd.apk is stored. If null,
     *        uses the default apk, chosen based on the device's build signature.
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    public SettingsUtil(ITestDevice device, File settingApk) throws DeviceNotAvailableException,
            TargetSetupError {
        mDevice = device;
        if (settingApk != null) {
            mUseSettingCmdApk = true;
            mSettingCmdApk = settingApk;
        } else if (mDevice.getApiLevel() >= 22) {
            mUseSettingCmdApk = false;
        } else {
            mUseSettingCmdApk = true;
            mSettingCmdApk = getSettingsCmdApk(device);
        }
    }

    /**
     * Gets the correctly-signed SettingsCmd apk File object, based on the device's platform key.
     *
     * @return file object to the correctly-signed SettingsCmd apk
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    private File getSettingsCmdApk(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        String keysType = mDevice.getProperty("ro.build.tags");
        if (keysType == null) {
            throw new TargetSetupError("device platform key is null", device.getDeviceDescriptor());
        }

        if (keysType.contains("test-keys")) {
            return new File(TEST_KEY_SETTINGS_CMD_APK_PATH);
        } else if (keysType.contains("dev-keys")) {
            return new File(DEV_KEY_SETTINGS_CMD_APK_PATH);
        } else {
            throw new TargetSetupError(String.format(
                    "No SettingCmd apk is available for platform key '%s'", keysType),
                    device.getDeviceDescriptor());
        }
    }

    /**
     * Install the setting cmd apk on device. Must be executed before any change setting calls are
     * made.
     */
    public void installSettingCmdApk() throws DeviceNotAvailableException {
        if (mUseSettingCmdApk) {
            Assert.assertNull(mDevice.installPackage(mSettingCmdApk, true));
        }
    }

    /**
     * Uninstall the setting cmd apk from device.
     */
    public void uninstallSettingCmd() throws DeviceNotAvailableException {
        if (mUseSettingCmdApk) {
            mDevice.uninstallPackage(SETTING_CMD_PKG);
        }
    }

    /**
     * Change a setting on the device
     * @param name
     * @param value
     * @throws DeviceNotAvailableException
     */
    public void changeSetting(String name, String value) throws DeviceNotAvailableException {
        if (mUseSettingCmdApk) {
            String cmd = String.format("am instrument -w -e name %s -e value %s "
                    + "%s/.SettingsInstrument", name, value, SETTING_CMD_PKG);
            mDevice.executeShellCommand(cmd);
        } else {
            mDevice.setSetting("system", name, value);
        }
    }

    /**
     * Change a secure setting on device
     * @param name
     * @param value
     * @throws DeviceNotAvailableException
     */
    public void changeSecureSetting(String name, String value)
            throws DeviceNotAvailableException {
        if (mUseSettingCmdApk) {
            String cmd = String.format("am instrument -w -e secure true -e name %s -e value %s " +
                    "%s/.SettingsInstrument", name, value, SETTING_CMD_PKG);
            mDevice.executeShellCommand(cmd);
        } else {
            mDevice.setSetting("secure", name, value);
        }
    }

    /**
     * Change a global setting on device
     * @param name
     * @param value
     * @throws DeviceNotAvailableException
     */
    public void changeGlobalSetting(String name, String value)
            throws DeviceNotAvailableException {
        if (mUseSettingCmdApk) {
            String cmd = String.format("am instrument -w -e global true -e name %s -e value %s " +
                    "%s/.SettingsInstrument", name, value, SETTING_CMD_PKG);
            mDevice.executeShellCommand(cmd);
        } else {
            mDevice.setSetting("global", name, value);
        }
    }

    /**
     * Change a Google partner setting on device
     * @param name
     * @param value
     * @throws DeviceNotAvailableException
     */
    public void changeGooglePartnerSetting(String name, String value)
            throws DeviceNotAvailableException {
        String cmd;
        if (mUseSettingCmdApk) {
            cmd = String.format("am instrument -w -e partner true -e name %s -e value %s " +
                    "%s/.SettingsInstrument", name, value, SETTING_CMD_PKG);
        } else {
            cmd = String.format("content insert --uri content://com.google.settings/partner " +
                    "--bind name:s:%s --bind value:s:%s", name, value);
        }
        mDevice.executeShellCommand(cmd);
    }

    /**
     * Override a gservices value on device.
     * <p/>
     * Note this operation is asynchronous: there is no guarantee that the gservices value is set
     * when this method returns.
     * @param name
     * @param value
     * @throws DeviceNotAvailableException
     */
    public void overrideGServices(String name, String value)
            throws DeviceNotAvailableException {
        String overrideCmd = String.format("am broadcast -a '%s' -e '%s' '%s'",
                OVERRIDE_GSERVICES_INTENT, name, value);
        mDevice.executeShellCommand(overrideCmd);
    }
}

