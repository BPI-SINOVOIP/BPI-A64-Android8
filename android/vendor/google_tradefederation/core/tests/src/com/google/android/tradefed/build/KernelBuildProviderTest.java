// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.IKernelBuildInfo;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.net.IHttpHelper;
import com.android.tradefed.util.net.IHttpHelper.DataSizeException;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;

import java.io.File;
import java.io.IOException;

/**
 * Unit tests for {@link KernelBuildProvider}.
 */
public class KernelBuildProviderTest extends TestCase {
    private final static String SHA1 = "0f99f2a06508af6b739381ed79eb8a1a06667c4a";
    private final static String SHORT_SHA1 = "0f99f2a";
    private final static String KERNEL_BRANCH = "android-omap-tuna-3.0-master";
    private final static String BUILD_FLAVOR = "mysid-userdebug";
    private final static int COMMIT_TIME = 1327706105;
    private final static String DEVICE_BUILD_ID = "1";
    private final static String TEST_TAG = "test_tag";
    private final static String HOST_NAME = "http://foo";
    private final static String GET_URL = "http://foo/?params";

    private final static String KERNEL_PATH = String.format("%s/%s/kernel", KERNEL_BRANCH, SHA1);

    private final static String VALID_RESPONSE = String.format(
            "{\"files\": [\"kernel\", \"vmlinux\", \"System.map\"], "
            + "\"sha1\": \"%s\", "
            + "\"build-flavor\": \"%s\", "
            + "\"commit-time\": %d, "
            + "\"short-sha1\": \"%s\", "
            + "\"url\": \"http://android-build/kernelbuilds/%s/%s\", "
            + "\"build-id\": \"%s\", "
            + "\"branch\": \"%s\", "
            + "\"path\": \"%s/%s\", "
            + "\"test-tag\": \"%s\"}",
            SHA1, BUILD_FLAVOR, COMMIT_TIME, SHORT_SHA1, KERNEL_BRANCH, SHA1, DEVICE_BUILD_ID,
            KERNEL_BRANCH, KERNEL_BRANCH, SHA1, TEST_TAG);


    IHttpHelper mMockHttpHelper;
    IFileDownloader mMockFileDownloader;
    KernelBuildProvider mBuildProvider;
    File mKernelFile;
    MultiMap<String, String> mExpectedParams;
    Capture<String> mUrlCapture = new Capture<String>();
    Capture<MultiMap<String, String>> mParamsCapture = new Capture<MultiMap<String, String>>();
    String mExpectedUrl = String.format("%s/%s/", HOST_NAME, KERNEL_BRANCH);

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockHttpHelper = EasyMock.createMock(IHttpHelper.class);
        mMockFileDownloader = EasyMock.createMock(IFileDownloader.class);
        mBuildProvider = new KernelBuildProvider() {
            @Override
            IHttpHelper createHttpHelper() {
                return mMockHttpHelper;
            }

            @Override
            IFileDownloader createDownloader() {
                return mMockFileDownloader;
            }

            @Override
            void markBuildAsTesting(IKernelBuildInfo info) {
            }
        };
        mBuildProvider.setKernelBranch(KERNEL_BRANCH);
        mBuildProvider.setTestTag(TEST_TAG);
        mBuildProvider.setBuildFlavor(BUILD_FLAVOR);
        mBuildProvider.setBuildId(DEVICE_BUILD_ID);
        mBuildProvider.setKernelHostName(HOST_NAME);

        mKernelFile = FileUtil.createTempFile("boot", ".img");

        mExpectedParams = new MultiMap<String, String>();
        mExpectedParams.put("test-tag", TEST_TAG);
        mExpectedParams.put("build-flavor", BUILD_FLAVOR);
        mExpectedParams.put("build-id", DEVICE_BUILD_ID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        FileUtil.deleteFile(mKernelFile);
    }

    /**
     * Test {@link KernelBuildProvider#getBuild()} under normal conditions.
     */
    public void testGetBuild() throws BuildRetrievalError, IOException, DataSizeException {
        setGetBuildExpectations();

        EasyMock.replay(mMockHttpHelper, mMockFileDownloader);

        IKernelBuildInfo buildInfo = (IKernelBuildInfo) mBuildProvider.getBuild();
        assertEquals(mExpectedUrl, mUrlCapture.getValue());
        verifyParameters(mExpectedParams, mParamsCapture.getValue());
        verifyBuildInfo(buildInfo);

        EasyMock.verify(mMockHttpHelper, mMockFileDownloader);
    }

    /**
     * Test {@link KernelBuildProvider#getBuild()} if there was an error downloading a build.
     */
    public void testGetBuild_buildretrievalerror() throws IOException, DataSizeException {
        EasyMock.expect(mMockHttpHelper.buildUrl(EasyMock.capture(mUrlCapture),
                EasyMock.capture(mParamsCapture))).andReturn(GET_URL);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry(GET_URL)).andThrow(new IOException());

        EasyMock.replay(mMockHttpHelper);

        try {
            mBuildProvider.getBuild();
            fail("Expected BuildRetrievalError");
        } catch (BuildRetrievalError e) {
            // expected
        }
        assertEquals(mExpectedUrl, mUrlCapture.getValue());
        verifyParameters(mExpectedParams, mParamsCapture.getValue());

