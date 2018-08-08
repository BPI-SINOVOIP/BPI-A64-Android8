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

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import java.util.ArrayList;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is for the monsoon power data collecting.
 */
public class PowerMonitor{
    // Monsoon options
    private static final String MONSOON_SERIALNO = "--serialno";
    private static final String MONSOON_VOLTAGE = "--voltage";
    private static final String MONSOON_USB_PASSTHROUGH = "--usbpassthrough";
    private static final String MONSOON_SAMPLES = "--samples";
    private static final String MONSOON_FREQUENCY = "--hz";
    private static final String MONSOON_SAMPLE_FREQUENCY = "10";

    private static final String TEST_START_TAG = "AUTOTEST_TEST_BEGIN";
    private static final String TEST_END_TAG = "AUTOTEST_TEST_SUCCESS";
    private static final String POWER_DATA_TAG = "PowerSample";
    private static final String POWER_RESULT_FILE = "PowerTestResult_";
    private static final long CMD_TIME_OUT = 30 * 1000;

    public static class PowerInfo {
        public long mTimeStamp = 0;
        public String mPowerData = null;
        public String mTag = null;
    }

    public static class AverageCurrentResult {
        public String mTestCase = null;
        public float mAverageCurrent = 0;
    }

    private static String[] createMonsoonCommand(String serialno, float voltage, long samples) {
        String monsoonVoltage = String.format("%.2f", voltage);
        String monsoonSamples = String.format("%d", samples);
        return new String[] {
                "monsoon", MONSOON_SERIALNO, serialno, MONSOON_VOLTAGE,
                monsoonVoltage, MONSOON_FREQUENCY, MONSOON_SAMPLE_FREQUENCY,
                MONSOON_SAMPLES, monsoonSamples};
    }
    /**
     * Start the monsoon and collect the raw power data. The data is written to
     * a list with the format: timestamp, test tag, power data
     *
     * @return The list which store the time stamp, tag and instantaneous current.
     */
    public static List<PowerInfo> getPowerData(String serialno, float voltage, long samples) {
        List<PowerInfo> powerData = new ArrayList<PowerInfo>();
        String[] monsoonCmd = createMonsoonCommand(serialno, voltage, samples);
        CLog.d("Run monsoon command : %s", java.util.Arrays.toString(monsoonCmd));
        try {
            Process pr = Runtime.getRuntime().exec(monsoonCmd);
            BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            String line;
            while ((line = input.readLine()) != null) {
                try {
                    PowerInfo powerInfo = new PowerInfo();
                    powerInfo.mTimeStamp = System.currentTimeMillis();
                    // round the power data to integer
                    powerInfo.mPowerData = String.format("%.2f",
                            (Float.valueOf(line.trim()) * 1000));
                    powerInfo.mTag = POWER_DATA_TAG;
                    powerData.add(powerInfo);
                } catch (NumberFormatException e) {
                    CLog.e("Fails to capture the monsoon data %s", e.toString());
                }
            }
            int exitVal = pr.waitFor();
            if (exitVal != 0) {
                CLog.e("Exited with error code " + exitVal);
            }
        } catch (IOException e) {
            CLog.e("Fails to capture the monsoon data %s", e.toString());
        } catch (InterruptedException e) {
            CLog.e("Fails to capture the monsoon data %s", e.toString());
        }
        return powerData;
    }

    public static float getCurrentConsumption(String testcase, String mMonsoonSerialno,
            float mMonsoonVoltage, long mMonsoonSamples) {
        return getCurrentConsumption(testcase, mMonsoonSerialno, mMonsoonVoltage,
                mMonsoonSamples, true);
    }

