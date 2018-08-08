// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.DeviceConcurrentUtil;
import com.android.tradefed.util.DeviceConcurrentUtil.ShellCommandCallable;
import com.android.tradefed.util.RunUtil;
import com.google.android.tradefed.testtype.ClockworkTest;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.junit.Assert;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link ClockworkTest} that Test android wear connection via notification from companion to
 * clockwork every 5 mins for a long period of time
 */
@OptionClass(alias = "cw-bt-throughput")
public class ClockworkBluetoothThroughput extends ClockworkTest {

    @Option(
        name = "file-size",
        description = "The total file size to transmit during the test " + "default to 10 Mbytes"
    )
    private int mFileSize = 10;

    @Option(
        name = "throughput-test-timeout",
        description =
                "Time period we wait for the test to "
                        + "finish for each 1M bytes data, default is 100 sec"
    )
    private int mTimeoutPerMB = 100;

    @Option(name = "iteration", description = "Total throughput iteration number")
    private int mIteration = 3;

    @Option(
        name = "test-run-name",
        description =
                "The name of the test run, used for reporting"
                        + " default is ClockworkBluetoothThroughputNYC"
    )
    private String mTestRunName = "ClockworkBluetoothThroughputNYC";

    private String mMacAddress = "";
    static final String SYSPROXYCTL_STOP_CMD = "sysproxyctl stop";
    static final String SYSPROXYCTL_START_CMD = "sysproxyctl start %s";
    static final String SYSPROXYCTL_PULL_CMD = "sysproxyctl pull %d";
    static final String SYSPROXYCTL_START_MSG = "sysproxy started";
    static final String SYSPROXYCTL_PING_CMD = "sysproxy ping";
    static final String LOGCAT_CMD = "logcat -d | grep sysproxy";
    static final String CLEAR_LOGCAT_CMD = "logcat -c";
    static final String GET_MACADDRESS_CMD = "settings get secure bluetooth_address";
    static final String LOGCAT_PATTERN = "Pull %d completed with an rx rate of (\\d+.\\d+) kB/s";
    static final String STOP_CMD = "stop";
    static final String START_CMD = "start";
    static final long TEST_TIMEOUT = 30 * 60;
    static final long ACTION_SLEEP_TIME = 10;
    static final long CHECK_THROUGHPUT_LOOP_TIME = 60;
    static final long COMMAND_WAIT_TIME = 30;
    private static final String WATCH = "WATCH";
    private static final String PHONE = "PHONE";
    private ConnectivityHelper mHelper;

    private ShellCommandCallable<Boolean> mSysproxyCtl =
            new ShellCommandCallable<Boolean>() {
                @Override
                public Boolean processOutput(String output) {
                    return output.contains("sysproxy started");
                }
            };

