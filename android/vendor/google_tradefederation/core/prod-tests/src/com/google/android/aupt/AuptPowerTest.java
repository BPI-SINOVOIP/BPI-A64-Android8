// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.aupt;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.UiAutomatorTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.google.android.power.tests.PowerMonitor;
import com.google.android.power.tests.PowerMonitor.AverageCurrentResult;
import com.google.android.power.tests.PowerMonitor.PowerInfo;
import com.google.android.power.tests.PowerUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test harness for the Automated User profile tests (AUPT) with Power Measurement.
 * The test harness sets up the device, issues the command to start the test,
 * disconnect USB and measure power for the duration of the test.
 */
public class AuptPowerTest extends UiAutomatorTest {
    private static final String DEVICE_LOG_FILE = "power.log";
    private static final int SMALL_DELAY = 15 * 1000;
    private static final long WAIT_TIME_FOR_DEVICE_STABLE = 5 * 60 * 1000;
    private static final String UIAUTOMATOR_PATH = "/system/bin/uiautomator";

    @Option(name = "monsoon_serialno",
            description = "Unique monsoon serialno", mandatory = true)
    private String mMonsoonSerialno = null;

    @Option(name = "monsoon_voltage",
            description = "Set the monsoon voltage", mandatory = true)
    private float mMonsoonVoltage = 4.2f;

    @Option(name = "monsoon_samples",
            description = "The total number of the monsoon samples", mandatory = true)
    private long mMonsoonSamples = 5000;

    @Option(name = "ru_name",
            description = "Reporting name unit", mandatory = true)
    private String mTestKey = null;

    private String mDeviceLogFile;
    private List<PowerInfo> rawMonsoonPowerData;
    private Thread powerMeasurementThread;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {

        init();

        startTest(listener);

        postProcessPowerLogs(listener);
    }

    private void init() throws DeviceNotAvailableException {
        mDeviceLogFile = String.format("%s/%s",
            getDevice().getMountPoint(IDevice.MNT_EXTERNAL_STORAGE), DEVICE_LOG_FILE);

        CLog.i("Synchronize the time on the device and host");
        PowerUtil.setDeviceTime(getDevice());

        clearPowerLogs();

        CLog.i("Sleeping to ensure device gets stable");
        RunUtil.getDefault().sleep(WAIT_TIME_FOR_DEVICE_STABLE);
    }

    /**
     * Measure power consumption
     */
    private void measurePowerConsumption() {
        powerMeasurementThread = new Thread() {
            @Override
            public void run() {
                try {
                    // Wait  to ensure the test gets started on the device
                    RunUtil.getDefault().sleep(SMALL_DELAY);
                    // Disconnect USB
                    PowerMonitor.disconnectUsb(mMonsoonSerialno);

                    CLog.i("Start power monitoring");
                    rawMonsoonPowerData = getRawPowerData();
                    // Connect USB
                    PowerMonitor.connectUsb(mMonsoonSerialno);
                } catch (Exception e) {
                    CLog.i("Ignore the error");
                }
            }


        };
        powerMeasurementThread.setName("AuptPowerTest#powerMeasurementThread");
        powerMeasurementThread.setDaemon(true);
        powerMeasurementThread.start();
    }


    private void startTest(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        try {
            // Start the power consumption in a seperate thread
            measurePowerConsumption();
            // Trigger the tests on the device
            triggerTestOnDevice(listener);
            // Wait for the power measurement to complete
            powerMeasurementThread.join();
        } catch (InterruptedException e) {
            CLog.e("Interrupted exception");
        }
    }

    private void triggerTestOnDevice(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        StringBuilder command = new StringBuilder();
        List<String> paramsList = null;
        Map<String, String> paramsMap = null;
        command.append(UIAUTOMATOR_PATH);
        command.append(" runtest ");
        paramsList= getTestJarPaths();
        for (String jarPath : paramsList) {
            command.append(jarPath);
            command.append(" ");
        }
        paramsList = getClassNames();
        command.append(" -e class ");
        for (String testClass : paramsList) {
            command.append(testClass);
            command.append(",");
        }
        paramsMap = getTestRunArgMap();
        for (Map.Entry<String, String> entry : paramsMap.entrySet()) {
            command.append(" -e ");
            command.append(entry.getKey());
            command.append(" ");
            command.append(entry.getValue());
        }
        command.append(" --nohup");
        CLog.i("Start %s command  on the device", command.toString());
        getDevice().executeShellCommand(command.toString());
    }

    private void postProcessPowerLogs(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        List<PowerInfo> sortedPowerResult;
        List<AverageCurrentResult> powerTestResult;
        File sortedPowerResultOutput = null;
        try {
            // Get sorted power data
            sortedPowerResult = getSortedPowerData(rawMonsoonPowerData);

            powerTestResult = PowerMonitor.getAveragePowerUsage(sortedPowerResult);
            sortedPowerResultOutput = PowerMonitor.writeSortedPowerData(sortedPowerResult);

            uploadPowerResults(listener, powerTestResult, sortedPowerResultOutput);
        } catch (IOException e) {
            CLog.e("IOException %s", e.getMessage());
        } finally {
            FileUtil.deleteFile(sortedPowerResultOutput);
        }
    }

    /**
     * Get power consumption data
     */
    private List<PowerInfo> getRawPowerData() {
        List<PowerInfo> monsoonPowerData = PowerMonitor.getPowerData(mMonsoonSerialno,
                mMonsoonVoltage, mMonsoonSamples);
        return monsoonPowerData;
    }

   /**
    * Get sorted power data
    */
    private List<PowerInfo> getSortedPowerData(List<PowerInfo> rawMonsoonData) throws
            DeviceNotAvailableException {
        List<PowerInfo> sortedPowerResult= null;
        File outputFile = null;
        try {
            outputFile = FileUtil.createTempFile("power", ".txt");
            getDevice().pullFile(mDeviceLogFile, outputFile);
            // Get the sorted power result
            sortedPowerResult = PowerMonitor.getSortedPowerResult(rawMonsoonData, outputFile);
        } catch (IOException e) {
            CLog.e("IOException:%s", e.getMessage());
        }
        return sortedPowerResult;
    }

    private void uploadPowerResults(ITestInvocationListener listener,
            List<AverageCurrentResult> powerResults, File powerDataFile) {
        float averagePowerInMw = 0;
        Map<String, String> resultMap = new HashMap<>();
        CLog.i("Number of power results:%d", powerResults.size());
        for (AverageCurrentResult powerResult : powerResults) {
            CLog.i(String.format("Test case = %s", powerResult.mTestCase));
            CLog.i(String.format("average current in mA = %f", powerResult.mAverageCurrent));
            averagePowerInMw = mMonsoonVoltage * powerResult.mAverageCurrent;
            resultMap.put(powerResult.mTestCase, String.format("%.0f", averagePowerInMw));
        }
        uploadPowerResultFile(listener, powerDataFile);
        listener.testRunStarted(mTestKey, 0);
        listener.testRunEnded(0, resultMap);
    }

    private void uploadPowerResultFile(ITestInvocationListener listener, File powerDataFile) {
        InputStreamSource inputSource = null;
        try {
            inputSource = new FileInputStreamSource(powerDataFile);
            listener.testLog("MonsoonLog.txt", LogDataType.TEXT, inputSource);
        } finally {
            StreamUtil.cancel(inputSource);
        }
    }

    private void clearPowerLogs() throws DeviceNotAvailableException {
        getDevice().executeShellCommand(String.format("rm %s", mDeviceLogFile));
    }
}
