// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.build.FolderBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;

import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;

/** Unit tests for {@link CtsLaunchControlProvider} */
@RunWith(JUnit4.class)
public class CtsLaunchControlProviderTest {

    private static final String TEST_TARGET = "TEST_TARGET";
    private static final String BUILD_NAME = "BUILD_NAME";
    private CtsLaunchControlProvider mProvider;
    private IBuildInfo mMockBuildInfo;
    private IFileDownloader mMockFileDownloader;

    private File mTmpDir;
    private File mCtsZip;

    @Before
    public void setUp() throws IOException {
        mProvider = new CtsLaunchControlProvider();
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mMockFileDownloader = EasyMock.createMock(IFileDownloader.class);
        mTmpDir = FileUtil.createTempDir("fake-cts");
        mCtsZip = ZipUtil.createZip(mTmpDir);
    }

    @After
    public void tearDown() {
        mProvider.cleanUp(mMockBuildInfo);
        FileUtil.recursiveDelete(mTmpDir);
        FileUtil.recursiveDelete(mCtsZip);
    }

    /**
     * Test that {@link CtsLaunchControlProvider#downloadBuildFiles(RemoteBuildInfo, String, String,
     * IFileDownloader)} correctly returns a {@link IBuildInfo} created with downloaded info.
     */
    @Test
    public void testDownloadBuildFile() throws Exception {
        RemoteBuildInfo remoteBuild = new RemoteBuildInfo();
        remoteBuild.addAttribute(BuildAttributeKey.CTS, "fakepath");
        EasyMock.expect(mMockFileDownloader.downloadFile(EasyMock.eq("fakepath")))
                .andReturn(mCtsZip);
        EasyMock.replay(mMockFileDownloader);
        IBuildInfo res =
                mProvider.downloadBuildFiles(
                        remoteBuild, TEST_TARGET, BUILD_NAME, mMockFileDownloader);
        try {
            assertEquals(BUILD_NAME, res.getBuildTargetName());
            assertEquals("0", res.getBuildId());
            assertTrue(res instanceof FolderBuildInfo);
            assertTrue(((FolderBuildInfo) res).getRootDir().getName().startsWith("cts_0_"));
            EasyMock.verify(mMockFileDownloader);
        } finally {
            res.cleanUp();
        }
    }

    /**
     * Test that {@link CtsLaunchControlProvider#downloadBuildFiles(RemoteBuildInfo, String, String,
     * IFileDownloader)} correctly returns a {@link IBuildInfo} created with downloaded info with
     * the extra files.
     */
    @Test
    public void testDownloadBuildFile_withExtra() throws Exception {
        File extraDir = FileUtil.createTempDir("extra-dir");
        File extra1 = FileUtil.createTempFile("extra1", "jar", extraDir);
        OptionSetter setter = new OptionSetter(mProvider);
        setter.setOptionValue("cts-extra-artifact", extra1.getName());
        setter.setOptionValue("cts-extra-artifact", "doesnotexists");
        setter.setOptionValue("cts-extra-alt-path", extraDir.getAbsolutePath());

        RemoteBuildInfo remoteBuild = new RemoteBuildInfo();
        remoteBuild.addAttribute(BuildAttributeKey.CTS, "fakepath");
        EasyMock.expect(mMockFileDownloader.downloadFile(EasyMock.eq("fakepath")))
                .andReturn(mCtsZip);
        EasyMock.replay(mMockFileDownloader);
        IBuildInfo res =
                mProvider.downloadBuildFiles(
                        remoteBuild, TEST_TARGET, BUILD_NAME, mMockFileDownloader);
        try {
            assertEquals(BUILD_NAME, res.getBuildTargetName());
            assertEquals("0", res.getBuildId());
            assertTrue(res instanceof FolderBuildInfo);
            assertTrue(((FolderBuildInfo) res).getRootDir().getName().startsWith("cts_0_"));
            // extra file
            assertNotNull(res.getFile(extra1.getName()));
            assertNull(res.getFile("doesnotexists"));
            EasyMock.verify(mMockFileDownloader);
        } finally {
            FileUtil.deleteFile(extra1);
            FileUtil.recursiveDelete(extraDir);
            res.cleanUp();
        }
    }

    /** Test {@link CtsLaunchControlProvider#findFilePath(String, String)}. */
    @Test
    public void testFindFilePath() throws Exception {
        assertNull(mProvider.findFilePath("", ""));
        assertNull(mProvider.findFilePath("", "abc"));
        assertNull(mProvider.findFilePath("abc", "def"));
        assertEquals("abc", mProvider.findFilePath("abc", "abc"));
        assertEquals("def", mProvider.findFilePath("abc,def", "def"));
    }
}
