/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.loganalysis.item.BugreportItem;
import com.android.loganalysis.parser.BugreportParser;
import com.android.loganalysis.rule.RuleEngine;
import com.android.loganalysis.rule.RuleEngine.RuleType;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.google.android.power.tests.PowerMonitor.AverageCurrentResult;
import com.google.android.power.tests.PowerMonitor.PowerInfo;

import org.json.JSONException;
import org.junit.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Power runner is the main test runner which do the followings:
 * 1) Start the UI automation test
 * 2) Start the monsoon power supply data collection.
 * 3) Merge the power supply data with the UI automation test result.
 * 4) Upload the power usage to dashboard.
 * <p/>
 * Note that this test will not run properly unless /sdcard is mounted and
 * writable.
 */
@OptionClass(alias = "power-test-runner")
public class PowerTestRunner implements IDeviceTest, IRemoteTest, IConfigurationReceiver {
    ITestDevice mTestDevice = null;

    @Option(name = "monsoon_serialno",
            description = "Unique monsoon serialno", mandatory = true)
    private String mMonsoonSerialno = null;

    @Option(name = "monsoon_voltage",
            description = "Set the monsoon voltage", mandatory = true)
    private float mMonsoonVoltage = 3.9f;

    @Option(name = "monsoon_samples",
            description = "The total number of the monsoon samples", mandatory = true)
    private long mMonsoonSamples = 1000;

    @Option(name = "ru_name",
            description = "Report unit name")
    private String mTestKey = null;

    @Option(
        name = "pre-test-nohup-command",
        description = "Run a adb shell command with nohup on device before power measurement starts"
    )
    private List<String> mPreTestCommands = new ArrayList<>();

    @Option(name = "test_case_name",
            description = "Specific test case name")
    private String mTestCaseName = null;

    // Options for the power test which drive from instrumentation.
    @Option(name = "test_class_name",
            description = "Instrumentation power test class name")
    private String mTestClassName = null;

    @Option(name = "test_package_name",
            description = "Instrumentation power test package name")
    private String mTestPackageName = "com.android.testing.uiautomation.platform.powertests";

    @Option(name = "test_runner_name",
            description = "Instrumentation power test runner name")
    private String mTestRunnerName = "com.android.testing.uiautomation.UiAutomationTestRunner";

    @Option(name = "emailer",
            description = "start the bulk emailer which send email in a specific interval")
    private boolean mEmailerOn = false;

    @Option(name = "report_unit_mw",
            description = "report the power usage in mW")
    private boolean mReportUnitMw = false;

    @Option(name = "battery_size",
            description = "device battery capacity")
    private int mBatterySize = 0;

    @Option(name = "report_battery_life",
            description = "report the battery life in hours")
    private boolean mReportyBatteryLife = false;

    @Option(name = "busybox-path",
            description = "Full path of busy-box on the device, this is needed to execute nohup")
    private String mBusyboxPath = "/data/local/busybox-android";

    @Option(name = "clockWork", description = "enable clockwork test.")
    private boolean mClockWork = false;

    @Option(name = "disable_clockwork_display", description = "disable clockwork display")
    private boolean mDisableClockworkDisplay = false;

    @Option(name = "baseline", description = "Post results to baseline target")
    private boolean mBaseline = false;

    @Option(name = "battery_saver_mode", description = "Enable battery saving mode")
    private boolean mBatterySaverMode = false;

    @Option(name = "smart_sampling",
            description = "Skip redundant data points in power chart to speed up loading time")
    private boolean mSmartSampling = false;

    @Option(name = "google-sync",
            description = "start the google sync to add contacts, calendar and email")
    private boolean mGoogleSync = false;

    @Option(
        name = "google-contacts-sync-details",
        description = "Contacts sync url and delay between contact sync"
    )
    private Map<String, Long> mGoogleContactsSyncDetails = new HashMap<>();

    @Option(
        name = "google-calendar-sync-details",
        description = "Contacts sync url and delay between calendar sync"
    )
    private Map<String, Long> mGoogleCalendarSyncDetails = new HashMap<>();

