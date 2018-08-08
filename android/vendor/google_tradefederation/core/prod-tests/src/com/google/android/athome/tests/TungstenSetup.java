// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.athome.tests;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IAppBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.LogcatReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.testtype.UiAutomatorTest;
import com.android.tradefed.testtype.UiAutomatorTest.LoggingOption;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import com.google.android.tradefed.targetprep.GoogleDeviceSetup;

import org.junit.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A helper class that knows how to setup a tungsten from a phone controller.
 */
public class TungstenSetup {

    private static final String UIAUTOMATION_PKG = "com.android.testing.uiautomation";
    private static final String REMOTECONTROL_UI_TESTS_PKG_NAME =
            "com.google.android.remotecontrol.ui.tests";
    private static final String MUSIC_PKG_NAME = "com.google.android.music";
    private static final String REMOTECTRL_PKG_NAME = "com.google.android.setupwarlock";
    private static final String MUSIC_UI_TESTS_PKG_NAME = "com.google.android.music.ui.tests";
    private static final String REMOTECONTROL_UI_AUTOMATOR_TESTS_PATH = "RemoteControlUiTests.jar";
    private static final String REMOTECONTROL_UI_AUTOMATOR_TESTS_CLASS =
            "com.android.test.uiautomator.app.remotecontrol.TungstenSetupTest";

    /** name of app packages that must be installed on phone controller */
    private static final String[] PHONE_APP_PACKAGES = {
        MUSIC_PKG_NAME,
        REMOTECTRL_PKG_NAME,
    };

    /** name of test packages that must be installed on ICS-based phone controller */
    private static final String[] PHONE_TEST_PACKAGES = {
        UIAUTOMATION_PKG,
        REMOTECONTROL_UI_TESTS_PKG_NAME,
        MUSIC_UI_TESTS_PKG_NAME
    };

    // this must match tungsten location set in on-device TungstenSetupTest
    // TODO: don't hardcode this
    private static final String ATHOME_DEVICE_NAME = "Kitchen";

    private static final int BT_POLL_TIME = 10;
    // number of attempts to get the bluetooth name of tungsten, spaced BT_POLL_TIME sec apart
    private static final int BT_ATTEMPTS = 18;

    @Option(name = "wifi-password", description = "the wifi password to use.")
    private String mWifiPassword = null;

    @Option(name = "account", description = "the Google account pattern to use on the phone. " +
            "Only necessary if multiple accounts are configured. Default: @gmail.com.")
    private String mAccount = null;

    @Option(name = "reboot-phone", description = "flag for controlling if phone should be " +
            "rebooted before starting setup test.")
    private boolean mRebootPhone = true;

    @Option(name = "app-name", description = "the remote control application name as displayed " +
            "in UI.")
    private String mAppName = null;

    @Option(name = "product-name", description = "the product name of tungsten as displayed in UI.")
    private String mProductName = null;

    @Option(name = "phone-serial", description = "the optional serial(s) of the phone to " +
            "allocate for this test.")
    private Collection<String> mPhoneSerials = new ArrayList<String>();

    /**
     * Helper method to load a {@link TungstenSetup} from a config.
     * <p/>
     * The config should have a 'object type="tungsten_setup"
     * class="com.google.android.athome.tests.TungstenSetup>[options]' entry
     *
     * @param config
     * @return
     * @throws ConfigurationException
     */
    public static TungstenSetup loadSetup(IConfiguration config) throws ConfigurationException {
        Object o = config.getConfigurationObject("tungsten_setup");
        if (o instanceof TungstenSetup) {
            return (TungstenSetup)o;
        }
        throw new ConfigurationException("tungsten_setup specific missing or incorrect in config");
    }

    /**
     * Allocate a phone device.
     * @return
     */
    ITestDevice allocatePhone() {
        DeviceSelectionOptions selection = new TungstenFilter();
        for (String phoneSerial : mPhoneSerials) {
            selection.addSerial(phoneSerial);
        }

        ITestDevice phoneDevice = getDeviceManager().allocateDevice(selection);
        Assert.assertNotNull("Failed to allocate a phone device", phoneDevice);
        return phoneDevice;
    }

