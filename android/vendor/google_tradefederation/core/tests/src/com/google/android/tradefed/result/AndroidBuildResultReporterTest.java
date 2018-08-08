// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.api.services.androidbuildinternal.model.TestResult;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link AndroidBuildResultReporter} */
public class AndroidBuildResultReporterTest {
    private static final String BUILD_ID = "88888";
    private static final String BRANCH = "somebranch";
    private static final String TEST_TAG = "sometest";
    private static final String BUILD_FLAVOR = "someflavor";
    private static final String BUILD_TARGET = "build_target";
    private static final String BUILD_ATTEMPT_ID = "attempt_id";
    private static final String BUILD_TYPE = "build_type";
    private static final Long TEST_RESULT_ID = 1l;

    private AndroidBuildResultReporter mResultReporter = null;
    private IBuildInfo mMockBuild = null;
    private IInvocationContext mContext = null;
    private Map<String, String> mBuildAttributes = new HashMap<String, String>();
    private boolean mCreateTestResult = false;
    private boolean mUpdateTestresult = false;
    private boolean mGetTestresult = false;
    private boolean mThrowException = false;
    private TestResult mTestResult = null;

    @Before
    public void setUp() {
        mMockBuild = EasyMock.createMock(IBuildInfo.class);
        EasyMock.expect(mMockBuild.getBuildBranch()).andReturn(BRANCH).anyTimes();
        EasyMock.expect(mMockBuild.getBuildId()).andReturn(BUILD_ID).anyTimes();
        EasyMock.expect(mMockBuild.getTestTag()).andReturn(TEST_TAG).anyTimes();
        EasyMock.expect(mMockBuild.getBuildFlavor()).andReturn(BUILD_FLAVOR).anyTimes();
        mBuildAttributes.put("build_attempt_id", BUILD_ATTEMPT_ID);
        mBuildAttributes.put("build_type", BUILD_TYPE);
        mBuildAttributes.put("build_target", BUILD_TARGET);
        EasyMock.expect(mMockBuild.getBuildAttributes()).andReturn(mBuildAttributes).anyTimes();
        EasyMock.replay(mMockBuild);
        mContext = new InvocationContext();
        mContext.addDeviceBuildInfo("fakeDevice", mMockBuild);
        mContext.setTestTag(TEST_TAG);

        mResultReporter = new AndroidBuildResultReporter(null) {
            @Override
            TestResult doCreateTestResult(IBuildInfo buildInfo, TestResult testResult)
                    throws IOException {
                testResult.setId(AndroidBuildResultReporterTest.TEST_RESULT_ID);
                mCreateTestResult = true;
                if (mThrowException) {
                    throw new IOException();
                }
                Assert.assertEquals(mMockBuild, buildInfo);
                return testResult;
            }

            @Override
            TestResult doUpdateTestResult(IBuildInfo buildInfo, TestResult testResult)
                    throws IOException {
                mUpdateTestresult = true;
                if (mThrowException) {
                    throw new IOException();
                }
                Assert.assertEquals(mMockBuild, buildInfo);
                mTestResult = testResult;
                return testResult;
            }

            @Override
            TestResult getTestResult(IBuildInfo buildInfo, Long id) throws IOException {
                mGetTestresult = true;
                if (mThrowException) {
                    throw new IOException();
                }
                TestResult testResult = new TestResult();
                testResult.setId(id);
                return testResult;
            }
        };

        mCreateTestResult = false;
        mUpdateTestresult = false;
        mGetTestresult = false;
    }

    @Test
    public void testInvocationStarted() {
        mResultReporter.invocationStarted(mContext);

        assertEquals(TEST_RESULT_ID, mResultReporter.getTestResultId());
        assertEquals(TEST_RESULT_ID, mResultReporter.getTestResult().getId());
        assertEquals(AndroidBuildResultReporter.STATUS_IN_PROGRESS,
                mResultReporter.getTestResult().getStatus());
        assertEquals(TEST_TAG, mResultReporter.getTestResult().getTestTag());
        assertTrue(mCreateTestResult);
    }

    @Test
    public void testInvocationStarted_withException() {
        mThrowException = true;
        mResultReporter.invocationStarted(mContext);

        assertEquals(null, mResultReporter.getTestResultId());
        assertEquals(null, mResultReporter.getTestResult());
        assertTrue(mCreateTestResult);
        assertFalse(mResultReporter.getShouldReport());
    }

    @Test
    public void testInvocationStarted_withContext_noTestResultId() {
        mResultReporter.invocationStarted(mContext);

        assertEquals(TEST_RESULT_ID, mResultReporter.getTestResultId());
        assertEquals(TEST_RESULT_ID, mResultReporter.getTestResult().getId());
        assertEquals(AndroidBuildResultReporter.STATUS_IN_PROGRESS,
                mResultReporter.getTestResult().getStatus());
        assertEquals(TEST_TAG, mResultReporter.getTestResult().getTestTag());
        assertTrue(mCreateTestResult);
        assertFalse(mGetTestresult);
        assertEquals(
                String.valueOf(TEST_RESULT_ID),
                mContext.getAttributes().get(AndroidBuildResultReporter.TEST_RESULT_ID).get(0));
    }

    @Test
    public void testInvocationStarted_withContext_withTestResultId() {
        mContext.addInvocationAttribute(AndroidBuildResultReporter.TEST_RESULT_ID, "2");
        mResultReporter.invocationStarted(mContext);

        assertEquals(Long.valueOf(2), mResultReporter.getTestResultId());
        assertEquals(Long.valueOf(2), mResultReporter.getTestResult().getId());
        assertEquals(AndroidBuildResultReporter.STATUS_IN_PROGRESS,
                mResultReporter.getTestResult().getStatus());
        assertEquals(TEST_TAG, mResultReporter.getTestResult().getTestTag());
        assertFalse(mCreateTestResult);
        assertTrue(mGetTestresult);
        assertTrue(mUpdateTestresult);
        assertEquals(
                "2",
                mContext.getAttributes().get(AndroidBuildResultReporter.TEST_RESULT_ID).get(0));
    }

    @Test
    public void testInvocationEnded() {
        mResultReporter.invocationStarted(mContext);
        mResultReporter.invocationEnded(0l);

        assertEquals(TEST_RESULT_ID, mTestResult.getId());
        assertEquals(AndroidBuildResultReporter.STATUS_PASS, mTestResult.getStatus());
        assertEquals("Ran 0 tests.  0 failed.", mTestResult.getSummary());
        assertTrue(mUpdateTestresult);
    }

    @Test
    public void testInvocationEnded_notReport() {
        mResultReporter.invocationStarted(mContext);
        mResultReporter.setShouldReport(false);
        mResultReporter.invocationEnded(0l);
        assertFalse(mUpdateTestresult);
    }

    @Test
    public void testInvocationEnded_withContext_withTestResultId() {
        mContext.addInvocationAttribute(AndroidBuildResultReporter.TEST_RESULT_ID, "2");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.invocationEnded(0l);

        assertEquals(Long.valueOf(2), mTestResult.getId());
    }
}
