// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep.clockwork;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.DeviceFailedToBootError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ITargetPreparer} that configures a clockwork device for testing based on provided
 * {@link Option}s.
 * <p>
 * Requires a device where 'adb root' is possible, typically a userdebug build type.
 * </p>
 * <p>
 * Should be performed <strong>after</strong> a new build is flashed.
 * </p>
 */
@OptionClass(alias = "clockwork-device-setup")
public class ClockworkDeviceSetup implements ITargetPreparer, ITargetCleaner {
    protected List<String> mRunCommands = new ArrayList<>();

    @Option(name = "skip-tutorial",
            description = "Skip the tutorial for Clockwork tests")
    private boolean mSkipTutorial = false;
    // true >=api24: am broadcast -a com.google.android.clockwork.action.TUTORIAL_SKIP
    // true <api24: am startservice -a com.google.android.clockwork.action.TUTORIAL_SKIP

    @Option(name = "test-mode",
            description = "Enable test mode for Clockwork tests")
    private boolean mTestMode = false;
    // true: am broadcast -a com.google.android.clockwork.action.TEST_MODE

    @Option(name = "disable-ambient",
            description = "Disable ambient for Clockwork tests")
    private boolean mDisableAmbient = false;
    // true: am broadcast -a com.google.android.clockwork.settings.SYNC_AMBIENT_DISABLED \
    //       --ez ambient_disabled true

    @Option(name = "disable-gaze",
            description = "Disable smart illuminate (gaze) for Clockwork tests")
    private boolean mDisableGaze = false;
    // true: am broadcast -a com.google.gservices.intent.action.GSERVICES_OVERRIDE -e
    // cw:smart_illuminate_enabled false

    @Option(name = "disable-ungaze",
            description = "Disable Ungaze for Clockwork tests")
    private boolean mDisableUnGaze = false;
    // true: am broadcast -a com.google.gservices.intent.action.GSERVICES_OVERRIDE -e
    // cw:ungaze_default_setting false

    @Option(name = "enter-retail",
            description = "Enter retail mode for test")
    private boolean mEnterRetail = false;
    // true: am startservice -a com.google.android.clockwork.settings.ENTER_RETAIL_FOR_TEST

    @Option(name = "wifi-setting", description = "Turn wifi on")
    protected boolean mWifiSetting = false;
    // true:  settings put system clockwork_wifi_setting on

    @Option(name = "disable-wifi-mediator", description = "Disable wifi mediator")
    protected boolean mDisableWifiMediator = false;
    // true:  settings put global cw_disable_wifimediator 1

    @Option(
        name = "cw-wifi-network",
        description =
                "The SSID of the network to connect to. Will only attempt to "
                        + "connect to a network if set"
    )
    protected String mCwWifiSsid = null;

    @Option(name = "cw-wifi-psk",
            description = "The passphrase used to connect to a secured network")
    protected String mCwWifiPsk = null;

    // Test harness
    @Option(name = "disable",
            description = "Disable the device setup")
    protected boolean mDisable = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mDisable) {
            return;
        }

        CLog.i("Performing Clockwork setup on %s", device.getSerialNumber());

        if (!device.enableAdbRoot()) {
            throw new TargetSetupError(String.format("Failed to enable adb root on %s",
                    device.getSerialNumber()), device.getDeviceDescriptor());
        }

        // Convert options into run commands
        processOptions(device);
        // Run commands
        runCommands(device, mRunCommands);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mDisable) {
            return;
        }

        CLog.i("Performing teardown on %s", device.getSerialNumber());

        if (e instanceof DeviceFailedToBootError) {
            CLog.d("boot failure: skipping teardown");
            return;
        }
    }

    /**
     * Process all the {@link Option}s and turn them into run commands. Does not run any commands on
     * the device at this time.
     * <p>
     * Exposed so that children classes may override this.
     * </p>
     *
     * @param device The {@link ITestDevice}
     * @throws DeviceNotAvailableException if the device is not available
     * @throws TargetSetupError if the {@link Option}s conflict
     */
    public void processOptions(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mSkipTutorial && !mEnterRetail) {
            if (device.getApiLevel() < 24) {
                mRunCommands.add(
                        "am startservice -a com.google.android.clockwork.action.TUTORIAL_SKIP");
            } else {
                mRunCommands.add(
                        "am broadcast -a com.google.android.clockwork.action.TUTORIAL_SKIP");
            }
        }

        if (mTestMode && !mEnterRetail) {
            mRunCommands.add(
                    "am broadcast -a com.google.android.clockwork.action.TEST_MODE");
        }

        if (mDisableAmbient) {
            mRunCommands.add(
                    "am broadcast -a com.google.android.clockwork.settings.SYNC_AMBIENT_DISABLED " +
                            "--ez ambient_disabled true");
        }

        if (mDisableGaze) {
            mRunCommands.add(
                    "am broadcast -a com.google.gservices.intent.action.GSERVICES_OVERRIDE "
                            + "-e cw:smart_illuminate_enabled false");
        }

        if (mDisableUnGaze) {
            mRunCommands.add(
                    "am broadcast -a com.google.gservices.intent.action.GSERVICES_OVERRIDE "
                            + "-e cw:ungaze_default_setting false");
        }

        if (mEnterRetail) {
            if (device.getApiLevel() < 24) {
                mRunCommands.add(
                    "am startservice -a com.google.android.clockwork.settings.ENTER_RETAIL_FOR_TEST"
                    );
            } else {
                mRunCommands.add(
                        "am broadcast -a com.google.android.clockwork.action.START_RETAIL_MODE");
            }
        }

        if (mWifiSetting) {
            mRunCommands.add("settings put system clockwork_wifi_setting on");
        }

        if (mDisableWifiMediator) {
            mRunCommands.add("settings put global cw_disable_wifimediator 1");
        }

        if (mCwWifiSsid != null && !mCwWifiSsid.isEmpty()) {
            String wifiServiceStr = "com.google.android.apps.wearable.settings/" +
                    "com.google.android.clockwork.settings.wifi.WifiSettingsService";
            if (device.getApiLevel() < 24) {
                mRunCommands
                        .add("content update --uri content://com.google.android.wearable.settings/"
                                + "auto_wifi --bind auto_wifi:i:0");
            }
            mRunCommands.add("svc wifi enable");
            mRunCommands.add("am startservice " + wifiServiceStr);

            String wifiCmd = "dumpsys activity service " + wifiServiceStr;
            if (mCwWifiPsk != null && !mCwWifiPsk.isEmpty()) {
                wifiCmd += String.format(" %s 2 %s", mCwWifiSsid, mCwWifiPsk);
            } else {
                wifiCmd += String.format(" %s 0 ''", mCwWifiSsid);
            }
            mRunCommands.add(wifiCmd);

            mRunCommands.add("am stopservice " + wifiServiceStr);
        }
    }

    /**
     * Execute additional commands on the device.
     *
     * @param device The {@link ITestDevice}
     * @param commands The list of commands to run
     * @throws DeviceNotAvailableException if the device is not available
     * @throws TargetSetupError if there was a failure setting the settings
     */
    private void runCommands(ITestDevice device, List<String> commands)
            throws DeviceNotAvailableException, TargetSetupError {
        for (String command : commands) {
            device.executeShellCommand(command);
        }
    }
}
