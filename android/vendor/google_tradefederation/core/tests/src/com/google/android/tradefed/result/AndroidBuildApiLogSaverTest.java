// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException.Builder;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Unit tests for {@link AndroidBuildApiLogSaver}.
 * <p>
 * Depends on filesystem I/O.
 * </p>
 */
public class AndroidBuildApiLogSaverTest {
    private static final String BUILD_ID = "88888";
    private static final String BRANCH = "somebranch";
    private static final String TEST_TAG = "sometest";
    private static final String BUILD_FLAVOR = "someflavor";
    private static final String BUILD_ATTEMPT_ID = "attempt_id";
    private static final String BUILD_TYPE = "build_type";
    private static final String BUILD_TARGET = "build_target";
    private static final String TEST_RESULT_ID_KEY = "test_result_id";
    private static final Long TEST_RESULT_ID = 12345L;
    private static final String REMOTE_PATH_PATTERN = "somebranch/88888/sometest/inv_.*";

    private AndroidBuildApiLogSaver mSaver;
    private IBuildInfo mMockBuild = null;
    private IInvocationContext mContext = null;
    private File mLogRootDir;
    final private List<String> mUploadedFiles = new LinkedList<>();
    private Boolean mShouldUpload;
    private Map<String, String> mBuildAttributes = new HashMap<String, String>();
    private Boolean mAllowUpload;
    private List<Integer> mMetricCode;
    private List<Integer> mMetricAttempt;
    private List<Long> mMetricFileSize;

