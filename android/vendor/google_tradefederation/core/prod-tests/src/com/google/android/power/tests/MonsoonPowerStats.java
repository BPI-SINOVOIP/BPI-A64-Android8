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

package com.google.android.power.tests;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;

import java.util.List;
import java.util.Map;

/**
 * Data analysis class for calculating average power and posting test results to dashboard 1) Pull
 * test timestamp log from device 2) Parse test cases and their test periods 3) Calculate average
 * power for each test case 4) Post test results
 */
public class MonsoonPowerStats extends PowerStats {

    private static final String BATTERYLIFE_RU_SUFFIX = "BatteryLife";
    private static final String DOU_SCHEMA_NAME = "DOU";
    private final float mVoltage;
    private final boolean mReportUnitmW;
    private final boolean mReportBatteryLife;
    private final boolean mSaveResultOnHost;
    private final boolean mAppendResultOnHost;
    private Map<String, Double> mLowerLimits;
    private Map<String, Double> mUpperLimits;
    private final int mBatterySize;
    private final Map<String, Double> mDouWeightsMap;
    private static final float DEFAULT_TEST_FAILURE_VALUE = -1f;

    public MonsoonPowerStats(
            ITestInvocationListener listener,
            ITestDevice testDevice,
            List<PowerMeasurement> powerMeasurements,
            Map<String, String> schemaRUPair,
            String ruSuffix,
            String schemaSuffix,
            float voltage,
            boolean reportUnitmW,
            boolean reportBatteryLife,
            int batterySize,
            Map<String, Double> douWeightsMap,
            boolean saveResultOnHost,
            boolean appendResultOnHost,
            Map<String, Double> lowerLimits,
            Map<String, Double> upperLimits) {
        super(listener, testDevice, powerMeasurements, schemaRUPair, ruSuffix, schemaSuffix);
        mVoltage = voltage;
        mReportUnitmW = reportUnitmW;
        mReportBatteryLife = reportBatteryLife;
        mBatterySize = batterySize;
        mDouWeightsMap = douWeightsMap;
        mSaveResultOnHost = saveResultOnHost;
        mAppendResultOnHost = appendResultOnHost;
        mLowerLimits = lowerLimits;
        mUpperLimits = upperLimits;
    }

    // Post test results for all test cases
    @Override
    protected void postMetrics(List<PowerMetric> metrics, int decimalPlaces) {
        float douScore = 0f;
        String ruName = null;

        // Iterate through all the results that have passed and post them to Database
        for (PowerMetric metric : metrics) {
            String schema = metric.getTag();
            ruName = mSchemaRUPair.get(schema);
            final String finalRu = PostingHelper.appendSuffix(ruName, mRuSuffix);
            final String finalSchema = PostingHelper.appendSuffix(schema, mSchemaSuffix);

            Map<String, Float> results = metric.getAverages();

            if(results.size() == 0){
                CLog.d(String.format("Undefined average for: %s (i. e. No valid measurements for "
                        + "such schema)", schema));
                reportInvalidResult(finalRu, finalSchema);
                continue;
            }

            for (String name : results.keySet()) {
                Float result = results.get(name);

                CLog.d(String.format("Average for [%s] across all iterations is [%.2f]",
                        schema, result));

                // Calculate aggregated battery life in hours
                float resultInHrs = 0;
                if (mReportBatteryLife && mBatterySize > 0 && result > 0) {
                    resultInHrs = mBatterySize / (result * mVoltage);
                }

                if (mDouWeightsMap != null && !mDouWeightsMap.isEmpty() &&
                        mDouWeightsMap.containsKey(schema)) {
                    final double weight = mDouWeightsMap.get(schema).doubleValue();
                    CLog.d(String.format("Weight for [%s] is [%f]", schema, weight));
                    douScore += result * weight;
                }

                if (mReportUnitmW) {
                    result *= mVoltage;
                    CLog.d("Conversion to mW is [%f]mW", result);
                }

                // Rounds result to a given number of decimal places.
                float pow = (float) Math.pow(10, decimalPlaces);
                result = (float) Math.round(result * pow) / pow;

                reportValidResult(finalRu, finalSchema, result);
                if (mReportBatteryLife && resultInHrs > 0) {
                    reportValidResult(
                            PostingHelper.appendSuffix(ruName, mRuSuffix + BATTERYLIFE_RU_SUFFIX),
                            PostingHelper.appendSuffix(schema, mSchemaSuffix),
                            resultInHrs);
                }
            }
        }

        if (mDouWeightsMap != null && !mDouWeightsMap.isEmpty()) {
            if (mBatterySize <= 0) {
                CLog.d("Missing battery life parameter");
                return;
            }
            if (douScore <= 0) {
                CLog.d("None of the DOU tests passed");
                return;
            }
            douScore = (mBatterySize / douScore) / 24;
            CLog.d(String.format("DOU Score is %.2f", douScore));
            reportValidResult(
                    PostingHelper.appendSuffix(ruName, mRuSuffix),
                    PostingHelper.appendSuffix(DOU_SCHEMA_NAME, mSchemaSuffix),
                    douScore);
        }
    }

    private double getUpperLimit(String finalSchema) {
        if (mUpperLimits == null || !mUpperLimits.containsKey(finalSchema)) {
            return Double.MAX_VALUE;
        }

        return mUpperLimits.get(finalSchema);
    }

    private double getLowerLimit(String finalSchema) {
        if (mLowerLimits == null || !mLowerLimits.containsKey(finalSchema)) {
            return Double.MIN_VALUE;
        }

        return mLowerLimits.get(finalSchema);
    }

    private void reportInvalidResult(String finalRu, String finalSchema) {
        PostingHelper.postResult(mListener, finalRu, finalSchema, DEFAULT_TEST_FAILURE_VALUE);
    }

    private void reportValidResult(String finalRu, String finalSchema, double result) {
        PostingHelper.postResult(
                mListener,
                finalRu,
                finalSchema,
                result,
                getLowerLimit(finalSchema),
                getUpperLimit(finalSchema));

        if (mSaveResultOnHost) {
            ResultFileUtil resultFileUtil = new ResultFileUtil(mTestDevice);
            resultFileUtil.writeResultToFile(
                    finalRu, finalSchema, System.currentTimeMillis(), result, mAppendResultOnHost);
        }
    }
}
