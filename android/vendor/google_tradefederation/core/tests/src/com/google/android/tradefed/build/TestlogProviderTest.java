// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link TestlogProvider}.
 */
@RunWith(JUnit4.class)
public class TestlogProviderTest {

    private File mTestTemp;
    private TestlogProvider mLogProvider;
    private File mLcCacheDir;

    @Before
    public void setUp() throws Exception {
        mTestTemp = FileUtil.createTempDir("test");
        mLogProvider = new TestlogProvider();
        mLcCacheDir = FileUtil.createTempDir("dlcp-unit-test");
        OptionSetter setter = new OptionSetter(mLogProvider);
        setter.setOptionValue("download-cache-dir", mLcCacheDir.getAbsolutePath());
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mTestTemp);
        FileUtil.recursiveDelete(mLcCacheDir);
    }

    @Test
    public void testDownloadBuildFilesAddTestlogs() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mLogProvider);
        optionSetter.setOptionValue("testlog-regex", "tests/.*");
        optionSetter.setOptionValue("branch", "git_master");
        optionSetter.setOptionValue("build-flavor", "full-eng");

        File testlog = FileUtil.createTempFile("FooTest", "log", mTestTemp);
        FileUtil.writeToFile("TEST TEXT", testlog);

        IFileDownloader mockDownloader = mock(IFileDownloader.class);
        when(mockDownloader.downloadFile(
                "git_master-linux-full-eng/124/tests/FooTest.txt")).thenReturn(testlog);

        RemoteBuildInfo remoteBuildInfo = RemoteBuildInfo.parseRemoteBuildInfo(
                "rc:\n"
                        + "target:git_master-linux-full-eng\n"
                        + "bid:124\n"
                        + "files:git_master-linux-full-eng/124/BUILD_INFO,"
                        + "git_master-linux-full-eng/124/tests/FooTest.txt\n");
        IBuildInfo localBuildInfo = mLogProvider
                .downloadBuildFiles(remoteBuildInfo, "test-target", "test-build", mockDownloader);
        try {
            File copiedLog = localBuildInfo.getFile(testlog.getAbsolutePath());
            assertEquals("TEST TEXT", FileUtil.readStringFromFile(copiedLog));
        } finally {
            localBuildInfo.cleanUp();
        }
    }

    @Test
    public void testDownloadBuildFilesConvertsIoExceptionToBuildRetrievalError() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mLogProvider);
        optionSetter.setOptionValue("testlog-regex", "tests/.*");
        optionSetter.setOptionValue("branch", "git_master");
        optionSetter.setOptionValue("build-flavor", "full-eng");

        File testlog = new File(mTestTemp, "IDONTEXIST");

        IFileDownloader mockDownloader = mock(IFileDownloader.class);
        when(mockDownloader.downloadFile(
                "git_master-linux-full-eng/124/tests/FooTest.txt")).thenReturn(testlog);
        RemoteBuildInfo remoteBuildInfo = RemoteBuildInfo.parseRemoteBuildInfo(
                "rc:\n"
                        + "target:git_master-linux-full-eng\n"
                        + "bid:124\n"
                        + "files:git_master-linux-full-eng/124/BUILD_INFO,"
                        + "git_master-linux-full-eng/124/tests/FooTest.txt\n");

        try {
            mLogProvider.downloadBuildFiles(remoteBuildInfo, "test-target", "test-build",
                    mockDownloader);
            fail("Expected error not thrown.");
        } catch (BuildRetrievalError e) {
            IBuildInfo storedInfo = e.getBuildInfo();
            assertEquals("124", storedInfo.getBuildId());
            assertEquals("test-build", storedInfo.getBuildTargetName());
            assertEquals("test-target", storedInfo.getTestTag());
        }
    }

    @Test
    public void testGetLogPaths() throws Exception {
        RemoteBuildInfo buildInfo =
                RemoteBuildInfo.parseRemoteBuildInfo(
                        "rc:\n"
                                + "target:git_master-linux-full-eng\n"
                                + "bid:124\n"
                                + "files:git_master-linux-full-eng/124/BUILD_INFO,"
                                + "git_master-linux-full-eng/124/robotests/FooTest-test-output.txt,"
                                + "git_master-linux-full-eng/124/robotests/BarTest-test-output.txt\n");
        OptionSetter optionSetter = new OptionSetter(mLogProvider);
        optionSetter.setOptionValue("testlog-regex", "robotests/.*");
        List<String> expected = Arrays.asList(
                "git_master-linux-full-eng/124/robotests/FooTest-test-output.txt",
                "git_master-linux-full-eng/124/robotests/BarTest-test-output.txt");

        assertEquals(expected, mLogProvider.getLogPaths(buildInfo));
    }

    @Test
    public void testGetLogPathsEmpty() throws Exception {
        RemoteBuildInfo buildInfo =
                RemoteBuildInfo.parseRemoteBuildInfo(
                        "rc:\ntarget:git_master-linux-full-eng\nbid:124\nfiles:\n");
        OptionSetter optionSetter = new OptionSetter(mLogProvider);
        optionSetter.setOptionValue("testlog-regex", "robotests/.*");

        assertTrue(mLogProvider.getLogPaths(buildInfo).isEmpty());
    }

}
