// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.tradefed.build.DeviceBuildDescriptor;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.InvocationStatus;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.targetprep.BuildError;
import com.google.android.tradefed.result.BlackboxPostUtil.TestResultsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Base class for posting to the old release dashboard.
 *
 * <p>This class has been modified to post to Blackbox but {@link BlackboxResultReporter} and {@link
 * BlackboxPostUtil} should be used for new tests.
 */
abstract class AbstractRdbResultReporter extends AbstractRemoteResultReporter
        implements ITestSummaryListener {
    private String mSummaryUrl = null;
    private InvocationStatus mStatus = InvocationStatus.SUCCESS;

    @Option(
        name = "reporting-unit-key-suffix",
        description = "suffix to append after the regular reporting unit key"
    )
    private String mReportingUnitKeySuffix = null;

    @Option(
        name = "rdb-build-flavor",
        description =
                "build flavor to pass to release dashboard. If unspecified, will use flavor "
                        + "stored in build info."
    )
    private String mBuildFlavor = null;

    @Option(
        name = "rdb-build-flavor-suffix",
        description =
                "build flavor suffix to append. If unspecified, will use flavor stored in build "
                        + "info."
    )
    private String mBuildFlavorSuffix = null;

    @Option(
        name = "rdb-branch",
        description =
                "build branch to pass to release dashboard. If unspecified, will use branch "
                        + "stored in build info."
    )
    private String mBuildBranch = null;

    @Option(
        name = "rdb-build-id",
        description =
                "build id to pass to release dashboard. If unspecified, will use id stored in "
                        + "build info."
    )
    private String mBuildId = null;

    @Option(
        name = "report-on-failure",
        description = "report data to dashboard on invocation failure."
    )
    private boolean mSendReportOnFailure = false;

    @Option(name = "disable", description = "flag to skip reporting of all the results")
    private boolean mDisabled = false;

    @Option(name = "base-url", description = "url for data posting RPC services on app-engine.")
    private String mBaseUrl = "https://android-rdb-post.googleplex.com/";

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        if (cause instanceof BuildError) {
            mStatus = InvocationStatus.BUILD_ERROR;
        } else {
            mStatus = InvocationStatus.FAILED;
        }
    }

    /**
     * Report summary of results to release dashboard.
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);

        if (mDisabled) {
            return;
        }

        if (mStatus == InvocationStatus.SUCCESS || mSendReportOnFailure) {
            postData(getPrimaryBuildInfo());
        } else {
            CLog.d("Skipping posting because invocation status is %s.", mStatus.toString());
        }
    }

    /**
     * Abstract methods for posting subclass-chosen data to the release dashboard.
     *
     * @param buildInfo the meta data for the build under test
     */
    abstract protected void postData(IBuildInfo buildInfo);

    private String getBuildBranch(IBuildInfo buildInfo) {
        if (mBuildBranch != null) {
            return mBuildBranch;
        }
        return buildInfo.getBuildBranch();
    }

    private String getProduct(IBuildInfo build) {
        if (mBuildFlavor != null && mBuildFlavorSuffix != null) {
            throw new RuntimeException(
                    "Cannot have both rdb-build-flavor-suffix and rdb-build-flavor set.");
        }
        String buildFlavor;
        if (mBuildFlavor != null) {
            buildFlavor = mBuildFlavor;
        } else {
            buildFlavor = build.getBuildFlavor();
            if (buildFlavor == null) {
                CLog.w("Could not find build flavor");
                buildFlavor = "unknown";
            }
            if (mBuildFlavorSuffix != null) {
                buildFlavor = buildFlavor + mBuildFlavorSuffix;
            }
        }

        if (!DeviceBuildDescriptor.describesDeviceBuild(build)) {
            return buildFlavor;
        }

        DeviceBuildDescriptor deviceBuildInfo = new DeviceBuildDescriptor(build);
        String deviceBuildId = deviceBuildInfo.getDeviceBuildId();
        String deviceBuildFlavor = deviceBuildInfo.getDeviceBuildFlavor();

        return String.format("%s-%s-%s", buildFlavor, deviceBuildFlavor, deviceBuildId);
    }

    private String getBuildId(IBuildInfo buildInfo) {
        if (mBuildId != null) {
            return mBuildId;
        }
        return buildInfo.getBuildId();
    }

    /**
     * Post metrics to the dashboard.
     *
     * @param buildInfo the meta data for the build under test
     * @param reportingUnitKey the unique reporting unit key to use
     * @param metrics a {@link Map} of name-value pairs to post
     * @return <code>true</code> if post was successful. <code>false</code> otherwise
     */
    protected boolean postResults(
            IBuildInfo buildInfo,
            String reportingUnitKey,
            Map<String, String> metrics) {
        BlackboxPostUtil postUtil = new BlackboxPostUtil(mBaseUrl);

        if (mReportingUnitKeySuffix != null) {
            reportingUnitKey += mReportingUnitKeySuffix;
        }

        TestResultsBuilder builder = new TestResultsBuilder();
        builder.setTestSuite(reportingUnitKey);
        builder.setBranch(getBuildBranch(buildInfo));
        builder.setProduct(getProduct(buildInfo));
        builder.setBuildId(getBuildId(buildInfo));
        builder.addBenchmarkMetrics(null, metrics, getSummaryUrl());

        return postUtil.postTestResults(builder);
    }

    /** {@inheritDoc} */
    @Override
    public void putSummary(List<TestSummary> summaries) {
        if (summaries.isEmpty()) {
            return;
        }

        mSummaryUrl = summaries.get(0).getSummary().getString();
    }

    /** Return the summary URL to be posted to RDB */
    protected String getSummaryUrl() {
        return mSummaryUrl;
    }
}
