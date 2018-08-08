// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.jankiness;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.DeviceFileReporter;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.NameMangleListener;
import com.android.tradefed.testtype.UiAutomatorTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Common harness for jank tests
 * <p>
 * The test harness instance can be used to drive jank test classes on device. Each jank test
 * class may have multiple test methods, corresponding to multiple use cases. Each use case will
 * generate a pair of metrics: avg number of jank and FPS
 * <p>
 * When jank test results are reported:
 * <ul>
 * <li> use 'reporting-key' parameter to override default reporting key, it must be overriden to
 *      a key that will be matched in reporting backend
 * <li> each reporting key will be suffixed with '-jank' and '-fps' to generate two sets of results
 *      for avg jank and fps respectively
 * <li> each test method corresponds to a use case, and will be mapped to a schema item for each of
 *      the reporting key, so the two result sets will have the same schema item structure
 * <li> by default schema items will use test method name as the default key, use "schema-mapping"
 *      parameter to override; e.g. "--schema-mapping testScroll scroll"
 * </ul>
 * <p>
 * When using the the test harness, the test method names must be unique among <strong>all</strong>
 * of the test classes; since for the sake of simplicity and less verboseness, the schema mapping
 * only looks at method names.
 * <p>
 * As an example, if we have test class <code>CalculatorExerciseTest</code> with methods
 * <code>testAddition</code>, <code>testSubtraction</code>, <code>testMultiplication</code> and
 * <code>testDivision</code>; using reporting key <code>calc-exec-test</code>, and schema mapping:
 * <br/>
 * <code>--schema-mapping testAddition addition<br/>
 * --schema-mapping testSubtraction subtraction<br/>
 * --schema-mapping testMultiplication multiplication<br/>
 * --schema-mapping testDivision division</code>
 * <p>
 * And the reported data sets will be (test result values are hypothetical):<br/>
 * <code>
 * calc-exec-test-jank: {addition: 0, subtraction: 0, multiplication: 0, division: 0}<br/>
 * calc-exec-test-fps: {addition: 60, subtraction: 60, multiplication: 60, division: 60}
 * </code>
 */
public class UiJankinessTest2 extends UiAutomatorTest {

    private static final String OUTPUT_FILE_NAME = "jankoutput.txt"; // output file
    private static final String STATUS_FILE_NAME = "jankstatus.txt"; // test status file
    private static final String RAW_DATA_DIRECTORY = "rawdata"; // raw data directory
    private static final String SYSTRACE_DIRECTORY = "systrace"; // systrace directory

    // result keys used by device side JankTestBase to directly emit various test params
    public static final String KEY_AVG_JANK = "avg-jank";
    public static final String KEY_MAX_JANK = "max-jank";
    public static final String KEY_AVG_FPS = "avg-fps";
    public static final String KEY_AVG_MAX_FRAME_DURATION = "avg-max-frame-duration";

    // maps result keys to appropriate suffixes
    // JankStatusListener will use this map to decide which metrics to keep and report
    private static final Map<String, String> REPORTED_METRICS = new HashMap<String, String>();
    static {
        REPORTED_METRICS.put(KEY_AVG_JANK, "-jank");
        REPORTED_METRICS.put(KEY_AVG_FPS, "-fps");
    }

    @Option(name="output-file-path", description = "dir path on device of jank output files.")
    private String mOutputFilePath = "/data/local/tmp/";

    @Option(name="reporting-key", description = "", importance=Importance.ALWAYS)
    private String mReportingKey = "ui-jank-test";

    @Option(name="schema-mapping", description = "mapping between test case name and report schema"
            +  " item key. The defaultkey is the test method name.")
    private Map<String, String> mTestMethodToKeyMap = new HashMap<String, String>();

    @Override
    protected void preTestSetup() throws DeviceNotAvailableException {
        super.preTestSetup();
        if (mOutputFilePath == null) {
            mOutputFilePath = getDevice().getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        }
        cleanOutputFiles();
        String extStore = getDevice().getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        String dataDir = String.format("%s/%s", extStore, RAW_DATA_DIRECTORY);

        if (!getDevice().doesFileExist(dataDir)) {
            CLog.v(String.format("The raw directory %s doesn't exist.", RAW_DATA_DIRECTORY));
            getDevice().executeShellCommand(String.format("mkdir \"%s\"", dataDir));
        }
        dataDir = String.format("%s/%s", extStore, SYSTRACE_DIRECTORY);
        if (!getDevice().doesFileExist(dataDir)) {
            CLog.v("Create directory for systrace ");
            getDevice().executeShellCommand(String.format("mkdir \"%s\"", dataDir));
        }
    }

