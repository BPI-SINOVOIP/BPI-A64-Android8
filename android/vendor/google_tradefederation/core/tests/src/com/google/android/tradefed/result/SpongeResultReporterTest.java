// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.IGlobalConfiguration;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.util.IRunUtil;

import com.google.android.tradefed.result.SpongeResultReporter.LogFileData;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.kxml2.io.KXmlSerializer;
import org.mockito.Mockito;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;


/**
 * Unit tests for {@link SpongeResultReporter}.
 */
public class SpongeResultReporterTest extends TestCase {

    private static final String REPORT_PATH = "report_path";
    private static final String REPORT_URL = "report_url";
    private MockSpongeResultReporter mResultReporter;
    private ILogSaver mMockLogSaver;

    class MockLogSaver implements ILogSaver {
        @Override
        public LogFile saveLogData(String dataName, LogDataType dataType,
                InputStream dataStream) {
            return new LogFile(REPORT_PATH, REPORT_URL, dataType.isCompressed(), dataType.isText());
        }

        @Override
        public LogFile saveLogDataRaw(String dataName, String ext, InputStream dataStream) {
            return new LogFile(REPORT_PATH, REPORT_URL, false, false);
        }

        @Override
        public LogFile getLogReportDir() {
            return new LogFile(REPORT_PATH, REPORT_URL, false, false);
        }

        @Override
        public void invocationStarted(IInvocationContext context) {
            // Ignore
        }

        @Override
        public void invocationEnded(long elapsedTime) {
            // Ignore
        }
    }

    private class MockUrlConnection extends HttpURLConnection {

        private IOException mException = null;
        private ByteArrayOutputStream mHttpOutputStream;

        void setInputException(IOException e) {
            mException  = e;
        }

        protected MockUrlConnection(URL u) {
            super(u);
            mHttpOutputStream = new ByteArrayOutputStream();
        }

        public MockUrlConnection() throws MalformedURLException {
            this(new URL("http://dummy"));
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() throws IOException {
        }

        /**
         * This stream will consume the client's uploaded data
         */
        @Override
        public OutputStream getOutputStream() throws IOException {
            return mHttpOutputStream;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (mException != null) {
                throw mException;
            }
            return new ByteArrayInputStream("foo".getBytes());
        }

        @Override
        public int getResponseCode() {
            return 200;
        }
    }

    class MockSpongeResultReporter extends SpongeResultReporter {
        private MockUrlConnection mMockUrlConnection;
        private IGlobalConfiguration mMockGlobalConfig;

        public MockSpongeResultReporter() throws MalformedURLException {
            mMockUrlConnection = new MockUrlConnection();
            mMockGlobalConfig = EasyMock.createMock(IGlobalConfiguration.class);
            replayMocks(mMockGlobalConfig);
        }

        @Override
        MockUrlConnection createSpongeConnection(String remoteFile) throws IOException {
            return mMockUrlConnection;
        }

        @Override
        long getTimestamp() {
            return -1;
        }

        @Override
        int getMaxRequestSize() {
            return 200 * 1024;
        }

        @Override
        OutputStream createGzipOutputStream(OutputStream outputStream) {
            return outputStream; // Don't gzip the contents for this test.
        }

        @Override
        IGlobalConfiguration getGlobalConfig() {
            return mMockGlobalConfig;
        }

        @Override
        String getHostName() throws UnknownHostException {
            return "foo";
        }

        MockUrlConnection getSpongeConnection() {
            return mMockUrlConnection;
        }

        /** Gets the output produced. */
        public String getOutput() throws IOException {
            return mMockUrlConnection.getOutputStream().toString();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockLogSaver = new MockLogSaver();
        mResultReporter = new MockSpongeResultReporter();
        mResultReporter.setLogSaver(mMockLogSaver);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }


    public void testNullExceptionParsedCorrectly() throws IOException, XPathExpressionException {

        final String message = null;
        testExceptionMessageParsedCorrectly(message, "EMPTY");
        testExceptionTypeParsedCorrectly(message, "exception");
    }

    public void testEmptyExceptionParsedCorrectly() throws IOException, XPathExpressionException {

        final String message = "";
        testExceptionMessageParsedCorrectly(message, "EMPTY");
        testExceptionTypeParsedCorrectly(message, "exception");
    }

