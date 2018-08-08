/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.memory;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Memory test runner is the main test runner which does the following:
 * 1) Start the UI automation test
 * 2) Get the memory stats from the dumped memory info.
 * 3) Upload the memory usage stats to dashboard.
 * <p/>
 * Note that this test will not run properly unless /sdcard is mounted and
 * writable.
 */
@OptionClass(alias = "memory-test-runner")
public class MemoryTestRunner implements IDeviceTest, IRemoteTest {
    ITestDevice mTestDevice = null;

    @Option(
        name = "test-class-name",
        description = "Instrumentation memory test class name",
        mandatory = true
    )
    private String mTestClassName = null;

    @Option(
        name = "test-package-name",
        description = "Instrumentation memory test package name",
        mandatory = true
    )
    private String mTestPackageName = null;

    @Option(name = "test-iter-num", description = "Test iteration number")
    private int mTestIterNum = 1;

    @Option(name = "test-case-name", description = "Specific test case name", mandatory = true)
    private String mTestCaseName = null;

    @Option(name = "test-run-key", description = "Test key reported to rdb", mandatory = true)
    private String mTestRunKey = null;

    @Option(name = "runner",
            description="The instrumentation test runner class name to use.")
    private String mRunnerName = "android.test.InstrumentationTestRunner";

    @Option(name = "metrics-key-name", description = "The metrics key reported to the dashboard.")
    private String mMetricsKeyName = "systemhealth.memorytest";

    // Constants for running the tests
    private static final String OUTPUT_PATH = "googletest/systemhealth.txt";
    private static final String OUTPUT_SUMMARY = "systemhealth_metrics_summary.txt";

    // Max test timeout - 30 mins
    private static final int MAX_TEST_TIMEOUT_MS = 30 * 60 * 1000;
    private static final long EVENTS_LOGCAT_SIZE = 80 * 1024 * 1024;
    private int mSuccessIter = 0;
    private LogReceiver mLogReceiver;
    Map<String, Integer> mMetricsMean = new HashMap<>();
    Map<String, Integer> mMetricsMax = new HashMap<>();
    Map<String, Double> mMetricsDeviation = new HashMap<>();
    Map<String, String> mMetricsReport = new HashMap<>();
    List<Map<String, String>> mMetricsHistory = new ArrayList<>();
    List<String> mMetricsNames = new ArrayList<>();

