/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.ddmlib.Log;
import com.android.ddmlib.MultiLineReceiver;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This runner control the real battery drain test by doing the followings: 1)
 * Start the UI automation test 2) Disconnect the usb connection 3) Wait for a
 * hard coded battery projection time. 4) Connect the usb and parse the battery
 * life from dumpsys batterystats.
 */
public class BatteryDrainRunner implements IDeviceTest, IRemoteTest, IConfigurationReceiver {
    ITestDevice mTestDevice = null;

    @Option(name = "usb-switch-port-id",
            description = "Unique usb switch port id", mandatory = true)
    private String mUSBSwitchPortID = null;

    @Option(name = "ru-name",
            description = "Report unit name", mandatory = true)
    private String mRuName = null;

    @Option(name = "test-case-name",
            description = "Specific test case name", mandatory = true)
    private String mTestCaseName = null;

     @Option(name = "test_package_name",
            description = "Instrumentation power test package name")
    private String mTestPackageName = "com.android.testing.uiautomation.platform.powertests";

    @Option(name = "test_class_name",
            description = "Instrumentation power test class name")
    private String mTestClassName = null;

    @Option(name = "test_runner-name",
            description = "Instrumentation power test runner name")
    private String mTestRunnerName = "com.android.testing.uiautomation.UiAutomationTestRunner";

    @Option(name = "emailer",
            description = "start the bulk emailer which send email in a specific interval")
    private boolean mEmailerOn = false;

    @Option(name = "projected-battery-life",
            description = "The projected battery life in ms", mandatory = true)
    private int mProjectedBatteryLife = 10 * 60 * 60 * 1000; // 10 hours

    @Option(name = "report-projected",
            description = "Report projected battery life based on drain rate below 95% battery level")
    private boolean mReportProjected = false;

    @Option(name = "drain-duration",
            description = "The duration to drain battery for report-projected option")
    private int mDrainDuration = 20 * 60 * 60 * 1000; // 20 hours

    @Option(name = "skip-drain-log-check",
            description = "Skip the battery drain log checking for suspend use case")
    private boolean mSkipDrainLogCheck = false;

    @Option(name = "clockWork", description = "enable clockwork test.")
    private boolean mClockWork = false;

    @Option(name = "busybox-path",
            description = "Full path of busy-box on the device, this is needed to execute nohup")
    private String mBusyboxPath = "/data/local/busybox-android";

    @Option(name = "usb-switch-type",
            description = "USB switch type, e.g.NCD, Datamation")
    private String mUSBSwitch = "NCD";

    @Option(name = "schema-name",
            description = "rdb schema name, set it to test case name if it is null")
    private String mSchemaName = null;


    /**
     * Stores the test cases that we should consider running.
     */
    private IConfiguration mConfig;

    private boolean mNohupPresent = false;