    @Option(
        name = "google-email-sync-details",
        description = "Email sync url and delay between email sync"
    )
    private Map<String, Long> mGoogleEmailSyncDetails = new HashMap<>();

    @Option(name = "use-power-reporter",
            description = "Use report metrics present in this file ")
    private boolean mUsePowerReporter = true;


    @Option(name = "keep_usb_connected",
            description = "The amount of milliseconds the usb connection will be kept after the test has started.")
    private int mKeepUsbConnected = 0;

    /**
     * Stores the test cases that we should consider running.
     */
    private TestInfo mTestCase = new TestInfo();

    private IConfiguration mConfig;

    private boolean mNohupPresent = false;

    private List<AverageCurrentResult> mPowerTestResult;

    static class TestInfo {
        public String mTestName = null;
        public String mClassName = null;
    }

    // Constants for running the tests

    // temp test result for each monsoon
    private static final String TEMP_TEST_OUTPUT_FILE = "autotester";
    private static final long NOHUP_CMD_WAIT_TIME = 5 * 1000;
    private static final long WAIT_TIME_FOR_DEVICE_STABLE = 5 * 60 * 1000;
    private static final long APP_START_TIMEOUT_SECS = 30;
    private static final String POWER_RESULT_FILE = "PowerMonsoonRawData";
    private static final String TEXT_EXTENTION = ".txt";
    private static final String DEVICE_TEST_LOG = "autotester.log";
    private static final String CAMERA_IMAGE_FOLDER = "${EXTERNAL_STORAGE}/DCIM";
    private static final String POWER_ANALYSIS_FILE = "PowerAnalysis";

    private static final String IDLE_TEST_CASE_SUFFIX = "Idle";
    private static final String IDLE_TEST_RU_SUFFIX = "Suspend";
    private static final String BATTERY_LIFE_RU_SUFFIX = "BatteryLife";
    private static final RuleType PARSER_RULE_TYPE = RuleType.POWER;

    @Override
    public void setConfiguration(IConfiguration configuration){
        mConfig = configuration;
    }

