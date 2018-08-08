// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IAppBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;
import com.android.tradefed.util.net.IHttpHelper;
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
 * Unit tests for {@link AppWithDeviceLaunchControlProvider}.
 */
public class AppWithDeviceLaunchControlProviderTest extends TestCase {
    private static final String ANDROID_INFO_FILE_NAME = "android-info.txt";
    private static final int BUILD_ID = 10;
    private static final String APP_BUILD_ALIAS = "ZZ4";
    private static final int DEVICE_BUILD_ID = 4;
    private static final String APP_PATH = "app.apk";
    private static final String DEVICE_IMAGE_PATH = "image.zip";
    private static final String DEVICE_TEST_PATH = "test.zip";
    private static final String APP_QUERY_RESPONSE = String.format("bid:%d\n%s:%s\nrc:%s", BUILD_ID,
            BuildAttributeKey.APP_APKS.getRemoteValue(), APP_PATH, APP_BUILD_ALIAS);
    // a mock device build server response, that just uses same file path for all build artifacts
    private static final String DEVICE_QUERY_RESPONSE = String.format(
            "bid:%d\n%s:%s\n%s:%s\n",
            DEVICE_BUILD_ID,
            BuildAttributeKey.DEVICE_IMAGE.getRemoteValue(), DEVICE_IMAGE_PATH,
            BuildAttributeKey.TESTS_ZIP.getRemoteValue(), DEVICE_TEST_PATH);
    private static final String NO_BUILD_QUERY_RESPONSE = "";
    private static final String TAG = "tag";
    private static final String DEVICE_BUILD_FLAVOR = "device-flavor";
    private static final String DEVICE_BRANCH = "master";
    private static final String APP_BRANCH = "unbungled_branch";
    private static final String APP_BUILD_FLAVOR = "flavorflav";
    private static final String BASEBAND_VERSION = "baseband";
    private static final String BOOTLOADER_VERSION = "bootloader";
    private static final String ANDROID_INFO = String.format("require board=board1|board2\n" +
            "require version-bootloader=%s\n" +
            "require version-baseband=%s\n", BOOTLOADER_VERSION, BASEBAND_VERSION);

