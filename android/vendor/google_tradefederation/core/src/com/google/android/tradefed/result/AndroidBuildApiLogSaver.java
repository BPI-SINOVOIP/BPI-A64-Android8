// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.LogFileSaver;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.android.tradefed.util.hostmetric.IHostMetricAgent;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.androidbuildinternal.Androidbuildinternal;
import com.google.api.services.androidbuildinternal.model.BuildArtifactMetadata;
import com.google.api.services.androidbuildinternal.model.TestResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class that saves log files to the Android Build API.
 */
@OptionClass(alias = "android-build-api-log-saver")
public class AndroidBuildApiLogSaver implements ILogSaver {
    private static final String ANDROID_BUILD_API_PATHSEP = "/";
    private static final String BUILD_TYPE = "build_type";
    private static final String BUILD_TYPE_PENDING = "pending";
    private static final String BUILD_TYPE_SUBMITTED = "submitted";
    private static final String BUILD_ATTEMPT_ID = "build_attempt_id";
    private static final String DEFAULT_BUILD_ATTEMPT_ID = "0";
    private static final String BUILD_TARGET = "build_target";
    private static final String TEST_RESULT_ID = "test_result_id";
    private static final String APP_NAME = "tradefed.AndroidBuildApiLogSaver";
    private static final int DEFAULT_MAX_UPLOAD_ATTEMPTS = 3;
    private static final int INIT_ATTEMPTS_WAIT_TIME = 500; // ms
    private static final int SUCCESS_CODE = 200;
    private static final int UNKNOWN_CODE = -1;
    private static final int READ_TIMEOUT = 5; // minutes
    static final String URL_TEMPLATE =
            "https://android-build.googleplex.com/builds/%s/%s/%s/%s/tests/%s/";
    private static final Collection<String> ACCESS_SCOPE = Collections
            .singleton("https://www.googleapis.com/auth/androidbuild.internal");
    // Key file path to access the Android Build API.
    private static final String KEY_FILE_PATH = "/google/data/ro/teams/tradefed/configs/"
            + "e006c97067dbad4ecff47a965821946d2d04be57-privatekey.p12";
    // Service account to access the Android Build API.
    private static final String SERVICE_ACCOUNT =
            "717267004052-o19a1ouu8025j2iumfkniem897nu9gj8@developer.gserviceaccount.com";

    // Some test invocation (special subprocess invocation) do not have full build infos.
    // Without full build infos, uploading to build api may fail. Should use stub build infos
    // in this case.
    static final String DEFAULT_STUB_BUILD_ID = "3136476";
    static final String DEFAULT_STUB_BUILD_TARGET = "build";
    static final String DEFAULT_STUB_BUILD_BRANCH = "ub-treehugger-prod";

    @Option(name = "build-api-key-file-path", description = "Key file used to access build apiary."
            + "Checkout go/android-test-infrastructure/android-build-api"
            + " to see how to create the key file.")
    private String mBuildApiKeyFilePath = KEY_FILE_PATH;

    @Option(name = "build-api-service-account", description = "Service account used to access build"
            + " apiary. Checkout go/android-test-infrastructure/android-build-api"
            + " to see how to create the service account.")
    private String mBuildApiServiceAccount = SERVICE_ACCOUNT;

    @Option(name = "compress-files",
            description = "whether to compress files which are not already compressed")
    private boolean mCompressFiles = false;

    @Option(name = "log-root-path", description = "root local path to hold logfiles " +
            "during the invocation.  Files will be moved from here to android build api after " +
            "the invocation completes.")
    private File mLogRootPath = new File(System.getProperty("java.io.tmpdir"),
            "stage-android-build-api");

    @Option(name = "remove-staged-files",
            description = "Whether to remove staged log files after " +
            "they were successfully exported to Android Build API.")
    private boolean mRemoveStagedFiles = true;

    @Option(name = "max-upload-attempts",
            description = "Max attempts to upload files to the Android Build API.")
    private int mMaxUploadAttempts = DEFAULT_MAX_UPLOAD_ATTEMPTS;

