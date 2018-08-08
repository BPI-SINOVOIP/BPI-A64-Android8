// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.performance;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.FileUtil;

import com.google.android.power.tests.PowerMonitor.AverageCurrentResult;
import com.google.android.power.tests.PowerTestRunner;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PowerJankTest extends PowerTestRunner {

    private static final String EXTERNAL_STORAGE = "${EXTERNAL_STORAGE}";
    private static final String OUTPUT_FILENAME = "/results.log";
    private static final String AVG90THPERCENTILE = "gfx-avg-frame-time-90";
    private static final String AVG95THPERCENTILE = "gfx-avg-frame-time-95";
    private static final String AVG99THPERCENTILE = "gfx-avg-frame-time-99";
    private static final String AVGJANK = "gfx-avg-jank";
    private static final String AVGJANKPERCENTAGE = "gfx-avg-jank-percentage";
    private static final String INVAVG90THPERCENTILE = "inv-gfx-avg-frame-time-90-sec";
    private static final String AVGPOWERINWATTS = "avg-power-watts";
    private static final String PERFPOWERRATIO = "inv90percentsec-avgpowerwatt-ratio";
    private static final String TESTTAG = "PowerJank";
    private static final double MILLIWATT_TO_WATT = 1000d;

    private Map<String, Map<String, Double>> mPerfMetricsResultMap = new HashMap<>();
    private List<AverageCurrentResult> mAverageCurrentList;
    private Map<String, Map<String, String>> mFinalResultMap = new HashMap<>();
    private Map<String, String> mTestCaseErrMsg = new HashMap<>();

    @Option(name = "run-arg",
            description = "Additional test specific arguments to provide.")
    private Map<String, String> mArgMap = new LinkedHashMap<String, String>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {

        // Remove if there is already existing results.out in /sdcard
        getDevice().executeShellCommand(String.format("rm -rf %s%s", EXTERNAL_STORAGE,
                OUTPUT_FILENAME));

        long testStartTime = System.currentTimeMillis();
        super.run(listener);
        long durationMs = System.currentTimeMillis() - testStartTime;

        // Pull the jank result file stored at /sdcard/results.log to /tmp
        File outputFileHandler = pullPerfOutputFile(OUTPUT_FILENAME);
        Assert.assertNotNull("Not able find the output file", outputFileHandler);

        // Upload the results.log file in sponge
        try (FileInputStreamSource stream = new FileInputStreamSource(outputFileHandler)) {
            listener.testLog(outputFileHandler.getName(), LogDataType.TEXT, stream);
        }

        // Get the power data
        mAverageCurrentList = getPowerTestResult();
        Assert.assertTrue("No average current data captured",
                (mAverageCurrentList != null && !mAverageCurrentList.isEmpty()));

        try {
            // Parse the jank data
            parsePerfOutputFile(outputFileHandler);

            // Analyze the Jank and Power data
            analyzePowerJankData();

        } catch (IOException ioe) {
            CLog.e(ioe);
            listener.testRunFailed("Not able to parse the performance output file");
        } finally {
            // Delete the perf data file from /tmp folder
            if (outputFileHandler != null) {
                FileUtil.deleteFile(outputFileHandler);
            }
        }

        // Report the Jank and Power metrics
        reportMetrics(listener);
    }

    /**
     * To pull the log file from the device to local host
     * @param outputFileName
     * @return
     * @throws DeviceNotAvailableException
     */
    public File pullPerfOutputFile(String outputFileName)
            throws DeviceNotAvailableException {
        File outputFileHandler = getDevice().pullFile(String.format("%s%s",
                EXTERNAL_STORAGE, outputFileName));
        Assert.assertTrue("Unable to retrieve the results.out file", outputFileHandler != null);
        return outputFileHandler;
    }

    /**
     * Parse the output file info in to result map
     * @param outputFileHandler
     * @return
     * @throws IOException
     */
    public void parsePerfOutputFile(File outputFileHandler) throws IOException {
        /*
         * result.out file format
         * Result testuniquekey1
         * gfx-max-frame-time-90 17
         * gfx-max-frame-time-95 21
         * Result testuniquekey2
         * gfx-max-frame-time-90 20
         * gfx-max-frame-time-95 36
         */
        BufferedReader br = new BufferedReader(new FileReader(outputFileHandler));
        String line;
        Map<String, Double> perfMetrics = null;
        while ((line = br.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] splitString = line.split(" ");
            if (splitString[0].equalsIgnoreCase("Result")) {
                perfMetrics = new HashMap<>();
                mPerfMetricsResultMap.put(splitString[1], perfMetrics);
                continue;
            }
            perfMetrics.put(splitString[0], Double.valueOf(splitString[1]));
        }
    }

    /**
     * To analyze the power and jank metrics collected
     */
    public void analyzePowerJankData() {
        for (AverageCurrentResult avgResult : mAverageCurrentList) {
            Map<String, Double> testPerfResult = mPerfMetricsResultMap.get(avgResult.mTestCase);
            if (testPerfResult == null) {
                mTestCaseErrMsg.put(avgResult.mTestCase,
                        "Unable to find the jank metrics for the test case :"
                                + avgResult.mTestCase);
                continue;
            }
            double averagePowerInWatt = (getMonsoonVoltage() * avgResult.mAverageCurrent)
                    / MILLIWATT_TO_WATT;
            double inverse90Percentile_sec = (1 * 1000) / (testPerfResult.get(AVG90THPERCENTILE));
            double perfPowerRatio = inverse90Percentile_sec / averagePowerInWatt;
            Map<String, String> testFinalResult = new HashMap<>();
            testFinalResult.put(AVGJANKPERCENTAGE,
                    String.format("%.2f", testPerfResult.get(AVGJANK)));
            testFinalResult.put(AVG90THPERCENTILE,
                    String.format("%.2f", testPerfResult.get(AVG90THPERCENTILE)));
            testFinalResult.put(AVG95THPERCENTILE,
                    String.format("%.2f", testPerfResult.get(AVG95THPERCENTILE)));
            testFinalResult.put(AVG99THPERCENTILE,
                    String.format("%.2f", testPerfResult.get(AVG99THPERCENTILE)));
            testFinalResult.put(AVGPOWERINWATTS, String.format("%.2f", averagePowerInWatt));
            testFinalResult.put(INVAVG90THPERCENTILE,
                    String.format("%.2f", inverse90Percentile_sec));
            testFinalResult.put(PERFPOWERRATIO, String.format("%.2f", perfPowerRatio));
            mFinalResultMap.put(avgResult.mTestCase, testFinalResult);
        }
    }

    /**
     * Report run metrics by creating an empty test run to stick them in.
     * @param listener The {@link ITestInvocationListener} of test results
     * @param metrics The {@link Map} that contains metrics for the given test
     */
    private void reportMetrics(ITestInvocationListener listener) {
        for (AverageCurrentResult currentResult : mAverageCurrentList) {
            listener.testRunStarted(
                    String.format("%s-%s", currentResult.mTestCase, TESTTAG), 0);
            if (!mTestCaseErrMsg.containsKey(currentResult.mTestCase)) {
                Map<String, String> powerPerformanceMetrics = mFinalResultMap
                        .get(currentResult.mTestCase);
                if (powerPerformanceMetrics != null && !powerPerformanceMetrics.isEmpty()) {
                    listener.testRunEnded(0, powerPerformanceMetrics);
                }
            } else {
                listener.testRunFailed(mTestCaseErrMsg.get(currentResult.mTestCase));
                listener.testRunEnded(0, Collections.<String,String>emptyMap());
            }
        }
    }

    /**
     * @return the arguments map to pass to the UiAutomatorRunner.
     */
    public Map<String, String> getTestRunArgMap() {
        return mArgMap;
    }

    /**
     * @param runArgMap the arguments to pass to the UiAutomatorRunner.
     */
    public void setTestRunArgMap(Map<String, String> runArgMap) {
        mArgMap = runArgMap;
    }

    @Override
    protected String getAdditionalArgs() {
        StringBuilder command = new StringBuilder();
        for (String key : getTestRunArgMap().keySet()) {
            command.append(String.format(" -e %s %s", key, getTestRunArgMap().get(key)));
        }
        return command.toString();
    }
}

