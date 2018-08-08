// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.RunUtil;

import com.google.android.tradefed.testtype.ClockworkTest;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ClockworkTest} that use actions to interrupt phone side and watch side to make sure
 * conectivity recover after.
 */
@OptionClass(alias = "cw-interrtupt-stress")
public class ClockworkInterruptStressTest extends ClockworkTest {

    @Option(name = "iteration", description = "number of repetitions for interrupt test")
    private int mIteration = 10;

    @Option(
        name = "between-iteration-time",
        description =
                "time period in seconds that test "
                        + "should pause between iterations; defaults to 30s"
    )
    private long mBetweenIterationTime = 30;

    @Option(
        name = "action-recover-time",
        description =
                "Time period in seconds that notification "
                        + "should be received after device action; defaults to 300s"
    )
    private int mActionRecoverTime = 300;

    @Option(name = "gms-stop-time", description = "Time period in seconds that gms is paused")
    private long mGmsPauseTime = 60;

    @Option(
        name = "target-device",
        description =
                "The device we will interrupt in test, default is WATCH, can be either PHONE, WATCH"
    )
    private String mTargetDeviceType = "WATCH";

    @Option(
        name = "target-action",
        description =
                "The action to inject into the target device"
                        + " default is null, it will interrupt the device, check ACTION_LIB for more actions"
    )
    private List<String> mTargetActions = new ArrayList<String>();

    @Option(
        name = "test-run-name",
        description = "The name of the test run, used for reporting" + " default is CwInterruptTest"
    )
    private String mTestRunName = "CwInterruptTest";

    private static final int DUMP_SYS_TIMEOUT = 60;
    private static final int AFTER_ACTION_WAIT_TIME = 60;
    private static final int IPTABLE_TIMEOUT = 60;
    private static final int OFF_SET = 100;
    private static final String TYPE = "NODE";
    private static final String SETUP = "Setup";
    private static final String REBOOT = "REBOOT";
    private static final String GMS_KILL_3 = "GMS_KILL_3";
    private static final String GMS_STOP_CONT = "GMS_STOP_CONT";
    private static final String GMS_KILL_9 = "GMS_KILL_9";
    private static final String PS_CMD = "ps | grep com.google.android.gms.persistent";
    private static final String PS_CMD_G = "ps -e | grep com.google.android.gms.persistent";
    private static final HashMap<String, String> ACTION_LIB;

    static {
        ACTION_LIB = new HashMap<String, String>();
        ACTION_LIB.put(GMS_KILL_3, "kill -3 %d");
        ACTION_LIB.put(GMS_STOP_CONT, "kill -STOP %d; sleep %d; kill -CONT %d");
        ACTION_LIB.put(GMS_KILL_9, "kill -9 %d");
        ACTION_LIB.put(REBOOT, "ls");
    }