    /* This test will use sysproxyctl tool to measure bluetooth through put, using these commands
     * on watch
     *      adb root
     *      adb shell sysproxyctl stop
     *      adb shell sysproxyctl start <bdaddr of phone>
     * This should connect the sysproxy from the watch to the phone.
     * Now open a new terminal window, and you can use sysproxyctl pull command to initiate a
     * bluetooth transfer from the phone to the watch.  The usage is like this:
     *      adb shell sysproxyctl pull <size>
     * So to initiate a 1mb transfer, you can do something like so:
     *      adb shell sysproxyctl pull 1000000
     * The results will show up in logcat on the watch.
     *  1816 D sysproxy: [B] Starting pull request 0 for 100000 bytes.
     *  1816 D sysproxy: [B] Pull 0 completed with an rx rate of 93.90 kB/s
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mHelper = new ConnectivityHelper();
        listener.testRunStarted(mTestRunName, mIteration);

        mMacAddress = getCompanion().executeShellCommand(GET_MACADDRESS_CMD).trim();
        Assert.assertNotNull("no bluetooth device address detected", mMacAddress);

        CLog.i("Stopping framework on watch");
        getDevice().executeShellCommand(STOP_CMD);
        RunUtil.getDefault().sleep(ACTION_SLEEP_TIME * 1000);
        // Stop sysproxyctl before start
        CLog.i("Stopping any existing sysproxyctl");
        getDevice().executeShellCommand(SYSPROXYCTL_STOP_CMD);
        RunUtil.getDefault().sleep(ACTION_SLEEP_TIME * 1000);
        // initializes the executor service
        ExecutorService svc =
                Executors.newFixedThreadPool(1, new ThreadFactoryBuilder().setDaemon(true).build());
        long start = System.currentTimeMillis();
        double totalThroughput = 0;
        double currentThroughput = 0;
        double minThroughput = 100000;
        // The sysproxyctl server need to launch becuase it will always failed to build the
        // connection in the first try.
        getDevice().executeShellCommand(String.format(SYSPROXYCTL_START_CMD, mMacAddress));
        // big try-finally block to ensure svc is shutdown
        try {
            // Start sysproxyctl, the server command is a blocking call and can't not run in
            // background, it needs to run on its own thread.
            mSysproxyCtl
                    .setCommand(String.format(SYSPROXYCTL_START_CMD, mMacAddress))
                    .setDevice(getDevice())
                    .setTimeout(TEST_TIMEOUT * 1000);
            Future<Boolean> startSysproxyctl = svc.submit(mSysproxyCtl);
            CLog.i("Wait 30 sec before starting sysproxyctl.");
            RunUtil.getDefault().sleep(COMMAND_WAIT_TIME * 1000);
            int timeout = mFileSize * mTimeoutPerMB;
            for (int i = 0; i < mIteration; i++) {
                // Pull files from sysproxyctl
                CLog.i(String.format("Starting sysproxyctl for iteration %d", i));
                getDevice()
                        .executeShellCommand(
                                String.format(SYSPROXYCTL_PULL_CMD, mFileSize * 1000 * 1000));
                // Wait for results in logcat
                currentThroughput = getThroughput(i, timeout);
                if (currentThroughput < 0) {
                    mHelper.captureLogs(listener, i, WATCH, getDevice());
                    mHelper.captureLogs(listener, i, PHONE, getCompanion());
                    Assert.fail(String.format("Not able to get throuphput for iteration %d", i));
                    break;
                } else {
                    CLog.i("Throughput for iteration %d is %.2f", i, currentThroughput);
                    totalThroughput += currentThroughput;
                    minThroughput = Math.min(currentThroughput, minThroughput);
                }
            }
            getDevice().executeShellCommand(LOGCAT_CMD);
            CLog.i("Shut down all the threads");
            getDevice().executeShellCommand(SYSPROXYCTL_STOP_CMD);
            DeviceConcurrentUtil.joinFuture(
                    "Running SysproxyCtl", startSysproxyctl, TEST_TIMEOUT * 1000);
        } catch (TimeoutException e) {
            CLog.e("SysproxyCtl Daemon got timeout");
        } finally {
            svc.shutdown();
        }
        // Report results
        getDevice().executeShellCommand(START_CMD);
        Map<String, String> metrics = new HashMap<String, String>();
        metrics.put("throughput", String.format("%.2f", totalThroughput / mIteration));
        metrics.put("minThroughput", String.format("%.2f", minThroughput));
        metrics.put("phoneBuild", getCompanion().getBuildId());
        CLog.d("all done! avg throughput = %.2f kB/s", totalThroughput / mIteration);
        listener.testRunEnded(System.currentTimeMillis() - start, metrics);
    }

    // Parsing logcat to get throughput results
    private double getThroughput(int iteration, int timeout) throws DeviceNotAvailableException {
        String logcatBuffer = "";
        long current = System.currentTimeMillis();
        long end = current + timeout * 1000;
        String pattern = String.format(LOGCAT_PATTERN, iteration);
        Pattern r = Pattern.compile(pattern);
        double throughput = 0;
        while (current < end) {
            logcatBuffer = getDevice().executeShellCommand(LOGCAT_CMD);
            Matcher m = r.matcher(logcatBuffer);
            if (m.find()) {
                throughput = Double.parseDouble(m.group(1));
                return throughput;
            }
            RunUtil.getDefault().sleep(CHECK_THROUGHPUT_LOOP_TIME * 1000);
            current = System.currentTimeMillis();
        }
        CLog.e("Not able to find a match for throughput");
        CLog.d(logcatBuffer);
        return -1;
    }
}
