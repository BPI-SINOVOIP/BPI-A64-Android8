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

package com.google.android.acts;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * ACTS runner is the main test runner which do the followings:
 * 1) Start the ACTS test provided from config
 * 2) Launch the command and wait until it finished
 * 3) Analyse the output to decide if the test has finished
 * 4) Upload the results to dashboard.
 */
@OptionClass(alias = "acts-test-runner")
public class ActsTestRunner implements IDeviceTest, IRemoteTest {
    ITestDevice mTestDevice = null;

    @Option(name = "test-config",
            description = "Config file for ACTS test", mandatory = true)
    private String mTestConfigFile = null;

    @Option(name = "acts-path",
            description = "Location for act.py script", mandatory = true)
    private String mActsPath = null;

    @Option(name = "test-class",
            description = "The test class you want to run, only support one clase for now",
            mandatory = true)
    private String mTestClass = null;

    @Option(
        name = "test-cases",
        description =
                "The list of test cases you want to run, if not provided, the whole test"
                        + " class will be executed"
    )
    private List<String> mTestCases = new ArrayList<>();

    @Option(name = "test-bed",
            description = "The group of resources used for the test", mandatory = true)
    private String mTestbed = null;

    @Option(name = "test-logpath",
            description = "The root path for log file, it should match config value",
            mandatory = true)
    private String mTestlogpath = null;

    @Option(name = "test-token",
            description = "A token to serialize test execution based on testbed limitation")
    private boolean mTestToken = true;

    @Option(name = "test-timeout",
            description = "A timeout in MS for the act.py to run, default six hours",
            isTimeVal = true)
    private long mTestTimeout = 6 * 3600 * 1000;

    @Option(
        name = "override-test-bed-with-serial",
        description = "Force to use the testbed according to the device serial number."
    )
    private boolean mOverrideTestBed = false;

    static class TestInfo {
        public String mTestName = null;
        public String mClassName = null;
    }

    // Constants for running the tests
    private static final String PASS_REGEX_STR = ".*PASS.*";
    private static final String SKIP_REGEX_STR = ".*SKIP.*";
    private static final String JSON_RESULT = "Result";
    private static final String JSON_TEST_NAME = "Test Name";
    private static final String JSON_FILE_NAME = "/latest/test_run_summary.json";
    static final Semaphore runToken = new Semaphore(1);

