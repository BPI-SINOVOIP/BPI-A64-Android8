// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.BluetoothUtils;
import com.android.tradefed.util.RunUtil;

import com.google.android.tradefed.testtype.ClockworkTest;

import org.junit.Assert;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Automatic test with UIAutomation for BT pairing between clockwork and companion device
 *
 * <p>Also supports a one shot mode used for initial pairing as test setup
 */
@OptionClass(alias = "pairing-ui")
public class PairingWithUI extends ClockworkTest {

    private static final String SUCCESS_CLEARDATA_OUTPUT = "Success";
    private static final String UI_AUTOMATION_PKG_NAME =
            "com.android.test.uiautomator.platform.clockwork";
    private static final String UI_AUTOMATION_RUNNER_NAME =
            "android.support.test.runner.AndroidJUnitRunner";
    private static final String UI_AUTOMATION_CLASS_NAME =
            "com.android.test.uiautomator.platform.clockwork.%s";
    private static final String APP_INSTALL_PKG_NAME = "com.android.test.uiautomator.aupt";
    private static final String APP_INSTALL_RUNNER_NAME =
            "android.support.test.aupt.AuptTestRunner";
    private static final String APP_INSTALL_CLASS_NAME =
            "com.android.test.uiautomator.aupt.PlayStoreTest";
    private static final String DISABLE_NUMBER_SYNC =
            "pm disable com.google.android.apps.wearable.numbersync";
    private static final String START_PAIRING_ON_CLOCKWORK =
            "am broadcast -a com.google.android.clockwork.action.START_PAIRING";
    protected static final String DISABLE_TUTORIAL_E =
            "am startservice -a com.google.android.clockwork.action.TUTORIAL_SKIP";
    protected static final String DISABLE_TUTORIAL_F =
            "am broadcast -a com.google.android.clockwork.action.TUTORIAL_SKIP";
    private static final String COMPANION_VERSION =
            "dumpsys package com.google.android.wearable.app | grep versionName";
    private static final String CW_VALIDATE_CMD = "am stack list";
    private static final String COMPANION_PAIR_CMD =
            "am start -n "
                    + "com.google.android.wearable.app/"
                    + "com.google.android.clockwork.companion.StatusActivity";
    private static final String[] NETWORK_DEBUG_CMDS = {
        "setprop 'log.tag.ClockworkProxy' 'VERBOSE'",
        "setprop 'log.tag.ClockworkProxyService' 'VERBOSE'",
        "setprop 'log.tag.TcpSocketIoHandler' 'VERBOSE'",
        "setprop 'log.tag.UdpRelayingThread' 'VERBOSE'",
        "setprop 'log.tag.bt_btm_sec' 'VERBOSE'",
        "setprop 'log.tag.bt_bta_dm' 'VERBOSE'",
    };
    private static final String WATCH_CONFIG_NAME = "watch";
    private static final String COMPANION_CONFIG_NAME = "companion";
    private static final String SINGLE_ITR_TEST_SUCCESS_OUTPUT = "OK (1 test)";
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
    private static final String PAIR_ACTIVITY =
            "com.google.android.wearable.setupwizard.steps.pair.PairActivity";
    private static final String PHONE_VIDEO_NAME = "phone_pairing";
    private static final String WATCH_VIDEO_NAME = "watch_pairing";
    private static final String PLAYSTORE_INSTR =
            "am instrument -w -r "
                    + "-e class com.android.test.uiautomator.aupt.PlayStoreTest "
                    + "-e shortSleep %d -e shuffle true -e iterations 1 -e disable_ime true "
                    + "-e app %s -e longSleep 30000 -e outputLocation aupt-output "
                    + "com.android.test.uiautomator.aupt/android.support.test.aupt.AuptTestRunner";

    private String mDeviceBluetoothMac;
    private long mPairingTotal;
    private long mBtThroughputTotal;
    private int mPairingSuccess;
    private ConnectivityHelper mHelper;
    private String mTestFailureReason = "";

    private ITestDevice mWatchDevice;
    private ITestDevice mCompanionDevice;
    private IBuildInfo mWatchBuildInfo;
    private IBuildInfo mCompanionBuildInfo;

    @Option(name = "password", description = "Account password for Google account.")
    private String mPassword = null;

    @Option(name = "network-debug", description = "enable network debugging during pairing")
    private boolean mNetworkDebug = false;

    @Option(
        name = "max-pairing-timeout",
        description = "max timeout for pairing, in seconds, " + "defaults to 60"
    )
    protected long mMaxPairingTimeout = 60;

    @Option(
        name = "pairing-start-timeout",
        description = "Max timeout for pairing to start in " + "seconds, defaults to 30"
    )
    protected long mPairingStartTimeout = 30;

