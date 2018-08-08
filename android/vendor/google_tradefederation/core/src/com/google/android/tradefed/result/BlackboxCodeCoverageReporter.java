// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.google.android.tradefed.result.BlackboxPostUtil.TestResultsBuilder;
import com.google.common.annotations.VisibleForTesting;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * A {@link ITestInvocationListener} which reports code coverage results to Blackbox.
 */
public class BlackboxCodeCoverageReporter implements ILogSaverListener, ITestInvocationListener {

    static final String LEGACY_REPORT_LOCATION = "https://cnsviewer.corp.google.com"
            + "/ear/cns/oc-d/home/android-code-coverage/legacy/%s/%s/tests/%s/%s";

    @Option(name = "base-url",
            description = "The base URL for the posting interface")
    private String mBaseUrl = "https://android-rdb-post.googleplex.com/";

    @Option(
        name = "max-results-per-request",
        description =
                "The number of results to send in one request. Warning: this should only be set if "
                        + "there are bugs preventing the default number of requests."
    )
    private int mMaxResults = 0;

    private BlackboxPostUtil mPostUtil = null;
    private TestResultsBuilder mTestResultsBuilder = null;

    private Map<String, String> mReportUrls = new HashMap<>();
    private Map<String, InputStream> mXmlReports = new HashMap<>();

    private String mTestResultId = null;
    private String mBranch = null;
    private String mBuildId = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogSaver(ILogSaver logSaver) {
        // Ignore
    }

    /** {@inheritDoc} */
    @Override
    public void invocationStarted(IInvocationContext context) {
        mPostUtil = new BlackboxPostUtil(mBaseUrl);
        if (mMaxResults > 0) {
            mPostUtil.setMaxResults(mMaxResults);
        }

        // Report on primary build info.
        IBuildInfo buildInfo = context.getBuildInfos().get(0);

        // Construct a new TestResultsBuilder
        mTestResultsBuilder = internalCreateTestResultsBuilder()
                .setBuildInfo(buildInfo)
                .setTestSuite(buildInfo.getTestTag());
        CLog.i("Will report results to Blackbox for branch \"%s\", target \"%s\", "
                + "test suite \"%s\", and build ID \"%s\".", buildInfo.getBuildBranch(),
                buildInfo.getBuildTargetName(), buildInfo.getTestTag(), buildInfo.getBuildId());

        // Get build info and test result id, so we can compute the location of the coverage report
        mTestResultId = context.getAttributes()
                .get(AndroidBuildResultReporter.TEST_RESULT_ID).get(0);
        mBranch = buildInfo.getBuildBranch();
        mBuildId = buildInfo.getBuildId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLogSaved(String dataName, LogDataType dataType, InputStreamSource dataStream,
            LogFile log) {

        // Save the URL where the HTML report has been saved
        if (LogDataType.EAR.equals(dataType)) {
            // Compute the location of the coverage report.
            String url = String.format(LEGACY_REPORT_LOCATION,
                    mBranch, mBuildId, mTestResultId, log.getPath());
            mReportUrls.put(dataName, url);
        }

        // Save the JACOCO_XML log so we can parse it in invocationEnded(..)
        if (LogDataType.JACOCO_XML.equals(dataType)) {
            mXmlReports.put(dataName, dataStream.createInputStream());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        // Parse each of the JACOCO_XML files
        for (Map.Entry<String, InputStream> entry : mXmlReports.entrySet()) {
            // Retrieve the base URL of the HTML report that corresponds to this XML
            String reportUrl = mReportUrls.get(entry.getKey());
            if (reportUrl == null) {
                // Not fatal, but deep-linking won't be supported
                CLog.w("Unable to get report url for %s", entry.getKey());
            }

            // Parse the XML and add the coverage results to the test results builder
            JacocoXmlHandler handler = new JacocoXmlHandler(mTestResultsBuilder, reportUrl);
            try {
                getParser().parse(entry.getValue(), handler);
            } catch (IOException | ParserConfigurationException | SAXException e) {
                // Just log the error. If we throw, the other result reporters won't be able to run.
                CLog.e("Failed to parse coverage XML file.");
                CLog.e(e);
                return;
            }
        }

        // Send the results to Blackbox. BlackboxPostUtil will log if anything goes wrong.
        postResults(mTestResultsBuilder);
    }

    /** Returns a new {@link TestResultsBuilder} instance. */
    @VisibleForTesting
    TestResultsBuilder internalCreateTestResultsBuilder() {
        return new TestResultsBuilder();
    }

    /** Sends the given {@code results} to the Blackbox posting interface at {@code baseUrl}. */
    @VisibleForTesting
    boolean postResults(TestResultsBuilder results) {
        CLog.i("Attempting to report coverage results to Blackbox for branch "
                + "\"%s\", target \"%s\", test suite \"%s\", and build ID \"%s\".",
                mTestResultsBuilder.getBranch(), mTestResultsBuilder.getProduct(),
                mTestResultsBuilder.getTestSuite(), mTestResultsBuilder.getBuildId());
        return mPostUtil.postTestResults(results);
    }

    /** Returns a new SAXParser. */
    private SAXParser getParser() throws ParserConfigurationException, SAXException {
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        // Instruct the parser to skip dtd validation
        parserFactory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        parserFactory.setFeature("http://xml.org/sax/features/validation", false);
        return parserFactory.newSAXParser();
    }
}