    /**
     * @param phoneDevice
     */
    void freePhoneDevice(ITestDevice phoneDevice) {
        getDeviceManager().freeDevice(phoneDevice, FreeDeviceState.AVAILABLE);
    }

    private IDeviceManager getDeviceManager() {
        return GlobalConfiguration.getDeviceManagerInstance();
    }

    /**
     * Make one setup attempt between phone and tungsten.
     * <p/>
     * Will capture logs on setup failures
     *
     * @param listener
     * @param phoneDevice
     * @param tungstenDevice
     * @param currentIteration
     * @return the setup test execution duration in ms, excluding any associated device preparation
     *         steps. <code>0</code> if setup failed.
     * @throws DeviceNotAvailableException
     */
    long performSetup(int currentIteration, ITestInvocationListener listener,
            ITestDevice phoneDevice, ITestDevice tungstenDevice)
            throws DeviceNotAvailableException {
        // create a separate logcat collector that collects logcat for just this iteration, and
        // doesn't disturb the ITestDevice.getLogcat collector that will collect logs for all
        // iterations
        LogcatReceiver logcatCollector = new LogcatReceiver(phoneDevice, 10 * 1024 * 1024, 0);
        logcatCollector.start();
        try {
            clearTungstenData(tungstenDevice);
            clearPhoneData(phoneDevice);

            checkTungstenState(tungstenDevice);

            String btAddress = getTungstenInfo(tungstenDevice);
            Assert.assertNotNull("Failed to find address for tungsten",
                    btAddress);

            checkTungstenState(tungstenDevice);

            // clear collector so only logs generated during setup are stored
            logcatCollector.clear();
            long startTime = System.currentTimeMillis();
            runSetupTests(phoneDevice, btAddress, currentIteration);
            long executionTime = System.currentTimeMillis() - startTime;
            CLog.logAndDisplay(LogLevel.INFO, "iteration %d: Success!", currentIteration);
            // capture phone logcat for data analysis
            takeLogcat("phone", currentIteration, logcatCollector, listener);
            return executionTime;
        } catch (AssertionError e) {
            sendFailureLogs(currentIteration, listener, phoneDevice, logcatCollector,
                    tungstenDevice, e);
            return 0;
        } finally {
            logcatCollector.stop();
        }
    }

    /**
     * Ensure tungsten is healthy and not in a system crash loop @see http://b/6362965
     *
     * @param tungstenDevice
     * @throws DeviceNotAvailableException
     */
    private void checkTungstenState(ITestDevice tungstenDevice) throws DeviceNotAvailableException {
        String logcat = tungstenDevice.executeShellCommand("logcat -v threadtime -d");
        Assert.assertFalse("Detected tungsten in possible system crash loop",
                logcat.contains("Exit zygote because system server"));
    }

    /**
     * Send the failure logs for both the phone and tungsten to the listener.
     *
     * @param listener the {@link ITestInvocationListener} to send logs to
     * @param phoneDevice the phone {@link ITestDevice}
     * @param logcatCollector
     * @param tungstenDevice the tungsten {@link ITestDevice}
     * @param e the {@link AssertionError}
     * @throws DeviceNotAvailableException
     */
    private void sendFailureLogs(
            int currentIteration,
            ITestInvocationListener listener,
            ITestDevice phoneDevice,
            LogcatReceiver logcatCollector,
            ITestDevice tungstenDevice,
            AssertionError e)
            throws DeviceNotAvailableException {
        CLog.logAndDisplay(LogLevel.WARN, "iteration %d: Setup failed: %s",
                currentIteration, e.getMessage());
        takeScreenshot(currentIteration, phoneDevice, listener);
        takeHierarchyDump(currentIteration, phoneDevice, listener);
        takeLogcat("phone", currentIteration, logcatCollector, listener);
        // TODO: is bugreport for phone still needed since logcat is being captured?
        takeBugreport("phone", currentIteration, phoneDevice, listener);
        takeBugreport("tungsten", currentIteration, tungstenDevice, listener);
    }

