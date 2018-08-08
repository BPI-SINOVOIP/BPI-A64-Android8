/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.performance;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.testtype.UiAutomatorTest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * A harness that launches AntutuBenchmarkTest and reports result.
 *
 * Requires AntutuBenchmarkTest apk and AnTuTuUiAutomatorTest.apk
 */
public class AnTuTuBenchmarkTest extends UiAutomatorTest {

    private static final String LOGTAG = "AnTuTuBenchmarkScore";

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        ResultForwarder forwarder = new ResultForwarder(listener) {
            private boolean testFailed = false;

            @Override
            public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
                CLog.i("Test Ended");
                if (!testFailed) {
                    BufferedReader logcat =
                            new BufferedReader(new InputStreamReader(
                                            getDevice().getLogcatDump().createInputStream()));
                    try {
                        String line;
                        while ((line = logcat.readLine()) != null) {
                            if (!line.contains(LOGTAG)) {
                                continue;
                            }
                            line = line.substring(line.indexOf(LOGTAG) + LOGTAG.length());
                            String[] results = line.split(" : ");
                            String key = results[0].split(": ")[1];
                            String value = results[1];
                            runMetrics.put(key, value);
                            CLog.i("%s : %s", key, value);
                            if (key.equals("score")) {
                                break;
                            }
                        }
                    } catch (IOException e) {
                        CLog.e(e);
                    }
                }

                if (runMetrics.isEmpty()) {
                    testRunFailed("No result found in logcat");
                }
                super.testRunEnded(elapsedTime, runMetrics);
            }

            @Override
            public void testFailed(TestIdentifier test, String trace) {
                CLog.i("Test Failed");
                testFailed = true;
                super.testFailed(test, trace);
            }

            @Override
            public void testAssumptionFailure(TestIdentifier test, String trace) {
                CLog.i("Test Assumption Failure");
                testFailed = true;
                super.testAssumptionFailure(test, trace);
            }
        };

        CLog.v("Check to see if network is disconnected before test starts.");
        if (getDevice().checkConnectivity()) {
            throw new RuntimeException("Test failed. This test shouldn't run with network.");
        }

        CLog.i("Test Started");
        super.run(forwarder);
    }
}
