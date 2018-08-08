// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil.IRunnableResult;
import com.android.tradefed.util.RunUtil;
import com.google.android.tradefed.result.BlackboxPostUtil.TestResultsBuilder;
import com.google.api.client.http.GenericUrl;
import com.google.protobuf.GeneratedMessage;
import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.PostErrorRequest;
import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.PostIgnoredRequest;
import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.PostTestResultsRequest;
import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.Result;
import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.Target;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link BlackboxPostUtil}.
 */
public class BlackboxPostUtilTest {

    private static final String BASE_URL = "http://baseurl";
    private static final String BRANCH = "branch";
    private static final String PRODUCT = "product";
    private static final String TEST_SUITE = "test_suite";
    private static final String BUILD_ID = "123456";

    @Test
    public void testTestResultsBuilder() {
        TestResultsBuilder builder = new TestResultsBuilder();

        PostTestResultsRequest request = builder.build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        Assert.assertEquals(expected.build(), request);
    }

    @Test
    public void testSetBuildInfo() {
        IBuildInfo buildInfo = new BuildInfo(BUILD_ID, null);
        buildInfo.setBuildBranch(BRANCH);
        buildInfo.setBuildFlavor(PRODUCT);

        TestResultsBuilder builder = new TestResultsBuilder().setBuildInfo(buildInfo);

        PostTestResultsRequest request = builder.build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.addTargets(
                Target.newBuilder().setBranch(BRANCH).setTarget(PRODUCT).setBuildId(BUILD_ID));
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        Assert.assertEquals(expected.build(), request);
    }

    // TODO(b/34614279): Remove this when Blackbox supports posting unbundled
    // products.
    @Test
    public void testSetBuildInfo_unbundled() {
        IBuildInfo buildInfo = new BuildInfo(BUILD_ID, null);
        buildInfo.setBuildBranch(BRANCH);
        buildInfo.setBuildFlavor(PRODUCT);
        buildInfo.addBuildAttribute("device_build_id", "98765");
        buildInfo.addBuildAttribute("device_build_flavor", "device_product");

        TestResultsBuilder builder = new TestResultsBuilder().setBuildInfo(buildInfo);

        PostTestResultsRequest request = builder.build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.addTargets(
                Target.newBuilder()
                        .setBranch(BRANCH)
                        .setTarget(PRODUCT + "-device_product-98765")
                        .setBuildId(BUILD_ID));
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        Assert.assertEquals(expected.build(), request);
    }

    @Test
    public void testSetBranch() {
        TestResultsBuilder builder = new TestResultsBuilder().setBranch(BRANCH);

        PostTestResultsRequest request = builder.build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.addTargets(Target.newBuilder().setBranch(BRANCH));
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        Assert.assertEquals(expected.build(), request);
    }

    @Test
    public void testSetProduct() {
        TestResultsBuilder builder = new TestResultsBuilder().setProduct(PRODUCT);

        PostTestResultsRequest request = builder.build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.addTargets(Target.newBuilder().setTarget(PRODUCT));
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        Assert.assertEquals(expected.build(), request);
    }

    @Test
    public void testSetTestSuite() {
        TestResultsBuilder builder = new TestResultsBuilder().setTestSuite(TEST_SUITE);

        PostTestResultsRequest request = builder.build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.setTestSuiteKey(TEST_SUITE);
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        Assert.assertEquals(expected.build(), request);
    }

    @Test
    public void testSetBuildId() {
        TestResultsBuilder builder = new TestResultsBuilder().setBuildId(BUILD_ID);

        PostTestResultsRequest request = builder.build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.addTargets(Target.newBuilder().setBuildId(BUILD_ID));
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        Assert.assertEquals(expected.build(), request);
    }

    @Test
    public void testAddTestCase() {
        TestResultsBuilder builder =
                new TestResultsBuilder()
                        .addTestCase(
                                new TestIdentifier("package.class", "passed"),
                                TestStatus.PASSED,
                                null)
                        .addTestCase(
                                new TestIdentifier("package.class", "failure"),
                                TestStatus.FAILURE,
                                null)
                        .addTestCase(
                                new TestIdentifier("package.class", "assumption_failure"),
                                TestStatus.ASSUMPTION_FAILURE,
                                null)
                        .addTestCase(
                                new TestIdentifier("package.class", "ignored"),
                                TestStatus.IGNORED,
                                null)
                        .addTestCase(
                                new TestIdentifier("package.class", "incomplete"),
                                TestStatus.INCOMPLETE,
                                null);

        PostTestResultsRequest request = builder.build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#passed")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.PASS)));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#failure")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.FAIL)));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#assumption_failure")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.NONE)));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#ignored")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.NONE)));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#incomplete")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.NONE)));
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        Assert.assertEquals(expected.build(), request);
    }

