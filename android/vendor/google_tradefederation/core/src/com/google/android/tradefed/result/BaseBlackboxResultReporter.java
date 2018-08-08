// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.net.HttpHelper;

import com.google.android.tradefed.result.BlackboxPostUtil.TestResultsBuilder;
import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link ITestInvocationListener} which reports results to Blackbox.
 */
abstract class BaseBlackboxResultReporter implements ITestSummaryListener, ITestInvocationListener {

    @Option(name = "disable", description = "If the reporter is disabled.")
    protected boolean mDisable = false;

    @Option(name = "base-url", description = "The base URL for the posting interface")
    private String mBaseUrl = "https://android-rdb-post.googleplex.com/";

    // TODO(b/38178329): Remove this option when you can configure on the server side.
    @Option(
        name = "post-benchmark-results",
        description = "Whether to post benchmark test results reported by testRunEnded()"
    )
    private boolean mPostBenchmarkResults = true;

    // TODO(b/38178329): Remove this option when you can configure on the server side.
    @Option(
        name = "post-functional-results",
        description = "Whether to post functional test results"
    )
    private boolean mPostFunctionalResults = true;

    // TODO(b/38178329): Remove this option when you can configure on the server side.
    @Option(
        name = "post-test-benchmark-results",
        description = "Whether to post benchmark test results reported by testEnded()"
    )
    private boolean mPostTestBenchmarkResults = true;

    @Option(
        name = "post-on-invocation-failure",
        description = "Whether to post results if the invocation fails"
    )
    private boolean mPostOnInvocationFailure = true;

    @Option(
        name = "max-results-per-request",
        description =
                "The number of results to send in one request. Warning: this should only be set if "
                        + "there are bugs preventing the default number of requests."
    )
    private int mMaxResults = 0;

    private IInvocationContext mContext = null;
    private boolean mInvocationFailed = false;

    // Summary URL from the ITestSummaryListener, normally the sponge link
    private String mSummaryUrl = null;

    // Need to keep track of all builders because we potentially get the summary link when
    // TestInvocationEnded is called for previous reporters, and then we need to populate the URLs
    // for each result.
    private List<TestResultsBuilder> mTestResultBuilders = new LinkedList<>();

    // The current request builder, calls to addTestCase and addMetrics will append results to this.
    private TestResultsBuilder mTestResultsBuilder = null;

    // The current test status. This is needed since the API will call testStarted, optionally a
    // testFailed, and then testEnded without the status.
    private TestStatus mCurrentTestStatus = null;

    // The current run name. This is used by tests to know the current run, and build its URL with
    // the run name as a parameter.
    private String mCurrentRunName = null;

    private BlackboxPostUtil mPostUtil = null;

