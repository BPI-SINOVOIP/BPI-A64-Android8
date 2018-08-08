/*
 * Copyright (C) 2015 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.SimpleStats;

/**
 * Test to run Vold benchmark on given list of volumes for n iterations
 * and report the average data
 * Feature request http://b/22504533
 */
public class VoldBenchMarkTest implements IDeviceTest, IRemoteTest {

    private static final String RUN_KEY = "vold_performance_test";
    private static final String VOLD_COMMAND = "vdc volume benchmark %s";
    //661 /data/misc/vold/bench r1572:w1001:s285 1162654791 268753229 320088594 16997813
    private static final Pattern VOLD_RESULT_PATTERN = Pattern.compile(
            "(?<statuscode>\\d+)\\s+(?<path>.*)\\s+(?<operations>.*)\\s+"
                    + "(?<create>\\d+)\\s+(?<drop>\\d+)\\s+(?<run>\\d+)\\s+(?<destroy>\\d+)");
    private ITestDevice mTestDevice = null;
    private Map<String, List<String>> mVolumeResults = new HashMap<>();
    private Map<String, List<VoldOutputMetrics>> mVolumeMetrics = new HashMap<>();
    private Map<String, Map<String, String>> mVolumeFinalMetrics = new HashMap<>();

    @Option(name = "iterations", description = "Number of iterations to run"
            + "Defaulted to 100 iterations")
    private int mIterations = 100;

    @Option(name = "volumes", description = "Comma separated storage volumes to be used as "
            + "target to benchmark. Defaulted to private")
    private String mVolumes = "private";

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        List<String> volumeNames = Arrays.asList(mVolumes.split(","));
        // Run the benchmark
        for (String volumeName : volumeNames) {
            mVolumeResults.put(volumeName, runVoldBenchmark(volumeName, mIterations));
        }
        // Verify the benchmark result
        for (String volumeName : volumeNames) {
            verifyVoldMetrics(volumeName);
        }
        // Analyze the metrics
        for (String volumeName : mVolumeMetrics.keySet()) {
            analyzeVoldMetrics(volumeName);
        }
        // Add the result to the listener
        for (String volumeName : volumeNames) {
            reportMetrics(volumeName, listener);
        }
    }

    /**
     * Method the run the benchmark for given number of iterations
     * on given volume
     * @param volumeName
     * @param iterations
     * @return
     * @throws DeviceNotAvailableException
     */
    private List<String> runVoldBenchmark(String volumeName, int iterations)
            throws DeviceNotAvailableException {
        List<String> volumeResult = new ArrayList<String>();
        for (int i = 0; i < iterations; i++) {
            String output = mTestDevice.executeShellCommand(
                    String.format(VOLD_COMMAND, volumeName));
            CLog.i(output);
            String mainResult = output.split("\\n")[0].trim();
            volumeResult.add(mainResult);
        }
        return volumeResult;
    }

    /**
     * Method to verify the result returned by the benchmark
     * @param volumeName
     */
    private void verifyVoldMetrics(String volumeName) {
        Matcher match = null;
        for (String resultString : mVolumeResults.get(volumeName)) {
            //Result code 661 -> Success
            if ((match = matches(VOLD_RESULT_PATTERN, resultString)) != null &&
                    Integer.parseInt(match.group("statuscode")) == 661) {
                VoldOutputMetrics voldOutputMetrics = new VoldOutputMetrics();
                voldOutputMetrics.setCreateTime(Long.parseLong(match.group("create")));
                voldOutputMetrics.setDropKernelCacheTime(Long.parseLong(match.group("drop")));
                voldOutputMetrics.setBenchRunTime(Long.parseLong(match.group("run")));
                voldOutputMetrics.setDestroyTime(Long.parseLong(match.group("destroy")));
                if (mVolumeMetrics.containsKey(volumeName)) {
                    mVolumeMetrics.get(volumeName).add(voldOutputMetrics);
                } else {
                    List<VoldOutputMetrics> voldMetrics = new ArrayList<VoldOutputMetrics>();
                    voldMetrics.add(voldOutputMetrics);
                    mVolumeMetrics.put(volumeName, voldMetrics);
                }
            } else {
                mVolumeMetrics.remove(volumeName);
                break;
            }
        }
    }

    /**
     * Method to analyze the Vold metrics collected to get the average and standard
     * deviation
     * @param volumName
     */
    private void analyzeVoldMetrics(String volumeName) {
        Map<String, String> mVolumeFinalMetric = new HashMap<>();
        SimpleStats createStats = new SimpleStats();
        SimpleStats dropStats = new SimpleStats();
        SimpleStats runStats = new SimpleStats();
        SimpleStats destroyStats = new SimpleStats();
        for (VoldOutputMetrics voldMetrics : mVolumeMetrics.get(volumeName)) {
            createStats.add(voldMetrics.getCreateTime());
            dropStats.add(voldMetrics.getDropKernelCacheTime());
            runStats.add(voldMetrics.getBenchRunTime());
            destroyStats.add(voldMetrics.getDestroyTime());
        }
        mVolumeFinalMetric.put("create_time_avg_nsec", String.format("%.2f", createStats.mean()));
        mVolumeFinalMetric.put("dropcache_time_avg_nsec", String.format("%.2f", dropStats.mean()));
        mVolumeFinalMetric.put("run_time_avg_nsec", String.format("%.2f", runStats.mean()));
        mVolumeFinalMetric.put("run_time_std_dev_nsec", String.format("%.2f", runStats.stdev()));
        mVolumeFinalMetric.put("destroy_time_avg_nsec", String.format("%.2f",
                destroyStats.mean()));
        mVolumeFinalMetrics.put(volumeName, mVolumeFinalMetric);
    }

    /**
     * Method to prepare the listener to report the
     * metrics collected on the given volumes
     * @param volumeName
     * @param listener
     */
    private void reportMetrics(String volumeName, ITestInvocationListener listener) {
        listener.testRunStarted(String.format("%s_%s", RUN_KEY, volumeName), 0);
        if (!mVolumeFinalMetrics.containsKey(volumeName)) {
            listener.testRunFailed("Failed to collect vold benchmark metric on volume : " +
                    volumeName);
        }
        listener.testRunEnded(0, mVolumeFinalMetrics.get(volumeName));
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
     * Checks whether {@code line} matches the given {@link Pattern}.
     * @return The resulting {@link Matcher} obtained by matching the {@code line} against
     *         {@code pattern}, or null if the {@code line} does not match.
     */
    private static Matcher matches(Pattern pattern, String line) {
        Matcher ret = pattern.matcher(line);
        return ret.matches() ? ret : null;
    }

    /**
     *Class used to store the benchmark results
     */
    private class VoldOutputMetrics {
        private long createTime;
        private long dropKernelCacheTime;
        private long benchRunTime;
        private long destroyTime;

        public long getCreateTime() {
            return createTime;
        }

        public void setCreateTime(long createTime) {
            this.createTime = createTime;
        }

        public long getDropKernelCacheTime() {
            return dropKernelCacheTime;
        }

        public void setDropKernelCacheTime(long dropKernelCacheTime) {
            this.dropKernelCacheTime = dropKernelCacheTime;
        }

        public long getBenchRunTime() {
            return benchRunTime;
        }

        public void setBenchRunTime(long benchRunTime) {
            this.benchRunTime = benchRunTime;
        }

        public long getDestroyTime() {
            return destroyTime;
        }

        public void setDestroyTime(long destroyTime) {
            this.destroyTime = destroyTime;
        }
    }
}

