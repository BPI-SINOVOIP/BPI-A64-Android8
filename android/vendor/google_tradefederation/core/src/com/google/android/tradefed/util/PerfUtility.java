// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import com.google.common.base.Joiner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Utility class that contains Perf metrics related methods.
 */
public class PerfUtility {

    private static final String TEST_HEADER_SEPARATOR = "\n\n";
    private static final String METRIC_SEPARATOR = "\n";
    private static final String METRIC_KEY_VALUE_SEPARATOR = ":";
    private final static String SEPARATOR = "#";

    /**
     * Parse test run and test metrics from run results and write it to a file.
     *
     * @param runResults which contains the test run and test metrics.
     * @return File while contains the test metrics, which need to be uploaded in sponge.
     * @throws IOException
     */
    public static File writeResultsToFile(Collection<TestRunResult> runResults) throws IOException {
        File resultsFile = FileUtil.createTempFile("test_results", "");
        FileOutputStream outputStream = new FileOutputStream(resultsFile);
        // loops over all test runs
        try {
            for (TestRunResult runResult : runResults) {
                // Parse run metrics
                if (runResult.getRunMetrics().size() > 0) {
                    outputStream.write(String.format("%s%s", runResult.getName(),
                            TEST_HEADER_SEPARATOR).getBytes());
                    for (Entry<String, String> entry : runResult.getRunMetrics().entrySet()) {
                        String test_metric = String.format("%s%s%s", entry.getKey(),
                                METRIC_KEY_VALUE_SEPARATOR, entry.getValue());
                        outputStream.write(String.format("%s%s", test_metric,
                                METRIC_SEPARATOR).getBytes());
                    }
                    outputStream.write(TEST_HEADER_SEPARATOR.getBytes());
                }

                // Parse test metrics
                Map<TestIdentifier, TestResult> testResultMap = runResult.getTestResults();
                for (Entry<TestIdentifier, TestResult> entry : testResultMap.entrySet()) {
                    TestIdentifier testIdentifier = entry.getKey();
                    TestResult testResult = entry.getValue();
                    Joiner joiner = Joiner.on(SEPARATOR).skipNulls();
                    String testName = joiner.join(testIdentifier.getClassName(),
                            testIdentifier.getTestName());
                    outputStream.write(String.format("%s%s", testName, TEST_HEADER_SEPARATOR)
                            .getBytes());
                    if (testResult.getMetrics().size() > 0) {
                        for (Entry<String, String> metric : testResult.getMetrics().entrySet()) {
                            String test_metric = String.format("%s%s%s", metric.getKey(),
                                    METRIC_KEY_VALUE_SEPARATOR, metric.getValue());
                            outputStream.write(String.format("%s%s", test_metric,
                                    METRIC_SEPARATOR).getBytes());
                        }
                    }
                    outputStream.write(TEST_HEADER_SEPARATOR.getBytes());
                }
            }
        } finally {
            outputStream.flush();
            StreamUtil.close(outputStream);
        }
        return resultsFile;
    }
}
