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

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.StreamUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class PostingHelper {

    private PostingHelper() {
        //This is an static class and should never be instantiated
    }

    // Helper method to post a file to sponge
    public static void postFile(ITestInvocationListener listener, File file, LogDataType dataType,
            String prefix) {
        // Add file for sponge submission
        InputStreamSource outputSource = null;
        try {
            outputSource = new FileInputStreamSource(file);
            listener.testLog(prefix, dataType, outputSource);
        } finally {
            StreamUtil.cancel(outputSource);
        }
    }

    /**
     * Helper method to append a numeric result to a test invocation listener.
     *
     * @param listener The invocation listener.
     * @param reportingUnit The reporting unit to which the result will be reported (some use the
     *     test class).
     * @param schema The schema to which the result will be reported (some use the test method).
     * @param result The numeric result to be added.
     * @param lowerLimit The minimum value allowed for the result to be considered a pass.
     * @param upperLimit The maximum value allowed for the result to be considered a pass.
     */
    public static void postResult(
            ITestInvocationListener listener,
            String reportingUnit,
            String schema,
            double result,
            double lowerLimit,
            double upperLimit) {
        Map<String, String> metric = new HashMap<>();

        metric.put(schema, String.format("%.2f", result));

        CLog.d("metrics: About to report: %s:%s", reportingUnit, metric);

        listener.testRunStarted(reportingUnit, 0);

        TestIdentifier testID = new TestIdentifier(reportingUnit, schema);
        listener.testStarted(testID);

        // Report as Test Fail
        if (result < 0) {
            listener.testFailed(testID, "Instrumentation test failed");
        } else if (result > upperLimit) {
            String message =
                    String.format(
                            "Result is greater than allowed limit. (upper limit: %f, result: %s)",
                            upperLimit, result);
            listener.testFailed(testID, message);
        } else if (result < lowerLimit) {
            String message =
                    String.format(
                            "Result is lower than allowed limit. (lower limit: %f, result: %s)",
                            lowerLimit, result);
            listener.testFailed(testID, message);
        }

        listener.testEnded(testID, metric);
        listener.testRunEnded(0, metric);
    }

    public static void postResult(
            ITestInvocationListener listener, String reportingUnit, String schema, double result) {
        postResult(listener, reportingUnit, schema, result, Double.MIN_VALUE, Double.MAX_VALUE);
    }

    /**
     * Takes a label and appends a suffix to it if the suffix is not null or empty.
     *
     * @param label The label to which the suffix will be appended.
     * @param suffix The suffix to be appended.
     * @return A concatenation of the form label-suffix
     */
    public static String appendSuffix(String label, String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return label;
        }

        return String.format("%s%s%s", label, "-", suffix);
    }
}