    private IHttpHelper mMockHttpHelper;
    private IFileDownloader mMockDownloader;
    private AppWithDeviceLaunchControlProvider mProvider;
    private File mMockFile;
    private ITestDevice mMockDevice;
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
        mMockHttpHelper = EasyMock.createNiceMock(IHttpHelper.class);
        mMockDownloader = EasyMock.createMock(IFileDownloader.class);
        mProvider = new AppWithDeviceLaunchControlProvider() {
            @Override
            protected IHttpHelper createHttpHelper() {
                return mMockHttpHelper;
            }

            @Override
            protected IFileDownloader createLaunchControlDownloader() {
                return mMockDownloader;
            }

            @Override
            DeviceLaunchControlProvider createDeviceLcProvider() {
                return new DeviceLaunchControlProvider() {
                    @Override
                    protected IHttpHelper createHttpHelper() {
                        return mMockHttpHelper;
                    }

                    @Override
                    protected IFileDownloader createLaunchControlDownloader() {
                        return mMockDownloader;
                    }

                    @Override
                    File extractZip(File testsZip) throws IOException, ZipException {
                        return new File("/tmp/somepath");
                    }
                };
            }


        };
        mProvider.setDeviceBranch(DEVICE_BRANCH);
        mProvider.setDeviceBuildFlavor(DEVICE_BUILD_FLAVOR);
        mProvider.setBranch(APP_BRANCH);
        mProvider.setBuildFlavor(APP_BUILD_FLAVOR);
        mProvider.setTestTag(TAG);
        mMockFile = new File("tmp");
        mMockDevice = EasyMock.createMock(ITestDevice.class);
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
     * Test normal case for {@link AppWithDeviceLaunchControlProvider#getBuild()}.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild() throws Exception {
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andStubReturn("");
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andStubReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                DEVICE_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(APP_PATH)).andReturn(mMockFile);
        // expect download of device image, and tests_zip
        EasyMock.expect(mMockDownloader.downloadFile(DEVICE_IMAGE_PATH)).andReturn(mStubImageFile);
        EasyMock.expect(mMockDownloader.downloadFile(DEVICE_TEST_PATH)).andReturn(mMockFile);
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        IBuildInfo build = mProvider.getBuild(mMockDevice);
        assertTrue(build instanceof IAppBuildInfo);
        assertTrue(build instanceof IDeviceBuildInfo);
        assertEquals(APP_BUILD_ALIAS, build.getBuildAttributes().get("build_alias"));
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link AppWithDeviceLaunchControlProvider#getBuild()} when there is no App build
     * available for testing.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_noAppBuild() throws Exception {
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andStubReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                NO_BUILD_QUERY_RESPONSE);
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        assertNull(mProvider.getBuild(mMockDevice));
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link AppWithDeviceLaunchControlProvider#getBuild()} when there is a app build, but no
     * device build
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_noDeviceBuild() throws Exception {
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                NO_BUILD_QUERY_RESPONSE);

        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mProvider.getBuild(mMockDevice);
            fail("should throw BuildRetrievalError");
        } catch (BuildRetrievalError e) {
            // expected
        }
        // TODO: verify that IBuildInfo.cleanup is called for app build
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link AppWithDeviceLaunchControlProvider#getBuild()} when download of app build fails.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_deviceDownloadFailed() throws Exception {
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                DEVICE_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(APP_PATH)).andReturn(mMockFile);
        EasyMock.expect(mMockDownloader.downloadFile(DEVICE_IMAGE_PATH)).andThrow(
                new BuildRetrievalError("error"));
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");

        // expect a 'reset build' post to mark the device build as not tested
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mProvider.getBuild(mMockDevice);
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            // expected
        }
        // TODO: verify that IBuildInfo.cleanup is called for app build
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link AppWithDeviceLaunchControlProvider#getBuild()} when download of device build
     * fails.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_appDownloadFailed() throws Exception {
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                DEVICE_QUERY_RESPONSE);
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockDownloader.downloadFile(APP_PATH)).andThrow(new BuildRetrievalError(
                "error"));

        // expect a 'reset build' post to mark the app build as not tested
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mProvider.getBuild(mMockDevice);
            fail("BuildRetrievalError not called");
        } catch (BuildRetrievalError e) {
            // expected
        }
        // TODO: verify that IBuildInfo.cleanup is called for app build
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link AppWithDeviceLaunchControlProvider#getBuild()} when min-build-id > retrieved
     * build.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_minBuild() throws Exception {
        mProvider.setMinBuildId(12);
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andStubReturn("");
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andStubReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        assertNull(mProvider.getBuild(mMockDevice));
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test that {@link AppWithDeviceLaunchControlProvider} min-build-id only applies to app
     * build. ie {@link AppWithDeviceLaunchControlProvider#getBuild()} works when
     * 'retrieved device build id' < min-build-id < 'retrieved app build id'
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_minBuildForApp() throws Exception {
        mProvider.setMinBuildId(8);
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andStubReturn("");
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andStubReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject())).andReturn(
                DEVICE_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(APP_PATH)).andReturn(mMockFile);
        // expect download of device image, and tests_zip
        EasyMock.expect(mMockDownloader.downloadFile(DEVICE_IMAGE_PATH)).andReturn(mStubImageFile);
        EasyMock.expect(mMockDownloader.downloadFile(DEVICE_TEST_PATH)).andReturn(mMockFile);
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        IBuildInfo build = mProvider.getBuild(mMockDevice);
        assertTrue(build instanceof IAppBuildInfo);
        assertTrue(build instanceof IDeviceBuildInfo);
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }
}
