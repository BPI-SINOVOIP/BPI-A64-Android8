// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.oat;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.AbiFormatter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Test that runs all Java Debugger Wire Protocol (JDWP) tests on given
 * device.
 */
@OptionClass(alias = "jdwp-test")
public class JDWPTest implements IDeviceTest, IRemoteTest {

    private ITestDevice mDevice = null;

    @Option(name = "test-timeout", description = "The max time in ms for all jdwp tests to run.")
    private int mMaxTestTimeMs = 30 * 60 * 1000;

    @Option(name = "test-retries", description = "The max number of retries to do if test fails. ")
    private int mTestRetryAttempts = 0;

    @Option(name = "jdwp-test-timeout", description = "Timeout for each jdwp test.")
    private int mJdwpTimeoutMs = 10 * 1000;

    @Option(name = "test-label", description = "The label for the test. ")
    private String mTestLabel = "jdwp_tests";

    @Option(name = "output-file", description = "The name for the junit test output.")
    private String mOutputFile = "jdwp-test-logs";

    @Option(name = "jdwp-runtime-options", description = "Runtime options to add to jdwp.")
    private String mJdwpRuntimeOptions = "";

    @Option(name = "vm",
            description = "VM to use. You can also pass additional args. e.g.: dalvikvm -XXlib:libart.so")
    private String mVMType = "dalvikvm|#ABI#| -XXlib:/data/lib|#ABI32#|/libartd.so";

    @Option(name = AbiFormatter.FORCE_ABI_STRING,
            description = AbiFormatter.FORCE_ABI_DESCRIPTION,
            importance = Importance.IF_UNSET)
    private String mForceAbi = "32";

    private static final String JDWP_CMD = "%s -cp " +
            " /data/jdwp/apache-harmony-jdwp-tests.jar:/data/junit/junit.jar" +
            " -Djpda.settings.verbose=true" +
            " -Djpda.settings.debuggeeJavaPath=\"%s%s\"" +
            " -Djpda.settings.timeout=%d" +
            " -Djpda.settings.waitingTime=%d" +
            " org.apache.harmony.jpda.tests.share.AllTests";

    private static final Pattern JUNIT_FAILED_RESULT =
            Pattern.compile("Tests run: ([0-9]*),  Failures: ([0-9]*),  Errors: ([0-9]*).*");

    private static final Pattern JUNIT_ALL_PASS_RESULT = Pattern.compile("OK \\(([0-9]*) tests\\)");

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
     * Set the max time in ms for a test to run.
     */
    void setMaxTestTimeMs(int timeout) {
        mMaxTestTimeMs = timeout;
    }

    /**
     * Parse the summary of the tests from the stdout.
     *
     * @param output {@link String} of the stdout
     * @return map of the results.
     */
    private Map<String, String> parseOutput(String output) {
        int totNumTests = 0;
        int numTestsPassed = 0;
        int numTestsFailed = 0;
        int numErrors = 0;

        String[] lines = output.split("\n");
        Map<String, String> testMetrics = new HashMap<String, String>(3);

        for (String line : lines) {
            line = line.replaceAll("[\\r\\n]", "");
            Matcher m = JUNIT_FAILED_RESULT.matcher(line);
            Matcher p = JUNIT_ALL_PASS_RESULT.matcher(line);
            if (m.matches()) {
                String totalNumTests = m.group(1);
                String totalNumFailures = m.group(2);
                String totalNumErrors = m.group(3);
                totNumTests = Integer.parseInt(totalNumTests);
                numTestsFailed = Integer.parseInt(totalNumFailures);
                numErrors = Integer.parseInt(totalNumErrors);
                numTestsPassed = totNumTests - numTestsFailed - numErrors;
                CLog.i("Pass: %d Failures: %d, Errors: %d",
                        numTestsPassed, numTestsFailed, numErrors);
                testMetrics.put("Pass", Integer.toString(numTestsPassed));
                testMetrics.put("Fail", Integer.toString(numTestsFailed));
                testMetrics.put("Error", Integer.toString(numErrors));
                break;
            }
            if (p.matches()) {
                String totalNumTests = p.group(1);
                totNumTests = Integer.parseInt(totalNumTests);
                CLog.i("Pass: %d Failures: 0, Errors: 0", totNumTests);
                testMetrics.put("Pass", Integer.toString(totNumTests));
                testMetrics.put("Fail", "0");
                testMetrics.put("Error", "0");
                break;
            }
        }
        if (testMetrics.isEmpty()) {
            CLog.e("Could not find test data from output '%s'", output);
        }
        return testMetrics;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }
        String cmd = String.format(JDWP_CMD, mVMType, mVMType, mJdwpRuntimeOptions, mJdwpTimeoutMs,
                mJdwpTimeoutMs);
        cmd = AbiFormatter.formatCmdForAbi(cmd, mForceAbi);
        CLog.i("About to run dalvikvm test command: %s", cmd);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        mDevice.executeShellCommand(cmd, receiver,
                mMaxTestTimeMs /* maxTimeToShellOutputResponse */,
                TimeUnit.MILLISECONDS,
                mTestRetryAttempts /* retryAttempts */);
        String output = receiver.getOutput();
        Map<String, String> results = parseOutput(output);
        try (InputStreamSource is = new ByteArrayInputStreamSource(output.getBytes())) {
            listener.testLog(mOutputFile, LogDataType.TEXT, is);
        }
        reportMetrics(listener, mTestLabel, results);
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     *
     * @param listener the {@link ITestInvocationListener} of test results
     * @param runName the test name
     * @param metrics the {@link Map} that contains metrics for the given test
     */
    void reportMetrics(ITestInvocationListener listener, String runName,
            Map<String, String> metrics) {
        // Create an empty testRun to report the parsed runMetrics
        CLog.d("About to report metrics: %s", metrics);
        listener.testRunStarted(runName, 0);
        listener.testRunEnded(0, metrics);
    }
}
