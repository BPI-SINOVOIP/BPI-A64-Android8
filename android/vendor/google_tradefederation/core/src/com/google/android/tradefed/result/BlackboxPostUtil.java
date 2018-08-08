// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.build.DeviceBuildDescriptor;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.IRunUtil.IRunnableResult;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.VersionParser;
import com.google.android.tradefed.util.SsoClientTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequestFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.protobuf.GeneratedMessage;
import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.PostErrorRequest;
import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.PostIgnoredRequest;
import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.PostTestResultsRequest;
import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.Result;
import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.Target;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A class which can be used to post to Blackbox. The provides a request builder to build and modify
 * a request, as well as a method to post the request to the backend.
 */
public class BlackboxPostUtil {

    private static final String CONTENT_TYPE = "application/octet-stream";
    private static final String POST_SUFFIX = "post/results/";
    private static final String IGNORED_SUFFIX = "post/ignore-info/";
    private static final String ERROR_SUFFIX = "post/error-info/";

    // SSO client has a default timeout of 20 seconds so be larger than that.
    private static final int OP_TIMEOUT_MS = 30 * 1000;
    private static final int MIN_POLL_MS = 1000;
    private static final int MAX_POLL_MS = 10 * 60 * 1000;
    private static final int MAX_TIME_MS = 10 * 60 * 1000;

    private final HttpRequestFactory mRequestFactory;

    private final GenericUrl mPostUrl;
    private final GenericUrl mIgnoredUrl;
    private final GenericUrl mErrorUrl;
    private int mMaxResults = 10000;

