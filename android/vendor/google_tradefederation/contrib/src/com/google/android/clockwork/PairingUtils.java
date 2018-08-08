// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.BluetoothUtils;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/** A pairing utility to support one time pairing */
public class PairingUtils {

    private static final String SUCCESS_CLEARDATA_OUTPUT = "Success";
    private static final String SUCCESS_INSTRUMENTATION_OUTPUT = "OK (1 test)";
    protected static final String DISABLE_TUTORIAL_E =
            "am startservice -a com.google.android.clockwork.action.TUTORIAL_SKIP";
    protected static final String DISABLE_TUTORIAL_F =
            "am broadcast -a com.google.android.clockwork.action.TUTORIAL_SKIP";
    private static final String DISABLE_NUMBER_SYNC =
            "pm disable com.google.android.apps.wearable.numbersync";
    private static final String DISABLE_STURGEON_DOCKING =
            "pm disable com.huawei.mercury.chargedetection";
    private static final String DISABLE_WIFI_OPT_IN_DIALOG =
            "pm disable com.google.android.wearable.app/"
                    + "com.google.android.clockwork.home.cloudsync.CloudSyncOptInService";
    private static final String DISABLE_LGE_DOCKING =
            "pm disable com.lge.wearable.chargingmode/"
                    + "com.lge.wearable.chargingmode.ChargingActivity";
    private static final String START_PAIRING_ON_CLOCKWORK =
            "am broadcast -a com.google.android.clockwork.action.START_PAIRING";
    private static final String CW_VALIDATE_CMD = "am stack list";
    private static final String COMPANION_PAIR_CMD =
            "am start -n "
                    + "com.google.android.wearable.app/"
                    + "com.google.android.clockwork.companion.StatusActivity -e EXTRA_AUTO_PAIR \"%s\"";
    private static final String CLOUDSYNC_CMD = " --ez EXTRA_OPT_INTO_CLOUD_SYNC true";
    // TODO: Introduce more granular apk for running single actions.
    private static final String PHONE_PAIR_BUTTON =
            "am instrument -w -r -e model %s "
                    + "-e class com.android.test.uiautomator.platform.clockwork.DevicePairingRetry "
                    + "com.android.test.uiautomator.platform.clockwork/"
                    + "android.support.test.runner.AndroidJUnitRunner";
    private static final String UI_AUTOMATION_PKG_NAME =
            "com.android.test.uiautomator.platform.clockwork";
    private static final String UI_AUTOMATION_RUNNER_NAME =
            "android.support.test.runner.AndroidJUnitRunner";
    private static final String UI_AUTOMATION_CLASS_NAME =
            "com.android.test.uiautomator.platform.clockwork.%s";
    private static final String[] NETWORK_DEBUG_CMDS = {
        "setprop 'log.tag.ClockworkProxy' 'VERBOSE'",
        "setprop 'log.tag.ClockworkProxyService' 'VERBOSE'",
        "setprop 'log.tag.TcpSocketIoHandler' 'VERBOSE'",
        "setprop 'log.tag.UdpRelayingThread' 'VERBOSE'",
        "setprop 'log.tag.bt_btm_sec' 'VERBOSE'",
        "setprop 'log.tag.bt_bta_dm' 'VERBOSE'",
    };
    private static final String CLEAR_DATA_CMD = "pm clear %s";
    private static final String WEAR_APP_PKG = "com.google.android.wearable.app";
    private static final String GMS_CORE_PKG = "com.google.android.gms";
    private static final long POST_BOOT_IDLE = 30;
    private static final long PAIRING_VALIDATE_INTERVAL = 10;
    private static final int CMD_TIME_OUT = 1000;
    private static final long MAX_PAIRING_TIMEOUT = 10 * 60;
    private static final long NETWORK_SYNC_TIMEOUT = 30 * 60;
    private static final long PAIRING_START_TIMEOUT = 2 * 60;
    private static final int TOGGLE_BT_TIME = 3;
    private static final long WIPE_TIMEOUT = 4 * 60 * 1000;
    private static final String MAIN_ACTIVITY = "com.google.android.clockwork.setup.MainActivity";
    private static final String EMERALD_HOME_ACTIVITY =
            "com.google.android.clockwork.home.HomeActivity";
    private static final String HOME_ACTIVITY =
            "com.google.android.clockwork.home2.activity.HomeActivity2";
    private static final String SYNC_SETTING_ACTIVITY =
            "com.google.android.clockwork.home.setup.SyncSettingsActivity";
    private static final String FIRST_SYNC_ACTIVITY =
            "com.google.android.clockwork.home.FirstSyncActivity";
    private static final String CHECKIN_ACTIVITY =
            "com.google.android.wearable.setupwizard.steps.CheckinActivity";
    private static final String PAIR_ACTIVITY =
            "com.google.android.wearable.setupwizard.steps.pair.PairActivity";
    private static final String SHELL_GET_BLUETOOTH_ADDRESS =
            "settings get secure bluetooth_address";
    private static final String SHELL_GET_BLUETOOTH_ADDRESS_E = "cat /persist/bdaddr.txt";
    private ConnectivityHelper mHelper;
    private String mTestFailureReason = "";
    private ITestDevice mDevice;

