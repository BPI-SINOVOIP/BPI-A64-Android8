// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.iot;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.Pair;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.SimpleStats;
import com.android.tradefed.util.StreamUtil;

import org.junit.Assert;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Test harness for Android WiFi Interoperability Test (IOT).
 *
 * <p>The test harness includes a set of
 * <li>functional tests: association test, ping test, TCP/UDP traffic tests </li>
 * <li>performance tests: TCP/UDP upload/download speed tests</li>
 * <li>stress tests: web browsing, file downloading and video streaming test.</li>
 */
public class InteroperabilityTest implements IRemoteTest, IDeviceTest {
    /** Device wifi frequency band settings*/
    public static final String DEVICE_WIFI_FREQUENCY_BAND_AUTO = "auto";
    public static final String DEVICE_WIFI_FREQUENCY_BAND_2GHZ = "2.4";
    public static final String DEVICE_WIFI_FREQUENCY_BAND_5GHZ = "5.0";

    private static final String ACCESS_POINT_FILE_NAME = "iot/accesspoints.xml";
    private static final String PASSWORD = "androidwifi";
    /** Test report file */
    private static final String TEST_REPORT = "iot-test-report";
    /** Record test result for functional and stress test  */
    private static final String PASS = "1";
    private static final String FAIL = "-1";
    private static final boolean DEBUG = false;

    /** Association test package, class, runner and test time. */
    private static final String ASSOC_TEST_PACKAGE_NAME =
        "com.android.connectivitymanagertest";
    private static final String ASSOC_TEST_CLASS_NAME =
        String.format("%s.functional.WifiAssociationTest", ASSOC_TEST_PACKAGE_NAME);
    private static final String ASSOC_TEST_RUNNER_NAME =
            ".WifiAssociationTestRunner";
    // Associate test timer is 10 minutes
    private static final int ASSOC_TEST_TIMER = 10 * 60 * 1000;

    /** File downloading test package, class, runner and extra parameters */
    private static final String FILE_DOWNLOAD_PKG = "com.android.frameworks.downloadmanagertests";
    private static final String FILE_DOWNLOAD_CLASS =
            "com.android.frameworks.downloadmanagertests.DownloadManagerTestApp";
    private static final String DOWNLOAD_TEST_RUNNER_NAME =
            "com.android.frameworks.downloadmanagertests.DownloadManagerTestRunner";
    // File download test timer is 30 minutes
    private static final int FILE_DOWNLOAD_TEST_TIMER = 30 * 60 * 1000;
    // Extra parameters to pass to the TestRunner
    private static final String EXTERNAL_DOWNLOAD_URI_KEY = "external_download_uri";

    /** Browsing test package, class, runner and extra parameters */
    private static final String CHROME_TEST_PACKAGE_NAME =
            "com.google.android.apps.chrome.tests";
    private static final String CHROME_TEST_CLASS_NAME =
            "com.google.android.apps.chrome.PopularUrlsTest";
    private static final String CHROME_TEST_METHOD_NAME = "testStability";
    private static final String CHROME_TEST_RUNNER_NAME =
            "android.test.InstrumentationTestRunner";
    private static final String URLS_FILE_NAME = "popular_urls.txt";
    private static final String STATUS_FILE_NAME = "test_status.txt";
    private static final int TOTAL_SITES = 10;

    /** Video streaming test package, class, runner and parameters */
    // Constants for running the tests
    private static final String VIDEO_TEST_PACKAGE_NAME = "com.android.mediaframeworktest";
    private static final String VIDEO_TEST_CLASS_NAME =
            "com.android.mediaframeworktest.stress.MediaPlayerStressTest";
    private static final String VIDEO_TEST_RUNNER_NAME = ".MediaPlayerStressTestRunner";
    // Video streaming test timer is set to 30 minutes
    private static final int VIDEO_TEST_TIMER = 30 * 60 * 1000;
    private static final String MEDIA_SOURCE_KEY = "media-source";
    // Maximum time for a device to wait for other devices to finish functional tests in mix-mode
    private static final long MIX_FUNCTION_MODE_TIMER = 10 * 60 * 1000;
    // Maximum time for a device to wait for other devices to finish performance tests: 60 minutes
    private static final long MIX_PERF_MODE_TIMER = 60 * 60 * 1000;

    private ITestDevice mTestDevice = null;
    // Two dimensional array as a pool for all access points. The first dimension is
    // indexed by NPS id. All APs connected to NPS_ONE will be stored in
    // mApStorage[0]. This structure makes AP controlling more efficient as
    // it avoids jumping from one NPS to another NPS for AP power control, which results
    // extra network communication.
    private AccessPointInfo[][] mApStorage = null;
    // Declare the ApController as static as it could be shared by multiple invocations
    // when running the tests continuously or in mix-function mode (run functional tests
    // on all devices after a test AP is powered on).
    private static ApController mApController;
    private DeviceHelper mDeviceHelper;
    private IperfTestRunner mIperfTestRunner;
    private AccessPointInfo mCurrentTestAp = null;
    private String mTestApSecurityType;
    private String mTestDeviceCurrentBand;

    /*
     * Record result for each test case in a hashmap,
     * The key is <<<brand_model_firmware, security_type>, band>, test_case>
     * in which
     * brand_model_firmware: branch, model and firmware of the test AP
     * security_type: security type of the test AP
     * band: device frequency band
     * test_case: test case name
     */
    private Map<Pair<Pair<Pair<String, String>, String>, String>, String> mTestResult =
            new HashMap<>();
    /*
     * Store functional, stress and performance test results for each AP
     */
    private SortedMap<String, String> mApTestResult = new TreeMap<>();

    private static final Semaphore runToken = new Semaphore(1);
    // Semaphore to modify number of devices that have finished the functional tests
    private static final Semaphore flagToken = new Semaphore(1);
    // Count number of devices that have finished tests on each AP
    private static Map<String, Integer> mDeviceCount = new HashMap<>();
    // Semaphore to control executing scripts to turn off all APs during initialization
    private static final Semaphore initializationToken = new Semaphore(1);
    // Flag to allow turning off all APs only once during initialization
    private static boolean apInitializationFlag = false;
    // Flag to allow disabling wifi on all devices only once during initialization
    private static boolean deviceInitializationFlag = false;
    // Flag to notify other invocations to stop if something is wrong
    private static boolean stopInvocationFlag = false;