    public BlackboxPostUtil(String baseUrl) {
        mRequestFactory = new SsoClientTransport().createRequestFactory();

        GenericUrl url = new GenericUrl(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        mPostUrl = url.clone();
        mPostUrl.appendRawPath(POST_SUFFIX);
        mIgnoredUrl = url.clone();
        mIgnoredUrl.appendRawPath(IGNORED_SUFFIX);
        mErrorUrl = url.clone();
        mErrorUrl.appendRawPath(ERROR_SUFFIX);
    }

    /**
     * A helper class used to build a post request which includes the initial setup, adding test
     * case results, metric results, and coverage results.
     */
    public static class TestResultsBuilder {
        private final PostTestResultsRequest.Builder mRequestBuilder;

        // The current map of tests to run names. This is used to generate the Sponge target URL
        // for each test result in the post request.
        private final Map<String, String> mTestRunMap;

        /** Constructor for {@link TestResultsBuilder}. */
        public TestResultsBuilder() {
            mRequestBuilder =
                    PostTestResultsRequest.newBuilder()
                            .setHostname(getHostName())
                            .setClient(getClient());
            mTestRunMap = new HashMap<>();
        }

        /**
         * Get the map of tests to run names.
         */
        public Map<String, String> getTestRunMap() {
            return mTestRunMap;
        }

        /**
         * Set the build target info based on the build info. This will set the branch, product,
         * build ID, and build alias fields for values in the build info which are not null. It does
         * not set the test suite.
         *
         * @param buildInfo the {@link IBuildInfo} object
         */
        public TestResultsBuilder setBuildInfo(IBuildInfo buildInfo) {
            if (mRequestBuilder.getTargetsCount() == 0) {
                mRequestBuilder.addTargetsBuilder();
            }
            Target.Builder target = mRequestBuilder.getTargetsBuilder(0);
            if (buildInfo.getBuildBranch() != null) {
                target.setBranch(buildInfo.getBuildBranch());
            }
            if (buildInfo.getBuildFlavor() != null) {
                if (DeviceBuildDescriptor.describesDeviceBuild(buildInfo)) {
                    // TODO(b/34614279): Remove this when Blackbox supports multiple build targets
                    // posting. Right now, use the workaround for unbundled builds as described in
                    // com.google.android.tradefed.result.releasedashboard.Entry#getJson()
                    DeviceBuildDescriptor deviceBuildInfo = new DeviceBuildDescriptor(buildInfo);
                    if (deviceBuildInfo.getDeviceBuildFlavor() != null
                            && deviceBuildInfo.getDeviceBuildId() != null) {
                        target.setTarget(
                                String.format(
                                        "%s-%s-%s",
                                        buildInfo.getBuildFlavor(),
                                        deviceBuildInfo.getDeviceBuildFlavor(),
                                        deviceBuildInfo.getDeviceBuildId()));
                    }
                } else {
                    target.setTarget(buildInfo.getBuildFlavor());
                }
            }
            if (buildInfo.getBuildId() != null) {
                target.setBuildId(buildInfo.getBuildId());
            }

            return this;
        }

        /** Get the branch or null if it's not set. */
        public String getBranch() {
            if (mRequestBuilder.getTargetsCount() == 0) {
                return null;
            }
            return mRequestBuilder.getTargetsBuilder(0).getBranch();
        }

        /**
         * Sets the branch in the build target info. Cannot be null.
         *
         * @param branch the branch key.
         */
        public TestResultsBuilder setBranch(String branch) {
            if (mRequestBuilder.getTargetsCount() == 0) {
                mRequestBuilder.addTargetsBuilder();
            }
            mRequestBuilder.getTargetsBuilder(0).setBranch(branch);
            return this;
        }

        /** Get the product or null if it's not set. */
        public String getProduct() {
            if (mRequestBuilder.getTargetsCount() == 0) {
                return null;
            }
            return mRequestBuilder.getTargetsBuilder(0).getTarget();
        }

        /**
         * Sets the product in the build target info. Cannot be null.
         *
         * @param product the product key.
         */
        public TestResultsBuilder setProduct(String product) {
            if (mRequestBuilder.getTargetsCount() == 0) {
                mRequestBuilder.addTargetsBuilder();
            }
            mRequestBuilder.getTargetsBuilder(0).setTarget(product);
            return this;
        }

        /**
         * Get the test suite or null if it's not set.
         */
        public String getTestSuite() {
            return mRequestBuilder.getTestSuiteKey();
        }

        /**
         * Sets the test suite. Cannot be null.
         *
         * @param testSuite the test suite key.
         */
        public TestResultsBuilder setTestSuite(String testSuite) {
            mRequestBuilder.setTestSuiteKey(testSuite);
            return this;
        }

        /** Get the build ID or null if it's not set. */
        public String getBuildId() {
            if (mRequestBuilder.getTargetsCount() == 0) {
                return null;
            }
            return mRequestBuilder.getTargetsBuilder(0).getBuildId();
        }

        /**
         * Sets the build ID in the build target info. Cannot be null.
         *
         * @param buildId the build ID as a string.
         */
        public TestResultsBuilder setBuildId(String buildId) {
            if (mRequestBuilder.getTargetsCount() == 0) {
                mRequestBuilder.addTargetsBuilder();
            }
            mRequestBuilder.getTargetsBuilder(0).setBuildId(buildId);
            return this;
        }

        /**
         * Adds a test case to the {@link TestResultsBuilder}.
         *
         * @param test the {@link TestIdentifier}.
         * @param status the {@link TestStatus}.
         * @param url the URL or {@code null}.
         */
        public TestResultsBuilder addTestCase(TestIdentifier test, TestStatus status, String url) {
            if (test == null || status == null) {
                return this;
            }
            Result.Value.FunctionalResult value;
            switch (status) {
                case PASSED:
                    value = Result.Value.FunctionalResult.PASS;
                    break;
                case FAILURE:
                    value = Result.Value.FunctionalResult.FAIL;
                    break;
                // TODO(erowe): Figure out how to treat ASSUMPTION_FAILURES
                case ASSUMPTION_FAILURE:
                case INCOMPLETE:
                default:
                    value = Result.Value.FunctionalResult.NONE;
                    break;
            }

            Result.Builder result = Result.newBuilder()
                    .setTestCaseKey(test.getClassName() + "#" + test.getTestName())
                    .setType(Result.ResultType.FUNCTIONAL)
                    .setStatus(Result.Status.FINISHED)
                    .setValue(Result.Value.newBuilder().setFunctionalResult(value));
            if (url != null) {
                result.setUrl(url);
            }

            mRequestBuilder.addResults(result);

            return this;
        }

        /**
         * Adds a map of benchmark metrics to the PostTestResultsRequest.
         *
         * @param test the {@link TestIdentifier} if the metrics came from a test, or {@code null}
         * if the tests came from a test run.
         * @param metrics a map from key to value, where values should be strings which can be
         * parsed into doubles. If the values cannot be parsed, they are ignored.
         * @param url the URL or {@code null}.
         */
        public TestResultsBuilder addBenchmarkMetrics(TestIdentifier test,
                Map<String, String> metrics, String url) {
            for (Entry<String, String> metricEntry : metrics.entrySet()) {
                String key = metricEntry.getKey();
                double value;
                try {
                    value = Double.parseDouble(metricEntry.getValue());
                } catch (NumberFormatException e) {
                    CLog.w("Skipping key %s: %s is not a float.", key, metricEntry.getValue());
                    continue;
                }

                Result.Builder result = Result.newBuilder()
                        .setMetricKey(key)
                        .setValue(Result.Value.newBuilder().setFloatValue(value))
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED);
                if (test != null) {
                    result.setTestCaseKey(test.getClassName() + "#" + test.getTestName());
                }
                if (url != null) {
                    result.setUrl(url);
                }

                mRequestBuilder.addResults(result);
            }

            return this;
        }

        /**
         * Adds a coverage metric to the {@link TestResultsBuilder}.
         *
         * @param unit the name of the leaf unit. For a method, would be in the form
         * {@code [[package.]class]#method}.
         * @param linesMissed the number of missed lines.
         * @param linesTotal the number of total lines.
         * @param branchesMissed the number of missed branches.
         * @param branchesTotal the number of total branches.
         * @param instructionsMissed the number of missed instructions.
         * @param instructionsTotal the number of total instructions.
         * @param url the URL or {@code null}.
         */
        public TestResultsBuilder addCoverageResult(String unit,
                int linesMissed, int linesTotal,
                int branchesMissed, int branchesTotal,
                int instructionsMissed, int instructionsTotal,
                String url) {
            Result.Builder result = Result.newBuilder()
                    .setTestCaseKey(unit)
                    .setType(Result.ResultType.COVERAGE)
                    .setStatus(Result.Status.FINISHED)
                    .setValue(Result.Value.newBuilder()
                        .setCoverageResult(Result.Value.CoverageResult.newBuilder()
                            .setLinesMissed(linesMissed)
                            .setLinesTotal(linesTotal)
                            .setBranchesMissed(branchesMissed)
                            .setBranchesTotal(branchesTotal)
                            .setInstructionsMissed(instructionsMissed)
                            .setInstructionsTotal(instructionsTotal)));
            if (url != null) {
                result.setUrl(url);
            }

            mRequestBuilder.addResults(result);

            return this;
        }

        /** Returns the number of results. */
        public long getResultCount() {
            return mRequestBuilder.getResultsCount();
        }

        /**
         * Update the URL for all the results. This is helpful if you do not know the URL when
         * building the results.
         *
         * @param url the URL.
         */
        public void updateUrl(String url) {
            mRequestBuilder.setUrl(url);
            for (Result.Builder resultBuilder : mRequestBuilder.getResultsBuilderList()) {
                resultBuilder.setUrl(url);
            }
        }

        /**
         * Returns the top level URL for the results. This corresponds to the URL set in {@link
         * #updateUrl(String)} or the default URL in {@link #updateUrl(String, Map)}.
         */
        public String getUrl() {
            return mRequestBuilder.getUrl();
        }

        /**
         * Update the URL for all the results. A map of tests to URLs is provided to lookup the URL
         * for each result. If the URL is not found in the map, the defaultUrl is used to update the
         * result. This is helpful if you do not know the URL when building the results.
         *
         * @param defaultUrl the default URL.
         * @param testUrlMap the map of tests to URLs.
         */
        public void updateUrl(String defaultUrl, Map<String, String> testUrlMap) {
            mRequestBuilder.setUrl(defaultUrl);
            for (Result.Builder resultBuilder : mRequestBuilder.getResultsBuilderList()) {
                String url = testUrlMap.containsKey(resultBuilder.getTestCaseKey())
                        ? testUrlMap.get(resultBuilder.getTestCaseKey())
                        : defaultUrl;
                resultBuilder.setUrl(url);
            }
        }

        /**
         * Build the request and return the underlying protobuf.
         *
         * @return the {@link PostTestResultsRequest} proto.
         */
        public PostTestResultsRequest build() {
            return mRequestBuilder.build();
        }

        /** Return the underlying protobuf builder. */
        public PostTestResultsRequest.Builder builder() {
            return mRequestBuilder;
        }

        /** Return {@code null} if the builder is valid or the reason if the builder is invalid. */
        public String validate() {
            List<String> reasons = new LinkedList<>();
            if (getTestSuite() == null || getTestSuite().isEmpty()) {
                reasons.add("test suite is not set");
            }
            if (getBranch() == null || getBranch().isEmpty()) {
                reasons.add("branch is not set");
            }
            if (getProduct() == null || getProduct().isEmpty()) {
                reasons.add("product is not set");
            }
            if (getBuildId() == null || !getBuildId().matches("\\d+")) {
                reasons.add(String.format("build id \"%s\" is not a digit", getBuildId()));
            }
            if (builder().getResultsCount() == 0) {
                reasons.add("no results");
            }
            return reasons.isEmpty() ? null : String.join(", ", reasons);
        }
    }