    /**
     * Prepare the phone for running setup tests.
     *
     * @param buildInfo the associated {@link IBuildInfo} that optionally contains the phone apks to
     *            install. IF this is not a {@link IAppBuildInfo}, the necessary apks must be
     *            pre-installed on the phone already.
     * @param phoneDevice the phone {@link ITestDevice}
     * @throws DeviceNotAvailableException
     */
    void preparePhone(IBuildInfo buildInfo, ITestDevice phoneDevice)
            throws DeviceNotAvailableException {
        if (buildInfo instanceof IAppBuildInfo) {
            for (String appPkg : PHONE_APP_PACKAGES) {
                phoneDevice.uninstallPackage(appPkg);
            }
            IAppBuildInfo appBuild = (IAppBuildInfo)buildInfo;
            for (VersionedFile apkFile : appBuild.getAppPackageFiles()) {
                String result = phoneDevice.installPackage(apkFile.getFile(), true);
                Assert.assertNull(
                        String.format("Failed to install %s: %s", apkFile.getFile().getName(),
                                result), result);
            }
        }
        if (mRebootPhone) {
            phoneDevice.reboot();
        }
        for (String appPkg : PHONE_APP_PACKAGES) {
            assertPkgInstalled(phoneDevice, appPkg);
        }
        if (!useGeppetto(phoneDevice)) {
            // test packages only needed for instrumentation runs
            for (String appPkg : PHONE_TEST_PACKAGES) {
                assertPkgInstalled(phoneDevice, appPkg);
            }
            // TODO: consider doing equivalent checks for geppetto packages
        }

        phoneDevice.enableAdbRoot();
    }

    /**
     * Determine if ICS/instrumentation based setup tests should be run, or JB/geppetto
     * based.
     * <p/>
     * Determined at runtime based on phone's API level.
     */
    private boolean useGeppetto(ITestDevice phoneDevice) throws DeviceNotAvailableException {
        String apiLevelString = phoneDevice.getProperty(IDevice.PROP_BUILD_API_LEVEL);
        try {
            int apiLevel = Integer.parseInt(apiLevelString);
            return apiLevel >= 16;
        } catch (NumberFormatException e) {
            CLog.w("Failed to parse api level %s from %s. Assuming > api 16.", apiLevelString,
                    phoneDevice.getSerialNumber());
        }
        return true;
    }

    /**
     * Determine the build id of the phone apks included in given build.
     *
     * @param buildInfo
     * @return the build id or <code>null</code> if it cannot be determined
     */
    static String getPhoneApkVersion(IBuildInfo buildInfo) {
        if (buildInfo instanceof IAppBuildInfo) {
            IAppBuildInfo appBuild = (IAppBuildInfo)buildInfo;
            // TODO: it should be enough in future to just grab version from first file. However,
            // currently some phone apks come from the platform build , so explicitly look for
            // version of RemoteControl apk
            for (VersionedFile fileEntry : appBuild.getAppPackageFiles()) {
                String fileName = fileEntry.getFile().getName();
                // don't want to confused with RemoteControlUiTests
                if (fileName.contains("RemoteControl") && !fileName.contains("Test")) {
                    return fileEntry.getVersion();
                }
            }
        }
        return null;
    }

    private void assertPkgInstalled(ITestDevice device, String pkgName)
            throws DeviceNotAvailableException {
        String result = device.executeShellCommand(String.format("pm path %s", pkgName));
        Assert.assertTrue(
                String.format("package %s is not installed on %s", pkgName,
                        device.getSerialNumber()), result.trim().length() > 0);
    }

    private void clearTungstenData(ITestDevice tungstenDevice) throws DeviceNotAvailableException {
        tungstenDevice.rebootIntoBootloader();
        for (int i = 0; i < 3; i++) {
            CommandResult result = tungstenDevice.executeFastbootCommand("oem",
                    "recovery:wipe_data");
            if (result.getStatus().equals(CommandStatus.SUCCESS)) {
                tungstenDevice.waitForDeviceOnline();
                setUpTungsten(tungstenDevice);
                return;
            }
        }
        Assert.fail("failed to run fastboot command after 3 attempts");
    }