    /**
     * The test do the following:
     * 1)Start the acts test in a new thread.
     * 2)Collect the acts result from stdout.
     * 3) parse logfile folder for pass failed details
     * @param listener
     * @throws DeviceNotAvailableException
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        try {
            if (mTestToken) {
                CLog.v("Waiting to acquire run token");
                runToken.acquire();
            }

            long start = System.currentTimeMillis();
            String mTestFile = "";
            if (mTestCases.isEmpty()) {
                mTestFile = mTestClass;
            } else {
                for(String testCase : mTestCases) {
                    if (mTestFile.isEmpty()) {
                        mTestFile = mTestClass.concat(":").concat(testCase);
                    } else {
                        mTestFile = mTestFile.concat(",").concat(testCase);
                    }
                }
            }

            if (mOverrideTestBed) {
                mTestbed = getDevice().getSerialNumber();
            }

            listener.testRunStarted(mTestClass, 0);
            TestIdentifier id = new TestIdentifier(getClass().getCanonicalName(),
                        "Executing " + mTestFile);
            listener.testStarted(id);
            String act_cmd = String.format("%s -c %s -tc %s -tb %s",
                        mActsPath, mTestConfigFile, mTestFile, mTestbed);
            CLog.d("ACTS command is '" + act_cmd + "'");
            CommandResult cr = RunUtil.getDefault().runTimedCmd(mTestTimeout, mActsPath, "-c",
                    mTestConfigFile, "-tc", mTestFile, "-tb", mTestbed);
            CommandStatus cs = cr.getStatus();
            File summaryFile = new File(mTestlogpath + "/" + mTestbed + JSON_FILE_NAME);
            if (cs == CommandStatus.SUCCESS) {
                if (summaryFile.exists() && !summaryFile.isDirectory()) {
                    CLog.d("We got the summary file " + summaryFile.getPath());
                } else {
                    listener.testFailed(id, "Not able to get json file " + summaryFile.getPath());
                }
            } else {
                listener.testFailed(id, "ACTS Test failed to run");
            }
            listener.testEnded(id, Collections.<String, String>emptyMap());
            // Parsing the summary file output
            Map<String, String> metrics = parseJsonSummaryFile(summaryFile, listener,
                    mTestlogpath, mTestbed);
            listener.testRunEnded(System.currentTimeMillis()-start, metrics);
        } catch (InterruptedException e) {
            CLog.e("Interrupted error running ACT command");
            CLog.e(e);
        } finally {
            if (mTestToken) {
                runToken.release();
            }
        }
    }

    /**
     * Parsing ACT summary JSON txt file to get test results
     * @param summaryFile The summary file from Acts logging all the pass/fail/skip in JSON format
     * @param listener
     */
    private Map<String, String> parseJsonSummaryFile(File summaryFile,
            ITestInvocationListener listener, String logpath, String testbedpath)  {
        Map<String, String> metrics = new HashMap<>();
        int passedNum, resultSize;
        int failedNum = 1000;
        int unknownNum = 1000;
        try {
            // Load JSON file into String
            BufferedReader in = new BufferedReader(new FileReader(summaryFile));
            String jsonInput = "";
            String temp = "";
            while ((temp = in.readLine()) != null) {
                jsonInput = jsonInput.concat(temp);
            }
            in.close();

            final JSONObject rootObj = new JSONObject(jsonInput);
            final JSONArray results = rootObj.getJSONArray("Results");
            final JSONObject summary = rootObj.getJSONObject("Summary");
            passedNum = summary.getInt("Passed");
            failedNum = summary.getInt("Failed");
            unknownNum = summary.getInt("Unknown");
            resultSize = results.length();
            metrics.put("Pass", String.format("%d", passedNum));
            metrics.put("Fail", String.format("%d", failedNum));
            JSONObject currResult;
            for (int i=0; i<resultSize; i++) {
                currResult = results.getJSONObject(i);
                TestIdentifier id = new TestIdentifier(getClass().getCanonicalName(),
                        currResult.getString(JSON_TEST_NAME));
                listener.testStarted(id);
                if (currResult.getString(JSON_RESULT).matches(PASS_REGEX_STR)) {
                    CLog.d(currResult.getString(JSON_TEST_NAME) + " has passed");
                } else if (currResult.getString(JSON_RESULT).matches(SKIP_REGEX_STR)) {
                    CLog.d(currResult.getString(JSON_TEST_NAME) + " has skipped");
                    listener.testIgnored(id);
                } else {
                    listener.testFailed(id, currResult.getString(JSON_TEST_NAME) + " has "
                            + currResult.getString(JSON_RESULT));
                }
                listener.testEnded(id, Collections.<String, String>emptyMap());
            }
        } catch (IOException e) {
            CLog.e("Not able to read from file");
            CLog.e(e);
            Assert.fail("Not able to read from file");
        } catch (JSONException e) {
            CLog.e("Not able to parse json file from file");
            CLog.e(e);
            Assert.fail("Not able to parse json file from file");
        }
        boolean anyTestFail = false;
        if (failedNum > 0 || unknownNum > 0) {
            anyTestFail = true;
        }
        // Load all the log files from ACTS folder to Sponge
        uploadLogFiles(listener, logpath + "/" + testbedpath + "/latest");
        // Delete host logfile folder based on testbed name
        File logfileRootDir = new File(logpath + "/" + testbedpath);
        FileUtil.recursiveDelete(logfileRootDir);
        return metrics;
    }

    /**
     *uploadLogFiles, upload all the files under the provided folder
     * @param listener
     * @param foldername The root folder for Acts log files used for uploading
     */
    private void uploadLogFiles(ITestInvocationListener listener, String foldername) {
        File folder = new File(foldername);
        File[] listOfFiles = folder.listFiles();
        String filename, shortFilename, directoryName;
        InputStreamSource outputSource = null;
        for (File file : listOfFiles) {
            if (file.isFile()) {
                filename = file.getPath();
                String[] filePathItems = filename.split("/latest/");
                shortFilename = filePathItems[filePathItems.length - 1];
                outputSource = new FileInputStreamSource(file);
                listener.testLog(shortFilename, LogDataType.TEXT, outputSource);
            } else if (file.isDirectory()) {
                directoryName = file.getAbsolutePath();
                uploadLogFiles(listener, directoryName);
            }
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

}
