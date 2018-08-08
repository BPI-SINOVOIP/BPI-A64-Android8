// Copyright 2013 Google Inc. All Rights Reserved.
package com.google.android.performance;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.SimpleStats;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A test to measure the start up time of apps. The test will launch all the apps and reboot
 * the device several times. Report the average start up time for each app.
 */
public class AppLaunchTest implements IRemoteTest, IDeviceTest {

    private static enum LaunchOrderOptions {
        CYCLIC, SEQUENTIAL
    }

    private static enum ReportMetricOptions {
        ALL, MIN, MAX, AVG, MEDIAN, STD_DEV
    }

    private static final String PACKAGE_NAME = "com.android.tests.applaunch";
    private static final String TRACE_DEST_DIRECTORY = "trace_directory";
    private static final String TRACE_ITERATION = "trace_iterations";
    private static final String DROP_CACHE = "drop_cache";
    private static final String DROP_CACHE_SCRIPT_FILE = "dropCache";
    private static final String TRIAL_LAUNCH = "trial_launch";
    private static final String LAUNCH_ORDER = "launch_order";
    private static final String SIMPLEPERF_CMD = "simpleperf_cmd";
    private static final String LAUNCH_DIR = "launch_directory";
    private static final String DEVICE_TEMPORARY_DIR_PATH = "/data/local/tmp/";
    private static final String SCRIPT_EXTENSION = ".sh";
    private static final String LAUNCH_SUB_FOLDER = "launch_logs";
    private static final String TRACE_SUB_FOLDER = "atrace_logs";
    private static final String DROP_CACHE_CMD = "echo 3 > /proc/sys/vm/drop_caches";
    private static final String REMOVE_CMD = "rm -rf %s/%s";
    private static final String LAUNCH_SUB_DIR = "launch_logs";
    private static final String TRACE_SUB_DIR = "atrace_logs";
    private static final String FIRST_BOOT = "first-boot";
    private static final String STOP_CMD = "stop";
    private static final String RMV_PKG_XML = "rm -rf /data/system/packages.xml";
    private static final String RMV_ARM_DEX = "rm -rf /data/dalvik-cache/arm/*.dex";
    private static final String RMV_ARM64_DEX = "rm -rf /data/dalvik-cache/arm64/*.dex";

    private ITestDevice mDevice;

    @Option(name = "apps", shortName = 'a', description = "List of apps to start on the device." +
            " They key is the application to launch on the device. The value is how it will" +
            " be reported in the results.")
    private Map<String, String> mApps = new LinkedHashMap<String, String>();

    @Option(name = "package-list", description = "Application packages to launch. The package "
            + "names will be used for reporting the startup metrics.")
    private List<String> mPackageList = new ArrayList<>();

    @Option(name = "ru-key", description = "Result key to use when posting to the dashboard")
    private String mRuKey = "AppLaunchTest";

    @Option(name = "idle-time", description = "Time to sleep before starting app launches."
            + " Default is 30s")
    private long mIdleTime = 30 * 1000;

    @Option(name = "launch-iteration", description = "Iterations for launching each app.")
    private int mLaunchIteration = 10;

    @Option(name = "required-account", description = "Accounts that must exist as a prequisite.")
    private List<String> mReqAccounts = new ArrayList<String>();

    @Option(name = "run-arg",
            description = "Additional test specific arguments to provide.")
    private Map<String, String> mArgMap = new LinkedHashMap<String, String>();

    private long mTestTimeout = 10 * 60 * mLaunchIteration * 1000; // 10m timeout per iteration

    @Option(name = "trial-launch", description = "Trial launch all the apps before launching"
            + " the apps used for app launch metric calculation")
    private boolean  mTrialLaunch = false;

    @Option(name = "launch-directory", description = "Directory to store the launch log info file."
            + " In case of simpleperf command is used the output is stored in this file.")
    private String mLaunchDirectory = "/sdcard";

    @Option(name = "launch-order", description = "Cyclic order will launch the apps one after the"
            + " other for each launch iteration count. Sequential order will launch each app for"
            + " given iteration count at once before launching other apps.")
    private LaunchOrderOptions mLaunchOrder = LaunchOrderOptions.CYCLIC;

    @Option(name = "trace-directory", description = "Directory to store the trace files")
    private String mTraceDirectory = null;

    @Option(name = "trace-iteration", description = "Iterations to collect the trace data")
    private int mTraceIteration = 3;

    @Option(name = "drop-cache", description = "Drop the cache or not in between the launches")
    private boolean mDropCache = false;

    @Option(name = "simple-perf-cmd", description = "Simple perf command to use while launching"
            + " the apps")
    private String mSimplePerfCmd = null;

    @Option(name = "report-metrics", description = "Setting it to 'ALL' will calculate all the"
            + " metrics. Otherwise mention more specific metric.")
    private ReportMetricOptions mReportMetric = ReportMetricOptions.MIN;

