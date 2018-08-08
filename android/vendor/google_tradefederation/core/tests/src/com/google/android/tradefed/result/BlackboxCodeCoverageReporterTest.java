// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.google.android.tradefed.result.BlackboxPostUtil.TestResultsBuilder;
import com.google.common.io.ByteStreams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;

/** Unit tests for {@link BlackboxCodeCoverageReporter}. */
@RunWith(JUnit4.class)
public class BlackboxCodeCoverageReporterTest {

    private static final String TEST_TAG = "some-test-tag";
    private IBuildInfo mBuildInfo;
    private IInvocationContext mContext;

    private static final String JACOCO_XML_RESOURCE_PATH = "/testdata/jacoco_basic_report.xml";
    private static final String JACOCO_XML_PATH = "/some/path/not/actually/used/report.xml";
    private static final String JACOCO_XML_URL = "www.example.com/report.xml";

    //private static final String FAKE_HTML_REPORT_URL = "www.example.com/coverage-report";
    private static final String BUILD_ID = "12345";
    private static final String BUILD_TARGET = "bullhead-userdebug_coverage";
    private static final String BUILD_BRANCH = "git_oc-release";
    private static final String TEST_RESULT_ID = "67890";

    private static final String HTML_REPORT_PATH = "staging/path/for/coverage/report.ear";
    private static final String HTML_REPORT_URL = String.format(
            BlackboxCodeCoverageReporter.LEGACY_REPORT_LOCATION, BUILD_BRANCH, BUILD_ID,
            TEST_RESULT_ID, HTML_REPORT_PATH);

    @Before
    public void setUp() {
        mBuildInfo = new BuildInfo(BUILD_ID, BUILD_TARGET);
        mBuildInfo.setBuildBranch(BUILD_BRANCH);
        mContext = new InvocationContext();
        mContext.setTestTag(TEST_TAG);
        mContext.addDeviceBuildInfo("device", mBuildInfo);
        mContext.addInvocationAttribute(AndroidBuildResultReporter.TEST_RESULT_ID, TEST_RESULT_ID);
    }

    @Test
    public void testReportCoverage() throws IOException {
        // Prepare test data
        InputStream jacocoXmlStream = getClass().getResourceAsStream(JACOCO_XML_RESOURCE_PATH);
        InputStreamSource jacocoSource = new ByteArrayInputStreamSource(
                ByteStreams.toByteArray(jacocoXmlStream));
        LogFile fakeJacocoLogFile = new LogFile(JACOCO_XML_PATH, JACOCO_XML_URL, false, false);

        InputStreamSource mockHtmlReportSource = Mockito.mock(InputStreamSource.class);
        LogFile fakeHtmlLogFile = new LogFile(HTML_REPORT_PATH, HTML_REPORT_URL, false, false);

        // Set up mocks
        BlackboxCodeCoverageReporter reporter = Mockito.spy(new BlackboxCodeCoverageReporter());
        TestResultsBuilder results = Mockito.spy(new TestResultsBuilder());
        doReturn(results).when(reporter).internalCreateTestResultsBuilder();
        doReturn(true).when(reporter).postResults(any(TestResultsBuilder.class));

        // Simulate a test run
        reporter.invocationStarted(mContext);
        reporter.testLogSaved("coverage", LogDataType.EAR, mockHtmlReportSource, fakeHtmlLogFile);
        reporter.testLogSaved("coverage", LogDataType.JACOCO_XML, jacocoSource, fakeJacocoLogFile);
        reporter.invocationEnded(1000);

        // Verify that the correct results were posted
        Mockito.verify(reporter).postResults(eq(results));
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#VCardEntryCounter()", 1, 1, 1, 1, 2, 2,
                HTML_REPORT_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#getCount()", 1, 1, 1, 1, 2, 2,
                HTML_REPORT_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#onEntryEnded()", 2, 2, 1, 1, 4, 4,
                HTML_REPORT_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#onEntryStarted()", 1, 1, 1, 1, 1, 1,
                HTML_REPORT_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#onVCardEnded()", 1, 1, 1, 1, 1, 1,
                HTML_REPORT_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#onVCardStarted()", 1, 1, 1, 1, 1, 1,
                HTML_REPORT_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntry$PostalData#getCountry()", 1, 1, 1, 1, 2, 2,
                HTML_REPORT_URL + "/com.android.vcard/VCardEntry$PostalData.html");
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntry$PostalData#isEmpty()", 0, 7, 4, 12, 11, 27,
                HTML_REPORT_URL + "/com.android.vcard/VCardEntry$PostalData.html");
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.exception.VCardException#VCardException()", 0, 2, 0, 1, 0, 2,
                HTML_REPORT_URL + "/com.android.vcard.exception/VCardException.html");
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.exception.VCardException#VCardException(java.lang.String)",
                0, 2, 0, 1, 0, 2,
                HTML_REPORT_URL + "/com.android.vcard.exception/VCardException.html");
    }

    @Test
    public void testReportCoverage_noHtmlReport() throws IOException {
        // Prepare test data
        InputStream jacocoXmlStream = getClass().getResourceAsStream(JACOCO_XML_RESOURCE_PATH);
        InputStreamSource jacocoSource = new ByteArrayInputStreamSource(
                ByteStreams.toByteArray(jacocoXmlStream));
        LogFile fakeJacocoLogFile = new LogFile(JACOCO_XML_PATH, JACOCO_XML_URL,
                false, false);

        // Set up mocks
        BlackboxCodeCoverageReporter reporter = Mockito.spy(new BlackboxCodeCoverageReporter());
        TestResultsBuilder results = Mockito.spy(new TestResultsBuilder());
        doReturn(results).when(reporter).internalCreateTestResultsBuilder();
        doReturn(true).when(reporter).postResults(any(TestResultsBuilder.class));

        // Simulate a test run, but don't log any HTML report
        reporter.invocationStarted(mContext);
        reporter.testLogSaved("coverage", LogDataType.JACOCO_XML, jacocoSource, fakeJacocoLogFile);
        reporter.invocationEnded(1000);

        // Verify that the correct results were posted
        Mockito.verify(reporter).postResults(eq(results));
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#VCardEntryCounter()", 1, 1, 1, 1, 2, 2, null);
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#getCount()", 1, 1, 1, 1, 2, 2, null);
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#onEntryEnded()", 2, 2, 1, 1, 4, 4, null);
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#onEntryStarted()", 1, 1, 1, 1, 1, 1, null);
        Mockito.verify(results).addCoverageResult(
               "com.android.vcard.VCardEntryCounter#onVCardEnded()", 1, 1, 1, 1, 1, 1, null);
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#onVCardStarted()", 1, 1, 1, 1, 1, 1, null);
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntry$PostalData#getCountry()", 1, 1, 1, 1, 2, 2, null);
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.VCardEntry$PostalData#isEmpty()", 0, 7, 4, 12, 11, 27, null);
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.exception.VCardException#VCardException()", 0, 2, 0, 1, 0, 2,
                null);
        Mockito.verify(results).addCoverageResult(
                "com.android.vcard.exception.VCardException#VCardException(java.lang.String)",
                0, 2, 0, 1, 0, 2, null);
    }
}