    @Option(
        name = "frequency-band",
        description =
                "frequency bands that the device supports."
                        + "Valid inputs are: 2.4, 5.0, and auto. For a single"
                        + "band device, enter the band only (without auto)."
    )
    private List<String> mDeviceFrequencyBands = new ArrayList<>();
    @Option(name = "script-path",
            description = "the absolute path for expect script to control NPS")
    private String mScriptPath = null;

    @Option(name = "functional-test",
            description = "flag to run functional test only")
    private boolean mFunctionalTestFlag = true;
    @Option(name = "ping-test",
            description = "flag to run ping test")
    private boolean mPingTestFlag = true;
    @Option(name = "traffic-test",
            description = "flag to run traffic test")

    private boolean mTrafficTestFlag = true;
    @Option(name = "performance-test",
            description = "flag to run performance test only")
    private boolean mPerformanceTestFlag = true;
    @Option(name = "tcp-ul",
            description = "flag to run tcp upload speed test")
    private boolean mTcpULTestFlag = true;
    @Option(name = "tcp-dl",
            description = "flag to run tcp download speed test")
    private boolean mTcpDLTestFlag = true;
    @Option(name = "udp-ul",
            description = "flag to run udp upload speed test")
    private boolean mUdpULTestFlag = true;
    @Option(name = "udp-dl",
            description = "flag to run udp download speed test")
    private boolean mUdpDLTestFlag = true;
    @Option(name = "stress-test",
            description = "flag to run stress test only")
    private boolean mStressTestFlag = true;
    @Option(name = "browsing-test",
            description = "flag to run browsing stress test")
    private boolean mBrowsingTestFlag = true;
    @Option(name = "downloading-test",
            description = "flag to run file downloading stress test")
    private boolean mDownloadingTestFlag = true;
    @Option(name = "videostreaming-test",
            description = "flag to run video streaming stress test")
    private boolean mVideoStreamingTestFlag = true;
    /** For file download test */
    @Option(name = "external-download-uri",
            description = "external URI under which the files downloaded by the tests can be found."
                    + "Uri must be accessible by the device during a test run.",
            importance = Importance.IF_UNSET)
    private String mExternalDownloadUriValue = null;
    /** Set video streaming uri source */
    @Option(name = "media-source",
            description = "extenal URI under which the video files can be found. URI must be"
                    + "accessible by the device during a test run.")
    private String mMediaSourceUriValue = null;
    /** Set option to run particular APs * */
    @Option(
        name = "ap-brand-model",
        description =
                "brand name and model of the selected test APs, input format should be"
                        + "\'brand_model\', for example: Apple_AirPort-Extreme, not case sensitive."
                        + "This option can be set together with NPS-id, only selected APs in the"
                        + "selected NPSs will be executed."
    )
    private List<String> mSelectedAccessPoints = new ArrayList<>();
    /** Set option to run all APs for particular NPSs * */
    @Option(
        name = "NPS-id",
        description =
                "IDs of selected NPS. Available values are: "
                        + "NPS_ONE, NPS_TWO, NPS_THREE. Upcases are enforced. If ap-brand-model is not"
                        + "set, all APs connected to the selected NPSs will be executed. Otherwise,"
                        + "only the selected APs will run the test."
    )
    private List<String> mSelectedNPSs = new ArrayList<>();
    @Option(name = "mix-function-mode",
            description = "Run functional tests on all connected devices once an AP is powered on")
    private boolean mMixFunctionMode = false;
    @Option(name = "mix-perf-mode",
            description = "Run functional and performance tests on all connected devices once "
                    + "an AP is powered up")
    private boolean mMixPerfMode = false;
    @Option(name = "perf-iterations",
            description = "Number of iterations for performance tests")
    private int mPerfIterations = 10;

    enum TEST_TYPE {
        IOT_FUNCTIONAL, IOT_STRESS, IOT_PERF
    }

    public enum TEST_CASE {
        ASSOCIATION, PING, TCP_TRAFFIC, UDP_TRAFFIC,
        TCP_UL_2GHZ, TCP_DL_2GHZ, UDP_UL_2GHZ, UDP_DL_2GHZ,
        TCP_UL_5GHZ, TCP_DL_5GHZ, UDP_UL_5GHZ, UDP_DL_5GHZ,
        BROWSING_TEST, DOWNLOAD_TEST, VIDEO_STREAMING_TEST;

