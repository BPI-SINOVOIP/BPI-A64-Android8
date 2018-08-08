// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionClass;


/**
 * Reports the test metrics for each run to the Release Dashboard.
 */
@OptionClass(alias = "rdb-metrics")
public class RdbRunMetricsResultReporter extends AbstractRdbResultReporter {

    /**
     * Post the metrics for each test run in this invocation to the release DB.
     *
     * @param buildInfo the meta data for the build under test
     */
    @Override
    protected void postData(IBuildInfo buildInfo) {
        for (TestRunResult runResults : getRunResults()) {
            if (runResults.getRunMetrics().size() > 0) {
                postResults(buildInfo, runResults.getName(), runResults.getRunMetrics());
            }
        }
    }
}
