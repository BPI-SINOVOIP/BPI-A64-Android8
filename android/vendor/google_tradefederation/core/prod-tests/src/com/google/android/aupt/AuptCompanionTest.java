// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.aupt;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.LogcatReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IMultiDeviceTest;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.clockwork.ClockworkUtils;
import com.android.tradefed.util.DeviceConcurrentUtil;
import com.android.tradefed.util.DeviceConcurrentUtil.ShellCommandCallable;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Test harness for Automated User Profile Test on Clockwork Devices, with paired companion device
 *
 * <p>The harness supports running an arbitrary command in concurrent on companion device in
 * concurrent with the main test execution on the primary clockwork device:
 *
 * <ul>
 *   <li>command should be provided with <code>companion-command</code> parameter
 *   <li>the provided command must be a format string, and must at least support one positional
 *       string argument for the iteration count as per the usual AUPT convention
 *   <li>the provided command may contain additional positional arguments in the format string, and
 *       they should be matched by <code>additional-arg</code> parameters
 *   <li>in addition, a <code>companion-command-success-output</code> parameter can be provided to
 *       determine if command execution was successful, based on whether the provided substring is
 *       found in companion command output
 * </ul>
 */
public class AuptCompanionTest extends AuptTest implements IMultiDeviceTest {

    private static final String COMPANION_MEMINFO = "dumpsys meminfo -c | cat > /sdcard/"
            + "aupt-output/compact-companion-meminfo-%s.txt";

    private static final String ITR_PARAM = "iterations";
    @Option(name = "companion-command", description = "command to run on companion side; the "
            + "command must be a format string, with all params as strings; the first positional "
            + "argument must be the interation count; additional positional arguments can be"
            + "provided via --additional-arg <value> parameters", importance = Importance.ALWAYS)
    private String mCompanionCommand = null;

    @Option(name = "companion-command-success-output", description = "a substring that the output "
            + "of companion command should contain if it were to be deemed sucessful; using null "
            + "will cause the command always considered to be successful")
    private String mCompanionCommandSuccessOutput = null;

    @Option(name = "additional-arg", description = "additional arguments to be used in conjunction "
            + "with --companion-command, to fit into the format string as positional arguments")
    private List<String> mAdditionalArgs = new ArrayList<>();

    @Option(name = "companion-command-timeout", description = "max timeout for companion command "
            + "to finish after test on primary device is done. In unit of minutes")
    private long mCompanionCommandTimeout = 3;

    @Option(name = "companion-meminfo", description = "collect meminfo on companion")
    private boolean mCompanionMeminfo = false;

    @Option(
        name = "companion-meminfo-interval",
        description = "interval for collecting meminfo on companion in seconds"
    )
    private int mCompanionMeminfoInterval = 300;

    @Option(name = "companion-recurring-command", description = "recurring shell command "
            + "on companion")
    private String mCompanionRecurringCommand = null;

    @Option(name = "companion-recurring-interval", description = "interval between recurring "
            + "command in seconds")
    private int mCompanionRecurringInterval = 25;

    @Option(name = "keep-screen-on", description = "if the screen should keep awake, e.g. clockwork"
            + " device will not enter ambient mode")
    private boolean mKeepScreenOn = true;

    private ITestDevice mCompanion;
    private List<ITestDevice> mDeviceList = new ArrayList<>();
    private Map<ITestDevice, IBuildInfo> mInfoMap = null;

    private ScheduledExecutorService mScheduler;
    private ScheduledExecutorService mMeminfo;

    private ShellCommandCallable<Boolean> mCompanionCmd = new ShellCommandCallable<Boolean>() {
        @Override
        public Boolean processOutput(String output) {
            if (mCompanionCommandSuccessOutput != null) {
                return output.contains(mCompanionCommandSuccessOutput);
            }
            return true;
        }
    };

