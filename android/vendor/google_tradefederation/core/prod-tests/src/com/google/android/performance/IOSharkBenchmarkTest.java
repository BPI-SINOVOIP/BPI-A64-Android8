/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.performance;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.SimpleStats;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Test to run IOShark benchmark to evaluate kernel storage/filesystems and report the data
 * Feature request <a href="http://b/34111692">http://b/34111692</a>
 */
public class IOSharkBenchmarkTest implements IDeviceTest, IRemoteTest {

    private ITestDevice mTestDevice = null;

    private static final String TMP_LOCATION_DEVICE = "/data/local/tmp/";
    private static final String WL_LOCATION_DEVICE = TMP_LOCATION_DEVICE + "wl/";
    // -s options decreases the verbosity of the command and returns the metric as space separated
    // string of numbers
    private static final String IOSHARK_PATH = "ioshark_bench";
    private static final String IOSHARK_OPTIONS = "-s";

    // the workload files have format = 1234.wl
    private static final String WL_FILE_PATTERN = "*wl";

    private static final String DELIMITER = "\\s+";

    // Metrics collected should be of this format
    // <creation time> <remove time> <test time> <read bytes> <write bytes> <CPU util>
    // <user CPU util> <system CPU util> <disk util>
    private static final int NUM_METRICS = 9;

    private static final String AVG_SUFFIX = "_avg";
    private static final String MIN_SUFFIX = "_min";
    private static final String MAX_SUFFIX = "_max";
    private static final String STDDEV_SUFFIX = "_std_dev";

    private static final String RUN_KEY = "runshark_benchmark";
    private static CollectingOutputReceiver mReceiver = new CollectingOutputReceiver();

    private static List<Map<String, String>> mReports = new ArrayList<>();

    private static enum IOSharkMetrics {
        CREATE_TIME("create_time"),
        REMOVE_TIME("remove_time"),
        TEST_TIME("test_time"),
        READ_BYTES("read_bytes"),
        WRITE_BYTES("write_bytes"),
        CPU_UTIL("cpu_util"),
        USER_CPU_UTIL("user_cpu_util"),
        SYSTEM_CPU_UTIL("system_cpu_util"),
        DISK_UTIL("disk_util");

        private final String mKey;

        IOSharkMetrics(String value) {
            mKey = value;
        }

        @Override
        public String toString() {
            return mKey;
        }
    }

    private static EnumMap<IOSharkMetrics, SimpleStats> mAggregateIOSharkMetrics = new EnumMap<>(
            IOSharkMetrics.class);

    @Option(name = "ioshark-execution-retries",
            description = "Maximum number of retries to be made in case the tool fails.")
    private int mNumRetries = 1;

    @Option(name = "ioshark-execution-timeout",
            description = "Maximum time in minutes to wait for ioshark tool to complete.",
            isTimeVal = true)
    private long mTimeoutMs = TimeUnit.MINUTES.toMillis(50); // in milliseconds

    @Option(name = "ioshark-iterations", description = "Number of iterations to run")
    private int mIterations = 1;

    @Option(name = "ioshark-path",
            description = "path in the device to locate ioshark_bench executable")
    private String mIOSharkPath = IOSHARK_PATH;

    @Option(name = "wl-file-pattern", description = "General pattern of wl filenames")
    private String mWlFilePattern = WL_FILE_PATTERN;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        listener.testRunStarted(RUN_KEY, 0);
        while (--mIterations >= 0) {
            // flush any output collected from previous runs
            mReceiver = new CollectingOutputReceiver();
            runBenchmark();
            if (verifyMetrics()) {
                parseMetrics();
            } else {
                CLog.e("Unable to verify metrics [output of ioshark tool: %s ]",
                        mReceiver.getOutput());
            }
        }
        aggregateMetrics();
        listener.testRunEnded(0, reportMetrics());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * <p>
     * Copies the trace files and runs the ioshark_benchmark using the trace files
     * Command is: {@code cd /data/local/tmp && ioshark_bench -s wl/trace*wl}
     * </p>
     */
    protected void runBenchmark() throws DeviceNotAvailableException {
        // cd to WL_LOCATION_DEVICE since ioshark creates tmp files.
        getDevice().executeShellCommand(
                String.format("cd %s && %s %s", WL_LOCATION_DEVICE, mIOSharkPath + " " +
                IOSHARK_OPTIONS, mWlFilePattern), mReceiver, mTimeoutMs, TimeUnit.MILLISECONDS,
                mNumRetries);
    }