    @Option(
        name = "class-name",
        description =
                "The apk class name for UI automation, "
                        + "defaults to DeviceCompanionPairing, can be DevicePairingRetry for retry version"
    )
    protected String mClassName = "DeviceCompanionPairing";

    @Option(
        name = "log-btsnoop",
        description = "Record the btsnoop trace. Works with the bluedroid " + "stack."
    )
    private boolean mLogBtsnoop = false;

    @Option(name = "screen-recording", description = "Record the UIAutomation screen")
    private boolean mScreenRecord = true;

    @Option(
        name = "playstore-short-sleep",
        description = "Sleep time for playstore download in secs"
    )
    private long mShortSleep = 10;

    @Option(name = "app-name", description = "App that we install on watch")
    private String mAppName = "runkeeper";

    @Option(name = "ota", description = "Expect getting 0-day OTA during pairing")
    private boolean mOtaExpected = false;

    @Option(name = "test-run-name", description = "Test run name for different reports")
    protected String mTestRunName = "DefaultUiPairingName";

    @Option(name = "disable-docking-cmd", description = "Command to disable charge screen")
    protected String mDisableDocking = null;

    @Option(
        name = "post-pair-sleep",
        description = "Extra time (in seconds) to sleep after pairing."
    )
    private int mPostPairSleep = 0;

    @Option(name = "post-pair-playstore-test", description = "Run playstore test after pairing")
    private boolean mPostPairPlayStoreTest = true;