    /**
     * Current this will be set by ATP if the test comes from ATP. It will be empty if the test
     * doesn't come from ATP.
     */
    @Option(name = "test-result-id",
            description = "Test result id in the Android Build API to save logs.")
    private Long mTestResultId = null;

    @Option(name = "use-stub-build",
            description = "Report the test result to a stub build or not.")
    private boolean mUseStubBuild = false;

    @Option(name = "stub-build-id",
            description = "Use this build id if use stub build is enabled.")
    private String mStubBuildId = DEFAULT_STUB_BUILD_ID;

    @Option(name = "stub-build-target",
            description = "Use this build target if use stub build is enabled.")
    private String mStubBuildTarget = DEFAULT_STUB_BUILD_TARGET;

    @Option(name = "stub-build-branch",
            description = "Use this build branch if use stub build is enabled.")
    private String mStubBuildBranch = DEFAULT_STUB_BUILD_BRANCH;

    /** The base path used for showing in android build api. */
    private String mRemotePath = null;
    /** Generated local staging directory for log files */
    private File mLogStagingDir = null;
    /** a {link {@link LogFileSaver} to save the log locally. */
    private LogFileSaver mLogFileSaver = null;
    /**
     * A counter to control access to methods which modify this class's directories. Acting as a
     * non-blocking reentrant lock, this int blocks access to sharded child invocations from
     * attempting to create or delete directories.
     */
    private int mShardingLock = 0;