        EasyMock.verify(mMockHttpHelper);
    }

    /**
     * Test {@link KernelBuildProvider#getBuild()} if a kernel build id is given.
     */
    public void testGetBuild_kernelbuildid() throws BuildRetrievalError, IOException,
            DataSizeException {
        mBuildProvider.setKernelBuildId(SHA1);
        mExpectedUrl = String.format("%s/%s/%s/", HOST_NAME, KERNEL_BRANCH, SHA1);

        setGetBuildExpectations();

        EasyMock.replay(mMockHttpHelper, mMockFileDownloader);

        IKernelBuildInfo buildInfo = (IKernelBuildInfo) mBuildProvider.getBuild();
        assertEquals(mExpectedUrl, mUrlCapture.getValue());
        verifyParameters(mExpectedParams, mParamsCapture.getValue());
        verifyBuildInfo(buildInfo);

        EasyMock.verify(mMockHttpHelper, mMockFileDownloader);
    }

    /**
     * Test {@link KernelBuildProvider#getBuild()} if a minimum kernel build id is given.
     */
    public void testGetBuild_minkernelbuildid() throws BuildRetrievalError, IOException,
            DataSizeException {
        mBuildProvider.setMinKernelBuildId(SHA1);
        mExpectedParams.put("min-kernel-build-id", SHA1);

        setGetBuildExpectations();

        EasyMock.replay(mMockHttpHelper, mMockFileDownloader);

        IKernelBuildInfo buildInfo = (IKernelBuildInfo) mBuildProvider.getBuild();
        assertEquals(mExpectedUrl, mUrlCapture.getValue());
        verifyParameters(mExpectedParams, mParamsCapture.getValue());
        verifyBuildInfo(buildInfo);

        EasyMock.verify(mMockHttpHelper, mMockFileDownloader);
    }

    /**
     * Test {@link KernelBuildProvider#getBuild()} if there are no builds to test
     */
    public void testGetBuild_nobuild() throws BuildRetrievalError, IOException,
            DataSizeException {
        EasyMock.expect(mMockHttpHelper.buildUrl(EasyMock.capture(mUrlCapture),
                EasyMock.capture(mParamsCapture))).andReturn(GET_URL);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry(GET_URL)).andReturn(
                VALID_RESPONSE.replace("sha1", "no-sha1"));

        EasyMock.replay(mMockHttpHelper);

        IKernelBuildInfo buildInfo = (IKernelBuildInfo) mBuildProvider.getBuild();
        assertEquals(mExpectedUrl, mUrlCapture.getValue());
        verifyParameters(mExpectedParams, mParamsCapture.getValue());
        assertNull(buildInfo);

        EasyMock.verify(mMockHttpHelper);
    }

    /**
     * Test {@link KernelBuildProvider#buildNotTested(IBuildInfo)}.
     */
    public void testBuildNotTested() throws IOException, DataSizeException {
        mExpectedUrl = String.format("%s/%s/%s/", HOST_NAME, KERNEL_BRANCH, SHA1);

        IKernelBuildInfo buildInfo = EasyMock.createMock(IKernelBuildInfo.class);
        EasyMock.expect(buildInfo.getSha1()).andReturn(SHA1);
        EasyMock.expect(buildInfo.getTestTag()).andReturn(TEST_TAG);
        EasyMock.expect(buildInfo.getBuildFlavor()).andReturn(BUILD_FLAVOR);
        EasyMock.expect(mMockHttpHelper.buildParameters(EasyMock.capture(mParamsCapture))
                ).andReturn("parameters");
        EasyMock.expect(mMockHttpHelper.doPostWithRetry(mExpectedUrl, "parameters")).andReturn("");

        mExpectedParams.put("reset-test", "1");

        EasyMock.replay(buildInfo, mMockHttpHelper);

        mBuildProvider.buildNotTested(buildInfo);
        verifyParameters(mExpectedParams, mParamsCapture.getValue());

        EasyMock.verify(buildInfo, mMockHttpHelper);
    }

    /**
     * Test {@link KernelBuildProvider#cleanUp(IBuildInfo)}.
     */
    public void testCleanUp() {
        IBuildInfo buildInfo = EasyMock.createMock(IBuildInfo.class);
        buildInfo.cleanUp();
        EasyMock.replay(buildInfo);
        mBuildProvider.cleanUp(buildInfo);
        EasyMock.verify(buildInfo);
    }

    /**
     * Set expectations for {@link KernelBuildProvider#getBuild()}.
     */
    private void setGetBuildExpectations() throws IOException, DataSizeException,
            BuildRetrievalError {
        EasyMock.expect(mMockHttpHelper.buildUrl(EasyMock.capture(mUrlCapture),
                EasyMock.capture(mParamsCapture))).andReturn(GET_URL);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry(GET_URL)).andReturn(VALID_RESPONSE);
        EasyMock.expect(mMockFileDownloader.downloadFile(KERNEL_PATH)).andReturn(mKernelFile);
    }

    /**
     * Verify that the parameters are the same by checking that the key set are value and then each
     * key value is equal.
     */
    private void verifyParameters(MultiMap<String, String> expected,
            MultiMap<String, String> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (String key : expected.keySet()) {
            assertEquals(expected.get(key), actual.get(key));
        }
    }

    /**
     * Verify that the {@link IKernelBuildInfo} returned by {@link KernelBuildProvider#getBuild()}
     * matches expectations.
     */
    private void verifyBuildInfo(IKernelBuildInfo buildInfo) {
        assertNotNull(buildInfo);
        assertEquals(SHA1, buildInfo.getBuildId());
        assertEquals(SHA1, buildInfo.getSha1());
        assertEquals(SHORT_SHA1, buildInfo.getShortSha1());
        assertEquals(COMMIT_TIME, buildInfo.getCommitTime());
        assertEquals(SHA1, buildInfo.getKernelVersion());
        assertEquals(mKernelFile, buildInfo.getKernelFile());
    }
}