    private LogcatReceiver mCompanionLogcat;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        AuptDeviceLogUploader poster = null;
        if (mCompanionMeminfo) {
            // Start an AuptDeviceLogUploader for the companion device
            // if compact meminfo collection is enabled.
            String outputDir = getTestRunArgMap().get("outputLocation");
            if (outputDir == null) {
                CLog.e("Location of output on device not specified. " +
                        "Please provide a valid outputLocation parameter");
                return;
            }
            outputDir = "${EXTERNAL_STORAGE}/" + outputDir;

            CLog.i("Removing old bugreports");
            getDevice().executeShellCommand(String.format("rm %s/*.txt", outputDir));

            // process detect kill parameter, and pass it onto test harness
            String monitoredProcs = null;
            if (!mDetectKill.isEmpty()) {
                monitoredProcs = ArrayUtil.join(",", mDetectKill);
            }

            if (!mMonitoredProcs.isEmpty()) {
                monitoredProcs = ArrayUtil.join(",", mMonitoredProcs);
            }

            // Companion build info
            IBuildInfo companionBuildInfo = new BuildInfo();
            // Want companion to show on same page as Clockwork device
            // so build ID must match
            companionBuildInfo.setBuildId(getDevice().getBuildId());
            companionBuildInfo.setBuildFlavor("companion");
            // and build branch must match
            companionBuildInfo.setBuildBranch(mBuildInfo.getBuildBranch());
            // Make it clear which companion is paired with which watch
            companionBuildInfo.setDeviceSerial(
                    "companion-for-" + getDevice().getSerialNumber());
            poster =
                    new AuptDeviceLogUploader(
                            getCompanion(),
                            outputDir,
                            mBugreportInterval,
                            listener,
                            companionBuildInfo,
                            monitoredProcs,
                            mExtraLogs,
                            false);
            poster.start();
        }
        // collect dumpheaps on companion
        super.setupDumpheap(getCompanion());
        Dumpheap dh = new Dumpheap(listener, DUMPHEAP_OUTPUT, getCompanion(), "_companion");
        Thread dumpheapThread = new Thread(dh);
        dumpheapThread.setName("AuptCompanionTest#dumpheapThread");
        dumpheapThread.setDaemon(true);
        dumpheapThread.start();

