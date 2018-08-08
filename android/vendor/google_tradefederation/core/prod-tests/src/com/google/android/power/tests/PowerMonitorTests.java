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
import com.android.tradefed.util.StreamUtil;
import com.google.android.power.tests.PowerMonitor.AverageCurrentResult;
import com.google.android.power.tests.PowerMonitor.PowerInfo;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * PowerMonitor unit test.
 */
public class PowerMonitorTests extends TestCase{

    private String mDeviceTestResults[] = {
            "1330561782514 AUTOTEST_TEST_BEGIN PartialWakeLock\n",
            "1330561794511 AUTOTEST_TEST_SUCCESS PartialWakeLock\n",
            "1330561794711 AUTOTEST_TEST_BEGIN PartialIdleWakeLock\n",
            "1330561795211 AUTOTEST_TEST_SUCCESS PartialIdleWakeLock\n"
    };
    // sample frequency 1hz.
    private String mRawPowerDatas[] = {
            "1330561781500 PowerSample 1.56",
            "1330561782500 PowerSample 2",
            "1330561783500 PowerSample 3",
            "1330561784500 PowerSample 4.31",
            "1330561785500 PowerSample 5.66",
            "1330561786500 PowerSample 6",
            "1330561787500 PowerSample 7",
            "1330561788500 PowerSample 8",
            "1330561788600 PowerSample 9",
            "1330561788700 PowerSample 10",
            "1330561788800 PowerSample 11",
            "1330561788900 PowerSample 12",
            "1330561789000 PowerSample 13",
            "1330561789100 PowerSample 14",
            "1330561789200 PowerSample 15",
            "1330561789300 PowerSample 16",
            "1330561789400 PowerSample 17",
            "1330561789500 PowerSample 18",
            "1330561789600 PowerSample 19",
            "1330561789700 PowerSample 20",
            "1330561789800 PowerSample 21",
            "1330561789900 PowerSample 22",
            "1330561790000 PowerSample 23",
            "1330561790100 PowerSample 24",
            "1330561790200 PowerSample 25",
            "1330561790300 PowerSample 26",
            "1330561790400 PowerSample 27",
            "1330561790500 PowerSample 28",
            "1330561790600 PowerSample 29",
            "1330561790700 PowerSample 30",
            "1330561790800 PowerSample 31",
            "1330561790900 PowerSample 32",
            "1330561791000 PowerSample 33",
            "1330561791100 PowerSample 34",
            "1330561791200 PowerSample 35",
            "1330561791300 PowerSample 36",
            "1330561791400 PowerSample 37",
            "1330561791500 PowerSample 38",
            "1330561791600 PowerSample 39",
            "1330561791700 PowerSample 40",
            "1330561791800 PowerSample 41",
            "1330561791900 PowerSample 42",
            "1330561792000 PowerSample 43",
            "1330561792100 PowerSample 44",
            "1330561792200 PowerSample 45",
            "1330561792300 PowerSample 46",
            "1330561792400 PowerSample 47",
            "1330561792500 PowerSample 48",
            "1330561792600 PowerSample 49",
            "1330561792700 PowerSample 50",
            "1330561792800 PowerSample 51",
            "1330561792900 PowerSample 52",
            "1330561793000 PowerSample 53",
            "1330561793100 PowerSample 54",
            "1330561793200 PowerSample 55",
            "1330561793300 PowerSample 56",
            "1330561793400 PowerSample 57",
            "1330561793500 PowerSample 58",
            "1330561793600 PowerSample 59",
            "1330561793700 PowerSample 60",
            "1330561793800 PowerSample 61",
            "1330561793900 PowerSample 62",
            "1330561794000 PowerSample 63",
            "1330561794100 PowerSample 64",
            "1330561794200 PowerSample 65",
            "1330561794300 PowerSample 66",
            "1330561794400 PowerSample 67",
            "1330561794500 PowerSample 68",
            "1330561794600 PowerSample 69",
            "1330561794700 PowerSample 70",
            "1330561794800 PowerSample 71",
            "1330561794900 PowerSample 72",
            "1330561795000 PowerSample 73",
            "1330561795100 PowerSample 74",
            "1330561795200 PowerSample 75",
            "1330561795300 PowerSample 76",
            "1330561795400 PowerSample 77",
            "1330561795500 PowerSample 78",
    };
    private String mExpectedOutputs[] = {
            "1330561781500 PowerSample 1.56",
            "1330561782500 PowerSample 2",
            "1330561782514 AUTOTEST_TEST_BEGIN PartialWakeLock",
            "1330561783500 PowerSample 3",
            "1330561784500 PowerSample 4.31",
            "1330561785500 PowerSample 5.66",
            "1330561786500 PowerSample 6",
            "1330561787500 PowerSample 7",
            "1330561788500 PowerSample 8",
            "1330561788600 PowerSample 9",
            "1330561788700 PowerSample 10",
            "1330561788800 PowerSample 11",
            "1330561788900 PowerSample 12",
            "1330561789000 PowerSample 13",
            "1330561789100 PowerSample 14",
            "1330561789200 PowerSample 15",
            "1330561789300 PowerSample 16",
            "1330561789400 PowerSample 17",
            "1330561789500 PowerSample 18",
            "1330561789600 PowerSample 19",
            "1330561789700 PowerSample 20",
            "1330561789800 PowerSample 21",
            "1330561789900 PowerSample 22",
            "1330561790000 PowerSample 23",
            "1330561790100 PowerSample 24",
            "1330561790200 PowerSample 25",
            "1330561790300 PowerSample 26",
            "1330561790400 PowerSample 27",
            "1330561790500 PowerSample 28",
            "1330561790600 PowerSample 29",
            "1330561790700 PowerSample 30",
            "1330561790800 PowerSample 31",
            "1330561790900 PowerSample 32",
            "1330561791000 PowerSample 33",
            "1330561791100 PowerSample 34",
            "1330561791200 PowerSample 35",
            "1330561791300 PowerSample 36",
            "1330561791400 PowerSample 37",
            "1330561791500 PowerSample 38",
            "1330561791600 PowerSample 39",
            "1330561791700 PowerSample 40",
            "1330561791800 PowerSample 41",
            "1330561791900 PowerSample 42",
            "1330561792000 PowerSample 43",
            "1330561792100 PowerSample 44",
            "1330561792200 PowerSample 45",
            "1330561792300 PowerSample 46",
            "1330561792400 PowerSample 47",
            "1330561792500 PowerSample 48",
            "1330561792600 PowerSample 49",
            "1330561792700 PowerSample 50",
            "1330561792800 PowerSample 51",
            "1330561792900 PowerSample 52",
            "1330561793000 PowerSample 53",
            "1330561793100 PowerSample 54",
            "1330561793200 PowerSample 55",
            "1330561793300 PowerSample 56",
            "1330561793400 PowerSample 57",
            "1330561793500 PowerSample 58",
            "1330561793600 PowerSample 59",
            "1330561793700 PowerSample 60",
            "1330561793800 PowerSample 61",
            "1330561793900 PowerSample 62",
            "1330561794000 PowerSample 63",
            "1330561794100 PowerSample 64",
            "1330561794200 PowerSample 65",
            "1330561794300 PowerSample 66",
            "1330561794400 PowerSample 67",
            "1330561794500 PowerSample 68",
            "1330561794511 AUTOTEST_TEST_SUCCESS PartialWakeLock",
            "1330561794600 PowerSample 69",
            "1330561794700 PowerSample 70",
            "1330561794711 AUTOTEST_TEST_BEGIN PartialIdleWakeLock",
            "1330561794800 PowerSample 71",
            "1330561794900 PowerSample 72",
            "1330561795000 PowerSample 73",
            "1330561795100 PowerSample 74",
            "1330561795200 PowerSample 75",
            "1330561795211 AUTOTEST_TEST_SUCCESS PartialIdleWakeLock"
    };