    /**
     * The test do the following:
     * 1)Start the uiautomation test in a new thread.
     * 2)Disconnect the usb.
     * 3)Collect the power usage.
     * 4)Resume the usb connection.
     * 5)Collect the power result.
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        List<PowerInfo> rawMonsoonData;
        List<PowerInfo> sortedPowerData;

        String deviceLogFile = String.format("%s/%s",
            mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE), DEVICE_TEST_LOG);

        mNohupPresent = isNohupPresent(mTestDevice);

        PowerUtil.setDeviceTime(mTestDevice);

        // Run adb shell commands with nohup before power measurement starts.
        // This allows issuing blocking adb process with nohup from the host side.
        // Since the adb command is run by nohup, the process will continue to run on device after
        // host side process is killed
        for (String cmd : mPreTestCommands) {
            try {
                String noHupCmd = String.format("nohup %s >/dev/null 2>&1", cmd);
                String serialNo = mTestDevice.getSerialNumber();
                String[] cmdStr = new String[] { "adb", "-s", serialNo, "shell", noHupCmd };

                CLog.d("About to run shell command on device %s: %s", serialNo, noHupCmd);
                Process p = RunUtil.getDefault().runCmdInBackground(cmdStr);

                // Destroy process on host side as it's not needed after the process is started
                // on device's side with nohup
                RunUtil.getDefault().sleep(NOHUP_CMD_WAIT_TIME);
                p.destroy();
            } catch (IOException e) {
                CLog.d(String.format("Error: %s", e.toString()));
            }
        }

        //Wait for device to stabilize
        RunUtil.getDefault().sleep(WAIT_TIME_FOR_DEVICE_STABLE);

        //Turn off the power wakelock explicitly
        mTestDevice.executeShellCommand("svc power stayon false");

        //Start the emailer if the option is turned on.
        if (mEmailerOn) {
            PowerUtil.startBulkEmailer(mConfig);
        }

        // Start the contacts/calendar/email sync if the option is enabled
        if (mGoogleSync) {
            final long testDurationMs = mMonsoonSamples * 100;
            PowerUtil.startGoogleSync(mGoogleContactsSyncDetails, testDurationMs);
            PowerUtil.startGoogleSync(mGoogleCalendarSyncDetails, testDurationMs);
            PowerUtil.startGoogleSync(mGoogleEmailSyncDetails, testDurationMs);
        }

        startInstrumentationPowerTest();

        //workaround to disable the clockwork display.
        if (mClockWork && mDisableClockworkDisplay) {
            //TODO: This is a haky way to sync between the test
            //and actual time to turn off the display. The test
            //will spend 2 seconds to do the setup before actually
            //run the test. So wait for 2 seconds before switching off
            //the display.
            RunUtil.getDefault().sleep(2 * 1000);
            CLog.i("Start turning off the display");
            mTestDevice.executeShellCommand("echo 1 > /sys/class/graphics/fb0/blank");
        }

        // Wait for test apk to launch
        long timeoutInSecs = APP_START_TIMEOUT_SECS;
        while (timeoutInSecs > 0) {
            if (null != getDevice().getProcessByName(mTestPackageName)) {
                break;
            }
            RunUtil.getDefault().sleep(5 * 1000);
            timeoutInSecs -= 5;
        }
        Assert.assertTrue("Test package did not start properly", timeoutInSecs > 0);
        RunUtil.getDefault().sleep(mKeepUsbConnected);
        PowerMonitor.disconnectUsb(mMonsoonSerialno);
        CLog.i("Start the monsoon : " + mMonsoonSerialno);
        rawMonsoonData = PowerMonitor.getPowerData(mMonsoonSerialno, mMonsoonVoltage,
                mMonsoonSamples);
        // Test finish. Resume the usb connection.
        PowerMonitor.connectUsb(mMonsoonSerialno);
        File sortedPowerResultOutput = null;
        File outputFile = null;

        try {
            outputFile = FileUtil.createTempFile(TEMP_TEST_OUTPUT_FILE, TEXT_EXTENTION);

            mTestDevice.pullFile(deviceLogFile, outputFile);

            // Get the sorted power result
            sortedPowerData = PowerMonitor.getSortedPowerResult(rawMonsoonData, outputFile);
            mPowerTestResult = PowerMonitor.getAveragePowerUsage(sortedPowerData);
            sortedPowerResultOutput = PowerMonitor.writeSortedPowerData(sortedPowerData);
            // Generate Power Chart HTML file
            PowerChart.createPowerChart(listener, mTestDevice, mMonsoonVoltage, mMonsoonSamples,
                    mPowerTestResult, sortedPowerData, mSmartSampling);
            // Parse output file and post data to dashboard.
            if (mUsePowerReporter) {
                parseOutputFiles(listener, mPowerTestResult, sortedPowerResultOutput);
            }
        } catch (IOException e) {
            CLog.e(e);
        } finally {
            cleanResultFile(deviceLogFile, outputFile, sortedPowerResultOutput);
        }
    }

    // Start the power test which drvie from instrumentation test.
    private void startInstrumentationPowerTest(){
        if (mTestCaseName != null && !mTestCaseName.isEmpty()) {
            mTestCase.mTestName = mTestCaseName;
            mTestCase.mClassName = String.format("%s.%s", mTestPackageName, mTestClassName);
        }

        // Start a new thread to run the instrumentation test.
        new Thread() {
            @Override
            public void run() {
                StringBuilder command = new StringBuilder();
                if (!mNohupPresent) {
                    command.append(mBusyboxPath + " ");
                }
                command.append("nohup am instrument -w -r ");
                if (mClockWork) {
                    command.append(" -e clockwork true ");
                }
                if (mBatterySaverMode) {
                    command.append(" -e batterySaverMode true ");
                }
                if (getAdditionalArgs() != null) {
                    command.append(getAdditionalArgs());
                }

                if (mTestCaseName != null && mTestClassName != null) {
                    command.append(String.format(" -e class %s.%s#%s ",
                            mTestPackageName, mTestClassName, mTestCaseName));
                } else if (mTestClassName != null) {
                    command.append(String.format(" -e class %s.%s ", mTestPackageName,
                            mTestClassName));
                }
                command.append(String.format(" %s/%s ",
                        mTestPackageName, mTestRunnerName));

                if (mNohupPresent) {
                    command.append(" >/dev/null 2>&1\n");
                }

                CLog.i("Run command " + command.toString());
                try {
                    mTestDevice.executeShellCommand(command.toString());
                } catch (DeviceNotAvailableException e) {
                    CLog.d("Device should be offline. Ignore the error.");
                }
            }
        }.start();
    }

    /**
     * Clean up the result files.
     */
    private void cleanResultFile(String deviceLogFile, File outputFile,
            File sortedPowerResultOutput) throws DeviceNotAvailableException {
        // Remove the autotester.log from the device
        mTestDevice.executeShellCommand(String.format("rm %s", deviceLogFile));
        mTestDevice.executeShellCommand(String.format("rm -r %s", CAMERA_IMAGE_FOLDER));
        // Remove the host log
        FileUtil.deleteFile(sortedPowerResultOutput);
        FileUtil.deleteFile(outputFile);
    }