    /** Call paring validation */
    private Callable<Boolean> mValidatePairing =
            new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return validatePairing(mDevice);
                }
            };

    /** Validate pairing is ready */
    private Callable<Boolean> mValidatePairingIsReady =
            new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return validatePairingIsReady(mDevice);
                }
            };

    /** Make sure pairing is started */
    private Callable<Boolean> mValidatePairingStarted =
            new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return validatePairingStarted(mDevice);
                }
            };

    /**
     * Validate the pairing is finished by polling the system using stack list command
     *
     * @param device
     * @return True if HOME_ACTIVITY or EMERALD_HOME_ACTIVITY are in the stack list. False
     *     otherwise.
     * @throws DeviceNotAvailableException
     */
    protected boolean validatePairing(ITestDevice device) throws DeviceNotAvailableException {
        String outputStr = device.executeShellCommand(CW_VALIDATE_CMD);
        if (outputStr.contains(HOME_ACTIVITY) || outputStr.contains(EMERALD_HOME_ACTIVITY)) {
            CLog.d("Clockwork pairing is done");
            return true;
        } else if (outputStr.contains(MAIN_ACTIVITY)) {
            CLog.d("Clockwork syncing is not started yet");
        } else if (outputStr.contains(FIRST_SYNC_ACTIVITY)
                || outputStr.contains(SYNC_SETTING_ACTIVITY)
                || outputStr.contains(CHECKIN_ACTIVITY)) {
            CLog.d("Clockwork is still syncing");
        }
        mTestFailureReason = "Device did not finish syncing and reach home actvitiy";
        return false;
    }

    /**
     * Validate the pairing is started by polling the system using stack list command
     *
     * @param device
     * @return True if MAIN_ACTIVITY and PAIR_ACTIVITY are not in the stack list. False otherwise.
     * @throws DeviceNotAvailableException
     */
    protected boolean validatePairingStarted(ITestDevice device)
            throws DeviceNotAvailableException {
        String outputStr = device.executeShellCommand(CW_VALIDATE_CMD);
        mTestFailureReason = "Device did not start syncing after pairing";
        if (outputStr.contains(MAIN_ACTIVITY) || outputStr.contains(PAIR_ACTIVITY)) {
            CLog.d("Clockwork syncing is not started yet");
            return false;
        }

        return true;
    }

    /**
     * Validate the pairing is ready and watch enter discoverable mode by polling the system using
     * stack list command
     *
     * @param device
     * @return True if device is in discoverable in pair_activity or main_activity. False otherwise.
     * @throws DeviceNotAvailableException
     */
    protected boolean validatePairingIsReady(ITestDevice device)
            throws DeviceNotAvailableException {
        String outputStr = device.executeShellCommand(CW_VALIDATE_CMD);
        if (outputStr.contains(PAIR_ACTIVITY) || outputStr.contains(MAIN_ACTIVITY)) {
            CLog.d("Clockwork is ready for pairing");
            return true;
        }
        mTestFailureReason = "Device is not ready for pairing, it is not in discoverable mode";
        return false;
    }

    /**
     * Start instrumentation command and parse the ourput
     *
     * @param device
     * @param packageName
     * @throws DeviceNotAvailableException
     */
    private boolean startInstrumentationCommand(ITestDevice device, String cmd)
            throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        CLog.d(cmd);
        device.executeShellCommand(cmd, receiver, CMD_TIME_OUT, TimeUnit.SECONDS, 0);
        String output = receiver.getOutput();
        CLog.d(output);
        return output.contains(SUCCESS_INSTRUMENTATION_OUTPUT);
    }

    /**
     * Clears all BT pairings on device
     *
     * @param device
     * @throws DeviceNotAvailableException
     */
    private void resetDeviceBluetoothPairing(ITestDevice device)
            throws DeviceNotAvailableException {
        Assert.assertTrue(
                "failed to clear pairing for " + device.getSerialNumber(),
                BluetoothUtils.unpairWithRetry(device));
    }

    /**
     * Clears application data of the package specified
     *
     * @param device
     * @param packageName
     * @throws DeviceNotAvailableException
     */
    private boolean clearDataOnPackage(ITestDevice device, String packageName)
            throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        device.executeShellCommand(String.format(CLEAR_DATA_CMD, packageName), receiver);
        String output = receiver.getOutput();
        return output.contains(SUCCESS_CLEARDATA_OUTPUT);
    }

    private void disablePairingScreens(ITestDevice device) throws DeviceNotAvailableException {
        device.executeShellCommand(DISABLE_LGE_DOCKING);
        device.executeShellCommand(DISABLE_STURGEON_DOCKING);
        device.executeShellCommand(DISABLE_NUMBER_SYNC);
        device.executeShellCommand(DISABLE_WIFI_OPT_IN_DIALOG);
    }

    /**
     * Clears clockwork BT pairing, and in stress mode, re-enable setup component, and reboot device
     *
     * @throws DeviceNotAvailableException
     * @throws ConfigurationException
     * @throws TargetSetupError
     */
    private void resetClockwork(ITestDevice device) throws DeviceNotAvailableException {
        // performs a wipe of userdata an cache
        CLog.v("Beging to factory default " + device.getSerialNumber());
        device.rebootIntoBootloader();
        CommandResult result = device.executeFastbootCommand(WIPE_TIMEOUT, "-w");
        CLog.v(
                String.format(
                        "Wipe %s userdata - stdout: %s stderr: %s",
                        device.getSerialNumber(), result.getStdout(), result.getStderr()));
        if (result.getStatus() != CommandStatus.SUCCESS || result.getStderr().contains("FAILED")) {
            CLog.e(
                    String.format(
                            "fastboot command %s failed in device %s. stdout: %s, stderr: %s",
                            "-w", device.getSerialNumber(), result.getStdout(), result.getStderr()),
                    device.getDeviceDescriptor());
        }
        device.executeFastbootCommand("reboot");
        device.waitForDeviceAvailable();
        device.enableAdbRoot();
        device.executeShellCommand("svc power stayon true");
        device.executeShellCommand(DISABLE_NUMBER_SYNC);
    }

    /**
     * Clears companion BT pairing, and in stress mode, reboot device
     *
     * @throws DeviceNotAvailableException
     */
    private void resetCompanion(ITestDevice device) throws DeviceNotAvailableException {
        resetDeviceBluetoothPairing(device);
        Assert.assertTrue(
                "failed to clear data for companion app", clearDataOnPackage(device, WEAR_APP_PKG));
        Assert.assertTrue(
                "failed to clear data for GMS core", clearDataOnPackage(device, GMS_CORE_PKG));
        mHelper.clearAllNotifications(device);
    }

    /**
     * Clean up after pairing complete
     *
     * @throws DeviceNotAvailableException
     */
    private void cleanupClockwork(ITestDevice device) throws DeviceNotAvailableException {
        // send out the intent to disable tutorial regardless of pass/fail, in case that
        // the pairing did work but we failed to correctly detect the state
        if (device.getApiLevel() < 24) {
            device.executeShellCommand(DISABLE_TUTORIAL_E);
        } else {
            device.executeShellCommand(DISABLE_TUTORIAL_F);
        }
    }

    /**
     * Getting bluetooth mac address from device
     *
     * @throws DeviceNotAvailableException
     */
    private String getBluetoothAddress(ITestDevice device) throws DeviceNotAvailableException {
        if (device.getApiLevel() >= 24) {
            return device.executeShellCommand(SHELL_GET_BLUETOOTH_ADDRESS).trim();
        } else {
            return device.executeShellCommand(SHELL_GET_BLUETOOTH_ADDRESS_E).trim();
        }
    }

    /**
     * Pairing the watch and companion support both single device and multi device TF.
     *
     * @return Elapsed time for pairing.
     */
    public long pair(
            ITestDevice watchDevice,
            ITestDevice companionDevice,
            boolean cloudSync,
            boolean factoryDefault,
            boolean toggleBt,
            int i)
            throws DeviceNotAvailableException, InterruptedException {
        mHelper = new ConnectivityHelper();
        mDevice = watchDevice;
        long pairingDuration = -1;
        String watchModel = mHelper.getModelName(watchDevice);
        String phoneModel = mHelper.getModelName(companionDevice);
        CLog.d("We will pair %s with %s", watchModel, phoneModel);
        // Enable bluetooth and get mac address
        String deviceBluetoothMac = getBluetoothAddress(watchDevice);
        Assert.assertNotNull("no bluetooth device address detected", deviceBluetoothMac);
        if (!mHelper.bluetoothEnabled(companionDevice)) {
            Assert.assertTrue(
                    "failed to enable bluetooth on companion",
                    BluetoothUtils.enable(companionDevice));
        }

        // before test starts, reset devices on both sides once
        CLog.d("resetting both devices before test");
        if (factoryDefault) {
            resetClockwork(watchDevice);
        } else {
            CLog.d("Skipping factory default on %s", watchDevice.getSerialNumber());
        }
        resetCompanion(companionDevice);
        long currentThroughput = 0;
        String pairingCommand = String.format(COMPANION_PAIR_CMD, deviceBluetoothMac);
        if (cloudSync) {
            pairingCommand += CLOUDSYNC_CMD;
        }

        CLog.d("Disable some screens");
        disablePairingScreens(watchDevice);

        long start = System.currentTimeMillis();
        mHelper.logMsg(watchDevice, companionDevice, "Starting pairing commands");

        boolean testPassed = false;
        // start pairing on clockwork with default locale
        testPassed =
                mHelper.cmdValidate(
                        watchDevice,
                        START_PAIRING_ON_CLOCKWORK,
                        PAIRING_START_TIMEOUT,
                        PAIRING_VALIDATE_INTERVAL,
                        mValidatePairingIsReady);

        // Issue pair command to companion
        mHelper.logMsg(watchDevice, companionDevice, "Launch companion app on phone");
        startInstrumentationCommand(companionDevice, pairingCommand);
        long teststart = System.currentTimeMillis();
        // Press pair button as needed
        // UI automation for companion will take long time to run
        mHelper.logMsg(watchDevice, companionDevice, "Launch UIAutomation on phone");
        testPassed =
                startInstrumentationCommand(
                        companionDevice, String.format(PHONE_PAIR_BUTTON, watchModel));

        if (testPassed) {
            testPassed =
                    mHelper.cmdValidate(
                            watchDevice,
                            null,
                            PAIRING_START_TIMEOUT,
                            PAIRING_VALIDATE_INTERVAL,
                            mValidatePairingStarted);
        }
        if (testPassed) {
            mHelper.logMsg(watchDevice, companionDevice, "Companion app finished pairing");
            if (toggleBt) {
                mHelper.logMsg(
                        watchDevice, companionDevice, "Start toggling bluetooth during pairing");
                mHelper.airplaneModeOn(watchDevice, true);
                RunUtil.getDefault().sleep(TOGGLE_BT_TIME * 1000);
                mHelper.airplaneModeOn(watchDevice, false);
            }
            testPassed =
                    mHelper.cmdValidate(
                            watchDevice,
                            null,
                            MAX_PAIRING_TIMEOUT,
                            PAIRING_VALIDATE_INTERVAL,
                            mValidatePairing);
        }
        long current = System.currentTimeMillis();
        pairingDuration = current - teststart;
        cleanupClockwork(watchDevice);

        if (testPassed) {
            mHelper.logMsg(watchDevice, companionDevice, "Pairing has finished");
            boolean cloudSyncPassed = true;
            // If cloud sync is enabled, we need to wait until cloud network id is synced
            if (cloudSync) {
                if (ConnectivityHelper.getCloudNetworkId(watchDevice, NETWORK_SYNC_TIMEOUT)
                        == null) {
                    mHelper.logMsg(watchDevice, companionDevice, "Cloud Network ID check failed");
                    //captureLogs(listener, i);
                    return pairingDuration;
                }
            }
            mHelper.logMsg(
                    watchDevice,
                    companionDevice,
                    String.format("Pairing is done for iteration %d", i));
            CLog.d("clockwork pairing done, duration = %s", pairingDuration);
        } else {
            CLog.e(mTestFailureReason);
            //captureLogs(listener, i);
            return -1;
        }
        return pairingDuration;
    }

    private void logMsg(ITestDevice watchDevice, ITestDevice companionDevice, String msg)
            throws DeviceNotAvailableException {
        CLog.d(msg);
        mHelper.logcatInfo(watchDevice, "PAIRING_UTIL", "i", msg);
        mHelper.logcatInfo(companionDevice, "PAIRING_UTIL", "i", msg);
    }
}
