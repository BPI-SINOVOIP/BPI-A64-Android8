// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.BluetoothUtils;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.DeviceConcurrentUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.junit.Assert;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Stress test harness for BT pairing between clockwork and companion device
 *
 * <p>Also supports a one shot mode used for initial pairing as test setup
 */
@OptionClass(alias = "pairing-stress")
public class PairingStress extends ClockworkConnectivityTest implements IBuildReceiver {

    private static final String SUCCESS_CLEARDATA_OUTPUT = "Success";
    protected static final String DISABLE_TUTORIAL_E =
            "am startservice -a com.google.android.clockwork.action.TUTORIAL_SKIP";
    protected static final String DISABLE_TUTORIAL_F =
            "am broadcast -a com.google.android.clockwork.action.TUTORIAL_SKIP";
    private static final String DISABLE_WIFI_OPT_IN_DIALOG =
            "pm disable com.google.android.wearable.app/"
                    + "com.google.android.clockwork.home.cloudsync.CloudSyncOptInService";
    private static final String DISABLE_LGE_DOCKING =
            "pm disable com.lge.wearable.chargingmode/"
                    + "com.lge.wearable.chargingmode.ChargingActivity";
    private static final String DISABLE_MOTO_DOCKING =
            "pm disable com.motorola.targetnotif/" + "com.motorola.loop.ChargeActivity";
    private static final String DISABLE_GRANT_DOCKING =
            "pm disable fossil.com.charge/" + "fossil.com.charge.MainActivity";
    private static final String DISABLE_NUMBER_SYNC =
            "pm disable com.google.android.apps.wearable.numbersync";
    private static final String DISABLE_STURGEON_DOCKING =
            "pm disable com.huawei.mercury.chargedetection";
    private static final String START_PAIRING_ON_CLOCKWORK =
            "am broadcast -a com.google.android.clockwork.action.START_PAIRING";
    private static final String CW_VALIDATE_CMD = "am stack list";
    private static final String COMPANION_PAIR_CMD =
            "am start -n "
                    + "com.google.android.wearable.app/"
                    + "com.google.android.clockwork.companion.StatusActivity -e EXTRA_AUTO_PAIR \"%s\"";
    private static final String CLOUDSYNC_CMD = " --ez EXTRA_OPT_INTO_CLOUD_SYNC true";
    protected static final String UI_AUTOMATION_PKG_NAME =
            "com.android.test.uiautomator.platform.clockwork";
    protected static final String UI_AUTOMATION_RUNNER_NAME =
            "android.support.test.runner.AndroidJUnitRunner";
    protected static final String UI_AUTOMATION_CLASS_NAME =
            "com.android.test.uiautomator.platform.clockwork.%s";
    protected static final String UI_AUTOMATION_METHOD_NAME = "DevicePairingRetry";
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
    protected static final long PAIRING_VALIDATE_INTERVAL = 10;
    private static final int CMD_TIME_OUT = 1000;
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

    private ITestDevice mDevice;
    private String mDeviceBluetoothMac;
    private long mPairingTotal;
    private long mBtThroughputTotal;
    private int mPairingSuccess;
    private IBuildInfo mBuildInfo;

    @Option(name = "iteration", description = "number of repetitions for pairing test")
    private int mIteration = 1;

    @Option(
        name = "one-shot",
        description =
                "perform a one time pair, instead of stress mode; "
                        + "used for pairing setup, iteration parameter will be ignored if specied"
    )
    private boolean mOneShot = false;

    @Option(name = "network-debug", description = "enable network debugging during pairing")
    private boolean mNetworkDebug = false;

    @Option(
        name = "max-pairing-timeout",
        description = "max timeout for pairing, in seconds, " + "defaults to 1200"
    )
    protected long mMaxPairingTimeout = 20 * 60;

    @Option(
        name = "cloud-networksync-timeout",
        description = "Timeout for cloud network sync, " + "in secs, defaults to 900"
    )
    protected long mCloudNetworkSyncTimeout = 30 * 60;

