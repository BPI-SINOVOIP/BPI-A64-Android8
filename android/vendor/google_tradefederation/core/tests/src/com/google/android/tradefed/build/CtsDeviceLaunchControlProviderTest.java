// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;
import com.android.tradefed.util.net.IHttpHelper;
import com.android.tradefed.util.net.IHttpHelper.DataSizeException;
import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

/**
 * Unit tests for {@link CtsDeviceLaunchControlProvider}.
 */
public class CtsDeviceLaunchControlProviderTest extends TestCase {

    private static final String ANDROID_INFO_FILE_NAME = "android-info.txt";
    private static final int BUILD_ID = 4;
    private static final String CTS_PATH = "git_ics-release-linux-cts/168183/android-cts.zip";
    private static final String DEVICE_IMAGE_PATH = "image.zip";
    private static final String DEVICE_TEST_PATH = "test.zip";
    private static final String CTS_QUERY_RESPONSE = String.format("bid:%d\n%s:%s\nfiles:%s",
            BUILD_ID, BuildAttributeKey.CTS.getRemoteValue(), CTS_PATH, CTS_PATH);
    // a mock device build server response, that just uses same file path for all build artifacts
    private static final String DEVICE_QUERY_RESPONSE = String.format(
            "bid:%d\n%s:%s\n%s:%s\n",
            BUILD_ID,
            BuildAttributeKey.DEVICE_IMAGE.getRemoteValue(), DEVICE_IMAGE_PATH,
            BuildAttributeKey.TESTS_ZIP.getRemoteValue(), DEVICE_TEST_PATH);
    private static final String NO_BUILD_QUERY_RESPONSE = "";
    private static final String TAG = "tag";
    private static final String DEVICE_BUILD_FLAVOR = "device-flavor";
    private static final String BRANCH = "master";
    private static final String BASEBAND_VERSION = "baseband";
    private static final String BOOTLOADER_VERSION = "bootloader";
    private static final String ANDROID_INFO = String.format("require board=board1|board2\n" +
            "require version-bootloader=%s\n" +
            "require version-baseband=%s\n", BOOTLOADER_VERSION, BASEBAND_VERSION);

    private IHttpHelper mMockHttpHelper;
    private IFileDownloader mMockDownloader;
    private CtsDeviceLaunchControlProvider mProvider;
    private File mMockFile;
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
        mProvider = new CtsDeviceLaunchControlProvider() {
            @Override
            protected IHttpHelper createHttpHelper() {
                return mMockHttpHelper;
            }

            @Override
            protected IFileDownloader createLaunchControlDownloader() {
                return mMockDownloader;
            }

            @Override
            CtsLaunchControlProvider createCtsLcProvider() {
                return new CtsLaunchControlProvider() {
                    @Override
                    protected IHttpHelper createHttpHelper() {
                        return mMockHttpHelper;
                    }

                    @Override
                    protected IFileDownloader createLaunchControlDownloader() {
                        return mMockDownloader;
                    }

                    @Override
                    File extractCtsZip(File ctsZip, String buildid) {
                        // overridden because parent method requires that the ctsZip file be a
                        // valid zip file
                        return mMockFile;
                    }
                };
            }

            @Override
            File extractZip(File testsZip) throws IOException, ZipException {
                return new File("/tmp/somepath");
            }
        };
        mProvider.setBranch(BRANCH);
        mProvider.setBuildFlavor(DEVICE_BUILD_FLAVOR);
        mProvider.setTestTag(TAG);
        mProvider.skipDownload(BuildAttributeKey.MKBOOTIMG);
        mProvider.skipDownload(BuildAttributeKey.RAMDISK);
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
     * Test normal case for {@link CtsDeviceLaunchControlProvider#getBuild()}.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild() throws IOException, DataSizeException, BuildRetrievalError {
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andStubReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                DEVICE_QUERY_RESPONSE);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                CTS_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(CTS_PATH)).andReturn(mMockFile);
        // expect download of device image and tests zip
        EasyMock.expect(mMockDownloader.downloadFile(DEVICE_IMAGE_PATH)).andReturn(mStubImageFile);
        EasyMock.expect(mMockDownloader.downloadFile(DEVICE_TEST_PATH)).andReturn(mMockFile);
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        IBuildInfo build = mProvider.getBuild();
        assertTrue(build instanceof IFolderBuildInfo);
        assertTrue(build instanceof IDeviceBuildInfo);
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link CtsDeviceLaunchControlProvider#getBuild()} when there is no device build
     * available for testing.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_noDeviceBuild() throws IOException, DataSizeException,
            BuildRetrievalError {
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                NO_BUILD_QUERY_RESPONSE);
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        assertNull(mProvider.getBuild());
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link CtsDeviceLaunchControlProvider#getBuild()} when there is a device build, but no
     * CTS build
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_noCtsBuild() throws IOException, DataSizeException,
    BuildRetrievalError {
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                DEVICE_QUERY_RESPONSE);
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                NO_BUILD_QUERY_RESPONSE);
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");

        // expect a 'reset build' post to mark the device build as not tested
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        assertNull(mProvider.getBuild());
        // TODO: verify that IBuildInfo.cleanup is called for device build
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link CtsDeviceLaunchControlProvider#getBuild()} when download of CTS build fails.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_ctsDownloadFailed() throws IOException, DataSizeException,
            BuildRetrievalError {
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                DEVICE_QUERY_RESPONSE);
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                CTS_QUERY_RESPONSE);
        // expect download of device image, and tests_zip
        EasyMock.expect(mMockDownloader.downloadFile(DEVICE_IMAGE_PATH)).andReturn(mStubImageFile);
        EasyMock.expect(mMockDownloader.downloadFile(DEVICE_TEST_PATH)).andReturn(mMockFile);
        EasyMock.expect(mMockDownloader.downloadFile(CTS_PATH)).andThrow(
                new BuildRetrievalError("error"));
        // expect a 'reset build' post to mark the cts build as not tested
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mProvider.getBuild();
            fail("BuildRetrievalError not called");
        } catch (BuildRetrievalError e) {
            // expected
        }
        // TODO: verify that IBuildInfo.cleanup is called for device build
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link CtsDeviceLaunchControlProvider#getBuild()} when download of device build fails.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_deviceDownloadFailed() throws IOException, DataSizeException,
            BuildRetrievalError {
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                DEVICE_QUERY_RESPONSE);
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                CTS_QUERY_RESPONSE);
        // expect download of device image, tests zip, and userdata
        EasyMock.expect(mMockDownloader.downloadFile(DEVICE_IMAGE_PATH)).andThrow(
                new BuildRetrievalError("error"));
        // expect a 'reset build' post to mark the cts build as not tested
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mProvider.getBuild();
            fail("BuildRetrievalError not called");
        } catch (BuildRetrievalError e) {
            // expected
        }
        // TODO: verify that IBuildInfo.cleanup is called for cts build
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }
}