    public static float getCurrentConsumption(String testcase, String mMonsoonSerialno,
            float mMonsoonVoltage, long mMonsoonSamples, boolean USBResetFlag) {
         List<PowerInfo> rawMonsoonData, processedMonsoonData;
        PowerInfo powerData = new PowerInfo();
        powerData.mTag = TEST_START_TAG;
        powerData.mPowerData= testcase;
        processedMonsoonData = new ArrayList<PowerInfo>();
        processedMonsoonData.add(powerData);
        RunUtil.getDefault().sleep(5000);
        if (USBResetFlag) {
            disconnectUsb(mMonsoonSerialno);
        }
        CLog.i("Start the monsoon : " + mMonsoonSerialno);
        rawMonsoonData = getPowerData(mMonsoonSerialno, mMonsoonVoltage,
                mMonsoonSamples);

        // Test finish. Resume the usb connection.
        if (USBResetFlag) {
            connectUsb(mMonsoonSerialno);
        }

        for(PowerInfo monsoonSamples : rawMonsoonData) {
            processedMonsoonData.add(monsoonSamples);
        }

        powerData = new PowerInfo();
        powerData.mTag = TEST_END_TAG;
        powerData.mPowerData= testcase;
        processedMonsoonData.add(powerData);

        List<AverageCurrentResult> averageCurrentList =
                getAveragePowerUsage(processedMonsoonData);
        return averageCurrentList.get(0).mAverageCurrent;
    }

    /**
     * Disconnect the usb
     *
     * @param serialno monsoon serialno
     */
    public static void disconnectUsb(String serialno) {
        getRunUtil().runTimedCmd(CMD_TIME_OUT, "monsoon", MONSOON_SERIALNO, serialno,
                MONSOON_USB_PASSTHROUGH, "off");
    }

    /**
     * Resume usb connection
     *
     * @param serialno monsoon serialno
     */
    public static void connectUsb(String serialno) {
        // TODO: Retry couple time to make sure the adb is alive.
        getRunUtil().runTimedCmd(CMD_TIME_OUT, "monsoon", MONSOON_SERIALNO, serialno,
                MONSOON_USB_PASSTHROUGH, "on");
        // Sleep for 5 seconds to make sure the usb restore
        getRunUtil().sleep(5 * 1000);
    }

    /**
     * Write the sorted power output in format of Time stamp tag value, value
     * Time stamp special tag value
     *
     * @param sortedTestResult list of sorted PowerInfo
     * @return output file
     * @throws IOException
     */
    public static File writeSortedPowerData(List<PowerInfo> sortedTestResult)
            throws IOException {
        File tmpDeviceResult = FileUtil.createTempFile(POWER_RESULT_FILE, ".txt");
        Writer output = new BufferedWriter(new FileWriter(tmpDeviceResult, true));
        SimpleDateFormat dateFormatter = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS ");

        int counter = 0;
        String newLine = "";
        // Parse the sorted data power data and write it to a output file.
        // Each power data tag contains 60 samples - 1 min.
        for (PowerInfo powerResult : sortedTestResult) {
            if (powerResult.mTag != null) {
                // Process the power samples tag
                if (POWER_DATA_TAG.equalsIgnoreCase(powerResult.mTag)) {
                    if (((counter % 60) == 0)) {
                        output.write(String.format("%s%s %s %s",
                                newLine, dateFormatter.format(powerResult.mTimeStamp),
                                powerResult.mTag, powerResult.mPowerData));
                    } else {
                        output.write(String.format(",%s", powerResult.mPowerData));
                    }
                }
                counter++;
                // Create a new entry when the test tag is hit.
                if (!POWER_DATA_TAG.equalsIgnoreCase(powerResult.mTag)) {
                    output.write(String.format("%s%s %s %s",
                            newLine, dateFormatter.format(powerResult.mTimeStamp),
                            powerResult.mTag, powerResult.mPowerData));
                    counter = 0;
                }
                newLine = "\n";
            }
        }
        output.write("\n");
        output.close();
        return tmpDeviceResult;
    }

