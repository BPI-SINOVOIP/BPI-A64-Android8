// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.google.android.tradefed.device.SettingsUtil;

import java.io.File;

/**
 * A {@link ITargetPreparer} that configures a device's settings in preparation for running
 * CTS tests.
 * <p/>
 * This class is temporarily in vendor/google, because it relies on a not-ready-for open source
 * mechanism to change secure settings. Once a open-source safe method for changing secure settings
 * is available, this class will be migrated to the CTS project
 * <p/>
 * This preparer must run *after* the CTS accessiblity apk is installed
 */
public class CtsSettingsSetup implements ITargetPreparer {
    @Option(name="setting-cmd-apk-path", description="file system path to the settings cmd apk.")
    private File mSettingCmdApk = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        if (mSettingCmdApk != null && !mSettingCmdApk.exists()) {
            throw new TargetSetupError(String.format("--setting-cmd-apk-path '%s' does not exist",
                    mSettingCmdApk.getAbsolutePath()), device.getDeviceDescriptor());
        }
        SettingsUtil settingsUtil = new SettingsUtil(device, mSettingCmdApk);
        settingsUtil.installSettingCmdApk();
        CLog.i("Modifying device settings to prep for CTS tests", device.getSerialNumber());
        settingsUtil.changeSecureSetting("mock_location", "1");
        // turn off auto-rotate screen
        settingsUtil.changeSetting("accelerometer_rotation", "0");
        // set user rotation to natural orientation
        settingsUtil.changeSetting("user_rotation", "0");
        // set network_location_opt_in on legacy builds
        settingsUtil.changeSecureSetting("network_location_opt_in", "1");
        // for newish builds, network_location_opt_in is stored in google partner dB. update it
        // to prevent 'Location Consent' dialog from appearing
        settingsUtil.changeGooglePartnerSetting("network_location_opt_in", "1");
        settingsUtil.uninstallSettingCmd();
    }
}