    private void setUpTungsten(ITestDevice tungstenDevice) throws
            DeviceNotAvailableException {
        try {
            CLog.i("Setting up %s after wipe", tungstenDevice.getSerialNumber());
            // run setup so device has ro.test_harness set
            new GoogleDeviceSetup().setUp(tungstenDevice, new BuildInfo());
        } catch (TargetSetupError e) {
            CLog.e(e);
            Assert.fail("failed to setup tungsten");
        }
    }

    private void clearPhoneData(ITestDevice phoneDevice) throws DeviceNotAvailableException {
        CLog.i("Clearing phone data for music and remote control apps");
        Assert.assertTrue(clearAppData(phoneDevice, MUSIC_PKG_NAME));
        Assert.assertTrue(clearAppData(phoneDevice, REMOTECTRL_PKG_NAME));
    }

    private boolean clearAppData(ITestDevice phoneDevice, String pkgName)
            throws DeviceNotAvailableException {
        String result = phoneDevice.executeShellCommand(String.format("pm clear %s", pkgName));
        if (result.contains("Success")) {
            return true;
        } else {
            CLog.e("Failed to clear data from %s. Result: %s", pkgName, result);
            return false;
        }
    }

    /**
     * Determines and extracts a Bluetooth address if one exists in the given input text.
     *
     * @param inputText the text to parse and determine whether there is a BT address in
     * @return the BT address that was parsed from the inputText, or null if we could not find one
     */
    static String extractBTAddress(String inputText) {
        String btAddress = null;
        if (inputText != null) {
            Pattern btAddressPattern = Pattern.compile("^([a-fA-F0-9]{2}:){5}[a-fA-F0-9]{2}$");
            Matcher addressMatcher = btAddressPattern.matcher(inputText);
            if (addressMatcher.find()) {
                btAddress = addressMatcher.group().trim();
            }
        }
        return btAddress;
    }

    private String getTungstenInfo(ITestDevice tungstenDevice)
            throws DeviceNotAvailableException {
        for (int i = 1; i <= BT_ATTEMPTS; i++) {
            String btAddrFile = tungstenDevice.getProperty("ro.bt.bdaddr_path");
            Assert.assertTrue("Unable to find BT address file from Tungsten.",
                    !btAddrFile.isEmpty());
            String output = tungstenDevice.executeShellCommand(String.format(
                    "cat %s", btAddrFile));
            String btAddress = extractBTAddress(output);

            if (btAddress != null) {
                return btAddress;
            }
            CLog.i("Failed to find address of tungsten on attempt %d of %d.", i,
                    BT_ATTEMPTS);
            if (i != BT_ATTEMPTS) {
                CLog.i("Sleeping for %d sec before retrying", BT_POLL_TIME);
                RunUtil.getDefault().sleep(BT_POLL_TIME * 1000);
            }
        }
        return null;
    }

    private void takeScreenshot(int iteration, ITestDevice device, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        try (InputStreamSource screenshotStream = device.getScreenshot()) {
            listener.testLog(String.format("iteration_%d_screenshot", iteration), LogDataType.PNG,
                    screenshotStream);
        }
    }

    private void takeHierarchyDump(int iteration, ITestDevice device,
            ITestInvocationListener listener) throws DeviceNotAvailableException {
        String fileLoc = useGeppetto(device) ? "/data/local/tmp/uidump.xml" :
            "/data/uidump/window_dump.xml";
        File tmpHierarchyFile = device.pullFile(fileLoc);
        if (tmpHierarchyFile != null) {
            InputStreamSource fileStream = new FileInputStreamSource(tmpHierarchyFile);
            try {
                listener.testLog(String.format("iteration_%d_window_dump", iteration),
                        LogDataType.XML, fileStream);
            } finally {
                tmpHierarchyFile.delete();
            }
        }
    }

