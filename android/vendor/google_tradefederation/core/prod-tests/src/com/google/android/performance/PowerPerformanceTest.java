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

package com.google.android.performance;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.AbiFormatter;
import com.android.tradefed.util.FileUtil;

import com.google.android.power.tests.PowerTestRunner;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * To collect the power and performance metrics
 */
public class PowerPerformanceTest extends PowerTestRunner
        implements IDeviceTest, IRemoteTest {

    private static final String DEVICE_TEMPORARY_DIR_PATH = "/data/local/tmp/";
    private static final String OUTPUT_FILENAME = "perfdata.out";
    private static final String PERCENTILE_90 = "90th percentile";
    private static final String PERCENTILE_95 = "95th percentile";
    private static final String PERCENTILE_99 = "99th percentile";
    private static final String EXTRA_ARGS = "-e script_filepath %s";
    private static final String SCRIPT_EXTENSION = ".sh";
    private static final double MILLIWATT_TO_WATT = 1000d;
    private static final double MILLISECS_TO_SECS = 1000d;

    @Option(name = AbiFormatter.FORCE_ABI_STRING,
            description = AbiFormatter.FORCE_ABI_DESCRIPTION,
            importance = Importance.IF_UNSET)
    private String mForceAbi = null;

    @Option(name = "script_content", description = "script to start the binary "
            + "and redirect the output to perfdata.out")
    private String mScriptContent = "";
    private Map<String, List<String>> mOutputInfo = new HashMap<>();
    private double mAvgPercentile90;
    private double mAvgPercentile95;
    private double mAvgPercentile99;
    private double mAvgPercentile90InSecs;
    private double mAveragePowerInWatt;
    private double mPerfPowerRatio;
    private String mScriptFilePath;

    /**
     * The test do the following: 1)Start the uiautomation test in a new thread. 2)Disconnect the
     * usb. 3)Collect the power usage. 4)Resume the usb connection. 5)Collect the performance
     * result. 6)Report the metrics
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        listener.testRunStarted(getTestKey(), 0);

        // Remove if there is already existing perfdata.out in /data/local/tmp
        getDevice().executeShellCommand(String.format("rm -rf %s%s", DEVICE_TEMPORARY_DIR_PATH,
                OUTPUT_FILENAME));
        // Prepare the script file to start the binary and redirect the output to perfdata.out
        mScriptContent = AbiFormatter.formatCmdForAbi(mScriptContent, mForceAbi);
        File scriptFile = null;
        try {
            scriptFile = FileUtil.createTempFile(getTestKey(), SCRIPT_EXTENSION);
            FileUtil.writeToFile(mScriptContent, scriptFile);
            getDevice().pushFile(scriptFile,
                    String.format("%s%s.sh", DEVICE_TEMPORARY_DIR_PATH, getTestKey()));
        } catch (IOException ioe) {
            CLog.e("Unable to create the Script file");
        }
        getDevice().executeShellCommand(String.format("chmod 755 %s%s.sh",
                DEVICE_TEMPORARY_DIR_PATH,
                getTestKey()));
        mScriptFilePath = String.format("%s%s.sh", DEVICE_TEMPORARY_DIR_PATH,
                getTestKey());

        long testStartTime = System.currentTimeMillis();
        super.run(listener);
        long durationMs = System.currentTimeMillis() - testStartTime;

        /*
         * Analyze perf data Pull the result file stored at /data/local/tmp/perfdata.out to /tmp
         */
        File outputFileHandler = pullPerfOutputFile(OUTPUT_FILENAME);
        Assert.assertNotNull("Not able find the output file", outputFileHandler);

        // Upload the perfdata.out file in sponge
        try (FileInputStreamSource stream = new FileInputStreamSource(outputFileHandler)) {
            listener.testLog(outputFileHandler.getName(), LogDataType.TEXT, stream);
        }

        Assert.assertNotNull("Not able to find the power data", getPowerTestResult().get(0));
        mAveragePowerInWatt = (getMonsoonVoltage() * getPowerTestResult().get(0).mAverageCurrent)
                / MILLIWATT_TO_WATT;

        // Parse the perf output file
        try {
            boolean isSuccess = parsePerfOutputFile(outputFileHandler);
            Assert.assertTrue("Unable to parse the file successfully", isSuccess);
        } catch (IOException ioe) {
            CLog.e(ioe);
            listener.testRunFailed("Not able to parse the output file");
        }

        if (mOutputInfo.size() == 0) {
            listener.testRunFailed("Not able to get data from output file");
        }

        // Analyze the performance output data
        analyzePerfOutputData();
        mAvgPercentile90InSecs = mAvgPercentile90 / MILLISECS_TO_SECS;
        // As per the discussion in http://b/26980693
        mPerfPowerRatio = (1 / mAvgPercentile90InSecs) / mAveragePowerInWatt;

        // Report data to the dash board
        Map<String, String> metrics = reportPerfPowerMetrics();
        listener.testRunEnded(durationMs, metrics);

    }

    /**
     * To pull the log file from the device to local host
     * @param outputFileName
     * @return
     * @throws DeviceNotAvailableException
     */
    public File pullPerfOutputFile(String outputFileName)
            throws DeviceNotAvailableException {
        File outputFileHandler = getDevice().pullFile(
                DEVICE_TEMPORARY_DIR_PATH + "/" + outputFileName);
        Assert.assertTrue("Unable to retrieve the perfdata.out file", outputFileHandler != null);
        return outputFileHandler;
    }

    /**
     * Parse the output file info in to map
     * @param outputFileHandler
     * @return
     * @throws IOException
     */
    public boolean parsePerfOutputFile(File outputFileHandler) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(outputFileHandler));
        String line;
        boolean isSuccess = false;
        while ((line = br.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            if (line.contains("Success!")) {
                isSuccess = true;
                break;
            }
            String[] splitString = line.split(":");
            Assert.assertEquals("Output format is not in expected format", 2,
                    splitString.length);
            if (mOutputInfo.containsKey(splitString[0])) {
                mOutputInfo.get(splitString[0]).add(splitString[1]);
            } else {
                List<String> resultList = new ArrayList<String>();
                resultList.add(splitString[1]);
                mOutputInfo.put(splitString[0], resultList);
            }
        }
        return isSuccess;
    }

    /**
     * To calculate avg 90th,95th and 99th percentile
     */
    public void analyzePerfOutputData() {
        double ninetyPercentileSum = 0;
        double ninetyFivePercentileSum = 0;
        double ninetyNinePercentileSum = 0;
        List<String> ninetyPercentileInfo = mOutputInfo.get(PERCENTILE_90);
        List<String> ninetyFivePercentileInfo = mOutputInfo.get(PERCENTILE_95);
        List<String> ninetyNinePercentileInfo = mOutputInfo.get(PERCENTILE_99);
        for (int i = 0; i < ninetyPercentileInfo.size(); i++) {
            ninetyPercentileSum += getAvgPercentage(ninetyPercentileInfo.get(i));
            ninetyFivePercentileSum += getAvgPercentage(ninetyFivePercentileInfo.get(i));
            ninetyNinePercentileSum += getAvgPercentage(ninetyNinePercentileInfo.get(i));
        }
        mAvgPercentile90 = ninetyPercentileSum / (ninetyPercentileInfo.size());
        mAvgPercentile95 = ninetyFivePercentileSum / (ninetyPercentileInfo.size());
        mAvgPercentile99 = ninetyNinePercentileSum / (ninetyPercentileInfo.size());
    }

    /**
     * To get the exact time removing the ms and extra spaces
     * @param percentStr
     * @return
     */
    public long getAvgPercentage(String percentStr) {
        String formattedPercent = percentStr.replace("ms", "").trim();
        return Long.parseLong(formattedPercent.trim());
    }

    /**
     * To set the metrics need to reported
     * @return
     */
    public Map<String, String> reportPerfPowerMetrics() {
        Map<String, String> metrics = new HashMap<String, String>();
        metrics.put("gfx-avg-frame-time-90", String.format("%.2f", mAvgPercentile90));
        metrics.put("gfx-avg-frame-time-95", String.format("%.2f", mAvgPercentile95));
        metrics.put("gfx-avg-frame-time-99", String.format("%.2f", mAvgPercentile99));
        metrics.put("inv-gfx-avg-frame-time-90-sec",
                String.format("%.2f", (1 / mAvgPercentile90InSecs)));
        metrics.put("avgPowerInWatt", String.format("%.2f", mAveragePowerInWatt));
        metrics.put("PerfPowerRatio", String.format("%.2f", mPerfPowerRatio));
        return metrics;
    }

    @Override
    protected String getAdditionalArgs() {
        return String.format(EXTRA_ARGS, mScriptFilePath);
    }
}
