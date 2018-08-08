// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IGlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.InvocationStatus;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.VersionParser;
import com.android.tradefed.util.net.HttpHelper;
import com.android.tradefed.util.net.IHttpHelper;

import com.google.android.tradefed.util.PerfUtility;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import org.kxml2.io.KXmlSerializer;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * Writes invocation test results to a XML file in a format that can be uploaded with the
 * Sponge HTTP-api, which accepts an XML representation of a sponge protobuf. See
 * http://s.corp.google.com/?fileprint=//depot/google3/testing/metricstore/proto/sponge.proto
 * <p/>
 * Collects all test info in memory, then dumps to file when invocation is complete.
 * <p/>
 * Based on tradefed XmlResultReporter, which was ported from dalvik runner XmlReportPrinter.
 * <p/>
 * Result files will be stored in path constructed via [--output-file-path]/[build_id]
 */
@OptionClass(alias = "sponge")
public class SpongeResultReporter extends AbstractRemoteResultReporter implements ILogSaverListener {

    private static final String LOG_TAG = "SpongeResultReporter";
    private static final Set<String> INVOCATION_ATTRIBUTE_EXCLUSION = new HashSet<>();
    static {
        // TODO: Store this in an invocation metadata class later
        INVOCATION_ATTRIBUTE_EXCLUSION.add("command_line_args");
    }

    static final String BACKEND_QA = "backend-qa";

    private static final int HTTP_POLL_TIME_MS = 5* 1000;

    @Option(name="sponge-backend", description="sponge backend to use: 'backend' or 'backend-qa'.")
    private String mSpongeBackend = "backend";

    @Option(name="sponge-httpapi-server", description="server to upload Sponge results to.")
    private String mSpongeApiServer = "sponge-http.appspot.com";

    @Option(name="sponge-httpapi-port", description="port to use when uploading Sponge results." +
            " Use -1 to indicate 'default for this protocol'.")
    private int mSpongeApiPort = -1;  // -1 == "default for this protocol"

    // TODO: this doesn't seem to get passed to sponge correctly
    @Option(name="sponge-size", description="the size of the test run to report to sponge. " +
            "Valid values 0 through 5.")
    private String mTestSize = null;

    @Option(name="sponge-label", description="the sponge label(s) to associate with invocation " +
            "result.")
    private Collection<String> mLabels = new ArrayList<>();

    @Option(
        name = "only-send-testlog-index",
        description =
                "For logs under Build Logs tab, upload only an index of test logfiles, instead of "
                        + "full snippets for each log."
    )
    private boolean mSendTestLogIndex = false;

    @Option(name = "use-google-file-pointer", description = "upload a pointer to the google file " +
            "in cns instead of uploading a snippet and a link.")
    private boolean mUseGoogleFilePointer = false;

    @Option(
        name = "duplicate-as-build-logs",
        description =
                "Upload all logs under Build Logs tab, besides Output Files tab of corresponding "
                        + "target."
    )
    private boolean mDuplicateAsBuildLogs = true;

    @Option(
            name = "save-test-results",
            description =
                    "Write run metrics and test metrics results to a file and upload"
                    + " it in sponge."
        )
    private boolean mSaveTestResults = false;

    /**
     * The maximum size in bytes of the testLog snippet uploaded as large_text to Sponge.
     *
     * <p/>The size limit of large_text field is 1MB. A small size is picked to reduce request size.
     */
    static final int MAX_UPLOAD_FILE_SIZE = 96 * 1024;

    /**
     * The maximum size in bytes of requests sent to Sponge HTTP API.
     *
     * <p/>The size limit of Sponge HTTP API request is 32 MB, so pick a size below that.
     */
    static final int MAX_REQUEST_SIZE = 31 * 1024 * 1024;

    /**
     * Whether report results to Sponge HTTP API using multiple updates.
     *
     * <p/>This is set to true if the invocation exceeds size limit. The the invocation will be
     * uploaded using multiple updates.
     */
    private boolean mMultiUpdate = false;

    /** The XML namespace */
    private static final String NS = null;

    private ILogSaver mLogSaver = null;

    /** The map of log data names to log data to send to Sponge "Build Logs" tab */
    private MultiMap<String, LogFileData> mLogDataMap = new MultiMap<>();

    /**
     * The map of target names to maps of log data names to log data to send to Sponge target
     * "Output Files" tab
     */
    private Map<String, MultiMap<String, LogFileData>> mTargetLogDataMap = new HashMap<>();

    private String mInvocationId;

    /** If the invocation fails, we'll see an invocationFailed call */
    private InvocationStatus mStatus = InvocationStatus.SUCCESS;

    private int mMaxHttpAttempts = 3;

    /** The current run name. This is used by tests to know the current run. */
    private String mCurrentRunName = "";

    /**
     * Whether or not to include the snippet in the log reporting. Snippet is sometimes causing the
     * invocation to fail posting.
     */
    private boolean mReportSnippet = true;

    /** Implement Sponge's "Status" enum; see protobuf for details */
    public enum SpongeStatus {
        PASSED("0"),
        FAILED("1"),
        CANCELLED_BY_USER("2"),
        ABORTED_BY_TOOL("3"),
        FAILED_TO_BUILD("4"),
        BUILT("5"),
        PENDING("6"),
        UNKNOWN_STATUS("7"),
        INTERNAL_ERROR("8");

        private final String value;

        SpongeStatus(String value) {
            this.value = value;
        }
    }

    /** Implement Sponge's "TargetResult.Type" enum; see protobuf for details */
    public enum SpongeTargetType {
        UNSPECIFIED_TYPE("0"),
        TEST("1"),
        BINARY("2"),
        LIBRARY("3");

        private final String value;

        SpongeTargetType(String value) {
            this.value = value;
        }
    }

    /**
     * Set the sponge backend to use.
     */
    @VisibleForTesting
    void setSpongeBackend(String spongeBackend) {
        mSpongeBackend = spongeBackend;
    }

    void setTestSize(String size) {
        mTestSize = size;
    }

    /**
     * Set the maximum number of http post attempts to Sponge.
     */
    @VisibleForTesting
    void setMaxHttpAttempts(int attempts) {
        mMaxHttpAttempts = attempts;
    }

    /**
     * Set whether to upload an index of test logfiles to Build Logs tab instead of full snippets
     * for each log.
     */
    @VisibleForTesting
    void setSendTestLogIndex(boolean val) {
        mSendTestLogIndex = val;
    }