    private void takeLogcat(
            String logNamePrefix,
            int iteration,
            LogcatReceiver logcatCollector,
            ITestInvocationListener listener) {
        try (InputStreamSource logStream = logcatCollector.getLogcatData()) {
            listener.testLog(String.format("iteration_%d_%s_logcat", iteration, logNamePrefix),
                    LogDataType.LOGCAT, logStream);
        }
    }

    private void takeBugreport(
            String logNamePrefix,
            int iteration,
            ITestDevice device,
            ITestInvocationListener listener) {
        try (InputStreamSource bugStream = device.getBugreport()) {
            listener.testLog(String.format("iteration_%d_%s_bugreport", iteration, logNamePrefix),
                    LogDataType.BUGREPORT, bugStream);
        }
    }

    /**
     * Runs the setup tests on given device
     *
     * @param device the {@link ITestDevice} to run the tests on
     * @param currentIteration
     * @throws DeviceNotAvailableException
     */
    private void runSetupTests(ITestDevice device, String btAddress,
            int currentIteration)
            throws DeviceNotAvailableException {
        if (useGeppetto(device)) {
            runUiAutomatorSetupTests(device, btAddress, currentIteration);
        } else {
            runInstrSetupTests(device, btAddress, currentIteration);
        }
    }

    /**
     * Runs the instrumentation-based setup tests on device.
     *
     * @param device
     * @param btAddress
     * @param currentIteration
     * @throws DeviceNotAvailableException
     */
    private void runInstrSetupTests(ITestDevice device, String btAddress, int currentIteration)
            throws DeviceNotAvailableException {
        InstrumentationTest instrTest = new InstrumentationTest();
        instrTest.setDevice(device);
        instrTest.setPackageName(REMOTECONTROL_UI_TESTS_PKG_NAME);
        instrTest.addInstrumentationArg("btAddress", String.format("\"%s\"", btAddress));

        if (mWifiPassword != null) {
            instrTest.addInstrumentationArg("wifiPassword", mWifiPassword);
        }
        if (mAccount != null) {
            instrTest.addInstrumentationArg("account", mAccount);
        }
        if (mAppName != null) {
            instrTest.addInstrumentationArg("appName", String.format("\"%s\"", mAppName));
        }
        if (mProductName != null) {
            instrTest.addInstrumentationArg("productName", String.format("\"%s\"", mProductName));
        }
        instrTest.addInstrumentationArg("iteration", Integer.toString(currentIteration));
        instrTest.setRunnerName(".SetupTestRunner");
        FailureCollectingListener collectingListener = new FailureCollectingListener();
        instrTest.run(collectingListener);

        if (collectingListener.mTestTrace != null) {
            // get the first two lines of stack trace to avoid polluting the log
            String msg = getFailureMessageFromStackTrace(collectingListener.mTestTrace);
            Assert.fail(String.format("Setup tests failed: %s", msg));
        }
    }

    /**
     * Runs the uiautomator-based setup tests on device.
     *
     * @param device
     * @param btAddress
     * @param currentIteration
     * @throws DeviceNotAvailableException
     */
    private void runUiAutomatorSetupTests(
            ITestDevice device, String btAddress, int currentIteration)
            throws DeviceNotAvailableException {
        // TODO: share  code with runInstrSetupTests
        UiAutomatorTest uiautomatorTest = new UiAutomatorTest();
        uiautomatorTest.setDevice(device);
        uiautomatorTest.setTestJarPaths(Arrays.asList(REMOTECONTROL_UI_AUTOMATOR_TESTS_PATH));
        uiautomatorTest.setLoggingOption(LoggingOption.OFF);
        uiautomatorTest.addRunArg("btAddress", String.format("\"%s\"", btAddress));

        if (mWifiPassword != null) {
            uiautomatorTest.addRunArg("wifiPassword", mWifiPassword);
        }
        if (mAccount != null) {
            uiautomatorTest.addRunArg("account", mAccount);
        }
        if (mAppName != null) {
            uiautomatorTest.addRunArg("appName", String.format("\"%s\"", mAppName));
        }
        if (mProductName != null) {
            uiautomatorTest.addRunArg("productName", String.format("\"%s\"", mProductName));
        }
        uiautomatorTest.addRunArg("iteration", Integer.toString(currentIteration));
        uiautomatorTest.addRunArg("class", REMOTECONTROL_UI_AUTOMATOR_TESTS_CLASS);
        uiautomatorTest.addRunArg("disable_ime", "true");
        FailureCollectingListener collectingListener = new FailureCollectingListener();
        uiautomatorTest.run(collectingListener);

        if (collectingListener.mTestTrace != null) {
            // get the first two lines of stack trace to avoid polluting the log
            String msg = getFailureMessageFromStackTrace(collectingListener.mTestTrace);
            Assert.fail(String.format("Setup tests failed: %s", msg));
        }
    }

