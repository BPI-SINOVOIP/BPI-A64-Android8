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
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.AbiFormatter;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * start CoreMark and parse the result from stdout
 */
public class CoreMarkTest implements IDeviceTest, IRemoteTest {

    private static final String SCORE_DELIMITER = " / ";
    private static final String OUTPUTTAG = "CoreMark 1.0 : ";
    private static final String RUN_KEY = "coremark";
    private static final String DEVICE_TEMPORARY_DIR_PATH = "/data/local/tmp/";
    private static final String COREMARK_BINARY_FILENAME = "coremark|#ABI#|";

    @Option(name = AbiFormatter.FORCE_ABI_STRING,
            description = AbiFormatter.FORCE_ABI_DESCRIPTION,
            importance = Importance.IF_UNSET)
    private String mForceAbi = "32";

    // COREMARK_PARAMETER contains parameters for coremark
    // the first three are seeds, and the last one is iteration number.
    private static final String COREMARK_PARAMETER = " 0 0 102 110000";
    private static final String ERRMSG = "No result found in output!";
    private static final int MAX_ITERATIONS = 5;
    private String mBinaryPath;
    private ITestDevice mDevice;

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
     * run CoreMark once and parse the result and get the Iterations/Sec
     *
     * @return Iterations/Sec
     * @throws DeviceNotAvailableException
     */
    private double runAndGetResult() throws DeviceNotAvailableException {
        String result = getDevice().executeShellCommand(mBinaryPath + COREMARK_PARAMETER);
        int headPosition = result.indexOf(OUTPUTTAG);
        if (headPosition != -1) {
            result = result.substring(headPosition + OUTPUTTAG.length());
            int tailPosition = result.indexOf(SCORE_DELIMITER);
            if (tailPosition != -1) {
                result = result.substring(0, tailPosition);
                CLog.i("result : " + result);
                return Double.parseDouble(result);
            }
        }
        return -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mBinaryPath = AbiFormatter.formatCmdForAbi(DEVICE_TEMPORARY_DIR_PATH
                + COREMARK_BINARY_FILENAME, mForceAbi);
        TestIdentifier testId = new TestIdentifier(getClass().getCanonicalName(), RUN_KEY);
        ITestDevice device = getDevice();

        Assert.assertTrue(String.format("CoreMark binary not found on device: %s",
                mBinaryPath), device.doesFileExist(mBinaryPath));

        listener.testRunStarted(RUN_KEY, 0);
        listener.testStarted(testId);
        long testStartTime = System.currentTimeMillis();
        Map<String, String> metrics = new HashMap<String, String>();

        double maxScore = -1;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            RunUtil.getDefault().sleep(120000);
            double score = runAndGetResult();
            maxScore = maxScore > score ? maxScore : score;
        }

        if (maxScore == -1) {
            CLog.e(ERRMSG);
            listener.testFailed(testId, ERRMSG);
            listener.testRunFailed(ERRMSG);
        }
        else {
            metrics.put("score", Double.toString(maxScore));
            CLog.i("maxscore : " + maxScore);
        }
        long durationMs = System.currentTimeMillis() - testStartTime;
        listener.testEnded(testId, metrics);
        listener.testRunEnded(durationMs, metrics);
    }
}