    /**
     * The test do the following: 1)Start the uiautomation test. 2)Read the memory log the
     * uiautomation test recorded. 3)Post the memory stats.
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        String deviceLogFile =
                String.format(
                        "%s/%s",
                        mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE), OUTPUT_PATH);
        boolean testIterPassed;
        IRemoteAndroidTestRunner runner =
                new RemoteAndroidTestRunner(
                        mTestPackageName, mRunnerName, mTestDevice.getIDevice());
        mTestDevice.setDate(new Date());
        runner.setClassName(
                String.format("%s.%s#%s", mTestPackageName, mTestClassName, mTestCaseName));
        runner.setRunName(mTestRunKey);
        runner.setMaxTimeToOutputResponse(MAX_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        mLogReceiver = new LogReceiver(mTestDevice,
                "logcat -v threadtime -b events", "events", EVENTS_LOGCAT_SIZE, 5);
        mLogReceiver.start();

        for (int iter = 0; iter < mTestIterNum; iter++) {
            mTestDevice.runInstrumentationTests(runner, listener);
            testIterPassed = logRawDataFile(listener, iter);
            cleanResultFile(deviceLogFile);
            if (!testIterPassed)
                break;
        }
        logOutputFile(listener, mTestIterNum);

        mLogReceiver.stop();
        mLogReceiver.postLog(listener);
    }

    /**
     * Clean up the result files.
     */
    private void cleanResultFile(String deviceLogFile) throws DeviceNotAvailableException {
        // Remove the log from the device
        mTestDevice.executeShellCommand(String.format("rm %s", deviceLogFile));
        CLog.d("deleting file: %s", deviceLogFile);
        // Remove the host log
        File outputFile = null;
        outputFile = mTestDevice.pullFileFromExternal(OUTPUT_PATH);
        FileUtil.deleteFile(outputFile);
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
     * Pull the output file from the device when a single iteration is done, add it to the logs
     */
    private boolean logRawDataFile(ITestInvocationListener listener, int iter)
            throws DeviceNotAvailableException {
        boolean testIterPassed = false;
        File outputFile = null;
        InputStreamSource outputSource = null;
        try {
            outputFile = mTestDevice.pullFileFromExternal(OUTPUT_PATH);

            if (outputFile == null) {
                return false;
            }

            CLog.d("Sending %d byte file %s into the log!", outputFile.length(), outputFile);
            outputSource = new FileInputStreamSource(outputFile);
            listener.testLog(OUTPUT_PATH + "_" + iter, LogDataType.TEXT, outputSource);
            parseOutputFile(outputFile, listener);
            mSuccessIter++;
            testIterPassed = true;
        } finally {
            FileUtil.deleteFile(outputFile);
            StreamUtil.cancel(outputSource);
        }
        return testIterPassed;
    }

    /**
     * Pull the output file from the device, add it to the logs, and also parse out the relevant
     * test metrics and report them.
     */
    private void logOutputFile(ITestInvocationListener listener, int totalIter) {
        InputStreamSource outputSource = null;
        String metricsTable = "";
        calculateMetrics(mMetricsHistory, mSuccessIter);

        mMetricsReport.put("Iteration number", Integer.toString(mSuccessIter));
        reportMetrics(listener, mMetricsReport);

        metricsTable = String.format(
                "%40s %40s %40s %40s \n", "Label,", "Mean,", "Max,", "Standard_Deviation");
        for (String item : mMetricsNames) {
            String metricLine =
                    String.format(
                            "%40s %40s %40s %40s \n",
                            item + ",",
                            mMetricsMean.get(item) + ",",
                            mMetricsMax.get(item) + ",",
                            mMetricsDeviation.get(item));
            metricsTable = metricsTable + metricLine;
        }

        CLog.d("Sending metrics summary file into the log!");
        outputSource =
                new ByteArrayInputStreamSource(metricsTable.getBytes(StandardCharsets.UTF_8));
        listener.testLog(OUTPUT_SUMMARY, LogDataType.TEXT, outputSource);
    }

    /**
     * Parse the relevant metrics from the Instrumentation test output file
     */
    private void parseOutputFile(File outputFile, ITestInvocationListener listener) {
        Map<String, String> runMetrics = new HashMap<>();
        // try to parse it
        String contents;
        try {
            InputStream dataStream = new FileInputStream(outputFile);
            contents = StreamUtil.getStringFromStream(dataStream);
        } catch (IOException e) {
            CLog.e(e);
            return;
        }

        List<String> lines = Arrays.asList(contents.split("\n"));
        ListIterator<String> lineIter = lines.listIterator();
        mMetricsNames = new ArrayList<>();
        while (lineIter.hasNext()) {
            String key = lineIter.next();
            String value = lineIter.next();
            runMetrics.put(key, value);
            mMetricsNames.add(key);
        }
        mMetricsHistory.add(runMetrics);
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     * <p />
     * Exposed for unit testing
     */
    private void reportMetrics(ITestInvocationListener listener, Map<String, String> metrics) {
        CLog.d("About to report metrics: %s", metrics);
        listener.testRunStarted(mMetricsKeyName, 0);
        listener.testRunEnded(0, metrics);
    }

    /**
     * Calculate Mean, Max and Standard Deviation for the recorded metrics
     *
     * @param metricsRecord
     * @param completedIter
     */
    private void calculateMetrics(List<Map<String, String>> metricsRecord, int completedIter) {
        for (Map<String, String> metricsMap : metricsRecord) {
            for (String item : mMetricsNames) {
                int memData = Integer.parseInt(metricsMap.get(item));
                if (!mMetricsMean.containsKey(item)) {
                    mMetricsMean.put(item, memData);
                    mMetricsMax.put(item, memData);
                } else {
                    int historyDataMean = mMetricsMean.get(item);
                    int historyDataMax = mMetricsMax.get(item);
                    mMetricsMax.put(item, (historyDataMax > memData) ? historyDataMax : memData);
                    mMetricsMean.put(item, historyDataMean + memData);
                }
            }
        }
        for (String item : mMetricsNames) {
            mMetricsMean.put(item, mMetricsMean.get(item) / completedIter);
            mMetricsReport.put(item, Integer.toString(mMetricsMean.get(item)));
        }
        for (Map<String, String> metricsMap : metricsRecord) {
            for (String item : mMetricsNames) {
                int memData = Integer.parseInt(metricsMap.get(item));
                int totalAverage = mMetricsMean.get(item);
                if (!mMetricsDeviation.containsKey(item)) {
                    mMetricsDeviation.put(
                            item, (double) (memData - totalAverage) * (memData - totalAverage));
                } else {
                    double historyDeviation = mMetricsDeviation.get(item);
                    mMetricsDeviation.put(
                            item,
                            historyDeviation + (memData - totalAverage) * (memData - totalAverage));
                }
            }
        }
        for (String item : mMetricsNames) {
            mMetricsDeviation.put(item, Math.sqrt(mMetricsDeviation.get(item) / completedIter));
        }
    }

}