    private Androidbuildinternal mClient;
    private boolean mClientInitialized;
    private IBuildInfo mBuildInfo;
    private IHostMetricAgent mMetricAgent;

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IInvocationContext context) {
        IBuildInfo buildInfo = context.getBuildInfos().get(0);
        if (mUseStubBuild) {
            buildInfo = new BuildInfo();
            buildInfo.setBuildFlavor(mStubBuildTarget);
            buildInfo.setBuildId(mStubBuildId);
            buildInfo.setBuildBranch(mStubBuildBranch);
            buildInfo.addBuildAttribute(BUILD_TARGET, mStubBuildTarget);
            // The provided test result id will be the one for actual build,
            // not for stub build.
            mTestResultId = null;
        }
        setBuildInfo(buildInfo);
        setTestResultId(fetchTestResultId(context));
        initLogSaver(buildInfo);
    }

    /**
     * Initialize the log saver.
     *
     * @param buildInfo
     */
    private void initLogSaver(IBuildInfo buildInfo) {
        synchronized (this) {
            if (mShardingLock == 0) {
                mLogFileSaver = new LogFileSaver(buildInfo, getLogRootPath());
                mRemotePath = generateAndroidBuildApiPath(
                        mLogFileSaver.getInvocationLogPathSegments());
                setLogStagingDir(mLogFileSaver.getFileDir());
            }
            mShardingLock++;
        }
    }

    /**
     * Fetch test result id. If currently invocation has a test result id, use that one. Otherwise,
     * create a test result in android build api and use its id.
     *
     * @param context
     * @return test result id
     */
    Long fetchTestResultId(IInvocationContext context) {
        // Test comes from ATP should already have the test result id.
        if (mTestResultId != null) {
            return mTestResultId;
        }

        Long testResultId = null;
        if (context != null && !mUseStubBuild) {
            List<String> testResultIds = context.getAttributes().get(TEST_RESULT_ID);
            if (testResultIds != null && !testResultIds.isEmpty()) {
                String testResultIdStr = testResultIds.get(0);
                try {
                    testResultId = Long.parseLong(testResultIdStr);
                    return testResultId;
                } catch (NumberFormatException e) {
                    CLog.e("%s is not a valid test result id, will create a new one",
                            testResultIdStr);
                    CLog.e(e);
                }
            }
        }
        testResultId = createTestResult();
        if (context != null && testResultId != null && !mUseStubBuild) {
            // Do not store test result id in context, since it's for stub build.
            context.addInvocationAttribute(TEST_RESULT_ID, String.valueOf(testResultId));
        }
        return testResultId;
    }

    /**
     * Create a test result in Android Build Api.
     *
     * @return test result's id
     */
    Long createTestResult() {
        if (getClient() == null) {
            return null;
        }
        TestResult testResult = new TestResult();
        testResult.setTestTag(getBuildInfo().getTestTag());

        try {
            testResult = getClient().testresult().insert(
                    getBuildInfo().getBuildId(),
                    getBuildTarget(),
                    getBuildAttemptId(),
                    testResult).execute();
            return testResult.getId();
        } catch (IOException e) {
            CLog.e("Failed to create test result, will not upload logs to build api.");
            CLog.e(e);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        if (!shouldUpload()) {
            return;
        }

        final String[] stagedPaths = listStagedFiles(getLogStagingDir());
        if (stagedPaths == null) {
            // It is uncommon that we don't even have a host log to save, so log something
            CLog.w("No log files were saved; skipping export to build api.");
            return;
        }

        for (String stagedPath : stagedPaths) {
            try {
                uploadFileToAndroidBuildApiWithMultiAttempts(stagedPath);
                if (mRemoveStagedFiles) {
                    FileUtil.deleteFile(new File(stagedPath));
                }
            } catch (IOException e) {
                CLog.e("Failed to upload %s to android build api.", stagedPath);
                String message = e.getMessage();
                if (!Strings.isNullOrEmpty(message)) {
                    // Outputting the whole message is too verbose.
                    // The first line have a brief error message.
                    CLog.e(message.split("\n", 2)[0]);
                }
            }
        }
        synchronized (this) {
            if (--mShardingLock == 0) {
                if (mRemoveStagedFiles && listStagedFiles(getLogStagingDir()).length == 0) {
                    FileUtil.recursiveDelete(getLogStagingDir());
                }
            }
            if (mShardingLock < 0) {
                CLog.w("Sharding lock exited more times than entered, possible " +
                        "unbalanced invocationStarted/Ended calls");
            }
        }
    }

    /**
     * Check if the log saver should upload the log files. Used for testing.
     *
     * @return should upload
     */
    boolean shouldUpload() {
        if (getTestResultId() == null) {
            CLog.w("There is no test result id for this invocation, can not upload tests files " +
                    "to android build api. The logs are saved under %s.", getLogStagingDir());
            return false;
        }

        if (getClient() == null) {
            CLog.w("No android build api client set, can not upload tests files to " +
                    "android build api. The logs are saved under %s.", getLogStagingDir());
            return false;
        }
        return true;
    }

    /**
     * Upload staged file to android build api with multiple attempts.
     *
     * @param filePath the staged file path
     * @throws IOException
     */
    void uploadFileToAndroidBuildApiWithMultiAttempts(String filePath) throws IOException {
        IOException exception = null;
        int waitTimeMS = INIT_ATTEMPTS_WAIT_TIME;
        int statusCode = UNKNOWN_CODE;
        long fileSize = new File(filePath).length();
        for (int i = 1; i <= mMaxUploadAttempts; ++i) {
            try {
                uploadFileToAndroidBuildApi(filePath);
                statusCode = SUCCESS_CODE;
                return;
            } catch (IOException e) {
                exception = e;
                String message = e.getMessage();
                if (e instanceof GoogleJsonResponseException) {
                    GoogleJsonResponseException respException = (GoogleJsonResponseException) e;
                    statusCode = respException.getStatusCode();
                    // Get the scotty id for debugging purpose.
                    String value = respException.getHeaders()
                            .getFirstHeaderStringValue("x-guploader-uploadid");
                    if (!Strings.isNullOrEmpty(value)) {
                        CLog.w("More info at https://go/scottydash/lookup.html?uploadId=%s", value);
                    } else {
                        CLog.w("There is no \"x-guploader-uploadid\" in the response.");
                    }
                    message = respException.getStatusMessage();
                }
                CLog.w("Failed to upload file %s to the Android Build API due to %s in attempt %d.",
                        filePath, message, i);
                if (i < mMaxUploadAttempts) {
                    CLog.w("Wait %dms for another retry.", waitTimeMS);
                    getRunUtil().sleep(waitTimeMS);
                    waitTimeMS *= 2;
                }
            } finally {
                emitLogUploadMetric(statusCode, i, fileSize);
            }
        }
        throw exception;
    }

    /**
     * Upload staged file to android build api.
     *
     * @param filePath the staged file path
     * @throws IOException
     */
    void uploadFileToAndroidBuildApi(String filePath) throws IOException {
        BuildArtifactMetadata metadata = new BuildArtifactMetadata();
        File f = new File(filePath);
        if (f.length() <= 0) {
            // Android Build API doesn't accept empty file
            FileUtil.writeToFile("empty", f);
        }
        BuildArtifactMetadata artifact = getTestArtifact(getRemoteFilePath(f.getName()));
        if (artifact != null) {
            String md5 = FileUtil.calculateMd5(f);
            if (md5.equals(artifact.getMd5())) {
                CLog.i("File %s with same md5 exists in Android Build Api."
                        + " Skip uploading.", filePath);
                return;
            } else {
                CLog.w("File %s with different md5 exists in Android Build Api.\n"
                        + "Delete the file in Android Build Api and reupload.", filePath);
                deleteTestArtifact(getRemoteFilePath(f.getName()));
            }
        }
        FileContent fileContent = new FileContent(FileUtil.getContentType(filePath),
                new File(filePath));
        getClient().testartifact().update(
                getBuildType(),
                getBuildInfo().getBuildId(),
                getBuildTarget(),
                getBuildAttemptId(),
                getTestResultId(),
                getRemoteFilePath(f.getName()),
                metadata,
                fileContent).execute();
    }

    /**
     * Get test artifact for a file from Android Build API.
     *
     * @param resourceId test artifact's resource id
     * @throws IOException
     */
    BuildArtifactMetadata getTestArtifact(String resourceId) throws IOException {
        try {
            return getClient().testartifact().get(
                    getBuildType(),
                    getBuildInfo().getBuildId(),
                    getBuildTarget(),
                    getBuildAttemptId(),
                    getTestResultId(),
                    resourceId).execute();
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == 404) {
                CLog.i("File %s doesn't exist in Android Build API. Expected.", resourceId);
                return null;
            } else {
                throw e;
            }
        }
    }

    /**
     * Delete a test artifact from Android Build API.
     *
     * @param resourceId test artifact's resource id
     * @throws IOException
     */
    void deleteTestArtifact(String resourceId) throws IOException {
        getClient().testartifact().delete(
                getBuildType(),
                getBuildInfo().getBuildId(),
                getBuildTarget(),
                getBuildAttemptId(),
                getTestResultId(),
                resourceId).execute();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LogFile saveLogData(String dataName, LogDataType dataType, InputStream dataStream)
            throws IOException {
        if (!mCompressFiles || dataType.isCompressed()) {
            File log = mLogFileSaver.saveLogData(dataName, dataType, dataStream);
            return new LogFile(getRemoteFilePath(log.getName()), getRemoteFileUrl(log.getName()),
                    dataType.isCompressed(), dataType.isText());
        }
        File log = mLogFileSaver.saveAndGZipLogData(dataName, dataType, dataStream);
        return new LogFile(getRemoteFilePath(log.getName()), getRemoteFileUrl(log.getName()),
                true /* compressed */, dataType.isText());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LogFile saveLogDataRaw(String dataName, String ext, InputStream dataStream)
            throws IOException {
        File log = mLogFileSaver.saveLogDataRaw(dataName, ext, dataStream);
        return new LogFile(getRemoteFilePath(log.getName()), getRemoteFileUrl(log.getName()),
                false, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LogFile getLogReportDir() {
        return new LogFile(getRemoteFilePath(""), getRemoteFileUrl(""), false, false);
    }

    /**
     * Generate base path in android build api, used as prefix of resource id for testaritifact api.
     *
     * @param invPathSegments
     * @return path in android build api.
     */
    String generateAndroidBuildApiPath(List<String> invPathSegments) {
        return String.join(ANDROID_BUILD_API_PATHSEP, invPathSegments.toArray(new String[]{}));
    }

    /**
     * List the files which are staged for upload to android build api.
     * <p />
     * Exposed for unit testing
     */
    String[] listStagedFiles(File parentDir) {
        final File[] files = parentDir.listFiles();
        if (files == null) {
            return null;
        }

        final String[] paths = new String[files.length];
        for (int i = 0; i < files.length; ++i) {
            paths[i] = files[i].getPath();
        }

        return paths;
    }

    /**
     * A helper method that returns a URL for a given file name.
     *
     * @param fileName the filename of the log
     * @return A URL that should allow visitors to view the logfile with the specified name
     */
    String getRemoteFileUrl(String fileName) {
        String baseUrl = String.format(URL_TEMPLATE,
                getBuildType(),
                getBuildInfo().getBuildId(),
                getBuildTarget(),
                getBuildAttemptId(),
                String.valueOf(getTestResultId()));
        fileName = sanitizeUrlFileName(fileName);
        return baseUrl + getRemoteFilePath(fileName);
    }

    /**
     * A helper function that replace the # with %23.
     * # is a special character in url, need to escape it.
     * Url encode will also replace character like ".", do not use it here.
     */
    private static String sanitizeUrlFileName(String filename) {
        return filename.replace("#", "%23");
    }

    /**
     * A helper method that returns the remote path for a given file name.
     *
     * @param fileName the filename of the log
     * @return remote path as an resource id of the log file in the Android Build API.
     */
    String getRemoteFilePath(String fileName) {
        return String.format("%s%s%s", mRemotePath, ANDROID_BUILD_API_PATHSEP, fileName);
    }

    /**
     * Get the Androidbuildinternal client.
     * @return a Androidbuildinternal
     */
    Androidbuildinternal getClient() {
        if (mClientInitialized) {
            return mClient;
        }
        synchronized (this) {
            if (!mClientInitialized) {
                try {
                    mClientInitialized = true;
                    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
                    HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                    GoogleCredential credential = new GoogleCredential.Builder()
                            .setTransport(httpTransport)
                            .setJsonFactory(jsonFactory)
                            .setServiceAccountId(mBuildApiServiceAccount)
                            .setServiceAccountScopes(ACCESS_SCOPE)
                            .setServiceAccountPrivateKeyFromP12File(new File(mBuildApiKeyFilePath))
                            .build();
                    Androidbuildinternal client = new Androidbuildinternal.Builder(
                            httpTransport, jsonFactory, setHttpTimeout(credential))
                                    .setApplicationName(APP_NAME)
                                    .build();
                    setClient(client);
                } catch (GeneralSecurityException | IOException e) {
                    CLog.e("Failed to create Android Build Api client, will save log locally");
                    CLog.e(e);
                }
            }
        }
        return mClient;
    }

    /**
     * Set http timeout.
     *
     * @param requestInitializer
     * @return a {@link HttpRequestInitializer} with custom time out
     */
    private HttpRequestInitializer setHttpTimeout(final HttpRequestInitializer requestInitializer) {
        return new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest httpRequest) throws IOException {
                requestInitializer.initialize(httpRequest);
                httpRequest.setReadTimeout(READ_TIMEOUT * 60 * 1000);
            }
        };
    }

    /**
     * Send metric.
     *
     * @param code
     * @param attempt
     */
    void emitLogUploadMetric(int code, int attempt, long fileSize) {
        Map<String, String> data = new HashMap<>();
        data.put("response_code", String.valueOf(code));
        data.put("attempt", String.valueOf(attempt));
        data.put("client", AndroidBuildApiLogSaver.class.getCanonicalName());
        data.put("server", "AndroidBuildApi");
        data.put("build_branch", getBuildInfo().getBuildBranch());
        data.put("build_flavor", getBuildInfo().getBuildFlavor());
        int logFileSize = 0;
        if (fileSize > 0) {
            logFileSize = (int) (Math.log(fileSize) / Math.log(2));
        }
        data.put("log_file_size", String.valueOf(logFileSize));
        if (getMetricAgent() != null) {
            try {
                getMetricAgent().emitValue(IHostMetricAgent.HTTP_REQUEST_METRIC, 1, data);
            } catch (Exception e) {
                // Sending metrics failed should not blocking logs uploading.
                CLog.w("Failed to sending metrics.");
                CLog.w(e.getMessage());
            }
        } else {
            CLog.w("There is no host metric agent configured");
        }
    }

    /**
     * Get metric agent to send log metric
     *
     * @return a {@link IHostMetricAgent}
     */
    private IHostMetricAgent getMetricAgent() {
        if (mMetricAgent == null) {
            mMetricAgent = (IHostMetricAgent) GlobalConfiguration.getInstance()
                    .getConfigurationObject("host_metric_agent");
        }
        return mMetricAgent;
    }

    /**
     * Get build attempt id for the current build.
     * @return build attempt id
     */
    String getBuildAttemptId() {
        String buildAttemptId = getBuildInfo().getBuildAttributes().get(BUILD_ATTEMPT_ID);
        if (buildAttemptId == null) {
            return DEFAULT_BUILD_ATTEMPT_ID;
        }
        return buildAttemptId;
    }

    /**
     * Get build type.
     * @return build type.
     */
    String getBuildType() {
        String buildType = getBuildInfo().getBuildAttributes().get(BUILD_TYPE);
        if (buildType == null) {
            if (getBuildInfo().getBuildId().startsWith("P")) {
                return BUILD_TYPE_PENDING;
            }
            return BUILD_TYPE_SUBMITTED;
        }
        return buildType;
    }

    /**
     * Get build target. If there is no build target attribute, return build flavor.
     * @return build target.
     */
    String getBuildTarget() {
        String buildTarget = getBuildInfo().getBuildAttributes().get(BUILD_TARGET);
        if (buildTarget == null) {
            String build_flavor = getBuildInfo().getBuildFlavor();
            CLog.w("No build target, fall back to build flavor %s", build_flavor);
            return build_flavor;
        }
        return buildTarget;
    }

    /**
     * Set the Androidbuildinternal client. Exposed for testing.
     *
     * @param client
     */
    void setClient(Androidbuildinternal client) {
        mClient = client;
    }

    /**
     * Get IBuildInfo. Exposed for testing.
     */
    IBuildInfo getBuildInfo() {
        return mBuildInfo;
    }

    /**
     * Set IBuildInfo. Exposed for testing.
     *
     * @param buildInfo
     */
    void setBuildInfo(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * Get test result id. Exposed for testing.
     *
     * @return test result id.
     */
    Long getTestResultId() {
        return mTestResultId;
    }

    /**
     * Set test result id. Exposed for testing.
     *
     * @param testResultId
     */
    void setTestResultId(Long testResultId) {
        mTestResultId = testResultId;
    }

    /**
     * Set log root path Exposed for testing.
     *
     * @param logRootPath
     */
    void setLogRootPath(File logRootPath) {
        mLogRootPath = logRootPath;
    }

    /**
     * Get log root path Exposed for testing.
     *
     * @return the log root path
     */
    File getLogRootPath() {
        return mLogRootPath;
    }

    /**
     * Set log staging dir. Exposed for testing.
     *
     * @param logStagingDir
     */
    void setLogStagingDir(File logStagingDir) {
        mLogStagingDir = logStagingDir;
    }

    /**
     * Get log staging dir. Exposed for testing.
     *
     * @return the log staing dir.
     */
    File getLogStagingDir() {
        return mLogStagingDir;
    }

    /**
     * Get remote path. Exposed for testing.
     *
     * @return the remote path
     */
    String getRemotePath() {
        return mRemotePath;
    }

    /**
     * Set compress. Exposed for testing.
     *
     * @param compress
     */
    void setCompressFiles(boolean compress) {
        mCompressFiles = compress;
    }

    /**
     * Set use stub build. Exposed for testing.
     *
     * @param useStubBuild
     */
    void setUseStubBuild(boolean useStubBuild) {
        mUseStubBuild = useStubBuild;
    }

    /**
     * Get the log file saver. Exposed for testing.
     *
     * @return log file saver
     */
    LogFileSaver getLogFilerSaver() {
        return mLogFileSaver;
    }

    /**
     * @return the default RunUtil.
     */
    @VisibleForTesting
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Set the client initialized flag. Exposed for testing.
     *
     * @param clientInitialized
     */
    void setClientInitialized(boolean clientInitialized) {
        mClientInitialized = clientInitialized;
    }
}
