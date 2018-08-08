// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileSystemLogSaver;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Functional tests for {@link SpongeResultReporter}.
 * <p/>
 * Upload a result to sponge backend. Essentially requires manual inspection to verify it looks ok.
 * TODO: in future, query back sponge api/screen scrape HTML to validate data
 */
public class SpongeResultReporterFuncTest extends TestCase {

    private static final String LOG_TAG = "SpongeResultReporterFuncTest";
    private static final String PATH = "path";
    private static final String URL = "url";
    private SpongeResultReporter mResultReporter;
    private FileSystemLogSaver mLogSaver;
    private File mTmpReportDir;

    @SuppressWarnings("unused")
    private class StubLaunchControlResultReporter extends LaunchControlResultReporter {
        String mSummaryUrl;

        @Override
        public void reportSuccess(IBuildInfo buildInfo, String testReportDetailUrl,
                String testSummaryUrl, String genericTestReportUrl) {
            mSummaryUrl = testSummaryUrl;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mLogSaver = new FileSystemLogSaver();
        mTmpReportDir = FileUtil.createTempDir("sponge-func");
        OptionSetter setter = new OptionSetter(mLogSaver);
        setter.setOptionValue("log-file-path", mTmpReportDir.getAbsolutePath());
        mResultReporter = new SpongeResultReporter() {
        };
        mResultReporter.setSpongeBackend(SpongeResultReporter.BACKEND_QA);
        mResultReporter.setLogSaver(mLogSaver);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() throws Exception {
        FileUtil.recursiveDelete(mTmpReportDir);
        super.tearDown();
    }

    /**
     * A simple test to ensure expected output is generated for test run with a passed test and
     * failed test.
     */
    public void testPassFail() throws IOException {
        IBuildInfo mockBuildInfo = createMockBuild();

        Map<String, String> emptyMap = Collections.emptyMap();
        final TestIdentifier testId = new TestIdentifier("com.android.FooTest", "testFoo");
        final String trace = "this is a trace";
        final TestIdentifier testId2 = new TestIdentifier("com.android.FooTest", "testFoo2");
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", mockBuildInfo);
        mLogSaver.invocationStarted(context);
        mResultReporter.invocationStarted(context);
        mResultReporter.testRunStarted("testRun", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testFailed(testId, trace);
        mResultReporter.testEnded(testId, emptyMap);
        mResultReporter.testRunEnded(2000, emptyMap);

        mResultReporter.testRunStarted("testRun2", 1);
        mResultReporter.testStarted(testId2);
        mResultReporter.testEnded(testId2, emptyMap);
        mResultReporter.testRunEnded(1000, emptyMap);

        mResultReporter.invocationEnded(1);
        mLogSaver.invocationEnded(1);

        assertNotNull(mResultReporter.getInvocationId());
        // TODO: just check the length for now.  In future verify format and content.
        assertTrue(mResultReporter.getInvocationId().length() > 16);

        TestSummary summary = mResultReporter.getSummary();
        assertTrue(summary instanceof StatisticsTestSummary);
        StatisticsTestSummary statSummary = (StatisticsTestSummary)summary;
        String textStatsUrl = statSummary.getStatistics().getString();
        Log.i(LOG_TAG, String.format("Checking statistics URL %s", textStatsUrl));
        URL statsUrl = new URL(textStatsUrl);
        String statsData = StreamUtil.getStringFromStream(statsUrl.openStream());
        assertEquals("0.500000|0|1|2", statsData);
    }

    public void testFailureWithNullExceptionSendsData() throws IOException {
        final String trace = null;
        sendFailureReport(trace);
    }

    public void testFailureWithBlankExceptionSendsData() throws IOException {
        final String trace = "";
        sendFailureReport(trace);
    }

    public void testFailureWithExceptionSendsData() throws IOException {
        final String trace = "test.some.ValidException";
        sendFailureReport(trace);
    }

    public void testFailureWithFormattedExceptionAndNoMessageSendsData() throws IOException {
        final String trace = "test.some.ValidException: ";
        sendFailureReport(trace);
    }

    public void testFailureWithFullExceptionSendsData() throws IOException {
        final String trace =
                "test.some.ValidException: This is a test exception that also contains a message";
        sendFailureReport(trace);
    }

    /**
     * A simple test to ensure expected output is generated for test run with a incomplete tests
     */
    public void testIncomplete() throws IOException {
        IBuildInfo mockBuildInfo = createMockBuild();

        Map<String, String> emptyMap = Collections.emptyMap();
        final TestIdentifier testId = new TestIdentifier("com.android.FooTest", "testFoo");
        final TestIdentifier testId2 = new TestIdentifier("com.android.FooTest", "testFoo2");
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", mockBuildInfo);
        mLogSaver.invocationStarted(context);
        mResultReporter.invocationStarted(context);
        mResultReporter.testRunStarted("testRun", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testRunEnded(2000, emptyMap);

        mResultReporter.testRunStarted("testRun2", 1);
        mResultReporter.testStarted(testId2);
        mResultReporter.testRunEnded(1000, emptyMap);

        mResultReporter.invocationEnded(1);
        mLogSaver.invocationEnded(1);

        assertNotNull(mResultReporter.getInvocationId());
        // TODO: just check the length for now.  In future verify format and content.
        assertTrue(mResultReporter.getInvocationId().length() > 16);

        TestSummary summary = mResultReporter.getSummary();
        assertTrue(summary instanceof StatisticsTestSummary);
        StatisticsTestSummary statSummary = (StatisticsTestSummary)summary;
        String textStatsUrl = statSummary.getStatistics().getString();
        Log.i(LOG_TAG, String.format("Checking statistics URL %s", textStatsUrl));
        URL statsUrl = new URL(textStatsUrl);
        String statsData = StreamUtil.getStringFromStream(statsUrl.openStream());
        assertEquals("0.000000|0|0|0", statsData);
    }

    /**
     * Test saving an invocation failure to sponge
     */
    public void testInvocationFailed() {
        IBuildInfo mockBuildInfo = createMockBuild();
        Map<String, String> emptyMap = Collections.emptyMap();
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", mockBuildInfo);
        mLogSaver.invocationStarted(context);
        mResultReporter.invocationStarted(context);
        mResultReporter.testRunStarted("testRun", 1);
        mResultReporter.testRunEnded(1000, emptyMap);
        mResultReporter.invocationFailed(new RuntimeException("invocation failed!"));
        mResultReporter.invocationEnded(1000);
        mLogSaver.invocationEnded(1000);

        assertNotNull(mResultReporter.getInvocationId());
        // TODO: just check the length for now.  In future verify format and content.
        assertTrue(mResultReporter.getInvocationId().length() > 16);
    }

    /**
     * Returns a build info mock.
     */
    private IBuildInfo createMockBuild() {
        final String buildId = "88888";
        final String attr1Name = "foo1";
        final String attr1Value = "bar1";
        final String attr2Name = "foo2";
        final String attr2Value = "bar2";

        IBuildInfo mockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        EasyMock.expect(mockBuildInfo.getBuildId()).andStubReturn(buildId);
        EasyMock.expect(mockBuildInfo.getTestTag()).andStubReturn("mockTestTarget");
        EasyMock.expect(mockBuildInfo.getBuildTargetName()).andStubReturn("mockBuildName");
        EasyMock.expect(mockBuildInfo.getBuildBranch()).andStubReturn(null);
        EasyMock.expect(mockBuildInfo.getBuildFlavor()).andStubReturn(null);
        EasyMock.expect(mockBuildInfo.getDeviceSerial()).andStubReturn("serial");

        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(attr1Name, attr1Value);
        attributes.put(attr2Name, attr2Value);
        EasyMock.expect(mockBuildInfo.getBuildAttributes()).andReturn(attributes).anyTimes();
        EasyMock.replay(mockBuildInfo);
        return mockBuildInfo;
    }

    /** A test to ensure expected output is generated for the invocation with a log. */
    public void testStoreLog() {
        final String log = "this is a log";
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());
        mLogSaver.invocationStarted(context);
        mResultReporter.invocationStarted(context);

        mResultReporter.testLog("log_data", LogDataType.TEXT,
                new ByteArrayInputStreamSource(log.getBytes()));
        mResultReporter.testLogSaved("log_data", LogDataType.TEXT,
                new ByteArrayInputStreamSource(log.getBytes()),
                new LogFile(PATH, URL, false /* compressed */, true /* text */));
        mResultReporter.invocationEnded(1);
        mLogSaver.invocationEnded(1);

        assertNotNull(mResultReporter.getInvocationId());

        // TODO: verify upload
    }

    /** A test to ensure expected output is generated for the invocation with a png image log. */
    public void testStorePngLog() {
        InputStreamSource pngDataSource =
                new InputStreamSource() {

                    @Override
                    public InputStream createInputStream() {
                        return new BufferedInputStream(
                                getClass().getResourceAsStream("/testdata/screenshot.png"));
                    }

                    @Override
                    public void close() {
                        // ignore
                    }

                    @Override
                    public long size() {
                        // TODO implement this
                        return 0;
                    }
                };
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());
        mLogSaver.invocationStarted(context);
        mResultReporter.invocationStarted(context);

        mResultReporter.testLog("screenshot", LogDataType.PNG, pngDataSource);
        mResultReporter.testLogSaved("screenshot", LogDataType.PNG, pngDataSource,
                new LogFile(PATH, URL, true /* compressed */, false /* text */));
        mResultReporter.invocationEnded(1);
        mLogSaver.invocationEnded(1);

        assertNotNull(mResultReporter.getInvocationId());

        // TODO: verify upload
    }

    /**
     * A test to ensure expected output is generated which gives correct organization of invocation
     * logs and run logs for the invocation.
     */
    public void testLogOrganization() {
        final String invocation_log = "This is the invocation log.";
        final String run_log = "This is the test run log.";
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());
        mLogSaver.invocationStarted(context);
        mResultReporter.invocationStarted(context);

        mResultReporter.testLog(
                "invocation_log",
                LogDataType.TEXT,
                new ByteArrayInputStreamSource(invocation_log.getBytes()));
        mResultReporter.testLogSaved(
                "invocation_log",
                LogDataType.TEXT,
                new ByteArrayInputStreamSource(invocation_log.getBytes()),
                new LogFile(PATH, URL, false /* compressed */, true /* text */));
        mResultReporter.testRunStarted("run1", 0);
        mResultReporter.testLog(
                "run1_log", LogDataType.TEXT, new ByteArrayInputStreamSource(run_log.getBytes()));
        mResultReporter.testLogSaved(
                "run1_log",
                LogDataType.TEXT,
                new ByteArrayInputStreamSource(run_log.getBytes()),
                new LogFile(PATH, URL, false /* compressed */, true /* text */));
        mResultReporter.testRunEnded(1, Collections.emptyMap());
        mResultReporter.invocationEnded(1);
        mLogSaver.invocationEnded(1);

        assertNotNull(mResultReporter.getInvocationId());

        // TODO: verify upload
    }

