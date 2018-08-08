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

package com.google.android.power.tests;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** DoU test suite result poster */
public class DouResultsPoster implements IDeviceTest, IRemoteTest {

    // Defined as part of the calculation by Verizon
    private static final float DAILY_EXCHANGE_EMAILS_RECEIVED = 96.0f;
    private static final float DAILY_GMAILS_RECEIVED = 36.0f;
    private static final float EMAIL_PULSE_WIDTH_IN_MIN = 5.0f / 60.0f;
    private static final float MINUTES_IN_A_DAY = 1440.0f;

    private static final String BACKGROUND_IDLE_TEST_METHOD = "testBackgroundIdle";
    private static final String RADIO_IDLE_TEST_SCHEMA = "DoUVerizonTests-testIdle-RADIOIdleTest";
    private static final String RECEIVE_EXCHANGE_TEST_METHOD = "testReceiveExchangeEmail";
    private static final String RECEIVE_GMAIL_TEST_METHOD = "testReceiveGmail";

    @Option(
        name = "schema_ru_pair",
        description = "Schema to reporting unit mapping",
        mandatory = true
    )
    protected Map<String, String> mSchemaRUPair = new HashMap<String, String>();

    @Option(
        name = "schema_weight_pair",
        description = "Schema to test weight mapping",
        mandatory = true
    )
    protected Map<String, Float> mSchemaWeightPair = new HashMap<String, Float>();

    @Option(name = "schema_result_pair", description = "Schema to power result mapping")
    protected Map<String, Float> mSchemaResultPair = new HashMap<String, Float>();

    @Option(name = "battery_capacity", description = "Device battery capacity")
    protected float mBatteryCapacity = 0.0f;

    @Option(name = "dou_schema", description = "DoU schema name")
    protected String mDouSchema = "";

    @Option(name = "ru_suffix", description = "Suffix to be appended in RU")
    protected String mRuSuffix = "";

    @Option(name = "schema_suffix", description = "Suffix to be appended in schema")
    protected String mSchemaSuffix = "";

    @Option(name = "only_post_total_score", description = "Only post the final DoU score")
    private boolean mOnlyPostTotalScore = false;

    private ITestDevice mTestDevice;

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        CLog.d(String.format("Post DoU Results for %s", mDouSchema));

        float douScore = 0.0f;
        ResultFileUtil resultFileUtil = new ResultFileUtil(getDevice());
        Map<String, Float> finalSchemaResultPair = new HashMap<String, Float>();

        // Process power test results stored on host
        for (String schema : mSchemaRUPair.keySet()) {
            CLog.d(String.format("Check RU pair schema = %s", schema));
            // User-input test results override local host's test results
            if (mSchemaResultPair.containsKey(schema)) {
                finalSchemaResultPair.put(schema, mSchemaResultPair.get(schema));
                continue;
            }

            String ruName = mSchemaRUPair.get(schema);
            String finalRu = PostingHelper.appendSuffix(ruName, mRuSuffix);
            String finalSchema = PostingHelper.appendSuffix(schema, mSchemaSuffix);
            List<Float> validResults = resultFileUtil.extractResultsFromFile(finalRu, finalSchema);
            if (validResults.size() > 0) {
                float total = 0.0f;
                for (Float value : validResults) {
                    total += value.floatValue();
                }

                float average = total / validResults.size();
                finalSchemaResultPair.put(schema, average);
            }
        }

        boolean testCompleted = true;
        for (String schema : mSchemaWeightPair.keySet()) {
            String ruName = mSchemaRUPair.get(schema);

            if (finalSchemaResultPair.containsKey(schema)) {
                float weight = mSchemaWeightPair.get(schema);
                float result = finalSchemaResultPair.get(schema);
                if (weight > 0.0) {
                    douScore += result * weight;
                } else {
                    // Special calculation is required
                    if (schema.contains(RECEIVE_EXCHANGE_TEST_METHOD)) {
                        douScore +=
                                result
                                        * EMAIL_PULSE_WIDTH_IN_MIN
                                        * DAILY_EXCHANGE_EMAILS_RECEIVED
                                        / MINUTES_IN_A_DAY;
                    } else if (schema.contains(RECEIVE_GMAIL_TEST_METHOD)) {
                        douScore +=
                                result
                                        * EMAIL_PULSE_WIDTH_IN_MIN
                                        * DAILY_GMAILS_RECEIVED
                                        / MINUTES_IN_A_DAY;
                    } else if (schema.contains(BACKGROUND_IDLE_TEST_METHOD)
                            && finalSchemaResultPair.containsKey(RADIO_IDLE_TEST_SCHEMA)) {
                        float idleRadioResult = finalSchemaResultPair.get(RADIO_IDLE_TEST_SCHEMA);
                        float scoreAdder = result - idleRadioResult;
                        if (scoreAdder >= 0.0) {
                            douScore += scoreAdder;
                        } else {
                            /* Score is invalid if the background idle test has a lower value than
                              that of the radio idle test.
                            */
                            CLog.d("Error: Background Idle result less than Radio Idle result");
                            testCompleted = false;
                        }
                    }
                }

                /* Even though individual Dou test run has already posted result,
                  we should still repost the individual test results here, for two reasons:

                  1. There may be multiple test runs.  Only this function computes the average
                     across all runs, and posts the average

                  2. User may want to specify manually measured results through schema_result_pair
                     flag.  Some DoU tests, such as Verizon map navigation, requires the power
                     to be measured in the field.  This provides an opportunity for users to
                     manually insert a test measurement result into the automated DoU test run.
                */
                if (!mOnlyPostTotalScore) {
                    CLog.d(String.format("Post result for %s %s = %f", ruName, schema, result));
                    String finalRu = PostingHelper.appendSuffix(ruName, mRuSuffix);
                    String finalSchema = PostingHelper.appendSuffix(schema, mSchemaSuffix);
                    PostingHelper.postResult(listener, finalRu, finalSchema, result);
                }
            } else {
                testCompleted = false;
                CLog.d(String.format("Result for %s %s is not found", ruName, schema));
            }
        }

        if (mBatteryCapacity > 0.0f) {
            String ruName = mSchemaRUPair.get(mDouSchema);
            if (testCompleted) {
                douScore = mBatteryCapacity / (douScore / 100.0f);
                douScore = (float) (Math.floor(douScore * 100) / 100.0);
            } else {
                douScore = -1.0f;
            }
            CLog.d(String.format("Post result for %s %s = %f", ruName, mDouSchema, douScore));

            String finalRu = PostingHelper.appendSuffix(ruName, mRuSuffix);
            String finalSchema = PostingHelper.appendSuffix(mDouSchema, mSchemaSuffix);
            PostingHelper.postResult(listener, finalRu, finalSchema, douScore);
        }
    }
}