    /**
     * Parse the test result and post to dashboard. For the "CommonUserActions"
     * test cases, it come with a pair of results. The RU for the screen on test
     * case is "CommonUserActions", while the idle case is
     * "CommonUserActionsSuspend"
     */
    private void parseOutputFiles(ITestInvocationListener listener,
            List<AverageCurrentResult> powerResults, File powerDataFile) {
        Map<String, String> runMetrics = new HashMap<>();
        Map<String, String> runIdleMetrics = new HashMap<>();
        Map<String, String> runHourMetrics = new HashMap<>();
        float averagePowerInMw = 0;
        InputStreamSource outputSource = null;

        BugreportItem bugreport = collectBugreport(listener,
                String.format("bugreport-%s.txt", mTestCase.mTestName));
        if (bugreport != null) {
            uploadPowerAnalysis(listener, getParsedData(bugreport));
        }

        // upload the sorted result file.
        try {
            outputSource = new FileInputStreamSource(powerDataFile);
            listener.testLog(String.format("%s%s", POWER_RESULT_FILE, TEXT_EXTENTION),
                    LogDataType.TEXT, outputSource);
        } finally {
            StreamUtil.cancel(outputSource);
        }

        // Post data
        for (AverageCurrentResult powerResult : powerResults) {
            CLog.d(String.format("Test case = %s", powerResult.mTestCase));
            CLog.d(String.format("average current in mA = %f", powerResult.mAverageCurrent));
            CLog.d(String.format("monsoon voltage = %s", mMonsoonVoltage));

            if (mReportUnitMw) {
                // Convert the reporting unit to mW and report to the dashboard as integer.
                averagePowerInMw = (mMonsoonVoltage * powerResult.mAverageCurrent);
                CLog.d(String.format("average current in mW = %f", averagePowerInMw));
            }

            if (mTestKey != null) {
                if (powerResult.mTestCase.contains(IDLE_TEST_CASE_SUFFIX) && mReportUnitMw) {
                    // Report idle metric in mW
                    runIdleMetrics.put(powerResult.mTestCase, String
                            .format("%.0f", averagePowerInMw));
                } else if (powerResult.mTestCase.contains(IDLE_TEST_CASE_SUFFIX)) {
                    // Report idle metric in mA
                    runIdleMetrics.put(powerResult.mTestCase, String
                            .format("%.0f", Float.valueOf(powerResult.mAverageCurrent)));
                } else if (mReportUnitMw) {
                    // Report regular metric in mW
                    runMetrics.put(powerResult.mTestCase, String
                            .format("%.0f", averagePowerInMw));
                } else {
                    // Report regular metric in mA
                    runMetrics.put(powerResult.mTestCase, String
                            .format("%.0f", Float.valueOf(powerResult.mAverageCurrent)));
                }
                // Battery life metric
                if (mReportyBatteryLife && mBatterySize != 0 && averagePowerInMw > 0 ) {
                    String batteryLife = String.format("%.2f", mBatterySize / averagePowerInMw);
                    runHourMetrics.put(powerResult.mTestCase, batteryLife);
                }
            }
            reportMetrics(listener, runMetrics, runIdleMetrics, runHourMetrics);
        }
    }

