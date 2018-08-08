// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.android.performance;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.AbiFormatter;

import org.junit.Assert;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/*
 * Runs a series of Dalvik microbenchmark performance tests and posts the results to the release
 * dashboard.
 */
@OptionClass(alias = "dalvik-perf")
public class DalvikPerformanceTest implements IDeviceTest, IRemoteTest {

    @Option(name = "ru-key", description = "Name of ru key to report performance benchmarks to.")
    private String mRuKey = "dalvik-perf";

    @Option(name = "test-jar-name", description = "Test jar to run.")
    private String mTestJarName = "RunPerf.jar";

    @Option(name = "test-class", description = "Test class to run.")
    private String mClassName = "com.android.unit_tests.RunPerf";

    @Option(name = "shell-cmd-retry-count", description = "Number of times the test gets to retry.")
    private int mCmdRetryTimes = 1;

    @Option(name = "shell-cmd-timeout-msec", description = "Timeout in msecs for shell commands.")
    private int mCmdTimeoutMsecs = 5 * 60 * 1000;

    @Option(name = "test-cmd", description = "Test command to run.")
    private String mTestCommand = "dalvikvm|#ABI#|";

    @Option(name = "test-path", description = "Default device path to test jars.")
    private String mTestPath = "/data/dalvikperf|#ABI32#|";

    private ITestDevice mDevice;

    @Option(name = AbiFormatter.FORCE_ABI_STRING,
            description = AbiFormatter.FORCE_ABI_DESCRIPTION,
            importance = Importance.IF_UNSET)
    private String mForceAbi = "32";

    //FIXME: find a better way to store the list of benchmarks.
    // List of benchmarks to run.
    private static final String[] BENCHMARKS = {
        "Ackermann",
        "AddTest",
        "AddMemberVariableTest",
        "AddMemberVariableInMethodTest",
        "ArrayListIterator",
        "BoundsCheckTest",
        "CallRoundTrip6",
        "CallRoundTrip30",
        "EmptyStaticMethod",
        "EmptyVirtualMethod",
        "EmptyJniStaticMethod0",
        "EmptyJniStaticMethod6",
        "EmptyJniStaticMethod6L",
        "EmptyInternalStaticMethod",
        "EmptyInlineStaticMethod",
        "EmptyInterfaceMethodTest",
        "EmptyVirtualMethodTestInLocal",
        "ExceptionThrow1",
        "ExceptionThrow10",
        "FibonacciFast",
        "FibonacciSlow",
        "FloatMathLib",
        "FloatOps",
        "FloatOpsD",
        "FloatRandom",
        "ForArrayList",
        "ForEachArrayList",
        "ForLocalArrayList",
        "ForLoopTest",
        "ForLoopSizeCalledInside",
        "ForLoopSizeCalledOutside",
        "HashMapContainsKey",
        "HashMapIterator",
        "HashMapPut",
        "InstanceOfTrivial",
        "InstanceOfInterface",
        "InstanceOfNot",
        "InterfaceCalls0",
        "InterfaceCalls1",
        "IntOps",
        "JniVersion",
        "JniField",
        "JniCallback",
        "LocalVariableAccess",
        "LongOps",
        "MemberVariableAccess",
        "NestedLoop",
        "StringCompareTo200",
        "StringConcatenation1",
        "StringCrawl",
        "StringEquals2",
        "StringEquals200",
        "StringLength",
        "WhileLoopTest",
        "AtomicGetAndSetInt",
        "SynchronizedGetAndSetInt",
        "SlowMath1",
        "SlowMath2"
    };


    /*
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Map<String, String> metrics = new HashMap<String, String> ();
        // Stop the runtime
        mDevice.executeShellCommand("stop");
        mDevice.setRecoveryMode(RecoveryMode.ONLINE);
        // Disable power management
        mDevice.executeShellCommand(
                "echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
        // Disable stack randomization
        mDevice.executeShellCommand("echo 0 > /proc/sys/kernel/randomize_va_space");
        // Run test for all benchmarks.
        for (String benchmark : BENCHMARKS) {
            String cmd = String.format("%s -Djava.library.path=%s:/system/lib -cp %s/%s %s -b %s",
                    mTestCommand, mTestPath, mTestPath, mTestJarName, mClassName, benchmark);
            cmd = AbiFormatter.formatCmdForAbi(cmd, mForceAbi);
            CLog.d("About to execute: %s", cmd);
            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
            mDevice.executeShellCommand(cmd, receiver,
                    mCmdTimeoutMsecs, TimeUnit.MILLISECONDS, mCmdRetryTimes);
            String output = receiver.getOutput();
            CLog.d("Got metric: %s ", output);
            String metric = parseOutput(benchmark, output);
            if (metric != null) {
                metrics.put(benchmark, metric);
            } else {
                CLog.e("Failed to get metric for %s", benchmark);
            }
        }
        reportMetrics(listener, mRuKey, metrics);
        // Restart the runtimestop
        mDevice.executeShellCommand("start");
    }

    /**
     * Parse the shell output and finds the performance metric from a string.
     * @param benchmark {@link String} label
     * @param shellOutput {@link String} output
     * @return {@link String} runtime in milliseconds
     */
    private String parseOutput(String benchmark, String shellOutput) {
        Assert.assertNotNull(benchmark);
        Assert.assertNotNull(shellOutput);
        String[] tokens = shellOutput.split(",");
        if (tokens.length == 2 && tokens[0].equalsIgnoreCase(benchmark)) {
            return tokens[1].trim();
        }
        return null;
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