    /** Call pairing validation */
    private Callable<Boolean> mValidatePairing =
            new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return validatePairing();
                }
            };

    /** Validate pairing is ready */
    private Callable<Boolean> mValidatePairingIsReady =
            new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return validatePairingIsReady();
                }
            };

    /**
     * Validate the pairing is finished by polling the system using stack list command
     *
     * @param device
     * @throws DeviceNotAvailableException
     */
    protected boolean validatePairing() throws DeviceNotAvailableException {
        String outputStr = mWatchDevice.executeShellCommand(CW_VALIDATE_CMD);
        if (outputStr.contains(HOME_ACTIVITY) || outputStr.contains(EMERALD_HOME_ACTIVITY)) {
            CLog.d("Clockwork pairing is done");
            return true;
        } else if (outputStr.contains(MAIN_ACTIVITY)) {
            CLog.d("Clockwork syncing is not started yet");
        } else {
            CLog.d("Clockwork is still syncing");
        }
        mTestFailureReason = "Device did not finish syncing and reach home actvitiy";
        return false;
    }

    /**
     * Validate the pairing is ready and watch enter discoverble mode by polling the system using
     * stack list command
     *
     * @param device
     * @return True if device is in discoverble in pair_activity or main_activity
     * @throws DeviceNotAvailableException
     */
    protected boolean validatePairingIsReady() throws DeviceNotAvailableException {
        String outputStr = mWatchDevice.executeShellCommand(CW_VALIDATE_CMD);
        if (outputStr.contains(PAIR_ACTIVITY) || outputStr.contains(MAIN_ACTIVITY)) {
            CLog.d("Clockwork is ready for pairing");
            return true;
        }
        mTestFailureReason = "Device is not ready for pairing, it is not in discoverble mode";
        return false;
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
        device.executeShellCommand(DISABLE_NUMBER_SYNC);
        if (mDisableDocking != null) {
            device.executeShellCommand(mDisableDocking);
        }
    }

    /**
     * Create the bluetooth pairing name regexp pattern based last 4 chars of serial number
     *
     * @param device
     * @throws DeviceNotAvailableException
     */
    private String getBtName(ITestDevice device) throws DeviceNotAvailableException {
        String serialNumber = device.getSerialNumber();
        int startIndex = serialNumber.length() - 4;
        String btname = ".+" + serialNumber.substring(startIndex).toUpperCase();
        return btname;
    }

    /** {@inheritDoc} */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mHelper = new ConnectivityHelper();
        // A helper only used for watch video recording
        ConnectivityHelper watchHelper = new ConnectivityHelper();

        mWatchDevice = getDevice();
        mWatchBuildInfo = getInfoMap().get(mWatchDevice);

        mCompanionDevice = getCompanion();
        mCompanionBuildInfo = getInfoMap().get(mCompanionDevice);

        String expectedBuildId = mWatchBuildInfo.getBuildId();
        String currentBuild = getDevice().getBuildId();
        if (mOtaExpected) {
            CLog.d("Current build: %s, OTA build: %s", currentBuild, expectedBuildId);
            Assert.assertFalse(
                    "Current build id is the same as the OTA build id before test begin",
                    expectedBuildId.equals(currentBuild));
            // For OTA case, device is not flashed/wiped, so we need to factory defaulted
            mHelper.resetClockwork(getDevice());
        }
        if (mLogBtsnoop) {
            CLog.d("Enable snoop log on companion");
            Assert.assertTrue(
                    "failed to enable hcisnoop log on companion",
                    BluetoothUtils.enableBtsnoopLogging(mCompanionDevice));
            CLog.d("Enable snoop log on clock works");
            Assert.assertTrue(
                    "failed to enable hcisnoop on clockwork",
                    BluetoothUtils.enableBtsnoopLogging(mWatchDevice));
        }

        int success = 0;
        listener.testRunStarted(mTestRunName, 0);
        TestIdentifier id =
                new TestIdentifier(getClass().getCanonicalName(), String.format("iteration%d", 0));
        CLog.d("Starting iteration %d", 0);
        listener.testStarted(id);
        long start = System.currentTimeMillis();
        boolean testPassed = false;

        // Get bluetooth name
        String btname = getBtName(mWatchDevice);
        CLog.d(String.format("Bluetooth search name is %s", btname));
        // Get model name
        String modelName = mHelper.getModelName(mWatchDevice);
        CLog.d(String.format("Model name is '%s'", modelName));

        // Clear packages data
        resetDeviceBluetoothPairing(mCompanionDevice);
        Assert.assertTrue(
                "failed to clear data for companion app",
                clearDataOnPackage(mCompanionDevice, WEAR_APP_PKG));
        Assert.assertTrue(
                "failed to clear data for GMS core",
                clearDataOnPackage(mCompanionDevice, GMS_CORE_PKG));
        // start pairing on clockwork with default locale
        try {
            CLog.d("Enable pairing on the watch side");
            //Todo, use UI automation or similar command to replace test hook here
            testPassed =
                    mHelper.cmdValidate(
                            mWatchDevice,
                            START_PAIRING_ON_CLOCKWORK,
                            mPairingStartTimeout,
                            PAIRING_VALIDATE_INTERVAL,
                            mValidatePairingIsReady);
            if (testPassed) {
                // disable pairing screens
                disablePairingScreens(mWatchDevice);
                //Clear all companion notifications
                mHelper.clearAllNotifications(mCompanionDevice);
                mCompanionDevice.clearErrorDialogs();
                CLog.d("Launch companion app on phone");
                mCompanionDevice.executeShellCommand(COMPANION_PAIR_CMD);
                if (mScreenRecord) {
                    mHelper.startScreenRecording(mCompanionDevice, PHONE_VIDEO_NAME);
                }
                CLog.d("Run UIAutomation on phone");
                IRemoteAndroidTestRunner runner =
                        new RemoteAndroidTestRunner(
                                UI_AUTOMATION_PKG_NAME,
                                UI_AUTOMATION_RUNNER_NAME,
                                mCompanionDevice.getIDevice());
                runner.setClassName(String.format(UI_AUTOMATION_CLASS_NAME, mClassName));
                runner.setRunName(mTestRunName);
                runner.setMaxTimeToOutputResponse(CMD_TIME_OUT, TimeUnit.SECONDS);
                BugreportCollector bugListener = new BugreportCollector(listener, mCompanionDevice);
                bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
                runner.addInstrumentationArg("name", btname);
                runner.addInstrumentationArg("model", modelName);
                if (mPassword != null) {
                    runner.addInstrumentationArg("password", mPassword);
                }
                if (mOtaExpected) {
                    runner.addInstrumentationArg("ota", WATCH_CONFIG_NAME);
                }
                testPassed = mCompanionDevice.runInstrumentationTests(runner, bugListener);
                if (mScreenRecord) {
                    RunUtil.getDefault().sleep(mShortSleep * 1000);
                    mHelper.stopScreenRecording();
                }
                if (testPassed) {
                    // Check make sure Watch is ready as well.
                    testPassed =
                            mHelper.cmdValidate(
                                    mWatchDevice,
                                    null,
                                    mMaxPairingTimeout,
                                    PAIRING_VALIDATE_INTERVAL,
                                    mValidatePairing);
                } else {
                    CLog.e("UI Automation failed");
                }
            } else {
                CLog.e("Pairing is not ready");
            }
        } catch (Exception e) {
            CLog.e(e);
            throw new RuntimeException(e);
        }
        if (!testPassed) {
            listener.testFailed(id, mTestFailureReason);
            if (mScreenRecord) {
                mHelper.uploadScreenRecording(mCompanionDevice, listener, PHONE_VIDEO_NAME);
            }
            mHelper.captureLogs(listener, 0, WATCH_CONFIG_NAME, mWatchDevice);
            mHelper.captureLogs(listener, 0, COMPANION_CONFIG_NAME, mCompanionDevice);
            if (mLogBtsnoop) {
                BluetoothUtils.uploadLogFiles(listener, mWatchDevice, "watch", 0);
                BluetoothUtils.uploadLogFiles(listener, mCompanionDevice, "phone", 0);
            }
            if (mClassName.equals("DevicePairingRetry")) {
                throw new RuntimeException("DevicePairingRetry failed on pairing");
            }
        } else if (mPassword == null) {
            if (!mHelper.validateTutorialViaDumpsys(getDevice(), CMD_TIME_OUT)) {
                listener.testFailed(id, "Tutorial did not show up");
            } else {
                CLog.d("App installation is skipped because account sync is skipped.");
                if (mScreenRecord) {
                    mHelper.removeScreenRecording(mCompanionDevice, PHONE_VIDEO_NAME);
                }
                success++;
            }
        } else if (mPostPairPlayStoreTest) {
            if (mScreenRecord) {
                mHelper.removeScreenRecording(mCompanionDevice, PHONE_VIDEO_NAME);
            }
            cleanupClockwork(getDevice());
            RunUtil.getDefault().sleep(60 * 1000);
            CLog.d("Run Playstore installation test on watch to prove account sync is finished");
            if (mScreenRecord) {
                watchHelper.startScreenRecording(mWatchDevice, WATCH_VIDEO_NAME);
            }
            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
            getDevice()
                    .executeShellCommand(
                            String.format(PLAYSTORE_INSTR, mShortSleep * 1000, mAppName),
                            receiver,
                            CMD_TIME_OUT,
                            TimeUnit.SECONDS,
                            0);
            String buffer = receiver.getOutput();
            CLog.d("PlayStoreTest output:");
            CLog.d(buffer);
            testPassed = buffer.contains(SINGLE_ITR_TEST_SUCCESS_OUTPUT);
            if (mScreenRecord) {
                RunUtil.getDefault().sleep(mShortSleep * 1000);
                watchHelper.stopScreenRecording();
                watchHelper.uploadScreenRecording(mWatchDevice, listener, WATCH_VIDEO_NAME);
            }
            if (testPassed) {
                CLog.d("App installation from playstore passed");
                success++;
            } else {
                listener.testFailed(id, buffer);
            }
        }
        Map<String, String> metrics = new HashMap<String, String>();
        String newBuild = getDevice().getBuildId();
        if (mOtaExpected) {
            metrics.put("previousBuild", currentBuild);
            metrics.put("latestBuild", newBuild);
            metrics.put("expectedBuild", expectedBuildId);
            CLog.d("After pairing current build: %s, OTA build: %s", newBuild, expectedBuildId);
            if (expectedBuildId.equals(newBuild)) {
                CLog.d("New watch build is matching OTA build");
            } else {
                success = 0;
                listener.testFailed(id, "New watch build is not matching OTA build");
            }
        }
        mHelper.captureLogs(listener, 1, WATCH_CONFIG_NAME, mWatchDevice);
        mHelper.captureLogs(listener, 1, COMPANION_CONFIG_NAME, mCompanionDevice);

        // prepare test results for reporting
        metrics.put("success", Integer.toString(success));
        metrics.put("phoneBuild", mCompanionDevice.getBuildId());
        metrics.put("Companion Version", getCompanionVersion(mCompanionDevice));
        CLog.d("Done! success = %d, version = %s", success, getCompanionVersion(mCompanionDevice));
        listener.testRunEnded(System.currentTimeMillis() - start, metrics);
        if (mLogBtsnoop) {
            BluetoothUtils.disableBtsnoopLogging(mCompanionDevice);
            BluetoothUtils.disableBtsnoopLogging(mWatchDevice);
        }

        CLog.i("Sleep for %d seconds.", mPostPairSleep);
        RunUtil.getDefault().sleep(mPostPairSleep * 1000);
    }

    private void logMsg(String msg) throws DeviceNotAvailableException {
        mHelper.logcatInfo(mWatchDevice, mTestRunName, "i", msg);
        mHelper.logcatInfo(mCompanionDevice, mTestRunName, "i", msg);
    }

    /**
     * Clean up after pairing complete
     *
     * @throws DeviceNotAvailableException
     */
    private void cleanupClockwork(ITestDevice device) throws DeviceNotAvailableException {
        // send out the intent to disable tutorial regardless of pass/fail, in case that
        // the pairing did work but we failed to correctly detect the state
        CLog.d("Disable tutorial");
        if (device.getApiLevel() < 24) {
            device.executeShellCommand(DISABLE_TUTORIAL_E);
        } else {
            device.executeShellCommand(DISABLE_TUTORIAL_F);
        }
    }

    private String getCompanionVersion(ITestDevice device) throws DeviceNotAvailableException {
        String companionVersion = device.executeShellCommand(COMPANION_VERSION);
        String[] versionItems = companionVersion.split("=")[1].split("\\.");
        String companionVersionMetric = "";
        if (versionItems.length == 4) {
            companionVersionMetric =
                    versionItems[0] + versionItems[1] + versionItems[2] + versionItems[3];
        }
        return companionVersionMetric;
    }
}
