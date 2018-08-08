/*
 * Copyright 2016 Google Inc. All Rights Reserved
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

package com.google.android.media.tests;

import com.google.android.utils.usb.switches.TigertailUsbSwitch;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A test that launches Audio Walt Test and reports corresponding result.
 */
public class AudioWaltTest implements IDeviceTest, IRemoteTest {
    private static final long RUN_SCRIPT_TIME_OUT = 60 * 1000; // 1 min

    private static final long CMD_RETRY_INTERVAL = 2 * 1000; // 2 seconds

    private IRunUtil mRunUtil = null;
    private ITestDevice mDevice;
    private TigertailUsbSwitch mUsbSwitch;

    @Option(name = "run-key", description = "Run key for the test")
    private String mRunKey = "AudioWALT";

    @Option(name = "tigertail-path", description = "The full path to the tigertool.py script path")
    private String mTigertoolPath = "";

    @Option(name = "tigertail-serial", description = "The serial number for tigertail")
    private String mSerial = "";

    @Option(name = "permission-script-path",
            description = "The full path to the USB_DEVICE_ATTACH permission grant script")
    private String mPermissionScriptPath = "";

    @Option(name = "output-path",
            description = "Where the data output file should be stored on the device")
    private String mOutputDataPath = "/sdcard/Result.csv";

    @Option(name = "fields",
            description = "The name for each field shown in the WALT test result csv seperate by ,")
    private String mFields = "";

    @Option(name = "retry-time",
            description = "How many times we want to retry the failed script commands."
    )
    private int mRetryLimit = 3;

    @Option(name = "repeat-times", description = "How many times we want to repeat the measure.")
    private int mRepeatTimes = 100;

    @Option(name = "test-type", description = "The WALT Test Type we want to do.")
    private String mTestType = "";

    @Option(name = "wait-time",
            isTimeVal = true,
            description = "The time (in ms) we expect to wait before data is ready."
    )
    private long mWaitTime = 20 * 1000;

    /**
     * This generates the map which contains the statistics for the fields we are interested in.
     */
    private static ArrayList<String> createMetricsKeyList(String fields) {
        CLog.i("Incoming fields: %s", fields);
        String[] tokens = fields.split(",");
        ArrayList<String> result = new ArrayList<String>();
        for (String token : tokens) {
            result.add(token);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }


    /**
     * This function is used to call script specified in the path given in option.
     *
     * @param cmd
     * @param listener
     * @param testId
     */
    public boolean callScript(
            String[] cmd, ITestInvocationListener listener, TestIdentifier testId) {
        CommandResult commandResult =
                mRunUtil.runTimedCmdRetry(RUN_SCRIPT_TIME_OUT, CMD_RETRY_INTERVAL,
                        mRetryLimit, cmd);
        if (commandResult != null) {
            if (commandResult.getStatus() == CommandStatus.SUCCESS) {
                CLog.i("Stderr: %s", commandResult.getStderr());
                CLog.i("Stdout: %s", commandResult.getStdout());
                return true;
            }
            CLog.e("Calling script failed");
            CLog.e("External Script path: %s", cmd[0]);
            CLog.e("Stderr: %s", commandResult.getStderr());
            CLog.e("Stdout: %s", commandResult.getStdout());
        }
        CLog.w("Script has return value: %d", commandResult.getStatus());

        CLog.e("Cannot call script, abort test.");
        reportFailure(
                listener, testId, "Test Aborted, make sure script exists and path correctly set.");
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mRunUtil == null) {
            mRunUtil = RunUtil.getDefault();
        }
        ArrayList<String> metricKeyList = createMetricsKeyList(mFields);
        CLog.i("The test type is: %s", mTestType);

        TestIdentifier testId = new TestIdentifier(getClass().getCanonicalName(), mRunKey);
        ITestDevice device = getDevice();

        listener.testRunStarted(mRunKey, 1);
        listener.testStarted(testId);

        long testStartTime = System.currentTimeMillis();
        Map<String, String> metrics = new HashMap<String, String>();
        // Check for scripts existence and permission.
        File tigerTailScript = new File(mTigertoolPath);
        if (!tigerTailScript.exists()
                || tigerTailScript.isDirectory()
                || !tigerTailScript.canExecute()) {
            reportFailure(
                    listener,
                    testId,
                    "Test Aborted, make sure permission script exists and path is correctly set.");
            return;
        }

        File permissionScript = new File(mPermissionScriptPath);
        if (!permissionScript.exists()
                || permissionScript.isDirectory()
                || !permissionScript.canExecute()) {
            reportFailure(
                    listener,
                    testId,
                    "Test Aborted, make sure permission script exists and path is correctly set.");
            return;
        }

        mUsbSwitch = new TigertailUsbSwitch(mTigertoolPath, mSerial);
        // Wait for the Device
        CLog.i("Switch tigertail to mux B");
        mUsbSwitch.disconnectUsb();

        CLog.i("Start testing");
        CLog.i("Start granting USB_DEVICE_ATTACH Permission.");
        device.waitForDeviceAvailable();
        if (!callScript(new String[]{mPermissionScriptPath}, listener, testId)) {
            return;
        }

        CLog.i("Switch tigertail to mux A");
        mUsbSwitch.connectUsb();

        // Wait for permission grant to finish.
        mRunUtil.sleep(mWaitTime);

        CLog.i("Switch tigertail to mux B");
        mUsbSwitch.disconnectUsb();

        String cmd =
                "am start -a org.chromium.latency.walt.START_TEST -n "
                        + "org.chromium.latency.walt/.MainActivity"
                        + " --ei Reps "
                        + String.valueOf(mRepeatTimes);
        cmd += " --es TestType " + mTestType;
        cmd += " --es FileName " + mOutputDataPath;
        CLog.v("The command we execute is: %s", cmd);
        device.executeShellCommand(cmd);

        //Switch the device to connect to WALT
        CLog.i("Switch tigertail to mux A");
        mUsbSwitch.connectUsb();

        CLog.i("Start waiting for result");
        mRunUtil.sleep(mWaitTime);

        //Switch the device back to mux B to pull the results back.
        CLog.i("Switch tigertail to mux B");
        mUsbSwitch.disconnectUsb();

        CLog.i("Start Fetching result from the device.");
        File audioTestReport = device.pullFile(mOutputDataPath);
        if (audioTestReport.length() > 0) {
            CLog.i("== AudioWaltTest Result==");
            try {
                Map<String, String> audioTestResult = parseResult(audioTestReport, metricKeyList);
                if (audioTestResult == null || audioTestResult.size() == 0) {
                    reportFailure(listener, testId, "Failed to parse AudioWaltTest result.");
                    return;
                }
                metrics = audioTestResult;
                listener.testLog(
                        "audio-walt-test-result", LogDataType.TEXT,
                        new FileInputStreamSource(audioTestReport));
            } catch (IOException ioe) {
                CLog.e(ioe.getMessage());
                reportFailure(listener, testId, "I/O error while parsing Audio-Walt-Test result.");
                return;
            } finally {
                FileUtil.deleteFile(audioTestReport);
            }
        } else {
            CLog.w("Emptry audio-walt-test-result file");
            reportFailure(listener, testId, "AudioWaltTest result not found, timed out.");
            return;
        }
        CLog.i("Test Finished");
        long durationMs = System.currentTimeMillis() - testStartTime;
        listener.testEnded(testId, metrics);
        listener.testRunEnded(durationMs, metrics);
    }

