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
import com.google.android.tradefed.device.SettingsUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ITargetPreparer} that configures device settings via instrumentation
 * of SettingsUtil.apk.
 * TODO: collapse/refactor common code from com.android.tradefed.targetprep.GoogleDeviceSetup.java.
 */
@OptionClass(alias = "settings-setup")
public class SettingsSetup implements ITargetPreparer {
    @Option(name = "enable-airplane-mode", description = "turn on airplane mode.")
    private boolean mAirplaneMode = false;

    @Option(name = "secure-setting", description =
            "change a secure setting. Format: --secure-setting key value. May be repeated.")
    private Map<String, String> mSecureSettings = new HashMap<String, String>();

    @Option(name = "setting", description =
            "change a system (non-secure) setting. Format: --setting key value. May be repeated.")
    private Map<String, String> mSettings = new HashMap<String, String>();

    @Option(name = "google-partner-setting", description =
            "change a google partner setting. Format: --setting key value. May be repeated.")
    private Map<String, String> mPartnerSettings = new HashMap<String, String>();

    @Option(name = "global-setting", description =
            "change a global setting. Format: --setting key value. May be repeated.")
    private Map<String, String> mGlobalSettings = new HashMap<String, String>();

    @Option(name = "gservices-setting", description =
            "change a GServices setting. Format: --gservices-settings key value. May be repeated.")
    private Map<String, String> mGServicesSettings = new HashMap<String, String>();

    @Option(name = "setting-cmd-apk-path",
            description = "file system path to the settings cmd apk.")
    private File mSettingCmdApk = null;

    @Option(name = "disable", description = "Disable this preparer")
    private boolean mDisable = false;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        CLog.i(String.format("Configuring settings on %s", device.getSerialNumber()));
        if (mDisable)
            return;
        changeSettings(device);
    }

    /**
     * Change additional settings for the device.
     *
     * @param device
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    protected void changeSettings(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mAirplaneMode) {
            mGlobalSettings.put("airplane_mode_on", "1");
        }
        if (mSecureSettings.isEmpty() && mSettings.isEmpty() && mGlobalSettings.isEmpty()
                && mPartnerSettings.isEmpty()) {
            return;
        }
        SettingsUtil settingsUtil = new SettingsUtil(device, mSettingCmdApk);
        settingsUtil.installSettingCmdApk();
        for (Map.Entry<String, String> secureEntry : mSecureSettings.entrySet()) {
            CLog.d("Changing secure setting %s to %s", secureEntry.getKey(),
                    secureEntry.getValue());
            settingsUtil.changeSecureSetting(secureEntry.getKey(), secureEntry.getValue());
        }
        for (Map.Entry<String, String> settingEntry : mSettings.entrySet()) {
            CLog.d("Changing setting %s to %s", settingEntry.getKey(),
                    settingEntry.getValue());
            settingsUtil.changeSetting(settingEntry.getKey(), settingEntry.getValue());
        }
        for (Map.Entry<String, String> globalEntry : mGlobalSettings.entrySet()) {
            CLog.d("Changing global setting %s to %s", globalEntry.getKey(),
                    globalEntry.getValue());
            settingsUtil.changeGlobalSetting(globalEntry.getKey(), globalEntry.getValue());
        }
        for (Map.Entry<String, String> partnerEntry : mPartnerSettings.entrySet()) {
            CLog.d("Changing google partner setting %s to %s", partnerEntry.getKey(),
                    partnerEntry.getValue());
            settingsUtil.changeGooglePartnerSetting(partnerEntry.getKey(), partnerEntry.getValue());
        }
        for (Map.Entry<String, String> gsEntry : mGServicesSettings.entrySet()) {
            CLog.d("Changing Gservices setting %s to %s", gsEntry.getKey(),
                    gsEntry.getValue());
            settingsUtil.overrideGServices(gsEntry.getKey(), gsEntry.getValue());
        }
        settingsUtil.uninstallSettingCmd();
        if (mAirplaneMode) {
            device.executeShellCommand(
                    "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true");
        }
    }

}
