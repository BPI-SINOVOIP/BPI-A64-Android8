// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.result.ITestInvocationListener;

import java.util.Map;

/**
 * A {@link ITestInvocationListener} which reports results to Blackbox.
 */
@OptionClass(alias = "blackbox-reporter")
public class BlackboxResultReporter extends BaseBlackboxResultReporter {

    @Option(
        name = "test-suite",
        description =
                "The test suite overriding the test tag. This may not be used with the "
                        + "test-suite-from-test-run flag."
    )
    private String mTestSuite = null;

    @Option(
        name = "test-suite-from-test-run",
        description =
                "Whether to override the test suite with the test run name. This may not be used "
                        + "with the test-suite flag."
    )
    private boolean mTestSuiteFromRunName = false;

    /** {@inheritDoc} */
    @Override
    public void invocationStarted(IInvocationContext context) {
        super.invocationStarted(context);
        if (!mTestSuiteFromRunName) {
            String testSuite = mTestSuite != null ? mTestSuite : getInvocationContext().getTestTag();
            initPostRequest(testSuite);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String name, int numTests) {
        super.testRunStarted(name, numTests);
        if (mTestSuiteFromRunName) {
            initPostRequest(name);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        if (mTestSuiteFromRunName) {
            finalizePostRequest();
        }
        super.testRunEnded(elapsedTime, runMetrics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        if (!mTestSuiteFromRunName) {
            finalizePostRequest();
        }
        super.invocationEnded(elapsedTime);
    }

    public void setTestSuiteFromRunName(boolean flag) {
        mTestSuiteFromRunName = flag;
    }

}