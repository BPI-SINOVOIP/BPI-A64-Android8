// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.BluetoothUtils;
import com.android.tradefed.util.RunUtil;
import com.google.android.tradefed.testtype.ClockworkTest;
import com.google.android.tradefed.util.AttenuatorUtil;

import org.junit.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ClockworkTest} that toggles bluetooth on companion side and verifies connection via
 * notification from companion to clockwork
 */
@OptionClass(alias = "cw-bt-reconnect")
public class ClockworkBluetoothReconnectStress extends ClockworkTest {

    @Option(name = "iteration", description = "number of repetitions for bluetooth reconnection")
    private int mIteration = 100;

    @Option(
        name = "bluetooth-down-time",
        description =
                "time period in milliseconds that "
                        + "bluetooth signal is lost; defaults to 15 mins"
    )
    private long mBtOffTime = 15 * 60;

    @Option(
        name = "bluetooth-stabilized-time",
        description =
                "wait time before the bluetooth " + "state get stabilized; defaults to 15 mins"
    )
    private int mBtWaitTime = 15 * 60;

    @Option(
        name = "notification-verification-time",
        description = "Time out for notification " + "checking; defaults to 30 s"
    )
    private int mNotificationTime = 30;

    @Option(
        name = "dumpsys-timeout",
        description = "Timeout period dumpsys notification check " + "default to 30s"
    )
    private int mDumpSysTimeout = 30;

    @Option(
        name = "between-iteration-time",
        description =
                "time period in seconds that test "
                        + "should pause between iterations; defaults to 30 sec"
    )
    private long mBetweenIterationTime = 30;

    @Option(name = "attenuator-ip", description = "Ip address for attenuator")
    private String mAttIpAddress = "192.168.1.10";

    @Option(
        name = "attenuator-step",
        description = "Step number when we progressivly change attenuator value"
    )
    private int mAttStep = 10;

    @Option(
        name = "attenuator-interval",
        description = "Interval time between attenuator value changing iterations, in MS"
    )
    private int mAttInterval = 2000;

    @Option(name = "btsnoop", description = "Record the btsnoop trace")
    private boolean mLogBtsnoop = true;

    @Option(
        name = "test-run-name",
        description =
                "The name of the test run, used for reporting"
                        + " default is ClockworkBluetoothReconnectStress"
    )
    private String mTestRunName = "ClockworkBluetoothReconnectStress";

    private static final int ATT_HIGH_VALUE = 90;
    private static final int ATT_LOW_VALUE = 0;
    private static final int OFF_SET = 100;
    private ConnectivityHelper mHelper;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mHelper = new ConnectivityHelper();
        int success = 0;
        boolean reconnected = false;
        boolean ret = false;
        listener.testRunStarted(mTestRunName, mIteration);
        long start = System.currentTimeMillis();
        long maxReconnectTime = 0;
        long totalReconnectTime = 0;
        long avgReconnectTime = 0;
        long currentReconnectTime = 0;
        long reconnectStartTime = 0;
        long reconnectEndTime = 0;