    /** Set whether to duplicate all logs as Build logs. */
    @VisibleForTesting
    void setDuplicateAsBuildLogs(boolean val) {
        mDuplicateAsBuildLogs = val;
    }

    /** {@inheritDoc} */
    @Override
    public void testRunStarted(String name, int numTests) {
        super.testRunStarted(name, numTests);
        mCurrentRunName = name;
    }

    /** {@inheritDoc} */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        super.testRunEnded(elapsedTime, runMetrics);
        mCurrentRunName = "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);
        if (mSaveTestResults) {
            uploadTestMetricInSponge();
        }
        generateReport(elapsedTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable e) {
        if (e instanceof BuildError) {
            mStatus = InvocationStatus.BUILD_ERROR;
        } else {
            mStatus = InvocationStatus.FAILED;
        }
        mStatus.setThrowable(e);
    }

    /**
     * Fetches current timestamp
     * @return The current time, in milliseconds, since the UNIX epoch
     */
    @VisibleForTesting
    long getTimestamp() {
        return (new Date()).getTime();
    }

    /**
     * Grabs an output stream and populates it with the report data from the completed tests.
     *
     * @param elapsedTime the elapsed time of invocation in ms
     */
    private void generateReport(long elapsedTime) {
        long timestamp = getTimestamp();
        String testInfo =
                String.format(
                        "build %s, test target %s. Invocation %s. Total tests: %d, Failed: %d. ",
                        getPrimaryBuildInfo().getBuildId(),
                        getTestTag(),
                        getInvocationStatusDescription(mStatus),
                        getNumTotalTests(),
                        getNumAllFailedTests());
        mInvocationId = null;
        try {
            for (int attempts = 0;
                    attempts < mMaxHttpAttempts && mInvocationId == null;
                    attempts++) {
                escalatingSleep(attempts);
                mInvocationId = createSpongeInvocation(elapsedTime, timestamp);
                // retry without the snippet, only the link to full log
                if (mInvocationId == null) {
                    CLog.d("Failed to post, next attempt will be without full snippets.");
                    mReportSnippet = false;
                }
            }

            String msg = null;
            if (mInvocationId != null) {
                msg =
                        String.format(
                                "Stored invocation results in sponge for %s Url: %s",
                                testInfo, getInvocationUrl(mInvocationId));

                // After invocation is successfully created, update the invocation if needed.
                updateInvocationWithResults(mInvocationId, timestamp);
            } else {
                // If reporting results to sponge failed, output the location of the logs.
                msg =
                        String.format(
                                "Failed to store invocation results in sponge for %s Url: %s",
                                testInfo, mLogSaver.getLogReportDir().getPath());
                notifyReportError("sponge", msg);
            }
            Log.logAndDisplay(LogLevel.INFO, LOG_TAG, msg);
        } finally {
            // Clean up some of the data that are held.
            cleanUp();
        }
    }

    /**
     * Get the {@link IRunUtil} instance to use.
     * <p/>
     * Exposed so unit tests can mock.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Create an invocation in sponge.
     *
     * @param elapsedTime
     * @param timestamp
     * @return the created invocation id or <code>null</code>
     */
    @VisibleForTesting
    String createSpongeInvocation(long elapsedTime, long timestamp) {
        ByteArrayOutputStream requestOutputStream = null;
        ByteArrayOutputStream gzipByteArrayOutputStream = null;
        InputStream logInputStream = null;
        InputStream httpInputStream = null;
        String invocationId = null;
        try {
            // Create uncompressed XML request.
            requestOutputStream = generateCreateSpongeInvocationRequest(elapsedTime, timestamp);

            // Send invocation using multiple updates instead if the uncompressed request size
            // exceeds the size limit.
            if (requestOutputStream.size() > getMaxRequestSize()) {
                CLog.i("Results for build %s test %s are too large to create in one http request. "
                        + "Splitting into multiple updates",
                        getPrimaryBuildInfo().getBuildId(), getTestTag());

                mMultiUpdate = true;
                requestOutputStream.close();
                requestOutputStream = generateCreateSpongeInvocationRequest(elapsedTime, timestamp);
            }

            // Compress request XML into gzip before send to Sponge HTTP API.
            gzipByteArrayOutputStream = getCompressedStream(requestOutputStream);

            final String dataName = "sponge_create_invocation";
            final String ext = String.format(
                    "%s.%s", LogDataType.XML.getFileExt(), LogDataType.GZIP.getFileExt());

            // Save the gzipped request in case uploading to sponge fails.
            logInputStream = new ByteArrayInputStream(gzipByteArrayOutputStream.toByteArray());
            LogFile log = mLogSaver.saveLogDataRaw(dataName, ext, logInputStream);
            CLog.i("Saved gzip of create invocation xml to %s", log.getPath());

            // Send the gzipped request to Sponge HTTP API. The response will be invocation Id.
            httpInputStream = new ByteArrayInputStream(gzipByteArrayOutputStream.toByteArray());
            invocationId = sendDataToHttp("/create_invocation", httpInputStream);
        } catch (IOException e) {
            CLog.w("Failed to create Sponge Invocation. Reason: '%s'. Log location: %s",
                    e.toString(), mLogSaver.getLogReportDir().getPath());
            CLog.e(e);
            // TODO: consider throwing exception
        } finally {
            StreamUtil.close(requestOutputStream);
            StreamUtil.close(gzipByteArrayOutputStream);
            StreamUtil.close(logInputStream);
            StreamUtil.close(httpInputStream);
        }
        return invocationId;
    }