    /**
     * <p>
     * Verify that:
     * 1. The ioshark_bench was able to run completely
     * 2. Required number of metrics has been generated
     * </p>
     *
     * @return true, if {@code NUM_METRICS} outputs received from IOShark, false, otherwise
     */
    protected boolean verifyMetrics() {
        if (mReceiver.getOutput() == null
                || mReceiver.getOutput().split(DELIMITER).length != NUM_METRICS) {
            return false;
        }
        return true;
    }

    /**
     * <p>
     * Aggregate from different runs to get min, max, mean and standard deviation of metrics during
     * reporting
     * </p>
     */
    protected void aggregateMetrics() {
        for (Map<String, String> report : mReports) {
            for (String key : report.keySet()) {
                SimpleStats simpleStats = mAggregateIOSharkMetrics
                        .getOrDefault(IOSharkMetrics.valueOf(key.toUpperCase()), new SimpleStats());
                simpleStats.add(Double.valueOf(report.get(key)));
                mAggregateIOSharkMetrics.put(IOSharkMetrics.valueOf(key.toUpperCase()),
                        simpleStats);
            }
        }
    }

    /**
     * Calculate the statistics of the data from ioshark_bench
     *
     * @return finalMetrics containing min, max, mean and stddev of the data from
     * {@code mNumRetries} execution of ioshark_bench
     */
    protected Map<String, String> reportMetrics() {
        Map<String, String> finalMetrics = new HashMap<>();

        for (IOSharkMetrics key : mAggregateIOSharkMetrics.keySet()) {
            SimpleStats simpleStats = mAggregateIOSharkMetrics.get(key);
            finalMetrics.put(key.toString() + MIN_SUFFIX,
                    String.format("%.2f", simpleStats.min()));
            finalMetrics.put(key.toString() + MAX_SUFFIX,
                    String.format("%.2f", simpleStats.max()));
            finalMetrics.put(key.toString() + AVG_SUFFIX,
                    String.format("%.2f", simpleStats.mean()));
            finalMetrics.put(key.toString() + STDDEV_SUFFIX,
                    String.format("%.2f", simpleStats.stdev()));
        }

        return finalMetrics;

    }

    /**
     * Parse the ioshark result and convert it to:
     * [{
     * "create_time":"...",
     * "remove_time":"...",
     * "test_time":"...",
     * "read_bytes":"...",
     * "write_bytes":"...",
     * "cpu_util":"...",
     * "user_cpu_util":"...",
     * "system_spu_util":"...",
     * "disk_util":"..."
     * }]
     * <p>
     * Besides, set the run key which acts as an unique identifier for this perf test and also set
     * the suffix for each iteration
     * </p>
     *
     * @return List of metrics as key,value pair where key describes what the value corresponds to
     */
    protected void parseMetrics() {
        String[] results = mReceiver.getOutput().trim().split(DELIMITER);

        mReports.add(parseMetrics(results));

    }

    private Map<String, String> parseMetrics(String[] results) {
        Map<String, String> report = new HashMap<>();
        EnumSet<IOSharkMetrics> metrics = EnumSet.of(IOSharkMetrics.CREATE_TIME,
                IOSharkMetrics.REMOVE_TIME, IOSharkMetrics.TEST_TIME, IOSharkMetrics.READ_BYTES,
                IOSharkMetrics.WRITE_BYTES, IOSharkMetrics.CPU_UTIL, IOSharkMetrics.USER_CPU_UTIL,
                IOSharkMetrics.SYSTEM_CPU_UTIL, IOSharkMetrics.DISK_UTIL);

        for (Enum<IOSharkMetrics> metric : metrics) {
            report.put(metric.toString(), results[metric.ordinal()]);
            CLog.i(metric.toString() + " = " + results[metric.ordinal()]);
        }

        return report;
    }

}