    // Constants for running the tests
    private static final String SD_CARD_PATH = "${EXTERNAL_STORAGE}/";
    private static final String CAMERA_IMAGE_FOLDER = "${EXTERNAL_STORAGE}/DCIM";
    private static final String BATTERY_LIFE_CMD = "dumpsys batterystats";
    private final static String BATTERY_DRAIN_FILE = "batteryDrain";
    private final static String LOG_EXTENSION = "log";
    private final static String TAG = "BatteryDrainTest";
    private final static double TEST_TOLERENCE = 0.75; // 45 mins
    private static final long CMD_TIME_OUT = 30 * 1000; //30 seconds
    private static final long WAIT_FOR_DEVICE_ONLINE = 20 * 60 * 1000; //20 mins
    private static final long SHORT_WAIT = 10 * 1000; //10 secs
    private static final long WAIT_FOR_DEVICE_BOOTUP = 60 * 1000; //60 secs
    private static final long RETRY_ATTEMPTS = 3;

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }

    /**
     * Class used to read and parse micro_bench output.
     */
    private static class BatteryLifeReceiver extends MultiLineReceiver {
        private static final Pattern TIME_ON_BATTERY_PATTERN =
                Pattern.compile("^Time on battery:\\s*((\\d+)d\\s*)?(\\d+)h\\s*(\\d+)m.*");
        private static final Pattern DISCHARGE_STEP_PATTERN =
                Pattern.compile("^.*: \\+((\\d+)h)?((\\d+)m)?(\\d+)s.* to (\\d+).*");
        private Integer mDays = null;
        private Integer mHours = null;
        private Integer mMins = null;

        // Total time counted for drain
        private boolean mReportProjected = false;
        private int mDrainDurationInSecs = 0;
        private int mDischargedPercent = 0;

        /**
         * {@inheritDoc}
         */
        @Override
        public void processNewLines(String[] lines) {
            Integer dischargedHours = null;
            Integer dischargedMins = null;
            Integer dischargedSecs = null;
            Integer batteryPercent = null;

            // It's possible to drop more than 1% at each step, need to keep track of start/end
            int dischargedStart = 0;
            int dischargedEnd = 99;
            int[] dischargeStepDuration = new int[100];
            for (int i = 0; i < 100; i++) {
                dischargeStepDuration[i] = 0;
            }

            for (String line : lines) {
                // Look for battery life from since last charge statistics.
                // Time on battery: 19h 34m 14s 435ms (100.0%)
                Matcher m = TIME_ON_BATTERY_PATTERN.matcher(line);
                if (m.matches()) {
                    if(m.group(2) != null){
                        mDays = Integer.parseInt(m.group(2));
                    }
                    mHours = Integer.parseInt(m.group(3));
                    mMins = Integer.parseInt(m.group(4));
                }

                if (mReportProjected) {
                    // Look for discharge step durations
                    // #11: +41m15s772ms to 75 (screen-off, power-save-off, device-idle-off)
                    Matcher dischargeStep = DISCHARGE_STEP_PATTERN.matcher(line);
                    if (dischargeStep.matches()) {
                        if (dischargeStep.group(2) != null) {
                            dischargedHours = Integer.parseInt(dischargeStep.group(2));
                        } else {
                            dischargedHours = 0;
                        }
                        if (dischargeStep.group(4) != null) {
                            dischargedMins = Integer.parseInt(dischargeStep.group(4));
                        } else {
                            dischargedMins = 0;
                        }
                        dischargedSecs = Integer.parseInt(dischargeStep.group(5));
                        batteryPercent = Integer.parseInt(dischargeStep.group(6));

                        if (batteryPercent > 99 || batteryPercent < 0) {
                            Assert.fail(
                                    String.format("Invalid battery percent: %d", batteryPercent));
                        }

                        // Discharge step duration could be repeated in Daily stats
                        // Only take the first (latest) statistics
                        if (dischargeStepDuration[batteryPercent] == 0) {
                            dischargeStepDuration[batteryPercent] = dischargedHours * 3600
                                    + dischargedMins * 60 + dischargedSecs;
                        }
                    }
                }
            }

            if (mReportProjected) {
                // Prevent counting the duration multiple times
                if (mDrainDurationInSecs == 0) {
                    // Start counting from 95% - #xx: +xxmxxsxxxms to 94 (screen-off,
                    // power-save-off)
                    for (int i = 94; i >= 0; i--) {
                        // In case test did not start from 100% battery
                        if (dischargeStepDuration[i] != 0) {
                            mDrainDurationInSecs += dischargeStepDuration[i];
                            if (i > dischargedStart) {
                                dischargedStart = i;
                            }
                            if (i < dischargedEnd) {
                                dischargedEnd = i;
                            }
                        }
                    }
                }

                // Calculate the drain % based on the highest and lowest reported battery %
                if (mDrainDurationInSecs > 0 && mDischargedPercent == 0) {
                    mDischargedPercent = dischargedStart - dischargedEnd + 1;
                }
            }
        }

        public double getBatteryLife() {
            if (mHours == null) {
                Assert.fail("Battery life should be greater than 1 hour");
            }
            int secs = 0;
            if (mMins != null) {
                secs += mMins * 60;
            }
            if (mHours != null) {
                secs += mHours * 3600;
            }
            if(mDays != null) {
                secs += mDays * 24 * 3600;
            }
            return secs / 3600.0;
        }

        // Return projected battery life in hours
        public double getProjectedBatteryLife() {
            CLog.i("Device discharged %d percent in %d secs.", mDischargedPercent,
                    mDrainDurationInSecs);
            if (mDischargedPercent <= 0) {
                Assert.fail("Battery life discharged less than 1% when battery is below 96%");
            }
            return mDrainDurationInSecs / 3600.0 * 100.0 / mDischargedPercent;
        }

        public void setReportProjected(boolean reportProjected) {
            mReportProjected = reportProjected;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    /**
     * The test do the following:
     * 1)Start the uiautomation test in a new thread.
     * 2)Disconnect the usb.
     * 3)Wait until the device's battery is fully drained.
     * 4)Resume the usb connection.
     * 5)Collect the power result.
     * @param listener the {@link ITestInvocationListener} of test results
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // Start the emailer if the option is turned on.
        if (mEmailerOn) {
            PowerUtil.startBulkEmailer(mConfig);
        }

        PowerUtil.setDeviceTime(mTestDevice);
        mNohupPresent = isNohupPresent(mTestDevice);

        startInstrumentationPowerTest();
        disconnectUsb();
        if (mReportProjected) {
            // Wait for the drain duration.
            RunUtil.getDefault().sleep(mDrainDuration);
        } else {
            // Wait for the project battery life time.
            RunUtil.getDefault().sleep(mProjectedBatteryLife);
        }
        // Test finish. Resume the usb connection.
        connectUsb();
        // Retry few times to ensure the device comes up
        for (int i = 0; i < RETRY_ATTEMPTS; i++) {
            if (mTestDevice.waitForDeviceShell(WAIT_FOR_DEVICE_ONLINE)) {
                CLog.i("Device is available");
                // Wait for device to fully boot up
                RunUtil.getDefault().sleep(WAIT_FOR_DEVICE_BOOTUP);
                break;
            }
            disconnectUsb();
            // Short sleep to ensure the ports are toggled
            RunUtil.getDefault().sleep(SHORT_WAIT);
            connectUsb();
            RunUtil.getDefault().sleep(SHORT_WAIT);
        }

        Assert.assertNotNull(mTestDevice.getProductVariant());
        // Turn off display to enable faster charging
        mTestDevice.executeShellCommand("svc power stayon false");

        parseOutput(listener);
        cleanResultFile();
    }

    private void disconnectUsb() {
        if (mUSBSwitch.equalsIgnoreCase("Datamation")) {
            getRunUtil().runTimedCmd(CMD_TIME_OUT, "u16s.py", "-d", mUSBSwitchPortID, "-m o");
        } else {
            getRunUtil().runTimedCmd(CMD_TIME_OUT, "ncd.py", "-d", mUSBSwitchPortID, "-f");
        }
    }

    private void connectUsb() {
        if (mUSBSwitch.equalsIgnoreCase("Datamation")) {
            getRunUtil().runTimedCmd(CMD_TIME_OUT, "u16s.py", "-d", mUSBSwitchPortID, "-m s");
        } else {
            getRunUtil().runTimedCmd(CMD_TIME_OUT, "ncd.py", "-d", mUSBSwitchPortID, "-n");
        }
    }

    private static IRunUtil getRunUtil(){
        return RunUtil.getDefault();
    }

    // Start the power test which drvie from instrumentation test.
    private void startInstrumentationPowerTest(){
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

                command.append(String.format("-e class %s.%s#%s %s/%s",
                        mTestPackageName, mTestClassName, mTestCaseName,
                        mTestPackageName, mTestRunnerName));

                if (mNohupPresent) {
                    command.append(" >/dev/null 2>&1\n");
                }

                CLog.i("Run command " + command);
                try {
                    mTestDevice.executeShellCommand(command.toString());
                } catch (DeviceNotAvailableException e) {
                    CLog.d("Device should be offline. Ignore the error.");
                }
            }
        }.start();
    }

    /***
     * Parse the last line of the test duration log file as
     * the total test duration.
     * @return Test duration in hour
     */
    private Double getTestDurationInHours() {
        double testDuration = -1;
        try {
            File outputFile = FileUtil.createTempFile(BATTERY_DRAIN_FILE, LOG_EXTENSION);
            mTestDevice.pullFile(String.format("%s%s.%s", SD_CARD_PATH, BATTERY_DRAIN_FILE,
                    LOG_EXTENSION), outputFile);
            try (Scanner in = new Scanner(new FileReader(outputFile))) {
                while (in.hasNextLine()) {
                    try {
                        testDuration = Double.valueOf(in.nextLine()) / 60.0;
                    } catch (NumberFormatException e) {
                        Log.e(TAG, e.toString());
                        return null;
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            return null;
        } catch (DeviceNotAvailableException e) {
            Log.e(TAG, e.toString());
            return null;
        }
        return testDuration;
    }

    /**
     * Parse the Time on battery life from dumpsys batterystats
     * and report to dashboard.
     * @param listener
     * @throws DeviceNotAvailableException
     */
    private void parseOutput(ITestInvocationListener listener) throws DeviceNotAvailableException {
        double batteryLife = 0;
        BatteryLifeReceiver batReceiver = new BatteryLifeReceiver();
        batReceiver.setReportProjected(mReportProjected);
        Map<String, String> runMetrics = new HashMap<String, String>();
        mTestDevice.executeShellCommand(BATTERY_LIFE_CMD, batReceiver);

        // Grab a bugreport right after the test and upload the log.
        try (InputStreamSource bugreport = mTestDevice.getBugreport()) {
            listener.testLog(
                    String.format("bugreport-BatteryDrain-%s.txt", mTestCaseName),
                    LogDataType.BUGREPORT,
                    bugreport);
        }

        if (mReportProjected) {
            batteryLife = batReceiver.getProjectedBatteryLife();
            // TODO: Check test actually ran properly

            // Sanity check
            if (batteryLife > mProjectedBatteryLife * 1.2) {
                CLog.i("Aggregated battery life is much longer than projected battery life");
                batteryLife = -1.0;
            }
        } else {
            // Convert the batterylife into hours.
            batteryLife = batReceiver.getBatteryLife();
            if (!mSkipDrainLogCheck) {
                Double testDuration = getTestDurationInHours();
                // Test duration should be within 1 hour difference.
                Log.v(TAG, String.format("Uiautomator test duration %.2f hours",
                        testDuration.doubleValue()));
                // If the difference between the test duration and battery life is > 45 mins
                // return -1 as invalid test result.
                if (testDuration == null ||
                        (Math.abs(batteryLife - testDuration)) > TEST_TOLERENCE) {
                    batteryLife = -1.0;
                }
            }
        }

        // Use the test case name as schema by default
        String schemaName = mTestCaseName;
        if (mSchemaName != null) {
            schemaName = mSchemaName;
        }

        runMetrics.put(schemaName, String.format("%.2f", Double.valueOf(batteryLife)));
        reportMetrics(listener, runMetrics);
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     *
     * @param listener the {@link ITestInvocationListener} of test results
     * @param metrics the {@link Map} that contains metrics for the given test
     */

    void reportMetrics(ITestInvocationListener listener, Map<String, String> metrics) {
        Log.d("metrics: ", String.format("About to report metrics: %s", metrics));
        if (metrics != null) {
            // Post the actions
            listener.testRunStarted(mRuName, 0);
            listener.testRunEnded(0, metrics);
        }
    }

    /**
     * Clean up the result files.
     */
    private void cleanResultFile() throws DeviceNotAvailableException {
        mTestDevice.executeShellCommand(String.format("rm -r %s", CAMERA_IMAGE_FOLDER));
        mTestDevice.executeShellCommand(String.format("rm %s%s.%s", SD_CARD_PATH,
                BATTERY_DRAIN_FILE, LOG_EXTENSION));
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
}