        try {
            // Continue enabling reporting on primary device
            super.run(listener);

        } finally {
            if (mCompanionMeminfo) {
                poster.cancel();
            }
            dh.cancel();
            try {
                if (mCompanionMeminfo) {
                    poster.join();
                }
            } catch (InterruptedException e) {
                CLog.e("AuptDeviceLogUploader thread did not stop.");
            }
            try {
                dumpheapThread.join();
            } catch (InterruptedException e) {
                CLog.e("Dumpheap thread did not stop.");
            }
        }
    }

    @Override
    protected void runTest(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mCompanionRecurringCommand != null) {
            scheduleRecurringCommand();
        }
        if (mCompanionMeminfo) {
            scheduleCompanionMeminfo();
        }
        if (!mKeepScreenOn) {
            getDevice().executeShellCommand("svc power stayon false");
        }
        // "steal" the commonly used "--run-arg iterations NNN" and use it as
        // first positional argument
        String iterations = getTestRunArgMap().get(ITR_PARAM);
        // prepare args list to be matched against the companion command format string
        List<String> args = new ArrayList<>();
        args.add(iterations);
        args.addAll(mAdditionalArgs);
        String command = String.format(mCompanionCommand, args.toArray());
        mCompanionCmd.setCommand(command).setDevice(getCompanion());
        // start background device action for companion logcat
        mCompanionLogcat = new LogcatReceiver(getCompanion(), 25 * 1024 * 1024, 0);
        mCompanionLogcat.start();
        // spawns a fixed one thread pool since we only need one extra thread for the companion
        // command execution
        ExecutorService svc =
                Executors.newFixedThreadPool(
                        1,
                        new ThreadFactoryBuilder()
                                .setNameFormat("AuptCompanionTest-threadfactory-%s")
                                .setDaemon(true)
                                .build());
        // submits companion command for execution in a separate thread
        Future<Boolean> task = svc.submit(mCompanionCmd);
        // now call into super class to execute the test case on primary device
        try {
            super.runTest(listener);
        } finally {
            // ensure we always join on the companion side command
            try {
                // at this point main test execution has already finished on primary device, waiting
                // here for companion side to finish
                DeviceConcurrentUtil.joinFuture("companion command", task,
                        mCompanionCommandTimeout * 60 * 1000);
            } catch (TimeoutException e) {
                CLog.e("Companion command timeout.");
            } finally {
                // ensure we always shutdown the executor services
                svc.shutdown();
                stopRecurringCommand();
                stopRecurringMeminfo();
                // save companion logcat
                try (InputStreamSource src = mCompanionLogcat.getLogcatData()) {
                    listener.testLog("companion_logcat", LogDataType.TEXT, src);
                }
                mCompanionLogcat.stop();
            }
        }
    }

    @Override
    protected void onScreenshotAndBugreport(
            ITestDevice device, ITestInvocationListener listener, String prefix) {
        super.onScreenshotAndBugreport(device, listener, "cw_" + prefix);
        super.onScreenshotAndBugreport(getCompanion(), listener, "cmp_" + prefix,
                TestFailureAction.SCREENSHOT);
    }

    /**
     * Fetches the companion device allocated for the primary device
     *
     * @return the allocated companion device
     * @throws RuntimeException if no companion device has been allocated
     */
    protected ITestDevice getCompanion() {
        return mCompanion;
    }

    protected void scheduleCompanionMeminfo() throws DeviceNotAvailableException {
        getCompanion().executeShellCommand("mkdir /${EXTERNAL_STORAGE}/aupt-output");
        mMeminfo = Executors.newScheduledThreadPool(1);
        mMeminfo.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    getCompanion().executeShellCommand(templateToFilename(COMPANION_MEMINFO));
                } catch (DeviceNotAvailableException e) {
                    CLog.e("Meminfo command failed on %s (%s)", getCompanion().getSerialNumber(),
                            COMPANION_MEMINFO);
                }
            }
        }, mCompanionMeminfoInterval, mCompanionMeminfoInterval, TimeUnit.SECONDS);
    }

    protected void scheduleRecurringCommand() {
        mScheduler = Executors.newScheduledThreadPool(1);
        mScheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    getCompanion().executeShellCommand(mCompanionRecurringCommand);
                } catch (DeviceNotAvailableException e) {
                    CLog.e("Recurring command failed on %s (%s)", getCompanion().getSerialNumber(),
                            mCompanionRecurringCommand);
                }
            }
        }, mCompanionRecurringInterval, mCompanionRecurringInterval, TimeUnit.SECONDS);
    }

    protected void stopRecurringMeminfo() {
        mMeminfo.shutdownNow();
        try {
            mMeminfo.awaitTermination(mCompanionMeminfoInterval, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            CLog.e("Could not terminate recurring meminfo command on %s (%s)",
                    getCompanion().getSerialNumber(), mCompanionMeminfoInterval);
        }
    }

    protected void stopRecurringCommand() {
        mScheduler.shutdownNow();
        try {
            mScheduler.awaitTermination(mCompanionRecurringInterval, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            CLog.e("Could not terminate recurring command on %s (%s)",
                    getCompanion().getSerialNumber(), mCompanionRecurringCommand);
        }
    }

    private String templateToFilename(String filenameTemplate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
        return String.format(filenameTemplate, sdf.format(new Date()));
    }

    /** {@inheritDoc} */
    @Override
    public void setDeviceInfos(Map<ITestDevice, IBuildInfo> deviceInfos) {
        ClockworkUtils cwUtils = new ClockworkUtils();
        mCompanion = cwUtils.setUpMultiDevice(deviceInfos, mDeviceList);
        mInfoMap = deviceInfos;
    }
}
