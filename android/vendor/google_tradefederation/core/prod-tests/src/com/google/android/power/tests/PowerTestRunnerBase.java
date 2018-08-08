/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.power.tests;

import com.google.android.power.collectors.MultiMetricsCollector;
import com.google.android.power.collectors.PowerTestLog;
import com.google.android.tradefed.util.AttenuatorController;
import com.google.android.tradefed.util.AttenuatorState;
import com.google.android.utils.usb.switches.IUsbSwitch;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.BulkEmailer;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/** Base class for running power tests with Tradefed: */
public class PowerTestRunnerBase
        implements IInvocationContextReceiver, IRemoteTest, IConfigurationReceiver {

    public static final String LAB_SETUP_MAP_FILE_OPTION = "peripherals-map-file";
    // TODO(b/67715975): Rename this option to monsoon-voltage.
    public static final String MONSOON_VOLTAGE_OPTION = "monsoon_voltage";
    public static final String MONSOON_LIB_PATH_OPTION = "monsoon-lib-path";
    public static final String TIGERTOOL_PATH_OPTION = "tigertool-path";

    private static final float DEFAULT_TEST_FAILURE_VALUE = -1f;

    @Option(name = TIGERTOOL_PATH_OPTION, description = "the path to tigertool.py")
    protected String mTigertoolPath;

    @Option(name = "tigertail-serial", description = "the serial port of a tigertail")
    protected String mTigertailSerial;

    @Option(
        name = "use-tigertail",
        description = "Whether should this test use tigertail. Default value is false."
    )
    protected boolean mUseTigertail = false;

    // Options for the power test which derive from instrumentation.
    // TODO(b/67715975): Rename this option to test_method.
    @Option(
        name = "test_method",
        description = "Test method name. Will be ignored if multiple classes are specified"
    )
    private List<String> mTestMethod = new ArrayList<String>();

    // TODO(b/67715975): Rename this option to test-class.
    @Option(
        name = "test_class",
        description = "Instrumentation power test class name. Support multiple classes."
    )
    private List<String> mTestClasses = new ArrayList<String>();

    // TODO(b/67715975): Rename this option to test-package.
    @Option(
        name = "test_package",
        description = "Instrumentation power test package name",
        mandatory = true
    )
    private String mTestPackage = "com.android.testing.platform.powertests";

    // TODO(b/67715975): Rename this option to test-runner.
    @Option(
        name = "test_runner",
        description = "Instrumentation power test runner name",
        mandatory = true
    )
    private String mTestRunner = "android.test.InstrumentationTestRunner";

    // TODO(b/67715975): Rename this option to test-arguments.
    @Option(name = "test_arguments", description = "Test package arguments")
    private Map<String, String> mTestArguments = new HashMap<String, String>();

    // TODO(b/67715975): Rename this option to test-duration.
    @Option(name = "test_duration", description = "Test duration in seconds")
    private Map<String, Long> mTestDurations = new HashMap<String, Long>();

    // TODO(b/67715975): Rename this option to device-stabilize-time.
    @Option(
        name = "device_stabilize_time",
        description = "Wait time in seconds after set up before starting instrumentation test"
    )
    protected long mDeviceStablizeTimeSecs = 5 * 60;

    // TODO(b/67715975): Rename this option to wait-for-disconnect-signal.
    @Option(
        name = "wait_for_disconnect_signal",
        description =
                "Test runner will listen for instrumentation test USB disconnection"
                        + " signal before disconnecting USB"
    )
    protected boolean waitForUSBdisconnect = true;

    @Option(
        name = LAB_SETUP_MAP_FILE_OPTION,
        description =
                "Path to a file containing a map of device's serial - peripherals' descriptors map"
    )
    protected String mLabSetupMap = null;

    // TODO(b/67715975): Rename this option to disconnect-signal-timeout.
    @Option(
        name = "disconnect_signal_timeout",
        description =
                "Wait time in seconds test runner will listen for instrumentation test "
                        + "USB disconnection signal before disconnecting USB"
    )
    private long usbDisconnectionSignalTimeSecs = 3 * 60; // 3 min

    // TODO(b/67715975): Rename this option to report-unit-mw
    @Option(name = "report_unit_mw", description = "report the power usage in mW")
    protected boolean mReportInmW = true;

    // TODO(b/67715975): Rename this option to battery-size
    @Option(name = "battery_size", description = "device battery capacity")
    protected int mBatterySize = 0;

    // TODO(b/67715975): Rename this option to report-battery-life
    @Option(name = "report_battery_life", description = "report the battery life in hours")
    protected boolean mReportBatteryLife = false;

    // TODO(b/67715975): Rename this option to schema-ru-pair
    @Option(name = "schema_ru_pair", description = "Schema to Reporting Unit mapping")
    protected Map<String, String> mSchemaRUPair = new HashMap<String, String>();

    // TODO(b/67715975): Rename this option to ru-suffix.
    @Option(name = "ru_suffix", description = "Suffix to be appended in RU")
    protected String mRuSuffix = "";

    // TODO(b/67715975): Rename this option to schema-suffix
    @Option(name = "schema_suffix", description = "Suffix to be appended in schema")
    protected String mSchemaSuffix = "";

    @Option(name = "pre-test-adb-command",
            description = "Run an adb shell blocking command before power measurement starts")
    private List<String> mPreTestForegroundCommands = new ArrayList<String>();

    @Option(name = "pre-test-nohup-command",
            description = "Run an adb shell command with nohup before power measurement starts")
    private List<String> mPreTestBackgroundCommands = new ArrayList<String>();

    @Option(name = "pre-test-host-command",
            description = "Run an shell on TF host command before power measurement starts")
    private List<String> mPreTestHostCommands = new ArrayList<String>();

    @Option(
        name = "post-test-adb-command",
        description = "Run an adb shell blocking command after power measurement ends"
    )
    private List<String> mPostTestForegroundCommands = new ArrayList<String>();

    @Option(name = "post-test-nohup-command",
            description = "Run an adb shell command with nohup after power measurement ends")
    private List<String> mPostTestCommands = new ArrayList<String>();

    @Option(name = "post-test-host-command",
            description = "Run an shell on TF host command after power measurement ends")
    private List<String> mPostTestHostCommands = new ArrayList<String>();

    // TODO(b/67715975): Rename this option to enable-tcpdump
    @Option(name = "enable_tcpdump", description = "Log network traffic packets during the test")
    private boolean mEnableTcpDump = false;

    @Option(name = "emailer", description = "Configure bulk emailer.")
    private boolean mEmailerOn = false;

    @Option(name = "dou-test-weights", description = "Weights for dou test cases")
    Map<String, Double> mDouWeightsMap = new HashMap<String, Double>();

    @Option(
        name = "signal-strength-controller",
        description = "Type of signal controller to be used. (Options are: none, cellular, wifi)"
    )
    private List<String> mSSCType = new ArrayList<>();

    @Option(name = "cellular-att-ip", description = "IP assigned to the cellular attenuator.")
    private String mCellularAttIp = null;

    @Option(name = "wifi-att-ip",
            description = "IP assigned to the wifi attenuator.")
    private String mWifiAttIp = null;

    @Option(
        name = "cellular-att-port",
        description = "Port to communicate with cellular attenuator."
    )
    private int mCellularAttPort = 23;

    @Option(name = "wifi-att-port", description = "Port to communicate with wifi attenuator.")
    private int mWifiAttPort = 23;

    @Option(
        name = "cellular-att-channel",
        description =
                "Attenuator channel number in case a multi channel attenuator is being "
                        + "used. Leave undefined if a single channel attenuator is being used."
    )
    private Integer mCellularAttChannel = null;

    @Option(
        name = "wifi-att-channel",
        description =
                "Attenuator channel number in case a multi channel attenuator is being "
                        + "used. Leave undefined if a single channel attenuator is being used."
    )
    private Integer mWifiAttChannel = null;

    @Option(
        name = "cellular-att-level",
        description =
                "Signal level for the cellular attenuator, it could be any integer "
                        + "between 0 and 95. The higher the number the weaker the signal."
    )
    private List<Integer> mCellularAttLevels = new ArrayList<>();

    @Option(
        name = "decimal-places",
        description = "Number of decimal places to round results to. Default value is 2."
    )
    protected Integer mDecimalPlaces = 2;

    @Option(
        name = "wifi-att-level",
        description =
                "Signal level for the wifi attenuator, it could be any integer "
                        + "between 0 and 95. The higher the number the weaker the signal."
    )
    private List<Integer> mWifiAttLevels = new ArrayList<>();

    @Option(
        name = "cellular-att-duration",
        description = "Durations in seconds for the respective cellular attenuation levels."
    )
    private List<Integer> mCellularAttDurations = new ArrayList<>();

    @Option(
        name = "wifi-att-duration",
        description = "Durations in seconds for the respective wifi attenuation levels."
    )
    private List<Integer> mWifiAttDurations = new ArrayList<>();

    @Option(
        name = "repeat-cellular-att-pattern",
        description =
                "Specifies if the attenuation pattern is gonna be repeated until the "
                        + "test ends or not. If this is set to 'false' after the pattern is completed"
                        + "the last attenuation level will be held for the rest of the test. Default "
                        + "value: true."
    )
    private boolean mRepeatCellularAttPattern = true;

    @Option(name = "repeat-wifi-att-pattern",
            description = "Specifies if the attenuation pattern is gonna be repeated until the "
                    + "test ends or not. If this is set to 'false' after the pattern is completed"
                    + "the last attenuation level will be held for the rest of the test. Default "
                    + "value: true.")
    private boolean mRepeatWifiAttPattern = true;

    @Option(
        name = MONSOON_LIB_PATH_OPTION,
        description = "Path to the monsoon power monitor library"
    )
    protected String mMonsoonLibPath = "/google/data/ro/teams/tradefed/testdata/power/monsoon";

    // TODO(b/67715975): Rename this option to monsoon-serial
    // internal naming convention. Do the same in SweetberryTestRunner.
    @Option(name = "monsoon_serialno", description = "Unique monsoon serialno")
    protected String mMonsoonSerialNo = null;

    @Option(name = MONSOON_VOLTAGE_OPTION, description = "Set the monsoon voltage")
    protected Float mMonsoonVoltage = null;

    @Option(name = "usb-switch-type", description = "USB hub type, e.g. NCD, Datamation")
    protected String mUSBHubType = null;

    @Option(name = "usb-switch-port-id", description = "Unique usb hub port id")
    protected String mUSBHubPortID = null;

    @Option(
        name = "primary-device-name",
        description =
                "Specifies which device name is the primary device for multi-device tests. "
                        + "This string is not required for single device tests."
    )
    private String mPrimaryDeviceName = null;

    @Option(
        name = "lower-limit",
        description =
                "Defines a lower limit for a metric. Key: final "
                        + "schema name, value: the lower limit."
    )
    protected Map<String, Double> mLowerLimits = new HashMap<String, Double>();

    @Option(
        name = "upper-limit",
        description =
                "Defines an upper limit for a metric. Key: final "
                        + "schema name, value: the upper limit."
    )
    protected Map<String, Double> mUpperLimits = new HashMap<String, Double>();

    private ITestDevice mTestDevice = null;
    private ITestDevice mCompanionTestDevice = null;
    protected ITestInvocationListener mListener;
    protected IConfiguration mConfig;
    private SignalStregthControllerCollection mSignalStrengthController =
            new SignalStregthControllerCollection();

    private boolean mNohupPresent = false;
    private static final String BUSYBOXPATH = "/data/local/busybox-android";

    private static final long MAX_HOST_DEVICE_TIME_OFFSET = 5 * 1000; // 5 sec
    private static final long POLL_INTERVAL = 500; // 500 ms
    private static final long SHELLCMDTIMEOUT = 5 * 1000; // 5 sec
    private static final long ADB_CMD_TIMEOUT = 60 * 1000; // 60 sec
    private static final int ADB_CMD_RETRY = 5;
    private static final String DISCONNECTUSBFILE = "disconnectusb.log";
    private static final String SCREENSHOTS_DIRECTORY = "test_screenshots";
    private static final String NOHUP_LOG = "nohup.log";
    private static final String DEVICE_TEST_LOG = "autotester.log";
    private static final String DEVICE_TCPDUMP_LOG = "packet.pcap";
    protected String mSDcardPath = null;
    private IBuildInfo mBuildInfo;
    private IBuildInfo mCompanionBuildInfo;
    private IUsbSwitch mUsbSwitch;
    private final MultiMetricsCollector mMultiMetricsCollector = new MultiMetricsCollector();

    public IConfiguration getConfiguration() {
        return mConfig;
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }

    /** {@inheritDoc} */
    @Override
    public void setInvocationContext(IInvocationContext invocationContext) {
        switch (invocationContext.getNumDevicesAllocated()) {
            case 1:
                mTestDevice = invocationContext.getDevices().get(0);
                mBuildInfo = invocationContext.getBuildInfo(mTestDevice);
                break;
            case 2:
                Assert.assertNotNull("Primary device not specified.", mPrimaryDeviceName);
                for (ITestDevice device : invocationContext.getDevices()) {
                    if (invocationContext.getDeviceName(device).equals(mPrimaryDeviceName)) {
                        mTestDevice = device;
                        mBuildInfo = invocationContext.getBuildInfo(device);
                    } else {
                        mCompanionTestDevice = device;
                        mCompanionBuildInfo = invocationContext.getBuildInfo(device);
                    }
                }
                if (mTestDevice == null) {
                    throw new RuntimeException("No primary device found.");
                }

                if (mCompanionTestDevice == null) {
                    throw new RuntimeException("No companion device found.");
                }
                CLog.d(
                        "Primary device: %s. Companion device: %s.",
                        mTestDevice.getSerialNumber(), mCompanionTestDevice.getSerialNumber());
                break;
            default:
                String errMsg =
                        String.format(
                                "Power test only supports 1 device or 1 device + 1 companion. "
                                        + "%d device(s) is currently not supported.",
                                invocationContext.getNumDevicesAllocated());
                throw new IllegalArgumentException(errMsg);
        }
    }

    public ITestDevice getDevice() {
        return mTestDevice;
    }

    public ITestDevice getCompanionDevice() {
        return mCompanionTestDevice;
    }

    protected IBuildInfo getBuildInfo() {
        return mBuildInfo;
    }

    protected IBuildInfo getCompanionBuildInfo() {
        return mCompanionBuildInfo;
    }

    protected void setUp() throws DeviceNotAvailableException {
        defineSignalStrengthController();
        mSDcardPath = getDevice().getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        getDevice().setDate(null);

        // Clean previous test files if exist
        cleanTestFile(String.format("%s/%s", mSDcardPath, DEVICE_TEST_LOG));
        cleanTestFile(String.format("%s/%s", mSDcardPath, DISCONNECTUSBFILE));
        cleanTestFile(String.format("%s/%s", mSDcardPath, NOHUP_LOG));
        cleanTestFile(String.format("%s/%s", mSDcardPath, SCREENSHOTS_DIRECTORY));

        if (!isTimeSynced(getDevice())) {
            Assert.fail("Fails to reset the device clock");
            return;
        }

        mNohupPresent = isNohupPresent(getDevice());

        startDebugDataLogging();
        runPreConditionForegroundCommands();
        runPreConditionBackgroundCommands();
        runPreConditionHostCommands();

        // Wait for device to stabilize
        CLog.d("Waiting for device to stabilize for %d seconds", mDeviceStablizeTimeSecs);
        RunUtil.getDefault().sleep(mDeviceStablizeTimeSecs * 1000);
        CLog.v("Resuming test");

        //Start the emailer if the option is turned on.
        if (mEmailerOn) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        BulkEmailer.loadMailer(getConfiguration()).sendEmails();
                    } catch (Exception e) {
                        Assert.fail("Unable to set up BulkEmailer.");
                    }
                }
            }.start();
        }
    }

    private void defineSignalStrengthController() {
        for (String type : mSSCType) {
            switch (type) {
                case "wifi":
                    if (mWifiAttIp == null) {
                        throw new RuntimeException("IP for wifi attenuator is required");
                    }
                    addAttenuator(
                            mWifiAttIp,
                            mWifiAttPort,
                            mWifiAttChannel,
                            mWifiAttLevels,
                            mWifiAttDurations,
                            mRepeatWifiAttPattern);
                    break;
                case "cellular":
                    if (mCellularAttIp == null) {
                        throw new RuntimeException("IP for cellular attenuator is required");
                    }
                    addAttenuator(
                            mCellularAttIp,
                            mCellularAttPort,
                            mCellularAttChannel,
                            mCellularAttLevels,
                            mCellularAttDurations,
                            mRepeatCellularAttPattern);
                    break;

                default:
                    String message =
                            String.format(
                                    "Unsupported option [%s] as " + "signal-strength-controller",
                                    mSSCType);
                    throw new IllegalArgumentException(message);
            }
            CLog.d("Using option [%s] for signal-strength-controller", mSSCType);
        }
    }

    private void addAttenuator(
            String attIP,
            int attPort,
            Integer attChannel,
            List<Integer> attLevels,
            List<Integer> attDurations,
            boolean attRepeatPattern) {
        AttenuatorController controller =
                new AttenuatorController(
                        attIP, attPort, attChannel, buildAttenuatorStates(attLevels, attDurations));
        controller.setRepeat(attRepeatPattern);
        controller.setGradualAttenuations(true);
        controller.setGradualStepSize(1);
        controller.setGradualStepWait(1000);
        mSignalStrengthController.add(controller);
    }

    private List<AttenuatorState> buildAttenuatorStates(
            List<Integer> levels, List<Integer> durations) {
        List<AttenuatorState> states = new ArrayList<>();

        Assert.assertTrue("Number of levels must be greater than zero", levels.size() > 0);
        Assert.assertEquals("number of durations must be equal to number of levels",
                levels.size(), durations.size());

        for (int i = 0; i < levels.size(); i++) {
            states.add(new AttenuatorState(levels.get(i), durations.get(i) * 1000));
        }

        return states;
    }


    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mListener = listener;
        setUp();
        startInstrumentationTest();
        RunUtil.getDefault().sleep(1000);

        if (!waitForUSBdisconnect || waitForDisconnectSignal()) {
            disconnectUsb();
            mSignalStrengthController.run();
            measurePower();
            connectUsb();
            reportResults();
        }

        tearDown();
    }

    private void disconnectUsb() {
        getUsbSwitch().disconnectUsb();
        RunUtil.getDefault().sleep(5 * 1000);

        // Tool failure
        Assert.assertEquals(
                "USB was not properly disconnected.",
                TestDeviceState.NOT_AVAILABLE,
                getDevice().getDeviceState());
    }

    protected void tearDown() throws DeviceNotAvailableException {
        mSignalStrengthController.stop();
        runPostConditionForegroundCommands();
        runPostConditionCommands();
        runPostConditionHostCommands();
        postDebugDatalog();
        cleanTestFiles();
    }

    private void invalidateTests() {
        if (!mTestClasses.isEmpty()) {
            for (String schema : mSchemaRUPair.keySet()) {
                String ruName = mSchemaRUPair.get(schema);

                // Report all the schema/RU as failures
                String finalRu = PostingHelper.appendSuffix(ruName, mRuSuffix);
                String finalSchema = PostingHelper.appendSuffix(schema, mSchemaSuffix);
                PostingHelper.postResult(
                        mListener, finalRu, finalSchema, DEFAULT_TEST_FAILURE_VALUE);
            }
        }
    }

    protected IUsbSwitch getUsbSwitch() {
        if (mUsbSwitch != null) {
            return mUsbSwitch;
        }

        try {
            PowerTestUsbSwitchProvider.Builder builder = PowerTestUsbSwitchProvider.Builder();
            builder.deviceSerial(getDevice().getSerialNumber())
                    .tigertoolPath(mTigertoolPath)
                    .tigertailSerial(mTigertailSerial)
                    .labSetupMapFilePath(mLabSetupMap)
                    .monsoonSerial(mMonsoonSerialNo)
                    .monsoonLibPath(mMonsoonLibPath)
                    .monsoonVoltage(mMonsoonVoltage);

            if (mUseTigertail) {
                builder.useTigertail(PowerTestUsbSwitchProvider.UseTigertailOption.USE_TIGERTAIL);
            } else {
                builder.useTigertail(
                        PowerTestUsbSwitchProvider.UseTigertailOption.DO_NOT_USE_TIGERTAIL);
            }
            if (mUSBHubType != null && "datamation".equals(mUSBHubType.toLowerCase())) {
                builder.datamationPort(mUSBHubPortID);
            }
            if (mUSBHubType != null && "ncd".equals(mUSBHubType.toLowerCase())) {
                builder.ncdPort(mUSBHubPortID);
            }

            mUsbSwitch = builder.build().getUsbSwitch();
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(
                    "Couldn't retrieve usb switch due to a file not "
                            + "file not found exception. Is the peripherals-map-file correct?",
                    e);
        }

        return mUsbSwitch;
    }

    private static final int RECONNECTION_ATTEMPTS = 5;
    private static final int POLLING_TIMES = 240;
    private static final long HALF_SECOND = 500;
    private static final long SHORT_WAIT = 5 * 1000;

    private void connectUsb() {
        // It might take long for a device whose battery was fully drained to show online.
        // we poll the device for up to two minutes checking if it is online.
        for (int i = 0; i < RECONNECTION_ATTEMPTS; i++) {
            getUsbSwitch().connectUsb();

            // Extra polling times will increase 20% each boot attempt.
            int extraPollingtimes = (POLLING_TIMES * i) / RECONNECTION_ATTEMPTS;
            for (int j = 0; j < POLLING_TIMES + extraPollingtimes; j++) {
                if (getDevice().getDeviceState().equals(TestDeviceState.ONLINE)) {
                    // Everything ok, device was recovered.
                    return;
                }

                RunUtil.getDefault().sleep(HALF_SECOND);
            }
            CLog.v("Couldn't connect usb. Trying to reconnect.");
            getUsbSwitch().disconnectUsb();
            RunUtil.getDefault().sleep(SHORT_WAIT);
        }

        // Tool failure.
        Assert.fail("USB was not properly connected.");
    }

    void measurePower() {
        mMultiMetricsCollector.start();
        mMultiMetricsCollector.stop();
    }

    // TODO(htellez) Create different metrics collector for DoU.
    // TODO(htellez) Create different metrics collector to report battery life in hours.
    // TODO(htellez) Create different metrics collector to report the result of BugreportAnalyzer.
    // TODO(htellez) MonsoonMetricsCollector should do all the conversions.
    void reportResults() throws DeviceNotAvailableException {
        for (PowerTestLog log : mMultiMetricsCollector.getLogs()) {
            PostingHelper.postFile(mListener, log.getLogFile(), log.getDataType(), log.getName());
        }

        // The keys from this map are the schemas.
        Map<String, Double> metrics = mMultiMetricsCollector.getMetrics();
        for (String schema : metrics.keySet()) {
            String ruName = mSchemaRUPair.get(schema);
            final String finalRu = PostingHelper.appendSuffix(ruName, mRuSuffix);
            final String finalSchema = PostingHelper.appendSuffix(schema, mSchemaSuffix);

            Double result = metrics.get(schema);

            // Rounds result to a given number of decimal places.
            double pow = Math.pow(10, mDecimalPlaces);
            result = Math.round(result * pow) / pow;
            PostingHelper.postResult(
                    mListener,
                    finalRu,
                    finalSchema,
                    result,
                    getLowerLimit(finalSchema),
                    getUpperLimit(finalSchema));
        }
    }

    private double getUpperLimit(String finalSchema) {
        if (mUpperLimits == null || !mUpperLimits.containsKey(finalSchema)) {
            return Double.MAX_VALUE;
        }

        return mUpperLimits.get(finalSchema);
    }

    private double getLowerLimit(String finalSchema) {
        if (mLowerLimits == null || !mLowerLimits.containsKey(finalSchema)) {
            return -Double.MAX_VALUE;
        }

        return mLowerLimits.get(finalSchema);
    }

    /*
     * Start the instrumentation test on device
     */
    private void startInstrumentationTest() {
        // Launch instrumentation test with nohup
        StringBuilder command = new StringBuilder();

        command.append("am instrument -w -r ");

        for (String testArguments : mTestArguments.keySet()) {
            command.append(
                    String.format(
                            " -e %s \"%s\"", testArguments, mTestArguments.get(testArguments)));
        }

        for (String testCase : mTestDurations.keySet()) {
            command.append(String.format(" -e %s %d", testCase, mTestDurations.get(testCase)));
        }

        // Run specific test method only when test method is specified
        if (mTestClasses.size() == 1 && mTestMethod.size() > 0) {
            command.append(String.format(" -e class %s.%s#%s", mTestPackage, mTestClasses.get(0),
                    mTestMethod.get(0)));

            for (int i = 1; i < mTestMethod.size(); i++) {
                command.append(String.format(",%s.%s#%s", mTestPackage, mTestClasses.get(0),
                        mTestMethod.get(i)));
            }
        } else {
            // Ignore mTestMethod as multiple test classes are specified
            // The tests' durations will need to come from mTestDurations
            // The test methods in the class should control which arguments to pick up by themselves
            for (int i = 0; i < mTestClasses.size(); i++) {
                command.append(String.format(" -e class %s.%s", mTestPackage, mTestClasses.get(i)));
            }
        }

        command.append(String.format(" %s/%s ", mTestPackage, mTestRunner));

        // Start a new process to run command in adb
        // TestDevice.executeShellCommand is not used because it will try to recover device if
        // shell command is timeout
        executeAdbCommand(getDevice(), command.toString());
        CLog.d("Command is %d characters long", command.toString().length());
    }

    /**
     * Helper method to listen for the existence of the disconnectusb log
     */
    protected boolean waitForDisconnectSignal() {
        try {
            CLog.d("Listening for USB disconnect signal for %d seconds",
                    usbDisconnectionSignalTimeSecs);
            String disconnectFileName = String.format("%s/%s", mSDcardPath, DISCONNECTUSBFILE);
            long pollStart = System.currentTimeMillis();

            // TestDevice.doesFileExist takes ~400ms to execute, so it's really polling in ~1s
            // interval
            while (System.currentTimeMillis() < pollStart + usbDisconnectionSignalTimeSecs * 1000) {
                if (getDevice().doesFileExist(disconnectFileName)) {
                    CLog.d("USB disconnect signal received.");
                    return true;
                }

                RunUtil.getDefault().sleep(POLL_INTERVAL);
            }

            // Test APK did not send USB disconnect signal and should report as Test Failure
            CLog.d("No USB disconnect signal received before timeout");
            invalidateTests();
        } catch (DeviceNotAvailableException e) {
            Assert.fail("Device offline while waiting for USB disconnect signal");
        }

        return false;
    }

    /**
     * Wrapper for running debug datalogging
     */
    private void startDebugDataLogging() throws DeviceNotAvailableException {
        if (mEnableTcpDump) {
            // Enable tcpdump if the option is enabled
            startTcpDump();
        }
    }

    /*
     * Helper method to start tcpdump
     */
    private void startTcpDump() throws DeviceNotAvailableException {
        CLog.d("Starting tcpdump on device");

        String tcpDumpLogPath = String.format("%s/%s", mSDcardPath, DEVICE_TCPDUMP_LOG);

        // Clean prior tcpdump if exists
        cleanTestFile(tcpDumpLogPath);

        // Run tcpdump with:
        // -i any: captures packets from all interfaces
        // -p: no promiscuous mode
        // -s 0: default snapshot length of 65535
        String tcpDumpCMD = String.format("tcpdump -i any -p -s 0 -w %s", tcpDumpLogPath);

        executeAdbCommand(getDevice(), tcpDumpCMD);

        if (!getDevice().doesFileExist(tcpDumpLogPath)) {
            CLog.e("tcpdump did not start successfully");
        }
    }

    /**
     * Run blocking adb shell commands prior to power measurements
     */
    private void runPreConditionForegroundCommands() throws DeviceNotAvailableException {
        for (String cmd : mPreTestForegroundCommands) {
            executeCommand(cmd);
        }
    }

    /** Run blocking adb shell commands post power measurements */
    private void runPostConditionForegroundCommands() throws DeviceNotAvailableException {
        for (String cmd : mPostTestForegroundCommands) {
            executeCommand(cmd);
        }
    }

    private void executeCommand(String cmd) throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        getDevice()
                .executeShellCommand(
                        cmd, receiver, ADB_CMD_TIMEOUT, TimeUnit.SECONDS, ADB_CMD_RETRY);
        String output = receiver.getOutput();
        CLog.d("%s on %s returned %s", cmd, getDevice().getSerialNumber(), output);
    }

    /**
     * Run background adb shell commands with nohup during power measurements
     */
    private void runPreConditionBackgroundCommands() {
        for (String cmd : mPreTestBackgroundCommands) {
            executeAdbCommand(getDevice(), cmd);
        }
    }

    /**
     * Run background host shell commands before power measurements
     */
    private void runPreConditionHostCommands() {
        for (String cmd : mPreTestHostCommands) {
            executeHostCommand(formatCommand(cmd));
        }
    }

    /**
     * Run background host shell commands after power measurements
     */
    private void runPostConditionHostCommands() {
        for (String cmd : mPostTestHostCommands) {
            executeHostCommand(formatCommand(cmd));
        }
    }

    /**
     * Run adb shell commands after power measurements
     */
    private void runPostConditionCommands() throws DeviceNotAvailableException {
        if (mEnableTcpDump) {
            String cmd = "pkill tcpdump";
            executeAdbCommand(getDevice(), cmd);
        }

        // Turn off the power wakelock explicitly
        getDevice().executeShellCommand("svc power stayon false");

        for (String cmd : mPostTestCommands) {
            executeAdbCommand(getDevice(), cmd);
        }
    }

    private void postDebugDatalog() throws DeviceNotAvailableException {
        // Post tcpdump if the option is enabled
        if (mEnableTcpDump) {
            postTcpDump();
        }

        postNohupLog();

        // Tests screenshots
        postScreenshotsDirectory(String.format("%s/%s", mSDcardPath, SCREENSHOTS_DIRECTORY));
    }

    private void postNohupLog() throws DeviceNotAvailableException {
        File hostFile = PowerAnalyzer.pullFile(getDevice(), mSDcardPath, NOHUP_LOG);
        PostingHelper.postFile(mListener, hostFile, LogDataType.TEXT, NOHUP_LOG);
    }

    private void postTcpDump() throws DeviceNotAvailableException {
        File hostFile = PowerAnalyzer.pullFile(getDevice(), mSDcardPath, DEVICE_TCPDUMP_LOG);
        PostingHelper.postFile(mListener, hostFile, LogDataType.UNKNOWN, DEVICE_TCPDUMP_LOG);
    }

    private void postScreenshotsDirectory(String directoryPath) throws DeviceNotAvailableException {
        if (!getDevice().doesFileExist(directoryPath)) {
            CLog.d("Couldn't find screenshots directory: %s", directoryPath);
            return;
        }

        CLog.d("Posting screenshots' files");
        PowerAnalyzer.postFilesFromDevice(
                getDevice(), mListener, String.format("%s/*uix", directoryPath), LogDataType.XML);
        PowerAnalyzer.postFilesFromDevice(
                getDevice(), mListener, String.format("%s/*png", directoryPath), LogDataType.PNG);
        CLog.d("Done posting screenshots' files from: %s", directoryPath);
    }

    /**
     * Helper method to validate device time is synced with host time
     */
    private static boolean isTimeSynced(ITestDevice testDevice) throws DeviceNotAvailableException {
        String deviceTime = testDevice.executeShellCommand("date +%s");
        java.util.Date date = new java.util.Date();
        long hostTime = date.getTime();
        long offset = 0;

        if (deviceTime != null) {
            offset = hostTime - (Long.valueOf(deviceTime.trim()) * 1000);
        }
        CLog.d("Time offset = " + offset);
        if (Math.abs(offset) > MAX_HOST_DEVICE_TIME_OFFSET) {
            return false;
        }
        return true;
    }

    /**
     * Determine whether nohup is installed on the device.
     */
    private boolean isNohupPresent(ITestDevice device) throws DeviceNotAvailableException {
        String output = device.executeShellCommand("which nohup");

        // If which command is found
        if (!output.contains("which: not found")) {
            // Output should be "/path/to/nohup" or ""
            return output.trim().endsWith("nohup");
        }

        // Check if nohup is installed at /system/bin/
        output = device.executeShellCommand("ls /system/bin/nohup");
        return !output.contains("No such file");
    }

    /**
     * Helper method to execute adb command with nohup
     */
    protected void executeAdbCommand(ITestDevice testDevice, String cmd) {
        try {
            StringBuilder noHupCmd = new StringBuilder();

            if (!mNohupPresent) {
                noHupCmd.append(BUSYBOXPATH + " ");
            }

            noHupCmd.append(String.format("nohup %s >> %s/%s 2>&1", cmd, mSDcardPath, NOHUP_LOG));
            String serialNo = testDevice.getSerialNumber();
            String[] cmdStr = new String[] { "adb", "-s", serialNo, "shell", noHupCmd.toString() };

            CLog.d("Run command on device %s: %s", serialNo, noHupCmd.toString());
            Process p = RunUtil.getDefault().runCmdInBackground(cmdStr);

            // Destroy process on host side as it's not needed after the process is started
            // on device's side with nohup
            RunUtil.getDefault().sleep(SHELLCMDTIMEOUT);
            p.destroy();
        } catch (IOException e) {
            CLog.d(String.format("Error: %s", e.toString()));
        }
    }

    /**
     * Helper method to execute shell command
     */
    protected void executeHostCommand(String cmd) {
        CLog.d("Running command on host %s", cmd);
        String[] runCmd = new String[] {"/bin/bash", "-c", cmd};
        CommandResult result = RunUtil.getDefault().runTimedCmd(6 * SHELLCMDTIMEOUT, runCmd);
        CLog.d(
                String.format(
                        "returned - stdout: %s stderr: %s",
                        result.getStdout(), result.getStderr()));
    }

    // Helper method to check whether file exists in host
    protected boolean isFilePresentInHost(String file) {
        String[] cmdStr = new String[] { "ls", file };
        CommandResult result = RunUtil.getDefault().runTimedCmd(SHELLCMDTIMEOUT, cmdStr);

        return !result.getStderr().toString().contains("No such file");
    }

    protected void cleanTestFile(String filePath) throws DeviceNotAvailableException {
        if (getDevice().doesFileExist(filePath)) {
            getDevice().executeShellCommand(String.format("rm -rf %s", filePath));
        }
    }

    protected void cleanTestFiles() throws DeviceNotAvailableException {
        cleanTestFile(String.format("%s/%s", mSDcardPath, DEVICE_TEST_LOG));
        cleanTestFile(String.format("%s/%s", mSDcardPath, NOHUP_LOG));
        cleanTestFile(String.format("%s/%s", mSDcardPath, SCREENSHOTS_DIRECTORY));
    }

    protected File extractAndPostPowerLog() throws DeviceNotAvailableException {
        File timeStampFile =
                PowerAnalyzer.pullFile(
                        getDevice(),
                        getDevice().getMountPoint(IDevice.MNT_EXTERNAL_STORAGE),
                        DEVICE_TEST_LOG);
        PostingHelper.postFile(mListener, timeStampFile, LogDataType.TEXT, DEVICE_TEST_LOG);
        return timeStampFile;
    }

    /**
     * Extracts the timestamp objects from the power log file.
     *
     * @param timeStampFile The power log file.
     * @return
     * @throws DeviceNotAvailableException
     */
    protected List<PowerTimestamp> extractTimestamps(File timeStampFile) {
        InputStreamReader powerLogStream = null;

        try {
            powerLogStream = new InputStreamReader(new FileInputStream(timeStampFile));
        } catch (FileNotFoundException e) {
            CLog.e("Device timestamp log not found.");
            CLog.e(e);
        }

        return new PowerLogParser(powerLogStream).getPowerTimestamps();
    }

    private String formatCommand(String input) {
        input = input.replaceAll("\\$serial", getDevice().getSerialNumber());
        return input;
    }
}
