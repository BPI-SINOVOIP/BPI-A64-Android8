// Copyright 2013 Google Inc. All Rights Reserved.
package com.google.android.aupt;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;

import java.util.Map;

/**
 * A result reporter that will post the number of AUPT iterations to the release dashboard,
 * in addition to the results obtained by parsing the logcat.
 */
public class AuptLogcatAnalysisReporter extends LogcatAnalysisReporter {
    private int mTestCount = 0;
    private int mFailCount = 0;

    @Option(name="test-count-key", description="Reporting unit to post total test count")
    private String mTestCountKey = "aupt";
    @Option(name="fail-count-key", description="Reporting unit to post failure count")
    private String mFailCountKey = "aupt-errors";

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        mTestCount += 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        mFailCount += 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testAssumptionFailure(TestIdentifier test, String trace) {
        mFailCount += 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        mRdb.post(mTestCountKey, mTestCount);
        mRdb.post(mFailCountKey, mFailCount);
    }
}