    /**
     * Test log Organization for an invocation with invocation logs and run logs, without
     * duplicating logs under Build Logs tab.
     */
    public void testLogOrganization_noDuplication() {
        mResultReporter.setDuplicateAsBuildLogs(false);

        final String invocation_log = "This is the invocation log.";
        final String run_log = "This is the test run log.";
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());
        mLogSaver.invocationStarted(context);
        mResultReporter.invocationStarted(context);

        mResultReporter.testLog(
                "invocation_log",
                LogDataType.TEXT,
                new ByteArrayInputStreamSource(invocation_log.getBytes()));
        mResultReporter.testLogSaved(
                "invocation_log",
                LogDataType.TEXT,
                new ByteArrayInputStreamSource(invocation_log.getBytes()),
                new LogFile(PATH, URL, false /* compressed */, true /* text */));
        mResultReporter.testRunStarted("run1", 0);
        mResultReporter.testLog(
                "run1_log", LogDataType.TEXT, new ByteArrayInputStreamSource(run_log.getBytes()));
        mResultReporter.testLogSaved(
                "run1_log",
                LogDataType.TEXT,
                new ByteArrayInputStreamSource(run_log.getBytes()),
                new LogFile(PATH, URL, false /* compressed */, true /* text */));
        mResultReporter.testRunEnded(1, Collections.emptyMap());
        mResultReporter.invocationEnded(1);
        mLogSaver.invocationEnded(1);

