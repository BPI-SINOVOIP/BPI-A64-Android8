// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link BlackboxResultReporter}.
 */
public class BlackboxResultReporterTest extends CommonBlackboxResultReporterTest {

    private static final String BUILD_BRANCH = "branch";
    private static final String BUILD_ID = "123456";
    private static final String BUILD_FLAVOR = "build_flavor";

    private BlackboxResultReporter mReporter;
    private IBuildInfo mBuildInfo;
    private IInvocationContext mContext;

    @Before
    public void prepareBlackboxResultReporterTest() {
        mBuildInfo = new BuildInfo(BUILD_ID, null);
        mBuildInfo.setBuildBranch(BUILD_BRANCH);
        mBuildInfo.setBuildFlavor(BUILD_FLAVOR);
        mContext = new InvocationContext();
        mContext.setTestTag(TEST_TAG);
        mContext.addDeviceBuildInfo("device", mBuildInfo);

        mReporter = new BlackboxResultReporter() {
            @Override
            void postRequest(BlackboxPostUtil.TestResultsBuilder postRequest) {
                addTestResult(postRequest);
            }
        };
        setInvocationContext(mContext);
        setResultReporter(mReporter);
    }

    @Test
    public void testInvocation_testruns() {
        mReporter.setTestSuiteFromRunName(true);

        mReporter.invocationStarted(mContext);
        mReporter.testRunStarted("run1", 1);
        mReporter.testStarted(new TestIdentifier("package.class", "test"));
        mReporter.testEnded(new TestIdentifier("package.class", "test"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        Assert.assertEquals(1, mReporter.getRequestBuilders().size());
        mReporter.testRunStarted("run2", 1);
        mReporter.testStarted(new TestIdentifier("package.class", "test"));
        mReporter.testEnded(new TestIdentifier("package.class", "test"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        Assert.assertEquals(2, mReporter.getRequestBuilders().size());
        mReporter.invocationEnded(0);

        List<BlackboxPostUtil.TestResultsBuilder> requests = mReporter.getRequestBuilders();
        Assert.assertEquals(2, requests.size());
        Assert.assertEquals("run1", requests.get(0).build().getTestSuiteKey());
        Assert.assertEquals("run2", requests.get(1).build().getTestSuiteKey());

        Assert.assertEquals(2, getCapturedResults().size());
    }

    @Test
    public void testInvocation_invalid_testruns() {
        mReporter.setTestSuiteFromRunName(true);

        mReporter.invocationStarted(mContext);
        // testRunStarted not called
        // testStarted not called
        mReporter.testEnded(new TestIdentifier("package.class", "test"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        mReporter.invocationEnded(0);

        List<BlackboxPostUtil.TestResultsBuilder> requests = mReporter.getRequestBuilders();
        Assert.assertEquals(0, requests.size());
        Assert.assertEquals(0, getCapturedResults().size());
    }
}
