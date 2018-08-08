// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.asit;

import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.loganalysis.item.DmesgActionInfoItem;
import com.android.loganalysis.item.DmesgServiceInfoItem;
import com.android.loganalysis.item.DmesgStageInfoItem;
import com.android.loganalysis.parser.DmesgParser;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.BackgroundDeviceAction;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.LogcatReceiver;
import com.android.tradefed.device.RemoteAndroidDevice;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.AltDirBehavior;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.InstalledInstrumentationsTest;
import com.android.tradefed.util.AaptParser;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.SimpleStats;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flashes the device as part of the test, report device post-flash status as test results and
 * calculates the successive boot delays and granular boot info if enabled.
 */
public abstract class BaseBootTest extends InstalledInstrumentationsTest
        implements IRemoteTest, IDeviceTest, IBuildReceiver {

    public enum RebootType {
        REBOOT_BOOTLOADER_TEST,
        REBOOT_TEST;
    }

    protected static final String ONLINE = "online";
    protected static final String BOOT_TEST = "DeviceBootTest";
    protected static final long INVALID_TIME_DURATION = -1;

    private static final String SUCCESSIVE_BOOT_TEST = "SuccessiveBootTest";
    private static final String SUCCESSIVE_BOOT_UNLOCK_TEST = "SuccessiveBootUnlockTest";
    private static final String INITIAL_BOOT = "initial-boot";
    private static final String BOOT_COMPLETE_LOGCAT_TOTAL = "boot-complete-logcat-total";
    private static final String BOOT_COMPLETE_DENIALS = "boot-complete-selinux-denials";
    private static final String SUCCESSIVE_ONLINE = "successive-online";
    private static final String SUCCESSIVE_BOOT = "successive-boot";
    private static final String MAX = "max";
    private static final String MIN = "min";
    private static final String AVERAGE = "avg";
    private static final String MEDIAN = "median";
    private static final String STD_DEV = "std_dev";
    private static final String LOGCAT_CMD = "logcat *:D";
    private static final long LOGCAT_SIZE = 80 * 1024 * 1024;
    private static final String BOOT_COMPLETED_PROP = "getprop sys.boot_completed";
    private static final String BOOT_COMPLETED_VAL = "1";
    private static final String BOOT_TIME_PROP = "ro.boot.boottime";
    private static final String BOOTLOADER_PREFIX = "bootloader-";
    private static final String LOGCAT_FILE = "Succesive_reboots_logcat";
    private static final String LOGCAT_UNLOCK_FILE = "Succesive_reboots_unlock_logcat";
    private static final String RUNNER = "android.support.test.runner.AndroidJUnitRunner";
    private static final String PACKAGE_NAME = "com.android.boothelper";
    private static final String CLASS_NAME = "com.android.boothelper.BootHelperTest";
    private static final String SETUP_PIN_TEST = "setupLockScreenPin";
    private static final String UNLOCK_PIN_TEST = "unlockScreenWithPin";
    private static final String UNLOCK_TIME = "screen_unlocktime";
    private static final int BOOT_COMPLETE_POLL_INTERVAL = 1000;
    private static final int BOOT_COMPLETE_POLL_RETRY_COUNT = 45;
    private static final String ROOT = "root";
    private static final String DMESG_FILE = "/data/local/tmp/dmesglogs.txt";
    private static final String DUMP_DMESG = String.format("dmesg > %s", DMESG_FILE);
    private static final String INIT = "init_";
    private static final String START_TIME = "_START_TIME";
    private static final String DURATION = "_DURATION";
    private static final String END_TIME = "_END_TIME";
    private static final String ACTION = "action_";
    private static final String INIT_STAGE = "init_stage_";
    /** logcat command for events buffer and only filters out logs with tag "auditd" */
    private static final String LOGCAT_EVENTS_AUDITD_CMD = "logcat -b events auditd:* *:s";
    private static final String BOOT_COMPLETED_SET_TIME_KEY =
            "action_sys.boot_completed=1_START_TIME";
    private static final String TOTAL_BOOT_TIME = "TOTAL_BOOT_TIME";
    private static final String MAKE_DIR = "mkdir %s";
    private static final String FOLDER_NAME_FORMAT = "sample_%s";
    private static final String RANDOM_FILE_CMD = "dd if=/dev/urandom of=%s bs=%d%s count=1";
    private static final String KB_IDENTIFIER = "k";
    private static final String MB_IDENTIFIER = "m";
    private static final String BYTES_TRANSFERRED = "bytes transferred";
    private static final String RM_DIR = "rm -rf %s";
    private static final String RAMDUMP_STATUS = "ramdump -s";
    private static final String COLD_BOOT = "Coldboot";
    private static final String BOOTLOADER_PHASE_SW = "SW";
    protected static final String ERROR_NOT_REBOOT = "%s failed after flashing the device.";

    /** the desired recentness of battery level * */
    private static final long BATTERY_FRESHNESS_MS = 30 * 1000;
    // 03-10 21:43:40.328 1005 1005 D SystemServerTiming:
    // StartKeyAttestationApplicationIdProviderService took to complete: 3474ms
    private static final String BOOT_TIME_PREFIX_PATTERN = "^\\d*-\\d*\\s*\\d*:\\d*:\\d*.\\d*\\s*"
            + "\\d*\\s*\\d*\\s*D\\s*(?<componentname>.*):\\s*(?<subname>.*)\\s*";
    private static final String BOOT_TIME_SUFFIX_PATTERN = ":\\s*(?<delayinfo>.*)ms\\s*$";
    private static final String TIMESTAMP_PID =
            "^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+" + "(\\d+)\\s+(\\d+)\\s+([A-Z])\\s+";
    // 04-05 18:26:52.637 2161 2176 I BootHelperTest: Screen Unlocked
    private static final Pattern SCREEN_UNLOCKED =
            Pattern.compile(TIMESTAMP_PID + "(.+?)\\s*: Screen Unlocked$");
    // 04-05 18:26:54.320 1013 1121 I ActivityManager: Displayed
    // com.google.android.apps.nexuslauncher/.NexusLauncherActivity: +648ms
    private static final Pattern DISPAYED_LAUNCHER =
            Pattern.compile(
                    TIMESTAMP_PID
                            + "(.+?)\\s*: Displayed com.google.android.apps.nexuslauncher/.NexusLauncherActivity:"
                            + "\\s*(.*)$");

    @Option(name = "device-boot-time", description = "Max time in ms to wait for device to boot.")
    protected long mDeviceBootTime = 5 * 60 * 1000;

    @Option(
        name = "first-boot",
        description = "Calculate the boot time metrics after flashing the device"
    )
    private boolean mFirstBoot = true;

    @Option(
            name = "test-reboot",
            description = "To verify if the device reboots after flashing the device."
        )
    private boolean testReboot = true;

    @Option(
        name = "test-file-name",
        description = "Name of a apk in expanded test zip to install on device. Can be repeated.",
        importance = Importance.IF_UNSET
    )
    private Collection<String> mTestFileNames = new ArrayList<String>();

    @Option(
        name = "alt-dir",
        description =
                "Alternate directory to look for the apk if the apk is not in the tests zip file. "
                        + "For each alternate dir, will look in //, //data/app, //DATA/app, "
                        + "//DATA/app/apk_name/ and //DATA/priv-app/apk_name/. Can be repeated. "
                        + "Look for apks in last alt-dir first."
    )
    private List<File> mAltDirs = new ArrayList<>();

    @Option(name = "alt-dir-behavior", description = "The order of alternate directory to be used "
            + "when searching for apks to install")
    private AltDirBehavior mAltDirBehavior = AltDirBehavior.FALLBACK;

    @Option(name = "successive-boot", description = "Calculate the succesive boot delay info")
    private boolean mSuccessiveBoot = false;

    @Option(
        name = "boot-count",
        description =
                "Number of times to boot the devices to calculate"
                        + " the successive boot delay. Second boot after the first boot will be "
                        + " skipped for correctness."
    )
    private int mBootCount = 5;

    @Option(
        name = "granular-boot-info",
        description = "Parse the granular timing info from successive boot time."
    )
    private boolean mGranularBootInfo = false;

    @Option(name = "dmesg-info", description = "Collect the init services info from dmesg logs.")
    private boolean mDmesgInfo = false;

    @Option(name = "bootloader-info", description = "Collect the boot loader timing.")
    private boolean mBootloaderInfo = false;

    @Option(name = "data-dest-folder", description = "Destination folder to populate the data.")
    private String mDataDestFolder = "data/test_tmp";

    @Option(name = "data-size-gb", description = "Random data of given size will be created"
            + " under data/test_tmp")
    private int mDataSize = 0;

    @Option(name = "core-count", description = "Number of cores in the device.")
    private int mCoreCount = 4;

    // 03-10 21:43:40.328 1005 1005 D SystemServerTiming:StartWifi took to
    // complete: 3474ms
    // 03-10 21:43:40.328 1005 1005 D component:subcomponent took to complete:
    // 3474ms
    @Option(
        name = "components",
        shortName = 'c',
        description =
                "Comma separated list of component names to parse the granular boot info printed "
                        + "in the logcat."
    )
    private String mComponentNames = null;

    @Option(
        name = "full-components",
        shortName = 'f',
        description =
                "Comma separated list of component_subcomponent names to parse the granular boot "
                        + "info printed in the logcat."
    )
    private String mFullCompNames = null;

    @Option(
        name = "test-reboot-unlock",
        description = "Test the reboot scenario with" + "screen unlock."
    )
    private boolean mRebootUnlock = false;

    @Option(
        name = "skip-pin-setup",
        description =
                "Skip the pin setup if already set once"
                        + "and not needed for the second run especially in local testing."
    )
    private boolean mSkipPinSetup = false;

    @Option(
        name = "metrics",
        description =
                "Comma separated list of metrics"
                        + " (min, max, avg, variance, std_dev). By default avg info is calculated."
    )
    private String mMetrics = "avg";

    @Option(
        name = "skip-battery-test",
        description = "If true, the battery reading test will" + " be skipped."
    )
    private boolean mSkipBatteryTest = false;

    @Option(
        name = "test-apk-dir",
        description =
                "Directory that contains the test apks needed for"
                        + "testing boot time with apps installed."
    )
    private File mTestApkDir;

    @Option(
            name = "skip-data-delete",
            description =
                    "Skip deleting the random data populated for testing."
        )
    private boolean mSkipDataDelete = false;


    private IBuildInfo mBuildInfo;
    private Map<String, List<Double>> mBootInfo = new LinkedHashMap<>();
    private LogcatReceiver mRebootLogcatReceiver = null;
    protected boolean mFirstBootSuccess = true;
    private IRemoteAndroidTestRunner mRunner = null;
    private Set<String> mParsedLines = new HashSet<String>();
    private List<String> mInstalledPackages = new ArrayList<String>();
    protected String mRamDumpPath = null;

    /** {@inheritDoc} */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /** Returns the {@link IBuildInfo} for the test. */
    public IBuildInfo getBuildInfo() {
        return mBuildInfo;
    }

    /** {@inheritDoc} */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        long start = System.currentTimeMillis();
        listener.testRunStarted(BOOT_TEST, 1);
        try {
            try {
                // Test first boot
                if (mFirstBoot) {
                    testFirstBoot(listener);
                }

                // Test "reboot" and "reboot bootloader" works fine after flashing the new build.
                if (testReboot) {
                    testRebootCommand(listener, RebootType.REBOOT_BOOTLOADER_TEST);
                    testRebootCommand(listener, RebootType.REBOOT_TEST);
                }

                if (mFirstBootSuccess) {
                    // Install test apk's needed for post boot testing
                    if (mTestApkDir != null || !mTestFileNames.isEmpty()) {
                        installTestApks();
                    }
                }

                // Test successive boots
                if (mFirstBootSuccess && mSuccessiveBoot) {
                    // Set the current date from the host in test device.
                    getDevice().setDate(null);
                    if (mDataSize > 0) {
                        try {
                            if (!populateRandomData()) {
                                listener.testRunFailed("Not able to create the required data.");
                            }
                        } catch (InterruptedException e) {
                            listener.testRunFailed("Not able to create the required data due to "
                                    + e.getMessage());
                        }
                    }
                    Map<String, String> succesiveResult = new HashMap<>();
                    boolean isSuccesiveBootsSuccess = true;
                    TestIdentifier succesiveBootTestId =
                            new TestIdentifier(
                                    String.format("%s.%s", BOOT_TEST, BOOT_TEST),
                                    SUCCESSIVE_BOOT_TEST);
                    listener.testStarted(succesiveBootTestId);
                    try {
                        // Skip second boot from successive boot delay calculation
                        doSecondBoot();
                        if (mGranularBootInfo) {
                            clearAndStartLogcat();
                        }
                        testSuccessiveBoots(false);
                        if (mGranularBootInfo) {
                            analyzeGranularBootInfo();
                        }
                    } catch (DeviceNotAvailableException dnae) {
                        CLog.e("Device not available after successive reboots");
                        CLog.e(dnae);
                        isSuccesiveBootsSuccess = false;
                        listener.testFailed(
                                succesiveBootTestId,
                                "Device not available after successive reboots");
                    } finally {
                        if (isSuccesiveBootsSuccess) {
                            if (null != mRebootLogcatReceiver) {
                                InputStreamSource logcatData =
                                        mRebootLogcatReceiver.getLogcatData();
                                listener.testLog(LOGCAT_FILE, LogDataType.TEXT, logcatData);
                                StreamUtil.cancel(logcatData);
                                mRebootLogcatReceiver.stop();
                            }
                            computeBootMetrics(succesiveResult);
                            listener.testEnded(succesiveBootTestId, succesiveResult);
                            // Uninstall the apk's added for testing
                            if (mTestApkDir != null) {
                                uninstallTestApks();
                            }
                            if (mDataSize > 0 && !mSkipDataDelete) {
                                getDevice().executeShellCommand(
                                        String.format(RM_DIR, mDataDestFolder));
                            }
                        }
                    }
                }

                // Test to measure the reboot time and time from unlocking the
                // screen using the pin
                // till the NexusLauncherActivity is displayed.
                if (mRebootUnlock) {
                    mBootInfo.clear();
                    Map<String, String> succesiveBootUnlockResult = new HashMap<>();
                    TestIdentifier succesiveBootUnlockTestId =
                            new TestIdentifier(
                                    String.format("%s.%s", BOOT_TEST, BOOT_TEST),
                                    SUCCESSIVE_BOOT_UNLOCK_TEST);
                    listener.testStarted(succesiveBootUnlockTestId);
                    try {
                        // If pin is already set skip the setup method otherwise
                        // setup the pin.
                        if (!mSkipPinSetup) {
                            mRunner = createRemoteAndroidTestRunner(SETUP_PIN_TEST);
                            getDevice()
                                    .runInstrumentationTests(mRunner, new CollectingTestListener());
                        }
                        clearAndStartLogcat();
                        testSuccessiveBoots(true);
                        analyzeUnlockBootInfo();
                    } finally {
                        InputStreamSource logcatData = mRebootLogcatReceiver.getLogcatData();
                        listener.testLog(LOGCAT_UNLOCK_FILE, LogDataType.TEXT, logcatData);
                        StreamUtil.cancel(logcatData);
                        mRebootLogcatReceiver.stop();
                        computeBootMetrics(succesiveBootUnlockResult);
                        listener.testEnded(succesiveBootUnlockTestId, succesiveBootUnlockResult);
                        // Uninstall the apk's added for testing
                        if (mTestApkDir != null) {
                            uninstallTestApks();
                        }
                    }
                }
            } finally {
                // Finish run for boot test. Health check bellow will start it's own test run.
                listener.testRunEnded(
                        System.currentTimeMillis() - start, Collections.<String, String>emptyMap());
            }

            // If first boot was successful, we run the installed tests apks.
            // Skip for successive boot test and reboot unlock test
            if (mFirstBootSuccess && !mSuccessiveBoot && !mRebootUnlock) {
                if (mTestApkDir != null || !mTestFileNames.isEmpty()) {
                    // re-establish recovery before running the health test instrumentations.
                    getDevice().setRecoveryMode(RecoveryMode.AVAILABLE);
                    super.run(listener);
                }
            }
        } finally {
            finalTearDown();
        }
    }

    /**
     * Verify if the device reboot successfully or not.
     *
     * @param listener
     * @param rebootType could be normal reboot or reboot to bootloader.
     */
    public abstract void testRebootCommand(ITestInvocationListener listener, RebootType rebootType);

    /**
     * Method to bring up the device online.
     *
     * @param listener
     * @param result
     * @return the time when boot started.
     * @throws DeviceNotAvailableException
     */
    public abstract long bringUp(ITestInvocationListener listener, Map<String, String> result)
            throws DeviceNotAvailableException;

    /**
     * Can be overriden to run extra device specific tests.
     *
     * @param listener the {@link ITestInvocationListener} where to report tests
     * @param testId the {@link TestIdentifier} to report.
     */
    public void extraDeviceCheck(ITestInvocationListener listener, TestIdentifier testId) {
        // empty on purpose
    }

    /**
     * Final optional tear down step for the Boot test.
     *
     * @throws DeviceNotAvailableException
     */
    public void finalTearDown() throws DeviceNotAvailableException {
        // empty on purpose
    }

    /**
     * Flash the device and calculate the time to bring the device online and first boot.
     *
     * @param listener
     * @throws DeviceNotAvailableException
     */
    private void testFirstBoot(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        Map<String, String> result = new HashMap<>();
        TestIdentifier firstBootTestId =
                new TestIdentifier(String.format("%s.%s", BOOT_TEST, BOOT_TEST), BOOT_TEST);

        long bootTime = INVALID_TIME_DURATION;
        long bootStart = bringUp(listener, result);

        try {
            listener.testStarted(firstBootTestId);
            if (!mFirstBootSuccess) {
                // in case bringUp was not successful.
                listener.testFailed(firstBootTestId, "Failed to boot completely.");
                return;
            }
            // start collecting logcat events buffer for SELinux denials
            SELinuxDenialCounter counter = new SELinuxDenialCounter();
            BackgroundDeviceAction eventsLogcat =
                    new BackgroundDeviceAction(
                            LOGCAT_EVENTS_AUDITD_CMD, "events logcat", getDevice(), counter, 0);
            eventsLogcat.start();
            // check if device can be fully booted, i.e. if application
            // framework won't boot
            CLog.v("Waiting for device %s boot complete", getDevice().getSerialNumber());
            getDevice().setRecoveryMode(RecoveryMode.AVAILABLE);
            try {
                getDevice().waitForDeviceAvailable(mDeviceBootTime);
                bootTime = System.currentTimeMillis() - bootStart;
                if (mRamDumpPath != null && !mRamDumpPath.isEmpty()) {
                    CLog.i("Ramdump status : %s", getDevice().executeShellCommand(RAMDUMP_STATUS));
                }
            } catch (DeviceNotAvailableException dnae) {
                CLog.e("Device not available after bringUp");
                CLog.e(dnae);
                // only report as test failure, not test run failure because we
                // were able to run the test until the end, despite the failure verdict, and the
                // device is returned to the pool in a useable state
                mFirstBootSuccess = false;
                listener.testFailed(firstBootTestId, "Device not available after bringUp");
                return;
            }
            eventsLogcat.cancel();
            counter.logDenialsLine(listener);
            counter.mIsCancelled = true;
            CLog.v("Device %s boot complete", getDevice().getSerialNumber());

            // Run device specific tests
            extraDeviceCheck(listener, firstBootTestId);

            // device booted, now count lines of logcat
            result.put(BOOT_COMPLETE_LOGCAT_TOTAL, Integer.toString(countLogcatLines()));
            // report number of SELinux denials detected
            result.put(BOOT_COMPLETE_DENIALS, Integer.toString(counter.getDenialCount()));
            if (mSkipBatteryTest || getDevice() instanceof RemoteAndroidDevice) {
                // If we skip the battery test, we can return directly after
                // boot complete.
                return;
            }
            // We check if battery level are readable as non root to ensure that
            // device is usable.
            getDevice().disableAdbRoot();
            try {
                Future<Integer> batteryFuture =
                        getDevice()
                                .getIDevice()
                                .getBattery(BATTERY_FRESHNESS_MS, TimeUnit.MILLISECONDS);
                // get cached value or wait up to 4100ms for battery level query
                // BatteryQuery has 2s timeout, may run two commands, add 100ms as buffer
                Integer level = batteryFuture.get(4100, TimeUnit.MILLISECONDS);
                CLog.d("Battery level value reading is: '%s'", level);
                if (level == null) {
                    mFirstBootSuccess = false;
                    listener.testFailed(firstBootTestId, "Reading of battery level is wrong.");
                    return;
                }
            } catch (InterruptedException
                    | ExecutionException
                    | java.util.concurrent.TimeoutException e) {
                CLog.e("Failed to query battery level for %s", getDevice().getSerialNumber());
                CLog.e(e);
                mFirstBootSuccess = false;
                listener.testFailed(firstBootTestId, "Failed to query battery level.");
                return;
            } finally {
                getDevice().enableAdbRoot();
            }
        } finally {
            if (bootTime != INVALID_TIME_DURATION) {
                result.put(INITIAL_BOOT, Double.toString(((double) bootTime) / 1000));
            }
            listener.testEnded(firstBootTestId, result);
        }
    }

    /**
     * Counts the number of lines in current logcat buffer
     *
     * @return number of lines
     */
    public int countLogcatLines() {
        InputStreamSource iss = null;
        int lineCount = -1;
        try {
            iss = getDevice().getLogcat();
            lineCount = StreamUtil.countLinesFromSource(iss);
        } catch (IOException ioe) {
            CLog.e("IOException (ignored) while counting lines from logcat buffer.");
            CLog.e(ioe);
        } finally {
            StreamUtil.cancel(iss);
        }
        return lineCount;
    }

    /**
     * Install the test apk's needed for post boot testing.
     *
     * @return true if the apps installation is successfull
     * @throws DeviceNotAvailableException
     */
    private boolean installTestApks() throws DeviceNotAvailableException {

        // Install the list of test apk files needed for device health testing.
        if (!mTestFileNames.isEmpty()) {
            TestAppInstallSetup appInstallSetup = new TestAppInstallSetup();
            for (File altDir : mAltDirs) {
                appInstallSetup.setAltDir(altDir);
            }
            appInstallSetup.setAltDirBehavior(mAltDirBehavior);
            for (String fileName : mTestFileNames) {
                appInstallSetup.addTestFileName(fileName);
            }
            try {
                appInstallSetup.setUp(getDevice(), mBuildInfo);
            } catch (TargetSetupError tse) {
                return false;
            }
        }

        // Install all the third party apk's from the given directory used for
        // testing boot
        // time with more apps installed.
        if (null != mTestApkDir) {
            String[] files = mTestApkDir.list();
            for (String fileName : files) {
                if (!fileName.endsWith(".apk")) {
                    CLog.d("Skipping non-apk %s", fileName);
                    continue;
                }
                File packageFile = new File(mTestApkDir, fileName);
                AaptParser parser = AaptParser.parse(packageFile);
                String output = getDevice().installPackage(packageFile, false);
                if (null != output) {
                    CLog.e("Failed to install package %s with error %s", packageFile, output);
                    return false;
                }
                mInstalledPackages.add(parser.getPackageName());
            }
        }
        return true;
    }

    /**
     * Uninstall the third party test apk's that are installed from the given directory and not
     * needed after the successive boot test is completed.
     *
     * @throws DeviceNotAvailableException
     */
    private void uninstallTestApks() throws DeviceNotAvailableException {
        for (String packageName : mInstalledPackages) {
            if (packageName != null) {
                CLog.d("Uninstalling: %s", packageName);
                getDevice().uninstallPackage(packageName);
            }
        }
    }


    /**
     * Populate random data of given data-size-gb under mDataDestFolder. For each GB of data,
     * 1000 directories each with 1kb and 1mb files, 24 kb and 23 mb files are created.
     *
     * @return true if data creation is successful otherwise false.
     * @throws DeviceNotAvailableException
     * @throws InterruptedException
     */
    private boolean populateRandomData() throws DeviceNotAvailableException, InterruptedException {
        // Create the destination directory
        String result = getDevice().executeShellCommand(String.format(MAKE_DIR, mDataDestFolder));
        if (result != null && result.isEmpty()) {
            for (int i = 0; i < mDataSize; i++) {
                // Create sub directory to store GB of data
                String directoryName = String.format(FOLDER_NAME_FORMAT, i);
                String fullDirPathFolder = String.format("%s/%s", mDataDestFolder, directoryName);
                getDevice().executeShellCommand(String.format(MAKE_DIR, fullDirPathFolder));
                ExecutorService threadPool = Executors.newFixedThreadPool(mCoreCount);
                // Create 1000 folders, each folder containing 1 kb and 1mb file
                AtomicBoolean status = new AtomicBoolean(true);
                for (int j = 1; j <= 1000; j++) {
                    threadPool.execute(new FileCreateRunnable(directoryName,
                            String.format("%d-%d", i, j), status));
                }
                threadPool.shutdown();
                if (!threadPool.awaitTermination(300, TimeUnit.SECONDS) || !status.get()) {
                    CLog.e("Took more time to write the data or the file creation did not happen"
                            + "successfully.");
                    return false;
                }
                // Create remaining 24 kb and 23 mb files
                if (!getDevice().executeShellCommand(
                        String.format(RANDOM_FILE_CMD, String.format(
                                "%s/%s", fullDirPathFolder, KB_IDENTIFIER), 24, KB_IDENTIFIER))
                        .contains(BYTES_TRANSFERRED)) {
                    return false;
                }
                if (!getDevice().executeShellCommand(
                        String.format(RANDOM_FILE_CMD, String.format(
                                "%s/%s", fullDirPathFolder, MB_IDENTIFIER), 23, MB_IDENTIFIER))
                        .contains(BYTES_TRANSFERRED)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * To create a folder with 1 kb and 1 mb file.
     */
    private class FileCreateRunnable implements Runnable {
        String mDirectoryName;
        String mSubDirNameSuffix;
        AtomicBoolean mSuccess;

        FileCreateRunnable(String directoryName, String subDirNameSuffix, AtomicBoolean success) {
            mDirectoryName = directoryName;
            mSubDirNameSuffix = subDirNameSuffix;
            mSuccess = success;
        }

        @Override
        public void run() {
            String subDirectoryName = String.format(FOLDER_NAME_FORMAT, mSubDirNameSuffix);
            String fullSubDirPathFolder = String.format("%s/%s/%s", mDataDestFolder,
                    mDirectoryName, subDirectoryName);
            try {
                getDevice().executeShellCommand(
                        String.format(MAKE_DIR, fullSubDirPathFolder));
                fullSubDirPathFolder = String.format("%s/%s", fullSubDirPathFolder,
                        subDirectoryName);
                if (!getDevice().executeShellCommand(String.format(RANDOM_FILE_CMD,
                        fullSubDirPathFolder + KB_IDENTIFIER, 1, KB_IDENTIFIER)).contains(
                        BYTES_TRANSFERRED)) {
                    mSuccess.set(false);
                }
                if (!getDevice().executeShellCommand(String.format(RANDOM_FILE_CMD,
                        fullSubDirPathFolder + MB_IDENTIFIER, 1, MB_IDENTIFIER)).contains(
                        BYTES_TRANSFERRED)) {
                    mSuccess.set(false);
                }
            } catch (DeviceNotAvailableException e) {
                CLog.e("Device not available while writing the data.");
                CLog.e(e);
                mSuccess.set(false);
            }
        }

    }


    /**
     * Do the second boot on the device to exclude from the successive boot time calcualtions
     *
     * @throws DeviceNotAvailableException
     */
    private void doSecondBoot() throws DeviceNotAvailableException {
        getDevice().nonBlockingReboot();
        getDevice().waitForDeviceOnline();
        getDevice().waitForDeviceAvailable(mDeviceBootTime);
    }

    /**
     * Clear the existing logs and start capturing the logcat
     *
     * @throws DeviceNotAvailableException
     */
    private void clearAndStartLogcat() throws DeviceNotAvailableException {
        getDevice().executeShellCommand("logcat -c");
        mRebootLogcatReceiver = new LogcatReceiver(getDevice(), LOGCAT_CMD, LOGCAT_SIZE, 0);
        mRebootLogcatReceiver.start();
    }

    /**
     * To perform the successive boot for given boot count and take the average of boot time and
     * online time delays
     *
     * @param dismissPin to dismiss pin after reboot
     * @throws DeviceNotAvailableException
     */
    private void testSuccessiveBoots(boolean dismissPin) throws DeviceNotAvailableException {
        for (int count = 0; count < mBootCount; count++) {
            DmesgParser dmesgLogParser = null;
            double bootStart = INVALID_TIME_DURATION;
            double onlineTime = INVALID_TIME_DURATION;
            double bootTime = INVALID_TIME_DURATION;
            getDevice().nonBlockingReboot();
            bootStart = System.currentTimeMillis();
            getDevice().waitForDeviceOnline();
            onlineTime = System.currentTimeMillis() - bootStart;
            if (mDmesgInfo) {
                dmesgLogParser = new DmesgParser();
                // Collect the dmesg logs after device is online and
                // after the device is boot completed to avoid losing the
                // initial logs.
                parseDmesgInfo(dmesgLogParser);
            }
            try {
                waitForBootCompleted();
            } catch (InterruptedException e) {
                CLog.e("Sleep Interrupted");
                CLog.e(e);
            } catch (DeviceNotAvailableException dne) {
                CLog.e("Device not available");
                CLog.e(dne);
            }
            bootTime = System.currentTimeMillis() - bootStart;
            if (mDmesgInfo) {
                // Collect the dmesg logs after device is online and
                // after the device is boot completed to avoid losing the
                // initial logs.
                parseDmesgInfo(dmesgLogParser);
                if (!dmesgLogParser.getServiceInfoItems().isEmpty()) {
                    analyzeDmesgServiceInfo(dmesgLogParser.getServiceInfoItems().values());
                }
                if (!dmesgLogParser.getStageInfoItems().isEmpty()) {
                    analyzeDmesgStageInfo(dmesgLogParser.getStageInfoItems());
                }
                if (!dmesgLogParser.getActionInfoItems().isEmpty()) {
                    analyzeDmesgActionInfo(dmesgLogParser.getActionInfoItems());
                }
            }

            // Parse bootloader timing info
            if (mBootloaderInfo)
                analyzeBootloaderTimingInfo();

            if (dismissPin) {
                mRunner = createRemoteAndroidTestRunner(UNLOCK_PIN_TEST);
                getDevice().runInstrumentationTests(mRunner, new CollectingTestListener());
                // Wait for 15 secs after every unlock to make sure home screen
                // is loaded
                // and logs are printed
                RunUtil.getDefault().sleep(15000);
            }
            if (onlineTime != INVALID_TIME_DURATION) {
                if (mBootInfo.containsKey(SUCCESSIVE_ONLINE)) {
                    mBootInfo.get(SUCCESSIVE_ONLINE).add(onlineTime);
                } else {
                    List<Double> onlineDelayList = new ArrayList<Double>();
                    onlineDelayList.add(onlineTime);
                    mBootInfo.put(SUCCESSIVE_ONLINE, onlineDelayList);
                }
            }
            if (bootTime != INVALID_TIME_DURATION) {
                if (mBootInfo.containsKey(SUCCESSIVE_BOOT)) {
                    mBootInfo.get(SUCCESSIVE_BOOT).add(bootTime);
                } else {
                    List<Double> bootDelayList = new ArrayList<Double>();
                    bootDelayList.add(bootTime);
                    mBootInfo.put(SUCCESSIVE_BOOT, bootDelayList);
                }
            }
        }
    }

    /**
     * Parse the logcat file for granular boot info (eg different system services start time) based
     * on the component name or full component name (i.e component_subcompname)
     */
    private void analyzeGranularBootInfo() {
        String[] compStr = new String[0];
        String[] fullCompStr = new String[0];
        boolean isFilterSet = false;

        if (null != mComponentNames) {
            compStr = mComponentNames.split(",");
            isFilterSet = true;
        }
        if (null != mFullCompNames) {
            fullCompStr = mFullCompNames.split(",");
            isFilterSet = true;
        }

        Set<String> compSet = new HashSet<>(Arrays.asList(compStr));
        Set<String> fullCompSet = new HashSet<>(Arrays.asList(fullCompStr));
        Set<String> matchedLine = new HashSet<String>();
        Pattern durationPattern = Pattern.compile(String.format("%stook to complete%s",
                BOOT_TIME_PREFIX_PATTERN, BOOT_TIME_SUFFIX_PATTERN));
        Pattern startTimePattern = Pattern.compile(String.format("%sstart time%s",
                BOOT_TIME_PREFIX_PATTERN, BOOT_TIME_SUFFIX_PATTERN));
        InputStreamSource logcatData = mRebootLogcatReceiver.getLogcatData();
        InputStream logcatStream = logcatData.createInputStream();
        InputStreamReader logcatReader = new InputStreamReader(logcatStream);
        try (BufferedReader br = new BufferedReader(logcatReader)) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher match = null;
                if (((match = matches(durationPattern, line))) != null ||
                        ((match = matches(startTimePattern, line))) != null) {
                    // To exclude the duplicate entries in the log file which
                    // happens
                    // when you reboot the device while collecting the logs
                    // using the receivers.
                    if (matchedLine.contains(line)) {
                        continue;
                    } else {
                        matchedLine.add(line);
                    }
                    String fullCompName =
                            String.format(
                                    "%s_%s",
                                    match.group("componentname").trim(),
                                    match.group("subname").trim());
                    // If filter not set then capture timing info for all the
                    // components otherwise
                    // only for the given component names and full component
                    // names.
                    if (!isFilterSet
                            || compSet.contains(match.group("componentname").trim())
                            || fullCompSet.contains(fullCompName)) {
                        if (mBootInfo.containsKey(fullCompName)) {
                            mBootInfo
                                    .get(fullCompName)
                                    .add(Double.parseDouble(match.group("delayinfo").trim()));
                        } else {
                            List<Double> delayList = new ArrayList<Double>();
                            delayList.add(Double.parseDouble(match.group("delayinfo").trim()));
                            mBootInfo.put(fullCompName, delayList);
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            CLog.e("Problem in parsing the granular boot delay information");
            CLog.e(ioe);
        } finally {
            StreamUtil.cancel(logcatData);
            StreamUtil.close(logcatStream);
            StreamUtil.close(logcatReader);
        }
    }

    /**
     * Collect the dmesg logs and parse the service info(start and end time), start time of boot
     * stages and actions being processed, logged in the dmesg file.
     *
     * @param dmesgLogParser
     * @throws DeviceNotAvailableException
     */
    private void parseDmesgInfo(DmesgParser dmesgLogParser)
            throws DeviceNotAvailableException {
        getDevice().executeAdbCommand(ROOT);
        // Dump the dmesg logs to a file in the device
        getDevice().executeShellCommand(DUMP_DMESG);
        try {
            File dmesgFile = getDevice().pullFile(DMESG_FILE);
            BufferedReader input =
                    new BufferedReader(new InputStreamReader(new FileInputStream(dmesgFile)));
            dmesgLogParser.parseInfo(input);
            dmesgFile.delete();
        } catch (IOException ioe) {
            CLog.e("Failed to analyze the dmesg logs", ioe);
        }
    }

    /**
     * Analyze the services info parsed from the dmesg logs and construct the metrics as a part of
     * boot time data.
     *
     * @param serviceInfoItems contains the start time, end time and the duration of of each service
     *     logged in the dmesg log file.
     */
    private void analyzeDmesgServiceInfo(Collection<DmesgServiceInfoItem> serviceInfoItems) {
        for (DmesgServiceInfoItem infoItem : serviceInfoItems) {
            if (infoItem.getStartTime() != null) {
                String key = String.format("%s%s%s", INIT, infoItem.getServiceName(), START_TIME);
                if (mBootInfo.get(key) != null) {
                    mBootInfo.get(key).add(infoItem.getStartTime().doubleValue());
                } else {
                    List<Double> timeList = new ArrayList<Double>();
                    timeList.add(infoItem.getStartTime().doubleValue());
                    mBootInfo.put(key, timeList);
                }
            }
            if (infoItem.getServiceDuration() != -1L) {
                String key = String.format("%s%s%s", INIT, infoItem.getServiceName(), DURATION);
                if (mBootInfo.get(key) != null) {
                    mBootInfo.get(key).add(infoItem.getServiceDuration().doubleValue());
                } else {
                    List<Double> timeList = new ArrayList<Double>();
                    timeList.add(infoItem.getServiceDuration().doubleValue());
                    mBootInfo.put(key, timeList);
                }
            }
            if (infoItem.getEndTime() != null) {
                String key = String.format("%s%s%s", INIT, infoItem.getServiceName(), END_TIME);
                if (mBootInfo.get(key) != null) {
                    mBootInfo.get(key).add(infoItem.getEndTime().doubleValue());
                } else {
                    List<Double> timeList = new ArrayList<Double>();
                    timeList.add(infoItem.getEndTime().doubleValue());
                    mBootInfo.put(key, timeList);
                }
            }
        }
    }

    /**
     * Analyze the boot stages info parsed from the dmesg logs and construct the metrics as a part
     * of boot time data.
     *
     * @param stageInfoItems contains the start time of each stage logged in the dmesg log file.
     */
    private void analyzeDmesgStageInfo(Collection<DmesgStageInfoItem> stageInfoItems) {
        for (DmesgStageInfoItem stageInfoItem : stageInfoItems) {
            if (stageInfoItem.getStartTime() != null) {
                String key = String.format("%s%s%s", INIT_STAGE, stageInfoItem.getStageName(),
                        START_TIME);
                List<Double> values = mBootInfo.getOrDefault(key, new ArrayList<>());
                values.add(stageInfoItem.getStartTime().doubleValue());
                mBootInfo.put(key, values);
            } else if (stageInfoItem.getStageName().contains(COLD_BOOT) &&
                    stageInfoItem.getDuration() != null) {
                List<Double> values = mBootInfo.getOrDefault(stageInfoItem.getStageName(),
                        new ArrayList<>());
                values.add(stageInfoItem.getDuration().doubleValue());
                mBootInfo.put(stageInfoItem.getStageName(), values);
            }
        }
    }

    /**
     * Analyze each action info parsed from the dmesg logs and construct the metrics as a part
     * of boot time data.
     *
     * @param actionInfoItems contains the start time of processing of each action logged in the
     *     dmesg log file.
     */
    private void analyzeDmesgActionInfo(Collection<DmesgActionInfoItem> actionInfoItems) {
        for (DmesgActionInfoItem actionInfoItem : actionInfoItems) {
            if (actionInfoItem.getStartTime() != null) {
                String key = String.format("%s%s%s", ACTION, actionInfoItem.getActionName(),
                        START_TIME);
                List<Double> values = mBootInfo.getOrDefault(key, new ArrayList<>());
                values.add(actionInfoItem.getStartTime().doubleValue());
                mBootInfo.put(key, values);
            }
        }
    }

    /**
     * Analyze the time taken by different phases in boot loader by parsing
     * the system property ro.boot.boottime
     * @throws DeviceNotAvailableException
     */
    private void analyzeBootloaderTimingInfo() throws DeviceNotAvailableException {
        String bootLoaderVal = getDevice().getProperty(BOOT_TIME_PROP);
        // Sample Output : 1BLL:89,1BLE:590,2BLL:0,2BLE:1344,SW:6734,KL:1193
        if (bootLoaderVal != null) {
            String[] bootLoaderPhases = bootLoaderVal.split(",");
            double bootLoaderTotalTime = 0d;
            for (String bootLoaderPhase : bootLoaderPhases) {
                String[] bootKeyVal = bootLoaderPhase.split(":");
                String key = String.format("%s%s", BOOTLOADER_PREFIX, bootKeyVal[0]);
                if (mBootInfo.containsKey(key)) {
                    mBootInfo.get(key).add(Double.parseDouble(bootKeyVal[1]));
                } else {
                    List<Double> timeList = new ArrayList<Double>();
                    timeList.add(Double.parseDouble(bootKeyVal[1]));
                    mBootInfo.put(key, timeList);
                }
                // SW is the time spent on the warning screen. So ignore it in
                // final boot time calculation.
                if (!BOOTLOADER_PHASE_SW.equalsIgnoreCase(bootKeyVal[0])) {
                    bootLoaderTotalTime += Double.parseDouble(bootKeyVal[1]);
                }
            }

            // "action_sys.boot_completed=1_START_TIME" is parsed already from dmesg logs.
            // Calculate the sum of bootLoaderTotalTime and boot completed flag set time.
            if (mBootInfo.containsKey(BOOT_COMPLETED_SET_TIME_KEY)) {
                int lastIndex = mBootInfo.get(BOOT_COMPLETED_SET_TIME_KEY).size() - 1;
                double totalBootTime = bootLoaderTotalTime + mBootInfo.get(
                        BOOT_COMPLETED_SET_TIME_KEY).get(lastIndex);
                if (mBootInfo.containsKey(TOTAL_BOOT_TIME)) {
                    mBootInfo.get(TOTAL_BOOT_TIME).add(totalBootTime);
                } else {
                    List<Double> timeList = new ArrayList<Double>();
                    timeList.add(totalBootTime);
                    mBootInfo.put(TOTAL_BOOT_TIME, timeList);
                }
            }
        }
    }

    /**
     * Parse the logcat file and calculate the time difference between the screen unlocked timestamp
     * till the Nexus launcher activity is displayed.
     */
    private void analyzeUnlockBootInfo() {
        InputStreamSource logcatData = mRebootLogcatReceiver.getLogcatData();
        InputStream logcatStream = logcatData.createInputStream();
        InputStreamReader logcatReader = new InputStreamReader(logcatStream);
        try (BufferedReader br = new BufferedReader(logcatReader)) {
            boolean logOrderTracker = false;
            double unlockInMillis = 0d;
            String line;
            while ((line = br.readLine()) != null) {
                Matcher match = null;
                if ((match = matches(SCREEN_UNLOCKED, line)) != null && !isDuplicateLine(line)) {
                    mParsedLines.add(line);
                    Date time = parseTime(match.group(1));
                    unlockInMillis = time.getTime();
                    logOrderTracker = true;
                } else if ((match = matches(DISPAYED_LAUNCHER, line)) != null
                        && !isDuplicateLine(line)
                        && logOrderTracker) {
                    Date time = parseTime(match.group(1));
                    if (mBootInfo.containsKey(UNLOCK_TIME)) {
                        mBootInfo.get(UNLOCK_TIME).add(time.getTime() - unlockInMillis);
                    } else {
                        List<Double> screenUnlockTime = new ArrayList<Double>();
                        screenUnlockTime.add(time.getTime() - unlockInMillis);
                        mBootInfo.put(UNLOCK_TIME, screenUnlockTime);
                    }
                    logOrderTracker = false;
                }
            }
        } catch (IOException ioe) {
            CLog.e("Problem in parsing screen unlock delay from logcat.");
            CLog.e(ioe);
        } finally {
            StreamUtil.cancel(logcatData);
            StreamUtil.close(logcatStream);
            StreamUtil.close(logcatReader);
        }
    }

    /**
     * To check if the line is duplicate entry in the log file.
     *
     * @param currentLine
     * @return true if log line are duplicated
     */
    private boolean isDuplicateLine(String currentLine) {
        if (mParsedLines.contains(currentLine)) {
            return true;
        } else {
            mParsedLines.add(currentLine);
            return false;
        }
    }

    /**
     * Compute metrics for successive boot delay and granular boot info during the successive boots
     *
     * @param result
     */
    private void computeBootMetrics(Map<String, String> result) {
        List<String> mMetricsDetails = Arrays.asList(mMetrics.split(","));
        for (Map.Entry<String, List<Double>> bootData : mBootInfo.entrySet()) {
            SimpleStats stats = new SimpleStats();
            stats.addAll(bootData.getValue());
            CLog.d("%s : %s", bootData.getKey(), concatenateTimeValues(bootData.getValue()));
            if (mMetricsDetails.contains(MAX)) {
                result.put(bootData.getKey() + "_max", stats.max().toString());
                CLog.d("%s_%s : %s", bootData.getKey(), MAX, stats.max());
            }
            if (mMetricsDetails.contains(MIN)) {
                result.put(bootData.getKey() + "_min", stats.min().toString());
                CLog.d("%s_%s : %s", bootData.getKey(), MIN, stats.min());
            }
            if (mMetricsDetails.contains(AVERAGE)) {
                result.put(bootData.getKey() + "_avg", stats.mean().toString());
                CLog.d("%s_%s : %s", bootData.getKey(), AVERAGE, stats.mean());
            }
            if (mMetricsDetails.contains(MEDIAN)) {
                result.put(bootData.getKey() + "_median", stats.median().toString());
                CLog.d("%s_%s : %s", bootData.getKey(), MEDIAN, stats.median());
            }
            if (mMetricsDetails.contains(STD_DEV)) {
                result.put(bootData.getKey() + "_std_dev", stats.stdev().toString());
                CLog.d("%s_%s : %s", bootData.getKey(), STD_DEV, stats.stdev());
            }
        }
    }

    /** Concatenate given list of values to comma separated string */
    public String concatenateTimeValues(List<Double> timeInfo) {
        StringBuilder timeString = new StringBuilder();
        for (Double time : timeInfo) {
            timeString.append(time);
            timeString.append(",");
        }
        return timeString.toString();
    }

    /**
     * Checks whether {@code line} matches the given {@link Pattern}.
     *
     * @return The resulting {@link Matcher} obtained by matching the {@code line} against {@code
     *     pattern}, or null if the {@code line} does not match.
     */
    private static Matcher matches(Pattern pattern, String line) {
        Matcher ret = pattern.matcher(line);
        return ret.matches() ? ret : null;
    }

    /**
     * Method to create the runner with given testName
     *
     * @return the {@link IRemoteAndroidTestRunner} to use.
     * @throws DeviceNotAvailableException
     */
    IRemoteAndroidTestRunner createRemoteAndroidTestRunner(String testName)
            throws DeviceNotAvailableException {
        RemoteAndroidTestRunner runner =
                new RemoteAndroidTestRunner(PACKAGE_NAME, RUNNER, getDevice().getIDevice());
        runner.setMethodName(CLASS_NAME, testName);
        return runner;
    }

    /**
     * Wait until the sys.boot_completed is set
     *
     * @throws InterruptedException
     * @throws DeviceNotAvailableException
     */
    private void waitForBootCompleted() throws InterruptedException, DeviceNotAvailableException {
        for (int i = 0; i < BOOT_COMPLETE_POLL_RETRY_COUNT; i++) {
            if (isBootCompleted()) {
                return;
            }
            Thread.sleep(BOOT_COMPLETE_POLL_INTERVAL);
        }
    }

    /**
     * Returns true if boot completed property is set to true.
     *
     * @throws DeviceNotAvailableException
     */
    private boolean isBootCompleted() throws DeviceNotAvailableException {
        return BOOT_COMPLETED_VAL.equals(
                getDevice().executeShellCommand(BOOT_COMPLETED_PROP).trim());
    }

    /**
     * Parse the timestamp and return a {@link Date}.
     *
     * @param timeStr The timestamp in the format {@code MM-dd HH:mm:ss.SSS}.
     * @return The {@link Date}.
     */
    private Date parseTime(String timeStr) {
        DateFormat yearFormatter = new SimpleDateFormat("yyyy");
        String mYear = yearFormatter.format(new Date());
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        try {
            return formatter.parse(String.format("%s-%s", mYear, timeStr));
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * An implementation of {@link MultiLineReceiver} that counts lines with specific SELinux denial
     * messages
     */
    static class SELinuxDenialCounter extends MultiLineReceiver {

        public static final String AVC_DENIED = "avc: denied";

        private boolean mIsCancelled = false;
        private int mDeniedCounter;
        private StringBuffer mBuffer = new StringBuffer();

        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                if (line.contains(AVC_DENIED)) {
                    mDeniedCounter++;
                    mBuffer.append(line);
                    mBuffer.append("\n");
                }
            }
        }

        public void logDenialsLine(ITestLogger logger) {
            if (mBuffer.length() > 0) {
                CLog.d("Found the following selinux denial:\n%s", mBuffer.toString());
                InputStreamSource data =
                        new ByteArrayInputStreamSource(mBuffer.toString().getBytes());
                try {
                    logger.testLog("denials_log", LogDataType.TEXT, data);
                } finally {
                    StreamUtil.cancel(data);
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return mIsCancelled;
        }

        public int getDenialCount() {
            return mDeniedCounter;
        }
    }
}
