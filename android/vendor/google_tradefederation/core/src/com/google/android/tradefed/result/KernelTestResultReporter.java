// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import com.android.tradefed.build.IKernelBuildInfo;
import com.android.tradefed.build.KernelDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.InvocationStatus;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.net.HttpHelper;
import com.android.tradefed.util.net.IHttpHelper;
import com.android.tradefed.util.net.IHttpHelper.DataSizeException;

import java.io.IOException;
import java.util.List;

/**
 * Reports the test results for kernel tests to the kernel build service.
 */
public class KernelTestResultReporter extends CollectingTestListener
        implements ITestSummaryListener{

    /**
     * Enum to store test status which the kernel build service understands.
     */
    public enum KernelTestStatus {
        PASSED("passed"),
        FAILED("failed"),
        TOOL_ERROR("tool_error"),
        BUILD_ERROR("build_error"),
        TESTING("testing");

        private String mStatus;

        KernelTestStatus(String status) {
            mStatus = status;
        }

        @Override
        public String toString() {
            return mStatus;
        }
    }

    private String mSummaryUrl = null;
    private InvocationStatus mStatus = InvocationStatus.SUCCESS;
    private KernelDeviceBuildInfo mBuildInfo = null;

    @Option(name = "kernel-hostname", description = "the host of the kernel build server")
    private String mHostName = "http://vpbs1.mtv.corp.google.com:8080";

    /**
     * {@inheritDoc}
     */
    @Override
    public void putSummary(List<TestSummary> summaries) {
        if (summaries.isEmpty()) {
            return;
        }

        mSummaryUrl = summaries.get(0).getSummary().getString();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Expects a {@link KernelDeviceBuildInfo} for primary build.
     */
    @Override
    public void invocationStarted(IInvocationContext context) {
        super.invocationStarted(context);
        if (!(getPrimaryBuildInfo() instanceof KernelDeviceBuildInfo)) {
            CLog.e("Build info was not a KernelDeviceBuildInfo");
            return;
        }

        mBuildInfo = (KernelDeviceBuildInfo) getPrimaryBuildInfo();
        setStatus(getUrl(), mBuildInfo, KernelTestStatus.TESTING, mSummaryUrl);
    }

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
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);
        if (mBuildInfo == null) {
            CLog.e("Build info was not set");
            return;
        }
        switch(mStatus) {
            case SUCCESS:
                if (hasFailedTests()) {
                    setStatus(getUrl(), mBuildInfo, KernelTestStatus.FAILED, mSummaryUrl);
                } else {
                    setStatus(getUrl(), mBuildInfo, KernelTestStatus.PASSED, mSummaryUrl);
                }
                break;
            case BUILD_ERROR:
                setStatus(getUrl(), mBuildInfo, KernelTestStatus.BUILD_ERROR, mSummaryUrl);
                break;
            case FAILED:
                setStatus(getUrl(), mBuildInfo, KernelTestStatus.TOOL_ERROR, mSummaryUrl);
        }
    }

    /**
     * Sets the test result status for a {@link KernelDeviceBuildInfo}.
     *
     * @param url the URL for the kernel, in the form {@code HOSTNAME/KERNEL_BRANCH/SHA1}
     * @param buildInfo the {@link KernelDeviceBuildInfo} object
     * @param testStatus a {@link KernelTestStatus} status object
     * @param testUrl a invocation URL, may be null
     */
    public void setStatus(String url, KernelDeviceBuildInfo buildInfo,
            KernelTestStatus testStatus, String testUrl) {
        setStatus(url, buildInfo, buildInfo.getDeviceBuildId(), testStatus, testUrl);
    }

    /**
     * Sets the test result status for a {@link IKernelBuildInfo}.
     *
     * @param url the URL for the kernel, in the form {@code HOSTNAME/KERNEL_BRANCH/SHA1}
     * @param buildInfo the {@link IKernelBuildInfo} object
     * @param deviceBuildId the build id of the device build.
     * @param testStatus a {@link KernelTestStatus} status object
     * @param testUrl a invocation URL, may be null
     */
    public void setStatus(String url, IKernelBuildInfo buildInfo, String deviceBuildId,
            KernelTestStatus testStatus, String testUrl) {
        IHttpHelper helper = getHttpHelper();
        final MultiMap<String, String> params = new MultiMap<String, String>();
        params.put("test-tag", buildInfo.getTestTag());
        params.put("build-flavor", buildInfo.getBuildFlavor());
        params.put("build-id", deviceBuildId);
        params.put("test-status", testStatus.toString());
        if (testUrl != null) {
            params.put("test-url", testUrl);
        }

        try {
            if (helper.doPostWithRetry(url, helper.buildParameters(params)) == null) {
                CLog.e("Could not set status for test with url %s", helper.buildUrl(url, params));
            }
        } catch (IOException e) {
            CLog.e("Could not set status for test with url %s", helper.buildUrl(url, params));
        } catch (DataSizeException e) {
            CLog.e("Could not set status for test with url %s", helper.buildUrl(url, params));
        }
    }

    /**
     * Set the host name of the remote server.
     */
    public void setHostName(String host) {
        mHostName = host;
    }

    /**
     * Formats the URL based on the host and build info. Exposed for unit testing.
     *
     * @return a URL in the form {@code HOSTNAME/KERNEL_BRANCH/SHA1}
     */
    private String getUrl() {
        return String.format("%s/%s/%s/", mHostName, mBuildInfo.getBuildBranch(),
                mBuildInfo.getSha1());
    }

    /**
     * Get {@link IHttpHelper} to use. Exposed for unit testing.
     */
    IHttpHelper getHttpHelper() {
        return new HttpHelper();
    }

    /**
     * Set the build info. Exposed for unit testing.
     */
    void setBuildInfo(KernelDeviceBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * Set the summary URL. Exposed for unit testing.
     */
    void setSummaryUrl(String url) {
        mSummaryUrl = url;
    }
}