        assertNotNull(mResultReporter.getInvocationId());

        // TODO: verify upload
    }

    /** Test to ensure an invocation with many test results and logs can be handled. */
    public void testLargeResults() {
        Map<String, String> emptyMap = Collections.emptyMap();
        StringBuilder logData = new StringBuilder();
        // About 200K log data, will be truncated into a 96K snippet
        for (int i = 0; i < 2048; i++) {
            logData.append("this is a log data repeated and this log data is almost");
            logData.append("approximately two hundred bytes in lengthrr");
        }
        InputStreamSource dataSource =
                new ByteArrayInputStreamSource(logData.toString().getBytes());

        // Generate a 39M invocation which exceeds Sponge HTTP API request limit. The invocation
        // will be reported to Sponge using multiple updates.
        IBuildInfo mockBuildInfo = createMockBuild();
        mResultReporter.setTestSize("0");
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", mockBuildInfo);
        mLogSaver.invocationStarted(context);
        mResultReporter.invocationStarted(context);

        // do 8 runs
        for (int i = 0; i < 8; i++) {
            mResultReporter.testRunStarted("testRun" + i, 100);
            // each run has 100 tests
            for (int j = 0; j < 100; j++) {
                final TestIdentifier testId = new TestIdentifier("com.android.FooTest" + i,
                        "testFoo" + j);
                mResultReporter.testStarted(testId);
                if (i==0 && j==0) {
                    mResultReporter.testFailed(testId, "this is a trace");
                }
                mResultReporter.testEnded(testId, emptyMap);
            }
            // each run has 25 logs
            for (int j = 0; j < 25; ++j) {
                mResultReporter.testLog(
                        "log_data" + Integer.toString(j) + "file", LogDataType.TEXT, dataSource);
                mResultReporter.testLogSaved(
                        "log_data" + Integer.toString(j) + "file",
                        LogDataType.TEXT,
                        dataSource,
                        new LogFile(PATH, URL, false /* compressed */, true /* text */));
            }
            mResultReporter.testRunEnded(0, emptyMap);
        }
        mResultReporter.invocationEnded(1);
        mLogSaver.invocationEnded(1);

        assertNotNull(mResultReporter.getInvocationId());
        // TODO: just check the length for now.  In future verify format and content.
        assertTrue(mResultReporter.getInvocationId().length() > 16);

        // TODO: verify upload
    }

