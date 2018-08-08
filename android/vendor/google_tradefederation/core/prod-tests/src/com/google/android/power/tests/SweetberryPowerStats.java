/*
 * Copyright (C) 2016 The Android Open Source Project
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

public class SweetberryPowerStats extends PowerStats {

    public SweetberryPowerStats(ITestInvocationListener listener, ITestDevice testDevice,
            List<PowerMeasurement> powerMeasurements, Map<String, String> schemaRUPair,
            String ruSuffix, String schemaSuffix) {
        super(listener, testDevice, powerMeasurements, schemaRUPair, ruSuffix, schemaSuffix);
    }

    @Override
    protected void postMetrics(List<PowerMetric> metrics, int decimalPlaces) {
        for (PowerMetric metric : metrics) {
            String schema = metric.getTag();
            String reportingUnit = mSchemaRUPair.get(schema);

            Map<String, Float> results = metric.getAverages();
            for (String rail : results.keySet()) {
                // For now only posting the VBAT rail is supported.
                if (rail.equals("VBAT uW")) {
                    CLog.d("Posting VBAT rail.");
                    String finalRu =
                            PostingHelper.appendSuffix(mSchemaRUPair.get(schema), mRuSuffix);
                    String finalSchema = PostingHelper.appendSuffix(schema, mSchemaSuffix);
                    Float result = results.get(rail) / 1000f;

                    // Rounds result to a given number of decimal places.
                    float pow = (float) Math.pow(10, decimalPlaces);
                    result = (float) Math.round(result * pow) / pow;

                    PostingHelper.postResult(mListener, finalRu, finalSchema, result);
                }

                CLog.d("metrics: About to report: %s: %s - %s : %f uW", reportingUnit, schema,
                        rail, results.get(rail));
            }
        }
    }
}
