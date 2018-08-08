// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.result.TestSummary;

import com.google.android.tradefed.result.BlackboxPostUtil.TestResultsBuilder;
import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.PostTestResultsRequest;
import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.Result;
import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.Target;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link BlackboxResultReporter}. */
public abstract class CommonBlackboxResultReporterTest {

    private static final String BUILD_BRANCH = "branch";
    private static final String BUILD_ID = "123456";
    private static final String BUILD_FLAVOR = "build_flavor";
    public static final String TEST_TAG = "test_tag";

    private BaseBlackboxResultReporter mReporter;
    private IBuildInfo mBuildInfo;
    private IInvocationContext mContext;

    private List<TestResultsBuilder> mTestResultCaptures;

    @Before
    public void setUp() {
        mBuildInfo = new BuildInfo(BUILD_ID, null);
        mBuildInfo.setBuildBranch(BUILD_BRANCH);
        mBuildInfo.setBuildFlavor(BUILD_FLAVOR);
        mTestResultCaptures = new LinkedList<>();
    }

    void addTestResult(TestResultsBuilder result) {
        mTestResultCaptures.add(result);
    }

    List<TestResultsBuilder> getCapturedResults() {
        return mTestResultCaptures;
    }

    void setResultReporter(BaseBlackboxResultReporter reporter) {
        mReporter = reporter;
    }

    void setInvocationContext(IInvocationContext context) {
        mContext = context;
    }