    public void testNullTerminatorExceptionParsedCorrectly()
            throws IOException, XPathExpressionException {

        // The \0 character is not allowed in XML
        // verify we can handle it without crashing
        final String message = "\0";
        testExceptionMessageParsedCorrectly(message, "<\\0>");
        testExceptionTypeParsedCorrectly(message, "exception");
    }

    public void testShortExceptionNoTypeNoColonParsedCorrectly()
            throws IOException, XPathExpressionException {

        final String message = "Plain string exception message - something went wrong.";
        testExceptionMessageParsedCorrectly(message, message);
        testExceptionTypeParsedCorrectly(message, "exception");
    }

    public void testShortExceptionNoTypeWithColonParsedCorrectly()
            throws IOException, XPathExpressionException {

        final String message =
                "This is a message with a colon inside: it has no type, "
                        + "but should still be parsed.";
        testExceptionMessageParsedCorrectly(message, message);
        testExceptionTypeParsedCorrectly(message, "exception");
    }

    public void testExceptionWithKeywordNoColonParsedCorrectly()
            throws IOException, XPathExpressionException {

        final String message = "There was an Error or Failure but not a java exception.";
        testExceptionMessageParsedCorrectly(message, message);
        testExceptionTypeParsedCorrectly(message, "exception");
    }

    public void testShortExceptionWithTypeParsedCorrectly()
            throws IOException, XPathExpressionException {

        final String expectedType = "java.lang.SomeOtherException";
        final String expectedMessage = "Some Error Happened Here";
        final String message = expectedType + ": " + expectedMessage;

        testExceptionMessageParsedCorrectly(message, expectedMessage);
        testExceptionTypeParsedCorrectly(message, expectedType);
    }

    public void testExceptionWithTypeAndBacktraceParsedCorrectly()
            throws IOException, XPathExpressionException {

        final String expectedType = "java.lang.NullPointerException";
        final String message =
                expectedType
                        + "\n"
                        + "at MyClass.eat(MyClass.java:9)\n"
                        + "at MyClass.the(MyClass.java:6)\n"
                        + "at MyClass.path(MyClass.java:3)";

        testExceptionMessageParsedCorrectly(message, "EMPTY");
        testExceptionTypeParsedCorrectly(message, expectedType);
    }

    public void testExceptionWithTypeMessageAndBacktraceParsedCorrectly()
            throws IOException, XPathExpressionException {

        final String expectedType = "java.lang.SomeOtherException";
        final String expectedMessage = "Some Error Happened Here";
        final String message =
                expectedType
                        + ": "
                        + expectedMessage
                        + "\n"
                        + "at MyClass.eat(MyClass.java:9)\n"
                        + "at MyClass.the(MyClass.java:6)\n"
                        + "at MyClass.path(MyClass.java:3)";

        testExceptionMessageParsedCorrectly(message, expectedMessage);
        testExceptionTypeParsedCorrectly(message, expectedType);
    }


    /** Ensure expected output is generated for test run with no tests. */
    public void testEmptyGeneration() throws IOException, XPathExpressionException {
        replayMocks();
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());
        mResultReporter.invocationStarted(context);
        mResultReporter.invocationEnded(1);
        final String output = mResultReporter.getOutput();
        // TODO: validate output against schema
        assertXmlContainsValue(output, "xml/server", "backend");
        assertXmlContainsValue(output, "xml/invocation/cl", "-1");
        assertXmlContainsNode(output, "xml/invocation/user");
        assertXmlContainsNode(output, "xml/invocation/hostname");
    }