    /**
     * Gets the failure message to show from the stack trace.
     * <p/>
     * Exposed for unit testing
     *
     * @param stack the full stack trace
     * @return the failure message
     */
    static String getFailureMessageFromStackTrace(String stack) {
        // return the first two lines of stack as failure message
        int endPoint = stack.indexOf('\n');
        if (endPoint != -1) {
            int nextLine = stack.indexOf('\n', endPoint + 1);
            if (nextLine != -1) {
                return stack.substring(0, nextLine);
            }
        }
        return stack;
    }

    /**
     * Sends the phone logcat to the listener
     *
     * @param listener
     * @param phoneDevice
     */
    void reportPhoneLog(ITestInvocationListener listener, ITestDevice phoneDevice) {
        try (InputStreamSource logcatStream = phoneDevice.getLogcat()) {
            listener.testLog("phone_logcat", LogDataType.LOGCAT, logcatStream);
        }
    }

    /**
     * Perform the Music app setup to direct music output to the At Home device.
     *
     * <p>This must be called after {@link #performSetup(int, ITestInvocationListener, ITestDevice,
     * ITestDevice)}
     *
     * @param phoneDevice
     * @throws DeviceNotAvailableException
     */
    public boolean performMusicSetup(
            int currentIteration,
            ITestInvocationListener listener,
            ITestDevice phoneDevice,
            ITestDevice tungstenDevice)
            throws DeviceNotAvailableException {
        LogcatReceiver logcatCollector = new LogcatReceiver(phoneDevice, 10 * 1024 * 1024, 0);
        logcatCollector.start();
        try {
            logcatCollector.clear();
            runMusicSetup(phoneDevice);
            CLog.logAndDisplay(LogLevel.INFO, "iteration %d for music setup: Success!",
                    currentIteration);
        } catch (AssertionError e) {
            sendFailureLogs(currentIteration, listener, phoneDevice, logcatCollector,
                    tungstenDevice, e);
        } finally {
            logcatCollector.stop();
        }
        return true;

    }

    /**
     * @throws DeviceNotAvailableException
     */
    private void runMusicSetup(ITestDevice device) throws DeviceNotAvailableException {
        InstrumentationTest instrTest = new InstrumentationTest();
        instrTest.setDevice(device);
        instrTest.setPackageName(MUSIC_UI_TESTS_PKG_NAME);
        instrTest.addInstrumentationArg("atHomeDevice", ATHOME_DEVICE_NAME);
        if (mAccount != null) {
            instrTest.addInstrumentationArg("account", mAccount);
        }
        instrTest.setRunnerName(".AtHomeSetupTestRunner");
        FailureCollectingListener collectingListener = new FailureCollectingListener();
        instrTest.run(collectingListener);

        if (collectingListener.mTestTrace != null) {
            // get the first two lines of stack trace to avoid polluting the log
            String msg = getFailureMessageFromStackTrace(collectingListener.mTestTrace);
            Assert.fail(String.format("Music Setup tests failed: %s", msg));
        }
    }

    private static class FailureCollectingListener extends CollectingTestListener {

        private String mTestTrace = null;

        @Override
        public void testFailed(TestIdentifier test, String trace) {
            super.testFailed(test, trace);
            if (trace != null) {
                mTestTrace = trace;
            } else {
                mTestTrace = "unknown failure";
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void testRunFailed(String errorMessage) {
            super.testRunFailed(errorMessage);
            mTestTrace = errorMessage;
        }
    }
}
