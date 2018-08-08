// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.command.FatalHostError;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
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
import java.util.Collection;
import java.util.LinkedList;
import java.util.zip.ZipOutputStream;

/**
 * Unit tests for {@link DeviceLaunchControlProvider} and by extension
 * {@link LaunchControlProvider}
 */
public class DeviceLaunchControlProviderTest extends TestCase {

    private static final String ANDROID_INFO_FILE_NAME = "android-info.txt";
    private static final String TEST_QUERY_RESULT = String.format("%s:4\n%s:filepath\nrc:AA3",
            BuildAttributeKey.BUILD_ID.getRemoteValue(),
            BuildAttributeKey.DEVICE_IMAGE.getRemoteValue());
    private static final String TEST_QUERY_RESULT_WITH_FILE =
            String.format(
                    "%s:4\n%s:filepath\n%s:foo\nrc:AA3",
                    BuildAttributeKey.BUILD_ID.getRemoteValue(),
                    BuildAttributeKey.DEVICE_IMAGE.getRemoteValue(),
                    BuildAttributeKey.FILES.getRemoteValue());
    private static final String BUILD_DETAIL_RESULT = String.format(
            "%s:git_mnc-release\n%s:2219288\nrc:mra58e\ntags:",
            BuildAttributeKey.BRANCH.getRemoteValue(),
            BuildAttributeKey.BUILD_ID.getRemoteValue());

    private static final String BASEBAND_VERSION = "baseband";
    private static final String BOOTLOADER_VERSION = "bootloader";
    private static final String ANDROID_INFO = String.format("require board=board1|board2\n" +
            "require version-bootloader=%s\n" +
            "require version-baseband=%s\n", BOOTLOADER_VERSION, BASEBAND_VERSION);
    private static final String TEST_TAG = "tag";
    private static final QueryType TEST_QUERY = QueryType.LATEST_GREEN_BUILD;
    private static final String TEST_TARGET = "passion";
    private static final String TEST_BRANCH = "master";

    private DeviceLaunchControlProvider mLaunchControlProvider;
    private IHttpHelper mMockHttpHelper;
    private IFileDownloader mMockDownloader;
    private File mStubImageFile = null;
    private File mTempDir = null;
    private File mAndroidInfo = null;
    private File mLcCacheDir = null;


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
        mMockDownloader = EasyMock.createNiceMock(IFileDownloader.class);
        mLaunchControlProvider = new DeviceLaunchControlProvider() {
            @Override
            protected IHttpHelper createHttpHelper() {
                return mMockHttpHelper;
            }

            @Override
            protected IFileDownloader createLaunchControlDownloader() {
                return mMockDownloader;
            }

            @Override
            String getUrl() {
                return "fakeUrl";
            }

        };
        mLaunchControlProvider.setBranch(TEST_BRANCH);
        mLaunchControlProvider.setBuildFlavor(TEST_TARGET);
        mLaunchControlProvider.setQueryType(TEST_QUERY);
        mLaunchControlProvider.setTestTag(TEST_TAG);
        mLcCacheDir = FileUtil.createTempDir("dlcp-unit-test");
        OptionSetter setter = new OptionSetter(mLaunchControlProvider);
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
     * Configure the mock downloader to simulate any number of successful download file attempts
     */
    private void setDownloadSuccessExpectations() throws Exception {
        EasyMock.expect(mMockDownloader.downloadFile("filepath")).andReturn(mStubImageFile);
        EasyMock.expect(mMockDownloader.downloadFile((String)EasyMock.anyObject())).
                andReturn(new File("tmp")).anyTimes();
        EasyMock.replay(mMockDownloader);
    }