    @Option(
        name = "androidid-check",
        description =
                "An option to check android id, "
                        + "for release that supports standalone, default true"
    )
    protected boolean mAndroidIdCheck = true;

    @Option(
        name = "pairing-start-timeout",
        description = "Max timeout for pairing to start in " + "seconds, defaults to 120"
    )
    protected long mPairingStartTimeout = 2 * 60;

    @Option(
        name = "clock-wipe-timeout",
        description = "max timeout for wipe, in seconds, " + "defaults to 1800"
    )
    private long mClockWipeTimeout = 20 * 60;

    @Option(
        name = "max-reboot-timeout",
        description = "max timeout for reboot, in seconds, " + "defaults to 180"
    )
    private long mMaxRebootTimeout = 3 * 60;

    @Option(
        name = "delay-between-iteration",
        description = "Time delay between iteration, in " + "seconds, defaults to 10"
    )
    private long mDelayBetweenIteration = 10;

    @Option(
        name = "log-btsnoop",
        description = "Record the btsnoop trace. Works with the bluedroid " + "stack."
    )
    private boolean mLogBtsnoop = false;

    @Option(name = "toggle-bt", description = "Toggle bluetooth after pairing started.")
    private boolean mToggleBt = false;

    @Option(
        name = "toggle-bt-time",
        description = "Toggle bluetooth time after pairing started, " + "default 3 secs."
    )
    private int mToggleBtTime = 3;

    @Option(name = "cloud-sync", description = "Enable cloudsync, default is disable")
    protected boolean mCloudsync = false;

    @Option(name = "test-run-name", description = "Test run name for different reports")
    protected String mTestRunName = "StressLoop";

