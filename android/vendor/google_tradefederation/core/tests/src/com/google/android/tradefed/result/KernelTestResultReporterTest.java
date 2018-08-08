// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.KernelBuildInfo;
import com.android.tradefed.build.KernelDeviceBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.net.IHttpHelper;
import com.android.tradefed.util.net.IHttpHelper.DataSizeException;

import com.google.android.tradefed.result.KernelTestResultReporter.KernelTestStatus;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;

import java.io.IOException;

/** Unit tests for {@link KernelTestResultReporter}. */
public class KernelTestResultReporterTest extends TestCase {
    private static final String DEVICE_BUILD_ID = "bid";
    private static final String KERNEL_BUILD_ID = "sha1";
    private static final String BUILD_BRANCH = "branch";
    private static final String BUILD_FLAVOR = "buildflavor";
    private static final String SHORT_SHA1 = "short_sha1";
    private static final String TEST_TAG = "testtag";
    private static final String HOST_NAME = "http://foo";
    private static final String SUMMARY_URL = "http://sponge";
    private static final String URL =
            String.format("%s/%s/%s/", HOST_NAME, BUILD_BRANCH, KERNEL_BUILD_ID);
    private static final String PARAMETERS = "parameters";

    IHttpHelper mMockHttpHelper;
    KernelTestResultReporter mResultReporter;
    private IInvocationContext mContext;
    KernelDeviceBuildInfo mBuildInfo;
    MultiMap<String, String> mParams;

    /** {@inheritDoc} */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockHttpHelper = EasyMock.createMock(IHttpHelper.class);

        mBuildInfo =
                new KernelDeviceBuildInfo(
                        String.format("%s_%s", KERNEL_BUILD_ID, DEVICE_BUILD_ID), "");
        mBuildInfo.setTestTag(TEST_TAG);
        mBuildInfo.setDeviceBuild(new DeviceBuildInfo(DEVICE_BUILD_ID, ""));
        mBuildInfo.setKernelBuild(new KernelBuildInfo(KERNEL_BUILD_ID, SHORT_SHA1, 0, ""));
        mBuildInfo.setBuildFlavor(BUILD_FLAVOR);
        mBuildInfo.setBuildBranch(BUILD_BRANCH);
        mBuildInfo.setDeviceImageFile(null, DEVICE_BUILD_ID);

        mParams = new MultiMap<String, String>();
        mParams.put("test-tag", TEST_TAG);
        mParams.put("build-flavor", BUILD_FLAVOR);
        mParams.put("build-id", DEVICE_BUILD_ID);

        mResultReporter =
                new KernelTestResultReporter() {
                    @Override
                    IHttpHelper getHttpHelper() {
                        return mMockHttpHelper;
                    }
                };
        mResultReporter.setHostName(HOST_NAME);
        mResultReporter.setBuildInfo(mBuildInfo);
        mContext = new InvocationContext();
        mContext.addDeviceBuildInfo("fakeDevice", mBuildInfo);
    }

    /**
     * Test that {@link KernelTestResultReporter#invocationStarted(IInvocationContext)} sets the
     * correct status.
     */
    public void testInvocationStarted() throws IOException, DataSizeException {
        mParams.put("test-status", KernelTestStatus.TESTING.toString());

        Capture<MultiMap<String, String>> params = new Capture<MultiMap<String, String>>();
        EasyMock.expect(mMockHttpHelper.buildParameters(EasyMock.capture(params)))
                .andReturn(PARAMETERS);
        EasyMock.expect(mMockHttpHelper.doPostWithRetry(URL, PARAMETERS)).andReturn("");

        EasyMock.replay(mMockHttpHelper);

        mResultReporter.invocationStarted(mContext);
        verifyParameters(mParams, params.getValue());

        EasyMock.verify(mMockHttpHelper);
    }

    /**
     * Test that {@link KernelTestResultReporter#invocationEnded(long)} sets the correct status when
     * the invocation succeeds.
     */
    public void testInvocationEnded() throws IOException, DataSizeException {
        mParams.put("test-status", KernelTestStatus.PASSED.toString());
        mParams.put("test-url", SUMMARY_URL);
        mResultReporter.setSummaryUrl(SUMMARY_URL);

        Capture<MultiMap<String, String>> params = new Capture<MultiMap<String, String>>();
        EasyMock.expect(mMockHttpHelper.buildParameters(EasyMock.capture(params)))
                .andReturn(PARAMETERS);
        EasyMock.expect(mMockHttpHelper.doPostWithRetry(URL, PARAMETERS)).andReturn("");

        EasyMock.replay(mMockHttpHelper);

        mResultReporter.invocationEnded(0);
        verifyParameters(mParams, params.getValue());

        EasyMock.verify(mMockHttpHelper);
    }

    /**
     * Test that {@link KernelTestResultReporter#invocationEnded(long)} sets the correct status when
     * the invocation fails.
     */
    public void testInvocationEnded_failed() throws IOException, DataSizeException {
        mParams.put("test-status", KernelTestStatus.TOOL_ERROR.toString());
        mParams.put("test-url", SUMMARY_URL);
        mResultReporter.setSummaryUrl(SUMMARY_URL);

        Capture<MultiMap<String, String>> params = new Capture<MultiMap<String, String>>();
        EasyMock.expect(mMockHttpHelper.buildParameters(EasyMock.capture(params)))
                .andReturn(PARAMETERS);
        EasyMock.expect(mMockHttpHelper.doPostWithRetry(URL, PARAMETERS)).andReturn("");

        EasyMock.replay(mMockHttpHelper);

        mResultReporter.invocationFailed(new Exception());
        mResultReporter.invocationEnded(0);
        verifyParameters(mParams, params.getValue());

        EasyMock.verify(mMockHttpHelper);
    }

    /**
     * Test that {@link KernelTestResultReporter#invocationEnded(long)} sets the correct status when
     * the invocation ends in a build failure.
     */
    public void testInvocationEnded_builderror() throws IOException, DataSizeException {
        mParams.put("test-status", KernelTestStatus.BUILD_ERROR.toString());
        mParams.put("test-url", SUMMARY_URL);
        mResultReporter.setSummaryUrl(SUMMARY_URL);

        Capture<MultiMap<String, String>> params = new Capture<MultiMap<String, String>>();
        EasyMock.expect(mMockHttpHelper.buildParameters(EasyMock.capture(params)))
                .andReturn(PARAMETERS);
        EasyMock.expect(mMockHttpHelper.doPostWithRetry(URL, PARAMETERS)).andReturn("");

        EasyMock.replay(mMockHttpHelper);
        mResultReporter.invocationFailed(
                new BuildError(
                        "",
                        new DeviceDescriptor(
                                "SERIAL",
                                false,
                                DeviceAllocationState.Available,
                                "unknown",
                                "unknown",
                                "unknown",
                                "unknown",
                                "unknown")));
        mResultReporter.invocationEnded(0);
        verifyParameters(mParams, params.getValue());

        EasyMock.verify(mMockHttpHelper);
    }

    /**
     * Verify that the parameters are the same by checking that the key set are value and then each
     * key value is equal.
     */
    private void verifyParameters(
            MultiMap<String, String> expected, MultiMap<String, String> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (String key : expected.keySet()) {
            assertEquals(expected.get(key), actual.get(key));
        }
    }
}
