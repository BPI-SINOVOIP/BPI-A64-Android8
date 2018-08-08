// Copyright 2014 Google Inc.  All Rights Reserved.
package com.google.android.tf_workbuf;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InvocationStatus;
import com.android.tradefed.testtype.FakeTest;
import com.android.tradefed.testtype.IRemoteTest;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;

/**
 * Unit tests for {@link BuildAPIResultReporter}
 */
public class BuildAPIResultReporterTest extends TestCase {
    private BuildAPIResultReporter mReporter = null;
    private IBuildAPIHelper mMockHelper = null;
    private IBuildInfo mBuildInfo = null;
    private IInvocationContext mContext = null;

    private static final String STOCK_ID = "12345";
    private static final String STOCK_TAG = "test-tag";
    private static final String STOCK_TARGET = "test-target";
    private static final String STOCK_ATTEMPT_ID = "1";

    private static final String TEST_RUN_NAME = "test_run";
    private static final long TEST_ELAPSED_TIME = 10000L;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mBuildInfo = new BuildInfo(STOCK_ID, STOCK_TARGET);
        mBuildInfo.addBuildAttribute("attemptId", STOCK_ATTEMPT_ID);
        mContext = new InvocationContext();
        mContext.addDeviceBuildInfo("fakeDevice", mBuildInfo);
        mContext.setTestTag(STOCK_TAG);

        mMockHelper = EasyMock.createStrictMock(IBuildAPIHelper.class);
        mReporter = new BuildAPIResultReporter(mMockHelper);
    }

    private void runInvocation(ITestInvocationListener l, String testRunPat,
            String invFailureString) throws Exception {
        final IRemoteTest test = new FakeTest();
        final OptionSetter option = new OptionSetter(test);
        option.setOptionValue("run", TEST_RUN_NAME, testRunPat);

        l.invocationStarted(mContext);
        test.run(l);
        if (invFailureString != null) {
            l.invocationFailed(new RuntimeException(invFailureString));
        }
        l.invocationEnded(TEST_ELAPSED_TIME);
    }

    /**
     * Make sure that we report the right results when the invocation succeeds and all tests pass.
     */
    public void testPassingTests() throws Exception {
        final Capture<InvocationStatus> captureStatus = new Capture<>();
        final String expTestDesc = "Ran 5 tests.  0 failed.";

        mMockHelper.postTestResults(EasyMock.eq(STOCK_ID), EasyMock.eq(STOCK_TARGET),
                EasyMock.eq(STOCK_ATTEMPT_ID), EasyMock.eq(true), EasyMock.eq(expTestDesc),
                EasyMock.capture(captureStatus));
        EasyMock.expectLastCall().andReturn(null);

        EasyMock.replay(mMockHelper);
        runInvocation(mReporter, "P5", null);
        EasyMock.verify(mMockHelper);

        assertTrue(captureStatus.hasCaptured());
        final InvocationStatus status = captureStatus.getValue();

        assertEquals(InvocationStatus.SUCCESS, status);
        assertNull(status.getThrowable());
    }

    /**
     * Make sure that we report the right results when the invocation succeeds but some tests fail.
     */
    public void testFailingTests() throws Exception {
        final Capture<InvocationStatus> captureStatus = new Capture<>();

        final String expTestDesc = "Ran 5 tests.  1 failed.";

        mMockHelper.postTestResults(EasyMock.eq(STOCK_ID), EasyMock.eq(STOCK_TARGET),
                EasyMock.eq(STOCK_ATTEMPT_ID), EasyMock.eq(false), EasyMock.eq(expTestDesc),
                EasyMock.capture(captureStatus));
        EasyMock.expectLastCall().andReturn(null);

        EasyMock.replay(mMockHelper);
        runInvocation(mReporter, "P2FP2", null);
        EasyMock.verify(mMockHelper);

        assertTrue(captureStatus.hasCaptured());
        final InvocationStatus status = captureStatus.getValue();

        assertEquals(InvocationStatus.SUCCESS, status);
        assertNull(status.getThrowable());
    }

    /**
     * Make sure that we report the right results when the invocation fails
     */
    public void testFailingInvocation() throws Exception {
        final Capture<InvocationStatus> captureStatus = new Capture<>();

        final String expTestDesc = "Ran 4 tests.  1 failed.";
        final String invFailureMessage = "Ran out of peanut butter :o(";

        mMockHelper.postTestResults(EasyMock.eq(STOCK_ID), EasyMock.eq(STOCK_TARGET),
                EasyMock.eq(STOCK_ATTEMPT_ID), EasyMock.eq(false), EasyMock.eq(expTestDesc),
                EasyMock.capture(captureStatus));
        EasyMock.expectLastCall().andReturn(null);

        EasyMock.replay(mMockHelper);
        runInvocation(mReporter, "P2FP", invFailureMessage);
        EasyMock.verify(mMockHelper);

        assertTrue(captureStatus.hasCaptured());
        final InvocationStatus status = captureStatus.getValue();

        assertEquals(InvocationStatus.FAILED, status);
        assertNotNull(status.getThrowable());
        assertEquals(invFailureMessage, status.getThrowable().getMessage());
    }
}
