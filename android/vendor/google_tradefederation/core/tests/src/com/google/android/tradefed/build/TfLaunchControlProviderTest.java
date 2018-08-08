// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import static org.junit.Assert.*;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;
import com.android.tradefed.util.net.IHttpHelper;

import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/** Unit tests for {@link TfLaunchControlProvider}. */
@RunWith(JUnit4.class)
public class TfLaunchControlProviderTest {

    private static final String TEST_TAG = "tag";
    private static final String TEST_TARGET = "passion";
    private static final String TEST_BRANCH = "master";
    private static final String TEST_QUERY_RESULT =
            String.format(
                    "%s:4\n%s:filepath\nrc:AA3",
                    BuildAttributeKey.BUILD_ID.getRemoteValue(),
                    BuildAttributeKey.DEVICE_IMAGE.getRemoteValue());
    private TfLaunchControlProvider mLcp;
    private IHttpHelper mMockHttpHelper;
    private IFileDownloader mMockDownloader;

    private File mLcCacheDir = null;

    @Before
    public void setUp() throws Exception {
        mMockHttpHelper = EasyMock.createMock(IHttpHelper.class);
        mMockDownloader = EasyMock.createNiceMock(IFileDownloader.class);

        mLcp =
                new TfLaunchControlProvider() {
                    @Override
                    protected IHttpHelper createHttpHelper() {
                        return mMockHttpHelper;
                    }

                    @Override
                    protected IFileDownloader createLaunchControlDownloader() {
                        return mMockDownloader;
                    }
                };
        mLcp.setTestTag(TEST_TAG);
        mLcp.setBuildFlavor(TEST_TARGET);
        mLcp.setBranch(TEST_BRANCH);
        mLcCacheDir = FileUtil.createTempDir("tflcp-unit-test");
        OptionSetter setter = new OptionSetter(mLcp);
        setter.setOptionValue("download-cache-dir", mLcCacheDir.getAbsolutePath());
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mLcCacheDir);
    }

    /** Test for when there is no google-tradefed.zip in the filepath returned by server. */
    @Test
    public void testQueryForBuild_noTfArtifact() throws Exception {
        EasyMock.expect(mMockHttpHelper.buildUrl(EasyMock.anyObject(), EasyMock.anyObject()))
                .andReturn("fakeUrl");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry("fakeUrl"))
                .andReturn(TEST_QUERY_RESULT + "\nfiles:file.img");
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mLcp.queryForBuild();
            fail("Should have thrown an exception");
        } catch (BuildRetrievalError expected) {
            assertEquals(
                    "Failed to locate TradeFed artifact google-tradefed.zip",
                    expected.getMessage());
        }
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /** Test for when the server returns a google-tradefed.zip path. */
    @Test
    public void testQueryForBuild() throws Exception {
        final String tfRemotePath = "path/google-tradefed.zip";
        EasyMock.expect(mMockHttpHelper.buildUrl(EasyMock.anyObject(), EasyMock.anyObject()))
                .andReturn("fakeUrl");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry("fakeUrl"))
                .andReturn(TEST_QUERY_RESULT + "\nfiles:" + tfRemotePath);
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        RemoteBuildInfo res = mLcp.queryForBuild();
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
        assertNotNull(res);
        assertEquals(tfRemotePath, res.getAttribute(BuildAttributeKey.TF));
    }

    /** Test when the downloaded file cannot be extracted. */
    @Test
    public void testDownloadFiles_failedToExtract() throws Exception {
        final String fakeRemotePath = "fake/path/google-tradefed.zip";
        RemoteBuildInfo remoteBuild = new RemoteBuildInfo();
        remoteBuild.addAttribute(BuildAttributeKey.TF, fakeRemotePath);
        File downloadedFile = FileUtil.createTempFile("tfLCP", ".test");
        EasyMock.expect(mMockDownloader.downloadFile(fakeRemotePath)).andReturn(downloadedFile);
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mLcp.downloadBuildFiles(remoteBuild, "targetName", "tradefed", mMockDownloader);
            fail("Should have thrown an exception");
        } catch (BuildRetrievalError expected) {
            // File should have been deleted
            assertFalse(downloadedFile.exists());
            assertEquals("Failed to extract google-tradefed.zip", expected.getMessage());
        } finally {
            FileUtil.recursiveDelete(downloadedFile);
        }
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /** Test when the downloaded file is correctly extracted and cleaned up. */
    @Test
    public void testDownloadFiles() throws Exception {
        final String fakeRemotePath = "fake/path/google-tradefed.zip";
        RemoteBuildInfo remoteBuild = new RemoteBuildInfo();
        remoteBuild.addAttribute(BuildAttributeKey.TF, fakeRemotePath);
        File downloadedFile = FileUtil.createTempDir("tf-lcp-test");
        File zipTf = ZipUtil.createZip(downloadedFile);

        EasyMock.expect(mMockDownloader.downloadFile(fakeRemotePath)).andReturn(zipTf);
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            IBuildInfo info = mLcp.downloadBuildFiles(remoteBuild, "tag", "build", mMockDownloader);
            assertNotNull(info);
            assertTrue(info instanceof IFolderBuildInfo);
            File rootFolder = ((IFolderBuildInfo) info).getRootDir();
            assertNotNull(rootFolder);
            assertTrue(rootFolder.exists());
            // After clean up the root folder must be deleted.
            mLcp.cleanUp(info);
            assertFalse(rootFolder.exists());
        } finally {
            FileUtil.recursiveDelete(downloadedFile);
            FileUtil.recursiveDelete(zipTf);
        }
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /** Test {@link TfLaunchControlProvider#findFilePath(String, String)}. */
    @Test
    public void testFindFilePath() throws Exception {
        assertNull(mLcp.findFilePath("", ""));
        assertNull(mLcp.findFilePath("", "abc"));
        assertNull(mLcp.findFilePath("abc", "def"));
        assertEquals("d/abc", mLcp.findFilePath("d/abc", "abc"));
        assertEquals("g/def", mLcp.findFilePath("abc,g/def", "def"));
    }
}