    /**
     * Posts the given byte data to a URL, retrying with an escalating timeout.
     *
     * @param builder the {@link TestResultsBuilder} to post.
     * @return {@code true} if the post was successful, {@code false} otherwise.
     */
    public boolean postTestResults(TestResultsBuilder builder) {
        String validationReason = builder.validate();
        if (validationReason != null) {
            CLog.e("Validation failed: skip posting request due to %s", validationReason);
            postIgnored(builder, validationReason.replaceAll("\"[^\"]*\" ", ""));
            return false;
        }

        PostTestResultsRequest.Builder orig = builder.builder();
        List<List<Result>> shards = Lists.partition(orig.getResultsList(), mMaxResults);
        if (shards.size() > 1) {
            CLog.v(
                    "Request has %d results (more than %d), so splitting request",
                    orig.getResultsCount(), mMaxResults);
        }

        int shardIdx = 0;
        for (List<Result> shard : shards) {
            CLog.v("Sending shard %d of %d", shardIdx + 1, shards.size());

            PostTestResultsRequest.Builder copy = orig.clone();
            copy.clearResults();
            copy.setShardIndex(shardIdx);
            copy.setShardCount(shards.size());
            copy.addAllResults(shard);

            IRunnableResult runnable = getRunnable(mPostUrl, copy.build());
            if (!getRunUtil()
                    .runEscalatingTimedRetry(
                            OP_TIMEOUT_MS, MIN_POLL_MS, MAX_POLL_MS, MAX_TIME_MS, runnable)) {
                CLog.e("Could not post to Blackbox after retries");
                postError(builder, "could not post results");
            }

            shardIdx++;
        }
        return true;
    }

