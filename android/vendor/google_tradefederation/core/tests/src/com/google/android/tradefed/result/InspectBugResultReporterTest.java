// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import com.android.loganalysis.item.BugreportItem;
import com.android.loganalysis.item.DumpsysItem;
import com.android.loganalysis.item.KernelLogItem;
import com.android.loganalysis.item.LogcatItem;
import com.android.loganalysis.item.MemInfoItem;
import com.android.loganalysis.item.MonkeyLogItem;
import com.android.loganalysis.item.ProcrankItem;
import com.android.loganalysis.item.TopItem;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.DeviceBuildDescriptor;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;

import com.google.api.client.http.HttpRequestFactory;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipOutputStream;

/**
 * Unit tests for {@link InspectBugResultReporter}.
 */
public class InspectBugResultReporterTest extends TestCase {

    private static final String REPORT_PATH = "report_path";
    private static final String REPORT_URL = "report_url";
    private static final String HOST = "http://example.com";
    private InspectBugResultReporter mResultReporter;
    private IBuildInfo mBuildInfo;
    private IInvocationContext mContext;
    private Date mTimestamp;

    private interface PostHelper {
        public String doPost(String url, String payload, String contentType);
    }

    private PostHelper mPostHelper;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mPostHelper = EasyMock.createStrictMock(PostHelper.class);

        mBuildInfo = new BuildInfo();
        mBuildInfo.setBuildId("0");
        mBuildInfo.setBuildBranch("branch");
        mBuildInfo.setBuildFlavor("product-userdebug");
        mBuildInfo.setDeviceSerial("12345678");
        mContext = new InvocationContext();
        mContext.addDeviceBuildInfo("device", mBuildInfo);
        mTimestamp = new Date(0);

