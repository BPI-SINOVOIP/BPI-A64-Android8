/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * start GFXBench and parse the result from JSON files.
 */
public class GFXBench3Test implements IDeviceTest, IRemoteTest {

    @Option(name = "package-name", description = "package which has the tests", mandatory = true)
    private String mPackageName;

    @Option(name = "test-list", description = "Tests need to be executed", mandatory = true)
    private String mTestList;

    @Option(name = "run-key", description = "Run key used for reporting results", mandatory = true)
    private String mRunKey;

    @Option(name = "stress-loop-time", description = "Repeat test run until specified time has "
            + "reached or exceeded; use any valid time value format, defaults to 0 (run once)",
            isTimeVal = true)
    private long mStressLoopTime = 0;

    // half an hour timeout
    private static final int TIMEOUT_MS = 1000 * 60 * 30;
    private static final String AM_CMD = "am broadcast -n"
            + " %s/net.kishonti.benchui.corporate.CommandLineSession"
            + " -a net.kishonti.testfw.ACTION_RUN_TESTS -e test_ids %s";
    private ITestDevice mDevice;
    private String resultPath;
    private String amCmd;

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
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        ITestDevice device = getDevice();

        //Construct the result path
        resultPath = String.format("${EXTERNAL_STORAGE}/Android/data/%s/files/results/", mPackageName);

        //Construct the am cmd
        amCmd = String.format(AM_CMD, mPackageName, mTestList);

        //Remove pre-existing result folder
        mDevice.executeShellCommand(String.format("rm -rf %s*",resultPath));
        listener.testRunStarted(mRunKey, 0);

        TestIdentifier testId = new TestIdentifier(getClass().getCanonicalName(), mRunKey);
        long testStartTime = System.currentTimeMillis();
        listener.testStarted(testId);
        do {
            device.executeShellCommand(String.format(amCmd, mTestList));
            waitForTestToFinish();
        } while ((System.currentTimeMillis() - testStartTime) < mStressLoopTime);
        long durationMs = System.currentTimeMillis() - testStartTime;
        listener.testEnded(testId, new HashMap<String, String>());
        Map<String, String> metrics = parseResults(listener);
        listener.testRunEnded(durationMs, metrics);
    }

    private Map<String, String> parseResults(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        Map<String, String> metrics = new HashMap<String, String>();

        // The result directory is named of the timestamp.
        IFileEntry resultFilepath = getDevice().getFileEntry(resultPath);
        if (resultFilepath == null) {
            listener.testRunFailed("No result folder found!");
            return metrics;
        }
        // only assert if when not running in stress mode
        if (mStressLoopTime == 0) {
            Assert.assertEquals("More than one result folder found", 1,
                    resultFilepath.getChildren(false).size());
        }
        String resultFolder = null;
        for (IFileEntry resultDir : resultFilepath.getChildren(false)) {
                resultFolder = resultDir.getName().trim();
        }
        if (resultFolder != null) {
            // Each testcase writes its result into a JSON file named of <testcase>.json under
            // the result directory.
            String[] tests = mTestList.split(",");
            for (String test : tests) {
                TestIdentifier miniTestId =
                        new TestIdentifier(getClass().getCanonicalName(), test);
                listener.testStarted(miniTestId);
                String fn = String.format("%s%s/%s.json", resultPath, resultFolder, test);
                File file = getDevice().pullFile(fn);
                CLog.d("Parsing result file: %s", fn);
                if (file != null) {
                    try {
                        // The result JSON file looks like this:
                        // {"results":[{
                        //    "error_string":"",
                        //    "status":"OK",
                        //    "test_id":"gl_alu",
                        //    "score":586.0,
                        //    ...
                        //    }]}
                        String finalString = getFileContent(file);
                        JSONObject result = new JSONObject(finalString)
                                .getJSONArray("results").getJSONObject(0);
                        if (result.getString("status").equals("OK")) {
                            metrics.put(result.getString("test_id"),
                                    Integer.toString(result.getInt("score")));
                        } else {
                            listener.testFailed(miniTestId,
                                    result.getString("error_string"));
                        }
                    } catch (IOException | JSONException e) {
                        listener.testFailed(miniTestId, e.toString());
                    }
                } else {
                    listener.testFailed(miniTestId,
                            String.format("No valid output at %s", fn));
                }
                listener.testEnded(miniTestId, metrics);
            }
        } else {
            listener.testRunFailed("No result folder found!");
        }
        return metrics;
    }

    /**
     * @throws DeviceNotAvailableException
     */
    private void waitForTestToFinish() throws DeviceNotAvailableException {
        long startMs = System.currentTimeMillis();
        do {
            if (System.currentTimeMillis() - startMs >= TIMEOUT_MS) {
                CLog.w("No response after %ds", TIMEOUT_MS / 1000);
                throw new RuntimeException("max test timeout reached");
            }
            RunUtil.getDefault().sleep(10 * 1000);
        } while (null != getDevice().getProcessByName(mPackageName));
        CLog.d("Finished");
    }

    /**
     * To return entire contents of the file as String
     * @param file
     * @return
     */
    private String getFileContent(File file) throws IOException {
        byte[] buffer = new byte[1024];
        StringBuilder content = new StringBuilder();
        FileInputStream fileStream = new FileInputStream(file);
        while (fileStream.read(buffer) != -1) {
            content.append(new String(buffer));
        }
        fileStream.close();
        return content.toString();
    }
}
