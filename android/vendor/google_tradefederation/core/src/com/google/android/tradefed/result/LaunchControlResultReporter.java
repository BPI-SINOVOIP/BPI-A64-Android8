// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.google.android.tradefed.build.SsoClientHttpHelper;

import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.InvocationStatus;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.net.HttpHelper;
import com.android.tradefed.util.net.IHttpHelper;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;

/**
 * Reports a summary of the test results to launch control.
 * <p/>
 * Note, results will only show up on the build page if one of the following test tags is used:
 * <ul>
 * <li>LargeSuite</li>
 * <li>MediumSuite</li>
 * <li>SmallSuite</li>
 * <li>functional</li>
 * <li>EmulatorSmokeTests</li>
 * <li>DeviceSmokeTests</li>
 * <li>UnitTests</li>
 * </ul>
 */
@OptionClass(alias = "launch-control")
public class LaunchControlResultReporter extends CollectingTestListener
        implements ITestSummaryListener {

    private static final String LOG_TAG = "LaunchControlResultReporter";
    private static final String LC_REPORT_URL = "http://%s/buildbot-update";
    private String mStatisticsUrl = null;
    private String mSummaryUrl = null;
    private String mGenericUrl = null;
    private InvocationStatus mStatus = InvocationStatus.SUCCESS;

    private static final String HOSTNAME = "hostname";
    @Option(name = HOSTNAME, description = "launch control host name")
    private String mLcHostname = "android-build.corp.google.com";

    @Option(name = "report-on-failure", description =
            "report data to lc on invocation failure.")
    private boolean mSendOnFailure = false;

    @Option(name = "query-timeout", description =
            "Maximum number of seconds to wait for a single LC query to return.")
    private int mQueryTimeoutSec = 180;

    @Option(name = "query-max-time", description =
            "Maximum number of minutes to try to query LC. Note an escalting backoff is used.")
    private int mQueryRetryMin = 31;

    @Option(name = "use-sso-client", description = "whether or not we should query LC with " +
            "sso_client.")
    private boolean mUseSsoClient = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void putSummary(List<TestSummary> summaries) {
        // By convention, only store the first summary that we see as the detail URL.  Overwrite it
        // if we see a result set that is an instance of StatisticsTestSummary.
        if (summaries.isEmpty()) {
            return;
        }

        mSummaryUrl = summaries.get(0).getSummary().getString();

        ListIterator<TestSummary> iter = summaries.listIterator();
        while (iter.hasNext()) {
            TestSummary summary = iter.next();
            if (summary instanceof StatisticsTestSummary) {
                StatisticsTestSummary sts = (StatisticsTestSummary)summary;

                mSummaryUrl = sts.getSummary().getString();
                mStatisticsUrl = sts.getStatistics().getString();
                mGenericUrl = sts.getGeneric().getString();
                return;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IInvocationContext context) {
        super.invocationStarted(context);
        // Only report to the primary build for now
        reportStartingTest(context.getBuildInfos().get(0));
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

    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);
        switch (mStatus) {
            case SUCCESS: {
                // FIXME: TestSummary may need to handle an extended data field (with a Map) so that
                // FIXME: things can provide more detailed info like this
                //reportSuccess(mBuildInfo, getInvocationUrl(), getInvocationSummaryUrl(),
                //        getBaseSpongeUrl());
                reportSuccess();
                break;
            } case BUILD_ERROR: {
                //reportBootError(mBuildInfo, getInvocationUrl(), getBaseSpongeUrl());
                reportBootError();
                break;
            } case FAILED:
                if (mSendOnFailure) {
                    // report success is badly worded in this case, but what should happen is
                    // test pass/fail counts are posted. Typically this is used in case where
                    // test results themselves capture fact that something went amiss
                    reportSuccess();
                }
                break;
        }
    }

    /**
     * A trampoline to use default args for reportSuccess
     */
    private void reportSuccess() {
        reportSuccess(getInvocationContext().getBuildInfos().get(0), mSummaryUrl,
                mStatisticsUrl, mGenericUrl);
    }

    /**
     * A trampoline to use default args for reportSuccess
     */
    private void reportBootError() {
        reportBootError(getInvocationContext().getBuildInfos().get(0), mSummaryUrl, mGenericUrl);
    }

    /**
     * Return the http helper to use.
     * <p/>
     * Exposed for unit testing
     */
    IHttpHelper getHttpHelper() {
        IHttpHelper helper = null;
        if (mUseSsoClient) {
            helper = new SsoClientHttpHelper();

        } else {
            helper = new HttpHelper();
        }
        helper.setOpTimeout(mQueryTimeoutSec * 1000);
        helper.setMaxTime(mQueryRetryMin * 60 * 1000);
        return helper;
    }

    // FIXME: create a common API library that LaunchControlResultReporter and LaunchControlProvider
    // FIXME: can share
    /**
     * Actually do the work of talking to the backend
     */
    void performPost(MultiMap<String, String> paramMap) {
        try {
            IHttpHelper helper = getHttpHelper();
            helper.doGetIgnore(helper.buildUrl(getUrl(), paramMap));
        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Failed to post to %s", getUrl()));
        }
    }

    /**
     * Report to LC that we are starting to run a test on the specified build.
     *
     * @param buildInfo the {@link IBuildInfo}. Assumes {@link IBuildInfo#getBuildTargetName()} and
     *            {@link IBuildInfo#getTestTag()} have been set to values launch control will
     *            recognize.
     */
    void reportStartingTest(IBuildInfo buildInfo) {
        MultiMap<String, String> paramMap = new MultiMap<String, String>();
        setCommonFields(paramMap, "NOTIFY-STARTING-TEST", buildInfo);

        performPost(paramMap);
    }

    /**
     * Report success of invocation to launch control
     *
     * @param buildInfo the {@link IBuildInfo}. Assumes {@link IBuildInfo#getBuildTargetName()} and
     *            {@link IBuildInfo#getTestTag()} have been set to values launch control will
     *            recognize.
     * @param testReportUrl an http url to a page showing detailed test results
     * @param testStatisticsUrl an http url that returns the test summary details. Expected format
     *            returned is
     *            <pre>
     *                (ratio of tests passed/total tests)|num incomplete tests|num failed tests|total tests
     *            </pre>
     * @param genericReportUrl an http url to the main test results repository
     */
    void reportSuccess(IBuildInfo buildInfo, String testReportUrl,
            String testStatisticsUrl, String genericReportUrl) {

        MultiMap<String, String> paramMap = new MultiMap<String, String>();
        setCommonFields(paramMap, "FUNC_TEST_LINK", buildInfo);

        paramMap.put("link", strNotNull(testReportUrl));
        paramMap.put("per_link", strNotNull(testStatisticsUrl));
        paramMap.put("generic_link", strNotNull(genericReportUrl));

        performPost(paramMap);
    }

    /**
     * Report a boot error to launch control
     *
     * @param buildInfo the {@link IBuildInfo}. Assumes {@link IBuildInfo#getBuildTargetName()} and
     *            {@link IBuildInfo#getTestTag()} have been set to values launch control will
     *            recognize.
     * @param testReportUrl a http url to a page showing detailed test results
     * @param genericReportUrl a http url to the main test results repository
     */
    void reportBootError(IBuildInfo buildInfo, String testReportUrl,
            String genericReportUrl) {

        MultiMap<String, String> paramMap = new MultiMap<String, String>();
        setCommonFields(paramMap, "BOOT_FAIL_TEST", buildInfo);

        paramMap.put("link", strNotNull(testReportUrl));
        paramMap.put("generic_link", strNotNull(genericReportUrl));

        performPost(paramMap);
    }

    private String strNotNull(String str) {
        return str == null ? "" : str;
    }

    /**
     * A helper function to pull the common bits out of the {@link IBuildInfo} and dump them into
     * the query parameter map.
     */
    void setCommonFields(MultiMap<String, String> paramMap, String op, IBuildInfo build) {
        paramMap.put("op", op);
        paramMap.put("id", build.getBuildTargetName());
        paramMap.put("bid", build.getBuildId());
        paramMap.put("tag", build.getTestTag());
    }

    /**
     * Returns the LC url.
     * <p />
     * Exposed for unit testing
     */
    String getUrl() {
        if (mLcHostname == null || mLcHostname.isEmpty()) {
            throw new IllegalArgumentException("Missing required option " + HOSTNAME);
        }
        return String.format(LC_REPORT_URL, mLcHostname);
    }
}