    /** Ensure build info attributes are dumped to output */
    public void testBuildInfo() throws IOException, XPathExpressionException {
        final String buildId = "88888";
        final String attr1Name = "foo1";
        final String attr1Value = "bar1";
        final String attr2Name = "foo2";
        final String attr2Value = "bar2";

        IBuildInfo mockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        EasyMock.expect(mockBuildInfo.getBuildId()).andReturn(buildId).anyTimes();
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(attr1Name, attr1Value);
        attributes.put(attr2Name, attr2Value);
        attributes.put("NO_VALUE", null);
        EasyMock.expect(mockBuildInfo.getBuildAttributes()).andReturn(attributes);
        EasyMock.expect(mockBuildInfo.getTestTag()).andReturn("foo").anyTimes();
        EasyMock.expect(mockBuildInfo.getBuildFlavor()).andReturn(null);
        EasyMock.expect(mockBuildInfo.getBuildBranch()).andReturn(null);
        EasyMock.expect(mockBuildInfo.getDeviceSerial()).andReturn(null);

        replayMocks(mockBuildInfo);
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", mockBuildInfo);
        mResultReporter.invocationStarted(context);
        mResultReporter.invocationEnded(1);
        String output = mResultReporter.getOutput();

        assertXmlContainsValue(output, "xml/invocation/cl", buildId);
        assertXmlContainsValue(output, "xml/invocation/configuration_value/name", attr1Name);
        assertXmlContainsValue(output, "xml/invocation/configuration_value/value", attr1Value);
        assertXmlContainsValue(output, "xml/invocation/configuration_value/name", attr2Name);
        assertXmlContainsValue(output, "xml/invocation/configuration_value/value", attr2Value);
        // only the two configuration_value node above exists the NO_VALUE one is not created.
        assertXmlContainsNodeWithOccurrence(output, "xml/invocation/configuration_value/name", 2);
    }

    /** Ensure invocation context attributes are added to the output */
    public void testInvocationContext() throws IOException, XPathExpressionException {
        final String buildId = "88888";
        final String attr1Name = "foo1";
        final String attr1Value = "bar1";
        final String attr2Name = "foo2";
        final String attr2Value = "bar2";

        IBuildInfo mockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        EasyMock.expect(mockBuildInfo.getBuildId()).andReturn(buildId).anyTimes();
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(attr1Name, attr1Value);
        attributes.put(attr2Name, attr2Value);
        EasyMock.expect(mockBuildInfo.getBuildAttributes()).andReturn(attributes);
        EasyMock.expect(mockBuildInfo.getTestTag()).andReturn("foo").anyTimes();
        EasyMock.expect(mockBuildInfo.getBuildFlavor()).andReturn(null);
        EasyMock.expect(mockBuildInfo.getBuildBranch()).andReturn(null);
        EasyMock.expect(mockBuildInfo.getDeviceSerial()).andReturn(null);

        replayMocks(mockBuildInfo);
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", mockBuildInfo);
        context.addInvocationAttribute("gerrit_cl", "123456");
        mResultReporter.invocationStarted(context);
        mResultReporter.invocationEnded(1);
        String output = mResultReporter.getOutput();

        assertXmlContainsValue(output, "xml/invocation/cl", buildId);
        assertXmlContainsValue(output, "xml/invocation/configuration_value/name", attr1Name);
        assertXmlContainsValue(output, "xml/invocation/configuration_value/value", attr1Value);
        assertXmlContainsValue(output, "xml/invocation/configuration_value/name", attr2Name);
        assertXmlContainsValue(output, "xml/invocation/configuration_value/value", attr2Value);
        assertXmlContainsValue(output, "xml/invocation/label", "gerrit_cl/123456");
    }

    /** Ensure expected output is generated for test run with a single passed test. */
    public void testSinglePass() throws IOException, XPathExpressionException {
        Map<String, String> emptyMap = Collections.emptyMap();
        final String className = "FooTest";
        final String testName = "testFoo";
        final TestIdentifier testId = new TestIdentifier(className, testName);

        replayMocks();
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());
        mResultReporter.invocationStarted(context);
        mResultReporter.testRunStarted("testrun", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, emptyMap);
        mResultReporter.testRunEnded(2, emptyMap);
        mResultReporter.invocationEnded(1);
        String output = mResultReporter.getOutput();

        // check that individual test is present
        assertXmlContainsValue(output, "xml/invocation/target_result/status", "0");
        assertXmlContainsNode(output, "xml/invocation/target_result/test_result");
        assertXmlContainsValue(
                output, "xml/invocation/target_result/test_result/test_case_count", "1");
        assertXmlContainsValue(
                output, "xml/invocation/target_result/test_result/child/class_name", className);
        assertXmlContainsValue(
                output,
                "xml/invocation/target_result/test_result/child/name",
                String.format("%s.%s", className, testName));
    }