    /**
     * Runs jank tests and parses results from test output.
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        JankStatusListener testListener = new JankStatusListener(listener);
        super.run(testListener);

        // do fake test runs to report sets of metrics, need to call on the real listener
        for (Entry<String, Map<String, String>> entry : testListener.getFinalMetrics().entrySet()) {
            listener.testRunStarted(entry.getKey(), 0);
            listener.testRunEnded(0, entry.getValue());
        }

        // reports raw test data
        pullDataIntoZip(testListener, RAW_DATA_DIRECTORY);
        pullDataIntoZip(testListener, SYSTRACE_DIRECTORY);
        DeviceFileReporter dfr = new DeviceFileReporter(getDevice(), testListener);
        dfr.setDefaultLogDataType(LogDataType.TEXT);
        dfr.addPatterns(mOutputFilePath + STATUS_FILE_NAME, mOutputFilePath + OUTPUT_FILE_NAME);
        dfr.run();
        cleanOutputFiles();
    }

    /**
     * Pulls a folder off device, zips it and logs it
     *
     * @param listener a {@link ITestInvocationListener} used for logging the test data zip
     * @param dir an on-device directory, assumed to be based under /data/local/tmp
     * @throws DeviceNotAvailableException
     */
    private void pullDataIntoZip(ITestInvocationListener listener, String dir)
            throws DeviceNotAvailableException {

        String rawFileDir = String.format("%s/%s", mOutputFilePath, dir);
        IFileEntry outputDir = getDevice().getFileEntry(rawFileDir);
        if (outputDir == null) {
            CLog.w("Directory not found on device: %s; test error?", rawFileDir);
            return;
        }
        // create a local temp folder
        File tmpDir = null;
        try {
            tmpDir = FileUtil.createTempDir(dir);
        } catch (IOException ioe) {
            // most likely cause would be host disk full, so rethrow as RTE
            throw new RuntimeException(String.format(
                    "failed to created local tmp folder to pull from: %s", rawFileDir), ioe);
        }
        for (IFileEntry file : outputDir.getChildren(false)) {
            File pulledFile = new File(tmpDir, file.getName());
            if (!getDevice().pullFile(file.getFullPath(), pulledFile)) {
                CLog.w("Could not pull file: %s", file.getFullPath());
                continue;
            }
            getDevice().executeShellCommand("rm " + file.getFullPath());
        }
        // zip up the local tmp folder, and log it
        File tmpZip = null;
        try {
            tmpZip = ZipUtil.createZip(tmpDir);
        } catch (IOException ioe) {
            // most likely cause would be host disk full, so rethrow as RTE
            throw new RuntimeException(String.format(
                    "failed to created temp zip for device files: %s", rawFileDir), ioe);
        }
        FileInputStreamSource stream = new FileInputStreamSource(tmpZip);
        listener.testLog(dir, LogDataType.ZIP, stream);
        StreamUtil.cancel(stream);
        tmpZip.delete();
        // clean up
        FileUtil.recursiveDelete(tmpDir);
    }

    // clean up output file
    private void cleanOutputFiles() throws DeviceNotAvailableException {
        getDevice().executeShellCommand(
                String.format("rm %s/%s", mOutputFilePath, OUTPUT_FILE_NAME));
        getDevice().executeShellCommand(
                String.format("rm %s/%s", mOutputFilePath, STATUS_FILE_NAME));
        getDevice().executeShellCommand(String.format("rm %s/%s/*", mOutputFilePath,
                RAW_DATA_DIRECTORY));
        getDevice().executeShellCommand(String.format("rm %s/%s/*", mOutputFilePath,
                SYSTRACE_DIRECTORY ));
    }

    /**
     * Listens to Jank test metrics, converts, accumulates and reports at end of test run
     */
    class JankStatusListener extends NameMangleListener {

        // <reporting key>-jank|fps -> {schema items -> test result}
        private Map<String, Map<String, String>> mAccumulatedMetrics =
                new HashMap<String, Map<String, String>>();
        private Map<String, String> mEmptyMap = Collections.emptyMap();

        public JankStatusListener(ITestInvocationListener listener) {
            super(listener);
        }

        @Override
        public void testRunStarted(String runName, int testCount) {
            // initialized the accumulated metrics map
            for (Entry<String, String> entry : REPORTED_METRICS.entrySet()) {
                mAccumulatedMetrics.put(mReportingKey + entry.getValue(),
                        new HashMap<String, String>());
            }
            super.testRunStarted(runName, testCount);
        }

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            // extract method name from test identifier
            String method = test.getTestName();
            // check if the method name is mapped to an existing key
            if (mTestMethodToKeyMap.containsKey(method)) {
                method = mTestMethodToKeyMap.get(method);
            }
            for (Entry<String, String> entry : testMetrics.entrySet()) {
                if (REPORTED_METRICS.containsKey(entry.getKey())) {
                    // get the results map (schema item level) from the map (result set level) where
                    // keys are <reporting key>-jank|fps
                    mAccumulatedMetrics.get(mReportingKey + REPORTED_METRICS.get(entry.getKey()))
                        .put(method, entry.getValue());
                }
            }
            // lie about interim per test method results, since we do manual reports
            super.testEnded(test, mEmptyMap);
        }

        public Map<String, Map<String, String>> getFinalMetrics() {
            return mAccumulatedMetrics;
        }
    }
}