    /**
     * Generate the request for creating an Sponge invocation.
     *
     * @param elapsedTime
     * @param timestamp
     */
    private ByteArrayOutputStream generateCreateSpongeInvocationRequest(
            long elapsedTime, long timestamp) {
        ByteArrayOutputStream byteArrayOutputStream = null;
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            KXmlSerializer serializer = new KXmlSerializer();

            prepareAndEmitXmlHeader(byteArrayOutputStream, serializer);
            emitTestResults(serializer, timestamp, mStatus, elapsedTime);
            emitXmlFooter(serializer);
        } catch (IOException e) {
            CLog.w("Failed to generate report data. Reason: '%s'.", e.toString());
            CLog.e(e);
        } finally {
            StreamUtil.close(byteArrayOutputStream);
        }
        return byteArrayOutputStream;
    }

    /**
     * Compress the data in given ByteArrayOutputStream using gzip and returns the compressed data.
     *
     * @param dataOutputStream the input data stream for compression
     */
    private ByteArrayOutputStream getCompressedStream(ByteArrayOutputStream dataOutputStream) {
        ByteArrayOutputStream compressedDataOutputStream = null;
        OutputStream gzipOutputStream = null;
        try {
            compressedDataOutputStream = new ByteArrayOutputStream();
            gzipOutputStream = createGzipOutputStream(compressedDataOutputStream);
            gzipOutputStream.write(dataOutputStream.toByteArray());
            gzipOutputStream.close();
        } catch (IOException e) {
            CLog.w("Failed to gzip data. Reason: '%s'.", e.toString());
            CLog.e(e);
        } finally {
            StreamUtil.close(dataOutputStream);
            StreamUtil.close(compressedDataOutputStream);
            StreamUtil.close(gzipOutputStream);
        }
        return compressedDataOutputStream;
    }

    private String getInvocationStatusDescription(InvocationStatus status) {
        if (status.equals(InvocationStatus.SUCCESS)) {
            return "completed";
        } else if (status.getThrowable() != null) {
            return String.format("failed to complete due to %s",
                    status.getThrowable().getClass().getSimpleName());
        } else {
            return "failed to complete due to an unknown error";
        }
    }

    /**
     * Send data contained in given file to given sponge-http endpoint
     *
     * @param remotePath the sponge-http endpoint to send data to. Expected format: "/endpoint"
     * @param dataStream the {@link InputStream} containing gzipped test XML data
     * @return the http response
     * @throws IOException if failed to send data
     */
    private String sendDataToHttp(String remotePath, InputStream dataStream) throws IOException {
        OutputStream httpOutStream = null;

        // copy the gzipped XML data to the http output stream
        // app engine will automagically ungzip the data before passing to the sponge-http app
        try {
            HttpURLConnection connection = createSpongeConnection(remotePath);
            httpOutStream = new BufferedOutputStream(connection.getOutputStream());
            StreamUtil.copyStreams(dataStream, httpOutStream);
            // We have to close the outputstream before reading the error/input streams.
            StreamUtil.close(httpOutStream);

            CLog.d("Response code: %s", connection.getResponseCode());
            InputStream errorStream = connection.getErrorStream();
            // If error stream is null there is no error
            if (errorStream != null) {
                String error = StreamUtil.getStringFromStream(errorStream);
                CLog.e("err stream: %s", error);
                throw new IOException(error);
            }
            String response = StreamUtil.getStringFromStream(connection.getInputStream()).trim();
            CLog.v("Received %d response \"%s\"", connection.getResponseCode(), mInvocationId);
            return response;
        } finally {
            // ensure streams are closed
            StreamUtil.close(httpOutStream);
        }
    }

    @VisibleForTesting
    int getMaxRequestSize() {
        return MAX_REQUEST_SIZE;
    }

    private void updateInvocationWithResults(String invocationId, long timestamp) {
        if (!mMultiUpdate || invocationId == null) {
            CLog.i("Skip updating invocation for invocationId %s", invocationId);
            return;
        }

        Iterator<TestRunResult> iter = getRunResults().iterator();

        // start from 1 since target result 0 is the target result that holds the invocation result
        for (int i=1; i <= getRunResults().size(); i++) {
            TestRunResult runResult = iter.next();
            boolean success = false;
            for (int attempts=0; attempts < mMaxHttpAttempts && !success; attempts++) {
                escalatingSleep(attempts);
                success = updateSpongeTargetResult(invocationId, timestamp, i, runResult);
            }
        }

        // After invocation has been updated, close the invocation.
        closeSpongeInvocation(mInvocationId, timestamp);
    }

    /**
     * Closes the sponge invocation identified by invocationId.
     *
     * @param invocationId the id of the Sponge invocation to update.
     * @param timestamp time at which target results were updated.
     */
    private void closeSpongeInvocation(String invocationId, long timestamp) {
        if (invocationId == null) {
            CLog.i("Skip closing invocation for invocationId %s", invocationId);
            return;
        }

        boolean success = false;
        for (int attempts = 0; attempts < mMaxHttpAttempts && !success; attempts++) {
            escalatingSleep(attempts);
            success = updateSpongeInvocationHeader(invocationId, timestamp);
        }

        if (!success) {
            CLog.e(
                    "Failed to close sponge invocation %s after %d attempts",
                    invocationId, mMaxHttpAttempts);
        }
    }

    /**
     * Logic to sleep between http request attempts.
     * @param attempts
     */
    private void escalatingSleep(int attempts) {
        long sleepTime = Math.round(HTTP_POLL_TIME_MS * Math.pow(attempts, 3) );
        getRunUtil().sleep(sleepTime);
    }

    /**
     * Update a sponge target result with test result details
     *
     * @param invocationId the id of the sponge invocation to update
     * @param timestamp
     * @param targetResultIndex the target result index to update
     * @param runResult the {@link TestRunResult} containing test results
     */
    private boolean updateSpongeTargetResult(
            String invocationId, long timestamp, int targetResultIndex, TestRunResult runResult) {
        ByteArrayOutputStream byteArrayOutputStream = null;
        OutputStream gzipOutputStream = null;
        InputStream logInputStream = null;
        InputStream httpInputStream = null;
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            gzipOutputStream = createGzipOutputStream(byteArrayOutputStream);
            // Need to send a gzip-compressed XML to sponge http.  Also saving the XML is useful in
            // case upload to sponge fails. First save the XML to an in-memory stream, then read it
            // back for both sending to http and saving the log.
            KXmlSerializer serializer = new KXmlSerializer();
            prepareAndEmitXmlHeader(gzipOutputStream, serializer);

            serializer.startTag(NS, "invocation");
            addSpongeAttribute(NS, serializer, "id", invocationId);
            addSpongeAttribute(NS, serializer, "run_date", Long.toString(timestamp));
            addSpongeAttribute(NS, serializer, "user", System.getProperty("user.name"));

            emitRunResult(serializer, timestamp, runResult, targetResultIndex, true);

            serializer.endTag(NS, "invocation");

            emitXmlFooter(serializer);
            gzipOutputStream.close();

            final String dataName = String.format("update_target_result%d_", targetResultIndex);
            final String ext = String.format("%s.%s", LogDataType.XML.getFileExt(),
                    LogDataType.GZIP.getFileExt());
            logInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
            LogFile log = mLogSaver.saveLogDataRaw(dataName, ext, logInputStream);
            CLog.i("Saved gzip of update_target_result %d to %s", targetResultIndex,
                    log.getPath());
            httpInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
            String response = sendDataToHttp("/update_target_result", httpInputStream);
            CLog.d("Received update_target_result response \"%s\"", response);
            // TODO: validate response
            return true;
        } catch (IOException e) {
            CLog.e("Failed to update report data for invocation %s, target result %d: %s. " +
                    "Log location: %s", invocationId, targetResultIndex, e.getMessage(),
                    mLogSaver.getLogReportDir().getPath());
            return false;
        } finally {
            StreamUtil.close(gzipOutputStream);
            StreamUtil.close(logInputStream);
            StreamUtil.close(httpInputStream);
        }
    }

    /**
     * Write test run and test metrics in text file and upload it to sponge.
     */
    private void uploadTestMetricInSponge() {
        File resultsFile = null;
        LogFile log = null;
        InputStreamSource inputStreamSrc = null;
        try {
            resultsFile = PerfUtility.writeResultsToFile(getRunResults());
            inputStreamSrc = new FileInputStreamSource(resultsFile);
            log = mLogSaver.saveLogDataRaw(resultsFile.getName(), "txt",
                    inputStreamSrc.createInputStream());
            if (null != resultsFile && null != log) {
                testLogSaved(resultsFile.getName(), LogDataType.TEXT, inputStreamSrc, log);
            }
        } catch (IOException e) {
            CLog.e("Not able to write test results file due to : %s", e.getMessage());
        } finally {
            StreamUtil.cancel(inputStreamSrc);
            FileUtil.deleteFile(resultsFile);
        }
    }

    /**
     * Updates sponge invocation header which closes the invocation.
     *
     * @param invocationId the id of the sponge invocation to update.
     * @param timestamp time at which target results were updated.
     */
    private boolean updateSpongeInvocationHeader(String invocationId, long timestamp) {
        ByteArrayOutputStream byteArrayOutputStream = null;
        OutputStream gzipOutputStream = null;
        InputStream logInputStream = null;
        InputStream httpInputStream = null;
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            gzipOutputStream = createGzipOutputStream(byteArrayOutputStream);

            KXmlSerializer serializer = new KXmlSerializer();
            prepareAndEmitXmlHeader(gzipOutputStream, serializer);

            serializer.startTag(NS, "invocation");
            addSpongeAttribute(NS, serializer, "id", invocationId);
            addSpongeAttribute(NS, serializer, "run_date", Long.toString(timestamp));
            addSpongeAttribute(NS, serializer, "user", System.getProperty("user.name"));

            serializer.endTag(NS, "invocation");

            emitXmlFooter(serializer);

            gzipOutputStream.close();

            final String dataName = String.format("update_invocation_header");
            final String ext =
                    String.format(
                            "%s.%s", LogDataType.XML.getFileExt(), LogDataType.GZIP.getFileExt());
            logInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
            LogFile log = mLogSaver.saveLogDataRaw(dataName, ext, logInputStream);
            CLog.i("Saved gzip of update_invocation_header to %s", log.getPath());

            httpInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
            sendDataToHttp("/update_invocation_header", httpInputStream);

            return true;
        } catch (IOException e) {
            CLog.w(
                    "Failed to update sponge invocation header for invocation id: %s."
                            + "Reason: '%s'. Log location: %s",
                    invocationId, e.toString(), mLogSaver.getLogReportDir().getPath());
            CLog.e(e);
            return false;
        } finally {
            StreamUtil.close(gzipOutputStream);
            StreamUtil.close(logInputStream);
            StreamUtil.close(httpInputStream);
            StreamUtil.close(byteArrayOutputStream);
        }
    }

    /**
     * Convert an output stream into a gzip output stream.
     */
    @VisibleForTesting
    OutputStream createGzipOutputStream(OutputStream outputStream) throws IOException {
        return new GZIPOutputStream(outputStream);
    }

    /**
     * Prepare the  xml serializer for output, and output starting xml details needed for each
     * Sponge HTTP API request.
     *
     * @param stream
     * @param serializer
     * @throws IOException
     */
    private void prepareAndEmitXmlHeader(OutputStream stream, KXmlSerializer serializer)
            throws IOException {
        serializer.setOutput(stream, "UTF-8");
        serializer.startDocument("UTF-8", null);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);

        // Sponge HTTP API requires this
        serializer.startTag(NS, "xml");
        addSpongeAttribute(NS, serializer, "server", mSpongeBackend);
    }

    /**
     * Emit the XML to close a Sponge HTTP API request.
     *
     * @param serializer
     * @throws IOException
     */
    private void emitXmlFooter(KXmlSerializer serializer) throws IOException {
        serializer.endTag(NS, "xml");
        serializer.endDocument();
    }

    /**
     * Gets the invocationId returned by sponge api server.
     * <p/>
     * Only valid after invocationEnded has been called.
     */
    String getInvocationId() {
        return mInvocationId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestSummary getSummary() {
        if (mInvocationId == null) {
            String url = mLogSaver.getLogReportDir().getUrl();
            if (url != null) {
                CLog.i("No invocation created, using reportUrl %s", url);
                return new TestSummary(url);
            }
            return null;
        }
        StatisticsTestSummary summary = new StatisticsTestSummary(getInvocationUrl(mInvocationId));
        summary.setStatistics(getInvocationSummaryUrl());
        summary.setGeneric(getBaseSpongeUrl());
        return summary;
    }

    /**
     * Gets the http url to the invocation displayed on sponge dashboard.
     * <p/>
     * Only valid after invocationEnded has been called.
     *
     * @return the http url or <code>null</code> if not available
     */
    private String getInvocationUrl(String invocationId) {
        if (invocationId == null) {
            return null;
        }
        MultiMap<String, String> paramMap = new MultiMap<>();
        paramMap.put("id", invocationId);
        // default to "Test Cases" tab showing failed and aborted tests
        paramMap.put("tab", "Test Cases");
        paramMap.put("show", "FAILED");
        paramMap.put("show", "INTERNAL_ERROR");
        return getHttpHelper().buildUrl(String.format("%s/invocation", getBaseSpongeUrl()),
                paramMap);
    }

    private String getBaseSpongeUrl() {
        if (mSpongeBackend.equals(BACKEND_QA)) {
            return "http://sponge-qa.corp.google.com";
        } else {
            return "http://sponge.corp.google.com";
        }
    }

    /**
     * Gets the http url that retrieves the test result summary for the invocation.
     * <p/>
     * Only valid after invocationEnded has been called.
     *
     * @return the http url or <code>null</code> if not available
     */
    private String getInvocationSummaryUrl() {
        if (mInvocationId == null) {
            return null;
        }
        MultiMap<String, String> paramMap = new MultiMap<>();
        paramMap.put("invocation_id", mInvocationId);
        paramMap.put("server", mSpongeBackend);

        try {
            URL url = new URL("HTTPS", mSpongeApiServer, mSpongeApiPort, "/get_test_summary");
            return getHttpHelper().buildUrl(url.toString(), paramMap);
        } catch (MalformedURLException e) {
            CLog.e("Could not construct summary url");
            CLog.e(e);
        }
        return null;
    }

    /**
     * Return the http helper to use.
     */
    @VisibleForTesting
    IHttpHelper getHttpHelper() {
        return new HttpHelper();
    }

    /**
     * Creates the Sponge connection to given remote file of <var>mSpongeApiServer</var>.
     */
    @VisibleForTesting
    HttpURLConnection createSpongeConnection(String remoteFile) throws IOException {
        URL httpApi = new URL("HTTPS", mSpongeApiServer, mSpongeApiPort, remoteFile);
        HttpURLConnection connection = getHttpHelper().createXmlConnection(httpApi, "PUT");
        // instruct app-engine that the incoming data is gzipped compressed, so it will
        // automatically ungzip
        connection.setRequestProperty("Content-Encoding", "gzip");
        return connection;
    }

    /**
     * Adds an XML tag that represents a Sponge attribute's name-value pair.
     */
    protected void addSpongeAttribute(
            String namespace, KXmlSerializer serializer, String name,
            String value) throws IOException {
        serializer.startTag(namespace, name);
        serializer.text(value);
        serializer.endTag(namespace, name);
    }

    private void addSpongeAttributeInCdata(String namespace, KXmlSerializer serializer,
            String name, String value) throws IOException {
        serializer.startTag(namespace, name);
        // escape the cdata termination sequence
        value = value.replaceAll("]]>", "cdata-esc");
        serializer.cdsect(value);
        serializer.endTag(namespace, name);
    }

    private void addSpongeConfValue(String namespace, KXmlSerializer serializer, String name,
            String value) throws IOException {
        if (value == null) {
            // protect against NPE when adding a null value.
            CLog.w(
                    "A null build attribute for key '%s' was attempted to be posted. ignoring.",
                    name);
            return;
        }
        serializer.startTag(namespace, "configuration_value");
        addSpongeAttribute(namespace, serializer, "name", name);
        addSpongeAttribute(namespace, serializer, "value", value);
        serializer.endTag(namespace, "configuration_value");
    }

    String getHostName() throws UnknownHostException {
        return InetAddress.getLocalHost().getHostName();
    }

    private void emitTestResults(
            KXmlSerializer serializer,
            long timestamp,
            InvocationStatus invocationStatus,
            long elapsedTime)
            throws IOException {

        serializer.startTag(NS, "invocation");
        addSpongeAttribute(NS, serializer, "id", "0");
        addSpongeAttribute(NS, serializer, "run_date", Long.toString(timestamp));
        addSpongeAttribute(NS, serializer, "user", System.getProperty("user.name"));
        addSpongeAttribute(NS, serializer, "hostname", getHostName());
        // TODO: Remove the cl fields.
        addSpongeAttribute(NS, serializer, "cl", getPrimaryBuildInfo().getBuildId());

        // add build id, branch and flavor as labels so they are searchable
        if (getPrimaryBuildInfo().getBuildId() != null) {
            addSpongeAttribute(
                    NS,
                    serializer,
                    "label",
                    String.format("bid/%s", getPrimaryBuildInfo().getBuildId()));
        }
        if (getPrimaryBuildInfo().getBuildBranch() != null) {
            addSpongeAttribute(
                    NS,
                    serializer,
                    "label",
                    String.format("b/%s", getPrimaryBuildInfo().getBuildBranch()));
        }
        if (getPrimaryBuildInfo().getBuildFlavor() != null) {
            addSpongeAttribute(
                    NS,
                    serializer,
                    "label",
                    String.format("f/%s", getPrimaryBuildInfo().getBuildFlavor()));
        }
        if (getPrimaryBuildInfo().getDeviceSerial() != null) {
            addSpongeAttribute(
                    NS,
                    serializer,
                    "label",
                    String.format("s/%s", getPrimaryBuildInfo().getDeviceSerial()));
        }

        // add invocation attributes as labels
        for (String attributeKey : getInvocationContext().getAttributes().keySet()) {
            if (INVOCATION_ATTRIBUTE_EXCLUSION.contains(attributeKey)) {
                // don't report excluded invocation attributes
                continue;
            }
            List<String> values = getInvocationContext().getAttributes().get(attributeKey);
            for (String value : values) {
                addSpongeAttribute(
                        NS, serializer, "label", String.format("%s/%s", attributeKey, value));
            }
        }

        // add other build attributes as labels too
        for (Map.Entry<String, String> attributeEntry :
                getPrimaryBuildInfo().getBuildAttributes().entrySet()) {
            String key = attributeEntry.getKey();
            String value = attributeEntry.getValue();
            if ("account".equals(key)) {
                // '@' character isn't valid in Sponge labels
                value = value.replace('@', '_');
            }
            // Other build attributes get reported as ConfigurationValues to be queriable.
            addSpongeConfValue(NS, serializer, key, value);
        }

        // add additional labels
        for (String label : mLabels) {
            addSpongeAttribute(NS, serializer, "label", label);
        }

        // identify the data as coming from Trade Federation
        addSpongeAttribute(NS, serializer, "label", "tradefed");

        // add the Trade Federation version
        addSpongeAttribute(
                NS,
                serializer,
                "label",
                String.format("tradefed_version/%s", VersionParser.fetchVersion()));

        // add the test tag
        addSpongeAttribute(NS, serializer, "label", String.format("test_tag/%s", getTestTag()));

        if (mDuplicateAsBuildLogs) {
            outputLogs(serializer);
        }

        // add a target_result to represent status of the invocation
        serializer.startTag(NS, "target_result");
        addSpongeAttribute(NS, serializer, "run_date", Long.toString(timestamp));
        addSpongeAttribute(NS, serializer, "run_duration_millis", Long.toString(elapsedTime));
        // append 'invocation' to the name of this target to further distinguish it from targets
        // representing test runs
        addSpongeAttribute(
                NS, serializer, "build_target", String.format("%s_invocation", getTestTag()));
        // mark this target as 'unspecified type', so it doesn't appear in test cases tab
        addSpongeAttribute(NS, serializer, "type", SpongeTargetType.UNSPECIFIED_TYPE.value);
        if (invocationStatus.equals(InvocationStatus.BUILD_ERROR)) {
            addSpongeAttribute(NS, serializer, "status", SpongeStatus.FAILED.value);
            addSpongeAttribute(
                    NS,
                    serializer,
                    "status_details",
                    "Boot failure\n" + getStackTrace(invocationStatus.getThrowable()));
        } else if (invocationStatus.equals(InvocationStatus.FAILED)){
            addSpongeAttribute(NS, serializer, "status", SpongeStatus.INTERNAL_ERROR.value);
            addSpongeAttribute(
                    NS,
                    serializer,
                    "status_details",
                    getStackTrace(invocationStatus.getThrowable()));
            // TODO: consider mapping an aborted invocation to SpongeStatus.ABORTED_BY_TOOL
        } else {
            addSpongeAttribute(NS, serializer, "status", SpongeStatus.PASSED.value);
        }

        emitExtraTargetResultFields(NS, serializer);

        outputLogs(serializer, "");

        serializer.endTag(NS, "target_result");

        for (TestRunResult runResult : getRunResults()) {
            emitRunResult(serializer, timestamp, runResult, null, !mMultiUpdate);
        }

        serializer.endTag(NS, "invocation");
    }

    /** Hook for subclasses to emit extra tags in a target_result tag. */
    @SuppressWarnings("unused")
    protected void emitExtraTargetResultFields(String ns, KXmlSerializer serializer) {}

    /**
     * Gets the stack trace as a {@link String}.
     *
     * @param throwable the {@link Throwable} to convert.
     * @return a {@link String} stack trace
     */
    private String getStackTrace(Throwable throwable) {
        // dump the print stream results to the ByteArrayOutputStream, so contents can be evaluated
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream bytePrintStream = new PrintStream(outputStream);
        throwable.printStackTrace(bytePrintStream);
        return outputStream.toString();
    }

    /**
     * Output a sponge target result containing the test run result's data.
     * <p/>
     * This method essentially has three modes:
     * <ol>
     * <li>Create a new target result containing all the test result details.</li>
     * <li>Create a new, empty target result. The test details will be added to this target result
     * in a future request.</li>
     * <li>Update an existing target result, containing the test result details.</li>
     * </ol>
     *
     * @param serializer
     * @param timestamp
     * @param runResult the {@link TestRunResult}
     * @param targetResultIndex the target result index to update. <code>null</code> if a target
     *            result is being created
     * @param outputTestContents <code>true</code> if the Sponge TargetResult being emitted should
     *            contain the test result details
     * @throws IOException
     */
    private void emitRunResult(KXmlSerializer serializer, long timestamp, TestRunResult runResult,
            Integer targetResultIndex, boolean outputTestContents) throws IOException {

        // represent each run as a 'target_result'. This will allow each test run to be queried
        // separately via the sponge ui, and allows each test run to be updated separately
        serializer.startTag(NS, "target_result");
        if (targetResultIndex != null) {
            addSpongeAttribute(NS, serializer, "index", targetResultIndex.toString());
        }
        addSpongeAttribute(NS, serializer, "run_date", Long.toString(timestamp));
        addSpongeAttribute(
                NS, serializer, "run_duration_millis", Long.toString(runResult.getElapsedTime()));
        addSpongeAttribute(NS, serializer, "build_target", runResult.getName());
        addSpongeAttribute(NS, serializer, "type", "1");

        if (mTestSize != null) {
            addSpongeAttribute(NS, serializer, "size", mTestSize);
        }

        if (outputTestContents) {
            if (runResult.isRunFailure()) {
                addSpongeAttribute(NS, serializer, "status", SpongeStatus.INTERNAL_ERROR.value);
                addSpongeAttribute(
                        NS, serializer, "status_details", runResult.getRunFailureMessage());
            } else if (runResult.hasFailedTests()) {
                addSpongeAttribute(NS, serializer, "status", SpongeStatus.FAILED.value);
            } else {
                addSpongeAttribute(NS, serializer, "status", SpongeStatus.PASSED.value);
            }
            emitRunResultContents(serializer, runResult);
            outputLogs(serializer, runResult.getName());
        } else {
            // PENDING state means will be updated later
            addSpongeAttribute(NS, serializer, "status", SpongeStatus.PENDING.value);
        }
        serializer.endTag(NS, "target_result");
    }

    private void emitRunResultContents(KXmlSerializer serializer, TestRunResult runResult)
            throws IOException {
        // output a parent test_result tag for the run result
        serializer.startTag(NS, "test_result");
        addSpongeAttribute(NS, serializer, "name", runResult.getName());

        addSpongeAttribute(
                NS, serializer, "run_duration_millis", Long.toString(runResult.getElapsedTime()));
        addSpongeAttribute(
                NS,
                serializer,
                "test_case_count",
                Integer.toString(runResult.getNumCompleteTests()));
        addSpongeAttribute(
                NS,
                serializer,
                "failure_count",
                Integer.toString(runResult.getNumAllFailedTests()));
        addSpongeAttribute(NS, serializer, "error_count", "0");

        for (Map.Entry<TestIdentifier, TestResult> testEntry :
                runResult.getTestResults().entrySet()) {
            emitTestResult(serializer, testEntry.getKey(), testEntry.getValue());
        }

        serializer.endTag(NS, "test_result");
    }

    /** Output log file data to the invocation */
    private void outputLogs(KXmlSerializer serializer) throws IOException {
        Map<String, LogFileData> uniqueLogMap = mLogDataMap.getUniqueMap();

        if (mSendTestLogIndex) {
            // Send a single large_text containing links to all of the logfiles.
            List<StringBuilder> logIndexes = new ArrayList<StringBuilder>();
            StringBuilder logIndexBuilder = new StringBuilder();
            logIndexes.add(logIndexBuilder);
            List<String> keys = new ArrayList<>(uniqueLogMap.keySet());
            Collections.sort(keys);
            for (String name : keys) {
                String url = uniqueLogMap.get(name).getUrl();
                String line = String.format("%s: %s\n", name, url);
                if (logIndexBuilder.length() + line.length() > MAX_UPLOAD_FILE_SIZE) {
                    logIndexBuilder = new StringBuilder();
                    logIndexes.add(logIndexBuilder);
                }
                if (line.length() > MAX_UPLOAD_FILE_SIZE) {
                    CLog.w(
                            "Log for '%s' exceeds max size of LargeText field (%db > %db). "
                                    + "Truncating...",
                            name, line.length(), MAX_UPLOAD_FILE_SIZE);
                    line = line.substring(0, MAX_UPLOAD_FILE_SIZE);
                }
                logIndexBuilder.append(line);
            }
            // create the large_text stanza
            for (int i = 0; i < logIndexes.size(); i += 1) {
                logIndexBuilder = logIndexes.get(i);
                serializer.startTag(NS, "large_text");
                if (logIndexes.size() > 1) {
                    addSpongeAttribute(
                            NS,
                            serializer,
                            "name",
                            String.format(
                                    "Logfile Index (Page %d of %d)", i + 1, logIndexes.size()));
                } else {
                    addSpongeAttribute(NS, serializer, "name", "Logfile Index");
                }
                addSpongeAttribute(NS, serializer, "value", logIndexBuilder.toString());
                serializer.endTag(NS, "large_text");
            }
        } else {
            for (Map.Entry<String, LogFileData> logEntry : uniqueLogMap.entrySet()) {
                // no need to sanitize log name if log is associated to the invocation
                logEntry.getValue().serializeLargeText(serializer, logEntry.getKey(), false);
            }
        }
    }

    /** Output log file data to given target */
    private void outputLogs(KXmlSerializer serializer, String targetName) throws IOException {
        if (!mTargetLogDataMap.containsKey(targetName)) return;

        Map<String, LogFileData> uniqueLogMap = mTargetLogDataMap.get(targetName).getUniqueMap();

        for (Map.Entry<String, LogFileData> logEntry : uniqueLogMap.entrySet()) {
            // sanitize log names if log is associated to a target
            logEntry.getValue().serializeLargeText(serializer, logEntry.getKey(), true);
        }
    }

    void emitTestResult(KXmlSerializer serializer, TestIdentifier testId, TestResult testResult)
            throws IOException {
        if (testResult.getStatus().equals(TestStatus.INCOMPLETE)) {
            CLog.v("Skipping emit of test result for incomplete test %s", testId);
            return;
        }

        serializer.startTag(NS, "child");
        addSpongeAttribute(
                NS,
                serializer,
                "name",
                String.format("%s.%s", testId.getClassName(), testId.getTestName()));
        addSpongeAttribute(NS, serializer, "class_name", testId.getClassName());
        addSpongeAttribute(NS, serializer, "test_case_count", "1");
        addSpongeAttribute(
                NS,
                serializer,
                "run_duration_millis",
                String.valueOf(testResult.getEndTime() - testResult.getStartTime()));
        if (testResult.getStatus().equals(TestStatus.FAILURE) ||
                testResult.getStatus().equals(TestStatus.ASSUMPTION_FAILURE)) {
            addSpongeAttribute(NS, serializer, "failure_count", "1");
            addFailure(serializer, "test_failure", testResult);
        }

        serializer.endTag(NS, "child");
    }

    void addFailure(KXmlSerializer serializer, String tag, TestResult testResult)
            throws IOException {
        serializer.startTag(NS, tag);
        String stackTrace = testResult.getStackTrace();

        if (stackTrace == null) {
            stackTrace = "";
        }
        stackTrace = sanitize(stackTrace);

        // Sponge HTTP API does not accept blank message or exception_type strings
        String message = "EMPTY";
        String exceptionType = "exception";

        // properly formatted exceptions will separate the type and message with ': '.
        // see Throwable#toString()
        final String exceptionRegex = "^(\\S*(Error|Exception|Failure|Fault|Interrupt))(: (.*)?|$)";
        Pattern exceptionPattern = Pattern.compile(exceptionRegex);

        String line = stackTrace.split("[\\r\\n]+")[0];
        Matcher matcher = exceptionPattern.matcher(line);
        if (matcher.find()) {
            exceptionType = matcher.group(1);
            if (matcher.group(4) != null && !matcher.group(4).trim().isEmpty()) {
                message = matcher.group(4);
            }
        } else if (!line.trim().isEmpty()) {
            message = line;
        }

        addSpongeAttribute(NS, serializer, "exception_type", exceptionType);
        addSpongeAttribute(NS, serializer, "message", message);

        String stackText = stackTrace;
        if (stackText.length() == 0) {
            stackText = "EMPTY";
        }
        addSpongeAttribute(NS, serializer, "detail", stackText);

        serializer.endTag(NS, tag);
    }

    /** Returns the text in a format that is safe for use in an XML document. */
    private String sanitize(String text) {
        return text.replace("\0", "<\\0>");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        // Ignore, sponge uses the global log file saver so test logs are handled in testLogSaved().
    }

    /** {@inheritDoc} */
    @Override
    public void testLogSaved(
            String dataName, LogDataType dataType, InputStreamSource dataStream, LogFile log) {
        try {
            LogFileData logData = new LogFileData(dataType, dataStream, log);
            mTargetLogDataMap.putIfAbsent(mCurrentRunName, new MultiMap<String, LogFileData>());
            mTargetLogDataMap.get(mCurrentRunName).put(dataName, logData);
            if (mDuplicateAsBuildLogs) {
                mLogDataMap.put(dataName, logData);
            }
        } catch (IllegalArgumentException | IOException e) {
            CLog.w("Unable to add log %s", dataName);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogSaver(ILogSaver logSaver) {
        mLogSaver = logSaver;
    }

    /**
     * @return the {@link ILogSaver} to be used by subclasses
     */
    protected ILogSaver getLogFileSaver() {
        return mLogSaver;
    }

    /**
     * Accessor method for getting reference to a {@link IGlobalConfiguration}.
     *
     * Visible so unit tests can mock.
     */
    IGlobalConfiguration getGlobalConfig() {
        return GlobalConfiguration.getInstance();
    }

    private String getTestTag() {
        return getInvocationContext().getTestTag();
    }

    /** Clean up of the data holded for reporting. Must be called for proper cleanup. */
    @VisibleForTesting
    void cleanUp() {
        if (mDuplicateAsBuildLogs) {
            Map<String, LogFileData> uniqueLogMap = mLogDataMap.getUniqueMap();
            for (Map.Entry<String, LogFileData> logEntry : uniqueLogMap.entrySet()) {
                logEntry.getValue().cleanUp();
            }
        } else {
            for (Entry<String, MultiMap<String, LogFileData>> targetLogs :
                    mTargetLogDataMap.entrySet()) {
                Map<String, LogFileData> uniqueLogMap = targetLogs.getValue().getUniqueMap();
                for (Map.Entry<String, LogFileData> logEntry : uniqueLogMap.entrySet()) {
                    logEntry.getValue().cleanUp();
                }
                targetLogs.getValue().clear();
            }
        }
        mLogDataMap.clear();
        mTargetLogDataMap.clear();
    }

    @VisibleForTesting
    File createTmpSnippetFile() throws IOException {
        return FileUtil.createTempFile("sponge-snippet-data", ".tmp");
    }

    /** Replace some illegal xml characters to ensure a well-formed document. */
    @VisibleForTesting
    String sanitizedSnippet(String in) {
        StringBuffer out = new StringBuffer();
        char current;
        if (Strings.isNullOrEmpty(in)) {
            return "";
        }
        for (int i = 0; i < in.length(); i++) {
            current = in.charAt(i);
            if ((current == 0x9)
                    || (current == 0xA)
                    || (current == 0xD)
                    || ((current >= 0x20) && (current <= 0x7E))) {
                out.append(current);
            } else {
                out.append("?");
            }
        }
        return out.toString();
    }

    /** Representation of the data of the log file to be uploaded to Sponge. */
    @VisibleForTesting
    class LogFileData {
        private final LogDataType mDataType;
        private final LogFile mLogFile;
        private final File mSnippetFile;
        private final long mSize;

        public LogFileData(LogDataType dataType, InputStreamSource dataStream, LogFile logFile)
                throws IOException {
            if (dataStream == null || dataType == null || logFile == null ||
                    logFile.getPath() == null) {
                throw new IllegalArgumentException();
            }
            mLogFile = logFile;
            mDataType = dataType;
            mSnippetFile = createTmpSnippetFile();
            try {
                FileUtil.writeToFile(generateSnippet(dataStream), mSnippetFile);
            } catch (IOException e) {
                FileUtil.deleteFile(mSnippetFile);
                throw e;
            }
            mSize = dataStream.size();
        }

        public String getUrl() {
            return mLogFile.getUrl() != null ? mLogFile.getUrl() : mLogFile.getPath();
        }

        public void serializeLargeText(
                KXmlSerializer serializer, String name, boolean sanitizeLogName)
                throws IOException {
            serializer.startTag(NS, "large_text");
            name = sanitizeLogName ? sanitizeName(name) : getNameWithExt(name);
            addSpongeAttribute(NS, serializer, "name", name);
            if (!useGoogleFilePointer()) {
                // Logs can have many kind of unpredictable illegal XML characters. We wrap them in
                // a CDATA section to avoid problems with sponge's API
                String content = null;
                if (mReportSnippet) {
                    content = FileUtil.readStringFromFile(mSnippetFile);
                    content = sanitizedSnippet(content);
                } else {
                    content = "Full log: " + getUrl();
                }
                addSpongeAttributeInCdata(NS, serializer, "value", content);
            } else {
                serializer.startTag(NS, "google_file_pointer");
                addSpongeAttribute(NS, serializer, "path", mLogFile.getPath());
                addSpongeAttribute(NS, serializer, "length", Long.toString(mSize));
                serializer.endTag(NS, "google_file_pointer");
            }
            serializer.endTag(NS, "large_text");
        }

        private boolean useGoogleFilePointer() {
            return mUseGoogleFilePointer &&
                    mLogFile.isText() && !mLogFile.isCompressed() &&
                    mLogFile.getPath().startsWith("/cns/");
        }

        private String getNameWithExt(String name) {
            return String.format("%s.%s", name, mDataType.getFileExt());
        }

        // This is used to sanitize large_text names associated to Sponge target_result. If name
        // contains an extension, Sponge will try to interpret the large_text value as the extension
        // suggests. But we want all logs being interpreted as plain texts because they are snippets
        // in texts. If a file does not have an extension, or has ".dat" extension, it will be
        // treated as plain texts. This function removes the extension if name already contains an
        // extension specified by the data type. Besides, if data type is LogDataType.UNKNOWN, or
        // name contains an extension which the data type does not recognize, a ".dat" extension
        // is added to name.
        @VisibleForTesting
        String sanitizeName(String name) {
            String extension = String.format(".%s", mDataType.getFileExt());
            name = name.trim();
            if (name.endsWith(extension)) {
                name = name.substring(0, name.length() - extension.length());
            }

            String datExtension = String.format(".%s", LogDataType.UNKNOWN.getFileExt());
            if ((mDataType == LogDataType.UNKNOWN || name.contains("."))
                    && !name.endsWith(datExtension)) {
                return String.format("%s%s", name, datExtension);
            }
            return name;
        }

        private String generateSnippet(InputStreamSource dataStream) throws IOException {
            StringBuilder largeText = new StringBuilder();
            largeText.append("Full log: ").append(getUrl());
            if (mDataType.isText()) {
                largeText.append("\n\n");

                // write out the log snippet
                InputStream origStream = dataStream.createInputStream();
                try {
                    long contentSize = dataStream.size();

                    if (contentSize == 0) {
                        largeText.append("(empty file)");
                    } else if (contentSize > MAX_UPLOAD_FILE_SIZE) {
                        // If the file is too big, only upload maxSize bytes.
                        largeText.append(
                                String.format(
                                        "(start of file truncated because of size constraints: "
                                                + "%db > %db)\n\n",
                                        contentSize, MAX_UPLOAD_FILE_SIZE));
                        origStream.skip(contentSize - MAX_UPLOAD_FILE_SIZE);
                    }
                    largeText.append(StreamUtil.getStringFromStream(origStream));
                } finally {
                    StreamUtil.close(origStream);
                }
            }
            return largeText.toString();
        }

        /** Clean up all the references in the object once we are done with it. */
        public void cleanUp() {
            FileUtil.deleteFile(mSnippetFile);
        }
    }
}
