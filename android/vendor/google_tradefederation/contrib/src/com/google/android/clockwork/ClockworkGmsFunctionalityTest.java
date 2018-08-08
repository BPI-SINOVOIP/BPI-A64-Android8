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

package com.google.android.clockwork;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** The Functionality test listed here only report pass/fail on blackbox */
@OptionClass(alias = "functionality-test-runner")
public class ClockworkGmsFunctionalityTest implements IDeviceTest, IRemoteTest {
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

    @Option(
        name = "test-suite-name",
        description = "The test suite name reported to the dashboard.",
        mandatory = true
    )
    private String mTestSuiteName = null;

    @Option(name = "runner", description = "The instrumentation test runner class name to use.")
    private String mRunnerName =
            "com.google.android.apps.common.testing.testrunner.Google3InstrumentationTestRunner";

    @Option(
        name = "metrics-key-name",
        description = "The metrics key reported to the dashboard.",
        mandatory = true
    )
    private String mMetricsKeyName = null;

    // Constants for running the tests
    private static final String OUTPUT_PATH = "googletest/testinfo.txt";
    private static final String OUTPUT_SUMMARY = "test_log.txt";

    //Max test timeout - 30 mins
    private static final int MAX_TEST_TIMEOUT_MS = 30 * 60 * 1000;
    private static final long EVENTS_LOGCAT_SIZE = 80 * 1024 * 1024;

    private int mSuccessIter = 0;
    private LogReceiver mLogReceiver;
    Map<String, String> mMetricsReport = new HashMap<>();
    List<String> mMetricsNames = new ArrayList<>();

    /**
     * The test do the following: 1)Start the uiautomation test; 2)Post the pass/fail results to
     * dashboard.
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
        runner.setRunName(mTestRunKey);
        mTestDevice.setDate(new Date());
        runner.setClassName(
                String.format("%s.%s#%s", mTestPackageName, mTestClassName, mTestCaseName));
        runner.setMaxTimeToOutputResponse(MAX_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        mLogReceiver =
                new LogReceiver(
                        mTestDevice,
                        "logcat -v threadtime -b events",
                        "events",
                        EVENTS_LOGCAT_SIZE,
                        5);
        mLogReceiver.start();

        for (int iter = 0; iter < mTestIterNum; iter++) {
            mTestDevice.runInstrumentationTests(runner, listener);
            testIterPassed = logRawDataFile(listener, iter);
            cleanResultFile(deviceLogFile);
            if (!testIterPassed) break;
        }
        logOutputFile(listener, mTestIterNum);

        mLogReceiver.stop();
        mLogReceiver.postLog(listener);
    }

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /** Clean up the result files. */
    private void cleanResultFile(String deviceLogFile) throws DeviceNotAvailableException {
        // Remove the log from the device
        mTestDevice.executeShellCommand(String.format("rm %s", deviceLogFile));
        CLog.d("deleting file: %s", deviceLogFile);
        // Remove the host log
        File outputFile = null;
        outputFile = mTestDevice.pullFileFromExternal(OUTPUT_PATH);
        FileUtil.deleteFile(outputFile);
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     *
     * <p>Exposed for unit testing
     */
    private void reportMetrics(ITestInvocationListener listener, Map<String, String> metrics) {
        CLog.d("About to report metrics: %s", metrics);
        listener.testRunStarted(mTestSuiteName, 0);
        listener.testRunEnded(0, metrics);
    }

    /**
     * Pull the output file from the device, add it to the logs, and also parse out the relevant
     * test metrics and report them.
     */
    private void logOutputFile(ITestInvocationListener listener, int totalIter) {
        InputStreamSource outputSource = null;
        String metricsTable = "";

        mMetricsReport.put(mMetricsKeyName, Integer.toString(mSuccessIter));
        reportMetrics(listener, mMetricsReport);

        CLog.d("Sending metrics summary file into the log!");
        try {
            outputSource =
                    new ByteArrayInputStreamSource(metricsTable.getBytes(StandardCharsets.UTF_8));
            listener.testLog(OUTPUT_SUMMARY, LogDataType.TEXT, outputSource);
        } finally {
            StreamUtil.cancel(outputSource);
        }
    }

    /** Pull the output file from the device when a single iteration is done, add it to the logs */
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
            mSuccessIter++;
            testIterPassed = true;
        } finally {
            FileUtil.deleteFile(outputFile);
            StreamUtil.cancel(outputSource);
        }
        return testIterPassed;
    }
}