        mResultReporter =
                new InspectBugResultReporter("suite-key", "suite-instance-key") {
                    @Override
                    BugreportItem parseBugreport(InputStream source) {
                        return new BugreportItem();
                    }

                    @Override
                    MonkeyLogItem parseMokeyLog(InputStream source) {
                        return new MonkeyLogItem();
                    }

                    @Override
                    LogcatItem parseLogcat(InputStream source) {
                        return new LogcatItem();
                    }

                    @Override
                    KernelLogItem parseKernelLog(InputStream source) {
                        return new KernelLogItem();
                    }

                    @Override
                    MemInfoItem parseMemInfo(InputStream source) {
                        return new MemInfoItem();
                    }

                    @Override
                    ProcrankItem parseProcrank(InputStream source) {
                        return new ProcrankItem();
                    }

                    @Override
                    TopItem parseTop(InputStream source) {
                        return new TopItem();
                    }

                    @Override
                    DumpsysItem parseDumpsys(InputStream source) {
                        return new DumpsysItem();
                    }

                    @Override
                    protected String getHost() {
                        return HOST;
                    }

                    @Override
                    protected Date getCurrentTimestamp() {
                        return mTimestamp;
                    }

                    @Override
                    protected String getSecondaryHost() {
                        return "";
                    }

                    @Override
                    protected String doPost(
                            String url,
                            String payload,
                            String contentType,
                            HttpRequestFactory factory)
                            throws IOException {
                        return mPostHelper.doPost(url, payload, contentType);
                    }
                };
    }

    /**
     * Test that adding a bugreport creates a run and adds the bugreport.
     */
    public void testSaveBugreport() throws JSONException {
        Capture<String> createRunCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/create_run"),
                EasyMock.capture(createRunCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"run_id\": 1}");
        Capture<String> addLogCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_bugreport"),
                EasyMock.capture(addLogCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"bugreport_id\": 1}");

        EasyMock.replay(mPostHelper);
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testLogSaved("bugreport", LogDataType.BUGREPORT,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));

        verifyCreateRun(createRunCapture.getValue());
        verifyAddLog(addLogCapture.getValue(), "bugreport");

        EasyMock.verify(mPostHelper);
    }

    /**
     * Test that adding a bugreportz creates a run and adds the bugreportz.
     */
    public void testSaveBugreportz() throws IOException, JSONException {
        File tempDir = null;
        File zipFile = null;
        InputStreamSource is = null;
        try {
            tempDir = FileUtil.createTempDir("bugreportz");
            File mainEntry = FileUtil.createTempFile("main_entry", ".txt", tempDir);
            File bugreport = FileUtil.createTempFile("bugreport_DEVICE_", ".txt", tempDir);
            InputStream name = new ByteArrayInputStream(bugreport.getName().getBytes());
            FileUtil.writeToFile(name, mainEntry);
            InputStream content = new ByteArrayInputStream("test".getBytes());
            FileUtil.writeToFile(content, bugreport);
            mainEntry.renameTo(new File(tempDir, "main_entry.txt"));
            // We create a fake bugreport zip with main_entry.txt and the bugreport.
            zipFile = ZipUtil.createZip(new File(""));
            FileOutputStream fileStream = new FileOutputStream(zipFile);
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(fileStream));
            ZipUtil.addToZip(out, bugreport, new LinkedList<String>());
            ZipUtil.addToZip(out, new File(tempDir, "main_entry.txt"), new LinkedList<String>());
            StreamUtil.close(out);
            is = new FileInputStreamSource(zipFile, true);
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }

        Capture<String> createRunCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/create_run"),
                EasyMock.capture(createRunCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"run_id\": 1}");
        Capture<String> addLogCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_bugreport"),
                EasyMock.capture(addLogCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"bugreport_id\": 1}");

        EasyMock.replay(mPostHelper);

        try {
            mResultReporter.invocationStarted(mContext);
            mResultReporter.testLogSaved("bugreport", LogDataType.BUGREPORTZ, is,
                    new LogFile(REPORT_PATH, REPORT_URL, true /* compressed */, false /* text */));
        } finally {
            StreamUtil.cancel(is);
        }

        verifyCreateRun(createRunCapture.getValue());
        verifyAddLog(addLogCapture.getValue(), "bugreport");

        EasyMock.verify(mPostHelper);
    }
    /**
     * Test that adding a monkey log creates a run and adds the monkey log.
     */
    public void testSaveMonkeyLog() throws JSONException {
        Capture<String> createRunCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/create_run"),
                EasyMock.capture(createRunCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"run_id\": 1}");
        Capture<String> addLogCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_monkey_log"),
                EasyMock.capture(addLogCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"monkey_log_id\": 1}");

        EasyMock.replay(mPostHelper);

        mResultReporter.invocationStarted(mContext);
        mResultReporter.testLogSaved("monkey_log", LogDataType.MONKEY_LOG,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));

        verifyCreateRun(createRunCapture.getValue());
        verifyAddLog(addLogCapture.getValue(), "monkey_log");

        EasyMock.verify(mPostHelper);
    }

    /** Test that adding the test logcat creates a run and adds logcat. */
    public void testSaveLogcat() throws JSONException {
        Capture<String> createRunCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/create_run"),
                EasyMock.capture(createRunCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"run_id\": 1}");
        Capture<String> addLogCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_logcat"),
                EasyMock.capture(addLogCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"logcat_id\": 1}");

        EasyMock.replay(mPostHelper);

        mResultReporter.invocationStarted(mContext);
        mResultReporter.testLogSaved(
                TestInvocation.getDeviceLogName(TestInvocation.Stage.TEST),
                LogDataType.LOGCAT,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));

        verifyCreateRun(createRunCapture.getValue());
        verifyAddLog(addLogCapture.getValue(), "logcat");

        EasyMock.verify(mPostHelper);
    }

    /**
     * Test that the skipped stages of logcat actually get skipped and that it applies only to
     * logcat.
     */
    public void testSkipLogcat() throws JSONException {
        Capture<String> createRunCapture = new Capture<>();
        EasyMock.expect(
                        mPostHelper.doPost(
                                EasyMock.eq(HOST + "/post/create_run"),
                                EasyMock.capture(createRunCapture),
                                EasyMock.eq("application/json")))
                .andReturn("{\"status\": \"OK\", \"run_id\": 1}");
        Capture<String> addLogCapture = new Capture<>();
        EasyMock.expect(
                        mPostHelper.doPost(
                                EasyMock.eq(HOST + "/post/add_bugreport"),
                                EasyMock.capture(addLogCapture),
                                EasyMock.eq("application/json")))
                .andReturn("{\"status\": \"OK\", \"bugreport_id\": 1}");

        EasyMock.replay(mPostHelper);

        mResultReporter.invocationStarted(mContext);
        // Save (and skip) logcat with the setup and teardown logcat names
        mResultReporter.testLogSaved(
                TestInvocation.getDeviceLogName(TestInvocation.Stage.SETUP),
                LogDataType.LOGCAT,
                new ByteArrayInputStreamSource("setup".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));
        mResultReporter.testLogSaved(
                TestInvocation.getDeviceLogName(TestInvocation.Stage.TEARDOWN),
                LogDataType.LOGCAT,
                new ByteArrayInputStreamSource("teardown".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));
        // Save a kernel log with the test logcat name
        mResultReporter.testLogSaved(
                TestInvocation.getDeviceLogName(TestInvocation.Stage.TEST),
                LogDataType.BUGREPORT,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));

        verifyCreateRun(createRunCapture.getValue());
        verifyAddLog(addLogCapture.getValue(), "bugreport");

        EasyMock.verify(mPostHelper);
    }

    /**
     * Test that adding a kernel log creates a run and adds the kernel log.
     */
    public void testSaveKernelLog() throws JSONException {
        Capture<String> createRunCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/create_run"),
                EasyMock.capture(createRunCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"run_id\": 1}");
        Capture<String> addLogCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_kernel_log"),
                EasyMock.capture(addLogCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"kernel_log_id\": 1}");

        EasyMock.replay(mPostHelper);

        mResultReporter.invocationStarted(mContext);
        mResultReporter.testLogSaved("kernel_log", LogDataType.KERNEL_LOG,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));

        verifyCreateRun(createRunCapture.getValue());
        verifyAddLog(addLogCapture.getValue(), "kernel_log");

        EasyMock.verify(mPostHelper);
    }

    /**
     * Test that adding mem info output creates a run and adds the mem info.
     */
    public void testSaveMemInfo() throws JSONException {
        Capture<String> createRunCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/create_run"),
                EasyMock.capture(createRunCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"run_id\": 1}");
        Capture<String> addLogCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_mem_info"),
                EasyMock.capture(addLogCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"mem_info_id\": 1}");

        EasyMock.replay(mPostHelper);

        mResultReporter.invocationStarted(mContext);
        mResultReporter.testLogSaved("mem_info", LogDataType.MEM_INFO,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));

        verifyCreateRun(createRunCapture.getValue());
        verifyAddLog(addLogCapture.getValue(), "mem_info");

        EasyMock.verify(mPostHelper);
    }

    /**
     * Test that adding procrank output creates a run and adds the procrank.
     */
    public void testSaveProcrank() throws JSONException {
        Capture<String> createRunCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/create_run"),
                EasyMock.capture(createRunCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"run_id\": 1}");
        Capture<String> addLogCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_procrank"),
                EasyMock.capture(addLogCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"procrank_id\": 1}");

        EasyMock.replay(mPostHelper);

        mResultReporter.invocationStarted(mContext);
        mResultReporter.testLogSaved("procrank", LogDataType.PROCRANK,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));

        verifyCreateRun(createRunCapture.getValue());
        verifyAddLog(addLogCapture.getValue(), "procrank");

        EasyMock.verify(mPostHelper);
    }

    /**
     * Test that adding top output creates a run and adds the top.
     */
    public void testSaveTop() throws JSONException {
        Capture<String> createRunCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/create_run"),
                EasyMock.capture(createRunCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"run_id\": 1}");
        Capture<String> addLogCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_top"),
                EasyMock.capture(addLogCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"top_id\": 1}");

        EasyMock.replay(mPostHelper);

        mResultReporter.invocationStarted(mContext);
        mResultReporter.testLogSaved("top", LogDataType.TOP,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));

        verifyCreateRun(createRunCapture.getValue());
        verifyAddLog(addLogCapture.getValue(), "top");

        EasyMock.verify(mPostHelper);
    }

    /**
     * Test that adding dumpsys output creates a run and adds the dumpsys.
     */
    public void testSaveDumpsys() throws JSONException {
        Capture<String> createRunCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/create_run"),
                EasyMock.capture(createRunCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"run_id\": 1}");
        Capture<String> addLogCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_dumpsys"),
                EasyMock.capture(addLogCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"dumpsys_id\": 1}");

        EasyMock.replay(mPostHelper);

        mResultReporter.invocationStarted(mContext);
        mResultReporter.testLogSaved("dumpsys", LogDataType.DUMPSYS,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));

        verifyCreateRun(createRunCapture.getValue());
        verifyAddLog(addLogCapture.getValue(), "dumpsys");

        EasyMock.verify(mPostHelper);
    }

    /**
     * Test that adding mulitple bugreports creates a run and adds the bugreports, and updates the
     * run when invocationEnded is called.
     */
    public void testSaveMultipleLogs() {
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/create_run"),
                (String) EasyMock.anyObject(), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"run_id\": 1}");
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_bugreport"),
                (String) EasyMock.anyObject(), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"bugreport_id\": 1}");
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_bugreport"),
                (String) EasyMock.anyObject(), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"bugreport_id\": 2}");
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_bugreport"),
                (String) EasyMock.anyObject(), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"bugreport_id\": 3}");
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/update_run"),
                (String) EasyMock.anyObject(), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\"}");

        EasyMock.replay(mPostHelper);

        mResultReporter.invocationStarted(mContext);
        mResultReporter.testLogSaved("bugreport", LogDataType.BUGREPORT,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));
        mResultReporter.testLogSaved("bugreport", LogDataType.BUGREPORT,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));
        mResultReporter.testLogSaved("bugreport", LogDataType.BUGREPORT,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));
        List<TestSummary> summaries = new LinkedList<>();
        summaries.add(new TestSummary("http://example.com/result"));
        mResultReporter.putSummary(summaries);
        mResultReporter.invocationEnded(0);

        EasyMock.verify(mPostHelper);
    }

    /**
     * Test that builds which use {@link DeviceBuildDescriptor} to describe device build that is
     * different from the build under test do populate aux_builds.
     */
    public void testSaveUnbundled() throws JSONException, DeviceNotAvailableException {
        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mockDevice.getBuildId()).andReturn("1");
        EasyMock.expect(mockDevice.getProductType()).andReturn("product");
        EasyMock.expect(mockDevice.getProductVariant()).andReturn("varient");
        EasyMock.expect(mockDevice.getBuildAlias()).andReturn("alias");
        EasyMock.expect(mockDevice.getProperty("ro.build.type")).andReturn("eng");
        EasyMock.expect(mockDevice.getProperty("ro.build.version.release")).andReturn("release");
        EasyMock.expect(mockDevice.getProperty("ro.product.brand")).andReturn("brand");
        EasyMock.expect(mockDevice.getProperty("ro.product.model")).andReturn("model");
        EasyMock.expect(mockDevice.getProperty("ro.product.name")).andReturn("product");

        EasyMock.replay(mockDevice);
        DeviceBuildDescriptor.injectDeviceAttributes(mockDevice, mBuildInfo);
        EasyMock.verify(mockDevice);

        Capture<String> createRunCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/create_run"),
                EasyMock.capture(createRunCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"run_id\": 1}");
        Capture<String> addLogCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_bugreport"),
                EasyMock.capture(addLogCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"bugreport_id\": 1}");

        EasyMock.replay(mPostHelper);

        mResultReporter.invocationStarted(mContext);
        mResultReporter.testLogSaved("bugreport", LogDataType.BUGREPORT,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));

        verifyCreateRun(createRunCapture.getValue(), false);
        verifyAddLog(addLogCapture.getValue(), "bugreport");

        JSONObject json = new JSONObject(createRunCapture.getValue());
        assertTrue(json.has("aux_builds"));
        JSONObject auxBuild = json.getJSONArray("aux_builds").getJSONObject(0);
        assertEquals("1", auxBuild.get("incremental"));
        assertEquals("alias", auxBuild.get("alias"));
        assertEquals("product-eng", auxBuild.get("flavor"));
        assertEquals("product", auxBuild.get("product"));
        assertEquals("eng", auxBuild.get("target"));

        EasyMock.verify(mPostHelper);
    }

    /**
     * Test that we send information properly when an invocation fails and
     * the {@code report-invocation-failures} option is enabled
     */
    public void testSaveTradefedLogs() throws JSONException, DeviceNotAvailableException,
            ConfigurationException {
        final OptionSetter option = new OptionSetter(mResultReporter);
        option.setOptionValue("report-invocation-failures", "true");
        final Throwable failException = new IllegalArgumentException("this was the exception");

        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mockDevice.getBuildId()).andReturn("1");
        EasyMock.expect(mockDevice.getProductType()).andReturn("product");
        EasyMock.expect(mockDevice.getProductVariant()).andReturn("varient");
        EasyMock.expect(mockDevice.getBuildAlias()).andReturn("alias");
        EasyMock.expect(mockDevice.getProperty("ro.build.type")).andReturn("eng");
        EasyMock.expect(mockDevice.getProperty("ro.build.version.release")).andReturn("release");
        EasyMock.expect(mockDevice.getProperty("ro.product.brand")).andReturn("brand");
        EasyMock.expect(mockDevice.getProperty("ro.product.model")).andReturn("model");
        EasyMock.expect(mockDevice.getProperty("ro.product.name")).andReturn("product");

        EasyMock.replay(mockDevice);
        DeviceBuildDescriptor.injectDeviceAttributes(mockDevice, mBuildInfo);
        EasyMock.verify(mockDevice);

        Capture<String> createRunCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/create_run"),
                EasyMock.capture(createRunCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"run_id\": 1}");
        Capture<String> addLogCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_bugreport"),
                EasyMock.capture(addLogCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"bugreport_id\": 1}");
        EasyMock.replay(mPostHelper);

        mResultReporter.invocationStarted(mContext);
        mResultReporter.testLogSaved("bugreport", LogDataType.BUGREPORT,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));
        mResultReporter.invocationFailed(failException);

        verifyCreateRun(createRunCapture.getValue(), false);
        verifyAddLog(addLogCapture.getValue(), "bugreport");

        JSONObject json = new JSONObject(createRunCapture.getValue());
        assertTrue(json.has("aux_builds"));
        JSONObject auxBuild = json.getJSONArray("aux_builds").getJSONObject(0);
        assertEquals("1", auxBuild.get("incremental"));
        assertEquals("alias", auxBuild.get("alias"));
        assertEquals("product-eng", auxBuild.get("flavor"));
        assertEquals("product", auxBuild.get("product"));
        assertEquals("eng", auxBuild.get("target"));

        EasyMock.verify(mPostHelper);
    }

    /**
     * Test that builds which use {@link DeviceBuildDescriptor} to describe the same build as the
     * build under test do not populate aux_builds.
     */
    public void testSaveDeviceDescriptor() throws JSONException, DeviceNotAvailableException {
        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mockDevice.getBuildId()).andReturn(mBuildInfo.getBuildId());
        EasyMock.expect(mockDevice.getProductType()).andReturn("product");
        EasyMock.expect(mockDevice.getProductVariant()).andReturn("varient");
        EasyMock.expect(mockDevice.getBuildAlias()).andReturn("alias");
        EasyMock.expect(mockDevice.getProperty("ro.build.type")).andReturn("userdebug");
        EasyMock.expect(mockDevice.getProperty("ro.build.version.release")).andReturn("release");
        EasyMock.expect(mockDevice.getProperty("ro.product.brand")).andReturn("brand");
        EasyMock.expect(mockDevice.getProperty("ro.product.model")).andReturn("model");
        EasyMock.expect(mockDevice.getProperty("ro.product.name")).andReturn("product");

        EasyMock.replay(mockDevice);
        DeviceBuildDescriptor.injectDeviceAttributes(mockDevice, mBuildInfo);
        EasyMock.verify(mockDevice);

        Capture<String> createRunCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/create_run"),
                EasyMock.capture(createRunCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"run_id\": 1}");
        Capture<String> addLogCapture = new Capture<>();
        EasyMock.expect(mPostHelper.doPost(EasyMock.eq(HOST + "/post/add_bugreport"),
                EasyMock.capture(addLogCapture), EasyMock.eq("application/json"))
                ).andReturn("{\"status\": \"OK\", \"bugreport_id\": 1}");

        EasyMock.replay(mPostHelper);

        mResultReporter.invocationStarted(mContext);
        mResultReporter.testLogSaved("bugreport", LogDataType.BUGREPORT,
                new ByteArrayInputStreamSource("test".getBytes()),
                new LogFile(REPORT_PATH, REPORT_URL, false /* compressed */, true /* text */));

        verifyCreateRun(createRunCapture.getValue());
        verifyAddLog(addLogCapture.getValue(), "bugreport");

        JSONObject json = new JSONObject(createRunCapture.getValue());
        assertFalse(json.has("aux_builds"));

        EasyMock.verify(mPostHelper);
    }

    private void verifyCreateRun(String args) throws JSONException {
        verifyCreateRun(args, true);
    }

    private void verifyCreateRun(String args, boolean hasProductTarget) throws JSONException {
        JSONObject argsJson = new JSONObject(args);

        assertEquals(mTimestamp.toString(), argsJson.get("timestamp"));
        assertEquals("suite-key", argsJson.getJSONObject("suite").get("key"));
        assertEquals("suite-instance-key", argsJson.getJSONObject("suite").get("instance_key"));

        JSONObject build = argsJson.getJSONObject("build");
        assertEquals("0", build.get("incremental"));
        assertEquals("branch", build.get("branch"));
        assertEquals("product-userdebug", build.get("flavor"));
        if (hasProductTarget) {
            assertEquals("product", build.get("product"));
            assertEquals("userdebug", build.get("target"));
        } else {
            assertFalse(build.has("product"));
            assertFalse(build.has("target"));
        }
    }

    private void verifyAddLog(String args, String logKey) throws JSONException {
        JSONObject argsJson = new JSONObject(args);

        assertEquals(mTimestamp.toString(), argsJson.get("timestamp"));
        assertEquals(REPORT_URL, argsJson.get("url"));
        assertEquals(1, argsJson.get("run_id"));
        assertTrue(argsJson.has(logKey));
        assertTrue(argsJson.get(logKey) instanceof JSONObject);
    }
}