    /**
     * Collect bugreport
     */
    private BugreportItem collectBugreport(ITestInvocationListener listener, String bugreportName) {
        try (InputStreamSource bugreport = mTestDevice.getBugreport()) {
            listener.testLog(bugreportName, LogDataType.BUGREPORT, bugreport);
            return new BugreportParser().parse(new BufferedReader(new InputStreamReader(
                    bugreport.createInputStream())));
        } catch (IOException e) {
            CLog.e("Could not parse bugreport");
            return null;
        }
    }

    /**
     * Parse the bugreport
     */
    private String getParsedData(BugreportItem bugreportItem) {
        StringBuilder parsedData = new StringBuilder();
        if (getSummaryAnalysis(bugreportItem) != null) {
            parsedData.append("SUMMARY\n");
            parsedData.append(getSummaryAnalysis(bugreportItem));
            parsedData.append("\n\n");
        }
        if (getDetailedStats(bugreportItem) != null) {
            parsedData.append("DETAILED STATS\n");
            parsedData.append(getDetailedStats(bugreportItem));
            parsedData.append("\n");
        }
        return parsedData.toString().trim() + "\n";
    }

    private String getSummaryAnalysis(BugreportItem bugreportItem) {
        try {
            RuleEngine ruleEngine = new RuleEngine(bugreportItem);
            ruleEngine.registerRules(PARSER_RULE_TYPE);
            ruleEngine.executeRules();
            return ruleEngine.getAnalysis().toString(2);
        } catch (JSONException e) {
            // Ignore
        }
        return null;
    }

    private String getDetailedStats(BugreportItem bugreportItem) {
        try {
            return bugreportItem.getDumpsys().getBatteryStats().toJson().toString(2);
        } catch (JSONException e) {
            // Ignore
        }
        return null;
    }

    /**
     * Upload parsed power results
     */

    private void uploadPowerAnalysis(ITestInvocationListener listener, String powerAnalysis) {
        try (InputStreamSource source = new ByteArrayInputStreamSource(powerAnalysis.getBytes())) {
            listener.testLog(POWER_ANALYSIS_FILE, LogDataType.TEXT, source);
        }
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     * <p />
     * Exposed for unit testing
     */
    void reportMetrics(ITestInvocationListener listener, Map<String, String> metrics,
            Map<String, String> idleMetrics, Map<String, String> runHourMetrics) {
        Log.d("metrics: ", String.format("About to report metrics: %s", metrics));
        Log.d("metrics: ", String.format("About to report metrics: %s", idleMetrics));
        Log.d("metrics: ", String.format("About to report hour metrics: %s", runHourMetrics));
        if (metrics != null) {
            // Post the actions
            listener.testRunStarted(mTestKey, 0);
            listener.testRunEnded(0, metrics);
        }
        if (idleMetrics != null) {
            // Post the idle use case
            listener.testRunStarted(String.format("%s%s", mTestKey, IDLE_TEST_RU_SUFFIX), 0);
            listener.testRunEnded(0, idleMetrics);
        }
        if (runHourMetrics != null){
         // Post the idle use case
            listener.testRunStarted(String.format("%s%s", mTestKey, BATTERY_LIFE_RU_SUFFIX), 0);
            listener.testRunEnded(0, runHourMetrics);
        }
    }

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
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

    protected float getMonsoonVoltage() {
        return mMonsoonVoltage;
    }

    protected void setMonsoonVoltage(float mMonsoonVoltage) {
        this.mMonsoonVoltage = mMonsoonVoltage;
    }

    protected String getTestKey() {
        return mTestKey;
    }

    protected void setTestKey(String mTestKey) {
        this.mTestKey = mTestKey;
    }

    protected List<AverageCurrentResult> getPowerTestResult() {
        return mPowerTestResult;
    }

    protected void setPowerTestResult(List<AverageCurrentResult> mpowerTestResult) {
        this.mPowerTestResult = mpowerTestResult;
    }

    protected String getAdditionalArgs() {
        return null;
    }
}