    @Test
    public void testInvocation() {
        String invocationUrl =
                "http://sponge.corp.google.com/invocation?tab=Test+Cases&show=FAILED&"
                        + "show=INTERNAL_ERROR&id=5714563b-8836-40c1-88ec-10ee679668e5";
        List<TestSummary> summaries = new ArrayList<>();
        summaries.add(new TestSummary(invocationUrl));

        String runUrlBase = "http://sponge.corp.google.com/target?show=FAILED&sortBy=STATUS" +
                "&id=5714563b-8836-40c1-88ec-10ee679668e5&target=";
        String runUrl1 = runUrlBase + "run1";
        String runUrl2 = runUrlBase + "run2";

        Map<String, String> metrics = new HashMap<>();
        metrics.put("key1", "1.0");
        metrics.put("key2", "2.0");
        metrics.put("key3", "ignored");

        mReporter.invocationStarted(mContext);
        mReporter.testRunStarted("run1", 1);
        mReporter.testStarted(new TestIdentifier("package.class", "pass"));
        mReporter.testEnded(new TestIdentifier("package.class", "pass"), metrics);
        mReporter.testStarted(new TestIdentifier("package.class", "fail"));
        mReporter.testFailed(new TestIdentifier("package.class", "fail"), "");
        mReporter.testEnded(new TestIdentifier("package.class", "fail"), Collections.emptyMap());
        mReporter.testStarted(new TestIdentifier("package.class", "assumption"));
        mReporter.testAssumptionFailure(new TestIdentifier("package.class", "assumption"), "");
        mReporter.testEnded(
                new TestIdentifier("package.class", "assumption"), Collections.emptyMap());
        mReporter.testStarted(new TestIdentifier("package.class", "ignored"));
        mReporter.testIgnored(new TestIdentifier("package.class", "ignored"));
        mReporter.testEnded(new TestIdentifier("package.class", "ignored"), Collections.emptyMap());
        mReporter.testRunEnded(0, metrics);
        mReporter.testRunStarted("run2", 1);
        mReporter.testStarted(new TestIdentifier("package.class2", "pass"));
        mReporter.testEnded(new TestIdentifier("package.class2", "pass"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        Assert.assertEquals(0, mReporter.getRequestBuilders().size());
        mReporter.putSummary(summaries);
        mReporter.invocationEnded(0);

        List<BlackboxPostUtil.TestResultsBuilder> requests = mReporter.getRequestBuilders();
        Assert.assertEquals(1, requests.size());

        PostTestResultsRequest request = requests.get(0).build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.setTestSuiteKey(TEST_TAG);
        expected.addTargets(
                Target.newBuilder()
                        .setBranch(BUILD_BRANCH)
                        .setTarget(BUILD_FLAVOR)
                        .setBuildId(BUILD_ID));
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        expected.setUrl(invocationUrl);
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#pass")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setUrl(runUrl1)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.PASS)));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#pass")
                        .setMetricKey("key1")
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED)
                        .setUrl(runUrl1)
                        .setValue(Result.Value.newBuilder().setFloatValue(1.0)));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#pass")
                        .setMetricKey("key2")
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED)
                        .setUrl(runUrl1)
                        .setValue(Result.Value.newBuilder().setFloatValue(2.0)));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#fail")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setUrl(runUrl1)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.FAIL)));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#assumption")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setUrl(runUrl1)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.NONE)));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#ignored")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setUrl(runUrl1)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.NONE)));
        expected.addResults(
                Result.newBuilder()
                        .setMetricKey("key1")
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED)
                        .setUrl(invocationUrl)
                        .setValue(Result.Value.newBuilder().setFloatValue(1.0)));
        expected.addResults(
                Result.newBuilder()
                        .setMetricKey("key2")
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED)
                        .setUrl(invocationUrl)
                        .setValue(Result.Value.newBuilder().setFloatValue(2.0)));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class2#pass")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setUrl(runUrl2)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.PASS)));
        Assert.assertEquals(expected.build(), request);

        Assert.assertEquals(1, mTestResultCaptures.size());
        Assert.assertTrue(
                Arrays.equals(
                        expected.build().toByteArray(),
                        mTestResultCaptures.get(0).build().toByteArray()));
    }

    @Test
    public void testInvocation_benchmark() {
        String invocationUrl =
                "http://sponge.corp.google.com/invocation?tab=Test+Cases&show=FAILED&"
                        + "show=INTERNAL_ERROR&id=5714563b-8836-40c1-88ec-10ee679668e5";
        List<TestSummary> summaries = new ArrayList<>();
        summaries.add(new TestSummary(invocationUrl));

        Map<String, String> metrics = new HashMap<>();
        metrics.put("key1", "1.0");
        metrics.put("key2", "2.0");

        mReporter.setPostBenchmarkResults(true);
        mReporter.setPostFunctionalResults(false);
        mReporter.setPostTestBenchmarkResults(false);

        mReporter.invocationStarted(mContext);
        mReporter.testRunStarted("run1", 1);
        mReporter.testStarted(new TestIdentifier("package.class", "pass"));
        mReporter.testEnded(new TestIdentifier("package.class", "pass"), metrics);
        mReporter.testRunEnded(0, metrics);
        mReporter.putSummary(summaries);
        mReporter.invocationEnded(0);

        List<BlackboxPostUtil.TestResultsBuilder> requests = mReporter.getRequestBuilders();
        Assert.assertEquals(1, requests.size());

        PostTestResultsRequest request = requests.get(0).build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.setTestSuiteKey(TEST_TAG);
        expected.addTargets(
                Target.newBuilder()
                        .setBranch(BUILD_BRANCH)
                        .setTarget(BUILD_FLAVOR)
                        .setBuildId(BUILD_ID));
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        expected.setUrl(invocationUrl);
        expected.addResults(
                Result.newBuilder()
                        .setMetricKey("key1")
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED)
                        .setUrl(invocationUrl)
                        .setValue(Result.Value.newBuilder().setFloatValue(1.0)));
        expected.addResults(
                Result.newBuilder()
                        .setMetricKey("key2")
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED)
                        .setUrl(invocationUrl)
                        .setValue(Result.Value.newBuilder().setFloatValue(2.0)));
        Assert.assertEquals(expected.build(), request);

        Assert.assertEquals(1, mTestResultCaptures.size());
        Assert.assertTrue(
                Arrays.equals(
                        expected.build().toByteArray(),
                        mTestResultCaptures.get(0).build().toByteArray()));
    }

    @Test
    public void testInvocation_functional() {
        String invocationUrl =
                "http://sponge.corp.google.com/invocation?tab=Test+Cases&show=FAILED&"
                        + "show=INTERNAL_ERROR&id=5714563b-8836-40c1-88ec-10ee679668e5";
        List<TestSummary> summaries = new ArrayList<>();
        summaries.add(new TestSummary(invocationUrl));

        String runUrl =
                "http://sponge.corp.google.com/target?show=FAILED&sortBy=STATUS"
                        + "&id=5714563b-8836-40c1-88ec-10ee679668e5&target=run1";

        Map<String, String> metrics = new HashMap<>();
        metrics.put("key1", "1.0");
        metrics.put("key2", "2.0");

        mReporter.setPostBenchmarkResults(false);
        mReporter.setPostFunctionalResults(true);
        mReporter.setPostTestBenchmarkResults(false);

        mReporter.invocationStarted(mContext);
        mReporter.testRunStarted("run1", 1);
        mReporter.testStarted(new TestIdentifier("package.class", "pass"));
        mReporter.testEnded(new TestIdentifier("package.class", "pass"), metrics);
        mReporter.testRunEnded(0, metrics);
        mReporter.putSummary(summaries);
        mReporter.invocationEnded(0);

        List<BlackboxPostUtil.TestResultsBuilder> requests = mReporter.getRequestBuilders();
        Assert.assertEquals(1, requests.size());

        PostTestResultsRequest request = requests.get(0).build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.setTestSuiteKey(TEST_TAG);
        expected.addTargets(
                Target.newBuilder()
                        .setBranch(BUILD_BRANCH)
                        .setTarget(BUILD_FLAVOR)
                        .setBuildId(BUILD_ID));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#pass")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setUrl(runUrl)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.PASS)));
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        expected.setUrl(invocationUrl);
        Assert.assertEquals(expected.build(), request);

        Assert.assertEquals(1, mTestResultCaptures.size());
        Assert.assertTrue(
                Arrays.equals(
                        expected.build().toByteArray(),
                        mTestResultCaptures.get(0).build().toByteArray()));
    }

    @Test
    public void testInvocation_test_benchmark() {
        String invocationUrl =
                "http://sponge.corp.google.com/invocation?tab=Test+Cases&show=FAILED&"
                        + "show=INTERNAL_ERROR&id=5714563b-8836-40c1-88ec-10ee679668e5";
        List<TestSummary> summaries = new ArrayList<>();
        summaries.add(new TestSummary(invocationUrl));

        String runUrl =
                "http://sponge.corp.google.com/target?show=FAILED&sortBy=STATUS"
                        + "&id=5714563b-8836-40c1-88ec-10ee679668e5&target=run1";

        Map<String, String> metrics = new HashMap<>();
        metrics.put("key1", "1.0");
        metrics.put("key2", "2.0");
        metrics.put("key3", "ignored");

        mReporter.setPostBenchmarkResults(false);
        mReporter.setPostFunctionalResults(false);
        mReporter.setPostTestBenchmarkResults(true);

        mReporter.invocationStarted(mContext);
        mReporter.testRunStarted("run1", 1);
        mReporter.testStarted(new TestIdentifier("package.class", "pass"));
        mReporter.testEnded(new TestIdentifier("package.class", "pass"), metrics);
        mReporter.testRunEnded(0, metrics);
        mReporter.putSummary(summaries);
        mReporter.invocationEnded(0);

        List<BlackboxPostUtil.TestResultsBuilder> requests = mReporter.getRequestBuilders();
        Assert.assertEquals(1, requests.size());

        PostTestResultsRequest request = requests.get(0).build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.setTestSuiteKey(TEST_TAG);
        expected.addTargets(
                Target.newBuilder()
                        .setBranch(BUILD_BRANCH)
                        .setTarget(BUILD_FLAVOR)
                        .setBuildId(BUILD_ID));
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        expected.setUrl(invocationUrl);
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#pass")
                        .setMetricKey("key1")
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED)
                        .setUrl(runUrl)
                        .setValue(Result.Value.newBuilder().setFloatValue(1.0)));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#pass")
                        .setMetricKey("key2")
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED)
                        .setUrl(runUrl)
                        .setValue(Result.Value.newBuilder().setFloatValue(2.0)));
        Assert.assertEquals(expected.build(), request);

        Assert.assertEquals(1, mTestResultCaptures.size());
        Assert.assertTrue(
                Arrays.equals(
                        expected.build().toByteArray(),
                        mTestResultCaptures.get(0).build().toByteArray()));
    }


    @Test
    public void testInvocation_invalid() {
        mReporter.invocationStarted(mContext);
        // testRunStarted not called
        // testStarted not called
        mReporter.testEnded(new TestIdentifier("package.class", "test"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        mReporter.invocationEnded(0);

        List<BlackboxPostUtil.TestResultsBuilder> requests = mReporter.getRequestBuilders();
        Assert.assertEquals(1, requests.size());

        PostTestResultsRequest request = mReporter.getRequestBuilders().get(0).build();
        PostTestResultsRequest expected =
                PostTestResultsRequest.newBuilder()
                        .setTestSuiteKey(TEST_TAG)
                        .addTargets(
                                Target.newBuilder()
                                        .setBranch(BUILD_BRANCH)
                                        .setTarget(BUILD_FLAVOR)
                                        .setBuildId(BUILD_ID))
                        .setHostname(BlackboxPostUtil.getHostName())
                        .setClient(BlackboxPostUtil.getClient())
                        .build();
        Assert.assertEquals(expected, request);

        Assert.assertEquals(1, mTestResultCaptures.size());
        Assert.assertTrue(
                Arrays.equals(
                        expected.toByteArray(), mTestResultCaptures.get(0).build().toByteArray()));
    }

    @Test
    public void testInvocation_failed_post() {
        // mPostOnInvocationFailure defaults to true

        mReporter.invocationStarted(mContext);
        mReporter.testRunStarted("run1", 1);
        mReporter.testStarted(new TestIdentifier("package.class", "pass"));
        mReporter.testEnded(new TestIdentifier("package.class", "pass"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        mReporter.invocationFailed(new RuntimeException());
        mReporter.invocationEnded(0);

        List<BlackboxPostUtil.TestResultsBuilder> requests = mReporter.getRequestBuilders();
        Assert.assertEquals(1, requests.size());
        Assert.assertEquals(1, mTestResultCaptures.size());
    }

    @Test
    public void testInvocation_failed_nopost() {
        mReporter.setPostOnInvocationFailure(false);

        mReporter.invocationStarted(mContext);
        mReporter.testRunStarted("run1", 1);
        mReporter.testStarted(new TestIdentifier("package.class", "pass"));
        mReporter.testEnded(new TestIdentifier("package.class", "pass"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        mReporter.invocationFailed(new RuntimeException());
        mReporter.invocationEnded(0);

        List<BlackboxPostUtil.TestResultsBuilder> requests = mReporter.getRequestBuilders();
        Assert.assertEquals(0, requests.size());
        Assert.assertEquals(0, mTestResultCaptures.size());
    }
}