        public static TEST_CASE getTestCase(String band, String protocol, int speedType) {
            if (DEVICE_WIFI_FREQUENCY_BAND_2GHZ.equals(band)) {
                if (IperfTestRunner.TCP.equals(protocol)) {
                    if (IperfTestRunner.UPLOAD == speedType) {
                        return TCP_UL_2GHZ;
                    } else {
                        return TCP_DL_2GHZ;
                    }
                } else if (IperfTestRunner.UDP.equals(protocol)) {
                    if (IperfTestRunner.UPLOAD == speedType) {
                        return UDP_UL_2GHZ;
                    } else {
                        return UDP_DL_2GHZ;
                    }
                }
            } else if (DEVICE_WIFI_FREQUENCY_BAND_5GHZ.equals(band)){
                if (IperfTestRunner.TCP.equals(protocol)) {
                    if (IperfTestRunner.UPLOAD == speedType) {
                        return TCP_UL_5GHZ;
                    } else {
                        return TCP_DL_5GHZ;
                    }
                } else if (IperfTestRunner.UDP.equals(protocol)) {
                    if (IperfTestRunner.UPLOAD == speedType) {
                        return UDP_UL_5GHZ;
                    } else {
                        return UDP_DL_5GHZ;
                    }
                }
            }
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice testDevice) {
        mTestDevice = testDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * Print access point information from the pool.
     */
    private void printApInfo() {
        StringBuilder sb = new StringBuilder();
        AccessPointInfo ap;
        for (int i = 0; i < IotUtil.NPS_SIZE; i++) {
            for (int j = 0; j < IotUtil.MAX_NPS_AP_NUMBER; j++) {
                ap = mApStorage[i][j];
                if (ap != null) {
                    sb.append("brand: " + ap.getBrand() + "\n");
                    sb.append("model: " + ap.getModel() + "\n");
                    sb.append("firmware: " + ap.getFirmware() + "\n");
                    for (String fb: ap.getFrequencyBand()) {
                        sb.append("frequency: " + fb + "\n");
                    }
                    for (String security: ap.getSecurityTypes()) {
                        sb.append("security type: " + security + "\n");
                    }
                    sb.append("ip address: " + ap.getIpAddress() + "\n");
                    sb.append("nps id: " + ap.getNpsId() + "\n");
                    sb.append("nps plug id: " + ap.getNpsPlugId() + "\n\n");
                }
            }
        }
        CLog.v("access point info: \n" + sb.toString());
    }

    /**
     * Load Access Point xml file and parse it.
     */
    private void loadAccessPointInfo() {
        // load AP resource file
        BufferedInputStream apInput = IotUtil.getSourceFile(ACCESS_POINT_FILE_NAME);
        AccessPointParser parser = new AccessPointParser(apInput);
        try {
            parser.parse();
        } catch (Exception e) {
            Assert.fail("Parsing XML file failed: " + e.toString());
        }
        mApStorage = parser.getAccessPointInfo();
        CLog.d("number of ap parsed: %d", parser.getTotalNumberAps());
        if (DEBUG) {
            printApInfo();
        }
    }

    /**
     * Verify the device frequency bands input are valid: 2.4, 5.0 or auto.
     *
     * @return {@code true} if all frequency bands are valid, {@code false} otherwise.
     */
    private boolean validateDeviceFrequencyBands() {
        if (mDeviceFrequencyBands.size() == 0) {
            CLog.v("Please input device frequency bands");
            return false;
        } else {
            for (String band: mDeviceFrequencyBands) {
                if (!DEVICE_WIFI_FREQUENCY_BAND_2GHZ.equals(band) &&
                        !DEVICE_WIFI_FREQUENCY_BAND_5GHZ.equals(band) &&
                        !DEVICE_WIFI_FREQUENCY_BAND_AUTO.equals(band)) {
                    CLog.v("%s is not a valid frequency band", band);
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Initialize test runner and do sanity check.
     *
     * @throws DeviceNotAvailableException
     */
    private boolean initialization() throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        loadAccessPointInfo();
        // Validate device frequency bands
        if (!validateDeviceFrequencyBands()) {
            return false;
        }

        if (mMixFunctionMode || mMixPerfMode) {
            try {
                CLog.v("wait to acquire token to initialize all aps.");
                initializationToken.acquire();
                if (!apInitializationFlag) {
                    // initialize ApController only once for all invocations in mix modes
                    try {
                        mApController = new ApController(mScriptPath);
                    } catch (Exception e) {
                        CLog.e("Initialize ap controller failed: %s", e.toString());
                        return false;
                    }
                    mApController.switchAllNpsOff();
                    apInitializationFlag = true;
                }
                stopInvocationFlag = false;
            } catch (InterruptedException e) {
                CLog.d("acquire initialization token interrupted");
                return false;
            } finally {
                initializationToken.release();
            }
        } else {
            try {
                mApController = new ApController(mScriptPath);
            } catch (Exception e) {
                CLog.e("Initialize ap controller failed: %s", e.toString());
                return false;
            }
            mApController.switchAllNpsOff();
        }

        // Initialize IperfTestRunner
        mIperfTestRunner = new IperfTestRunner(mTestDevice);

        // Convert all strings in mSelectedAccessPoints to lower case
        if (mSelectedAccessPoints != null) {
            for (int i = 0; i < mSelectedAccessPoints.size(); i++) {
                String newAp = mSelectedAccessPoints.get(i).toLowerCase();
                mSelectedAccessPoints.set(i, newAp);
            }
        }
        // Initialize DeviceHelper
        try {
            mDeviceHelper = new DeviceHelper(mScriptPath);
        } catch (IOException e) {
            CLog.e("Initialize device helper failed: %s", e.toString());
            return false;
        }
        // Disable WiFi on all devices
        try {
            initializationToken.acquire();
            if (!deviceInitializationFlag) {
                mDeviceHelper.disableWiFiSettings();
                deviceInitializationFlag = true;
            }
        } catch (InterruptedException e) {
            CLog.d("acquire initialization token interrupted");
            return false;
        } finally {
            initializationToken.release();
        }
        return true;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // Initialization before running any test
        if (!initialization()) {
            CLog.v("initialization failed, stop the test!");
            return;
        }

        // Decide which NPSs to run the test
        IotUtil.NPS[] npsList = null;
        if (!mSelectedNPSs.isEmpty()) {
            // If the requirement is to run only selected NPS
            npsList = new IotUtil.NPS[mSelectedNPSs.size()];
            for(int i = 0; i < mSelectedNPSs.size(); i++) {
                // Verify the input NPS ids are correct
                int index = -1;
                try {
                    index = IotUtil.NPS.valueOf(mSelectedNPSs.get(i)).ordinal();
                } catch (IllegalArgumentException e) {
                    CLog.e("Didn't find NPS id: %s", mSelectedNPSs.get(i));
                }
                if (index >= 0) {
                    npsList[i] = IotUtil.NPS.valueOf(mSelectedNPSs.get(i));
                }
            }
        } else {
            // To run all APs connected to all NPSs
            npsList = IotUtil.NPS.values();
        }
        for(IotUtil.NPS nps : npsList) {
            if (nps == null) {
                CLog.e("nps is empty");
                continue;
            }
            for (int j = 0; j < IotUtil.MAX_NPS_AP_NUMBER; j++) {
                mCurrentTestAp = mApStorage[nps.ordinal()][j];
                if (mCurrentTestAp == null) {
                    CLog.v("no more AP to test on NPS %d", nps.ordinal());
                    break;
                }
                // Check whether this one of the selected AP
                if (!isSelectedAccessPoint()) {
                    CLog.v("not a selected access point");
                    continue;
                }
                mApTestResult.clear();

                // If it is to run functional tests on all platforms
                if (mMixFunctionMode) {
                    // Acquire semaphore to operate on the AP
                    try {
                        CLog.v("wait for acquire run token");
                        runToken.acquire();
                        if (!isApAvailable()) {
                            if (mApController.isApEnabled(mCurrentTestAp)) {
                                // If the AP has been powered on by other device and
                                // it failed to boot up, simply go to the next AP
                                continue;
                            }
                            // if the AP is not up yet, power it on and wait till it is up.
                            mApController.enableAp(mCurrentTestAp);
                            boolean res = waitForApPowerUp();
                            if (!res) {
                                // if the test AP failed to power up, go to the next AP
                                continue;
                            }
                        }
                    } catch (InterruptedException e) {
                        CLog.d("acquire run token interrupted");
                        return;
                    } finally {
                        runToken.release();
                    }
                    runTests(listener);
                } else if (mMixPerfMode) {
                    // Acquire semaphore to operate on the AP and also execute tests
                    try {
                        CLog.v("wait for acquire run token");
                        runToken.acquire();
                        if (stopInvocationFlag) {
                            // stop the invocation if it is notified by other invocation
                            CLog.v("Stop the invocation, something is wrong with the test.");
                            return;
                        }
                        if (!isApAvailable()) {
                           if (mApController.isApEnabled(mCurrentTestAp)) {
                                // If the AP has been powered on by other device and
                                // it failed to boot up, simply go to the next AP
                                continue;
                            }
                            // if the AP is not up yet, power it on and wait till it is up.
                            mApController.enableAp(mCurrentTestAp);
                            boolean res = waitForApPowerUp();
                            if (!res) {
                                // if the test AP failed to power up, go to the next AP
                                continue;
                            }
                        }
                        runTests(listener);
                        // If is mix perf test mode, disable wifi after test execution
                        boolean success = mTestDevice.disconnectFromWifi();
                        if (!success) {
                            mTestDevice.executeShellCommand("svc wifi disable");
                        }
                        getRunUtil().sleep(5 * 1000);
                        if (!mTestDevice.isWifiEnabled()) {
                            CLog.v("Wifi on device %s is disabled, continue with next device",
                                    mTestDevice.getSerialNumber());
                        } else {
                            CLog.v("Wifi on device %s is still enabled, something is wrong?",
                                mTestDevice.getSerialNumber());
                            stopInvocationFlag = true;
                        }
                    } catch (InterruptedException e) {
                        CLog.e("acquire run token interrupted");
                    } finally {
                        runToken.release();
                    }
                } else {
                    // Power on the AP
                    mApController.enableAp(mCurrentTestAp);
                    if (!waitForApPowerUp()) {
                        // if the test AP failed to power up, go to the next AP
                        continue;
                    }
                    runTests(listener);
                }

                // After finishing test for each AP, report the results
                reportTestResults(listener);
                if (DEBUG) {
                    printTestResults();
                }

                if (mMixFunctionMode || mMixPerfMode) {
                    // update the flag after all tests are finished
                    try {
                        CLog.v("Update flag token after all tests are executed");
                        flagToken.acquire();
                        Integer countInt = mDeviceCount.get(mCurrentTestAp.getBrandModel());
                        mDeviceCount.put(mCurrentTestAp.getBrandModel(),
                                (countInt == null ? 1 : countInt + 1));
                    } catch (InterruptedException e) {
                        CLog.d("acquire flag token interrupted");
                        return;
                    } finally {
                        flagToken.release();
                    }

                    if (mCurrentTestAp != null) {
                        CLog.v("wait until all devices have finished the "
                                + "tests on the current AP");
                        int numAvailableDevices = mDeviceHelper.getAvailableDevices();
                        long start = System.currentTimeMillis();
                        long timer;
                        long periodCheckTime;
                        if (mMixFunctionMode) {
                            timer = MIX_FUNCTION_MODE_TIMER;
                            periodCheckTime = 10 * 1000;
                        } else {
                            timer = MIX_PERF_MODE_TIMER;
                            periodCheckTime = 5 * 10 * 1000;
                        }
                        while ((System.currentTimeMillis() - start) < timer) {
                            Integer count = mDeviceCount.get(mCurrentTestAp.getBrandModel());
                            if ((count != null) && (count.intValue() < numAvailableDevices)) {
                                CLog.v("available devices: %d", numAvailableDevices);
                                CLog.v("wait for %s milliseconds", periodCheckTime);
                                getRunUtil().sleep(periodCheckTime);
                                numAvailableDevices = mDeviceHelper.getAvailableDevices();
                                // Just in case invocations go wrong
                                if (stopInvocationFlag) {
                                    CLog.v("Don't waste time waiting, stop the test");
                                    return;
                                }
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
        }

        // generate test report file
        createTestReport(listener);

        // After all devices have finished the tests, reset test flags to
        // prepare for the next invocation of a new build.
        if (mMixFunctionMode || mMixPerfMode) {
            try {
                flagToken.acquire();
                if (!mDeviceCount.isEmpty()) {
                    CLog.v("clear mDeviceCount");
                    mDeviceCount.clear();
                }
                if (apInitializationFlag) {
                    CLog.v("reset apInitializationFlag");
                    apInitializationFlag = false;
                }
                if (deviceInitializationFlag) {
                    CLog.v("reset deviceInitializationFlag");
                    deviceInitializationFlag = false;
                }
                if (stopInvocationFlag) {
                    CLog.v("reset deviceInitializationFlag");
                    stopInvocationFlag = false;
                }
            } catch (InterruptedException e) {
                CLog.e("acquiring flag token interrupted");
            } finally {
                flagToken.release();
            }
        }
    }

    /**
     * Run all tests for the current test AP
     */
    private void runTests(ITestInvocationListener listener) throws DeviceNotAvailableException {
        for (String security: mCurrentTestAp.getSecurityTypes()) {
            mTestApSecurityType = security;
            if (mCurrentTestAp.isDualBand()) {
                CLog.v("\nAP %s is a dual band AP", mCurrentTestAp.getBrandModel());
                for (String band: mDeviceFrequencyBands) {
                    mTestDeviceCurrentBand = band;
                    String ssid = getAssociationTestSsidForDualAP();
                    CLog.v("mTestDeviceCurrentBand: %s", mTestDeviceCurrentBand);
                    CLog.v("ssid: %s\n", ssid);
                    executeTests(listener, ssid);
                    // wait for 30 seconds after running the tests
                    getRunUtil().sleep(30 * 1000);
                }
            } else {
                // test AP doesn't support dual band
                CLog.v("\nAP %s is a single band AP", mCurrentTestAp.getBrandModel());
                String apFrequencyBand = mCurrentTestAp.getFrequencyBand().get(0);
                String ssid = getAssociationTestSsidForSingleBandAP(apFrequencyBand);
                for (String band: mDeviceFrequencyBands) {
                    if (band.equals(apFrequencyBand) ||
                            band.equals(DEVICE_WIFI_FREQUENCY_BAND_AUTO)) {
                        mTestDeviceCurrentBand = band;
                        CLog.v("mTestDeviceCurrentBand: %s", mTestDeviceCurrentBand);
                        CLog.v("ssid: %s\n", ssid);
                        executeTests(listener, ssid);
                        // wait for 30 seconds after running the tests
                        getRunUtil().sleep(30 * 1000);
                    }
                }
            }
        }
    }

    /**
     * Verify whether the current AP is in the selected access point list.
     * @return true if {@code mSelectedAccessPoints} is null or the current AP is in
     *         {@code mSelectedAccessPoints}.
     */
    private boolean isSelectedAccessPoint() {
        if (mSelectedAccessPoints.isEmpty()) {
            // if selected access point list is empty, return true
            return true;
        } else {
            if (mSelectedAccessPoints.contains(mCurrentTestAp.getBrandModel().toLowerCase())) {
                return true;
            } else {
                return false;
            }
        }
    }
    /**
     * Decide which ssid the test device will connected to when the device
     * is set with different WiFi frequency band and the test AP is dual band.
     *
     * If test device is set with 2.4GHz, it will associate with ssid on 2.4GHz;
     * if test device is set with 5.0GHz, it will associate with ssid on 5.0GHz;
     * if test device is set with auto, it will associate with ssid on 5.0GHz or 2.4GHz
     * depending the devices' own frequency bands capability.
     */
    private String getAssociationTestSsidForDualAP() {
        if (mCurrentTestAp.getBrand().contains("Medialink")
                || mCurrentTestAp.getBrand().contains("Tenda")) {
            // Those two brand doesn't allow '.' in the ssid name
            if (DEVICE_WIFI_FREQUENCY_BAND_AUTO.equals(mTestDeviceCurrentBand)
                    || DEVICE_WIFI_FREQUENCY_BAND_5GHZ.equals(mTestDeviceCurrentBand)) {
                return String.format("%s_50", mCurrentTestAp.getBrandModel());
            } else {
                return String.format("%s_24", mCurrentTestAp.getBrandModel());
            }
        }

        if (DEVICE_WIFI_FREQUENCY_BAND_AUTO.equals(mTestDeviceCurrentBand)) {
            if (mDeviceFrequencyBands.contains(DEVICE_WIFI_FREQUENCY_BAND_5GHZ)) {
                return String.format("%s_5.0", mCurrentTestAp.getBrandModel());
            } else {
                return String.format("%s_2.4", mCurrentTestAp.getBrandModel());
            }
        } else {
            return String.format("%s_%s", mCurrentTestAp.getBrandModel(), mTestDeviceCurrentBand);
        }
    }

    /**
     * Decide which ssid the test device will connected to when the device
     * is set with different WiFi frequency band and the test AP is single band.
     *
     * @param apFrequencyBand the test AP frequency band
     * @return ssid
     */
    private String getAssociationTestSsidForSingleBandAP(String apFrequencyBand) {
        if (mCurrentTestAp.getBrand().contains("Medialink")
                || mCurrentTestAp.getBrand().contains("Tenda")) {
            if (DEVICE_WIFI_FREQUENCY_BAND_5GHZ.equals(apFrequencyBand)) {
                return String.format("%s_50", mCurrentTestAp.getBrandModel());
            } else {
                return String.format("%s_24", mCurrentTestAp.getBrandModel());
            }
        }
        return String.format("%s_%s", mCurrentTestAp.getBrandModel(), apFrequencyBand);
    }

    /**
     * Wait for the current test AP to power up
     */
    private boolean waitForApPowerUp() {
        String ipAddress = mCurrentTestAp.getIpAddress();
        long start = System.currentTimeMillis();
        Process p;
        String command = String.format("ping -c 5 -w 10 %s", ipAddress);
        while ((System.currentTimeMillis() - start) < IotUtil.MAX_AP_POWERUP_TIMER) {
            try {
                p = Runtime.getRuntime().exec(command);
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("5 packets transmitted, 5 received")) {
                        CLog.v("AP %s is up", mCurrentTestAp.getBrandModel());
                        // wait for 5 minutes for AP to fully boot up
                        getRunUtil().sleep(IotUtil.AP_STABLE_TIMER);
                        return true;
                    }
                }
            } catch (IOException e) {
                CLog.e("exception: %s", e.toString());
            }
            CLog.v("AP %s is not up yet, wait for %d seconds to retry",
                    mCurrentTestAp.getBrandModel(), IotUtil.MIN_AP_POWERUP_TIMER/1000);
            getRunUtil().sleep(IotUtil.MIN_AP_POWERUP_TIMER);
        }
        CLog.e("!!!Timeout while waiting for AP %s to power up, start up exceeds %d seconds",
                mCurrentTestAp.getBrandModel(), IotUtil.MAX_AP_POWERUP_TIMER/1000);
        return false;
    }

    /**
     * Verify whether AP is available
     */
    private boolean isApAvailable() {
        String ipAddress = mCurrentTestAp.getIpAddress();
        Process p;
        String command = String.format("ping -c 5 -w 10 %s", ipAddress);
        try {
            p = Runtime.getRuntime().exec(command);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("5 packets transmitted, 5 received")) {
                    CLog.v("AP %s is up", mCurrentTestAp.getBrandModel());
                    return true;
                }
            }
        } catch (IOException e) {
            CLog.e("IOException: %s", e.toString());
        }
        return false;
    }

    /**
     * Run test with the given information
     *
     * @param listener test invocation listener
     * @param ssid network ssid that the test device should associate with
     * @throws DeviceNotAvailableException
     */
    public void executeTests(ITestInvocationListener listener, String ssid)
            throws DeviceNotAvailableException {
        // run association test before running any other tests
        // this test sets device frequency band and associate with test AP
        boolean associationTestResult = executeAssociationTest(listener, ssid);
        String reportValue;
        if (!associationTestResult) {
            CLog.v("association test failed");
            reportValue = FAIL;
        } else {
            reportValue = PASS;
        }
        recordApTestResult(TEST_TYPE.IOT_FUNCTIONAL, TEST_CASE.ASSOCIATION, reportValue);
        recordDetailedTestResult(TEST_CASE.ASSOCIATION, reportValue);

        // If the association test failed, the rest of tests bail out.
        if (!associationTestResult) {
            return;
        }
        // run other functional tests
        boolean functionalRes = true;
        if (mFunctionalTestFlag) {
            functionalRes = executeFunctionalTests();
        }
        // if ping test failed, the rest of tests bail out.
        if (!functionalRes) {
            return;
        }
        getRunUtil().sleep(15 * 1000);

        if (mPerformanceTestFlag) {
            CLog.v("Execute performance tests");
            executePerformanceTest();
        }
        if (mStressTestFlag) {
            CLog.v("Execute stress tests");
            executeStressTest(listener);
        }
        // catch a bugreport after the performance test
        try (InputStreamSource bugreport = getDevice().getBugreport()) {
            listener.testLog(ssid + "_bugreport", LogDataType.BUGREPORT, bugreport);
        }
    }

    /**
     * Execute functional tests and collect results.
     *
     * @return {@code true} if all functional tests passed, {@code false} if test failed.
     * @throws DeviceNotAvailableException
     */
    public boolean executeFunctionalTests() throws DeviceNotAvailableException {
        // run ping test
        String reportValue;
        if (mPingTestFlag) {
            boolean pingTestResult = executePingTest();
            if (!pingTestResult) {
                CLog.v("ping test failed");
                reportValue = FAIL;
            } else {
                reportValue = PASS;
            }
            recordApTestResult(TEST_TYPE.IOT_FUNCTIONAL, TEST_CASE.PING, reportValue);
            recordDetailedTestResult(TEST_CASE.PING, reportValue);
            // if ping test failed, return false
            if (!pingTestResult) {
                return false;
            }
        }

        if (mMixFunctionMode) {
            mIperfTestRunner.setMixMode(true);
        }
        if (mTrafficTestFlag) {
            // wait for 15 seconds before starting the traffic test
            getRunUtil().sleep(15 * 1000);
            // run tcp traffic test
            boolean tcpTrafficTestResult = mIperfTestRunner.runTcpTrafficTest();
            if (!tcpTrafficTestResult) {
                CLog.v("tcp traffic test failed");
                reportValue = FAIL;
            } else {
                reportValue = PASS;
            }
            recordApTestResult(TEST_TYPE.IOT_FUNCTIONAL, TEST_CASE.TCP_TRAFFIC, reportValue);
            recordDetailedTestResult(TEST_CASE.TCP_TRAFFIC, reportValue);

            // wait for 15 seconds before starting the traffic test
            getRunUtil().sleep(15 * 1000);
            // run udp traffic test
            boolean udpTrafficTestResult = mIperfTestRunner.runUdpTrafficTest();
            if (!udpTrafficTestResult) {
                CLog.v("udp traffic test failed");
                reportValue = FAIL;
            } else {
                reportValue = PASS;
            }
            recordApTestResult(TEST_TYPE.IOT_FUNCTIONAL, TEST_CASE.UDP_TRAFFIC, reportValue);
            recordDetailedTestResult(TEST_CASE.UDP_TRAFFIC, reportValue);
        }
        return true;
    }

    /**
     * Execute association test by running WiFi association instrumentation test
     *
     * @param listener invocation listener
     * @return {@code true} if association test passed, {@code false} else.
     * @throws DeviceNotAvailableException
     */
    public boolean executeAssociationTest(ITestInvocationListener listener, String ssid)
            throws DeviceNotAvailableException {
        // Add bugreport listener for bugreport after each test case fails
        BugreportCollector bugListener = new
            BugreportCollector(listener, mTestDevice);
        bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);
        bugListener.setDescriptiveName(String.format("%s_%s", mCurrentTestAp.getBrandModel(),
                mTestDeviceCurrentBand));
        // Device may reboot during the test, to capture a bugreport after that,
        // wait for 30 seconds for device to be online, otherwise, bugreport will be empty
        bugListener.setDeviceWaitTime(30);

        // Decide whether to set frequency band
        // If the device is a single band device, no frequency band will be set
        String frequencyBand = null;
        if (mDeviceFrequencyBands.size() > 1) {
          // If the device has more than 1 frequency band, set to the current
          // frequency band.
          frequencyBand = mTestDeviceCurrentBand;
        }

        CollectingTestListener collectingListener = new CollectingTestListener();
        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                ASSOC_TEST_PACKAGE_NAME, ASSOC_TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(ASSOC_TEST_CLASS_NAME);
        runner.addInstrumentationArg("ssid", ssid);
        runner.addInstrumentationArg("security-type", mTestApSecurityType);
        runner.addInstrumentationArg("password", PASSWORD);
        if (frequencyBand != null) {
            runner.addInstrumentationArg("frequency-band", frequencyBand);
        }
        runner.setMaxTimeToOutputResponse(ASSOC_TEST_TIMER, TimeUnit.MILLISECONDS);
        mTestDevice.runInstrumentationTests(runner, listener, bugListener, collectingListener);

        TestRunResult testResults = collectingListener.getCurrentRunResults();
        return !testResults.hasFailedTests();
    }

    /**
     * Run ping test
     *
     * @throws DeviceNotAvailableException
     */
    public boolean executePingTest() throws DeviceNotAvailableException {
        for (int i = 0; i < 10; i++) {
            String command = String.format("ping -c 5 -w 10 %s", IotUtil.PING_SERVER);
            String pingOutput = mTestDevice.executeShellCommand(command);
            if (pingOutput.contains("5 packets transmitted, 5 received")) {
                return true;
            }
        }
        CLog.v("Can not ping host %s", IotUtil.PING_SERVER);
        return false;
    }

    /**
     * Run performance tests using iperf
     *
     * @throws DeviceNotAvailableException
     */
    public void executePerformanceTest() throws DeviceNotAvailableException {
        CLog.v("Start performance test");
        if(mTestDeviceCurrentBand.equals(DEVICE_WIFI_FREQUENCY_BAND_AUTO)) {
            CLog.v("Ignore performance test if device frequency is auto.");
            return;
        }
        mIperfTestRunner.setPerfTestIterations(mPerfIterations);
        double speed;
        SimpleStats speedData;
        TEST_CASE testCase = null;
        if (mTcpULTestFlag) {
            // tcp upload speed
            mIperfTestRunner.setTestTime(10);
            CLog.v("run tcp upload speed for %s_%s", mCurrentTestAp.getBrandModel(),
                    mTestDeviceCurrentBand);
            speed = mIperfTestRunner.getTcpUploadSpeed();
            speedData = mIperfTestRunner.getSpeeds();
            CLog.v("tcp upload speed: %f", speed);
            if (DEBUG) {
                printSpeeds(speedData);
            }
            testCase = TEST_CASE.getTestCase(mTestDeviceCurrentBand, IperfTestRunner.TCP,
                    IperfTestRunner.UPLOAD);
            storePerfTestResults(testCase, speed, speedData);
        }
        if (mTcpDLTestFlag) {
            // tcp download speed
            mIperfTestRunner.setTestTime(10);
            CLog.v("run tcp download speed for %s_%s", mCurrentTestAp.getBrandModel(),
                    mTestDeviceCurrentBand);
            speed = mIperfTestRunner.getTcpDownloadSpeed();
            speedData = mIperfTestRunner.getSpeeds();
            CLog.v("tcp download speed: %f", speed);
            if (DEBUG) {
                printSpeeds(speedData);
            }
            testCase = TEST_CASE.getTestCase(mTestDeviceCurrentBand, IperfTestRunner.TCP,
                    IperfTestRunner.DOWNLOAD);
            storePerfTestResults(testCase, speed, speedData);
        }
        if (mUdpULTestFlag) {
            // udp upload speed
            mIperfTestRunner.setTestTime(60);
            CLog.v("run udp upload speed for %s_%s", mCurrentTestAp.getBrandModel(),
                    mTestDeviceCurrentBand);
            speed = mIperfTestRunner.getUdpUploadSpeed();
            speedData = mIperfTestRunner.getSpeeds();
            CLog.v("udp upload speed: %f", speed);
            testCase = TEST_CASE.getTestCase(mTestDeviceCurrentBand, IperfTestRunner.UDP,
                    IperfTestRunner.UPLOAD);
            storePerfTestResults(testCase, speed, speedData);
        }
        if (mUdpDLTestFlag) {
            // udp download speed
            mIperfTestRunner.setTestTime(60);
            CLog.v("run udp download speed for %s_%s", mCurrentTestAp.getBrandModel(),
                    mTestDeviceCurrentBand);
            speed = mIperfTestRunner.getUdpDownloadSpeed();
            speedData = mIperfTestRunner.getSpeeds();
            CLog.v("udp download speed: %f", speed);
            testCase = TEST_CASE.getTestCase(mTestDeviceCurrentBand, IperfTestRunner.UDP,
                    IperfTestRunner.DOWNLOAD);
            storePerfTestResults(testCase, speed, speedData);
        }
    }

    /**
     * Save performance test results
     *
     * @param testCase test case name
     * @param speed average speed
     * @param speedData detailed speed data
     * @throws DeviceNotAvailableException
     */
    private void storePerfTestResults(TEST_CASE testCase, double speed, SimpleStats speedData)
                    throws DeviceNotAvailableException {
        recordApTestResult(TEST_TYPE.IOT_PERF, testCase, String.valueOf(speed));
        recordDetailedTestResult(testCase, String.valueOf(speed));
        recordDetailedPerfTestResult(testCase, speedData);
    }

    /**
     * Execute stress tests and collect test results
     *
     * @param listener invocation listener
     * @throws DeviceNotAvailableException
     */
    public void executeStressTest(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        String reportValue;
        boolean testResult;
        if (mBrowsingTestFlag) {
            testResult = executeBrowsingTest();
            if (!testResult) {
                CLog.v("Browsing test failed");
                reportValue = FAIL;
            } else {
                reportValue = PASS;
            }
            recordApTestResult(TEST_TYPE.IOT_STRESS, TEST_CASE.BROWSING_TEST, reportValue);
            recordDetailedTestResult(TEST_CASE.BROWSING_TEST, reportValue);
        }

        if (mDownloadingTestFlag) {
            testResult = executeFileDownloadTest(listener);
            if (!testResult) {
                CLog.v("File downloading test failed");
                reportValue = FAIL;
            } else {
                reportValue = PASS;
            }
            recordApTestResult(TEST_TYPE.IOT_STRESS, TEST_CASE.DOWNLOAD_TEST, reportValue);
            recordDetailedTestResult(TEST_CASE.DOWNLOAD_TEST, reportValue);
        }

        if (mVideoStreamingTestFlag) {
            testResult = executeVideoStreamingTest(listener);
            if (!testResult) {
                CLog.v("Video streaming test failed");
                reportValue = FAIL;
            } else {
                reportValue = PASS;
            }
            recordApTestResult(TEST_TYPE.IOT_STRESS, TEST_CASE.VIDEO_STREAMING_TEST, reportValue);
            recordDetailedTestResult(TEST_CASE.VIDEO_STREAMING_TEST, reportValue);
        }
    }

    /**
     * Browsing test by loading popular websites.
     *
     * @return {@code true} if browsing test passed, {@code false} if test failed.
     * @throws DeviceNotAvailableException
     */
    private boolean executeBrowsingTest() throws DeviceNotAvailableException {
        String urlsFilePath;
        String statusFilePath;

        // Pre-setup for browsing test
        String extStore = mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        urlsFilePath = String.format("%s/%s", extStore, URLS_FILE_NAME);
        statusFilePath = String.format("%s/%s", extStore, STATUS_FILE_NAME);
        if (!mTestDevice.doesFileExist(urlsFilePath)) {
            throw new RuntimeException("missing URL list file at: " + urlsFilePath);
        }
        mTestDevice.executeShellCommand("rm " + statusFilePath);

        // Create and config runner for instrumentation test
        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(CHROME_TEST_PACKAGE_NAME,
                CHROME_TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(CHROME_TEST_CLASS_NAME);
        runner.setMethodName(CHROME_TEST_CLASS_NAME, CHROME_TEST_METHOD_NAME);
        // max timeout is 1 minute per site
        runner.setMaxTimeToOutputResponse(60 * 1000 * TOTAL_SITES, TimeUnit.MILLISECONDS);
        CollectingTestListener collectingTestListener = new CollectingTestListener();

        // run the test and count failures
        boolean exitConditionMet = false;
        int failureCounter = 0;
        while (!exitConditionMet) {
            mTestDevice.runInstrumentationTests(runner, collectingTestListener);
            // if the status file exists, then the previous instrumentation has crashed
            if (!mTestDevice.doesFileExist(statusFilePath)) {
                exitConditionMet = true;
            } else {
                ++failureCounter;
            }
        }
        CLog.v("failureCounter: %d", failureCounter);
        // if all pages failed to load, the test failed.
        return failureCounter < TOTAL_SITES;
    }

    /**
     * Execute downloading test with a large file
     *
     * @param listener invocation listener
     * @return {@code true} if file downloading test passed , {@code false} if test failed.
     * @throws DeviceNotAvailableException
     */
    private boolean executeFileDownloadTest(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        IRemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(FILE_DOWNLOAD_PKG,
                DOWNLOAD_TEST_RUNNER_NAME, mTestDevice.getIDevice());
        testRunner.setMethodName(FILE_DOWNLOAD_CLASS, "runLargeDownloadOverWiFi");
        testRunner.addInstrumentationArg(EXTERNAL_DOWNLOAD_URI_KEY, mExternalDownloadUriValue);
        testRunner.setMaxTimeToOutputResponse(FILE_DOWNLOAD_TEST_TIMER, TimeUnit.MILLISECONDS);
        CollectingTestListener collectingListener = new CollectingTestListener();
        mTestDevice.runInstrumentationTests(testRunner, listener, collectingListener);
        return(!collectingListener.hasFailedTests());
    }

    /**
     * Video streaming test by streaming videos over network
     *
     * @param listener invocation listener
     * @return {@code true} if video streaming test passed, {@code false} if test failed.
     * @throws DeviceNotAvailableException
     */
    private boolean executeVideoStreamingTest(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(VIDEO_TEST_PACKAGE_NAME,
                VIDEO_TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.setClassName(VIDEO_TEST_CLASS_NAME);
        runner.addInstrumentationArg(MEDIA_SOURCE_KEY, mMediaSourceUriValue);
        runner.setMaxTimeToOutputResponse(VIDEO_TEST_TIMER, TimeUnit.MILLISECONDS);
        CollectingTestListener collectingListener = new CollectingTestListener();
        mTestDevice.runInstrumentationTests(runner, listener, collectingListener);
        return !collectingListener.hasFailedTests();
    }

    private void printSpeeds(SimpleStats data) {
        CLog.v("print speeds");
        for (Double d : data.getData()) {
            CLog.v("speed: %.2f", d);
        }
    }
    /**
     * Store test result for a test AP
     *
     * @param testType the test category: IOT_FUNCTIONAL, IOT_STRESS, IOT_PERF
     * @param testCase the test case
     * @param value the test result: PASS/FAIl for function/stress, speed value for performance
     */
    protected void recordApTestResult(TEST_TYPE testType, TEST_CASE testCase, String value) {
        if (testType == TEST_TYPE.IOT_PERF) {
            // for performance test, save the value
            if (Double.valueOf(value) > 0) {
                mApTestResult.put(testCase.name(), value);
            } else {
                CLog.v("performance data is not valid");
            }
        } else {
            // For functional test and stress test, count the total number of
            // tests that have passed.
            int result;
            if (PASS.equals(value)) {
                result = 1;
            } else {
                result = 0;
            }
            int newResult;
            String valueStr = mApTestResult.get(testType.name());
            if (valueStr != null) {
                newResult = Integer.parseInt(valueStr) + result;
            } else {
                // the very first test
                CLog.v("record the first test result");
                newResult = result;
            }
            mApTestResult.put(testType.name(), String.valueOf(newResult));
        }
    }

    /**
     * Helper function to record performance test results.
     *
     * @param testCase
     * @param data
     */
    protected void recordDetailedPerfTestResult(TEST_CASE testCase, SimpleStats data) {
        List<Double> dataList = data.getData();
        for (int i = 0; i < dataList.size(); i++) {
            Pair<Pair<Pair<String, String>, String>, String> pair = Pair.create(Pair.create(
                    Pair.create(mCurrentTestAp.getApKey(), mTestApSecurityType),
                    mTestDeviceCurrentBand), String.format("%s_iteration_%s", testCase.name(), i));
            mTestResult.put(pair, String.valueOf(dataList.get(i)));
        }
    }

    /**
     * Record test results for each test case and each test run in a hashmap.
     *
     * @param testCase test case
     * @param value test result
     */
    protected void recordDetailedTestResult(TEST_CASE testCase, String value) {
        Pair<Pair<Pair<String, String>, String>, String> pair = Pair.create(Pair.create(
                Pair.create(mCurrentTestAp.getApKey(), mTestApSecurityType),
                mTestDeviceCurrentBand), testCase.name());
        mTestResult.put(pair, value);
    }

    /**
     * Helper function to report test results.
     *
     * @param listener test invocation listener
     */
    public void reportTestResults(ITestInvocationListener listener) {
        if (mApTestResult != null) {
            String reportUnitKey = mCurrentTestAp.getApKey();
            // Create an empty testRun to report test results
            CLog.d("About to report metrics for %s: %s", mCurrentTestAp.getApKey(),
                    mApTestResult);
            listener.testRunStarted(reportUnitKey, 0);
            listener.testRunEnded(0, mApTestResult);
        } else {
            CLog.w("Not reporting test results - not test data");
        }
    }

    /**
     * Print detailed test results into a file
     *
     * @param listener
     */
    public void createTestReport(ITestInvocationListener listener) {
        StringBuilder resultString = new StringBuilder();
        for (Entry<Pair<Pair<Pair<String, String>, String>, String>, String> entry:
                mTestResult.entrySet()) {
            // entry: <<<brand_model_firmware, security_type>, band>, test case>, result
            Pair<Pair<Pair<String, String>, String>, String> keyPair = entry.getKey();
            Pair<String, String> brandModelFirmSecurity = keyPair.first.first;
            String band = keyPair.first.second;
            String testCase = keyPair.second;
            resultString.append(String.format("%s\t %s\t %s\t %s ", brandModelFirmSecurity.first,
                    brandModelFirmSecurity.second, band, testCase));
            String value = entry.getValue();
            String result;
            if (PASS.equals(value)) {
                result = "pass";
            } else if (FAIL.equals(value)) {
                result = "fail";
            } else {
                result = value;
            }
            resultString.append(result);
            resultString.append("\n");
        }
        // write the results into a file
        InputStreamSource outputSource = null;
        try {
            outputSource = new ByteArrayInputStreamSource(resultString.toString().getBytes());
            listener.testLog(TEST_REPORT, LogDataType.TEXT, outputSource);
        } finally {
            StreamUtil.cancel(outputSource);
        }
    }

    /**
     * Helper function to print test results. Used in debug mode.
     */
    protected void printTestResults() {
        for (Entry<Pair<Pair<Pair<String, String>, String>, String>, String> entry:
            mTestResult.entrySet()) {
            // entry: <<<brand_model_firmware, security_type>, band>, test case>, result
            Pair<Pair<Pair<String, String>, String>, String> keyPair = entry.getKey();
            Pair<String, String> brandModelFirmSecurity = keyPair.first.first;
            String band = keyPair.first.second;
            String testCase = keyPair.second;
            StringBuilder resultString = new StringBuilder();
            resultString.append(String.format("%s %s %s %s", brandModelFirmSecurity.first,
                    brandModelFirmSecurity.second, band, testCase));
            String value = entry.getValue();
            String result;
            if (PASS.equals(value)) {
                result = "pass";
            } else if (FAIL.equals(value)) {
                result = "fail";
            } else {
                result = value;
            }
            resultString.append(result);
            resultString.append("\n");
            CLog.v(resultString.toString());
        }
    }

    protected static IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