    private ConnectivityHelper mHelper;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mHelper = new ConnectivityHelper();
        mBetweenIterationTime *= 1000;
        mTargetDeviceType = mTargetDeviceType.toUpperCase();
        String msg, cmd;
        msg = cmd = "";
        List<String> actions = new ArrayList<String>();
        if (mTargetActions.size() > 0) {
            actions = mTargetActions;
        } else {
            actions.add("REBOOT");
        }
        ITestDevice targetDevice = getDevice();
        if (mTargetDeviceType.equals(PHONE)) {
            targetDevice = getCompanion();
        } else if (mTargetDeviceType.equals(WATCH)) {
            targetDevice = getDevice();
        } else {
            Assert.fail(
                    String.format(
                            "Target device should be either PHONE or WATCH, we got %s",
                            mTargetDeviceType));
        }
        long start = System.currentTimeMillis();
        Assert.assertTrue(
                "Confirm node connection is up before the test started",
                mHelper.validateConnectionState(getDevice(), mActionRecoverTime));
        // Loop through all the actions and run the test
        for (int j = 0; j < actions.size(); j++) {
            int success = 0;
            int ipTableCheck = 0;
            String currentAction = actions.get(j);
            String testName = mTestRunName + "_" + currentAction + "_" + mTargetDeviceType;
            listener.testRunStarted(testName, mIteration);
            for (int i = 0; i < mIteration; i++) {
                TestIdentifier id =
                        new TestIdentifier(
                                getClass().getCanonicalName(), String.format("Iteration %d", i));
                listener.testStarted(id);
                CLog.i("Starting iteration %d for device %s", i, mTargetDeviceType);
                CLog.i("Confirm bluetooth connection is up before the test start");
                if (!mHelper.validateConnectionState(getDevice(), DUMP_SYS_TIMEOUT)) {
                    reportFailure(listener, i);
                    listener.testFailed(id, "Bluetooth connection is not up at start");
                    break;
                }
                // Validate initial notification
                Assert.assertTrue(
                        "Send notification from phone",
                        mHelper.sendNotification(getCompanion(), i + OFF_SET));
                if (!mHelper.validateNotificationViaDumpsys(
                        getDevice(), i + OFF_SET, DUMP_SYS_TIMEOUT)) {
                    listener.testFailed(
                            id,
                            String.format(
                                    "Not getting notification at the "
                                            + "beginning of iteration %d for %s",
                                    i, mTargetDeviceType));
                    reportFailure(listener, i);
                    break;
                }
                msg =
                        String.format(
                                "Action %s on %s side iteration %d",
                                currentAction, mTargetDeviceType, i);
                CLog.i(msg);
                logTestActions(msg);
                if (currentAction.equals(REBOOT)) {
                    targetDevice.reboot();
                    if (mTargetDeviceType.equals(WATCH)) {
                        if (mHelper.validateProxyIptable(getDevice(), "TCP", IPTABLE_TIMEOUT)
                                && mHelper.validateProxyIptable(
                                        getDevice(), "UDP", IPTABLE_TIMEOUT)) {
                            ipTableCheck++;
                        }
                    }
                } else {
                    cmd = generateCommand(targetDevice, currentAction);
                    targetDevice.executeShellCommand(cmd);
                }
                RunUtil.getDefault().sleep(AFTER_ACTION_WAIT_TIME * 1000);
                Assert.assertTrue(
                        "Send notification via phone after action",
                        mHelper.sendNotification(getCompanion(), i));
                if (!mHelper.validateNotificationViaDumpsys(getDevice(), i, mActionRecoverTime)) {
                    listener.testFailed(
                            id,
                            String.format(
                                    "Not getting notification after " + "action at %s iteration %d",
                                    mTargetDeviceType, i));
                    reportFailure(listener, i);
                    break;
                }
                success++;
                if (mBetweenIterationTime > 0) {
                    RunUtil.getDefault().sleep(mBetweenIterationTime);
                }
            }
            Map<String, String> metrics = new HashMap<String, String>();
            metrics.put("Success", Integer.toString(success));
            if (currentAction.equals(REBOOT) && mTargetDeviceType.equals(WATCH)) {
                metrics.put("iptablecheck", Integer.toString(ipTableCheck));
                CLog.d(
                        "IpTable check is done! %s ipTableCheck = %d",
                        mTargetDeviceType, ipTableCheck);
            }
            CLog.d("All done! %s success = %d", mTargetDeviceType, success);
            listener.testRunEnded(System.currentTimeMillis() - start, metrics);
        }
    }

    /**
     * Logging information to logcat
     *
     * @param device
     * @throws DeviceNotAvailableException
     * @return PID of GMS
     */
    private void logTestActions(String message) throws DeviceNotAvailableException {
        mHelper.logcatInfo(getDevice(), "CW_INTERRUPT_TEST", "i", message);
        mHelper.logcatInfo(getCompanion(), "CW_INTERRUPT_TEST", "i", message);
    }

    /**
     * Convenience method to return current GMS PID
     *
     * @param device
     * @throws DeviceNotAvailableException
     * @return PID of GMS
     */
    private int getGmsPid(ITestDevice device) throws DeviceNotAvailableException {
        String psCommand = PS_CMD;
        if (mHelper.isGoldAndAbove(device)) {
            psCommand = PS_CMD_G;
        }
        String buffer = device.executeShellCommand(psCommand);
        if (buffer.isEmpty()) {
            Assert.fail("No GMS process is found");
        }
        String[] commandItems = buffer.split("\\s+");
        return Integer.parseInt(commandItems[1]);
    }

    /**
     * Convenience method to generate the command with pid
     *
     * @param cmdKey The key of the command
     * @param device target device
     * @throws DeviceNotAvailableException
     * @return final command
     */
    private String generateCommand(ITestDevice device, String cmdKey)
            throws DeviceNotAvailableException {
        int pid = getGmsPid(device);
        String command = "";
        if (cmdKey.equals(GMS_STOP_CONT)) {
            command = String.format(ACTION_LIB.get(cmdKey), pid, mGmsPauseTime, pid);
        } else {
            command = String.format(ACTION_LIB.get(cmdKey), pid);
        }
        return command;
    }

    private void reportFailure(ITestInvocationListener listener, int iteration)
            throws DeviceNotAvailableException {
        mHelper.captureLogs(listener, iteration, mTargetDeviceType, getDevice());
        mHelper.captureLogs(listener, iteration, mTargetDeviceType, getCompanion());
    }
}
