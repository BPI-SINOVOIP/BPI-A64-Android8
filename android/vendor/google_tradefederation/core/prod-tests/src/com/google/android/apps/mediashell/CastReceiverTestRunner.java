// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.apps.mediashell;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StringEscapeUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tests AndroidMediaShell to verify Cast.
 */
public class CastReceiverTestRunner implements IDeviceTest, IRemoteTest {

    private ITestDevice mDevice;
    private IFileEntry mLocalStorageFile;
    private Map<String, String> mEmptyMap = new HashMap<String, String>();
    private long mTestTimeoutMs = 30000; // 30 sec

    private static final String MEDIASHELL_PACKAGE = "com.google.android.apps.mediashell";
    private static final String MEDIASHELL_SERVICE = "MediaShellCastReceiverService";
    private static final String MEDIASHELL_ACTIVITY = "MediaShellActivity";
    private static final String MEDIASHELL_DATA_DIR =
            "/data/data/com.google.android.apps.mediashell";
    private static final String MEDIASHELL_LOCALSTORAGE_DIR =
            MEDIASHELL_DATA_DIR + "/app_media_shell/media_shell/Local Storage/";
    private static final String LOCALSTORAGE_EXT_NAME = ".localstorage";

    // This must match with the values in result-store.js &
    // result-transformer.js at
    // https://eureka-internal.googlesource.com/test/+/master/js/lib/results/src/
    private static final String TEST_NAME_KEY = "test_name";
    private static final String FAILED_KEY = "failed";
    private static final String CLASS_NAME_KEY = "class_name";
    private static final String HAS_ERROR_KEY = "has_error";
    private static final String ELAPSED_SECS_KEY = "elapsed_secs";
    private static final String STACK_KEY = "stack";
    private static final String RESULT_STORE_TAG_KEY = "ResultStoreTag";
    private static final String START_TIMESTAMP_KEY = "start_timestamp";
    private static final String MEASUREMENT_JSON_KEY = "measurement_json";
    // TODO: refactor to cover different result schema across Cast tests.
    private static final String[] REQUIRED_SCHEMA_KEYS = {
            TEST_NAME_KEY, FAILED_KEY, RESULT_STORE_TAG_KEY };
    private static final String LAST_UPDATE_UTC_KEY = "LastUpdateUTC_ResultStoreTag";

    // Options
    @Option(name = "test-url",
            description = "Absolute URL path to Cast tests excluding URL parameters.",
            importance = Importance.ALWAYS, mandatory = true)
    private String mTestUrl = "";

    @Option(name = "url-param",
            description = "URL parameters passed to Cast tests. "
                    + "Note that characters(e.g \"&\") in this option must be expressed "
                    + "as an entity reference(e.g. \"&amp;\").", importance = Importance.ALWAYS)
    private Map<String, String> mUrlParamMap = new LinkedHashMap<String, String>();

    // TODO: get the number of tests from local storage if test page supports
    @Option(name = "test-count",
            description = "Number of all tests in total. If set, test runner will stop "
                    + "if tests completed reaches this number.")
    private int mTestCount = 0;

    @Option(name = "testrun-timeout", description = "Timeout in seconds to run all tests.")
    private long mTestRunTimeout = 300; // 300 sec

    @Option(name = "run-name", description = "the name used to report the MediaShell metrics.")
    private String mRunName = "androidmediashell";

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        ITestDevice device = getDevice();

        setUp(device);

        // build test URL with URL parameters
        StringBuilder sb = new StringBuilder(getTestUrl());
        sb.append("?");
        for (Map.Entry<String, String> entry : getUrlParamMap().entrySet()) {
            if ("timeout".equals(entry.getKey().toLowerCase())) {
                try {
                    setTestTimeoutMs(Long.parseLong(entry.getValue()));
                } catch (NumberFormatException e) {
                    // fall through
                }
            }
            sb.append(entry.getKey());
            sb.append("=");
            sb.append(entry.getValue());
            sb.append("&");
        }
        String testUrl = sb.toString();
        testUrl = testUrl.substring(0, testUrl.length() - 1);
        setTestUrl(StringEscapeUtils.escapeShell(testUrl));

