// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.CompanionAwareTest;
import com.android.tradefed.util.BluetoothUtils;
import com.android.tradefed.util.RunUtil;

import com.google.android.tradefed.testtype.ClockworkTest;

import org.junit.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link CompanionAwareTest} that toggles bluetooth on companion side and verifies connection via
 * notification from companion to clockwork
 */
@OptionClass(alias = "cw-bt-toggle")
public class ClockworkBluetoothToggleStress extends ClockworkTest {

    @Option(name = "iteration", description = "number of repetitions for bluetooth toggling")
    private int mIteration = 5;

    @Option(
        name = "bluetooth-off-time",
        description =
                "time period in seconds that bluetooth "
                        + "should stay in off mode when toggling; defaults to 10s"
    )
    private long mBtOffTime = 10;

    @Option(
        name = "between-iteration-time",
        description =
                "time period in seconds that test "
                        + "should pause between iterations; defaults to 30s"
    )
    private long mBetweenIterationTime = 30;

    @Option(name = "test-run-name", description = "Test run name for different reports")
    protected String mTestRunName = "BluetoothToggleLoop";

    private static final int DUMP_SYS_TIMEOUT = 30;
    private static final int CONNECTION_CHECK_TIMEOUT = 5 * 60;
    private static final int DOWNLOADER_TIMEOUT = 20;

    private ConnectivityHelper mHelper;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mHelper = new ConnectivityHelper();

        mBetweenIterationTime *= 1000;
        mBtOffTime *= 1000; // s to ms
        int success = 0;
        listener.testRunStarted(mTestRunName, mIteration);
        long start = System.currentTimeMillis();

        for (int i = 0; i < mIteration; i++) {
            TestIdentifier id =
                    new TestIdentifier(
                            getClass().getCanonicalName(), String.format("iteration%d", i));
            listener.testStarted(id);
            // initial setup
            CLog.i("Confirm bluetooth connection is up before the test start");
            if (!mHelper.validateConnectionState(getDevice(), DUMP_SYS_TIMEOUT)) {
                reportFailure(listener, i);
                listener.testFailed(id, "Bluetooth connection is not up at start");
                break;
            }
            CLog.i("Toggling BT on companion, off-time = %ds", mBtOffTime);
            // switches BT off on companion side
            Assert.assertTrue(
                    "failed to switch off bluetooth on companion",
                    BluetoothUtils.disable(getCompanion()));
            if (mBtOffTime > 0) {
                RunUtil.getDefault().sleep(mBtOffTime);
            }
            Assert.assertTrue(
                    "failed to switch on bluetooth on companion",
                    BluetoothUtils.enable(getCompanion()));

            Assert.assertTrue(
                    "Send notification from phone", mHelper.sendNotification(getCompanion(), i));
            boolean reconnected =
                    mHelper.validateConnectionState(getDevice(), CONNECTION_CHECK_TIMEOUT);
            boolean ret = mHelper.validateNotificationViaDumpsys(getDevice(), i, DUMP_SYS_TIMEOUT);
            if (ret && reconnected) {
                if (mHelper.downloader(getDevice(), DOWNLOADER_TIMEOUT) > 0) {
                    CLog.i("DownloaderTest pass after toggle bluetooth");
                } else {
                    listener.testFailed(id, "Downloader test failed after toggle bluetooth");
                    break;
                }
                success++;
            } else {
                // if something failed, grab bugreport and screenshots on both sides
                listener.testFailed(id, "Notification check failed after toggle");
                reportFailure(listener, i);
                break;
            }
            if (mBetweenIterationTime > 0) {
                RunUtil.getDefault().sleep(mBetweenIterationTime);
            }
        }
        Map<String, String> metrics = new HashMap<String, String>();
        metrics.put("success", Integer.toString(success));
        CLog.d("all done! success = %d", success);
        listener.testRunEnded(System.currentTimeMillis() - start, metrics);
    }

    private void reportFailure(ITestInvocationListener listener, int iteration)
            throws DeviceNotAvailableException {
        mHelper.captureLogs(listener, iteration, WATCH, getDevice());
        mHelper.captureLogs(listener, iteration, PHONE, getCompanion());
    }
}
