// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;

import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.PostTestResultsRequest;
import com.google.wireless.android.testtools.blackbox.proto.PostServiceProto.Target;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link BlackboxResultReporter}.
 */
public class BaseBlackboxResultReporterTest {

    private static final String BUILD_BRANCH = "branch";
    private static final String BUILD_ID = "123456";
    private static final String BUILD_FLAVOR = "build_flavor";
    private static final String TEST_TAG = "test_tag";

    private BaseBlackboxResultReporter mReporter;
    private IBuildInfo mBuildInfo;
    private IInvocationContext mContext;

    @Before
    public void setUp() {
        mBuildInfo = new BuildInfo(BUILD_ID, null);
        mBuildInfo.setBuildBranch(BUILD_BRANCH);
        mBuildInfo.setBuildFlavor(BUILD_FLAVOR);
        mContext = new InvocationContext();
        mContext.setTestTag(TEST_TAG);
        mContext.addDeviceBuildInfo("device", mBuildInfo);

        // empty subclass because the parent class is marked abstract purely for the purpose of
        // no instantiation
        mReporter = new BaseBlackboxResultReporter() {};
    }

    @Test
    public void testInitPostRequest() {
        mReporter.setInvocationContext(mContext);
        mReporter.initPostRequest(TEST_TAG);

        PostTestResultsRequest request = mReporter.getCurrentRequestBuilder().build();
        PostTestResultsRequest expected =
                PostTestResultsRequest.newBuilder()
                        .setTestSuiteKey(TEST_TAG)
                        .setHostname(BlackboxPostUtil.getHostName())
                        .setClient(BlackboxPostUtil.getClient())
                        .build();
        Assert.assertEquals(expected, request);
    }

    @Test
    public void testFinalizePostRequest() {
        mReporter.setInvocationContext(mContext);
        mReporter.initPostRequest(TEST_TAG);
        mReporter.finalizePostRequest();

        Assert.assertEquals(1, mReporter.getRequestBuilders().size());

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

        Assert.assertNull(mReporter.getCurrentRequestBuilder());
    }

    @Test
    public void testGetTargetUrl() {
        String invocationUrl = "http://sponge.corp.google.com/invocation?tab=Test+Cases" +
                "&show=FAILED&show=INTERNAL_ERROR&id=5714563b-8836-40c1-88ec-10ee679668e5";
        String runName = "android.launcher.functional";
        String expected = "http://sponge.corp.google.com/target?show=FAILED&sortBy=STATUS" +
                "&id=5714563b-8836-40c1-88ec-10ee679668e5&target=android.launcher.functional";

        String runUrl = mReporter.getTargetUrl(invocationUrl, runName);
        Assert.assertEquals(expected, runUrl);

        invocationUrl = "https://sponge-qa.corp.google.com/invocation?id=5714563b-8836-40c1-88ec-" +
                "10ee679668e5&tab=Test+Cases&show=FAILED&show=INTERNAL_ERROR";
        expected = "https://sponge-qa.corp.google.com/target?show=FAILED&sortBy=STATUS" +
                "&id=5714563b-8836-40c1-88ec-10ee679668e5&target=android.launcher.functional";

        runUrl = mReporter.getTargetUrl(invocationUrl, runName);
        Assert.assertEquals(expected, runUrl);
    }

    @Test
    public void testGetTargetUrl_matchFailed() {
        String invocationUrl = "bad url";
        String runName = "android.launcher.functional";

        String runUrl = mReporter.getTargetUrl(invocationUrl, runName);

        Assert.assertEquals(invocationUrl, runUrl);
    }

    @Test
    public void testGetTargetUrl_nullRunName() {
        String invocationUrl =
                "http://sponge.corp.google.com/invocation?tab=Test+Cases"
                        + "&show=FAILED&show=INTERNAL_ERROR&id=5714563b-8836-40c1-88ec-10ee679668e5";

        String runUrl = mReporter.getTargetUrl(invocationUrl, null);

        Assert.assertEquals(invocationUrl, runUrl);
    }
}
