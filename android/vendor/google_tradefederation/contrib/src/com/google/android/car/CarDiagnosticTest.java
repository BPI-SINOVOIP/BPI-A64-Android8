// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.car;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.LogcatReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Car diagnostic API end-to-end test */
public class CarDiagnosticTest implements IDeviceTest, IRemoteTest {

    @Option(
        name = "injector",
        description = "Path to the diagnostics events injector",
        importance = Importance.ALWAYS
    )
    private String mInjectorPath;

    @Option(
        name = "diagjson",
        description = "Path to the diagnostics events JSON file",
        importance = Importance.ALWAYS
    )
    private String mDiagJsonPath;

    @Option(
        name = "app-start-delay",
        description = "Time (sec) to wait for diagnostic test app to fully start",
        importance = Importance.ALWAYS
    )
    private long mAppStartDelay = 1;

    private ITestDevice mDevice;

    private static final String RUN_KEY = "car_diagnostic_test";
    private static final String START_LISTEN_CMD =
            "am broadcast -a com.google.android.car.diagnosticverifier.action.START_LISTEN";

    private static final String STOP_LISTEN_CMD =
            "am broadcast -a com.google.android.car.diagnosticverifier.action.STOP_LISTEN";

    private static final String LOGCAT_CMD = "logcat | grep DiagnosticVerifier";

    private static final long LOGCAT_SIZE = 80 * 1024 * 1024;

    /** Event data injection should not be longer than 2 hours */
    private static final long INJECTOR_TIMEOUT = 2 * 60 * 60 * 1000;

    private static final Pattern VERIFICATION_RESULT_PATH_PATTERN =
            Pattern.compile(
                    "^\\d*-\\d*\\s*\\d*:\\d*:\\d*.\\d*\\s*\\d*\\s*\\d*\\s*I\\s*"
                            + "DiagnosticVerifier:\\s*Verification result: (.*)$");

    /** {@inheritDoc} */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /** {@inheritDoc} */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    static class VerificationResult {

        static final String TEST_CASE_KEY = "testCase";
        static final String TEST_RESULT_KEY = "success";
        static final String ERROR_MESSAGE_KEY = "errorMessage";

        public final String testCase;
        public final boolean success;
        public final String errorMessage;

        private VerificationResult(String testCase, boolean success, String errorMessage) {
            this.testCase = testCase;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static VerificationResult fromJSON(String jsonStr) throws JSONException {
            JSONObject jsonResult = new JSONObject(jsonStr);
            return new VerificationResult(
                    jsonResult.getString(TEST_CASE_KEY),
                    jsonResult.getBoolean(TEST_RESULT_KEY),
                    jsonResult.getString(ERROR_MESSAGE_KEY));
        }
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        listener.testRunStarted(RUN_KEY, 3);
        long testRunStartTime = System.currentTimeMillis();

        try {
            Thread.sleep(mAppStartDelay * 1000);
        } catch (InterruptedException e) {
            CLog.w("Interruption: " + e);
        }

        startListen();

        if (!injectEvents()) {
            listener.testRunFailed("Failed to inject diagnostic events");
            return;
        }

        stopListen();

        String testResultLocation = getTestResultLocation();
        if (testResultLocation == null || testResultLocation.isEmpty()) {
            listener.testRunFailed("Failed to get test result location on device");
            return;
        }

        File localFile = pull(testResultLocation);

        List<VerificationResult> results;
        try {
            results = parseResults(localFile);
        } catch (IOException e) {
            listener.testRunFailed("Failed to read verification result file. " + e);
            return;
        } catch (JSONException e) {
            listener.testRunFailed("Failed to parse JSON result file. " + e);
            return;
        }

        for (VerificationResult result : results) {
            TestIdentifier testId =
                    new TestIdentifier(getClass().getCanonicalName(), result.testCase);
            listener.testStarted(testId);
            CLog.i("Test Case: " + result.testCase);
            CLog.i(String.format("Test result: %s", result.success ? "Pass" : "Fail"));
            if (!result.success) {
                CLog.i("Error Msg: " + result.errorMessage);
                listener.testFailed(testId, result.errorMessage);
            }
            listener.testEnded(testId, Collections.emptyMap());
        }

        long testRunEndTime = System.currentTimeMillis();
        listener.testRunEnded(testRunStartTime - testRunEndTime, Collections.emptyMap());
    }

    private void startListen() throws DeviceNotAvailableException {
        CLog.i("Signal diagnostic verifier to start listening for events.");
        getDevice().executeShellCommand(START_LISTEN_CMD);
    }

    private void stopListen() throws DeviceNotAvailableException {
        CLog.i("Signal diagnostic verifier to stop listening for events and do verification.");
        getDevice().executeShellCommand(STOP_LISTEN_CMD);
    }

    private boolean injectEvents() {
        String serialNum = getDevice().getSerialNumber();
        CLog.i("Injecting diagnostic events to device " + serialNum);
        CLog.i("Calling injector: " + mInjectorPath);
        CLog.i("Diagnostic events data: " + mDiagJsonPath);
        CommandResult result =
                RunUtil.getDefault()
                        .runTimedCmd(
                                INJECTOR_TIMEOUT, mInjectorPath, "-s " + serialNum, mDiagJsonPath);
        if (result.getStatus() != CommandStatus.SUCCESS) {
            CLog.e("Failed to execute diagnostics injector: " + result.getStderr());
            return false;
        }
        return true;
    }

    private String getTestResultLocation() {
        LogcatReceiver receiver = new LogcatReceiver(getDevice(), LOGCAT_CMD, LOGCAT_SIZE, 0);
        receiver.start();
        CLog.i("Receiving test result file location on device.");
        final int RETRY_MAX = 5;
        final int SLEEP_TIMEOUT = 1000;
        String resultLocation = null;

        for (int i = 0; i < RETRY_MAX; i++) {
            try {
                Thread.sleep(SLEEP_TIMEOUT);
                resultLocation = parseResultLocation(receiver);
                if (resultLocation != null) {
                    break;
                }
            } catch (IOException e) {
                CLog.w("Failed to read logcat: " + e);
            } catch (InterruptedException e) {
                CLog.w("Interruption: " + e);
            }
        }
        receiver.stop();
        CLog.i("Verification result location on device: " + resultLocation);
        return resultLocation;
    }

    private String parseResultLocation(LogcatReceiver receiver) throws IOException {
        BufferedReader br =
                new BufferedReader(
                        new InputStreamReader(
                                receiver.getLogcatData().createInputStream(), "UTF-8"));

        String line;
        while ((line = br.readLine()) != null) {
            Matcher matcher = VERIFICATION_RESULT_PATH_PATTERN.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private File pull(String remoteLocation) throws DeviceNotAvailableException {
        File localResultFile = mDevice.pullFile(remoteLocation);
        CLog.i(
                String.format(
                        "Pulled remote file %s to local %s",
                        remoteLocation, localResultFile.getAbsolutePath()));
        return localResultFile;
    }

    private List<VerificationResult> parseResults(File localFile)
            throws IOException, JSONException {
        List<VerificationResult> results = new ArrayList<>();
        if (localFile == null) {
            return results;
        }
        String jsonResultStr = FileUtil.readStringFromFile(localFile);
        JSONArray jsonResults = new JSONArray(jsonResultStr);
        for (int i = 0; i < jsonResults.length(); i++) {
            results.add(VerificationResult.fromJSON(jsonResults.getString(i)));
        }
        return results;
    }
}
