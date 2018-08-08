// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.IKernelBuildInfo;
import com.android.tradefed.build.KernelDeviceBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;
import com.android.tradefed.util.net.IHttpHelper;
import com.android.tradefed.util.net.IHttpHelper.DataSizeException;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

/**
 * Unit tests for {@link KernelDeviceLaunchControlProvider}.
 */
public class KernelDeviceLaunchControlProviderTest extends TestCase {
    private static final String ANDROID_INFO_FILE_NAME = "android-info.txt";
    private static final int DEVICE_BUILD_ID = 4;
    private static final String TAG = "tag";
    private static final String DEVICE_IMAGE_PATH = "image.zip";
    private static final String DEVICE_TEST_PATH = "test.zip";
    private static final String DEVICE_BUILD_FLAVOR = "device-flavor";
    private static final String DEVICE_BRANCH = "master";
    private final static String SHA1 = "0f99f2a06508af6b739381ed79eb8a1a06667c4a";
    private final static String SHORT_SHA1 = "0f99f2a";
    private final static String KERNEL_BRANCH = "android-omap-tuna-3.0-master";
    private final static int COMMIT_TIME = 1327706105;
    private final static String TEST_TAG = "test_tag";

    private File mMockFile;

    private static final String DEVICE_QUERY_RESPONSE = String.format(
            "bid:%d\n%s:%s\n%s:%s\n",
            DEVICE_BUILD_ID,
            BuildAttributeKey.DEVICE_IMAGE.getRemoteValue(), DEVICE_IMAGE_PATH,
            BuildAttributeKey.TESTS_ZIP.getRemoteValue(), DEVICE_TEST_PATH);
    private static final String NO_DEVICE_QUERY_RESPONSE = "";

    private final static String KERNEL_QUERY_RESPONSE = String.format(
            "{\"files\": [\"kernel\", \"vmlinux\", \"System.map\"], "
            + "\"sha1\": \"%s\", "
            + "\"build-flavor\": \"%s\", "
            + "\"commit-time\": %d, "
            + "\"short-sha1\": \"%s\", "
            + "\"url\": \"http://android-build/kernelbuilds/%s/%s\", "
            + "\"build-id\": \"%d\", "
            + "\"branch\": \"%s\", "
            + "\"path\": \"%s/%s\", "
            + "\"test-tag\": \"%s\"}",
            SHA1, DEVICE_BUILD_FLAVOR, COMMIT_TIME, SHORT_SHA1, KERNEL_BRANCH, SHA1,
            DEVICE_BUILD_ID, KERNEL_BRANCH, KERNEL_BRANCH, SHA1, TEST_TAG);
    private final static String NO_KERNEL_QUERY_RESPONSE = "{}";

    private final static String DEVICE_TARGET = String.format("%s-linux-%s", DEVICE_BRANCH,
            DEVICE_BUILD_FLAVOR);
    private final static String KERNEL_PATH = String.format("%s/%s/kernel", KERNEL_BRANCH, SHA1);
    private final static String MKBOOTIMG_PATH = String.format("%s/%d/mkbootimg", DEVICE_TARGET,
            DEVICE_BUILD_ID);
    private final static String RAMDISK_PATH = String.format("%s/%d/ramdisk.img", DEVICE_TARGET,
            DEVICE_BUILD_ID);
    private static final String BASEBAND_VERSION = "baseband";
    private static final String BOOTLOADER_VERSION = "bootloader";
    private static final String ANDROID_INFO = String.format("require board=board1|board2\n" +
            "require version-bootloader=%s\n" +
            "require version-baseband=%s\n", BOOTLOADER_VERSION, BASEBAND_VERSION);

