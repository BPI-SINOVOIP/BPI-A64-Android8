// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.oat;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;

import com.android.tradefed.util.AbiFormatter;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.RunUtil;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Test that executes a suite of runtime performance tests on given device.
 */
@OptionClass(alias = "runtime-perf-test")
public class RuntimePerfTest implements IDeviceTest, IRemoteTest {

    private static final String PERF_CMD = "%s dalvikvm|#ABI#| %s -cp %s %s %s";

    private ITestDevice mDevice = null;

    @Option(name = "perf-results-regex",
            description = "Additional runtime args. May be repeated.",
            importance = Option.Importance.ALWAYS)
    private String mResultRegex = null;

    @Option(name = AbiFormatter.FORCE_ABI_STRING,
            description = AbiFormatter.FORCE_ABI_DESCRIPTION,
            importance = Importance.IF_UNSET)
    private String mForceAbi = "";

    @Option(name = "pre-perf-arg",
            description = "Additional argument to prepend on the performance command.")
    private String mPrePerfArg = "";

    @Option(name = "runtime-args",
            description = "Additional runtime args. May be repeated.")
    private Collection<String> mRuntimeArgs = new LinkedList<String>();

    @Option(name = "perf-tests-jarfiles",
            description = "The list of jarfiles needed for running the tests. May be repeated.",
            importance = Option.Importance.ALWAYS)
    private Collection<String> mJarFiles = new LinkedList<String>();

    @Option(name = "perf-tests-map",
            description = "Maps a given test main class to its test label, "
                + "can also map to schema prefix. Can be repeated."
                + "Format: mainClass->testLabel"
                + "or : mainClass->testLabel->schemaPrefix",
            importance = Option.Importance.ALWAYS)
    private Collection<String> mPerfTestMap = new ArrayList<String>();

    @Option(name = "benchmark-args",
            description = "Additional benchmark args to set. May be repeated.")
    private Collection<String> mBenchmarkArgs = new LinkedList<String>();

    @Option(name = "perf-test-timeout", description =
            "The max time in ms for a performance test to run. " +
                    "Test run will be aborted if any test takes longer.")
    private int mMaxTestTimeMs = 1 * 60 * 1000;

    @Option(name = "working-dir", description =
        "The current directory to run all the benchmark under." +
                "Can be null, which means default: /")
    private String mCurrentPath = null;

    @Option(name = "ld-library-path", description = "Provide these additional paths as shared "
            + "library search paths")
    private Collection<String> mLdLibraryPaths = new ArrayList<>();

    @Option(name = "test-retries", description = "The max number of retries to do if test fails. ")
    private int mTestRetryAttempts = 0;

    @Option(name = "post-test-cooldown",
        description = "Whether wait for cooltime "
            + "for 1/10 of the runtime after each test run. Default: false")
    private boolean mCooldownEnabled = false;

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
     * Run the performance test on the device.
     *
     * @param testLabel
     * @param schemaPrefix
     * @param testMainClass
     * @param listener
     * @throws DeviceNotAvailableException
     */
    protected void runTest(String testLabel, String schemaPrefix,
            String testMainClass, ITestRunListener listener)
            throws DeviceNotAvailableException {
        String runTimeArgs = "";
        String classpath = "";
        String benchmarkArgs = "";

        if (mJarFiles != null && !mJarFiles.isEmpty()) {
            classpath = AbiFormatter.formatCmdForAbi(Joiner.on(":").join(mJarFiles), mForceAbi);
        }

        if (mRuntimeArgs != null && !mRuntimeArgs.isEmpty()) {
            runTimeArgs = AbiFormatter.formatCmdForAbi(Joiner.on(" ").join(mRuntimeArgs),
                    mForceAbi);
        }

        if (mBenchmarkArgs != null && !mBenchmarkArgs.isEmpty()) {
            benchmarkArgs = AbiFormatter.formatCmdForAbi(Joiner.on(" ").join(mBenchmarkArgs),
                    mForceAbi);
        }

        StringBuilder prefix = new StringBuilder();
        // first insert the "pre-perf-arg's"
        prefix.append(AbiFormatter.formatCmdForAbi(mPrePerfArg, mForceAbi));
        // next consider the LD_LIBRARY_PATH prefix
        if (!mLdLibraryPaths.isEmpty()) {
            prefix.append(" LD_LIBRARY_PATH=");
            prefix.append(ArrayUtil.join(":", mLdLibraryPaths));
        }
        String cmd = String.format(AbiFormatter.formatCmdForAbi(PERF_CMD, mForceAbi),
                prefix.toString(), runTimeArgs, classpath,
                AbiFormatter.formatCmdForAbi(testMainClass, mForceAbi),
                benchmarkArgs);
        if (mCurrentPath != null){
            cmd = "cd "+ AbiFormatter.formatCmdForAbi(mCurrentPath, mForceAbi) + " && " + cmd;
        }
        CLog.d("About to run performance test command: %s", cmd);
        listener.testRunStarted(testLabel, 0);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        long testStartTime = System.currentTimeMillis();
        getDevice().executeShellCommand(cmd, receiver,
                mMaxTestTimeMs /* maxTimeToShellOutputResponse */,
                TimeUnit.MILLISECONDS,
                mTestRetryAttempts /* retryAttempts */);
        String output = receiver.getOutput();
        CLog.d("%s on %s returned %s", cmd, getDevice().getSerialNumber(), output);
        Map<String, String> result = parseResults(output, schemaPrefix);
        long testRunTime = System.currentTimeMillis() - testStartTime;
        CLog.d("About to report '%s' metrics: %s", testLabel, result);
        listener.testRunEnded(testRunTime, result);
        if (mCooldownEnabled) {
            RunUtil.getDefault().sleep(testRunTime / 10);
            CLog.d("slept for cooldown: %d ms", testRunTime / 10);
        }
    }

    /**
     * Helper method used to parse the stdout results.
     *
     * @param output
     * @param schemaPrefix schema prefix to use, can be null
     */
    protected Map<String, String> parseResults(String output, String schemaPrefix) {
        Pattern regexPattern = Pattern.compile(mResultRegex);
        Map<String, String> result = new HashMap<String, String>();
        Matcher m = regexPattern.matcher(output);
        while (m.find()) {
            String key;
            if (schemaPrefix != null) {
                key = schemaPrefix + m.group(1);
            } else {
                key = m.group(1);
            }
            result.put(key, m.group(2));
            CLog.d("Found match for regex %s -> %s and %s.", regexPattern, key, m.group(2));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }
        for (String testEntry : mPerfTestMap) {
            String[] tokens = testEntry.split("->");
            if (tokens.length < 2 || tokens.length > 3) {
                CLog.e("Invalid perf test format: '%s'", testEntry);
                continue;
            }
            String testMainClass = tokens[0];
            String testLabel = tokens[1];
            String schemaPrefix = null;
            if (tokens.length == 3) {
                schemaPrefix = tokens[2];
            }
            runTest(testLabel, schemaPrefix, testMainClass, listener);
        }
    }
}