    /**
     * Test normal success case for {@link LaunchControlProvider#getBuild()}.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild() throws Exception {
        setDownloadSuccessExpectations();
        // TODO: consider verifying contents of param - but probably overkill
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject());
        EasyMock.expectLastCall().andReturn(TEST_QUERY_RESULT);
        EasyMock.replay(mMockHttpHelper);
        IBuildInfo info = mLaunchControlProvider.getBuild();
        assertNotNull(info);
        assertEquals("4", info.getBuildId());
        assertEquals(TEST_BRANCH, info.getBuildBranch());
        assertEquals(TEST_TARGET, info.getBuildFlavor());
        assertEquals("AA3", info.getBuildAttributes().get("build_alias"));
    }

    /**
     * Test {@link LaunchControlProvider#getBuild()} when a build id is provided wirth no query
     * type.
     * <p/>
     * Expect queryType to be override to {@link QueryType#NOTIFY_TEST_BUILD}.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_buildId() throws Exception {
        setDownloadSuccessExpectations();
        mLaunchControlProvider.setBuildId("4");
        // TODO: verify that query type is NOTIFY_TEST_BUILD
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject());
        EasyMock.expectLastCall().andReturn(TEST_QUERY_RESULT);
        EasyMock.replay(mMockHttpHelper);
        IBuildInfo info = mLaunchControlProvider.getBuild();
        assertNotNull(info);
        assertEquals("4", info.getBuildId());
        assertEquals(TEST_BRANCH, info.getBuildBranch());
        assertEquals(TEST_TARGET, info.getBuildFlavor());
    }

    /**
     * Test {@link LaunchControlProvider#getBuild()} behavior when branch is not specified.
     */
    public void testGetBuild_noBranch() throws Exception {
        mLaunchControlProvider.setBranch(null);
        EasyMock.replay(mMockHttpHelper);
        try {
            mLaunchControlProvider.getBuild();
            fail("did not throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test {@link LaunchControlProvider#getBuild()} behavior when target is not specified.
     */
    public void testGetBuild_noTarget() throws Exception {
        mLaunchControlProvider.setBuildFlavor(null);
        EasyMock.replay(mMockHttpHelper);
        try {
            mLaunchControlProvider.getBuild();
            fail("did not throw BuildRetrievalError");
        } catch (BuildRetrievalError e) {
            // expected
        }
    }

    /**
     * Test {@link LaunchControlProvider#getBuild()} behavior when test tag is not specified.
     */
    public void testGetBuild_noTag() throws Exception {
        mLaunchControlProvider.setTestTag(null);
        EasyMock.replay(mMockHttpHelper);
        try {
            mLaunchControlProvider.getBuild();
            fail("did not throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test {@link LaunchControlProvider#getBuild()} when {@link IOException} is thrown when
     * contacting server.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_IOException() throws Exception {
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("").anyTimes();
        mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(new IOException()).anyTimes();
        EasyMock.replay(mMockHttpHelper);
        try {
            mLaunchControlProvider.getBuild();
            fail("did not throw BuildRetrievalError");
        } catch (BuildRetrievalError e) {
            // expected
        }
    }

    /**
     * Test {@link LaunchControlProvider#getBuild()} when {@link DataSizeException} is thrown when
     * contacting server.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_DataSizeException() throws Exception {
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("").anyTimes();
        mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(new DataSizeException()).anyTimes();
        EasyMock.replay(mMockHttpHelper);
        try {
            mLaunchControlProvider.getBuild();
            fail("did not throw BuildRetrievalError");
        } catch (BuildRetrievalError e) {
            // expected
        }
    }

    /**
     * Test {@link LaunchControlProvider#getBuild()} when a min build id has been provided
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_minBuildId() throws Exception {
        // set min build id > build id returned in TEST_QUERY_RESULT
        mLaunchControlProvider.setMinBuildId(5);
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject());
        EasyMock.expectLastCall().andReturn(TEST_QUERY_RESULT);
        EasyMock.replay(mMockHttpHelper);
        assertNull(mLaunchControlProvider.getBuild());
    }

    /**
     * Test {@link LaunchControlProvider#getBuild()} when downloading of files fails due to a
     * {@link FatalHostError}
     */
    public void testGetBuild_downloadFailedFatal() throws Exception {
        setGetBuildDownloadFailedExpectations(new FatalHostError("error"));
        try {
            mLaunchControlProvider.getBuild();
            fail("FatalHostError not thrown");
        } catch (FatalHostError e) {
            // expected
        }
        EasyMock.verify(mMockHttpHelper);
    }

    /**
     * Test {@link LaunchControlProvider#getBuild()} when downloading of files fails due to a
     * {@link BuildRetrievalError}
     */
    public void testGetBuild_downloadFailed() throws Exception {
        setGetBuildDownloadFailedExpectations(new BuildRetrievalError("error"));
        try {
            mLaunchControlProvider.getBuild();
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            // expected
        }
        EasyMock.verify(mMockHttpHelper);
    }

    /**
     * Test {@link DeviceLaunchControlProvider#downloadBuildFiles(RemoteBuildInfo, String,
     *      String, IFileDownloader)}
     * when {@link RemoteBuildInfo} has no device image.
     */
    public void testDownloadBuildFiles_noDeviceImage() throws Exception {
        try {
            String queryResultNoDeviceImage = String.format("%s:4\nrc:AA3",
                    BuildAttributeKey.BUILD_ID.getRemoteValue());
            RemoteBuildInfo remoteBuild = RemoteBuildInfo.parseRemoteBuildInfo(
                    queryResultNoDeviceImage);
            mLaunchControlProvider.downloadBuildFiles(
                    remoteBuild, "target", "build", mMockDownloader);
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            // expected
        }
    }

    /**
     * Make sure that download skipping works as expected.
     */
    public void testSkipDownload() throws Exception {
        final BuildAttributeKey key = BuildAttributeKey.USER_DATA;
        mLaunchControlProvider.addDownloadKey(key);
        assertTrue(mLaunchControlProvider.shouldDownload(key));
        mLaunchControlProvider.skipDownload(key);
        assertFalse(mLaunchControlProvider.shouldDownload(key));
    }

    /**
     * Make sure that we don't attempt to download files that aren't specified to download.
     */
    public void testAddDownloadKey() throws Exception {
        final BuildAttributeKey key = BuildAttributeKey.USER_DATA;
        mLaunchControlProvider.getDownloadKeys().clear();
        assertFalse(mLaunchControlProvider.shouldDownload(key));
        mLaunchControlProvider.addDownloadKey(key);
        assertTrue(mLaunchControlProvider.shouldDownload(key));
    }

    /**
     * Make sure that by default, we attempt to download the default keys, and don't attempt to
     * download the keys that aren't mentioned.
     */
    public void testDefaults() throws Exception {
        Collection<BuildAttributeKey> downloadKeys = mLaunchControlProvider.getDownloadKeys();

        for (BuildAttributeKey key : BuildAttributeKey.values()) {
            assertEquals(downloadKeys.contains(key), mLaunchControlProvider.shouldDownload(key));
        }
    }

    /**
     * Configure EasyMock expectations to simulate a {@link LaunchControlProvider#getBuild()} call
     * that fails due to a {@link IFileDownloader#downloadFile(String)} exception
     *
     * @param downloadException the {@link Exception} that
     *            {@link IFileDownloader#downloadFile(String)} should throw
     */
    @SuppressWarnings("unchecked")
    private void setGetBuildDownloadFailedExpectations(Exception downloadException)
            throws IOException, DataSizeException, BuildRetrievalError {
        // expect successful query
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject());
        EasyMock.expectLastCall().andReturn(TEST_QUERY_RESULT);
        // then expect "reset-test" query
        // TODO: verify contents
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());
        EasyMock.expect(mMockDownloader.downloadFile((String)EasyMock.anyObject())).andThrow(
                downloadException);
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Verifies that resolveBuildInfoFromDevice will override query type to notify test CL if device
     * has a valid numeric build id
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public void testResolveBuildInfoFromDevice_NotifyTestCl() throws Exception {
        ITestDevice device = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(device.getBuildId()).andReturn("2219288");
        EasyMock.expect(device.getBuildFlavor()).andReturn(null);
        EasyMock.expect(mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject());
        EasyMock.expectLastCall().andReturn(BUILD_DETAIL_RESULT);
        EasyMock.replay(mMockHttpHelper, device);
        mLaunchControlProvider.resolveBuildInfoFromDevice(device);
        EasyMock.verify(mMockHttpHelper, device);
        assertEquals(QueryType.NOTIFY_TEST_BUILD, mLaunchControlProvider.getQueryType());
    }

    /**
     * Verifies that {@link DeviceLaunchControlProvider#resolveBuildInfoFromDevice(ITestDevice)}
     * will override query type to query latest build if device has an eng build
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public void testResolveBuildInfoFromDevice_QueryLatest() throws Exception {
        ITestDevice device = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(device.getBuildId()).andReturn("ABCDEFG");
        EasyMock.replay(mMockHttpHelper, device);
        mLaunchControlProvider.resolveBuildInfoFromDevice(device);
        assertEquals(QueryType.QUERY_LATEST_BUILD, mLaunchControlProvider.getQueryType());
        EasyMock.verify(mMockHttpHelper, device);
    }

    /**
     * Verifies that {@link DeviceLaunchControlProvider#downloadBuildFiles}
     * will try to download bootloader image.
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public void testDownloadBootloader() throws Exception {
        setDownloadSuccessExpectations();
        RemoteBuildInfo remoteBuild = RemoteBuildInfo.parseRemoteBuildInfo(
                TEST_QUERY_RESULT + "\nfiles:bootloader.img");
        IDeviceBuildInfo info = (IDeviceBuildInfo) mLaunchControlProvider.downloadBuildFiles(
                remoteBuild, "target", "build", mMockDownloader);
        assertNotNull(info);
        assertNotNull(info.getBootloaderImageFile());
        assertEquals(BOOTLOADER_VERSION, info.getBootloaderVersion());
    }

    /**
     * Verifies that {@link DeviceLaunchControlProvider#downloadBuildFiles}
     * will skip to download bootloader image if it's not in files.
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public void testDownloadBootloader_notInFiles() throws Exception {
        setDownloadSuccessExpectations();
        RemoteBuildInfo remoteBuild = RemoteBuildInfo.parseRemoteBuildInfo(
                TEST_QUERY_RESULT + "\nfiles:file.img");
        IDeviceBuildInfo info = (IDeviceBuildInfo) mLaunchControlProvider.downloadBuildFiles(
                remoteBuild, "target", "build", mMockDownloader);
        assertNotNull(info);
        assertNull(info.getBootloaderImageFile());
        assertNull(info.getBootloaderVersion());
    }

    /**
     * Verifies that {@link DeviceLaunchControlProvider#downloadBuildFiles}
     * will try to download bootloader image and if there is no bootloader image in
     * the artifact, it will ignore the failure.
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public void testDownloadBootloader_failed() throws Exception {
        RemoteBuildInfo remoteBuild = RemoteBuildInfo.parseRemoteBuildInfo(
                TEST_QUERY_RESULT + "\nfiles:bootloader.img");
        EasyMock.expect(mMockDownloader.downloadFile("bootloader.img"))
                .andThrow(new BuildRetrievalError("No bootloader image."));
        setDownloadSuccessExpectations();
        IDeviceBuildInfo info = (IDeviceBuildInfo) mLaunchControlProvider.downloadBuildFiles(
                remoteBuild, "target", "build", mMockDownloader);
        assertNotNull(info);
        assertNull(info.getBootloaderImageFile());
    }

    /**
     * Verifies that {@link DeviceLaunchControlProvider#downloadBuildFiles}
     * will try to download baseband image.
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public void testDownloadBaseband() throws Exception {
        setDownloadSuccessExpectations();
        RemoteBuildInfo remoteBuild = RemoteBuildInfo.parseRemoteBuildInfo(
                TEST_QUERY_RESULT + "\nfiles:radio.img");
        IDeviceBuildInfo info = (IDeviceBuildInfo) mLaunchControlProvider.downloadBuildFiles(
                    remoteBuild, "target", "build", mMockDownloader);
        assertNotNull(info);
        assertNotNull(info.getBasebandImageFile());
        assertEquals(BASEBAND_VERSION, info.getBasebandVersion());
    }

    /**
     * Verifies that {@link DeviceLaunchControlProvider#downloadBuildFiles}
     * will skip to download baseband image if baseband image is not in files.
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public void testDownloadBaseband_notInFiles() throws Exception {
        setDownloadSuccessExpectations();
        RemoteBuildInfo remoteBuild = RemoteBuildInfo.parseRemoteBuildInfo(
                TEST_QUERY_RESULT + "\nfiles:file.img");
        IDeviceBuildInfo info = (IDeviceBuildInfo) mLaunchControlProvider.downloadBuildFiles(
                remoteBuild, "target", "build", mMockDownloader);
        assertNotNull(info);
        assertNull(info.getBasebandImageFile());
        assertNull(info.getBasebandVersion());
    }

    /**
     * Verifies that {@link DeviceLaunchControlProvider#downloadBuildFiles}
     * will try to download baseband image and if there is no baseband image in
     * the artifact, it will ignore the failure.
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public void testDownloadBaseband_failed() throws Exception {
        RemoteBuildInfo remoteBuild = RemoteBuildInfo.parseRemoteBuildInfo(
                TEST_QUERY_RESULT + "\nfiles:radio.img");
        EasyMock.expect(mMockDownloader.downloadFile("radio.img"))
                .andThrow(new BuildRetrievalError("No baseband image."));
        setDownloadSuccessExpectations();
        IDeviceBuildInfo info = (IDeviceBuildInfo) mLaunchControlProvider.downloadBuildFiles(
                remoteBuild, "target", "build", mMockDownloader);
        assertNotNull(info);
        assertNull(info.getBasebandImageFile());
    }

    /**
     * Verifies that {@link DeviceLaunchControlProvider#downloadBuildFiles}
     * will throw BuildRetrievalError when pasing android-info.txt failed.
     * @throws Exception
     */
    public void testDownloadBuildFiles_parseAndroidInfoFailed() throws Exception {
        EasyMock.expect(mMockDownloader.downloadFile("filepath")).andReturn(new File("tmp"));
        EasyMock.replay(mMockDownloader);
        RemoteBuildInfo remoteBuild = RemoteBuildInfo.parseRemoteBuildInfo(
                TEST_QUERY_RESULT);
        try {
            mLaunchControlProvider.downloadBuildFiles(
                    remoteBuild, "target", "build", mMockDownloader);
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            // expected
        }
    }

    /**
     * Verifies that {@link DeviceLaunchControlProvider#downloadBuildFiles}
     * can skip device image.
     * @throws Exception
     */
    public void testDownloadBuildFiles_skipDeviceImage() throws Exception {
        mLaunchControlProvider.skipDownload(BuildAttributeKey.DEVICE_IMAGE);
        RemoteBuildInfo remoteBuild = RemoteBuildInfo.parseRemoteBuildInfo(
                TEST_QUERY_RESULT);
        setDownloadSuccessExpectations();
        IDeviceBuildInfo info = (IDeviceBuildInfo) mLaunchControlProvider.downloadBuildFiles(
                remoteBuild, "target", "build", mMockDownloader);
        assertNotNull(info);
        assertNull(info.getDeviceImageFile());
    }

    /**
     * Verifies that {@link DeviceLaunchControlProvider#fetchTestsZipPath(RemoteBuildInfo)} returns
     * the correct zip path when RemoteBuildInfo has no files.
     *
     * @throws Exception
     */
    public void testFetchTestsZipPath_empty() throws Exception {
        OptionSetter setter = new OptionSetter(mLaunchControlProvider);
        RemoteBuildInfo remoteBuildEmpty = RemoteBuildInfo.parseRemoteBuildInfo(TEST_QUERY_RESULT);
        assertNull(mLaunchControlProvider.fetchTestsZipPath(remoteBuildEmpty));
        setter.setOptionValue("test-zip-file-filter", "hi");
        assertNull(mLaunchControlProvider.fetchTestsZipPath(remoteBuildEmpty));
    }

    /**
     * Verifies that {@link DeviceLaunchControlProvider#fetchTestsZipPath(RemoteBuildInfo)} returns
     * the correct zip path when RemoteBuildInfo has a file foo.
     *
     * @throws Exception
     */
    public void testFetchTestsZipPath_withFile() throws Exception {
        OptionSetter setter = new OptionSetter(mLaunchControlProvider);
        RemoteBuildInfo remoteBuildEmpty =
                RemoteBuildInfo.parseRemoteBuildInfo(TEST_QUERY_RESULT_WITH_FILE);
        assertNull(mLaunchControlProvider.fetchTestsZipPath(remoteBuildEmpty));
        setter.setOptionValue("test-zip-file-filter", "foo");
        assertEquals("foo", mLaunchControlProvider.fetchTestsZipPath(remoteBuildEmpty));
    }

}