        if (getTestRunTimeoutMs() < getTestTimeoutMs()) {
            throw new RuntimeException(
                    String.format(
                            "Timeout for test run(%d ms) must be longer than timeout for a test(%d ms)",
                            getTestRunTimeoutMs(), getTestTimeoutMs()));
        }

        // run Cast tests on AndroidMediaShell
        listener.testRunStarted(mRunName, 0);
        String url = getTestUrl();
        CLog.i("Starting MediaShell to open [URL] %s", url);
        device.executeShellCommand(String.format(
                "am start -a android.intent.action.VIEW -n %s/.%s \"%s\"", MEDIASHELL_PACKAGE,
                MEDIASHELL_ACTIVITY, url));

        // poll until test ends
        long testStartTime = System.currentTimeMillis();
        long pollingIntervalMs = getTestTimeoutMs();
        int pid = getPid(device, "mediashell");
        boolean hasTestTimedout = false;
        boolean isProcessRunning = (pid != -1);
        String errMsg = null;
        // loop until (test is timed out) OR (all tests are done. but this could
        // be ignored if the number of tests is unknown, set to 0)
        // as long as mediashell process is kept alive during test run
        while (isProcessRunning && !hasTestTimedout
                && (getTestCount() == 0 || getTestEndedCount() < getTestCount())) {
            CLog.i("mediashell (pid %d) waiting for all tests to be completed... [%d%s]", pid,
                    getTestEndedCount(),
                            getTestCount() == 0 ? "" : "/" + String.valueOf(getTestCount()));
            RunUtil.getDefault().sleep(pollingIntervalMs);
            hasTestTimedout = ((System.currentTimeMillis() - testStartTime)
                    >= getTestRunTimeoutMs());
            isProcessRunning = (pid == getPid(device, "mediashell"));
        }

        // report results when all tests are executed
        if (!isProcessRunning) {
            errMsg = String.format("mediashell process (pid %d) was died before test ends. "
                    + "Check the device logcat.", pid);
        } else {
            if (hasTestTimedout && (getTestCount() > 0 && getTestEndedCount() < getTestCount())) {
                errMsg = String.format(
                        "Tests are imcompleted due to timeout (%d sec). %d tests aren't executed.",
                        mTestRunTimeout, getTestCount() - getTestEndedCount());
            } else {
                // test is completed
                if (!parseTestResults(listener, getLocalStorageFile())) {
                    errMsg = "Failed to parse test results at least once. "
                            + "Please check the host log.";
                }
            }
        }

        // handle error
        if (errMsg != null) {
            CLog.e(errMsg);
            listener.testRunFailed(errMsg);
        } else {
            long durationMs = System.currentTimeMillis() - testStartTime;
            listener.testRunEnded(durationMs, mEmptyMap);
        }