    private String mExpectedSortedOtuputFile[] = {
            "2012-02-29 16:29:41.500  PowerSample 1.56,2",
            "2012-02-29 16:29:42.514  AUTOTEST_TEST_BEGIN PartialWakeLock",
            "2012-02-29 16:29:43.500  PowerSample 3,4.31,5.66,6,7,8,9,10,11," +
                "12,13,14,15,16,17,18,19,20,21,22,23,24,25,26," +
                "27,28,29,30,31,32,33,34,35,36,37,38,39,40,41," +
                "42,43,44,45,46,47,48,49,50,51,52,53,54,55,56," +
                "57,58,59,60,61,62",
            "2012-02-29 16:29:54.000  PowerSample 63,64,65,66,67,68",
            "2012-02-29 16:29:54.511  AUTOTEST_TEST_SUCCESS PartialWakeLock",
            "2012-02-29 16:29:54.600  PowerSample 69,70",
            "2012-02-29 16:29:54.711  AUTOTEST_TEST_BEGIN PartialIdleWakeLock",
            "2012-02-29 16:29:54.800  PowerSample 71,72,73,74,75",
            "2012-02-29 16:29:55.211  AUTOTEST_TEST_SUCCESS PartialIdleWakeLock"
    };

    private String mExpectedAverageOutput[] = {
            "PartialWakeLock 35.51",
            "PartialIdleWakeLock 73.00"
    };

    private List<PowerInfo> mPowerData;
    private List<PowerInfo> mExpectedSortedData;
    private File mDeviceTestResultFile;
    private List<AverageCurrentResult> mExpectedAvgResult;


