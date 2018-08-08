/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.clockwork;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.util.RunUtil;

import com.google.android.tradefed.util.AttenuatorUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * A {@link ConnectivityTestRunner} that test android wear for iOS BT reconnect testing using
 * attenuator to block/unblock bluetooth signal, then verify we can done load a file from the web
 */
@OptionClass(alias = "cw-ios-bt-reconnect")
public class ClockworkiOSBtReconnect extends DeviceTestCase {

    @Option(name = "iteration", description = "Total throughput iteration number")
    private int mIteration = 5;

    @Option(name = "test-run-name", description = "Test run name for different reports")
    protected String mTestRunName = "ClockworkIosBtThroughput";

    @Option(
        name = "bluetooth-down-time",
        description =
                "time period in seconds that " + "bluetooth signal is lost; defaults to 900 secs"
    )
    private long mBtOffTime = 15 * 60;

    @Option(
        name = "bluetooth-stabilized-time",
        description =
                "wait time before the bluetooth " + "state get stabilized; defaults to 300 secs"
    )
    private int mBtWaitTime = 5 * 60;

    @Option(
        name = "between-iteration-time",
        description =
                "time period in seconds that test "
                        + "should pause between iterations; defaults to 30 sec"
    )
    private long mBetweenIterationTime = 30;

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

    @Option(
        name = "test-token",
        description = "A token to serialize test execution based on testbed limitation"
    )
    private boolean mTestToken = true;

    @Option(name = "attenuator-ip", description = "Ip address for attenuator")
    private String mAttIpAddress = "192.168.2.20";

    private static final long TEST_WAIT_TIME = 120 * 1000;
    private static final long SHORT_WAIT_TIME = 20 * 1000;

    private static final int ATT_HIGH_VALUE = 90;
    private static final int ATT_LOW_VALUE = 0;
    private static final int DOWNLOADER_TIMEOUT = 20;

    public static final Semaphore runToken = new Semaphore(1);
    private static final String NODE = "NODE";

    private ConnectivityHelper mHelper;
    private ITestDevice mDevice;

    /** {@inheritDoc} */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /** {@inheritDoc} */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * This test will run downloader test to validate the connection and use attenuator to cut the
     * connection betwen iphone and watch
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mHelper = new ConnectivityHelper();
        int success = 0;
        boolean reconnected = false;
        boolean ret = false;
        listener.testRunStarted(mTestRunName, mIteration);
        long start = 0;
        long maxReconnectTime = 0;
        long totalReconnectTime = 0;
        long avgReconnectTime = 0;
        long currentReconnectTime = 0;
        long reconnectStartTime = 0;
        long reconnectEndTime = 0;
        String connectionType = NODE;
        try {
            if (mTestToken) {
                CLog.v("Waiting to acquire run token");
                runToken.acquire();
            }
            start = System.currentTimeMillis();
            AttenuatorUtil att = new AttenuatorUtil(mAttIpAddress);
            // Attenuator reset to 0
            mHelper.logAttenuatorValue(getDevice(), ATT_LOW_VALUE, ATT_LOW_VALUE);
            att.setValue(ATT_LOW_VALUE);
            for (int i = 0; i < mIteration; i++) {
                TestIdentifier id =
                        new TestIdentifier(
                                getClass().getCanonicalName(),
                                String.format("Reconnect test iteration%d", i));
                if (!mHelper.validateConnectionState(getDevice(), mBtWaitTime)) {
                    listener.testFailed(id, "Not able to get connection before test start");
                    break;
                }
                CLog.d("Starting reconnect test iteration %d", i);
                listener.testStarted(id);
                //only run this once
                if (i == 0) {
                    CLog.i("Start pre-test check from watch");
                    if (mHelper.downloader(getDevice(), DOWNLOADER_TIMEOUT) > 0) {
                        CLog.i("Test pass before blocking bluetooth ");
                    } else {
                        listener.testFailed(id, "Test failed before blocking bluetooth");
                        break;
                    }
                }
                CLog.i(
                        "Set attenuator value from 0 to 90 to shut down bluetooth signal for, "
                                + "off-time = %ds",
                        mBtOffTime);
                mHelper.logAttenuatorValue(getDevice(), ATT_LOW_VALUE, ATT_HIGH_VALUE);
                att.progressivelySetAttValue(ATT_LOW_VALUE, ATT_HIGH_VALUE, mAttStep, mAttInterval);
                if (mBtOffTime > 0) {
                    RunUtil.getDefault().sleep(mBtOffTime * 1000);
                }
                CLog.i("Confirm bluetooth connection is down");
                if (mHelper.downloader(getDevice(), DOWNLOADER_TIMEOUT) > 0) {
                    listener.testFailed(id, "Bluetooth connection is still up");
                    break;
                }

                CLog.i(
                        "Restore attenuator value to 0 to allow bluetooth reconnect, "
                                + "wait-time = %ds",
                        mBtWaitTime);
                mHelper.logAttenuatorValue(getDevice(), ATT_HIGH_VALUE, ATT_LOW_VALUE);
                att.progressivelySetAttValue(ATT_HIGH_VALUE, ATT_LOW_VALUE, mAttStep, mAttInterval);
                if (!mHelper.validateConnectionState(getDevice(), mBtWaitTime)) {
                    listener.testFailed(id, "Not able to get connection after bluetooth unblock");
                    break;
                }
                if (mHelper.downloader(getDevice(), DOWNLOADER_TIMEOUT) > 0) {
                    CLog.i("Test pass after unblocking bluetooth");
                    success++;
                } else {
                    listener.testFailed(id, "Test failed after reconnect bluetooth");
                    break;
                }
                listener.testEnded(id, Collections.<String, String>emptyMap());
                RunUtil.getDefault().sleep(SHORT_WAIT_TIME);
            }
        } catch (InterruptedException e) {
            CLog.e(e);
            CLog.e("Interrupted error running iOS reconnect test");
        } finally {
            if (mTestToken) {
                runToken.release();
            }
        }

        // Report results
        Map<String, String> metrics = new HashMap<String, String>();
        metrics.put("Success", String.format("%d", success));
        CLog.d("success = ", success);
        listener.testRunEnded(System.currentTimeMillis() - start, metrics);
    }
}
