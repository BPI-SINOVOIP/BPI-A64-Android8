// Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.BluetoothUtils;
import com.android.tradefed.util.RunUtil;

import com.google.android.tradefed.testtype.ClockworkTest;

import org.junit.Assert;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Pairing Stress test harness for BT pairing between clockwork and companion device using test hook
 */
@OptionClass(alias = "pairing-stress-test")
public class PairingStressTest extends ClockworkTest {

    private static final String WATCH = "WATCH";
    private static final String PHONE = "PHONE";
    private ConnectivityHelper mHelper;

    private ITestDevice mDevice;
    private String mDeviceBluetoothMac;
    private long mPairingTotal;
    private long mBtThroughputTotal;

    @Option(name = "iteration", description = "number of repetitions for pairing test")
    private int mIteration = 1;

    @Option(name = "network-debug", description = "enable network debugging during pairing")
    private boolean mNetworkDebug = false;

    @Option(
        name = "delay-between-iteration",
        description = "Time delay between iteration, in seconds, defaults to 10"
    )
    private long mDelayBetweenIteration = 10;

    @Option(
        name = "log-btsnoop",
        description = "Record the btsnoop trace. Works with the bluedroid stack."
    )
    private boolean mLogBtsnoop = false;

    @Option(name = "toggle-bt", description = "Toggle bluetooth after pairing started.")
    private boolean mToggleBt = false;

    @Option(name = "cloud-sync", description = "Enable cloudsync, default is disable")
    private boolean mCloudsync = false;

    @Option(name = "test-run-name", description = "Test run name for different reports")
    private String mTestRunName = "StressLoop";

    /** {@inheritDoc} */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // some initial setup
        PairingUtils mUtils = new PairingUtils();
        mHelper = new ConnectivityHelper();

        if (mLogBtsnoop) {
            CLog.d("Enable snoop log on companion");
            Assert.assertTrue(
                    "failed to enable hcisnoop log on companion",
                    BluetoothUtils.enableBtsnoopLogging(getCompanion()));
            CLog.d("Enable snoop log on clock works");
            Assert.assertTrue(
                    "failed to enable hcisnoop on clockwork",
                    BluetoothUtils.enableBtsnoopLogging(getDevice()));
        }
        mPairingTotal = 0;
        mBtThroughputTotal = 0;

        long pairingDuration = 0;
        int success = 0;
        long currentThroughput = 0;
        listener.testRunStarted(mTestRunName, mIteration);
        long start = System.currentTimeMillis();
        for (int i = 0; i < mIteration; i++) {
            TestIdentifier id =
                    new TestIdentifier(
                            getClass().getCanonicalName(), String.format("iteration%d", i));
            CLog.d("Starting pairing iteration %d", i);
            listener.testStarted(id);
            logMsg(String.format("String pairing iteration %d", i));
            try {
                pairingDuration =
                        mUtils.pair(getDevice(), getCompanion(), mCloudsync, true, mToggleBt, i);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (pairingDuration > 0) {
                logMsg(String.format("Pairing is done for iteration %d", i));
                CLog.d("clockwork pairing done, ret = %s", pairingDuration);
                // calculate total pairing time so far
                mPairingTotal += pairingDuration;
                success++;
            } else {
                listener.testFailed(id, "Pairing failed");
                reportFailure(listener, i);
            }
            listener.testEnded(id, Collections.<String, String>emptyMap());
            RunUtil.getDefault().sleep(mDelayBetweenIteration * 1000);
        }

        Map<String, String> metrics = new HashMap<String, String>();
        // prepare test results for reporting
        metrics.put("success", Integer.toString(success));
        CLog.d("all done! success = %d", success);
        if (success != 0) {
            long avg = mPairingTotal / success;
            CLog.d("clockwork pairing stats: avgtime = %d, success = %d", avg, success);
            metrics.put("avg-time", Long.toString(avg / 1000));
        }
        listener.testRunEnded(System.currentTimeMillis() - start, metrics);
        if (mLogBtsnoop) {
            BluetoothUtils.disableBtsnoopLogging(getCompanion());
            BluetoothUtils.disableBtsnoopLogging(getDevice());
        }
    }

    private void logMsg(String msg) throws DeviceNotAvailableException {
        mHelper.logcatInfo(getDevice(), "PAIRING_STRESS", "i", msg);
        mHelper.logcatInfo(getCompanion(), "PAIRING_STRESS", "i", msg);
    }

    private void reportFailure(ITestInvocationListener listener, int i)
            throws DeviceNotAvailableException {
        mHelper.captureLogs(listener, i, WATCH, getDevice());
        mHelper.captureLogs(listener, i, PHONE, getCompanion());
        if (mLogBtsnoop) {
            BluetoothUtils.uploadLogFiles(listener, getDevice(), WATCH, i);
            BluetoothUtils.uploadLogFiles(listener, getCompanion(), PHONE, i);
        }
    }
}
