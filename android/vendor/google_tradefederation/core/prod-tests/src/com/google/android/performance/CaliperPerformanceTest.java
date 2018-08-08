// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.performance;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import org.junit.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Runs a series of caliper microbenchmark performance tests and posts the results to the release
 * dashboard.
 * TODO: move this out of vendor/google
 */
@OptionClass(alias = "caliper-perf")
public class CaliperPerformanceTest implements IDeviceTest, IRemoteTest {
    @Option(name = "vm", description = "VM to use.")
    private String mVMType = "dalvikvm";

    @Option(name = "cache-name", description = "Cache directory name to use.")
    private String mCacheName = "dalvik-cache";

    @Option(name = "shell-cmd-retry-count", description = "Number of times the test gets to retry.")
    private int mCmdRetryTimes = 1;

    @Option(name = "shell-cmd-timeout-msec", description = "Timeout in msecs for shell commands.")
    private int mCmdTimeoutMsecs = 5 * 60 * 1000;

    private static final Pattern BENCHMARK_PATTERN = Pattern.compile(
            ".*\\d+%.*\\{.*benchmark=(.*)\\} (\\d+\\.\\d+) ns;.*trials");

    private static final String TEMP_PATH = "/data/local/tmp/benchmark-tests";
    private static final String BENCHMARK_CMD = "ANDROID_DATA=%s %s " +
            "-classpath /data/caliperperf/benchmarks.jar com.google.caliper.Runner %s";
    private static final String SEPARATOR = "_";

    private ITestDevice mDevice;

