// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.FileSystemLogSaver;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Functional tests for {@link ColossusLogSaver}.
 * <p>
 * Depends on filesystem I/O.
 * </p>
 */
public class ColossusLogSaverFuncTest extends TestCase {
    private static final String BUILD_ID = "88888";
    private static final String BRANCH = "somebranch";
    private static final String TEST_TAG = "sometest";

    private File mReportDir = null;
    private File mStagingDir = null;
    private IBuildInfo mMockBuild = null;

    private IInvocationContext mStubContext = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mReportDir = FileUtil.createTempDir("reportdir");
        mStagingDir = FileUtil.createTempDir("stagingdir");

        mMockBuild = EasyMock.createMock(IBuildInfo.class);
        EasyMock.expect(mMockBuild.getBuildBranch()).andReturn(BRANCH).anyTimes();
        EasyMock.expect(mMockBuild.getBuildId()).andReturn(BUILD_ID).anyTimes();
        EasyMock.expect(mMockBuild.getTestTag()).andReturn(TEST_TAG).anyTimes();
        EasyMock.replay(mMockBuild);

        mStubContext = new InvocationContext();
        mStubContext.addDeviceBuildInfo("STUB_DEVICE", mMockBuild);
    }

    @Override
    protected void tearDown() throws Exception {
        FileUtil.recursiveDelete(mReportDir);
        FileUtil.recursiveDelete(mStagingDir);
        super.tearDown();
    }

    /**
     * Test saving uncompressed data for
     * {@link FileSystemLogSaver#saveLogData(String, LogDataType, InputStream)} when compress-files
     * is unset.
     */
    public void testSaveLogData_uncompressed() throws IOException, ConfigurationException {
        File realLogFile = null;
        LogFile logFile = null;
        BufferedReader logFileReader = null;
        try {
            // TODO: would be nice to create a mock file output to make this test not use disk I/O
            ColossusLogSaver saver = new FakeColossusLogSaver();
            OptionSetter setter = new OptionSetter(saver);
            setter.setOptionValue("compress-files", "false");
            setter.setOptionValue("log-file-path", mReportDir.getPath());
            setter.setOptionValue("log-file-staging-path", mStagingDir.getPath());
            saver.invocationStarted(mStubContext);

            final String testData = "Here's some test data, blah";
            ByteArrayInputStream mockInput = new ByteArrayInputStream(testData.getBytes());
            logFile = saver.saveLogData("testSaveLogData", LogDataType.TEXT, mockInput);
            assertTrue(logFile.getPath().endsWith(LogDataType.TEXT.getFileExt()));
            realLogFile = new File(logFile.getPath());

            saver.invocationEnded(0);

            // Verify test data was written to file
            logFileReader = new BufferedReader(new FileReader(realLogFile));
            String actualLogString = logFileReader.readLine().trim();
            assertEquals(testData, actualLogString);
        } finally {
            StreamUtil.close(logFileReader);
            FileUtil.deleteFile(realLogFile);
        }
    }

    /**
     * Test saving uncompressed data for
     * {@link FileSystemLogSaver#saveLogData(String, LogDataType, InputStream)} when compress-files
     * is set.
     */
    public void testSaveLogData_compressed_uncompressed() throws IOException,
            ConfigurationException {
        File realLogFile = null;
        LogFile logFile = null;
        GZIPInputStream gzipStream = null;
        try {
            // TODO: would be nice to create a mock file output to make this test not use disk I/O
            ColossusLogSaver saver = new FakeColossusLogSaver();
            OptionSetter setter = new OptionSetter(saver);
            setter.setOptionValue("compress-files", "true");
            setter.setOptionValue("log-file-path", mReportDir.getPath());
            setter.setOptionValue("log-file-staging-path", mStagingDir.getPath());
            saver.invocationStarted(mStubContext);

            final String testData = "Here's some test data, blah";
            ByteArrayInputStream mockInput = new ByteArrayInputStream(testData.getBytes());
            logFile = saver.saveLogData("testSaveLogData", LogDataType.TEXT, mockInput);
            assertTrue(logFile.getPath().endsWith(LogDataType.GZIP.getFileExt()));
            realLogFile = new File(logFile.getPath());

            saver.invocationEnded(0);

            // Verify test data was written to file
            gzipStream = new GZIPInputStream(new FileInputStream(realLogFile));
            String actualLogString = StreamUtil.getStringFromStream(gzipStream);
            assertEquals(testData, actualLogString);
        } finally {
            StreamUtil.close(gzipStream);
            FileUtil.deleteFile(realLogFile);
        }
    }

    /**
     * Test saving compressed data for
     * {@link FileSystemLogSaver#saveLogData(String, LogDataType, InputStream)} when compress-files
     * is set.
     */
    public void testSaveLogData_compressed_compressed() throws IOException, ConfigurationException {
        File realLogFile = null;
        LogFile logFile = null;
        BufferedReader logFileReader = null;
        try {
            // TODO: would be nice to create a mock file output to make this test not use disk I/O
            ColossusLogSaver saver = new FakeColossusLogSaver();
            OptionSetter setter = new OptionSetter(saver);
            setter.setOptionValue("compress-files", "true");
            setter.setOptionValue("log-file-path", mReportDir.getPath());
            setter.setOptionValue("log-file-staging-path", mStagingDir.getPath());
            saver.invocationStarted(mStubContext);

            final String testData = "Here's some test data, blah";
            ByteArrayInputStream mockInput = new ByteArrayInputStream(testData.getBytes());
            logFile = saver.saveLogData("testSaveLogData", LogDataType.ZIP, mockInput);
            realLogFile = new File(logFile.getPath());

            saver.invocationEnded(0);

            // Verify test data was written to file
            logFileReader = new BufferedReader(new FileReader(realLogFile));
            String actualLogString = logFileReader.readLine().trim();
            assertEquals(testData, actualLogString);
        } finally {
            StreamUtil.close(logFileReader);
            FileUtil.deleteFile(realLogFile);
        }
    }

    /**
     * Simple normal case test for
     * {@link FileSystemLogSaver#saveLogDataRaw(String, String, InputStream)}.
     */
    public void testSaveLogDataRaw() throws IOException, ConfigurationException {
        File realLogFile = null;
        LogFile logFile = null;
        BufferedReader logFileReader = null;
        try {
            // TODO: would be nice to create a mock file output to make this test not use disk I/O
            ColossusLogSaver saver = new FakeColossusLogSaver();
            OptionSetter setter = new OptionSetter(saver);
            setter.setOptionValue("log-file-path", mReportDir.getPath());
            setter.setOptionValue("log-file-staging-path", mStagingDir.getPath());
            saver.invocationStarted(mStubContext);

            final String testData = "Here's some test data, blah";
            ByteArrayInputStream mockInput = new ByteArrayInputStream(testData.getBytes());
            logFile = saver.saveLogDataRaw("testSaveLogData", LogDataType.TEXT.getFileExt(),
                    mockInput);
            realLogFile = new File(logFile.getPath());

            saver.invocationEnded(0);

            // Verify test data was written to file
            logFileReader = new BufferedReader(new FileReader(realLogFile));
            String actualLogString = logFileReader.readLine().trim();
            assertEquals(testData, actualLogString);
        } finally {
            StreamUtil.close(logFileReader);
            FileUtil.deleteFile(realLogFile);
        }
    }

    class FakeColossusLogSaver extends ColossusLogSaver {
        @Override
        List<String[]> getCopyCmds(String destDirPath, String[] srcPaths) {
            final CommandResult result = getRunUtil().runTimedCmd(100, "mkdir", "-p", destDirPath);
            assertEquals(CommandStatus.SUCCESS, result.getStatus());

            final List<String> copyCmd = new ArrayList<>();
            copyCmd.add("cp");
            copyCmd.add("-t");
            copyCmd.add(destDirPath);
            copyCmd.addAll(Arrays.asList(srcPaths));
            final List<String[]> cmds = new ArrayList<>();
            cmds.add(copyCmd.toArray(new String[copyCmd.size()]));
            return cmds;
        }
    }
}