        // clear logs in the device
        device.executeShellCommand(String.format("rm -f \"%s\"*", MEDIASHELL_LOCALSTORAGE_DIR));
        CLog.i("Test %s ended.", mRunName);
    }

    /**
     * Get process id in device
     *
     * @param device the {@link ITestDevice} to run the tests on
     * @param processName name of process to find
     * @return process id in device. -1 if process is not found
     * @throws DeviceNotAvailableException
     */
    private int getPid(ITestDevice device, String processName) throws DeviceNotAvailableException {
        int pid = -1;
        String command = String.format("ps | grep %s$", processName);
        String output = device.executeShellCommand(command);
        String[] tokens = output.split("\\s+");
        if (tokens.length > 1) {
            try {
                pid = Integer.parseInt(tokens[1]);
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return pid;
    }

    /**
     * Set up preconditions
     *
     * @param device the {@link ITestDevice} to run the tests on
     * @throws DeviceNotAvailableException
     */
    private void setUp(ITestDevice device) throws DeviceNotAvailableException {
        // check if MediaShell package exists
        if (device.getAppPackageInfo(MEDIASHELL_PACKAGE) == null) {
            throw new RuntimeException(String.format("Package %s not found", MEDIASHELL_PACKAGE));
        }

        // check if host in test URL is reachable
        if (!isHostReachableFromDevice(device, getTestUrl())) {
            throw new RuntimeException(
                    String.format("Test URL (%s) is not reachable.", getTestUrl()));
        }

        // clean logs and stop MediaShell
        device.executeShellCommand(String.format("rm -f \"%s\"*", MEDIASHELL_LOCALSTORAGE_DIR));
        device.executeShellCommand(String.format("am force-stop %s", MEDIASHELL_PACKAGE));
        RunUtil.getDefault().sleep(3 * 1000);

        // Cast receiver service should start in advance of opening MediaShell
        // with a certain URL
        String commandStartService = String.format(
                "am startservice %s/.%s", MEDIASHELL_PACKAGE, MEDIASHELL_SERVICE);
        device.executeShellCommand(commandStartService);
    }

    /**
     * Get the count of tests ended.
     *
     * @return the count of tests ended.
     * @throws DeviceNotAvailableException
     */
    private int getTestEndedCount() throws DeviceNotAvailableException {
        int testEndedCount = 0;
        IFileEntry file = getLocalStorageFile();
        if (file != null) {
            String dbPath = file.getFullPath();
            String countTestsSql = String.format(
                    "sqlite3 \"%s\" \"SELECT COUNT(1) FROM ItemTable WHERE key != \'%s\';\"",
                    dbPath, LAST_UPDATE_UTC_KEY);
            String output = getDevice().executeShellCommand(countTestsSql);
            try {
                testEndedCount = Integer.parseInt(output.trim());
            } catch (NumberFormatException e) {
                // ignore this error so that test runs.
                CLog.w(dbPath + " has invalid value in the local storage");
            }
        }
        return testEndedCount;
    }

    /**
     * Parse test results from local storage file in device
     *
     * @param listener the {@link ITestInvocationListener} of test results
     * @param file {@link IFileEntry} of local storage file
     * @return true if no error in parsing all test results.
     * @throws DeviceNotAvailableException
     */
    private boolean parseTestResults(ITestInvocationListener listener, IFileEntry file)
            throws DeviceNotAvailableException {
        // Test results in local storage are stored in the SQLite database.
        // Select all JSON results from database except for one record named
        // "LastUpdateUTC_ResultStoreTag"
        String dbPath = file.getFullPath();
        String selectValuesSql = String.format(
                "sqlite3 \"%s\" \"SELECT value FROM ItemTable WHERE key != \'%s\';\"", dbPath,
                LAST_UPDATE_UTC_KEY);
        String output = getDevice().executeShellCommand(selectValuesSql);
        String[] results = output.split("\\r?\\n");
        if (results.length == 0)
            return false;
        boolean isSuccess = true;
        for (String result : results) {
            isSuccess &= parseTestResultJson(listener, result);
        }
        return isSuccess;
    }

    /**
     * Parse test result from JSON and report it accordingly.
     *
     * @param listener the {@link ITestInvocationListener} of test results
     * @param result {@link String} test result in JSON
     * @return true if no error in parsing results.
     */
    private boolean parseTestResultJson(ITestInvocationListener listener, String result) {
        CLog.d("Starting parsing test result : [%s]", result);
        try {
            // results in JSON passed as a parameter, e.g.
            // for mse-eme-* tests,
            // "test_name":"Presence"
            // "undefined":"30000"
            // "class_name":"MSE"
            // "failed":false
            // "has_error":false
            // "elapsed_secs":0.004
            // "stack":"..."
            // "ResultStoreTag":"ResultStoreTag"
            //
            // for cast-player test,
            // "test_name":"testVideoPlayback_PLAYREADY"
            // "stack":"..."
            // "failed":true
            // "elapsed_secs":0.428
            // "start_timestamp":"2014-07-29T10:30:38.068Z"
            // "measurement_json":"[{\"country\":\"USA\",\"partner\":\"PLAY READY\"}]"
            // "ResultStoreTag":1406629837243
            JSONTokener parser = new JSONTokener(result);
            JSONObject json = (JSONObject) parser.nextValue();

            for (String key : REQUIRED_SCHEMA_KEYS) {
                if (!json.has(key)) {
                    throw new JSONException(
                            String.format("Required key(%s) not found in results", key));
                }
            }
            String testName = json.optString(TEST_NAME_KEY);
            String className = json.optString(CLASS_NAME_KEY);
            boolean failed = json.optBoolean(FAILED_KEY);
            boolean hasError = json.optBoolean(HAS_ERROR_KEY, false);
            double elapsedSecs = json.optDouble(ELAPSED_SECS_KEY);
            String stack = json.optString(STACK_KEY);
            String resultStoreTag = json.optString(RESULT_STORE_TAG_KEY);

            // Keys found only from cast-player test
            String startTimestamp = json.optString(START_TIMESTAMP_KEY);
            String measurementJson = json.optString(MEASUREMENT_JSON_KEY);

            className = "".equals(className) ? getClass().getCanonicalName()
                    : getClass().getCanonicalName() + "." + className;

            TestIdentifier testId = new TestIdentifier(className, testName);
            listener.testStarted(testId);

            // add call stack & elapsed_secs to failure information
            if (failed || hasError) {
                listener.testFailed(testId,
                        String.format("[stack]\n%s\n[tag]\n%s", stack, resultStoreTag));
            }
            listener.testEnded(testId, mEmptyMap);
        } catch (JSONException e) {
            CLog.w("JSONException: %s, Failed to parse result: %s", e.getMessage(), result);
            // ignore exception in parsing results so that test runs till the
            // end.
            return false;
        }
        return true;
    }

    /**
     * Get local storage file where test results are saved
     *
     * @return {@link IFileEntry} of local storage file. null if not exist.
     * @throws DeviceNotAvailableException
     */
    private IFileEntry getLocalStorageFile() throws DeviceNotAvailableException {
        if (mLocalStorageFile == null) {
            String command = String.format("ls \"%s\"*%s", MEDIASHELL_LOCALSTORAGE_DIR,
                    LOCALSTORAGE_EXT_NAME);
            String localStorageFullPath = getDevice().executeShellCommand(command).trim();
            if (!localStorageFullPath.contains("No such file or directory") &&
                    getDevice().doesFileExist(localStorageFullPath)) {
                mLocalStorageFile = getDevice().getFileEntry(localStorageFullPath);
                CLog.i("local storage found in %s", localStorageFullPath);
            }
        }
        return mLocalStorageFile;
    }

    /**
     * Check to see if test URL is reachable from device
     *
     * @param device the {@link ITestDevice} to run the tests on
     * @param testUrl {@link String} of URL pointing to test suites
     * @return true if host is reachable from device
     * @throws DeviceNotAvailableException
     */
    private boolean isHostReachableFromDevice(ITestDevice device, String testUrl)
            throws DeviceNotAvailableException {
        try {
            URL url = new URL(testUrl);
            String hostname = url.getHost();
            final int nTimeout = 10; // in seconds
            final String PING_SUCCESS = "1 packets transmitted, 1 received";
            String command = String.format("ping -c 1 -w %d %s", nTimeout, hostname);
            String output = device.executeShellCommand(command);
            if (output.contains(PING_SUCCESS)) {
                return true;
            } else {
                CLog.e(hostname
                        + " is not reachable. Please check to see if DUT connects to the URL.");
                return false;
            }
        } catch (MalformedURLException e) {
            CLog.e(e);
            return false;
        }
    }

    /**
     * @return {@link String} device accessible URL to load the tests.
     */
    protected String getTestUrl() {
        return mTestUrl;
    }

    /**
     * Set URL pointing to test suites with validated path and parameters
     *
     * @param testUrl {@link String} of URL pointing to test suites
     */
    protected void setTestUrl(String testUrl) {
        mTestUrl = testUrl;
    }

    /**
     * @return the URL parameters map to be appended after the test URL.
     */
    public Map<String, String> getUrlParamMap() {
        return mUrlParamMap;
    }

    /**
     * @param urlParamMap the URL parameters to be appended after the test URL.
     */
    public void setUrlParamMap(Map<String, String> urlParamMap) {
        mUrlParamMap = urlParamMap;
    }

    /**
     * @return timeout to run all tests.
     */
    protected long getTestRunTimeoutMs() {
        return mTestRunTimeout * 1000;
    }

    /**
     * @return timeout to run a test.
     */
    protected long getTestTimeoutMs() {
        return mTestTimeoutMs;
    }

    protected void setTestTimeoutMs(long testTimeoutMs) {
        mTestTimeoutMs = testTimeoutMs;
    }

    /**
     * @return number of all tests.
     */
    protected int getTestCount() {
        return mTestCount;
    }
}
