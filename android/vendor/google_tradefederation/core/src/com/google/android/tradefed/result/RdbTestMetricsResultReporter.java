// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.google.common.base.Joiner;

import java.util.Map;
import java.util.Map.Entry;

/**
 * Reports the test metrics for each test to the Release Dashboard.
 */
@OptionClass(alias = "rdb-test-metrics")
public class RdbTestMetricsResultReporter extends AbstractRdbResultReporter {

    @Option(name="include-run-name", description="include test run name in reporting unit")
    private boolean mIncludeRunName = false;

    private final static String SEPARATOR = "#";

    /**
     * Post the metrics for each test in this invocation to the release DB.
     *
     * @param buildInfo {@link IBuildInfo}
     */
    @Override
    protected void postData(IBuildInfo buildInfo) {
        for (TestRunResult runResults : getRunResults()) {
            Map<TestIdentifier, TestResult> testResultMap = runResults.getTestResults();
            for(Entry<TestIdentifier, TestResult> entry : testResultMap.entrySet()) {
                TestIdentifier testIdentifier = entry.getKey();
                TestResult testResult = entry.getValue();
                if (testResult.getMetrics().size() > 0) {
                    Joiner joiner = Joiner.on(SEPARATOR).skipNulls();
                    String reportingUnit = joiner.join(
                           mIncludeRunName ? runResults.getName() : null,
                           testIdentifier.getClassName(), testIdentifier.getTestName());
                    postResults(buildInfo, reportingUnit, testResult.getMetrics());
                }
            }
        }
    }
}
