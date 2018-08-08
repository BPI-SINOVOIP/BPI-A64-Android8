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

package com.google.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Reports the test metrics for each run to the Dashboard.
 */
public class PowerRunMetricsReporter extends AbstractRdbResultReporter {

    public static final String DEFAULT_FAILURE_VALUE = "-2";

    @Option(name = "schema_ru_pair", description = "Schema to Reporting Unit mapping")
    private Map<String, String> mSchemaRUPair = new HashMap<>();

    @Option(name = "ru_suffix", description = "Suffix to be appended in RU")
    private String mRuSuffix = "";

    @Option(name = "schema_suffix", description = "Suffix to be appended in schema")
    private String mSchemaSuffix = "";

    private Map<String, String> mFinalSchemaMap;

    private Map<String, String> getFinalSchemaMap() {
        if (mFinalSchemaMap != null) {
            return mFinalSchemaMap;
        }

        mFinalSchemaMap = new HashMap<>();
        Iterator<String> it = mSchemaRUPair.keySet().iterator();
        while (it.hasNext()) {
            String schema = it.next();
            String ru = mSchemaRUPair.get(schema);

            // Append suffixes.
            if (!mSchemaSuffix.isEmpty()) {
                schema = String.format("%s-%s", schema, mSchemaSuffix);
            }
            if (!mRuSuffix.isEmpty()) {
                ru = String.format("%s-%s", ru, mRuSuffix);
            }
            mFinalSchemaMap.put(schema, ru);
        }

        return mFinalSchemaMap;
    }

    @Override
    protected void postData(IBuildInfo buildInfo) {
        Map<TestIdentifier, Boolean> mReportedMetrics = new HashMap<>();

        // First post all the available results.
        for (TestRunResult result : getRunResults()) {
            Collection<TestIdentifier> identifiers = getIdentifiers(result);
            for (TestIdentifier id : identifiers) {
                postResults(buildInfo, id, result);
                mReportedMetrics.put(id, Boolean.TRUE);
            }
        }

        // Then post the default values to all the missing expected result.
        for (String schema : getFinalSchemaMap().keySet()) {
            String ru = getFinalSchemaMap().get(schema);
            TestIdentifier testId = new TestIdentifier(ru, schema);
            if (mReportedMetrics.getOrDefault(testId, Boolean.FALSE)) {
                // Result previously posted.
                continue;
            }
            postDefaultResults(buildInfo, testId);
        }
    }

    private Collection<TestIdentifier> getIdentifiers(TestRunResult result) {
        Collection<TestIdentifier> identifiers = new ArrayList<TestIdentifier>();
        String ru = result.getName();
        for (String schema : result.getRunMetrics().keySet()) {
            identifiers.add(new TestIdentifier(ru, schema));
        }

        return identifiers;
    }

    private void postResults(IBuildInfo buildInfo, TestIdentifier testId, TestRunResult result) {
        Map<String, String> metrics = new HashMap<>();
        metrics.put(testId.getTestName(), result.getRunMetrics().get(testId.getTestName()));
        CLog.d(
                "Posting test result ru:%s schema:%s value:%s",
                testId.getClassName(), testId.getTestName(), metrics.get(testId.getTestName()));
        postResults(buildInfo, testId.getClassName(), metrics);
    }

    private void postDefaultResults(IBuildInfo buildInfo, TestIdentifier testId) {
        Map<String, String> metrics = new HashMap<>();
        // The default value for a power metric will be -2 to tell apart tool failures from test
        // failures.
        metrics.put(testId.getTestName(), DEFAULT_FAILURE_VALUE);
        CLog.e(
                "Posting error result ru:%s schema:%s value:%s",
                testId.getClassName(), testId.getTestName(), metrics.get(testId.getTestName()));
        postResults(buildInfo, testId.getClassName(), metrics);
    }
}