    /** drives reset of companion for next round of pairing test */
    private Callable<Void> mCompanionReset =
            new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    resetCompanionPairing();
                    return null;
                }
            };

    /** drives reset of clockwork for next round of pairing test */
    private Callable<Void> mClockworkReset =
            new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    resetClockworkPairing();
                    return null;
                }
            };

    /** Call paring validation */
    private Callable<Boolean> mValidatePairing =
            new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return validatePairing(getDevice());
                }
            };

    /** Validate pairing is ready */
    private Callable<Boolean> mValidatePairingIsReady =
            new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return validatePairingIsReady(getDevice());
                }
            };

    /** Make sure pairing is started */
    private Callable<Boolean> mValidatePairingStarted =
            new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return validatePairingStarted(getDevice());
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
    /** {@inheritDoc} */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /** {@inheritDoc} */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
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

    /**
     * Clears clockwork BT pairing, and in stress mode, re-enable setup component, and reboot device
     *
     * @throws DeviceNotAvailableException
     * @throws ConfigurationException
     * @throws TargetSetupError
     */
    private void resetClockworkPairing()
            throws DeviceNotAvailableException, ConfigurationException, TargetSetupError {
        // clear parings first
        resetDeviceBluetoothPairing(getDevice());
        // assume device is already "clean" in one shot mode
        if (!mOneShot) {
            // performs a wipe of userdata an cache
            getDevice().rebootIntoBootloader();
            CommandResult result = getDevice().fastbootWipePartition("userdata");
            CLog.v(
                    String.format(
                            "format userdata - stdout: %s stderr: %s",
                            result.getStdout(), result.getStderr()));
            result = getDevice().fastbootWipePartition("cache");
            CLog.v(
                    String.format(
                            "format cache - stdout: %s stderr: %s",
                            result.getStdout(), result.getStderr()));
            getDevice().executeFastbootCommand("reboot");
            getDevice().waitForDeviceAvailable();
            // invokes the preparer to install dependent pieces since we have wiped the device
            TestAppInstallSetup install = new TestAppInstallSetup();
            OptionSetter setter = new OptionSetter(install);
            setter.setOptionValue("test-file-name", "BluetoothTests.apk");
            setter.setOptionValue("test-file-name", "GeppettoClockwork.apk");
            install.setUp(getDevice(), mBuildInfo);
            RunUtil.getDefault().sleep(POST_BOOT_IDLE * 1000);
            getDevice().enableAdbRoot();
            getDevice().executeShellCommand("svc power stayon true");
        }
        if (mNetworkDebug) {
            enableNetworkLogging(getDevice());
        }
    }

    /**
     * Clears companion BT pairing, and in stress mode, reboot device
     *
     * @throws DeviceNotAvailableException
     */
    private void resetCompanionPairing() throws DeviceNotAvailableException {
        resetDeviceBluetoothPairing(getCompanion());
        Assert.assertTrue(
                "failed to clear data for companion app",
                clearDataOnPackage(getCompanion(), WEAR_APP_PKG));
        Assert.assertTrue(
                "failed to clear data for GMS core",
                clearDataOnPackage(getCompanion(), GMS_CORE_PKG));
        getCompanion().reboot();
        RunUtil.getDefault().sleep(POST_BOOT_IDLE * 1000);
        if (mNetworkDebug) {
            enableNetworkLogging(getCompanion());
        }
    }

    /**
     * Convenience method for resetting devices in parallel
     *
     * @param svc {@link ExecutorService} responsible for running the tasks
     * @throws DeviceNotAvailableException
     * @throws TimeoutException
     */
    protected void resetDevices(ExecutorService svc)
            throws DeviceNotAvailableException, TimeoutException {
        Future<Void> resetClockwork = svc.submit(mClockworkReset);
        Future<Void> resetCompanion = svc.submit(mCompanionReset);
        mMaxRebootTimeout *= 1000;
        mClockWipeTimeout *= 1000;
        DeviceConcurrentUtil.joinFuture(
                "reset clockwork",
                resetClockwork,
                mOneShot ? mMaxRebootTimeout : mClockWipeTimeout);
        DeviceConcurrentUtil.joinFuture("reset companion", resetCompanion, mMaxRebootTimeout);
    }

    /**
     * Convenience method to enable network logging on device
     *
     * @param device
     * @throws DeviceNotAvailableException
     */
    private void enableNetworkLogging(ITestDevice device) throws DeviceNotAvailableException {
        for (String cmd : NETWORK_DEBUG_CMDS) {
            device.executeShellCommand(cmd);
        }
    }

    protected void disablePairingScreens(ITestDevice device) throws DeviceNotAvailableException {
        device.executeShellCommand(DISABLE_LGE_DOCKING);
        device.executeShellCommand(DISABLE_GRANT_DOCKING);
        device.executeShellCommand(DISABLE_MOTO_DOCKING);
        device.executeShellCommand(DISABLE_STURGEON_DOCKING);
        device.executeShellCommand(DISABLE_NUMBER_SYNC);
    }

    /** {@inheritDoc} */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // some initial setup
        mDeviceBluetoothMac = BluetoothUtils.getBluetoothMac(getDevice());
        Assert.assertNotNull("no bluetooth device address detected", mDeviceBluetoothMac);
        Assert.assertTrue(
                "failed to enable bluetooth on companion", BluetoothUtils.enable(getCompanion()));
        if (mLogBtsnoop) {
            CLog.d("Enable snoop log on companion");
            Assert.assertTrue(
                    "failed to enable hcisnoop log on companion",
                    BluetoothUtils.enableBtsnoopLogging(getCompanion()));
            CLog.d("Enable snoop log on clock works");
            Assert.assertTrue(
                    "failed to enable hcisnoop on clockwork",
                    BluetoothUtils.enableBtsnoopLogging(getDevice()));
        }
        mPairingSuccess = 0;
        mPairingTotal = 0;
        mBtThroughputTotal = 0;
        // If it is a one shot setup, we need to overwrite it, so it is not reported.
        if (mOneShot) {
            if (mCloudsync) {
                mTestRunName = "PairingSetupCloudSync";
            } else {
                mTestRunName = "PairingSetup";
            }
        }
        // initializes the executor service
        ExecutorService svc =
                Executors.newFixedThreadPool(2, new ThreadFactoryBuilder().setDaemon(true).build());
        // big try-finally block to ensure svc is shutdown
        try {
            // before test starts, reset devices on both sides once
            CLog.d("resetting bluetooth on both devices once before test");
            try {
                resetDevices(svc);
            } catch (TimeoutException te) {
                throw new RuntimeException("timeout during initial reset", te);
            }
            String model = getModelName(getDevice());
            long pairingDuration = 0;
            int success = 0;
            String pairingCommand = String.format(COMPANION_PAIR_CMD, mDeviceBluetoothMac);
            if (mCloudsync) {
                pairingCommand += CLOUDSYNC_CMD;
            }
            long currentThroughput = 0;
            listener.testRunStarted(mTestRunName, mIteration);
            long start = System.currentTimeMillis();
            for (int i = 0; i < mIteration; i++) {
                TestIdentifier id =
                        new TestIdentifier(
                                getClass().getCanonicalName(), String.format("iteration%d", i));
                CLog.d("Starting iteration %d", i);
                listener.testStarted(id);
                CLog.d("Starting pairing commands");
                logMsg(String.format("String pairing iteration %d", i));
                boolean testPassed = false;
                // start pairing on clockwork with default locale
                testPassed =
                        cmdValidate(
                                getDevice(),
                                START_PAIRING_ON_CLOCKWORK,
                                mPairingStartTimeout,
                                PAIRING_VALIDATE_INTERVAL,
                                mValidatePairingIsReady);
                // disable pairing screens
                disablePairingScreens(getDevice());
                //Clear all companion notifications
                clearAllNotifications(getCompanion());
                // Issue pair command to companion
                getCompanion().executeShellCommand(pairingCommand);
                long teststart = System.currentTimeMillis();
                // Press pair button as needed
                // UI automation for companion will take long time to run
                CLog.d("Run UIAutomation on phone");
                IRemoteAndroidTestRunner runner =
                        new RemoteAndroidTestRunner(
                                UI_AUTOMATION_PKG_NAME,
                                UI_AUTOMATION_RUNNER_NAME,
                                getCompanion().getIDevice());
                runner.setClassName(
                        String.format(UI_AUTOMATION_CLASS_NAME, UI_AUTOMATION_METHOD_NAME));
                runner.setRunName(mTestRunName);
                runner.setMaxTimeToOutputResponse(CMD_TIME_OUT, TimeUnit.SECONDS);
                BugreportCollector bugListener = new BugreportCollector(listener, getCompanion());
                bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
                runner.addInstrumentationArg("model", model);
                testPassed = getCompanion().runInstrumentationTests(runner, bugListener);

                if (testPassed) {
                    testPassed =
                            cmdValidate(
                                    getDevice(),
                                    null,
                                    mPairingStartTimeout,
                                    PAIRING_VALIDATE_INTERVAL,
                                    mValidatePairingStarted);
                }
                if (testPassed) {
                    logMsg(String.format("Pairing started for iteration %d", i));
                    if (mToggleBt) {
                        CLog.d("Start toggling bluetooth during pairing");
                        BluetoothUtils.disable(getDevice());
                        if (mToggleBtTime > 0) {
                            RunUtil.getDefault().sleep(mToggleBtTime * 1000);
                        }
                        BluetoothUtils.enable(getDevice());
                    }
                    testPassed =
                            cmdValidate(
                                    getDevice(),
                                    null,
                                    mMaxPairingTimeout,
                                    PAIRING_VALIDATE_INTERVAL,
                                    mValidatePairing);
                }
                long current = System.currentTimeMillis();
                pairingDuration = current - teststart;
                // WiFi opt-in must be disabled on each iteration.
                CLog.d("Disable WiFi opt-in dialog");
                getDevice().executeShellCommand(DISABLE_WIFI_OPT_IN_DIALOG);
                // send out the intent to disable tutorial regardless of pass/fail, in case that
                // the pairing did work but we failed to correctly detect the state
                if (getDevice().getApiLevel() < 24) {
                    getDevice().executeShellCommand(DISABLE_TUTORIAL_E);
                } else {
                    getDevice().executeShellCommand(DISABLE_TUTORIAL_F);
                }
                if (testPassed) {
                    logMsg(String.format("Pairing finished for iteration %d", i));
                    boolean cloudSyncPassed = true;
                    // If cloud sync is enabled, we need to wait until cloud network id is synced
                    if (mCloudsync && mAndroidIdCheck) {
                        if (getCloudNetworkId(getDevice(), mCloudNetworkSyncTimeout) == null) {
                            listener.testFailed(id, "Timeout during cloud network id check");
                            captureLogs(listener, i);
                            if (mLogBtsnoop) {
                                BluetoothUtils.uploadLogFiles(listener, getDevice(), "watch", i);
                                BluetoothUtils.uploadLogFiles(listener, getCompanion(), "phone", i);
                            }
                            cloudSyncPassed = false;
                            if (mOneShot) {
                                throw new RuntimeException("Timeout during cloud network id check");
                            }
                        }
                    }
                    logMsg(String.format("Pairing is done for iteration %d", i));
                    CLog.d("clockwork pairing done, ret = %s", pairingDuration);
                    // calculate total pairing time so far
                    mPairingTotal += pairingDuration;
                    // TODO, the calculation and logic will move to ClockworkConnectivityTest
                    long[] btData =
                            ClockworkConnectivityTest.getWearableTransportConnectionStat(
                                    getDevice());
                    if (btData[2] > 0) {
                        currentThroughput = btData[1] / (btData[2] * 1024);
                        CLog.d(
                                "Current Bluetooth throughput is %d in interation %d",
                                currentThroughput, i);
                        mBtThroughputTotal += currentThroughput;
                    } else {
                        CLog.d("Duration is 0 for iteration %d", i);
                    }
                    if (cloudSyncPassed) {
                        mPairingSuccess++;
                        success++;
                    }
                } else {
                    listener.testFailed(id, mTestFailureReason);
                    captureLogs(listener, i);
                    if (mLogBtsnoop) {
                        BluetoothUtils.uploadLogFiles(listener, getDevice(), "watch", i);
                        BluetoothUtils.uploadLogFiles(listener, getCompanion(), "phone", i);
                    }
                    if (mOneShot) {
                        throw new RuntimeException(
                                "Pairing failed in one shot mode " + mTestFailureReason);
                    }
                }
                listener.testEnded(id, Collections.<String, String>emptyMap());
                if (mOneShot) {
                    // in one shot mode, break after first iteration, also before we reset device
                    CLog.d("one shot mode, bailing...");
                    break;
                }
                try {
                    resetDevices(svc);
                } catch (TimeoutException te) {
                    throw new RuntimeException("timeout during reset after test", te);
                }
                RunUtil.getDefault().sleep(mDelayBetweenIteration * 1000);
            }
            Map<String, String> metrics = new HashMap<String, String>();
            // prepare test results for reporting
            metrics.put("success", Integer.toString(success));
            CLog.d("all done! success = %d", success);
            if (mPairingSuccess != 0) {
                long avg = mPairingTotal / mPairingSuccess;
                long avgThroughput = mBtThroughputTotal / mPairingSuccess;
                CLog.d(
                        "clockwork pairing stats: avgtime = %d, avg throughput = %dk "
                                + "success = %d",
                        avg, avgThroughput, mPairingSuccess);
                metrics.put("avg-time", Long.toString(avg / 1000));
                metrics.put("avg-throughput", Long.toString(avgThroughput));
            }
            listener.testRunEnded(System.currentTimeMillis() - start, metrics);
        } catch (Exception e) {
            CLog.e(e);
            throw new RuntimeException(e);
        } finally {
            if (mLogBtsnoop) {
                BluetoothUtils.disableBtsnoopLogging(getCompanion());
                BluetoothUtils.disableBtsnoopLogging(getDevice());
            }
            svc.shutdown();
        }
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    private void logMsg(String msg) throws DeviceNotAvailableException {
        logcatInfo(getDevice(), "PAIRING_STRESS", "i", msg);
        logcatInfo(getCompanion(), "PAIRING_STRESS", "i", msg);
    }
}