    @Before
    public void setUp() throws Exception {

        mMockBuild = EasyMock.createMock(IBuildInfo.class);
        EasyMock.expect(mMockBuild.getBuildBranch()).andReturn(BRANCH).anyTimes();
        EasyMock.expect(mMockBuild.getBuildId()).andReturn(BUILD_ID).anyTimes();
        EasyMock.expect(mMockBuild.getTestTag()).andReturn(TEST_TAG).anyTimes();
        EasyMock.expect(mMockBuild.getBuildFlavor()).andReturn(BUILD_FLAVOR).anyTimes();
        mBuildAttributes.put("build_attempt_id", BUILD_ATTEMPT_ID);
        mBuildAttributes.put("build_type", BUILD_TYPE);
        mBuildAttributes.put("build_target", BUILD_TARGET);
        EasyMock.expect(mMockBuild.getBuildAttributes()).andReturn(mBuildAttributes).anyTimes();
        EasyMock.replay(mMockBuild);
        mContext = new InvocationContext();
        mContext.addDeviceBuildInfo("Device", mMockBuild);

        mShouldUpload = false;
        mAllowUpload = true;
        mMetricCode = new ArrayList<>();
        mMetricAttempt = new ArrayList<>();
        mMetricFileSize = new ArrayList<>();

        mLogRootDir = FileUtil.createTempDir("AndroidBuildApiLogSaverTest");
        mSaver =
                new AndroidBuildApiLogSaver() {
                    @Override
                    public void invocationStarted(IInvocationContext context) {
                        super.invocationStarted(context);
                        // simulate the context getting locked after invocation started.
                        ((InvocationContext) context).lockAttributes();
                    }

                    @Override
                    Long createTestResult() {
                        return TEST_RESULT_ID;
                    }

                    @Override
                    void uploadFileToAndroidBuildApi(String path) throws IOException {
                        if (mAllowUpload) {
                            mUploadedFiles.add(path);
                        } else {
                            throw new GoogleJsonResponseException(
                                    new Builder(409, "error message", new HttpHeaders()), null);
                        }
                    }

                    @Override
                    boolean shouldUpload() {
                        return mShouldUpload;
                    }

                    @Override
                    File getLogRootPath() {
                        return mLogRootDir;
                    }

                    @Override
                    void emitLogUploadMetric(int code, int attempt, long fileSize) {
                        mMetricCode.add(code);
                        mMetricAttempt.add(attempt);
                        mMetricFileSize.add(fileSize);
                    }
                };
        mAllowUpload = true;
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mLogRootDir);
    }

    @Test
    public void testGenerateAndroidBuildApiPath() {
        String remotePath = mSaver.generateAndroidBuildApiPath(
                Arrays.asList(new String[]{"a", "b", "c"}));
        assertEquals("a/b/c", remotePath);
    }

    @Test
    public void testInvocationStarted() throws Exception {
        mSaver.invocationStarted(mContext);

        assertEquals(mMockBuild, mSaver.getBuildInfo());
        assertEquals(TEST_RESULT_ID, mSaver.getTestResultId());
        assertMatchPattern(mSaver.getRemotePath(), REMOTE_PATH_PATTERN);
        assertMatchPattern(mSaver.getLogStagingDir().getPath(),
                mLogRootDir.getPath() + "/" + REMOTE_PATH_PATTERN);
    }

    @Test
    public void testInvocationStarted_stubBuild() throws Exception {
        mSaver.setUseStubBuild(true);
        mSaver.setTestResultId(1L); // This test result id will not be used.
        // This test result id will not be used.
        mContext.addInvocationAttribute(TEST_RESULT_ID_KEY, "2");
        mSaver.invocationStarted(mContext);

        IBuildInfo buildInfo = mSaver.getBuildInfo();
        assertEquals(AndroidBuildApiLogSaver.DEFAULT_STUB_BUILD_ID, buildInfo.getBuildId());
        assertEquals(AndroidBuildApiLogSaver.DEFAULT_STUB_BUILD_BRANCH, buildInfo.getBuildBranch());
        assertEquals(AndroidBuildApiLogSaver.DEFAULT_STUB_BUILD_TARGET,
                buildInfo.getBuildAttributes().get("build_target"));
        assertEquals(TEST_RESULT_ID, mSaver.getTestResultId());
        String remotePathPattern = String.format("%s/%s/stub/inv_.*",
                AndroidBuildApiLogSaver.DEFAULT_STUB_BUILD_BRANCH,
                AndroidBuildApiLogSaver.DEFAULT_STUB_BUILD_ID);
        assertMatchPattern(mSaver.getRemotePath(), remotePathPattern);
        assertMatchPattern(mSaver.getLogStagingDir().getPath(),
                mLogRootDir.getPath() + "/" + remotePathPattern);
        // The test result id will not be override in context.
        assertEquals("2", mContext.getAttributes().get(TEST_RESULT_ID_KEY).get(0));
    }

    @Test
    public void testInvocationStarted_withTestResultId() throws Exception {
        mSaver.setTestResultId(1L);
        mSaver.invocationStarted(mContext);

        assertEquals(mMockBuild, mSaver.getBuildInfo());
        assertEquals(Long.valueOf(1L), mSaver.getTestResultId());
        assertMatchPattern(mSaver.getRemotePath(), REMOTE_PATH_PATTERN);
        assertMatchPattern(mSaver.getLogStagingDir().getPath(),
                mLogRootDir.getPath() + "/" + REMOTE_PATH_PATTERN);
    }

    @Test
    public void testInvocationStarted_withContext_withTestResultId() throws Exception {
        mContext.addInvocationAttribute(TEST_RESULT_ID_KEY, "2");
        mSaver.invocationStarted(mContext);

        assertEquals(Long.valueOf(2), mSaver.getTestResultId());
        assertEquals(mMockBuild, mSaver.getBuildInfo());
        assertMatchPattern(mSaver.getRemotePath(), REMOTE_PATH_PATTERN);
        assertMatchPattern(mSaver.getLogStagingDir().getPath(),
                mLogRootDir.getPath() + "/" + REMOTE_PATH_PATTERN);
    }

    @Test
    public void testInvocationStarted_withContext_noTestResultId() throws Exception {
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("Device", mMockBuild);

        mSaver.invocationStarted(context);

        assertEquals(TEST_RESULT_ID, mSaver.getTestResultId());
        assertEquals("12345", context.getAttributes().get(TEST_RESULT_ID_KEY).get(0));
        assertEquals(mMockBuild, mSaver.getBuildInfo());
        assertMatchPattern(mSaver.getRemotePath(), REMOTE_PATH_PATTERN);
        assertMatchPattern(mSaver.getLogStagingDir().getPath(),
                mLogRootDir.getPath() + "/" + REMOTE_PATH_PATTERN);
    }

    @Test
    public void testGetPath() throws Exception {
        mSaver.invocationStarted(mContext);
        String path = mSaver.getRemoteFilePath("filename");
        assertMatchPattern(path,
                REMOTE_PATH_PATTERN + "/filename");
    }

    @Test
    public void testGetUrl() throws Exception {
        mSaver.invocationStarted(mContext);
        String url = mSaver.getRemoteFileUrl("filename");
        assertMatchPattern(url,
                "https://android-build.googleplex.com/builds/build_type/88888/"
                        + "build_target/attempt_id/tests/12345/" + REMOTE_PATH_PATTERN
                        + "/filename");
    }

    @Test
    public void testGetBuildTarget() throws Exception {
        mSaver.invocationStarted(mContext);
        String build_target = mSaver.getBuildTarget();
        assertEquals(BUILD_TARGET, build_target);
    }

    @Test
    public void testGetBuildTarget_noBuildTarget() throws Exception {
        mSaver.invocationStarted(mContext);
        mBuildAttributes.remove("build_target");
        String build_target = mSaver.getBuildTarget();
        assertEquals(BUILD_FLAVOR, build_target);
    }

    @Test
    public void testSaveLogData() throws Exception {
        InputStream input = new ByteArrayInputStream("test".getBytes());

        mSaver.setCompressFiles(false);
        mSaver.invocationStarted(mContext);
        LogFile logFile = mSaver.saveLogData("dataname", LogDataType.TEXT, input);
        assertTrue(logFile.isText());
        assertFalse(logFile.isCompressed());
        assertMatchPattern(logFile.getPath(), mSaver.getRemoteFilePath("dataname_.*\\.txt"));
        assertMatchPattern(logFile.getUrl(), mSaver.getRemoteFileUrl("dataname_.*\\.txt"));
        File[] logs = mSaver.getLogStagingDir().listFiles();
        assertEquals(1, logs.length);
        assertEquals("test", FileUtil.readStringFromFile(logs[0]));
    }

    @Test
    public void testSaveLogData_filenameWithHash() throws Exception {
        InputStream input = new ByteArrayInputStream("test".getBytes());

        mSaver.setCompressFiles(false);
        mSaver.invocationStarted(mContext);
        LogFile logFile = mSaver.saveLogData("log#dataname", LogDataType.TEXT, input);
        assertTrue(logFile.isText());
        assertFalse(logFile.isCompressed());
        assertMatchPattern(logFile.getPath(), mSaver.getRemoteFilePath("log#dataname_.*\\.txt"));
        assertMatchPattern(logFile.getUrl(), mSaver.getRemoteFileUrl("log#dataname_.*\\.txt"));
        assertMatchPattern(logFile.getUrl(), ".*/log\\%23dataname_.*\\.txt");
        File[] logs = mSaver.getLogStagingDir().listFiles();
        assertEquals(1, logs.length);
        assertEquals("test", FileUtil.readStringFromFile(logs[0]));
    }

    @Test
    public void testSaveLogData_compress() throws Exception {
        InputStream input = new ByteArrayInputStream("test".getBytes());

        mSaver.setCompressFiles(true);
        mSaver.invocationStarted(mContext);
        LogFile logFile = mSaver.saveLogData("dataname", LogDataType.TEXT, input);

        assertTrue(logFile.isText());
        assertTrue(logFile.isCompressed());
        assertMatchPattern(logFile.getPath(), mSaver.getRemoteFilePath("dataname_.*\\.gz"));
        assertMatchPattern(logFile.getUrl(), mSaver.getRemoteFileUrl("dataname_.*\\.gz"));

        File[] logs = mSaver.getLogStagingDir().listFiles();
        assertEquals(1, logs.length);
        GZIPInputStream inputStream = new GZIPInputStream(new FileInputStream(logs[0]));
        String uncompressed = StreamUtil.getStringFromStream(
                inputStream);
        assertEquals("test", uncompressed);
    }

    @Test
    public void testSaveLogDataRaw() throws Exception {
        InputStream input = new ByteArrayInputStream("test".getBytes());

        mSaver.invocationStarted(mContext);

        LogFile logFile = mSaver.saveLogDataRaw("dataname", "txt", input);
        assertMatchPattern(logFile.getPath(), mSaver.getRemoteFilePath("dataname_.*\\.txt"));
        assertMatchPattern(logFile.getUrl(), mSaver.getRemoteFileUrl("dataname_.*\\.txt"));
        File[] logs = mSaver.getLogStagingDir().listFiles();
        assertEquals(1, logs.length);
        assertEquals(FileUtil.readStringFromFile(logs[0]), "test");
    }

    @Test
    public void testInvocationEnded() throws Exception {
        InputStream input = new ByteArrayInputStream("test".getBytes());

        mShouldUpload = true;
        mSaver.setCompressFiles(false);
        mSaver.invocationStarted(mContext);
        mSaver.saveLogData("dataname", LogDataType.TEXT, input);
        mSaver.invocationEnded(0l);

        assertEquals(1, mUploadedFiles.size());
        assertMatchPattern(mUploadedFiles.get(0),
                mSaver.getLogStagingDir() + "/dataname_.*\\.txt");
        // Uploaded file should be deleted.
        assertFalse(new File(mUploadedFiles.get(0)).exists());
        assertArrayEquals(new Integer[] {
                200
        }, mMetricCode.toArray(new Integer[0]));
        assertArrayEquals(new Integer[] {
                1
        }, mMetricAttempt.toArray(new Integer[0]));
        assertArrayEquals(new Long[] {
                4l
        }, mMetricFileSize.toArray(new Long[0]));
    }

    @Test
    public void testInvocationEnded_notUpload() throws Exception {
        InputStream input = new ByteArrayInputStream("test".getBytes());

        mShouldUpload = false;

        mSaver.setCompressFiles(false);
        mSaver.invocationStarted(mContext);
        mSaver.saveLogData("dataname", LogDataType.TEXT, input);
        mSaver.invocationEnded(0l);

        assertTrue(mUploadedFiles.isEmpty());
        // Non-uploaded files should still be there in the local folder.
        File[] logs = mSaver.getLogStagingDir().listFiles();
        assertEquals(1, logs.length);
        assertEquals(FileUtil.readStringFromFile(logs[0]), "test");
        assertArrayEquals(new Integer[0], mMetricCode.toArray(new Integer[0]));
        assertArrayEquals(new Integer[0], mMetricAttempt.toArray(new Integer[0]));
        assertArrayEquals(new Integer[0], mMetricFileSize.toArray(new Integer[0]));
    }

    private void assertMatchPattern(String str, String pattern) {
        String message = String.format("%s doesn't match %s", str, pattern);
        assertTrue(message, str.matches(pattern));
    }

    @Test
    public void testUploadFileToAndroidBuildApiWithMultiAttempts() throws Exception {
        final Integer[] count = new Integer[]{0};
        final List<Long> sleepTimes = new ArrayList<Long>();
        AndroidBuildApiLogSaver saver = new AndroidBuildApiLogSaver() {
            @Override
            void uploadFileToAndroidBuildApi(String path) throws IOException {
                count[0]++;
                throw new GoogleJsonResponseException(
                        new Builder(409, "error message", new HttpHeaders()), null);
            }

            @Override
            IRunUtil getRunUtil() {
                return new RunUtil() {
                    @Override
                    public void sleep(long time) {
                        sleepTimes.add(time);
                    }
                };
            }

            @Override
            void emitLogUploadMetric(int code, int attempt, long fileSize) {
                mMetricCode.add(code);
                mMetricAttempt.add(attempt);
                mMetricFileSize.add(fileSize);
            }
        };
        try {
            saver.uploadFileToAndroidBuildApiWithMultiAttempts("file");
            assert false;
        } catch (IOException e) {
            // expected
        }
        assertEquals(3, (int) count[0]);
        assertEquals(2, sleepTimes.size());
        assertEquals(500, (long)sleepTimes.get(0));
        assertEquals(1000, (long) sleepTimes.get(1));
        assertArrayEquals(new Integer[] {
                409, 409, 409
        }, mMetricCode.toArray(new Integer[0]));
        assertArrayEquals(new Integer[] {
                1, 2, 3
        }, mMetricAttempt.toArray(new Integer[0]));
    }
}
