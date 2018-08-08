// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import org.junit.Assert;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A test that toggles airplanemode on watch side and verifies cellular data/ Wifi connection
 * connection via ping command to make sure the internet connection recovered after toggle
 */
@OptionClass(alias = "cw-cellular-toggle")
public class ClockworkAirplaneToggleStress extends ClockworkConnectivityTest
        implements IDeviceTest, IRemoteTest {

    private static final int PING_TIMEOUT = 60;
    private static final int PING_INTERVAL = 10;
    private static final String AIRPLANEMODE_ON = "settings put global airplane_mode_on 1";
    private static final String AIRPLANEMODE_OFF = "settings put global airplane_mode_on 0";
    private static final String AIRPLANEMODE_BROADRCAST =
            "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true";
    private static final String PING_CMD = "ping -c 5 www.google.com";
    private static final String PING_RETURN = "5 packets transmitted, 5 received, 0% packet loss";
    private static final String DIAG_CMD = "diag_mdlog -f /sdcard/radio.cfg -p -c &";
    private static final String STOP_DIAG_CMD = "diag_mdlog -k";
    private static final String FOLDER_NAME = "/sdcard/diag_logs";
    private static final String DELETE_FOLDER = "rm -rf /sdcard/diag_logs";
    ITestDevice mTestDevice = null;

    @Option(name = "iteration", description = "number of repetitions for cellular toggling")
    private int mIteration = 10;

    @Option(name = "cellular-timeout", description = "The timeout waiting for cellular to recover")
    private long mCellTimeout = 60;

    @Option(
        name = "between-iteration-time",
        description =
                "time period in seconds that test "
                        + "should pause between iterations; defaults to 30s"
    )
    private long mBetweenIterationTime = 30;

    @Option(name = "test-run-name", description = "Test run name for different reports")
    protected String mTestRunName = "CwCellularToggleLoop";

    @Option(name = "qxdmlogger", description = "Turn on QXDMLogger")
    protected boolean mQXDMLogger = false;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        mBetweenIterationTime *= 1000;
        int success = 0;
        listener.testRunStarted(mTestRunName, mIteration);
        long start = System.currentTimeMillis();
        boolean testPass = false;
        // Turn on QXDMLogger before the test started
        if (mQXDMLogger) {
            mTestDevice.executeShellCommand(DIAG_CMD);
        }
        for (int i = 0; i < mIteration; i++) {
            TestIdentifier id =
                    new TestIdentifier(
                            getClass().getCanonicalName(), String.format("iteration%d", i));
            listener.testStarted(id);
            CLog.d("Starting iteration %d", i);
            // initial check
            testPass = pingTest(mTestDevice, mCellTimeout, PING_INTERVAL, true);
            CLog.d("Starting test 1");
            if (!testPass) {
                listener.testFailed(id, "Ping test failed before toggle airplane mode");
                break;
            }
            CLog.i("Toggling Airplan mode on");
            // switches Cellular off with airplane mode on
            airplaneModeOn(true);
            CLog.i("Ping test should fail during airplane mode");
            testPass = pingTest(mTestDevice, PING_TIMEOUT, PING_INTERVAL, false);
            if (!testPass) {
                listener.testFailed(id, "Ping test is unexpectedly pass under airplane mode!");
                airplaneModeOn(false);
                break;
            }
            CLog.i("Toggling Airplan mode off");
            airplaneModeOn(false);
            testPass = pingTest(mTestDevice, mCellTimeout, PING_INTERVAL, true);
            if (!testPass) {
                listener.testFailed(id, "Ping test failed after toggle airplane mode");
                break;
            }
            success++;
            if (mBetweenIterationTime > 0) {
                RunUtil.getDefault().sleep(mBetweenIterationTime);
            }
        }
        if (mQXDMLogger) {
            mTestDevice.executeShellCommand(STOP_DIAG_CMD);
        }
        if (mQXDMLogger) {
            uploadLogFiles(listener, FOLDER_NAME);
        }
        Map<String, String> metrics = new HashMap<>();
        metrics.put("success", Integer.toString(success));
        CLog.d("all done! success = %d out of %d", success, mIteration);
        listener.testRunEnded(System.currentTimeMillis() - start, metrics);
    }

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * Ping google.com, if reachable, return true
     *
     * @param device
     * @param timeout
     * @param interval ping command interval
     */
    private boolean pingTest(ITestDevice device, long timeout, long interval, boolean expectPass) {
        final CountDownLatch done = new CountDownLatch(1);
        try {
            new Thread(
                            () -> {
                                String buffer = null;
                                boolean result = !expectPass;
                                while (result != expectPass) {
                                    try {
                                        buffer = device.executeShellCommand(PING_CMD);
                                    } catch (DeviceNotAvailableException e) {
                                        CLog.e(e);
                                    }
                                    if (buffer != null && buffer.contains(PING_RETURN)) {
                                        result = true;
                                    } else {
                                        result = false;
                                    }
                                    RunUtil.getDefault().sleep(interval * 1000);
                                    CLog.d("ping is %b, expect %b", result, expectPass);
                                }
                                done.countDown();
                            })
                    .start();
            return done.await(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            CLog.e(e);
            return false;
        }
    }

    /**
     * Turn on/off airplane mode
     *
     * @param on
     */
    private void airplaneModeOn(boolean on) throws DeviceNotAvailableException {
        if (on) {
            mTestDevice.executeShellCommand(AIRPLANEMODE_ON);
        } else {
            mTestDevice.executeShellCommand(AIRPLANEMODE_OFF);
        }
        mTestDevice.executeShellCommand(AIRPLANEMODE_BROADRCAST);
    }

    /**
     * uploadLogFiles, upload all the files under the provided folder
     *
     * @param listener
     */
    private void uploadLogFiles(ITestInvocationListener listener, String foldername)
            throws DeviceNotAvailableException {
        CLog.d(String.format("Upload log files under %s", foldername));
        File folder = new File(foldername);
        if (!folder.exists()) {
            CLog.e("%s Folder does not exist", foldername);
            return;
        }
        String directoryName;
        InputStreamSource inputSource = null;
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isFile()) {
                try {
                    inputSource = new FileInputStreamSource(fileEntry);
                    listener.testLog(fileEntry.getPath(), LogDataType.UNKNOWN, inputSource);
                } finally {
                    StreamUtil.cancel(inputSource);
                }
            } else if (fileEntry.isDirectory()) {
                directoryName = fileEntry.getAbsolutePath();
                uploadLogFiles(listener, directoryName);
            }
        }
        mTestDevice.executeShellCommand(DELETE_FOLDER);
    }
}
