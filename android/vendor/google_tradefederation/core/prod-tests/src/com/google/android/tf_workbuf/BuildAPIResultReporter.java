// Copyright 2014 Google Inc.  All Rights Reserved.
package com.google.android.tf_workbuf;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.InvocationStatus;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.targetprep.BuildError;

import org.json.JSONException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * An {@link ITestSummaryListener} that reports test results to the Build API
 */
@OptionClass(alias = "build-api-reporter")
public class BuildAPIResultReporter extends CollectingTestListener
        implements IConfigurationReceiver, ITestSummaryListener {

    /** if the invocation fails, we'll see an invocationFailed call */
    private InvocationStatus mStatus = InvocationStatus.SUCCESS;

    private IConfiguration mConfiguration;

    private IBuildAPIHelper mHelper = null;

    private String mSummaryUrl = null;

    /**
     * Create an {@link BuildAPIResultReporter}.
     */
    public BuildAPIResultReporter() {
        super();
    }

    /**
     * Unit testing constructor
     */
    BuildAPIResultReporter(IBuildAPIHelper helper) {
        if (helper == null) {
            throw new NullPointerException();
        }
        mHelper = helper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable e) {
        super.invocationFailed(e);

        if (e instanceof BuildError) {
            mStatus = InvocationStatus.BUILD_ERROR;
        } else {
            mStatus = InvocationStatus.FAILED;
        }
        mStatus.setThrowable(e);
    }

    /**
     * Triggers posting results to the Build API.
     * <p />
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);

        postResultsToBuildApi();
    }

    /**
     * Return a human-readable explanation of the results.  Will be displayed in a web UI, so may
     * contain hyperlinks.
     */
    String summarizeTestResult() {
        StringBuilder sum = new StringBuilder(String.format(
                "Ran %d tests.  %d failed.",
                getNumTotalTests(), getNumAllFailedTests()));
        if (mSummaryUrl != null) {
            sum.append("\n");
            sum.append("For more details see: ");
            sum.append(mSummaryUrl);
        }
        return sum.toString();
    }

    /**
     * Post results to the Build API, and complain to the log if it doesn't work
     */
    void postResultsToBuildApi() {
        if (mHelper == null) {
            mHelper = (BuildAPIHelper) mConfiguration.getConfigurationObject("build-api-helper");
            try {
                mHelper.setupTransport();
            } catch (GeneralSecurityException | IOException | NullPointerException e) {
                CLog.e("Failed to initialize BuildAPIHelper; result reporter will deactivate.");
                CLog.e(e);
                mHelper = null;
                return;
            }
        }

        final IBuildInfo build = getBuildInfo();
        final String attemptId = build.getBuildAttributes().get("attemptId");
        if (attemptId == null) {
            CLog.e("Cannot post test results to Build API: attemptId is unset in buildInfo");
            CLog.e("Build info: %s", build);
            return;
        }

        CLog.i("About to post test status %s to build API for %s/%s/%s", mStatus.toString(),
                build.getBuildTargetName(), attemptId, build.getBuildId());
        try {
            mHelper.postTestResults(build.getBuildId(), build.getBuildTargetName(), attemptId,
                    !hasFailedTests(), summarizeTestResult(), mStatus);

            CLog.d("Post was successful.");
        } catch (IOException | JSONException e) {
            CLog.e("Failed to post test results to the Build API");
            CLog.e(e);
        }
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    @Override
    public void putSummary(List<TestSummary> summaries) {
        if (summaries.size() > 0) {
            mSummaryUrl = summaries.get(0).getSummary().getString();
        }
    }
}