    private class Runnable implements IRunnableResult {
        private final GenericUrl mUrl;
        private final HttpContent mContent;

        public Runnable(GenericUrl url, GeneratedMessage proto) {
            mUrl = url;
            mContent = new ByteArrayContent(CONTENT_TYPE, proto.toByteArray());
        }

        @Override
        public boolean run() throws Exception {
            try {
                mRequestFactory.buildPostRequest(mUrl, mContent).execute();
                CLog.i("Post to Blackbox succeeded");
                return true;
            } catch (IOException e) {
                CLog.e("Post to Blackbox failed");
                CLog.e(e);
                return false;
            }
        }

        @Override
        public void cancel() {
            // Ignore
        }
    }

    /**
     * Posts to the Blackbox backend that the result was ignored. This is useful if the post was not
     * sent due to missing or invalid fields or if there were no results.
     */
    @VisibleForTesting
    boolean postIgnored(TestResultsBuilder builder, String reason) {
        PostIgnoredRequest.Builder request =
                PostIgnoredRequest.newBuilder()
                        .setHostname(getHostName())
                        .setClient(getClient())
                        .setTestSuite(builder.getTestSuite())
                        .setResultCount(builder.getResultCount());

        String url = builder.getUrl();
        if (url != null) {
            request.setUrl(url);
        }
        if (reason != null) {
            request.setReason(reason);
        }

        return postInfo(mIgnoredUrl, request.build());
    }

    /**
     * Posts to the Blackbox backend that there was an error posting results. This is useful if
     * there is an error between the request being sent and the backend receiving the request, or if
     * there are errors that the cannot be resolved through retries.
     */
    @VisibleForTesting
    boolean postError(TestResultsBuilder builder, String reason) {
        PostErrorRequest.Builder request =
                PostErrorRequest.newBuilder()
                        .setHostname(getHostName())
                        .setClient(getClient())
                        .setTestSuite(builder.getTestSuite())
                        .setResultCount(builder.getResultCount());

        String url = builder.getUrl();
        if (url != null) {
            request.setUrl(url);
        }
        if (reason != null) {
            request.setReason(reason);
        }

        return postInfo(mErrorUrl, request.build());
    }

    /** Posts a small info message to the backend. */
    private boolean postInfo(GenericUrl url, GeneratedMessage proto) {
        IRunnableResult runnable = getRunnable(url, proto);
        // Only try to post the message once, since this is a small, best effort attempt.
        CommandStatus status = getRunUtil().runTimed(OP_TIMEOUT_MS, runnable, false);
        if (!CommandStatus.SUCCESS.equals(status)) {
            CLog.e("Could not post to %s: %s", url.build(), status.toString());
            return false;
        }
        return true;
    }

    /**
     * Sets the maximum results to post in one request. If the number of requests is higher than
     * {@code maxResults}, then the results will be sharded.
     */
    public void setMaxResults(int maxResults) {
        mMaxResults = maxResults;
    }

    @VisibleForTesting
    static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            CLog.w("Could not get hostname: %s", e.getMessage());
            return "";
        }
    }

    @VisibleForTesting
    static String getClient() {
        return String.format("tradefed:%s", VersionParser.fetchVersion());
    }

    @VisibleForTesting
    IRunnableResult getRunnable(GenericUrl url, GeneratedMessage proto) {
        return new Runnable(url, proto);
    }

    @VisibleForTesting
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