    @Test
    public void testAddTestCase_null() {
        TestResultsBuilder builder =
                new TestResultsBuilder()
                        .addTestCase(null, TestStatus.PASSED, null)
                        .addTestCase(new TestIdentifier("package.class", "passed"), null, null);

        PostTestResultsRequest request = builder.build();
        PostTestResultsRequest expected =
                PostTestResultsRequest.newBuilder()
                        .setHostname(BlackboxPostUtil.getHostName())
                        .setClient(BlackboxPostUtil.getClient())
                        .build();
        Assert.assertEquals(expected, request);
    }

    @Test
    public void testAddBenchmarkMetrics() {
        Map<String, String> metrics = new HashMap<>();
        metrics.put("key1", "1.0");
        metrics.put("key2", "2.0");
        metrics.put("key3", "ignored");

        TestResultsBuilder builder = new TestResultsBuilder()
                .addBenchmarkMetrics(null, metrics, null)
                .addBenchmarkMetrics(new TestIdentifier("package.class", "test"), metrics, null);

        PostTestResultsRequest request = builder.build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.addResults(
                Result.newBuilder()
                        .setMetricKey("key1")
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(Result.Value.newBuilder().setFloatValue(1.0)));
        expected.addResults(
                Result.newBuilder()
                        .setMetricKey("key2")
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(Result.Value.newBuilder().setFloatValue(2.0)));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#test")
                        .setMetricKey("key1")
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(Result.Value.newBuilder().setFloatValue(1.0)));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#test")
                        .setMetricKey("key2")
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(Result.Value.newBuilder().setFloatValue(2.0)));
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        Assert.assertEquals(expected.build(), request);
    }

    @Test
    public void testAddCoverageResult() {
        TestResultsBuilder builder =
                new TestResultsBuilder()
                        .addCoverageResult("package.class#method", 1, 2, 3, 4, 5, 6, null);

        PostTestResultsRequest request = builder.build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#method")
                        .setType(Result.ResultType.COVERAGE)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setCoverageResult(
                                                Result.Value.CoverageResult.newBuilder()
                                                        .setLinesMissed(1)
                                                        .setLinesTotal(2)
                                                        .setBranchesMissed(3)
                                                        .setBranchesTotal(4)
                                                        .setInstructionsMissed(5)
                                                        .setInstructionsTotal(6))));
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        Assert.assertEquals(expected.build(), request);
    }

    @Test
    public void testUpdateUrl() {
        String url = "url";

        Map<String, String> metrics = new HashMap<>();
        metrics.put("key1", "1.0");

        TestResultsBuilder builder =
                new TestResultsBuilder()
                        .addTestCase(
                                new TestIdentifier("package.class", "testCase1"),
                                TestStatus.PASSED,
                                null)
                        .addBenchmarkMetrics(null, metrics, null);

        builder.updateUrl(url);

        PostTestResultsRequest request = builder.build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#testCase1")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.PASS))
                        .setUrl(url));
        expected.addResults(
                Result.newBuilder()
                        .setMetricKey("key1")
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(Result.Value.newBuilder().setFloatValue(1.0))
                        .setUrl(url));
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        expected.setUrl(url);
        Assert.assertEquals(expected.build(), request);
    }

    @Test
    public void testUpdateUrl_withTestUrlMap() {
        String defaultUrl = "default_url";
        String url1 = "url1";

        Map<String, String> testUrlMap = new HashMap<>();
        testUrlMap.put("package.class#testCase1", url1);

        Map<String, String> metrics = new HashMap<>();
        metrics.put("key1", "1.0");

        TestResultsBuilder builder =
                new TestResultsBuilder()
                        .addTestCase(
                                new TestIdentifier("package.class", "testCase1"),
                                TestStatus.PASSED,
                                null)
                        .addTestCase(
                                new TestIdentifier("package.class", "testCase2"),
                                TestStatus.FAILURE,
                                null)
                        .addBenchmarkMetrics(null, metrics, null);

        builder.updateUrl(defaultUrl, testUrlMap);

        PostTestResultsRequest request = builder.build();
        PostTestResultsRequest.Builder expected = PostTestResultsRequest.newBuilder();
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#testCase1")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.PASS))
                        .setUrl(url1));
        expected.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#testCase2")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.FAIL))
                        .setUrl(defaultUrl));
        expected.addResults(
                Result.newBuilder()
                        .setMetricKey("key1")
                        .setType(Result.ResultType.BENCHMARK)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(Result.Value.newBuilder().setFloatValue(1.0))
                        .setUrl(defaultUrl));
        expected.setHostname(BlackboxPostUtil.getHostName());
        expected.setClient(BlackboxPostUtil.getClient());
        expected.setUrl(defaultUrl);
        Assert.assertEquals(expected.build(), request);
    }

    @Test
    public void testValidateBuilder_valid() {
        IBuildInfo buildInfo = new BuildInfo(BUILD_ID, null);
        buildInfo.setBuildBranch(BRANCH);
        buildInfo.setBuildFlavor(PRODUCT);

        TestResultsBuilder builder = new TestResultsBuilder();
        builder.setBuildInfo(buildInfo);
        builder.setTestSuite(TEST_SUITE);
        builder.addTestCase(new TestIdentifier("package.class", "passed"), TestStatus.PASSED, null);

        Assert.assertNull(builder.validate());
    }

    @Test
    public void testValidateBuilder_no_test_suite() {
        IBuildInfo buildInfo = new BuildInfo(BUILD_ID, null);
        buildInfo.setBuildBranch(BRANCH);
        buildInfo.setBuildFlavor(PRODUCT);

        TestResultsBuilder builder = new TestResultsBuilder();
        builder.setBuildInfo(buildInfo);
        builder.addTestCase(new TestIdentifier("package.class", "passed"), TestStatus.PASSED, null);

        Assert.assertEquals("test suite is not set", builder.validate());
    }

    @Test
    public void testValidateBuilder_no_branch() {
        IBuildInfo buildInfo = new BuildInfo(BUILD_ID, null);
        buildInfo.setBuildFlavor(PRODUCT);

        TestResultsBuilder builder = new TestResultsBuilder();
        builder.setBuildInfo(buildInfo);
        builder.setTestSuite(TEST_SUITE);
        builder.addTestCase(new TestIdentifier("package.class", "passed"), TestStatus.PASSED, null);

        Assert.assertEquals("branch is not set", builder.validate());
    }

    @Test
    public void testValidateBuilder_no_product() {
        IBuildInfo buildInfo = new BuildInfo(BUILD_ID, null);
        buildInfo.setBuildBranch(BRANCH);

        TestResultsBuilder builder = new TestResultsBuilder();
        builder.setBuildInfo(buildInfo);
        builder.setTestSuite(TEST_SUITE);
        builder.addTestCase(new TestIdentifier("package.class", "passed"), TestStatus.PASSED, null);

        Assert.assertEquals("product is not set", builder.validate());
    }

    @Test
    public void testValidateBuilder_invalid_build() {
        IBuildInfo buildInfo = new BuildInfo("P12345", null);
        buildInfo.setBuildBranch(BRANCH);
        buildInfo.setBuildFlavor(PRODUCT);

        TestResultsBuilder builder = new TestResultsBuilder();
        builder.setBuildInfo(buildInfo);
        builder.setTestSuite(TEST_SUITE);
        builder.addTestCase(new TestIdentifier("package.class", "passed"), TestStatus.PASSED, null);

        Assert.assertEquals("build id \"P12345\" is not a digit", builder.validate());
    }

    @Test
    public void testValidateBuilder_no_results() {
        IBuildInfo buildInfo = new BuildInfo(BUILD_ID, null);
        buildInfo.setBuildBranch(BRANCH);
        buildInfo.setBuildFlavor(PRODUCT);

        TestResultsBuilder builder =
                new TestResultsBuilder().setBuildInfo(buildInfo).setTestSuite(TEST_SUITE);

        Assert.assertEquals("no results", builder.validate());
    }

    @Test
    public void testPostTestResults() {
        IBuildInfo buildInfo = new BuildInfo(BUILD_ID, null);
        buildInfo.setBuildBranch(BRANCH);
        buildInfo.setBuildFlavor(PRODUCT);

        TestResultsBuilder builder = new TestResultsBuilder();
        builder.setBuildInfo(buildInfo);
        builder.setTestSuite(TEST_SUITE);
        builder.addTestCase(new TestIdentifier("package.class", "passed"), TestStatus.PASSED, null);

        BlackboxPostUtil postUtil = Mockito.spy(new BlackboxPostUtil(BASE_URL));

        IRunnableResult stubRunnable = new StubRunnable();
        doReturn(stubRunnable)
                .when(postUtil)
                .getRunnable(any(GenericUrl.class), any(GeneratedMessage.class));

        RunUtil runUtil = Mockito.mock(RunUtil.class);
        doReturn(true)
                .when(runUtil)
                .runEscalatingTimedRetry(
                        anyLong(), anyLong(), anyLong(), anyLong(), any(IRunnableResult.class));
        doReturn(runUtil).when(postUtil).getRunUtil();

        Assert.assertTrue(postUtil.postTestResults(builder));

        Mockito.verify(runUtil)
                .runEscalatingTimedRetry(
                        anyLong(), anyLong(), anyLong(), anyLong(), eq(stubRunnable));

        GenericUrl wantUrl = new GenericUrl(BASE_URL + "/post/results/");
        PostTestResultsRequest.Builder wantProto = PostTestResultsRequest.newBuilder();
        wantProto.addTargets(
                Target.newBuilder().setBranch(BRANCH).setTarget(PRODUCT).setBuildId(BUILD_ID));
        wantProto.setTestSuiteKey(TEST_SUITE);
        wantProto.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#passed")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.PASS)));
        wantProto.setHostname(BlackboxPostUtil.getHostName());
        wantProto.setClient(BlackboxPostUtil.getClient());
        wantProto.setShardIndex(0);
        wantProto.setShardCount(1);
        Mockito.verify(postUtil).getRunnable(eq(wantUrl), eq(wantProto.build()));
    }

    @Test
    public void testPostTestResults_shard() {
        IBuildInfo buildInfo = new BuildInfo(BUILD_ID, null);
        buildInfo.setBuildBranch(BRANCH);
        buildInfo.setBuildFlavor(PRODUCT);

        TestResultsBuilder builder = new TestResultsBuilder();
        builder.setBuildInfo(buildInfo);
        builder.setTestSuite(TEST_SUITE);
        builder.addTestCase(
                new TestIdentifier("package.class", "passed1"), TestStatus.PASSED, null);
        builder.addTestCase(
                new TestIdentifier("package.class", "passed2"), TestStatus.PASSED, null);
        builder.addTestCase(
                new TestIdentifier("package.class", "passed3"), TestStatus.PASSED, null);
        builder.addTestCase(
                new TestIdentifier("package.class", "passed4"), TestStatus.PASSED, null);

        BlackboxPostUtil postUtil = Mockito.spy(new BlackboxPostUtil(BASE_URL));
        postUtil.setMaxResults(2);

        IRunnableResult stubRunnable = new StubRunnable();
        doReturn(stubRunnable)
                .when(postUtil)
                .getRunnable(any(GenericUrl.class), any(GeneratedMessage.class));

        RunUtil runUtil = Mockito.mock(RunUtil.class);
        doReturn(true)
                .when(runUtil)
                .runEscalatingTimedRetry(
                        anyLong(), anyLong(), anyLong(), anyLong(), any(IRunnableResult.class));
        doReturn(runUtil).when(postUtil).getRunUtil();

        Assert.assertTrue(postUtil.postTestResults(builder));

        Mockito.verify(runUtil, times(2))
                .runEscalatingTimedRetry(
                        anyLong(), anyLong(), anyLong(), anyLong(), eq(stubRunnable));

        GenericUrl wantUrl = new GenericUrl(BASE_URL + "/post/results/");
        PostTestResultsRequest.Builder wantProto = PostTestResultsRequest.newBuilder();
        wantProto.addTargets(
                Target.newBuilder().setBranch(BRANCH).setTarget(PRODUCT).setBuildId(BUILD_ID));
        wantProto.setTestSuiteKey(TEST_SUITE);
        wantProto.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#passed1")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.PASS)));
        wantProto.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#passed2")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.PASS)));
        wantProto.setHostname(BlackboxPostUtil.getHostName());
        wantProto.setClient(BlackboxPostUtil.getClient());
        wantProto.setShardIndex(0);
        wantProto.setShardCount(2);
        Mockito.verify(postUtil).getRunnable(eq(wantUrl), eq(wantProto.build()));

        wantProto.clearResults();
        wantProto.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#passed3")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.PASS)));
        wantProto.addResults(
                Result.newBuilder()
                        .setTestCaseKey("package.class#passed4")
                        .setType(Result.ResultType.FUNCTIONAL)
                        .setStatus(Result.Status.FINISHED)
                        .setValue(
                                Result.Value.newBuilder()
                                        .setFunctionalResult(Result.Value.FunctionalResult.PASS)));
        wantProto.setShardIndex(1);
        wantProto.setShardCount(2);
        Mockito.verify(postUtil).getRunnable(eq(wantUrl), eq(wantProto.build()));
    }

    @Test
    public void testPostTestResults_invalid() {
        TestResultsBuilder builder = new TestResultsBuilder();

        BlackboxPostUtil postUtil = Mockito.spy(new BlackboxPostUtil(BASE_URL));
        doReturn(true).when(postUtil).postIgnored(any(TestResultsBuilder.class), any(String.class));

        RunUtil runUtil = Mockito.mock(RunUtil.class);
        doReturn(runUtil).when(postUtil).getRunUtil();

        Assert.assertFalse(postUtil.postTestResults(builder));

        Mockito.verifyZeroInteractions(runUtil);
        Mockito.verify(postUtil)
                .postIgnored(eq(builder), eq(builder.validate().replaceAll("\"null\" ", "")));
    }

    @Test
    public void testPostIgnored() {
        TestResultsBuilder builder = new TestResultsBuilder();
        builder.setTestSuite("test_suite");
        builder.updateUrl("url");

        BlackboxPostUtil postUtil = Mockito.spy(new BlackboxPostUtil(BASE_URL));

        IRunnableResult stubRunnable = new StubRunnable();
        doReturn(stubRunnable)
                .when(postUtil)
                .getRunnable(any(GenericUrl.class), any(GeneratedMessage.class));

        RunUtil runUtil = Mockito.mock(RunUtil.class);
        doReturn(CommandStatus.SUCCESS)
                .when(runUtil)
                .runTimed(anyLong(), any(IRunnableResult.class), anyBoolean());
        doReturn(runUtil).when(postUtil).getRunUtil();

        Assert.assertTrue(postUtil.postIgnored(builder, "reason"));

        Mockito.verify(runUtil).runTimed(anyLong(), eq(stubRunnable), eq(false));

        GenericUrl wantUrl = new GenericUrl(BASE_URL + "/post/ignore-info/");
        PostIgnoredRequest.Builder wantProto =
                PostIgnoredRequest.newBuilder()
                        .setHostname(BlackboxPostUtil.getHostName())
                        .setClient(BlackboxPostUtil.getClient())
                        .setUrl("url")
                        .setResultCount(0)
                        .setTestSuite("test_suite")
                        .setReason("reason");
        Mockito.verify(postUtil).getRunnable(eq(wantUrl), eq(wantProto.build()));
    }

    @Test
    public void testPostError() {
        TestResultsBuilder builder = new TestResultsBuilder();
        builder.setTestSuite("test_suite");
        builder.updateUrl("url");

        BlackboxPostUtil postUtil = Mockito.spy(new BlackboxPostUtil(BASE_URL));

        IRunnableResult stubRunnable = new StubRunnable();
        doReturn(stubRunnable)
                .when(postUtil)
                .getRunnable(any(GenericUrl.class), any(GeneratedMessage.class));

        RunUtil runUtil = Mockito.mock(RunUtil.class);
        doReturn(CommandStatus.SUCCESS)
                .when(runUtil)
                .runTimed(anyLong(), any(IRunnableResult.class), anyBoolean());
        doReturn(runUtil).when(postUtil).getRunUtil();

        Assert.assertTrue(postUtil.postError(builder, "reason"));

        Mockito.verify(runUtil).runTimed(anyLong(), eq(stubRunnable), eq(false));

        GenericUrl wantUrl = new GenericUrl(BASE_URL + "/post/error-info/");
        PostErrorRequest.Builder wantProto =
                PostErrorRequest.newBuilder()
                        .setHostname(BlackboxPostUtil.getHostName())
                        .setClient(BlackboxPostUtil.getClient())
                        .setUrl("url")
                        .setTestSuite("test_suite")
                        .setResultCount(0)
                        .setReason("reason");
        Mockito.verify(postUtil).getRunnable(eq(wantUrl), eq(wantProto.build()));
    }

    private class StubRunnable implements IRunnableResult {
        @Override
        public boolean run() throws Exception {
            return true;
        }

        @Override
        public void cancel() {
            // Ignore
        }
    }
}