    private KernelDeviceLaunchControlProvider mProvider = null;
    private IHttpHelper mMockHttpHelper = null;
    private IFileDownloader mMockDownloader = null;
    private File mStubImageFile = null;
    private File mTempDir = null;
    private File mAndroidInfo = null;
    private File mLcCacheDir;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTempDir = FileUtil.createTempDir("test");
        mAndroidInfo = new File(mTempDir, ANDROID_INFO_FILE_NAME);
        FileUtil.writeToFile(ANDROID_INFO, mAndroidInfo);
        mStubImageFile = new File(mTempDir, "image.zip");
        ZipOutputStream zipOutput = new ZipOutputStream(new FileOutputStream(mStubImageFile));
        ZipUtil.addToZip(zipOutput, mAndroidInfo, new LinkedList<String>());
        StreamUtil.close(zipOutput);
        mMockHttpHelper = EasyMock.createMock(IHttpHelper.class);
        mMockDownloader = EasyMock.createMock(IFileDownloader.class);
        mProvider = new KernelDeviceLaunchControlProvider() {
            @Override
            protected IHttpHelper createHttpHelper() {
                return mMockHttpHelper;
            }

            @Override
            protected IFileDownloader createLaunchControlDownloader() {
                return mMockDownloader;
            }

            @Override
            KernelBuildProvider createKernelBuildProvider() {
                return new KernelBuildProvider() {
                    @Override
                    IHttpHelper createHttpHelper() {
                        return mMockHttpHelper;
                    }

                    @Override
                    IFileDownloader createDownloader() {
                        return mMockDownloader;
                    }

                    @Override
                    void markBuildAsTesting(IKernelBuildInfo info) {
                    }
                };
            }

            @Override
            File extractZip(File testsZip) throws IOException, ZipException {
                return new File("/tmp/somepath");
            }
        };
        mProvider.setBranch(DEVICE_BRANCH);
        mProvider.setBuildFlavor(DEVICE_BUILD_FLAVOR);
        mProvider.setTestTag(TAG);
        mProvider.setKernelBranch(KERNEL_BRANCH);