    /** Ensure expected output is generated for test run with a single failed test. */
    public void testSingleFail() throws IOException, XPathExpressionException {
        Map<String, String> emptyMap = Collections.emptyMap();
        final String className = "FooTest";
        final String testName = "testFoo";
        final TestIdentifier testId = new TestIdentifier(className, testName);
        final String trace = "this is a trace";
        replayMocks();

        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());
        mResultReporter.invocationStarted(context);
        mResultReporter.testRunStarted("testrun", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testFailed(testId, trace);
        mResultReporter.testEnded(testId, emptyMap);
        mResultReporter.testRunEnded(2, emptyMap);
        mResultReporter.invocationEnded(1);
        String output = mResultReporter.getOutput();

        assertXmlContainsValue(output, "xml/invocation/target_result/status", "1");
        assertXmlContainsNode(output, "xml/invocation/target_result/test_result");
        assertXmlContainsValue(
                output, "xml/invocation/target_result/test_result/test_case_count", "1");
        assertXmlContainsValue(
                output, "xml/invocation/target_result/test_result/child/class_name", className);
        assertXmlContainsValue(
                output,
                "xml/invocation/target_result/test_result/child/name",
                String.format("%s.%s", className, testName));
        assertXmlContainsValue(
                output, "xml/invocation/target_result/test_result/failure_count", "1");
        assertXmlContainsNode(
                output, "xml/invocation/target_result/test_result/child/test_failure");
        assertXmlContainsValue(
                output,
                "xml/invocation/target_result/test_result/child/test_failure/detail",
                trace);
    }

    /** Test mechanism to update large invocations in chunks. */
    public void testMultiUpload() throws IOException {
        Map<String, String> emptyMap = Collections.emptyMap();
        StringBuilder logData = new StringBuilder();
        // About 200K log data, will be truncated into a 96K snippet
        for (int i = 0; i < 2048; i++) {
            logData.append("this is a log data repeated and this log data is almost");
            logData.append("approximately two hundred bytes in lengthrr");
        }
        InputStreamSource dataSource =
                new ByteArrayInputStreamSource(logData.toString().getBytes());

        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());
        mResultReporter.invocationStarted(context);

