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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ClockworkTest} that blocking bluetooth signal and let the watch move to wifi connection
 * then blocking and unblock wifi signal to make sure device can reconnect to wifi. At the end it
 * unblock the bluetooth signal to make sure it reconnect to bluetooth.
 */
@OptionClass(alias = "cw-wifi-reconnect")
public class ClockworkWifiReconnectStress extends ClockworkTest {

    @Option(name = "iteration", description = "number of repetitions for bluetooth reconnection")
    private int mIteration = 30;

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
        name = "wifi-down-time",
        description =
                "time period in milliseconds that "
                        + "bluetooth signal is lost; defaults to 15 mins"
    )
    private long mWifiOffTime = 15 * 60;

    @Option(
        name = "wifi-stabilized-time",
        description = "wait time before the wifi " + "state get stabilized; defaults to 15 mins"
    )
    private int mWifiWaitTime = 15 * 60;

    @Option(
        name = "notification-verification-time",
        description = "Time out for notification " + "checking; defaults to 30 s"
    )
    private int mNotificationTime = 30;

    @Option(
        name = "between-iteration-time",
        description =
                "time period in seconds that test "
                        + "should pause between iterations; defaults to 30 sec"
    )
    private long mBetweenIterationTime = 30;

    @Option(name = "bt-attenuator-ip", description = "Ip address for attenuator")
    private String mAttBtIpAddress = "192.168.2.20";

    @Option(name = "phone-wifi-attenuator-ip", description = "Ip address for phone wifi attenuator")
    private String mAttPhoneWifiIpAddress = "192.168.2.21";

    @Option(name = "watch-wifi-attenuator-ip", description = "Ip address for watch wifi attenuator")
    private String mAttWatchWifiIpAddress = "192.168.2.22";

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
    private boolean mLogBtsnoop = false;

    @Option(
        name = "test-run-name",
        description =
                "The name of the test run, used for reporting"
                        + " default is ClockworkWifiReconnectStress"
    )
    private String mTestRunName = "ClockworkWifiReconnectStress";

    static final int ATT_HIGH_VALUE = 90;
    static final int ATT_LOW_VALUE = 0;
    static final int CONNECTION_TIMEOUT = 30;
    private static final String WATCH = "WATCH";
    private static final String PHONE = "PHONE";
    private static final int OFF_SET = 100;
    private ConnectivityHelper mHelper;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mHelper = new ConnectivityHelper();
        int success = 0;
        listener.testRunStarted(mTestRunName, mIteration);
        long start = System.currentTimeMillis();
        long maxBTReconnectTime = 0;
        long maxWifiReconnectTime = 0;
        long avgBTReconnectTime = 0;
        long avgWifiReconnectTime = 0;
        long reconnectStartTime = 0;
        long reconnectEndTime = 0;
        boolean connected = false;

        AttenuatorUtil attBt = new AttenuatorUtil(mAttBtIpAddress);
        AttenuatorUtil attPhoneWifi = new AttenuatorUtil(mAttPhoneWifiIpAddress);
        AttenuatorUtil attWatchWifi = new AttenuatorUtil(mAttWatchWifiIpAddress);
        // Attenuator reset to 0
        ArrayList<AttenuatorUtil> attList = new ArrayList<AttenuatorUtil>();
        attList.add(attBt);
        attList.add(attPhoneWifi);
        attList.add(attWatchWifi);
        resetAttenuatorValue(attList, 0);
        if (mLogBtsnoop) {
            BluetoothUtils.enableBtsnoopLogging(getDevice());
            BluetoothUtils.enableBtsnoopLogging(getCompanion());
        }
        /*
         * We cut the bluetooth signal between phone and watch first then, then cut WiFi signal
         * to the phone and watch. Wait couple minutes, recover the Wifi signal check Wifi
         * connection, then cover bluetooth signal and send notification to verify end to end
         * connection after bluetooth connect is recovered.
         */
        for (int i = 0; i < mIteration; i++) {
            TestIdentifier id =
                    new TestIdentifier(
                            getClass().getCanonicalName(), String.format("iteration%d", i));
            CLog.d("Starting iteration %d", i);
            listener.testStarted(id);
            if (mBetweenIterationTime > 0) {
                RunUtil.getDefault().sleep(mBetweenIterationTime * 1000);
            }

            CLog.i("Validate BT(PROXY) is connected and wifi is disconnected at first");
            if (!verifyTwoConnectionStates(true, false, listener, i, CONNECTION_TIMEOUT)) {
                //break;
            }

            CLog.i(
                    "Set BT attenuator value from 0 to 90 to shut down bluetooth signal for, "
                            + "off-time = %ds",
                    mBtOffTime);
            logAttenuatorValue("Set bluetooth attenuator", ATT_LOW_VALUE, ATT_HIGH_VALUE);
            attBt.progressivelySetAttValue(ATT_LOW_VALUE, ATT_HIGH_VALUE, mAttStep, mAttInterval);
            if (mBtOffTime > 0) {
                RunUtil.getDefault().sleep(mBtOffTime * 1000);
            }

            CLog.i("Validate BT(PROXY) is disconnected and wifi is connected after BT cut");
            if (!verifyTwoConnectionStates(false, true, listener, i, CONNECTION_TIMEOUT)) {
                //break;
            }

            Assert.assertTrue(
                    "Send notification after bt signal is lost",
                    mHelper.sendNotification(getCompanion(), i + OFF_SET));
            Assert.assertTrue(
                    "Check notification after bt signal is lost",
                    mHelper.validateNotificationViaDumpsys(
                            getDevice(), i + OFF_SET, mNotificationTime));

            CLog.i(
                    "Set Wifi attenuators value from 0 to 90 to shut down wifi signal for, "
                            + "off-time = %ds",
                    mWifiOffTime);
            logAttenuatorValue("Set wifi attenuators", ATT_LOW_VALUE, ATT_HIGH_VALUE);
            attPhoneWifi.progressivelySetAttValue(
                    ATT_LOW_VALUE, ATT_HIGH_VALUE, mAttStep, mAttInterval);
            attWatchWifi.progressivelySetAttValue(
                    ATT_LOW_VALUE, ATT_HIGH_VALUE, mAttStep, mAttInterval);
            if (mWifiOffTime > 0) {
                RunUtil.getDefault().sleep(mWifiOffTime * 1000);
            }

            CLog.i("Validate both connection are off now");
            if (!verifyTwoConnectionStates(false, false, listener, i, CONNECTION_TIMEOUT)) {
                //break;
            }

            CLog.i(
                    "Restore Wifi attenuators value to 0 to allow Wifi reconnect, wait-time = %dms",
                    mWifiWaitTime);
            logAttenuatorValue("Recover wifi attenuators", ATT_HIGH_VALUE, ATT_LOW_VALUE);
            attPhoneWifi.progressivelySetAttValue(
                    ATT_HIGH_VALUE, ATT_LOW_VALUE, mAttStep, mAttInterval);
            attWatchWifi.progressivelySetAttValue(
                    ATT_HIGH_VALUE, ATT_LOW_VALUE, mAttStep, mAttInterval);
            reconnectStartTime = System.currentTimeMillis();
            connected = mHelper.validateConnectionState(getDevice(), mWifiWaitTime);
            reconnectEndTime = System.currentTimeMillis();
            maxWifiReconnectTime =
                    Math.max(maxWifiReconnectTime, (reconnectEndTime - reconnectStartTime) / 1000);

            if (!connected) {
                CLog.e("Wifi connection should be restored now");
                reportFailure(listener, i);
                //break;
            }
            avgWifiReconnectTime += (reconnectEndTime - reconnectStartTime) / 1000;

            CLog.i(
                    "Restore bt attenuators value to 0 to allow bluetooth reconnect, wait-time = "
                            + "%ds",
                    mBtWaitTime);
            logAttenuatorValue("Recover bluetooth attenuators", ATT_HIGH_VALUE, ATT_LOW_VALUE);
            attBt.progressivelySetAttValue(ATT_HIGH_VALUE, ATT_LOW_VALUE, mAttStep, mAttInterval);
            reconnectStartTime = System.currentTimeMillis();
            connected = mHelper.validateConnectionState(getDevice(), mBtWaitTime);
            reconnectEndTime = System.currentTimeMillis();
            maxBTReconnectTime =
                    Math.max(maxBTReconnectTime, (reconnectEndTime - reconnectStartTime) / 1000);

            if (!connected) {
                CLog.e("BT connection should be restored now");
                reportFailure(listener, i);
                //break;
            }
            avgBTReconnectTime += (reconnectEndTime - reconnectStartTime) / 1000;

            Assert.assertTrue(
                    "Send notification after bluetooth is reconnected",
                    mHelper.sendNotification(getCompanion(), i));
            boolean ret = mHelper.validateNotificationViaDumpsys(getDevice(), i, mNotificationTime);
            if (ret) {
                success++;
            } else {
                reportFailure(listener, i);
                listener.testFailed(id, "Notification at the end of the test failed");
                break;
            }
            Map<String, String> iteration_metrics = new HashMap<String, String>();
            iteration_metrics.put(
                    "btReconnectTime",
                    Long.toString((reconnectEndTime - reconnectStartTime) / 1000));
            listener.testEnded(id, iteration_metrics);
        }
        // Attenuator reset to 0
        resetAttenuatorValue(attList, 0);
        if (success > 0) {
            avgBTReconnectTime /= success;
            avgWifiReconnectTime /= success;
        }
        if (mLogBtsnoop) {
            captureBtSnoopLog(listener, mIteration);
            BluetoothUtils.disableBtsnoopLogging(getDevice());
            BluetoothUtils.disableBtsnoopLogging(getCompanion());
        }
        Map<String, String> metrics = new HashMap<String, String>();
        metrics.put("success", Integer.toString(success));
        metrics.put("maxWifiTime", Long.toString(maxWifiReconnectTime));
        metrics.put("maxBTTime", Long.toString(maxBTReconnectTime));
        metrics.put("avgWifiTime", Long.toString(avgWifiReconnectTime));
        metrics.put("avgBTTime", Long.toString(avgBTReconnectTime));
        CLog.d(
                "Done! success = %d, wifi max reconnect Time = %d, bt max reconnect time = %d "
                        + "wifi avg time = %d, bt avg time = %d",
                success,
                maxWifiReconnectTime,
                maxBTReconnectTime,
                avgWifiReconnectTime,
                avgBTReconnectTime);
        listener.testRunEnded(System.currentTimeMillis() - start, metrics);
    }

    /**
     * A function to validate two connections status with expected connection state, return true if
     * they match, return false otherwise
     *
     * @param state1: Expected connection state for the first conection, true for connected
     * @param state2: Expected connection state for the 2nd conection, true for connected
     * @param listener: ITestInvocationListener, for log capture purpose
     * @param i: Current iteration number
     * @param timeout: The timeout for how long we should wait for the expected states.
     * @throws DeviceNotAvailableException
     */
    private boolean verifyTwoConnectionStates(
            boolean state1, boolean state2, ITestInvocationListener listener, int i, int timeout)
            throws DeviceNotAvailableException {
        if (mHelper.isFeldsparAndAbove(getDevice())) {
            if (mHelper.validateConnectionState(getDevice(), timeout) ^ (state1 || state2)) {
                CLog.e("Failed to match node connection state");
                reportFailure(listener, i);
                return false;
            }
        } else {
            if ((mHelper.validateConnectionState(getDevice(), timeout) ^ state1)
                    || (mHelper.validateConnectionState(getDevice(), timeout) ^ state2)) {
                CLog.e("Failed to match expected states");
                reportFailure(listener, i);
                return false;
            }
        }
        return true;
    }

    // Setup/cleanup attenuator values
    private void resetAttenuatorValue(ArrayList<AttenuatorUtil> attList, int value)
            throws DeviceNotAvailableException {
        for (AttenuatorUtil att : attList) {
            att.setValue(value);
        }
        logAttenuatorValue("Reset attenuators", value, value);
    }

    private void captureBtSnoopLog(ITestInvocationListener listener, int interation)
            throws DeviceNotAvailableException {
        BluetoothUtils.uploadLogFiles(listener, getDevice(), "watch", interation);
        BluetoothUtils.uploadLogFiles(listener, getCompanion(), "phone", interation);
    }

    private void reportFailure(ITestInvocationListener listener, int i)
            throws DeviceNotAvailableException {
        mHelper.captureLogs(listener, i, WATCH, getDevice());
        mHelper.captureLogs(listener, i, PHONE, getCompanion());
        if (mLogBtsnoop) {
            captureBtSnoopLog(listener, i);
        }
    }

    private void logAttenuatorValue(String message, int startValue, int endValue)
            throws DeviceNotAvailableException {
        mHelper.logcatInfo(
                getDevice(),
                "ATT",
                "i",
                String.format("%s set from %d to %d", message, startValue, endValue));
        mHelper.logcatInfo(
                getCompanion(),
                "ATT",
                "i",
                String.format("%s set from %d to %d", message, startValue, endValue));
    }
}