        mMockFile = new File("tmp");
        mLcCacheDir = FileUtil.createTempDir("dlcp-unit-test");
        OptionSetter setter = new OptionSetter(mProvider);
        setter.setOptionValue("download-cache-dir", mLcCacheDir.getAbsolutePath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() throws Exception {
        FileUtil.recursiveDelete(mTempDir);
        FileUtil.recursiveDelete(mLcCacheDir);
        super.tearDown();
    }

    /**
     * Test {@link KernelDeviceLaunchControlProvider#getBuild()} in the normal case.
     */
    public void testGetBuild() throws BuildRetrievalError, IOException, DataSizeException {
        setGetRemoteBuildExpectations();
        setGetKernelBuild();

        // fetchRemoteBuild()
        EasyMock.expect(mMockDownloader.downloadFile(DEVICE_IMAGE_PATH)).andReturn(mStubImageFile);
        EasyMock.expect(mMockDownloader.downloadFile(DEVICE_TEST_PATH)).andReturn(mMockFile);
        EasyMock.expect(mMockDownloader.downloadFile(MKBOOTIMG_PATH)).andReturn(mMockFile);
        EasyMock.expect(mMockDownloader.downloadFile(RAMDISK_PATH)).andReturn(mMockFile);

        EasyMock.replay(mMockHttpHelper, mMockDownloader);

        KernelDeviceBuildInfo info = (KernelDeviceBuildInfo) mProvider.getBuild();
        assertNotNull(info);
        assertEquals(String.format("%s_%d", SHA1, DEVICE_BUILD_ID), info.getBuildId());
        assertEquals(String.format("%s-%s", KERNEL_BRANCH, DEVICE_TARGET),
                info.getBuildTargetName());
        assertEquals(DEVICE_BUILD_FLAVOR, info.getBuildFlavor());
        assertEquals(KERNEL_BRANCH, info.getBuildBranch());

        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link KernelDeviceLaunchControlProvider#getBuild()} if there is no device build.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_noDeviceBuild() throws BuildRetrievalError, IOException,
            DataSizeException {
        // getRemoteBuild()
        EasyMock.expect(mMockHttpHelper.buildUrl((String) EasyMock.anyObject(),
                (MultiMap<String, String>) EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                NO_DEVICE_QUERY_RESPONSE);

        EasyMock.replay(mMockHttpHelper, mMockDownloader);

        assertNull(mProvider.getBuild());

        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link KernelDeviceLaunchControlProvider#getBuild()} if there is no kernel build.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_noKernelBuild() throws BuildRetrievalError, IOException,
            DataSizeException {
        setGetRemoteBuildExpectations();
        setResetTestBuildExpectations();

        // KernelBuildProvider.getBuild()
        EasyMock.expect(mMockHttpHelper.buildUrl((String) EasyMock.anyObject(),
                (MultiMap<String, String>) EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                NO_KERNEL_QUERY_RESPONSE);

        EasyMock.replay(mMockHttpHelper, mMockDownloader);

        assertNull(mProvider.getBuild());

        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link KernelDeviceLaunchControlProvider#getBuild()} if the device build failed to
     * download.
     */
    public void testGetBuild_failedDeviceDownload() throws BuildRetrievalError, IOException,
            DataSizeException {
        setGetRemoteBuildExpectations();
        setGetKernelBuild();
        setResetTestBuildExpectations();

        EasyMock.expect(mMockDownloader.downloadFile(DEVICE_IMAGE_PATH)).andThrow(
                new BuildRetrievalError(""));

        setBuildNotTestedExpectations();

        EasyMock.replay(mMockHttpHelper, mMockDownloader);

        try {
            mProvider.getBuild();
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            // expected
        }

        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link KernelDeviceLaunchControlProvider#getBuild()} if the kernel build failed to
     * download.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_failedKernelDownload() throws BuildRetrievalError, IOException,
            DataSizeException {
        setGetRemoteBuildExpectations();

        EasyMock.expect(mMockHttpHelper.buildUrl((String) EasyMock.anyObject(),
                (MultiMap<String, String>) EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                KERNEL_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(KERNEL_PATH)).andThrow(
                new BuildRetrievalError(""));

        EasyMock.replay(mMockHttpHelper, mMockDownloader);

        try {
            mProvider.getBuild();
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            // expected
        }

        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link KernelDeviceLaunchControlProvider#buildNotTested(IBuildInfo)}.
     */
    public void testBuildNotTested() throws IOException, DataSizeException {
        setBuildNotTestedExpectations();

        EasyMock.replay(mMockHttpHelper);

        mProvider.buildNotTested(new KernelDeviceBuildInfo());

        EasyMock.verify(mMockHttpHelper);
    }

    /**
     * Set the expectations for {@link DeviceLaunchControlProvider#getRemoteBuild()}
     */
    @SuppressWarnings("unchecked")
    private void setGetRemoteBuildExpectations() throws IOException, DataSizeException {
        // DeviceLaunchControlProvider.getRemoteBuild()
        EasyMock.expect(mMockHttpHelper.buildUrl((String) EasyMock.anyObject(),
                (MultiMap<String, String>) EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                DEVICE_QUERY_RESPONSE);
    }

    /**
     * Set the expectations for {@link KernelBuildProvider#getBuild()}.
     */
    @SuppressWarnings("unchecked")
    private void setGetKernelBuild() throws BuildRetrievalError, IOException, DataSizeException {
        // KernelBuildProvider.getBuild()
        EasyMock.expect(mMockHttpHelper.buildUrl((String) EasyMock.anyObject(),
                (MultiMap<String, String>) EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                KERNEL_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(KERNEL_PATH)).andReturn(mMockFile);
    }

    /**
     * Set the expectations for {@link DeviceLaunchControlProvider#buildNotTested(IBuildInfo)} and
     * {@link KernelBuildProvider#buildNotTested(IBuildInfo)}.
     */
    @SuppressWarnings("unchecked")
    private void setBuildNotTestedExpectations() throws IOException, DataSizeException {
        // DeviceLaunchControlProvider.buildNotTested()
        EasyMock.expect(mMockHttpHelper.buildUrl((String) EasyMock.anyObject(),
                (MultiMap<String, String>) EasyMock.anyObject())).andReturn("");
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());

        // KernelBuildProvider.buildNotTested()
        EasyMock.expect(mMockHttpHelper.buildParameters(
                (MultiMap<String, String>) EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doPostWithRetry((String) EasyMock.anyObject(),
                (String) EasyMock.anyObject())).andReturn("");
    }

    /**
     * Set the expectations for {@link DeviceLaunchControlProvider#resetTestBuild(String)}.
     */
    @SuppressWarnings("unchecked")
    private void setResetTestBuildExpectations() throws IOException {
        // DeviceLaunchControlProvider.resetTestBuild()
        EasyMock.expect(mMockHttpHelper.buildUrl((String) EasyMock.anyObject(),
                (MultiMap<String, String>) EasyMock.anyObject())).andReturn("");
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());
    }
}