    private static List<PowerInfo> parseDeviceOutput(File deviceResultFile) throws IOException {
        List<PowerInfo> powerTestResult = new ArrayList<PowerInfo>();
        Pattern powerDataPattern =
                Pattern.compile("^(\\d+) (\\S.*) (\\S.*)");
        String deviceLine;
        CLog.i("Start sorting the power data");
        BufferedReader bufferReader = null;

        try {
            bufferReader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(deviceResultFile)));
            // Parse the test result in the device
            while ((deviceLine = bufferReader.readLine()) != null) {
                try {
                    PowerInfo testResultInfo = new PowerInfo();
                    Matcher m = powerDataPattern.matcher(deviceLine.trim());
                    if (m.matches()) {
                        testResultInfo.mTimeStamp = Long.valueOf(m.group(1));
                        testResultInfo.mTag = m.group(2);
                        testResultInfo.mPowerData = m.group(3);
                    }
                    powerTestResult.add(testResultInfo);
                } catch (NumberFormatException e) {
                    CLog.e("Failed to parse the device test result : %s", e.toString());
                }
            }
        } finally {
            StreamUtil.close(bufferReader);
        }
        return powerTestResult;
    }

    /**
     * Sort the raw power data with the device test log based on the timestamp.
     *
     * @param monsoonData Raw power data with timestamp
     * @param deviceResult test result written in the device
     * @return hashtable which contains the test case name and the average
     *         current.
     */
    public static List<PowerInfo> getSortedPowerResult(List<PowerInfo> monsoonData,
            File deviceResultFile) throws IOException {
        // Parse the result file into a list
        List<PowerInfo> deviceTestResult = new ArrayList<PowerInfo>();
        List<PowerInfo> sortedTestResult = new ArrayList<PowerInfo>();

        deviceTestResult = parseDeviceOutput(deviceResultFile);
        // Merge and sort the power data with the test result by the
        // timestamp
        long tmpTimeStamp = 0;
        for (PowerInfo testInfo : deviceTestResult) {
            for (PowerInfo powerData : monsoonData) {
                if (testInfo.mTimeStamp <= powerData.mTimeStamp) {
                    sortedTestResult.add(testInfo);
                    tmpTimeStamp = testInfo.mTimeStamp;
                    break;
                } else if (powerData.mTimeStamp >= tmpTimeStamp) {
                    sortedTestResult.add(powerData);
                }
            }
        }
        return sortedTestResult;
    }

    /**
     * Get the average current usage and write the raw data output to a file.
     *
     * @param sortedPowerResult list of sorted PowerInfo
     * @return list of average power usage.
     */
    public static List<AverageCurrentResult> getAveragePowerUsage(
            List<PowerInfo> sortedPowerResult) {
        List<AverageCurrentResult> testResult = new ArrayList<AverageCurrentResult>();
        String testCaseName = null;
        boolean startCollectData = false;
        float totalCurrent = 0;
        int sampleCount = 0;
        float averageCurrent = 0;
        // Parse the list and calculate the average current usage.
        try {
            for (PowerInfo powerData : sortedPowerResult) {
                if (TEST_START_TAG.equalsIgnoreCase(powerData.mTag)) {
                    testCaseName = powerData.mPowerData;
                    startCollectData = true;
                    continue;
                }
                if (TEST_END_TAG.equalsIgnoreCase(powerData.mTag)) {
                    AverageCurrentResult averageCurrentResult = new AverageCurrentResult();
                    if (sampleCount != 0) {
                        // stop get the power data and get the average
                        averageCurrent = (totalCurrent / sampleCount);
                        averageCurrentResult.mAverageCurrent = averageCurrent;
                        averageCurrentResult.mTestCase = testCaseName;
                        CLog.d("Test Case: %s Average Current: %s", testCaseName,
                                averageCurrentResult.mAverageCurrent);
                        testResult.add(averageCurrentResult);
                    }
                    // reset the counter.
                    startCollectData = false;
                    totalCurrent = 0;
                    sampleCount = 0;
                    averageCurrent = 0;
                }
                if (startCollectData) {
                    if (!POWER_DATA_TAG.equalsIgnoreCase(powerData.mTag)) {
                        // something wrong with the output result file.
                        break;
                    }
                    totalCurrent += Float.valueOf(powerData.mPowerData);
                    sampleCount++;
                }
            }
        } catch (NumberFormatException e) {
            CLog.e("Fails to parse the average power usage : " + e.toString());
        }
        return testResult;
    }

    private static IRunUtil getRunUtil(){
        return RunUtil.getDefault();
    }
}