    /**
     * Report failure with error message specified and fail the test.
     *
     * @param listener
     * @param testId
     * @param errMsg
     */
    private void reportFailure(
            ITestInvocationListener listener, TestIdentifier testId, String errMsg) {
        CLog.e(errMsg);
        listener.testFailed(testId, errMsg);
        listener.testEnded(testId, new HashMap<String, String>());
        listener.testRunFailed(errMsg);
    }

    /**
     * Process each list of value and calculate the corresponding fields: sum,avg,
     * mean,median,min and max.
     *
     * @param dataMap
     * @param metricKeyList
     */
    private Map<String, String> processData(ArrayList<ArrayList<Double>> dataMap,
            ArrayList<String> metricKeyList) {
        Map<String, String> resultMap = new HashMap<String, String>();
        // We can add more analysis to the data if required by Eng.
        for (int i = 0; i < dataMap.size(); ++i) {
            Collections.sort(dataMap.get(i));
            double sum = 0.0;
            double avg = 0;
            for (double val : dataMap.get(i)) {
                sum += val;
            }
            avg = sum / dataMap.get(i).size();
            String key = "mean_" + metricKeyList.get(i);
            resultMap.put(key, String.valueOf(avg));

            int pos = dataMap.get(i).size() / 2;
            double median = dataMap.get(i).get(pos);
            key = "median_" + metricKeyList.get(i);
            resultMap.put(key, String.valueOf(median));

            double min = dataMap.get(i).get(0);
            key = "min_" + metricKeyList.get(i);
            resultMap.put(key, String.valueOf(min));

            double max = dataMap.get(i).get(dataMap.get(i).size() - 1);
            key = "max_" + metricKeyList.get(i);
            resultMap.put(key, String.valueOf(max));

            key = "sum_" + metricKeyList.get(i);
            resultMap.put(key, String.valueOf(sum));
        }
        for (String key : resultMap.keySet()) {
            CLog.i("Metric %s : %s", key, resultMap.get(key));
        }
        return resultMap;
    }

    /**
     * Parse result.The csv file is comma separated file and the format can be found at:
     * https://github.com/google/walt. Format: key = value
     *
     * @param result AudioWaltTest result file
     * @return a {@link HashMap} that contains metrics keys and results
     * @throws IOException
     */
    private Map<String, String> parseResult(File result, ArrayList<String> metricKeyList)
            throws IOException {
        Map<String, String> resultMap = new HashMap<String, String>();
        BufferedReader br = new BufferedReader(new FileReader(result));
        ArrayList<ArrayList<Double>> dataMap = new ArrayList<ArrayList<Double>>();
        // Number of fields we are interested in.
        for (int i = 0; i < metricKeyList.size(); ++i) {
            dataMap.add(new ArrayList<Double>());
        }
        try {
            String line = br.readLine();

            while (line != null) {
                String[] tokens = line.split(",");
                if (tokens.length == dataMap.size()) {
                    for (int i = 0; i < tokens.length; ++i) {
                        // Convert to double, then store in the dataMap.
                        double val = Double.parseDouble(tokens[i]);
                        if (val < 0) {
                            CLog.e("Negative value from result: %s of line: %s",
                                    String.valueOf(val),
                                    line
                            );
                        }
                        dataMap.get(i).add(val);
                    }

                } else {
                    CLog.e(
                            "Line has length: %s while we are looking for %d fields"
                            , String.valueOf(tokens.length)
                            , dataMap.size()
                    );
                }
                line = br.readLine();
            }
            resultMap = processData(dataMap, metricKeyList);

        } finally {
            br.close();
        }

        return resultMap;
    }
}