        AttenuatorUtil att = new AttenuatorUtil(mAttIpAddress);
        // Attenuator reset to 0
        logAttenuatorValue(0, 0);
        att.setValue(0);
        if (mLogBtsnoop) {
            BluetoothUtils.enableBtsnoopLogging(getDevice());
            BluetoothUtils.enableBtsnoopLogging(getCompanion());
        }
        /*
         * First, we inject notification on the phone side and checking notification
         * received on the watch side to validate the connection
         * Next, the attenuator is progressively tune down to cut off the bluetooth signal
         * to simulate walking away, and we validate the watch side has lost bluetooth
         * connection. We will keep the bluetooth signal down for 2 mins, then progressively
         * tune up the attenuator to let bluetooth signal pass through. And we check how
         * long it take for the bluetooth connection to be re-established.
         * Once bluetooth is up, we will inject notification from the phone side and validate
         * it from the watch side to confirm end to end connectivity.
         * The steps above are repeated multiple times to stress the system.
         */
        for (int i = 0; i < mIteration; i++) {
            TestIdentifier id =
                    new TestIdentifier(
                            getClass().getCanonicalName(), String.format("iteration%d", i));
            CLog.d("Starting iteration %d", i);
            listener.testStarted(id);
            // initial check up
            CLog.i("Confirm bluetooth connection is up before the test start");
            if (!mHelper.validateConnectionState(getDevice(), mNotificationTime)) {
                reportFailure(listener, i);
                listener.testFailed(id, "Bluetooth connection is not up at start");
                break;
            }
            Assert.assertTrue(
                    "Send notification before reconnection",
                    mHelper.sendNotification(getCompanion(), i + OFF_SET));
            CLog.i("Check notification before reconnection");
            if (!mHelper.validateNotificationViaDumpsys(
                    getDevice(), i + OFF_SET, mNotificationTime)) {
                reportFailure(listener, i);
                listener.testFailed(id, "Notification is not recevied at the beginning");
                break;
            }

            CLog.i(
                    "Set attenuator value from 0 to 90 to shut down bluetooth signal for, "
                            + "off-time = %ds",
                    mBtOffTime);
            logAttenuatorValue(ATT_LOW_VALUE, ATT_HIGH_VALUE);
            att.progressivelySetAttValue(ATT_LOW_VALUE, ATT_HIGH_VALUE, mAttStep, mAttInterval);
            if (mBtOffTime > 0) {
                RunUtil.getDefault().sleep(mBtOffTime * 1000);
            }
            CLog.i("Confirm bluetooth connection is down");
            if (mHelper.validateConnectionState(getDevice(), 10)) {
                reportFailure(listener, i);
                listener.testFailed(id, "Bluetooth connection is still up");
                break;
            }

            CLog.i(
                    "Restore attenuator value to 0 to allow bluetooth reconnect, "
                            + "wait-time = %ds",
                    mBtWaitTime);
            logAttenuatorValue(ATT_HIGH_VALUE, ATT_LOW_VALUE);
            att.progressivelySetAttValue(ATT_HIGH_VALUE, ATT_LOW_VALUE, mAttStep, mAttInterval);
            reconnectStartTime = System.currentTimeMillis();
            reconnected = mHelper.validateConnectionState(getDevice(), mBtWaitTime);
            reconnectEndTime = System.currentTimeMillis();
            currentReconnectTime = (reconnectEndTime - reconnectStartTime) / 1000;
            maxReconnectTime = Math.max(maxReconnectTime, currentReconnectTime);
            if (!reconnected) {
                // if something failed, grab bugreport and screenshots on both sides
                reportFailure(listener, i);
                break;
            }
            totalReconnectTime += currentReconnectTime;

            Assert.assertTrue(
                    "Send notification after bluetooth is reconnected",
                    mHelper.sendNotification(getCompanion(), i));
            ret = mHelper.validateNotificationViaDumpsys(getDevice(), i, mNotificationTime);
            if (ret) {
                success++;
            } else {
                // if something failed, grab bugreport and screenshots on both sides
                reportFailure(listener, i);
                listener.testFailed(id, "Notification at the end of the test failed");
                break;
            }
            if (mBetweenIterationTime > 0) {
                RunUtil.getDefault().sleep(mBetweenIterationTime * 1000);
            }
            Map<String, String> iteration_metrics = new HashMap<String, String>();
            iteration_metrics.put("btReconnectTime", Long.toString(currentReconnectTime));
            listener.testEnded(id, iteration_metrics);
        }
        // Attenuator reset to 0
        if (success > 0) {
            avgReconnectTime = totalReconnectTime / success;
        }
        logAttenuatorValue(0, 0);
        att.setValue(0);
        if (mLogBtsnoop) {
            captureBtSnoopLog(listener, mIteration);
            BluetoothUtils.disableBtsnoopLogging(getDevice());
            BluetoothUtils.disableBtsnoopLogging(getCompanion());
        }
        Map<String, String> metrics = new HashMap<String, String>();
        metrics.put("success", Integer.toString(success));
        metrics.put("maxTime", Long.toString(maxReconnectTime));
        metrics.put("avgTime", Long.toString(avgReconnectTime));
        CLog.d(
                "all done! success = %d, max time = %d sec, average time = %d sec",
                success, maxReconnectTime, avgReconnectTime);
        listener.testRunEnded(System.currentTimeMillis() - start, metrics);
    }

    private void captureBtSnoopLog(ITestInvocationListener listener, int interation)
            throws DeviceNotAvailableException {
        BluetoothUtils.uploadLogFiles(listener, getDevice(), "watch", interation);
        BluetoothUtils.uploadLogFiles(listener, getCompanion(), "phone", interation);
    }

    private void reportFailure(ITestInvocationListener listener, int iteration)
            throws DeviceNotAvailableException {
        mHelper.captureLogs(listener, iteration, WATCH, getDevice());
        mHelper.captureLogs(listener, iteration, PHONE, getCompanion());
        if (mLogBtsnoop) {
            captureBtSnoopLog(listener, iteration);
        }
    }

    private void logAttenuatorValue(int startValue, int endValue)
            throws DeviceNotAvailableException {
        mHelper.logcatInfo(
                getDevice(),
                "ATT",
                "i",
                String.format("Attenuator set from %d to %d", startValue, endValue));
        mHelper.logcatInfo(
                getCompanion(),
                "ATT",
                "i",
                String.format("Attenuator set from %d to %d", startValue, endValue));
    }
}
