// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.androidbuildinternal.Androidbuildinternal;
import com.google.api.services.androidbuildinternal.model.TestResult;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.InvocationStatus;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.targetprep.BuildError;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Reports test results to Android Build API.
 */
@OptionClass(alias = "build-api-test-result-reporter")
public class AndroidBuildResultReporter extends CollectingTestListener
        implements ITestSummaryListener {
    public static final String STATUS_IN_PROGRESS = "inProgress";
    public static final String STATUS_PASS = "completePass";
    public static final String STATUS_FAIL = "completeFail";
    public static final String STATUS_ERROR = "error";
    static final String BUILD_ATTEMPT_ID = "build_attempt_id";
    static final String DEFAULT_BUILD_ATTEMPT_ID = "0";
    private static final String BUILD_TARGET = "build_target";
    static final String TEST_RESULT_ID = "test_result_id";
    private static final String APP_NAME = "tradefed.AndroidBuildResultReporter";
    static final String KEY_FILE_PATH = "/google/data/ro/teams/tradefed/configs/"
            + "e006c97067dbad4ecff47a965821946d2d04be57-privatekey.p12";
    private static final String STOCK_ERROR_MSG = "\n\nError during test run.";
    // Account name
    // Corresponds to this account/project:
    // https://console.developers.google.com/project/apps~android-test-infra/apiui/credential'
    static final String SERVICE_ACCOUNT = "717267004052-o19a1ouu8025j2iumfkniem897nu9gj8@developer.gserviceaccount.com";
    private static final Collection<String> ACCESS_SCOPE = Collections
            .singleton("https://www.googleapis.com/auth/androidbuild.internal");

    @Option(name = "build-api-key-file-path", description = "Key file used to access build apiary."
            + "Checkout go/android-test-infrastructure/android-build-api"
            + " to see how to create the key file.")
    private String mBuildApiKeyFilePath = KEY_FILE_PATH;

    @Option(name = "build-api-service-account",
            description = "Service account used to access build apiary."
            + "Checkout go/android-test-infrastructure/android-build-api"
            + " to see how to create the service account.")
    private String mBuildApiServiceAccount = SERVICE_ACCOUNT;

    @Option(name = "test-result-id", description = "Test result id is used to report test results"
            + " to build API. If the test result id is provided in command options, it is implied"
            + " that the test was requested from ATP and ATP will report the result back.")
    private Long mTestResultId = null;

    private Androidbuildinternal mClient = null;
    private InvocationStatus mStatus = InvocationStatus.SUCCESS;
    private String mSummaryUrl = null;
    private boolean mShouldReport = true;
    private TestResult mTestResult = null;

    public AndroidBuildResultReporter() {
        try {
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            GoogleCredential credential = new GoogleCredential.Builder()
                    .setTransport(httpTransport)
                    .setJsonFactory(jsonFactory)
                    .setServiceAccountId(mBuildApiServiceAccount)
                    .setServiceAccountScopes(ACCESS_SCOPE)
                    .setServiceAccountPrivateKeyFromP12File(new File(mBuildApiKeyFilePath))
                    .build();
            mClient = new Androidbuildinternal.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName(APP_NAME)
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            CLog.e("Failed to create android build api client,"
                    + "can't report the test result to the Android Build API");
            CLog.e(e);
        }
    }

    /**
     * Create a new AndroidBuildResultReporter. Exposed for testing.
     *
     * @param client
     */
    AndroidBuildResultReporter(Androidbuildinternal client) {
        mClient = client;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putSummary(List<TestSummary> summaries) {
        if (summaries.size() > 0) {
            mSummaryUrl = summaries.get(0).getSummary().getString();
        }
    }

    /**
     * Get test result from the Android Build API.
     *
     * @param buildInfo build information
     * @param id test result id
     * @return a test result
     * @throws IOException
     */
    TestResult getTestResult(IBuildInfo buildInfo, Long id) throws IOException {
        return mClient.testresult().get(
                buildInfo.getBuildId(),
                getBuildTarget(buildInfo),
                getBuildAttemptId(buildInfo),
                id).execute();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IInvocationContext context) {
        super.invocationStarted(context);
        if (mTestResultId != null) {
            // If the command option contains a test result id, it means this is an ATP test
            // invocation. Since ATP will be reporting the Android Build API, disable to avoid
            // duplicate reporting
            mShouldReport = false;
            return;
        }
        // This result reporter is always based on the primary build info.
        IBuildInfo buildInfo = context.getBuildInfos().get(0);
        List<String> testResultIds = context.getAttributes().get(TEST_RESULT_ID);
        // Test result id in context, this test result id is not created by ATP.
        if (testResultIds != null && !testResultIds.isEmpty()) {
            try {
                mTestResultId = Long.parseLong(testResultIds.get(0));
                mTestResult = getTestResult(buildInfo, mTestResultId);
                initTestResult(mTestResult, context.getTestTag());
                updateTestResult(buildInfo);
                return;
            } catch (NumberFormatException e) {
                CLog.e("Can't parse %s to Long.", testResultIds.get(0));
                CLog.e(e);
            } catch (IOException e) {
                CLog.e("Failed to get test result %s", mTestResultId);
                CLog.e(e);
            }
            mShouldReport = false;
            return;
        }
        createTestResult(buildInfo, context.getTestTag());
        if (mTestResultId != null) {
            context.addInvocationAttribute(TEST_RESULT_ID, String.valueOf(mTestResultId));
        }
    }

    /**
     * Create a new test result in the Android Build API.
     *
     * @param buildInfo
     */
    void createTestResult(IBuildInfo buildInfo, String testTag) {
        TestResult testResult = new TestResult();
        initTestResult(testResult, testTag);
        try {
            mTestResult = doCreateTestResult(buildInfo, testResult);
            mTestResultId = mTestResult.getId();
        } catch (IOException e) {
            CLog.e("Failed to create test result in Android Build API.");
            CLog.e(e);
            mShouldReport = false;
        }
    }

    /**
     * Actually create test result in the Android Build API. For testing.
     *
     * @param buildInfo
     * @param testResult
     * @return a test result
     * @throws IOException
     */
    TestResult doCreateTestResult(IBuildInfo buildInfo, TestResult testResult) throws IOException {
        return mClient.testresult().insert(
                buildInfo.getBuildId(),
                getBuildTarget(buildInfo),
                getBuildAttemptId(buildInfo),
                testResult).execute();
    }

    /**
     * Initialize a test result
     *
     * @param testResult a test result
     * @param testTag the test tag
     */
    void initTestResult(TestResult testResult, String testTag) {
        testResult.setStatus(STATUS_IN_PROGRESS);
        testResult.setTestTag(testTag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        super.invocationFailed(cause);

        if (cause instanceof BuildError) {
            mStatus = InvocationStatus.BUILD_ERROR;
        } else {
            mStatus = InvocationStatus.FAILED;
        }
        mStatus.setThrowable(cause);
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);
        if (!mShouldReport) {
            return;
        }

        // Summarize test results.
        final StringBuilder summary = new StringBuilder(summarizeTestResult());

        switch (mStatus) {
            case SUCCESS:
                // Need to set to STATUS_FAIL if test fails even with invocation
                // failed.
                if (hasFailedTests()) {
                    mTestResult.setStatus(STATUS_FAIL);
                } else {
                    mTestResult.setStatus(STATUS_PASS);
                }
                break;

            // FIXME: differentiate between BUILD ERROR and FAILED
            case BUILD_ERROR:
            case FAILED:
                mTestResult.setStatus(STATUS_ERROR);
                // Append additional error messages to summary.
                summary.append(STOCK_ERROR_MSG);
                if (mStatus.getThrowable() != null) {
                    summary.append("\n");
                    summary.append(mStatus.getThrowable().toString());
                }
                break;

            default:
                CLog.w("Should never have reach this status. %s", mStatus.toString());
                break;
        }
        mTestResult.setSummary(summary.toString());
        try {
            // This result reporter only reports on primary build info.
            updateTestResult(getPrimaryBuildInfo());
        } catch (IOException e) {
            CLog.e("Failed to update test result %s in the Android Build API.",
                    mTestResultId.toString());
            CLog.e(e);
            mShouldReport = false;
        }
    }

    /**
     * Post results to the Build API
     *
     * @param buildInfo
     * @throws IOException
     */
    void updateTestResult(IBuildInfo buildInfo) throws IOException {
        if (!mShouldReport) {
            return;
        }
        mTestResult = doUpdateTestResult(buildInfo, mTestResult);
    }

    /**
     * Actually post result to the Android Build API. Exposed for testing.
     *
     * @param buildInfo
     * @param testResult
     * @return a test result
     * @throws IOException
     */
    TestResult doUpdateTestResult(IBuildInfo buildInfo, TestResult testResult) throws IOException {
        return mClient.testresult().update(
                buildInfo.getBuildId(),
                getBuildTarget(buildInfo),
                getBuildAttemptId(buildInfo),
                mTestResultId,
                testResult).execute();
    }

    /**
     * Return a human-readable explanation of the results. Will be displayed in
     * a web UI, so may contain hyperlinks.
     */
    String summarizeTestResult() {
        StringBuilder sum = new StringBuilder(String.format(
                "Ran %d tests.  %d failed.",
                getNumTotalTests(), getNumAllFailedTests()));
        if (mSummaryUrl != null) {
            sum.append("\n");
            sum.append("For more details see: ");
            sum.append(mSummaryUrl);
        }
        return sum.toString();
    }

    /**
     * Get build attempt id for the current build.
     * @return build attempt id
     */
    private String getBuildAttemptId(IBuildInfo buildInfo) {
        String buildAttemptId = buildInfo.getBuildAttributes().get(BUILD_ATTEMPT_ID);
        if (buildAttemptId == null) {
            return DEFAULT_BUILD_ATTEMPT_ID;
        }
        return buildAttemptId;
    }

    /**
     * Get build target. If there is no build target attribute, return build flavor.
     * @return build target.
     */
    private String getBuildTarget(IBuildInfo buildInfo) {
        String buildTarget = buildInfo.getBuildAttributes().get(BUILD_TARGET);
        if (buildTarget == null) {
            String build_flavor = buildInfo.getBuildFlavor();
            CLog.w("No build target, fall back to build flavor %s", build_flavor);
            return build_flavor;
        }
        return buildTarget;
    }

    /**
     * Exposed for testing.
     *
     * @return the mTestResultId
     */
    Long getTestResultId() {
        return mTestResultId;
    }

    /**
     * Exposed for testing.
     *
     * @return the mTestResult
     */
    TestResult getTestResult() {
        return mTestResult;
    }

    /**
     * Exposed for testing.
     *
     * @return should report
     */
    Boolean getShouldReport() {
        return mShouldReport;
    }

    /**
     * Set should report. Exposed for testing.
     *
     * @param shouldReport
     */
    void setShouldReport(Boolean shouldReport) {
        mShouldReport = shouldReport;
    }
}
