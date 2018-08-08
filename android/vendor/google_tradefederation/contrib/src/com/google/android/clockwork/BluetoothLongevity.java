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

import org.junit.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ClockworkTest} that create notification on companion side and verifies connection via
 * notification from companion to clockwork every 5 mins for a long period of time
 */
@OptionClass(alias = "cw-bt-longevity")
public class BluetoothLongevity extends ClockworkTest {

    private static final long SLEEP_TIME = 5;

    @Option(
        name = "test-period",
        description = "Time period for how long the test should run " + "default to 1200s"
    )
    private long mTestPeriod = 20 * 60;

    @Option(
        name = "iteration",
        description =
                "Total number of notification we send, defaults 48," + "it should be less than 49"
    )
    private int mIteration = 48;

    @Option(name = "btsnoop", description = "Record the btsnoop trace")
    private boolean mLogBtsnoop = false;

    @Option(name = "usb-switch-type", description = "The the type of USB switching, default is ncd")
    private String mUsbSwitchType = "ncd";

    @Option(
        name = "usb-switch-port-id",
        description = "The port number on ncd connection, default -1"
    )
    private int mNcdId = -1;

    @Option(
        name = "test-run-name",
        description =
                "The name of the test run, used for reporting" + " default is BluetoothLongevity"
    )
    private String mTestRunName = "BluetoothLongevity";

    private static final int DUMP_SYS_TIMEOUT = 5;
    private static final int INITIAL_TIMEOUT = 300;
    private static final String CLEAR_NOTIFICATION = "service call notification 1";
    private static final String CONNECT = "CONNECT";
    private static final String DISCONNECT = "DISCONNECT";

    private ConnectivityHelper mHelper;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mHelper = new ConnectivityHelper();
        int betweenIterationTime = (int) mTestPeriod / mIteration;
        int betweenIterationMins = betweenIterationTime / 60;
        int success = 0;
        long start = System.currentTimeMillis();
        long testPeriod_MS = mTestPeriod * 1000 + start;
        listener.testRunStarted(mTestRunName, mIteration);
        long current = 0;
        long nextRunStartTime = 0;
        int currentIteration = 0;
        boolean hasTestFailed = false;
        boolean ret = false;
        // Clear all the existing notifications
        getDevice().executeShellCommand(CLEAR_NOTIFICATION);
        if (mLogBtsnoop) {
            BluetoothUtils.enableBtsnoopLogging(getDevice());
            BluetoothUtils.enableBtsnoopLogging(getCompanion());
        }
        Assert.assertTrue(
                "Confirm node connection from companion is up before the test started",
                mHelper.validatePhoneConnectionState(getCompanion(), INITIAL_TIMEOUT));
        // Cutting USB connection if there is a Ncd ID option set
        if (mNcdId >= 0) {
            Assert.assertTrue(mHelper.ncdAction(DISCONNECT, mNcdId));
            RunUtil.getDefault().sleep(60 * 1000);
        }
        while (current < testPeriod_MS && currentIteration < mIteration && !hasTestFailed) {
            current = System.currentTimeMillis();
            TestIdentifier id =
                    new TestIdentifier(
                            getClass().getCanonicalName(),
                            String.format("iteration%d", currentIteration));
            listener.testStarted(id);
            // Sleep for 15 mins
            nextRunStartTime = start + currentIteration * betweenIterationTime * 1000;
            while (current < nextRunStartTime) {
                RunUtil.getDefault().sleep(SLEEP_TIME * 1000);
                current = System.currentTimeMillis();
            }
            CLog.d("Send notification from phone for iteration %d", currentIteration);
            Assert.assertTrue(
                    "Send notification from phone",
                    mHelper.sendNotification(getCompanion(), currentIteration, currentIteration));
            if (mHelper.validatePhoneConnectionState(getCompanion(), DUMP_SYS_TIMEOUT)) {
                success++;
                Map<String, String> testMetrics = new HashMap<String, String>();
                listener.testEnded(id, testMetrics);
            } else {
                // if something failed, grab bugreport and screenshots on both sides
                listener.testFailed(id, "Connectivity check failed");
                hasTestFailed = true;
            }
            currentIteration++;
        }
        // Reconnect USB connection if there is a Ncd ID option set
        if (mNcdId >= 0) {
            Assert.assertTrue(mHelper.ncdAction(CONNECT, mNcdId));
            getDevice().waitForDeviceAvailable();
        }
        currentIteration = 0;
        CLog.d("Check all the notification at the end to make sure we have received all of them");
        while (currentIteration < mIteration) {
            TestIdentifier id =
                    new TestIdentifier(
                            getClass().getCanonicalName(),
                            String.format("verification%d", currentIteration));
            listener.testStarted(id);
            if (!mHelper.validateNotificationViaDumpsys(
                    getDevice(), currentIteration, DUMP_SYS_TIMEOUT)) {
                CLog.d("Not able to get notification %d", currentIteration);
                listener.testFailed(
                        id, String.format("Not able to get notification %d", currentIteration));
                mHelper.captureLogs(listener, currentIteration, "Watch", getDevice());
                mHelper.captureLogs(listener, currentIteration, "Phone", getCompanion());
                break;
            }
            Map<String, String> testMetrics = new HashMap<String, String>();
            listener.testEnded(id, testMetrics);
            currentIteration++;
        }
        if (mLogBtsnoop) {
            captureBtSnoopLog(listener, currentIteration);
            BluetoothUtils.disableBtsnoopLogging(getDevice());
            BluetoothUtils.disableBtsnoopLogging(getCompanion());
        }
        Map<String, String> metrics = new HashMap<String, String>();
        metrics.put("success", Integer.toString(currentIteration * betweenIterationMins));
        metrics.put(
                "reconnect_count",
                Integer.toString(mHelper.wearableTransportReconnectCount(getDevice())));
        metrics.put(
                "bluetooth_disconnect",
                Integer.toString(mHelper.bluetoothDisconnectCount(getDevice())));
        CLog.d("all done! success = %d", currentIteration * betweenIterationMins);
        listener.testRunEnded(System.currentTimeMillis() - start, metrics);
    }

    private void captureBtSnoopLog(ITestInvocationListener listener, int interation)
            throws DeviceNotAvailableException {
        BluetoothUtils.uploadLogFiles(listener, getDevice(), "watch", interation);
        BluetoothUtils.uploadLogFiles(listener, getCompanion(), "phone", interation);
    }
}