    /** Test creation of Logfile Index. */
    public void testSendTestLogIndex() {
        mResultReporter.setSendTestLogIndex(true);

        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());

        mLogSaver.invocationStarted(context);
        mResultReporter.invocationStarted(context);

        final String logData = "this is the log data";
        InputStreamSource txtDataSource = new ByteArrayInputStreamSource(logData.getBytes());
        mResultReporter.testLog("txt_log1", LogDataType.TEXT, txtDataSource);
        mResultReporter.testLogSaved(
                "txt_log1",
                LogDataType.TEXT,
                txtDataSource,
                new LogFile(
                        "report_path1", "report_url1", false /* compressed */, true /* text */));
        mResultReporter.testLog("txt_log2", LogDataType.TEXT, txtDataSource);
        mResultReporter.testLogSaved(
                "txt_log2",
                LogDataType.TEXT,
                txtDataSource,
                new LogFile(
                        "report_path2", "report_url2", false /* compressed */, true /* text */));

        mResultReporter.invocationEnded(1);
        mLogSaver.invocationEnded(1);

        assertNotNull(mResultReporter.getInvocationId());

        // TODO: verify upload
    }

    /** Test creation of Logfile Index with multiple pages. */
    public void testSendTestLogIndex_multiplePages() {
        mResultReporter.setSendTestLogIndex(true);

        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());

        mLogSaver.invocationStarted(context);
        mResultReporter.invocationStarted(context);

        final String logData = "this is the log data";
        String longUrl = new String(new char[80 * 1024]).replace("\0", "o");
        InputStreamSource txtDataSource = new ByteArrayInputStreamSource(logData.getBytes());
        mResultReporter.testLog("txt_log1", LogDataType.TEXT, txtDataSource);
        mResultReporter.testLogSaved(
                "txt_log1",
                LogDataType.TEXT,
                txtDataSource,
                new LogFile("report_path1", longUrl, false /* compressed */, true /* text */));
        mResultReporter.testLog("txt_log2", LogDataType.TEXT, txtDataSource);
        mResultReporter.testLogSaved(
                "txt_log2",
                LogDataType.TEXT,
                txtDataSource,
                new LogFile("report_path2", longUrl, false /* compressed */, true /* text */));

        mResultReporter.invocationEnded(1);
        mLogSaver.invocationEnded(1);

        assertNotNull(mResultReporter.getInvocationId());

        // TODO: verify upload
    }

    /**
     * Send test results that include a failed test with the given exception trace and make sure
     * they are sent correctly with no errors.
     */
    private void sendFailureReport(String trace) throws IOException {
        IBuildInfo mockBuildInfo = createMockBuild();

        Map<String, String> emptyMap = Collections.emptyMap();

        final TestIdentifier testId = new TestIdentifier("com.android.FooTest", "testFoo");
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", mockBuildInfo);
        mLogSaver.invocationStarted(context);
        mResultReporter.invocationStarted(context);
        mResultReporter.testRunStarted("testRun", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testFailed(testId, trace);
        mResultReporter.testEnded(testId, emptyMap);
        mResultReporter.testRunEnded(1000, emptyMap);
        mResultReporter.invocationEnded(1);
        mLogSaver.invocationEnded(1);

        assertNotNull(mResultReporter.getInvocationId());
        // TODO: verify format and content.
        assertTrue(mResultReporter.getInvocationId().length() > 16);

        TestSummary summary = mResultReporter.getSummary();
        assertTrue(summary instanceof StatisticsTestSummary);
        StatisticsTestSummary statSummary = (StatisticsTestSummary) summary;
        String textStatsUrl = statSummary.getStatistics().getString();
        Log.i(LOG_TAG, String.format("Checking statistics URL %s", textStatsUrl));
        URL statsUrl = new URL(textStatsUrl);
        String statsData = StreamUtil.getStringFromStream(statsUrl.openStream());
        assertEquals("0.000000|0|1|1", statsData);
    }
}