    @Option(name = "launch-mode", description = "Setting it to first boot will do the setup"
            + " for first boot experiance.")
    private String mLaunchMode = "";

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mDropCache) {
            File scriptFile = null;
            try {
                scriptFile = FileUtil.createTempFile(DROP_CACHE_SCRIPT_FILE, SCRIPT_EXTENSION);
                FileUtil.writeToFile(DROP_CACHE_CMD, scriptFile);
                getDevice().pushFile(scriptFile, String.format("%s%s.sh",
                        DEVICE_TEMPORARY_DIR_PATH, DROP_CACHE_SCRIPT_FILE));
            } catch (IOException ioe) {
                CLog.e("Unable to create the Script file");
            }
            getDevice().executeShellCommand(String.format("chmod 755 %s%s.sh",
                    DEVICE_TEMPORARY_DIR_PATH, DROP_CACHE_SCRIPT_FILE));
            scriptFile.delete();
        }
        RunUtil.getDefault().sleep(mIdleTime);
        ProxyListener proxy = new ProxyListener(listener);
        doTestRun(proxy);
        proxy.postResults();
    }

    private void doTestRun(ITestInvocationListener listener) throws DeviceNotAvailableException {
        InstrumentationTest instr = new InstrumentationTest();
        instr.setDevice(getDevice());

        instr.setPackageName(PACKAGE_NAME);

        List<String> appsKey = new ArrayList<String>();
        for (Map.Entry<String, String> entry : mApps.entrySet()) {
            appsKey.add(entry.getKey() + "^" + entry.getValue());
        }

        // package name will be used to report the metrics if package list
        // is availble.
        for (String pkgName : mPackageList) {
            appsKey.add(pkgName + "^" + pkgName);
        }

        instr.addInstrumentationArg("apps", String.format("\"%s\"", ArrayUtil.join("|", appsKey)));
        instr.addInstrumentationArg("launch_iterations", Integer.toString(mLaunchIteration));
        if (!mReqAccounts.isEmpty()) {
            instr.addInstrumentationArg("required_accounts", ArrayUtil.join(",", mReqAccounts));
        }

        if (mLaunchDirectory.endsWith("/")) {
            mLaunchDirectory = mLaunchDirectory.substring(0, mLaunchDirectory.length() - 1);
        }
        mDevice.executeShellCommand(String.format(REMOVE_CMD, mLaunchDirectory,
                LAUNCH_SUB_DIR));
        instr.addInstrumentationArg(TRIAL_LAUNCH, Boolean.toString(mTrialLaunch));
        instr.addInstrumentationArg(LAUNCH_ORDER, mLaunchOrder.toString());
        instr.addInstrumentationArg(LAUNCH_DIR, mLaunchDirectory);

        if (null != mTraceDirectory && !mTraceDirectory.isEmpty()) {
            if (mTraceDirectory.endsWith("/")) {
                mTraceDirectory = mTraceDirectory.substring(0, mTraceDirectory.length() - 1);
            }
            mDevice.executeShellCommand(String.format(REMOVE_CMD, mTraceDirectory, TRACE_SUB_DIR));
            mTestTimeout = mTestTimeout + (15 * 60 * mTraceIteration * 1000);
            instr.addInstrumentationArg(TRACE_DEST_DIRECTORY, mTraceDirectory);
            instr.addInstrumentationArg(TRACE_ITERATION, mTraceIteration + "");
        }
        if (mDropCache) {
            instr.addInstrumentationArg(DROP_CACHE, Boolean.toString(mDropCache));
        }
        if (null != mSimplePerfCmd) {
            instr.addInstrumentationArg(SIMPLEPERF_CMD, String.format("\"%s\"", mSimplePerfCmd));
        }

        for (Map.Entry<String, String> entry : getTestRunArgMap().entrySet()) {
            instr.addInstrumentationArg(entry.getKey(), entry.getValue());
        }

        if (FIRST_BOOT.equalsIgnoreCase(mLaunchMode)) {
            simulateFirstBoot();
        }

        instr.setShellTimeout(mTestTimeout);
        instr.run(listener);
    }

    /**
     * A listener to collect the metrics from all the iterations, average them and
     * post the final result.
     */
    private class ProxyListener implements ITestInvocationListener {
        private ITestInvocationListener mListener;
        private Map<String, String> mFinalMetrics = new HashMap<String, String>();

        public ProxyListener(ITestInvocationListener listener) {
            mListener = listener;
        }

        @Override
        public void testFailed(TestIdentifier test, String trace) {
            mListener.testFailed(test, trace);
        }

        @Override
        public void testRunFailed(String errorMessage) {
            mListener.testRunFailed(errorMessage);
        }

        @Override
        public void invocationFailed(Throwable cause) {
            mListener.invocationFailed(cause);
        }

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            for (Map.Entry<String, String> metric : testMetrics.entrySet()) {
                String keyApp = metric.getKey();
                String splitLaunch[] = metric.getValue().split(",");
                SimpleStats stats = new SimpleStats();
                for (int count = 0; count < splitLaunch.length; count++) {
                    stats.add(Double.parseDouble(splitLaunch[count]));
                }
                CLog.i(String.format("%s - Launch Times - %s", keyApp,
                        metric.getValue()));
                CLog.i(String.format("%s - Min - %s", keyApp,
                        stats.min().toString()));
                CLog.i(String.format("%s - Max - %s", keyApp,
                        stats.max().toString()));
                CLog.i(String.format("%s - Avg - %s", keyApp,
                        stats.mean().toString()));
                CLog.i(String.format("%s - Median - %s", keyApp,
                        stats.median().toString()));
                CLog.i(String.format("%s - Std Dev - %s", keyApp,
                        stats.stdev().toString()));
                if (ReportMetricOptions.ALL.equals(mReportMetric)) {
                    mFinalMetrics.put(keyApp + "_min", stats.min().toString());
                    mFinalMetrics.put(keyApp + "_max", stats.max().toString());
                    mFinalMetrics.put(keyApp + "_avg", stats.mean().toString());
                    mFinalMetrics.put(keyApp + "_median", stats.median().toString());
                    mFinalMetrics.put(keyApp + "_std_dev", stats.stdev().toString());
                } else {
                    switch (mReportMetric) {
                        case MIN:
                            mFinalMetrics.put(keyApp, stats.min().toString());
                            break;
                        case MAX:
                            mFinalMetrics.put(keyApp, stats.max().toString());
                            break;
                        case AVG:
                            mFinalMetrics.put(keyApp, stats.mean().toString());
                            break;
                        case MEDIAN:
                            mFinalMetrics.put(keyApp, stats.median().toString());
                            break;
                        case STD_DEV:
                            mFinalMetrics.put(keyApp, stats.stdev().toString());
                    }
                }
            }
        }

        /**
         * Forward the final results to other listeners.
         * @throws DeviceNotAvailableException
         */
        public void postResults() throws DeviceNotAvailableException {
            mListener.testRunStarted(mRuKey, 1);
            TestIdentifier testId = new TestIdentifier(mRuKey, "AppLaunchTest");
            mListener.testStarted(testId);
            mListener.testEnded(testId, Collections.<String, String> emptyMap());
            mListener.testRunEnded(0, mFinalMetrics);
            try {
                logFiles(mListener, mLaunchDirectory, LAUNCH_SUB_FOLDER);
                mDevice.executeShellCommand(String.format(REMOVE_CMD, mLaunchDirectory,
                        LAUNCH_SUB_DIR));
                if (mTraceDirectory != null) {
                    logFiles(mListener, mTraceDirectory, TRACE_SUB_FOLDER);
                    mDevice.executeShellCommand(String.format(REMOVE_CMD, mTraceDirectory,
                            TRACE_SUB_DIR));
                }
                mDevice.executeShellCommand(String.format("rm -rf %s%s.sh",
                        DEVICE_TEMPORARY_DIR_PATH, DROP_CACHE_SCRIPT_FILE));
            } catch (IOException e) {
                CLog.e(e);
            }
        }

    }

    /**
     * Pull the files if they exist under destDirectory and log it
     * @param listener test result listener
     * @param srcDirectory source directory in the device where the  files are copied to the
     * local tmp directory
     * @throws DeviceNotAvailableException
     * @throws IOException
     */
    private void logFiles(ITestInvocationListener listener, String srcDirectory,
            String subFolderName)
            throws DeviceNotAvailableException, IOException {
        File tmpDestDir = null;
        FileInputStreamSource streamSource = null;
        File zipFile = null;
        try {
            tmpDestDir = FileUtil.createTempDir(subFolderName);
            IFileEntry srcDir = mDevice.getFileEntry(String.format("%s/%s",
                    srcDirectory, subFolderName));
            // Files are retrieved from source directory in device
            if (srcDir != null) {
                for (IFileEntry file : srcDir.getChildren(false)) {
                    File pulledFile = new File(tmpDestDir, file.getName());
                    if (!mDevice.pullFile(file.getFullPath(), pulledFile)) {
                        throw new IOException(
                                "Not able to pull the file from test device");
                    }
                }
                zipFile = ZipUtil.createZip(tmpDestDir);
                streamSource = new FileInputStreamSource(zipFile);
                listener.testLog(tmpDestDir.getName(), LogDataType.ZIP, streamSource);
            }
        } finally {
            if (tmpDestDir != null) {
                FileUtil.recursiveDelete(tmpDestDir);
                if (streamSource != null) {
                    StreamUtil.cancel(streamSource);
                }
                if (zipFile != null) {
                    zipFile.delete();
                }
            }
        }
    }

    private void simulateFirstBoot() throws DeviceNotAvailableException {
        mDevice.executeShellCommand(STOP_CMD);
        mDevice.executeShellCommand(RMV_PKG_XML);
        mDevice.executeShellCommand(RMV_ARM_DEX);
        mDevice.executeShellCommand(RMV_ARM64_DEX);
        mDevice.reboot();
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
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * @return the arguments map to pass to the UiAutomatorRunner.
     */
    public Map<String, String> getTestRunArgMap() {
        return mArgMap;
    }

    /**
     * @param runArgMap the arguments to pass to the UiAutomatorRunner.
     */
    public void setTestRunArgMap(Map<String, String> runArgMap) {
        mArgMap = runArgMap;
    }
}