    @Override
    public void setUp() throws Exception {
        mDeviceTestResultFile = prepareDeviceResultFile();
        mPowerData = preparePowerRawInput(mRawPowerDatas);
        mExpectedSortedData = preparePowerRawInput(mExpectedOutputs);
        mExpectedAvgResult = prepareAvgResult(mExpectedAverageOutput);
    }

    @Override
    public void tearDown() throws Exception {
        FileUtil.deleteFile(mDeviceTestResultFile);
    }

    private File prepareDeviceResultFile() throws IOException {
        File tmpDeviceResult = FileUtil.createTempFile("deviceTestOutput", ".txt");
        Writer output = new BufferedWriter(new FileWriter(tmpDeviceResult, true));
        for (String deviceResult : mDeviceTestResults) {
            output.write(deviceResult);
        }
        output.close();
        return tmpDeviceResult;
    }

    private List<PowerInfo> preparePowerRawInput(String[] results) {
        List<PowerInfo> powerData = new ArrayList<PowerInfo>();
        Pattern powerDataPattern =
                Pattern.compile("^(\\d+) (\\S.*) (\\S.*)");
        for (String result : results) {
            Matcher m = powerDataPattern.matcher(result);
            if (m.matches()) {
                PowerMonitor.PowerInfo powerInfo = new PowerMonitor.PowerInfo();
                powerInfo.mTimeStamp = Long.valueOf(m.group(1));
                powerInfo.mTag = m.group(2);
                powerInfo.mPowerData = m.group(3);
                powerData.add(powerInfo);
            }
        }
        return powerData;
    }

    private List<AverageCurrentResult> prepareAvgResult(String[] results) {
        List<AverageCurrentResult> avgResultOutput = new ArrayList<AverageCurrentResult>();
        Pattern powerResultPattern = Pattern.compile("^(\\S.*) (\\d+.\\d+)");
        for (String result : results) {
            Matcher m = powerResultPattern.matcher(result);
            if (m.matches()) {
                AverageCurrentResult avgResult = new AverageCurrentResult();
                avgResult.mTestCase = m.group(1);
                avgResult.mAverageCurrent = Float.parseFloat(m.group(2));
                avgResultOutput.add(avgResult);
            }
        }
        return avgResultOutput;
    }

    public void testgetSortedPowerResult() throws Exception {
        List<PowerInfo> powerSortedResult = PowerMonitor.getSortedPowerResult(mPowerData,
                mDeviceTestResultFile);
        assertEquals("getSortedPowerResult size", mExpectedSortedData.size(),
                powerSortedResult.size());
        for (int i = 0; i < mExpectedSortedData.size(); i++) {
            PowerInfo expectedData = new PowerInfo();
            PowerInfo sortedResult = new PowerInfo();
            // Compare each component.
            expectedData = mExpectedSortedData.get(i);
            sortedResult = powerSortedResult.get(i);
            assertEquals("Sorted timestamp: ", expectedData.mTimeStamp,
                    sortedResult.mTimeStamp);
            assertEquals("Sorted Tap: ", expectedData.mTag, sortedResult.mTag);
            assertEquals("Sorted Data: ", expectedData.mPowerData, sortedResult.mPowerData);
        }
    }

    public void testGetAverageResult() throws Exception {
        List<PowerInfo> powerSortedResult = PowerMonitor.getSortedPowerResult(mPowerData,
                mDeviceTestResultFile);
        List<AverageCurrentResult> avgResult = PowerMonitor.getAveragePowerUsage(powerSortedResult);
        assertEquals("getAverageResult size", mExpectedAvgResult.size(), avgResult.size());
        for (int i = 0; i < mExpectedAvgResult.size(); i++) {
            AverageCurrentResult expected = new AverageCurrentResult();
            AverageCurrentResult avgOutput = new AverageCurrentResult();
            // Compare each component.
            expected = mExpectedAvgResult.get(i);
            avgOutput = avgResult.get(i);
            assertEquals("Average test case name: ", expected.mTestCase, avgOutput.mTestCase);
            CLog.d("average usage",avgOutput.mAverageCurrent);
            assertEquals("Average power usage: ", expected.mAverageCurrent,
                    avgOutput.mAverageCurrent);
        }
    }

    public void testWriteSortedResult() throws Exception {
        List<PowerInfo> powerSortedResult = PowerMonitor.getSortedPowerResult(mPowerData,
                mDeviceTestResultFile);
        File outputPath = PowerMonitor.writeSortedPowerData(powerSortedResult);
        BufferedReader bufferReader = null;
        try {
            bufferReader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(outputPath)));
            String outputLine;
            int counter = 0;
            // Validate the output file.
            while ((outputLine = bufferReader.readLine()) != null) {
                assertEquals("Match the output line by line",
                        mExpectedSortedOtuputFile[counter], outputLine);
                counter++;
            }
        } finally {
            // remove the sorted output file
            FileUtil.deleteFile(outputPath);
            StreamUtil.close(bufferReader);
        }
    }
}