    // FIXME: find a better way to store the list of benchmarks.
    // List of benchmarks to run.
    private static final String[] BENCHMARKS = {
            "benchmarks.AdditionBenchmark",
            "benchmarks.ArrayCopyBenchmark",
            "benchmarks.ArrayIterationBenchmark",
            "benchmarks.ArrayListIterationBenchmark",
            "benchmarks.BufferedZipFileBenchmark",
            "benchmarks.FieldAccessBenchmark",
            "benchmarks.HashedCollectionsBenchmark",
            "benchmarks.MethodInvocationBenchmark",
            "benchmarks.MultiplicationBenchmark",
            "benchmarks.StringIterationBenchmark",
            "benchmarks.VirtualVersusInterfaceBenchmark",
            "benchmarks.XmlParseBenchmark",
            "benchmarks.regression.AnnotatedElementBenchmark",
            "benchmarks.regression.BigIntegerBenchmark",
            "benchmarks.regression.BitSetBenchmark",
            "benchmarks.regression.ByteBufferBenchmark",
            "benchmarks.regression.ByteBufferScalarVersusVectorBenchmark",
            "benchmarks.regression.CharacterBenchmark",
            "benchmarks.regression.CharsetBenchmark",
            "benchmarks.regression.CharsetForNameBenchmark",
            "benchmarks.regression.ChecksumBenchmark",
            "benchmarks.regression.CipherBenchmark",
            "benchmarks.regression.DateToStringBenchmark",
            "benchmarks.regression.DefaultCharsetBenchmark",
            "benchmarks.regression.DigestBenchmark",
            "benchmarks.regression.DnsBenchmark",
            "benchmarks.regression.DoPrivilegedBenchmark",
            "benchmarks.regression.DoubleBenchmark",
            "benchmarks.regression.EqualsHashCodeBenchmark",
            "benchmarks.regression.ExpensiveObjectsBenchmark",
            "benchmarks.regression.FloatBenchmark",
            "benchmarks.regression.FormatterBenchmark",
            "benchmarks.regression.HostnameVerifierBenchmark",
            "benchmarks.regression.IntConstantDivisionBenchmark",
            "benchmarks.regression.IntConstantMultiplicationBenchmark",
            "benchmarks.regression.IntConstantRemainderBenchmark",
            "benchmarks.regression.IntegerBenchmark",
            "benchmarks.regression.IntegralToStringBenchmark",
            "benchmarks.regression.JarFileBenchmark",
            "benchmarks.regression.KeyPairGeneratorBenchmark",
            "benchmarks.regression.LoopingBackwardsBenchmark",
            "benchmarks.regression.MathBenchmark",
            "benchmarks.regression.MessageDigestBenchmark",
            "benchmarks.regression.MutableIntBenchmark",
            "benchmarks.regression.NativeMethodBenchmark",
            "benchmarks.regression.ParseBenchmark",
            "benchmarks.regression.PriorityQueueBenchmark",
            "benchmarks.regression.PropertyAccessBenchmark",
            "benchmarks.regression.RandomBenchmark",
            "benchmarks.regression.RealToStringBenchmark",
            "benchmarks.regression.ReflectionBenchmark",
            "benchmarks.regression.SchemePrefixBenchmark",
            "benchmarks.regression.SerializationBenchmark",
            "benchmarks.regression.SignatureBenchmark",
            "benchmarks.regression.SSLSocketBenchmark",
            "benchmarks.regression.StrictMathBenchmark",
            "benchmarks.regression.StringBenchmark",
            "benchmarks.regression.StringBuilderBenchmark",
            "benchmarks.regression.StringCaseMappingBenchmark",
            "benchmarks.regression.StringIsEmptyBenchmark",
            "benchmarks.regression.StringLengthBenchmark",
            "benchmarks.regression.StringSplitBenchmark",
            "benchmarks.regression.StringToRealBenchmark",
            "benchmarks.regression.SystemPropertiesBenchmark",
            "benchmarks.regression.ThreadLocalBenchmark",
            "benchmarks.regression.TimeZoneBenchmark",
            "benchmarks.regression.URLConnectionBenchmark",
            "benchmarks.regression.XmlEntitiesBenchmark",
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

        // Create temp cache for storing the oat or dex files.
        if (!mDevice.doesFileExist(TEMP_PATH)) {
            mDevice.executeShellCommand(String.format("mkdir %s", TEMP_PATH));
        }
        File cache = new File(TEMP_PATH, mCacheName);
        if (!mDevice.doesFileExist(cache.getAbsolutePath())) {
            mDevice.executeShellCommand(String.format("mkdir %s", cache.getAbsolutePath()));
        }
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
            Map<String, String> metrics = new HashMap<String, String>();
            String cmd = String.format(BENCHMARK_CMD, TEMP_PATH, mVMType, benchmark);
            CLog.d("About to execute: %s", cmd);
            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
            mDevice.executeShellCommand(cmd, receiver,
                    mCmdTimeoutMsecs, TimeUnit.MILLISECONDS, mCmdRetryTimes);
            String output = receiver.getOutput();
            parseOutput("", output, metrics);
            // Also save the output of the stdout.
            String label = getClassWithoutPackage(benchmark);
            try (InputStreamSource is = new ByteArrayInputStreamSource(output.getBytes())) {
                listener.testLog(label, LogDataType.TEXT, is);
            }
            // report metrics for the given benchmark.
            reportMetrics(listener, label, metrics);
        }
        // Restart the runtime
        mDevice.executeShellCommand("start");
    }

    /**
     * Helper method to get the class name without the package
     * <p> Exposed for unit-testing
     *
     * @param fullClassName {@link String} full qualified class name, which includes the package.
     *
     * @return {@link String} class name without the package.
     */
    protected String getClassWithoutPackage(String fullClassName) {
        Assert.assertNotNull("Invalid class name", fullClassName);
        int index = fullClassName.lastIndexOf ('.') + 1;
        return fullClassName.substring(index);
    }

    /**
     * Parse the shell output and finds the performance metric from a string.
     * </p> Exposed for unit-testing.
     *
     * @param benchmark {@link String} classname of the benchmark test
     * @param shellOutput {@link String} output from the caliper tests
     * @param metrics where to store the parsed metrics
     */
    protected void parseOutput(String benchmark, String shellOutput, Map<String, String> metrics) {
        Assert.assertNotNull(metrics);
        Assert.assertNotNull(shellOutput);
        Matcher m = BENCHMARK_PATTERN.matcher(shellOutput);
        while (m.find()) {
            String label = generateKeyFromString(benchmark, m.group(1));
            String metric = m.group(2);
            CLog.d("Benchmark: %s -> %s nanoseconds", label, metric);
            metrics.put(label, metric);
        }
    }

    /**
     * Class used to sort the params based on their name.
     */
    public class ParamAndValue implements Comparable<ParamAndValue> {
        private final String mParam;
        private final String mValue;

        public ParamAndValue(String param, String value) {
            this.mParam = param;
            this.mValue = value;
        }

        public String getParam() {
            return mParam;
        }

        public String getValue() {
            return mValue;
        }

        @Override
        public int compareTo(ParamAndValue o) {
            return this.mParam.compareTo(o.getParam());
        }
    }

    /**
     * Generate a string key from the list of parameters and benchmark name. The
     * parameters are alphabetically sorted, and their values are used as part
     * of the key.
     *
     * @param prefix {@link String} to prepend to the key
     * @param value {@link String} to parse benchmark and params from
     * @return {@link String} the key
     */
    protected String generateKeyFromString(String prefix, String value) {
        Assert.assertNotNull("Null prefix", prefix);
        // Split the different params from the string.
        String[] tokens = value.split(",");
        Assert.assertTrue("Missing valid string to parse the key from.", tokens.length > 0);
        StringBuilder ruKeyBuilder = new StringBuilder();
        if (!prefix.isEmpty()) {
            ruKeyBuilder.append(prefix);
            ruKeyBuilder.append(SEPARATOR);
        }
        ruKeyBuilder.append(tokens[0]);

        // Sort the params by their name alphabetically.
        ArrayList<ParamAndValue> paramList = new ArrayList<ParamAndValue>();
        for (int i = 1; i < tokens.length; i++) {
            String[] params = tokens[i].split("=");
            if (params.length != 2) {
                CLog.w("Invalid param found %s", tokens[i]);
                continue;
            }
            ParamAndValue tmp = new ParamAndValue(params[0].trim(), params[1].trim());
            paramList.add(tmp);
        }
        Collections.sort(paramList);

        // Append the value of the params to the benchmark, if any, to form the
        // key.
        for (ParamAndValue v : paramList) {
            ruKeyBuilder.append(SEPARATOR);
            ruKeyBuilder.append(v.mValue);
        }

        // Return the key.
        return ruKeyBuilder.toString();
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
        CLog.d("About to report %s metrics: %s", runName, metrics);
        listener.testRunStarted(runName, 0);
        listener.testRunEnded(0, metrics);
    }

}