        // Generate a large invocation which exceeds Sponge HTTP API request limit. The invocation
        // will be reported using multiple updates.
        for (int i = 0; i < 3; i++) {
            mResultReporter.testRunStarted("testRun" + i, 0);
            mResultReporter.testLogSaved(
                    "log_data" + Integer.toString(i),
                    LogDataType.TEXT,
                    dataSource,
                    new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));
            mResultReporter.testRunEnded(0, emptyMap);
        }
        mResultReporter.invocationEnded(1);
        String output = mResultReporter.getOutput();

        String regex =
                ".*<xml>.*<target_result>.*</target_result>.*<target_result>.*</target_result>"
                        + ".*<target_result>.*</target_result>.*<target_result>"
                        + ".*</target_result>.*<\\/xml>.*"
                        + "<xml>.*<target_result>.*</target_result>.*</xml>.*"
                        + "<xml>.*<target_result>.*</target_result>.*</xml>";
        String invocationHeaderXmlRegex =
                ".*<?xml\\s.*>.*"
                        + "<xml>.*"
                        + "<server>.*</server>.*"
                        + "<invocation>.*"
                        + "<id>.*</id>.*<run_date>.*</run_date>.*<user>.*</user>.*"
                        + "</invocation>.*"
                        + "</xml>";
        assertHasPattern(output, regex);
        assertHasPattern(output, invocationHeaderXmlRegex);
    }

    /** Ensure expected output is generated for test run with a png image log. */
    public void testStorePngLog() throws IOException {
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
        mResultReporter.invocationStarted(context);
        mResultReporter.testLogSaved("screenshot", LogDataType.PNG, pngDataSource,
                new LogFile(REPORT_PATH, REPORT_URL, true /* compressed */, false /* text */));
        mResultReporter.invocationEnded(1);

        String output = mResultReporter.getOutput();
        // expect just the log reference - no snippet
        assertHasPair(
                output,
                "large_text",
                "screenshot.png",
                cdata(String.format("Full log: %s", REPORT_URL)));
        assertHasPair(
                output,
                "large_text",
                "screenshot",
                cdata(String.format("Full log: %s", REPORT_URL)));
    }

    /** Ensure expected output is generated for test run with text log data */
    public void testStoreTxtLog() throws IOException {
        final String logData = "this is the log data";
        InputStreamSource txtDataSource = new ByteArrayInputStreamSource(logData.getBytes());

        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());
        mResultReporter.invocationStarted(context);
        mResultReporter.testLogSaved("txt_log", LogDataType.TEXT, txtDataSource,
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));
        mResultReporter.invocationEnded(1);

        String output = mResultReporter.getOutput();
        // expect just the log reference and snippet
        assertHasPair(
                output,
                "large_text",
                "txt_log.txt",
                cdata(String.format("Full log: %s\n\n%s", REPORT_URL, logData)));
        assertHasPair(
                output,
                "large_text",
                "txt_log",
                cdata(String.format("Full log: %s\n\n%s", REPORT_URL, logData)));
    }

    /**
     * Ensure that we still report to Sponge if a particular file fails to be saved. The file itself
     * will not be available.
     */
    public void testStoreLog_failed() throws IOException, XPathExpressionException {
        final String logData = "this is the log data";
        InputStreamSource txtDataSource = new ByteArrayInputStreamSource(logData.getBytes());

        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());
        MockSpongeResultReporter mSpyReporter = Mockito.spy(mResultReporter);
        doThrow(new IOException()).when(mSpyReporter).createTmpSnippetFile();
        mSpyReporter.invocationStarted(context);
        mSpyReporter.testLogSaved(
                "txt_log",
                LogDataType.TEXT,
                txtDataSource,
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));
        mSpyReporter.invocationEnded(1);
        String output = mSpyReporter.getOutput();
        // assert that report does not contain a node for the logged file since it was cleaned up.
        AssertionError expect = null;
        try {
            assertHasPair(
                    output,
                    "large_text",
                    "txt_log.txt",
                    cdata(String.format("Full log: %s\n\n%s", REPORT_URL, logData)));
        } catch (AssertionError expected) {
            expect = expected;
        }
        assertNotNull("Should have thrown an exception.", expect);
        // We still have an invocation tag.
        assertXmlContainsNode(output, "xml/invocation");
    }

    /** Ensure file name is sanitized for png logs */
    public void testLogFileDataSanitizeName_pngType() throws IOException {
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

        LogFileData textData =
                mResultReporter
                .new LogFileData(
                        LogDataType.PNG,
                        pngDataSource,
                        new LogFile(
                                REPORT_PATH, REPORT_URL, true /* compressed */, false /* text */));
        try {
            assertEquals("screenshot", textData.sanitizeName("screenshot"));
            assertEquals("screenshot", textData.sanitizeName("screenshot.png"));
            assertEquals("screenshot.hasDot.dat", textData.sanitizeName("screenshot.hasDot.png"));
            assertEquals("screenshot.dat", textData.sanitizeName("screenshot.dat"));
        } finally {
            textData.cleanUp();
        }
    }

    /** Ensure file name is sanitized for logs of unknown type */
    public void testLogFileDataSanitizeName_unknownType() throws IOException {
        final String logData = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><node/>";
        InputStreamSource logDataSource = new ByteArrayInputStreamSource(logData.getBytes());

        LogFileData textData =
                mResultReporter
                .new LogFileData(
                        LogDataType.UNKNOWN,
                        logDataSource,
                        new LogFile(
                                REPORT_PATH, REPORT_URL, false /* compressed */, false /* text */));
        try {
            assertEquals("log.dat", textData.sanitizeName("log"));
            assertEquals("log.uix.dat", textData.sanitizeName("log.uix"));
            assertEquals("log.dat", textData.sanitizeName("log.dat"));
        } finally {
            textData.cleanUp();
        }
    }

    /** Test log Organization for an invocation with invocation logs and run logs. */
    public void testLogOrganization() throws IOException, XPathExpressionException {
        final String invocationLogData = "invocation log data";
        final String runLogData = "run log data";

        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());

        mResultReporter.invocationStarted(context);
        mResultReporter.testLogSaved(
                "invocation_log",
                LogDataType.TEXT,
                new ByteArrayInputStreamSource(invocationLogData.getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));
        mResultReporter.testRunStarted("run1", 0);
        mResultReporter.testLogSaved(
                "run1_log",
                LogDataType.TEXT,
                new ByteArrayInputStreamSource(runLogData.getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));
        mResultReporter.testRunEnded(1, Collections.emptyMap());
        mResultReporter.invocationEnded(2);

        String output = mResultReporter.getOutput();

        assertXmlContainsNodeWithOccurrence(output, "xml/invocation/large_text", 2);
        assertXmlContainsNodeWithOccurrence(output, "xml/invocation/target_result", 2);
        assertXmlContainsNodeWithOccurrence(
                output, "xml/invocation/target_result[1]/large_text", 1);
        assertXmlContainsNodeWithOccurrence(
                output, "xml/invocation/target_result[2]/large_text", 1);

        assertHasPair(
                output,
                "large_text",
                "invocation_log.txt",
                cdata(String.format("Full log: %s\n\n%s", REPORT_URL, invocationLogData)));
        assertHasPair(
                output,
                "large_text",
                "run1_log.txt",
                cdata(String.format("Full log: %s\n\n%s", REPORT_URL, runLogData)));
        assertHasPair(
                output,
                "large_text",
                "invocation_log",
                cdata(String.format("Full log: %s\n\n%s", REPORT_URL, invocationLogData)));
        assertHasPair(
                output,
                "large_text",
                "run1_log",
                cdata(String.format("Full log: %s\n\n%s", REPORT_URL, runLogData)));
    }

    /**
     * Test log Organization for an invocation with invocation logs and run logs, without
     * duplicating logs under Build Logs tab.
     */
    public void testLogOrganization_noDuplication() throws IOException, XPathExpressionException {
        mResultReporter.setDuplicateAsBuildLogs(false);

        final String invocationLogData = "invocation log data";
        final String runLogData = "run log data";

        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());

        mResultReporter.invocationStarted(context);
        mResultReporter.testLogSaved(
                "invocation_log",
                LogDataType.TEXT,
                new ByteArrayInputStreamSource(invocationLogData.getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));
        mResultReporter.testRunStarted("run1", 0);
        mResultReporter.testLogSaved(
                "run1_log",
                LogDataType.TEXT,
                new ByteArrayInputStreamSource(runLogData.getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));
        mResultReporter.testRunEnded(1, Collections.emptyMap());
        mResultReporter.invocationEnded(2);

        String output = mResultReporter.getOutput();

        assertXmlContainsNodeWithOccurrence(output, "xml/invocation/large_text", 0);
        assertXmlContainsNodeWithOccurrence(output, "xml/invocation/target_result", 2);
        assertXmlContainsNodeWithOccurrence(
                output, "xml/invocation/target_result[1]/large_text", 1);
        assertXmlContainsNodeWithOccurrence(
                output, "xml/invocation/target_result[2]/large_text", 1);

        assertHasPair(
                output,
                "large_text",
                "invocation_log",
                cdata(String.format("Full log: %s\n\n%s", REPORT_URL, invocationLogData)));
        assertHasPair(
                output,
                "large_text",
                "run1_log",
                cdata(String.format("Full log: %s\n\n%s", REPORT_URL, runLogData)));
    }

    /** Test creation of Logfile Index. */
    public void testSendTestLogIndex() throws IOException {
        mResultReporter.setSendTestLogIndex(true);

        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());

        mResultReporter.invocationStarted(context);

        final String logData = "this is the log data";
        InputStreamSource txtDataSource = new ByteArrayInputStreamSource(logData.getBytes());
        mResultReporter.testLogSaved(
                "txt_log1",
                LogDataType.TEXT,
                txtDataSource,
                new LogFile(
                        "report_path1", "report_url1", false /* compressed */, true /* text */));
        mResultReporter.testLogSaved(
                "txt_log2",
                LogDataType.TEXT,
                txtDataSource,
                new LogFile(
                        "report_path2", "report_url2", false /* compressed */, true /* text */));

        mResultReporter.invocationEnded(1);

        String output = mResultReporter.getOutput();
        assertHasPair(
                output,
                "large_text",
                "Logfile Index",
                "txt_log1: report_url1\ntxt_log2: report_url2\n");
        assertHasPair(
                output,
                "large_text",
                "txt_log1",
                cdata(String.format("Full log: %s\n\n%s", "report_url1", logData)));
        assertHasPair(
                output,
                "large_text",
                "txt_log2",
                cdata(String.format("Full log: %s\n\n%s", "report_url2", logData)));
    }

    /** Test creation of Logfile Index with multiple pages. */
    public void testSendTestLogIndex_multiplePages() throws IOException {
        mResultReporter.setSendTestLogIndex(true);

        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());

        mResultReporter.invocationStarted(context);

        final String logData = "this is the log data";
        String longUrl = new String(new char[80 * 1024]).replace("\0", "o");
        InputStreamSource txtDataSource = new ByteArrayInputStreamSource(logData.getBytes());
        mResultReporter.testLogSaved(
                "txt_log1",
                LogDataType.TEXT,
                txtDataSource,
                new LogFile("report_path1", longUrl, false /* compressed */, true /* text */));
        mResultReporter.testLogSaved(
                "txt_log2",
                LogDataType.TEXT,
                txtDataSource,
                new LogFile("report_path2", longUrl, false /* compressed */, true /* text */));

        mResultReporter.invocationEnded(1);

        String output = mResultReporter.getOutput();
        assertHasPair(
                output,
                "large_text",
                "Logfile Index (Page 1 of 2)",
                String.format("txt_log1: %s", longUrl));
        assertHasPair(
                output,
                "large_text",
                "Logfile Index (Page 2 of 2)",
                String.format("txt_log2: %s", longUrl));
        assertHasPair(
                output,
                "large_text",
                "txt_log1",
                cdata(String.format("Full log: %s\n\n%s", longUrl, logData)));
        assertHasPair(
                output,
                "large_text",
                "txt_log2",
                cdata(String.format("Full log: %s\n\n%s", longUrl, logData)));
    }

    /** Test {@link SpongeResultReporter#getSummary()} when results can not be stored in sponge */
    public void testGetSummary_spongeFail() {
        mResultReporter.getSpongeConnection().setInputException(new IOException("spongefail"));
        mResultReporter.setMaxHttpAttempts(1);
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());
        mResultReporter.invocationStarted(context);
        mResultReporter.invocationEnded(1000);

        assertNull(mResultReporter.getInvocationId());
        assertNotNull(mResultReporter.getSummary());
        assertEquals(REPORT_URL, mResultReporter.getSummary().getSummary().getString());
    }

    /** Test {@link SpongeResultReporter#sanitizedSnippet(String)} removes illegal xml characters */
    public void testSanitizeSnippet() throws Exception {
        String content = "NfcAdaptation: find found NFA_PROPRIETARY_CFG=^E��^F��p��\n";
        content = mResultReporter.sanitizedSnippet(content);
        assertEquals("NfcAdaptation: find found NFA_PROPRIETARY_CFG=^E??^F??p??\n", content);
    }

    /**
     * Test that when the posting fails first then we retry removing the snippets, and only the full
     * log will show in the xml.
     */
    public void testStoreTxtLog_failSnippet() throws IOException {
        final Boolean[] shouldFail = new Boolean[1];
        shouldFail[0] = true;
        mResultReporter =
                new MockSpongeResultReporter() {
                    @Override
                    String createSpongeInvocation(long elapsedTime, long timestamp) {
                        if (shouldFail[0]) {
                            shouldFail[0] = false;
                            return null;
                        } else {
                            return super.createSpongeInvocation(elapsedTime, timestamp);
                        }
                    }

                    @Override
                    IRunUtil getRunUtil() {
                        return mock(IRunUtil.class);
                    }
                };
        mResultReporter.setLogSaver(mMockLogSaver);
        final String logData = "this is the log data";
        InputStreamSource txtDataSource = new ByteArrayInputStreamSource(logData.getBytes());

        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo());
        mResultReporter.invocationStarted(context);
        mResultReporter.testLogSaved(
                "txt_log",
                LogDataType.TEXT,
                txtDataSource,
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));
        mResultReporter.invocationEnded(1);

        String output = mResultReporter.getOutput();
        // expect just the log reference and snippet
        assertHasPair(
                output,
                "large_text",
                "txt_log.txt",
                cdata(String.format("Full log: %s", REPORT_URL)));
        assertHasPair(
                output, "large_text", "txt_log", cdata(String.format("Full log: %s", REPORT_URL)));
    }

    /** Verifies that the output has the given pattern in regex. */
    private static void assertHasPattern(String output, String regex) {
        Pattern p = Pattern.compile(regex, Pattern.DOTALL);
        assertTrue(
                "\"" + output + "\"\ndoesn't match pattern:\n\"" + regex + "\"",
                p.matcher(output).matches());
    }

    /**
     * A simple utility to make it easier to test for the presence of Sponge's gargantuan key-value
     * pair structures
     */
    private static void assertHasPair(String output, String type, String name, String value) {
        // This regex pattern matches key-value pair structures in an XML, allowing for arbitrary
        // whitespace characters.  The \\Q and \\E constructs are for escaping characters from the
        // arguments, to prevent them from interfering with the regex pattern.
        String regex = String.format(".*<\\Q%s\\E>\\s*<name>\\s*\\Q%s\\E\\s*</name>\\s*<value>\\s*"
                + "\\Q%s\\E\\s*</value>\\s*</\\Q%s\\E>.*",
                type, name, value, type);
        assertHasPattern(output, regex);
    }

    /**
     * Wraps the given data String in a CDATA section
     *
     * @return a String wrapping data like <![CDATA[data]]>
     */
    private static String cdata(String data) {
        return "<![CDATA[" + data + "]]>";
    }

    /** Return all XML nodes that match the given xPathExpression. */
    private NodeList getXmlNodes(String xml, String xPathExpression)
            throws XPathExpressionException {

        InputSource inputSource = new InputSource(new StringReader(xml));
        XPath xpath = XPathFactory.newInstance().newXPath();
        return (NodeList) xpath.evaluate(xPathExpression, inputSource, XPathConstants.NODESET);
    }

    /** Assert that the XML contains a node matching the given xPathExpression. */
    private NodeList assertXmlContainsNode(String xml, String xPathExpression)
            throws XPathExpressionException {
        NodeList nodes = getXmlNodes(xml, xPathExpression);
        assertNotNull(
                String.format("XML '%s' returned null for xpath '%s'.", xml, xPathExpression),
                nodes);
        assertTrue(
                String.format(
                        "XML '%s' should have returned at least 1 node for xpath '%s', "
                                + "but returned %s nodes instead.",
                        xml, xPathExpression, nodes.getLength()),
                nodes.getLength() >= 1);
        return nodes;
    }

    private NodeList assertXmlContainsNodeWithOccurrence(
            String xml, String xPathExpression, int occurrence) throws XPathExpressionException {
        NodeList nodes = getXmlNodes(xml, xPathExpression);
        assertNotNull(
                String.format("XML '%s' returned null for xpath '%s'.", xml, xPathExpression),
                nodes);
        assertTrue(
                String.format(
                        "XML '%s' should have returned %s node for xpath '%s', "
                                + "but returned %s nodes instead.",
                        xml, xPathExpression, occurrence, nodes.getLength()),
                nodes.getLength() == occurrence);
        return nodes;
    }

    /**
     * Assert that the XML contains a node matching the given xPathExpression and that the node has
     * a given value.
     */
    private void assertXmlContainsValue(String xml, String xPathExpression, String value)
            throws XPathExpressionException {
        NodeList nodes = assertXmlContainsNode(xml, xPathExpression);
        boolean found = false;

        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (element.getTextContent().equals(value)) {
                found = true;
                break;
            }
        }

        assertTrue(
                String.format(
                        "xPath '%s' should contain value '%s' but does not. XML: '%s'",
                        xPathExpression, value, xml),
                found);
    }

    /**
     * Report a test failure and validate that the report XML contains the given node with the given
     * value.
     */
    private void validateFailureReportNode(String message, String nodeName, String nodeValue)
            throws IOException, XPathExpressionException {
        final String testTag = "test_failure"; // matches SpongeResultReporter.addFailure()

        if (message == null) {
            message = "";
        }

        TestResult testResult = new TestResult();
        testResult.setStatus(TestStatus.FAILURE);
        testResult.setStackTrace(message);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        KXmlSerializer serializer = new KXmlSerializer();
        serializer.setOutput(outputStream, "UTF-8");
        serializer.startDocument("UTF-8", null);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);

        mResultReporter.addFailure(serializer, testTag, testResult);
        serializer.endDocument();

        final String xml = outputStream.toString("UTF-8");
        String xPathExpression = String.format("/%s/%s", testTag, nodeName);
        assertXmlContainsValue(xml, xPathExpression, nodeValue);
    }

    /** Validate that the exception message is parsed correctly when added to a report. */
    private void testExceptionMessageParsedCorrectly(String message, String expectedMessage)
            throws IOException, XPathExpressionException {

        validateFailureReportNode(message, "message", expectedMessage);
    }

    /** Validate that the exception type is parsed correctly when added to a report. */
    private void testExceptionTypeParsedCorrectly(String message, String expectedType)
            throws IOException, XPathExpressionException {

        validateFailureReportNode(message, "exception_type", expectedType);
    }

    private void replayMocks(Object... mocks) {
        for (Object mock: mocks) {
            EasyMock.replay(mock);
        }
    }
}