    /** {@inheritDoc} */
    @Override
    public void invocationStarted(IInvocationContext context) {
        mContext = context;

        if (mDisable) {
            return;
        }

        mPostUtil = new BlackboxPostUtil(mBaseUrl);
        if (mMaxResults > 0) {
            mPostUtil.setMaxResults(mMaxResults);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String name, int numTests) {
        if (mDisable) {
            return;
        }

        mCurrentRunName = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testStarted(TestIdentifier test) {
        mCurrentTestStatus = TestStatus.INCOMPLETE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        if (mDisable) {
            return;
        }

        Exception notAnException = new Exception("See stack trace:");
        TestResultsBuilder testResultsBuilder = getCurrentRequestBuilder();
        if (testResultsBuilder == null) {
            CLog.e("testEnded() called before initPostRequest(), returning");
            CLog.e(notAnException);
            return;
        }
        if (mCurrentTestStatus == null) {
            CLog.e("testEnded() called before testStarted(), returning");
            CLog.e(notAnException);
            return;
        }

        // Only treat the test status as passed if testFailed, testAssumptionFailure, or testIgnored
        // was not called.
        if (TestStatus.INCOMPLETE.equals(mCurrentTestStatus)) {
            mCurrentTestStatus = TestStatus.PASSED;
        }
        if (mPostFunctionalResults) {
            testResultsBuilder.addTestCase(test, mCurrentTestStatus, null);
        }
        if (mPostTestBenchmarkResults) {
            testResultsBuilder.addBenchmarkMetrics(test, testMetrics, null);
        }
        testResultsBuilder.getTestRunMap().put(test.toString(), mCurrentRunName);
        mCurrentTestStatus = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        if (mDisable) {
            return;
        }

        mCurrentTestStatus = TestStatus.FAILURE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testAssumptionFailure(TestIdentifier test, String trace) {
        if (mDisable) {
            return;
        }

        mCurrentTestStatus = TestStatus.ASSUMPTION_FAILURE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testIgnored(TestIdentifier test) {
        if (mDisable) {
            return;
        }

        mCurrentTestStatus = TestStatus.IGNORED;
    }

    /** {@inheritDoc} */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        if (mDisable) {
            return;
        }

        mCurrentRunName = null;

        TestResultsBuilder testResultsBuilder = getCurrentRequestBuilder();
        if (testResultsBuilder == null) {
            CLog.e("testRunEnded() called before initPostRequest(), returning");
            CLog.e(new Exception("See stack trace:"));
            return;
        }

        if (mPostBenchmarkResults) {
            testResultsBuilder.addBenchmarkMetrics(null, runMetrics, null);
        }
    }

    @Override
    public void invocationFailed(Throwable cause) {
        mInvocationFailed = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        if (mDisable) {
            return;
        }

        if (mInvocationFailed && !mPostOnInvocationFailure) {
            clearTestResultsBuilds();
            CLog.i(
                    "Invocation failed, will not post (to enable posting, use "
                            + "--post-on-invocation-failed)");
            return;
        }

        // Update the URLs with Sponge target URLs, and submit the results
        for (TestResultsBuilder postRequest : getRequestBuilders()) {
            if (mSummaryUrl != null) {
                Map<String, String> testUrlMap = new HashMap<>();

                for (Entry<String, String> testRunEntry : postRequest.getTestRunMap().entrySet()) {
                    String url = getTargetUrl(mSummaryUrl, testRunEntry.getValue());
                    testUrlMap.put(testRunEntry.getKey(), url);
                }

                postRequest.updateUrl(mSummaryUrl, testUrlMap);
            }
            postRequest(postRequest);
        }
    }

    /**
     * Builds the Sponge target URL for the run. invocationUrl provides the base URL and
     * invocationId as a parameter to build the target URL. runName is another URL parameter needed.
     *
     * @param invocationUrl the URL of the invocation.
     * @param runName the name of the run.
     */
    @VisibleForTesting
    String getTargetUrl(String invocationUrl, String runName) {
        String pattern = "(http[s]?:\\/\\/sponge(-qa)?.corp.google.com)\\/([^\\?\\s]+)\\?" +
                "([^\\s]*&)?id=([^&\\s]*)(&[^\\s]*)?";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(invocationUrl);
        if (!m.matches()) {
            return invocationUrl;
        }

        String baseSpongeUrl = m.group(1);
        String invocationId = m.group(5);

        if (baseSpongeUrl == null || invocationId == null || runName == null) {
            return invocationUrl;
        }

        MultiMap<String, String> paramMap = new MultiMap<>();
        paramMap.put("id", invocationId);
        paramMap.put("target", runName);
        paramMap.put("show", "FAILED");
        paramMap.put("sortBy", "STATUS");
        return new HttpHelper().buildUrl(String.format("%s/target", baseSpongeUrl), paramMap);
    }

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

    public void setDisable(boolean flag) {
        mDisable = flag;
    }

    public void setPostOnInvocationFailure(boolean flag) {
        mPostOnInvocationFailure = flag;
    }

    @VisibleForTesting
    void postRequest(TestResultsBuilder postRequest) {
        mPostUtil.postTestResults(postRequest);
    }

    @VisibleForTesting
    void setInvocationContext(IInvocationContext context) {
        mContext = context;
    }

    @VisibleForTesting
    void setPostBenchmarkResults(boolean post) {
        mPostBenchmarkResults = post;
    }

    @VisibleForTesting
    void setPostFunctionalResults(boolean post) {
        mPostFunctionalResults = post;
    }

    @VisibleForTesting
    void setPostTestBenchmarkResults(boolean post) {
        mPostTestBenchmarkResults = post;
    }

    /**
     * Retrieves the list of {@link TestResultsBuilder}s representing result sets under each test
     * suite
     */
    List<TestResultsBuilder> getRequestBuilders() {
        return mTestResultBuilders;
    }

    /**
     * Retrieves the current test result set
     */
    TestResultsBuilder getCurrentRequestBuilder() {
        return mTestResultsBuilder;
    }

    /**
     * Sets the current {@link TestResultsBuilder} tracking test results. May set to <code>null
     * </code> to clear the current test result set.
     */
    void setCurrentRequestBuilder(TestResultsBuilder testResultsBuilder) {
        mTestResultsBuilder = testResultsBuilder;
    }

    /**
     * Enqueues the {@link TestResultsBuilder} for future actions, e.g. posting.
     */
    void enqueuCurrentRequestBuilder(TestResultsBuilder testResultsBuilder) {
        mTestResultBuilders.add(testResultsBuilder);
    }

    /**
     * Clears the cached list of {@link TestResultsBuilder}s.
     */
    void clearTestResultsBuilds() {
        mTestResultBuilders.clear();
    }

    /**
     * Initializes the current PostTestResultsRequest, either during {@link #invocationStarted} or
     * {@link #testRunStarted} depending on whether or not the test run is used as the test suite.
     */
    TestResultsBuilder initPostRequest(String testSuite) {
        TestResultsBuilder testResultsBuilder = getOrCreatePostRequestForTestSuite(testSuite);
        setCurrentRequestBuilder(testResultsBuilder);
        return testResultsBuilder;
    }

    TestResultsBuilder getOrCreatePostRequestForTestSuite(String testSuite) {
        // always do creation for default implementation
        return createPostRequestForTestSuite(testSuite);
    }

    TestResultsBuilder createPostRequestForTestSuite(String testSuite) {
        return new TestResultsBuilder().setTestSuite(testSuite);
    }

    /**
     * Adds the current PostTestResultsRequest to the collection and sets it to null, either during
     * {@link #invocationEnded} or {@link #testRunEnded} depending on whether or not the test run
     * is used as the test suite.
     */
    void finalizePostRequest() {
        TestResultsBuilder testResultsBuilder = getCurrentRequestBuilder();
        if (testResultsBuilder == null) {
            CLog.w("Finalizing in incorrect order, request builder is null");
            return;
        }

        testResultsBuilder.setBuildInfo(mContext.getBuildInfos().get(0));

        CLog.i(
                "Will report results to Blackbox for branch \"%s\", target \"%s\", "
                        + "test suite \"%s\", and build ID \"%s\".",
                testResultsBuilder.getBranch(),
                testResultsBuilder.getProduct(),
                testResultsBuilder.getTestSuite(),
                testResultsBuilder.getBuildId());

        enqueuCurrentRequestBuilder(testResultsBuilder);
        setCurrentRequestBuilder(null);
    }

    /** Returns the injected {@link IInvocationContext} */
    IInvocationContext getInvocationContext() {
        return mContext;
    }
}
