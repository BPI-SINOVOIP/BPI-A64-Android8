// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import com.google.api.services.androidbuildinternal.model.BuildArtifactMetadata;
import com.google.api.services.androidbuildinternal.model.TestArtifactListResponse;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Functional tests for {@link AndroidBuildApiLogSaver}.
 * <p>
 * Depends on filesystem I/O and the Android Build Api.
 * </p>
 */
public class AndroidBuildApiLogSaverFuncTest extends TestCase {
    private static final String BUILD_ID = "3775817";
    private static final String BRANCH = "git_master";
    private static final String TEST_TAG = "CtsUnitTests_tfFuncTest";
    private static final String BUILD_FLAVOR = "test_suites_x86_64";
    private static final String BUILD_TARGET = "test_suites_x86_64_fastbuild3d_linux";
    private static final String BUILD_ATTEMPT_ID = "latest";
    private static final String BUILD_TYPE = "submitted";

    private AndroidBuildApiLogSaver mSaver;
    private IBuildInfo mMockBuild;
    private IInvocationContext mContext;
    private File mLogRootDir;
    private Map<String, String> mBuildAttributes;

    @Override
    protected void setUp() throws Exception {

        mMockBuild = EasyMock.createMock(IBuildInfo.class);
        EasyMock.expect(mMockBuild.getBuildBranch()).andReturn(BRANCH).anyTimes();
        EasyMock.expect(mMockBuild.getBuildId()).andReturn(BUILD_ID).anyTimes();
        EasyMock.expect(mMockBuild.getTestTag()).andReturn(TEST_TAG).anyTimes();
        EasyMock.expect(mMockBuild.getBuildFlavor()).andReturn(BUILD_FLAVOR).anyTimes();
        mBuildAttributes = new HashMap<>();
        mBuildAttributes.put("build_attempt_id", BUILD_ATTEMPT_ID);
        mBuildAttributes.put("build_type", BUILD_TYPE);
        mBuildAttributes.put("build_target", BUILD_TARGET);
        EasyMock.expect(mMockBuild.getBuildAttributes()).andReturn(mBuildAttributes).anyTimes();
        EasyMock.replay(mMockBuild);
        mContext = new InvocationContext();
        mContext.addDeviceBuildInfo("Device", mMockBuild);
        mLogRootDir = FileUtil.createTempDir("AndroidBuildApiLogSaverFuncTest");
        mSaver = new AndroidBuildApiLogSaver() {
            @Override
            void emitLogUploadMetric(int code, int attempt, long fileSize) {
            }
        };
        mSaver.setLogRootPath(mLogRootDir);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            cleanUpTestResult();
        } finally {
            FileUtil.recursiveDelete(mSaver.getLogStagingDir());
            FileUtil.recursiveDelete(mLogRootDir);
        }
    }

    /**
     * Cleanup the test artifacts.
     * @throws Exception
     */
    private void cleanUpTestResult() throws Exception {
        if (mSaver.getTestResultId() != null) {
            TestArtifactListResponse response = mSaver.getClient().testartifact().list(
                    mSaver.getBuildType(), mSaver.getBuildInfo().getBuildId(),
                    mSaver.getBuildTarget(), mSaver.getBuildAttemptId(),
                    mSaver.getTestResultId()).execute();
            List<BuildArtifactMetadata> artifacts = response.getTestArtifacts();
            if (artifacts != null) {
                for (BuildArtifactMetadata artifact : artifacts) {
                    mSaver.getClient()
                            .testartifact()
                            .delete(
                                    mSaver.getBuildType(),
                                    mSaver.getBuildInfo().getBuildId(),
                                    mSaver.getBuildTarget(),
                                    mSaver.getBuildAttemptId(),
                                    mSaver.getTestResultId(),
                                    artifact.getName());
                }
            }
            // TODO(xingdai): Delete the test result.
            // But right now build api doesn't provide a test result delete api.
        }
    }

    /**
     * Check log saver will create test result id if there is no attmpet id and build type.
     *
     * @throws Exception
     */
    public void testInvocationStarted_noAttemptId() throws Exception {
        mBuildAttributes.remove("build_type");
        mBuildAttributes.remove("build_attempt_id");

        try {
            mSaver.invocationStarted(mContext);
            assertNotNull(mSaver.getTestResultId());
        } finally {
            // TODO(xingdai): Delete the test result.
            // But right now build api doesn't provide a test result delete api.
        }
    }

    /**
     * Check that log will be uploaded to android build api.
     *
     * @throws Exception
     */
    public void testInvocationEnded() throws Exception {
        InputStream input = new ByteArrayInputStream("test".getBytes());
        InputStream emptyInput = new ByteArrayInputStream("".getBytes());
        List<BuildArtifactMetadata> artifacts = null;

        mSaver.setCompressFiles(true);
        mSaver.invocationStarted(mContext);
        mSaver.saveLogData("dataname", LogDataType.TEXT, input);
        mSaver.saveLogData("emptyfile", LogDataType.TEXT, emptyInput);

        LogFile file = mSaver.saveLogData("log#dataname", LogDataType.TEXT, input);
        assertMatchPattern(".*/log\\%23dataname_.*\\.gz", file.getUrl());
        mSaver.invocationEnded(0l);

        assertNotNull(mSaver.getTestResultId());
        // Verify the log is uploaded to the android build api.
        TestArtifactListResponse response = mSaver.getClient().testartifact().list(
                BUILD_TYPE, BUILD_ID, BUILD_TARGET,
                BUILD_ATTEMPT_ID, mSaver.getTestResultId()).execute();
        artifacts = response.getTestArtifacts();
        assertEquals(3, artifacts.size());

        assertMatchPattern(mSaver.getRemotePath() + "/dataname_.*\\.gz",
                artifacts.get(0).getName());
        assertMatchPattern(mSaver.getRemotePath() + "/emptyfile_.*\\.gz",
                artifacts.get(1).getName());
        assertMatchPattern(mSaver.getRemotePath() + "/log#dataname_.*\\.gz",
                artifacts.get(2).getName());
        assertFalse(mSaver.getLogStagingDir().exists());
    }

    /**
     * Check that LogSaver with no androidbuildinternal client will save the log locally, if
     * mLogLocallyOnError enabled.
     */
    public void testInvocationEnded_notUploaded() throws Exception {
        InputStream input = new ByteArrayInputStream("test".getBytes());
        mSaver.setCompressFiles(true);
        mSaver.setClientInitialized(true);
        mSaver.invocationStarted(mContext);
        mSaver.saveLogData("dataname", LogDataType.TEXT, input);
        mSaver.invocationEnded(0l);

        assertNull(mSaver.getTestResultId());
        assertTrue(mSaver.getLogStagingDir().exists());
        File[] logs = mSaver.getLogStagingDir().listFiles();
        assertEquals(1, logs.length);
        GZIPInputStream inputStream = new GZIPInputStream(new FileInputStream(logs[0]));
        assertEquals("test", StreamUtil.getStringFromStream(inputStream));
    }

    private void assertMatchPattern(String pattern, String str) {
        String message = String.format("%s doesn't match %s", str, pattern);
        assertTrue(message, str.matches(pattern));
    }

    /**
     * Check that html log will be uploaded to android build api.
     *
     * @throws Exception
     */
    public void testUpload_html() throws Exception {
        InputStream input = new ByteArrayInputStream(
                ("<html><head><title>hello world</title></head>"
                + "<body><h1>hello world</h1><p>42</p></body></html>").getBytes());
        List<BuildArtifactMetadata> artifacts = null;

        mSaver.invocationStarted(mContext);
        mSaver.saveLogData("dataname", LogDataType.HTML, input);
        mSaver.invocationEnded(0l);

        assertNotNull(mSaver.getTestResultId());
        // Verify the log is uploaded to the android build api.
        TestArtifactListResponse response = mSaver.getClient().testartifact().list(
                BUILD_TYPE, BUILD_ID, BUILD_TARGET,
                BUILD_ATTEMPT_ID, mSaver.getTestResultId()).execute();
        artifacts = response.getTestArtifacts();
        assertEquals(1, artifacts.size());

        assertMatchPattern(mSaver.getRemotePath() + "/dataname_.*\\.html",
                artifacts.get(0).getName());
        assertFalse(mSaver.getLogStagingDir().exists());
    }

    /**
     * Check that log will be uploaded to a stub build in android build api.
     *
     * @throws Exception
     */
    public void testInvocationEnded_stubBuild() throws Exception {
        InputStream input = new ByteArrayInputStream("test".getBytes());
        InputStream emptyInput = new ByteArrayInputStream("".getBytes());
        List<BuildArtifactMetadata> artifacts = null;

        mSaver.setUseStubBuild(true);
        mSaver.setCompressFiles(true);
        mSaver.invocationStarted(mContext);
        mSaver.saveLogData("dataname", LogDataType.TEXT, input);
        mSaver.saveLogData("emptyfile", LogDataType.TEXT, emptyInput);
        mSaver.invocationEnded(0l);

        assertNotNull(mSaver.getTestResultId());
        // Verify the log is uploaded to the android build api.
        TestArtifactListResponse response = mSaver.getClient().testartifact().list(
                BUILD_TYPE, AndroidBuildApiLogSaver.DEFAULT_STUB_BUILD_ID,
                AndroidBuildApiLogSaver.DEFAULT_STUB_BUILD_TARGET,
                BUILD_ATTEMPT_ID, mSaver.getTestResultId()).execute();
        artifacts = response.getTestArtifacts();
        assertEquals(2, artifacts.size());

        assertMatchPattern(mSaver.getRemotePath() + "/dataname_.*\\.gz",
                artifacts.get(0).getName());
        assertMatchPattern(mSaver.getRemotePath() + "/emptyfile_.*\\.gz",
                artifacts.get(1).getName());
        assertFalse(mSaver.getLogStagingDir().exists());
    }

    /**
     * Check if the file already exists in the Android Build API, will skip uploading the file.
     *
     * @throws Exception
     */
    public void testUpload_exist() throws Exception {
        InputStream input = new ByteArrayInputStream("test".getBytes());
        List<BuildArtifactMetadata> artifacts = null;

        mSaver.invocationStarted(mContext);
        mSaver.saveLogData("dataname", LogDataType.HTML, input);
        String filepath = mSaver.listStagedFiles(mSaver.getLogStagingDir())[0];
        // Upload the file.
        mSaver.uploadFileToAndroidBuildApi(filepath);

        // The invocationEnded will try to upload the file again.
        mSaver.invocationEnded(0l);
        assertNotNull(mSaver.getTestResultId());

        // Verify the log is uploaded to the android build api.
        TestArtifactListResponse response = mSaver.getClient().testartifact().list(
                BUILD_TYPE, BUILD_ID, BUILD_TARGET,
                BUILD_ATTEMPT_ID, mSaver.getTestResultId()).execute();
        artifacts = response.getTestArtifacts();
        assertEquals(1, artifacts.size());

        assertMatchPattern(mSaver.getRemotePath() + "/dataname_.*\\.html",
                artifacts.get(0).getName());
        assertFalse(mSaver.getLogStagingDir().exists());
    }

    /**
     * Check if the file already exists in the Android Build API but with a different md5, the old
     * file will be deleted and the new file be uploaded.
     *
     * @throws Exception
     */
    public void testUpload_existDifferentMd5() throws Exception {
        InputStream input = new ByteArrayInputStream(("test").getBytes());
        List<BuildArtifactMetadata> artifacts = null;

        mSaver.invocationStarted(mContext);
        mSaver.saveLogData("dataname", LogDataType.HTML, input);
        String filepath = mSaver.listStagedFiles(mSaver.getLogStagingDir())[0];
        // Upload the file.
        mSaver.uploadFileToAndroidBuildApi(filepath);

        // Modify the file.
        File file = new File(filepath);
        FileUtil.writeToFile("another test", file);

        // The invocationEnded will try to upload the file again.
        mSaver.invocationEnded(0l);
        assertNotNull(mSaver.getTestResultId());

        // Verify the log is uploaded to the android build api.
        TestArtifactListResponse response = mSaver.getClient().testartifact().list(
                BUILD_TYPE, BUILD_ID, BUILD_TARGET,
                BUILD_ATTEMPT_ID, mSaver.getTestResultId()).execute();
        artifacts = response.getTestArtifacts();
        assertEquals(1, artifacts.size());

        assertMatchPattern(mSaver.getRemotePath() + "/dataname_.*\\.html",
                artifacts.get(0).getName());
        assertEquals(
                StreamUtil.calculateMd5(new ByteArrayInputStream(
                        "another test".getBytes())),
                artifacts.get(0).getMd5());
        assertFalse(mSaver.getLogStagingDir().exists());
    }
}
