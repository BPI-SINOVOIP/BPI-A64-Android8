// Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.clockwork.ota;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import com.google.android.clockwork.ConnectivityHelper;
import com.google.android.tradefed.testtype.ClockworkTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A {@link ClockworkTest} that check for OTA download condition */
@OptionClass(alias = "cw-ota-download")
public class ClockworkOtaDownloadTest extends ClockworkTest {

    /** The time in ms to wait for a device to become unavailable. Should usually be short */
    private static final int DEFAULT_UNAVAILABLE_TIMEOUT = 60 * 1000;

    private static final int DUMP_SYS_TIMEOUT_SEC = 5 * 60;
    private static final int CMD_TIME_OUT_SEC = 5 * 60;
    private static final String SINGLE_ITR_TEST_SUCCESS_OUTPUT = "OK (1 test)";
    private static final String FAKE_OTA_PATH = "/google/data/ro/projects/android/fake-ota";
    private static final String WATCH_TIME = "date \"+%Y-%m-%d %k:%M:%S.000\"";
    private static final String CHARGER_UNPLUG_CMD = "dumpsys battery unplug";
    private static final String CHARGER_RESET_CMD = "dumpsys battery reset";
    private static final String SIZE_SMALL = "small";
    private static final String SIZE_MEDIUM = "medium";
    private static final String SIZE_LARGE = "large";
    private static final String SIZE_FAIL = "fail";
    private static final String OTA_SECURITY = "security";
    private static final String URGENCY_RE = "recommended";
    private static final String URGENCY_MA = "mandatory";
    private static final String URGENCY_AUTO = "automatic";
    private static final String URGENCY_AUTO_TIMED = "automatic-timed";
    private static final String OTA_NOTIF_KEYWORD = "fake-ota";
    private static final String OTA_DOWNLOAD_KEYWORD = "DownloadAttempt";
    private static final String OTA_DOWNLOAD_PROGRESS = "DownloadAttempt: progress now \\d+%";
    private static final String OTA_DOWNLOAD_CANCELLED = "ota downloading is cancelled";

    private static final int TEST_ACTION_UNPLUG = 1;
    private static final int TEST_ACTION_AIRPLANE_MODE = 2;
    private static final int TEST_ACTION_REBOOT = 3;

    @Option(
        name = "ota-interruption",
        description =
                "The options for interrupting OTA download. Can be 1 for unplug, 2 for "
                        + "airplane_mode, 3 for reboot"
    )
    protected int mTestAction = TEST_ACTION_AIRPLANE_MODE;

    @Option(name = "test-run-name", description = "Test run name for different reports")
    protected String mTestRunName = "ClockworkOtaUpdate";

    private ConnectivityHelper mHelper;
    private ITestDevice mWatchDevice;
    private IBuildInfo mWatchBuildInfo;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mHelper = new ConnectivityHelper();
        mWatchDevice = getDevice();
        mWatchBuildInfo = getInfoMap().get(mWatchDevice);
        String watchSerial = getDevice().getSerialNumber();
        RunUtil.getDefault().sleep(5 * 1000);
        int success = 0;
        int bugreportCount = 0;
        listener.testRunStarted(mTestRunName, 2);
        long start = System.currentTimeMillis();
        try {
            /**
             * Check for OTA notification We will issue fake OTA command, verify downloading is in
             * progress then issue command to interrupt download. Once the interruption is gone, we
             * validate the downloading is resumed and try again with different urgency
             */
            String fakeOtaCommand;
            String size = SIZE_LARGE;
            String marker = "";
            int currentPercentage = 0;
            List<String> urgencyList = new ArrayList<>();
            urgencyList.add(URGENCY_MA);
            //urgencyList.add(URGENCY_RE);
            urgencyList.add(URGENCY_AUTO);
            for (String urgency : urgencyList) {
                TestIdentifier id =
                        new TestIdentifier(
                                getClass().getCanonicalName(), urgency + " OTA download test");
                listener.testStarted(id);
                CLog.d("Start " + urgency + " OTA download test");
                // Clean up all GService flags
                runFakeOtaCmd(watchSerial, false, "", "");
                RunUtil.getDefault().sleep(10 * 1000);
                CLog.d("Start fake ota command");
                runFakeOtaCmd(watchSerial, true, urgency, size);
                CLog.d(String.format("Checking for OTA download with urgency %s", urgency));
                marker = mWatchDevice.executeShellCommand(WATCH_TIME).trim();
                if (!mHelper.checkLogcatAfterMarker(
                        mWatchDevice, OTA_DOWNLOAD_PROGRESS, marker, OTA_DOWNLOAD_KEYWORD, 60)) {
                    listener.testFailed(id, "OTA download did not start");
                    mHelper.reportFailure(listener, bugreportCount++, getDevice(), getCompanion());
                    listener.testEnded(id, Collections.emptyMap());
                    break;
                }

                marker = mWatchDevice.executeShellCommand(WATCH_TIME).trim();
                boolean checkOtaCancelled = true;
                switch (mTestAction) {
                    case TEST_ACTION_UNPLUG:
                        CLog.d("Unplug power charger");
                        mWatchDevice.executeShellCommand(CHARGER_UNPLUG_CMD);
                        break;
                    case TEST_ACTION_AIRPLANE_MODE:
                        CLog.d("Turn on airplane mode");
                        mHelper.airplaneModeOn(mWatchDevice, true);
                        checkOtaCancelled = false;
                        break;
                    case TEST_ACTION_REBOOT:
                        CLog.d("Reboot watch");
                        mWatchDevice.reboot();
                        checkOtaCancelled = false;
                        break;
                    default:
                        CLog.d("Unsupport action", mTestRunName);
                        listener.testFailed(id, "Unsupport action");
                }
                RunUtil.getDefault().sleep(10 * 1000);
                if (checkOtaCancelled) {
                    CLog.d("Checking for OTA download cancelled");
                    if (!mHelper.checkLogcatAfterMarker(
                            mWatchDevice,
                            OTA_DOWNLOAD_CANCELLED,
                            marker,
                            OTA_DOWNLOAD_KEYWORD,
                            60)) {
                        listener.testFailed(id, "OTA download did not stop during interruption");
                        mHelper.reportFailure(
                                listener, bugreportCount++, getDevice(), getCompanion());
                    }
                }

                marker = mWatchDevice.executeShellCommand(WATCH_TIME).trim();
                switch (mTestAction) {
                    case TEST_ACTION_UNPLUG:
                        CLog.d("Reset power charger");
                        mWatchDevice.executeShellCommand(CHARGER_RESET_CMD);
                        break;
                    case TEST_ACTION_AIRPLANE_MODE:
                        CLog.d("Turn off airplane mode");
                        mHelper.airplaneModeOn(mWatchDevice, false);
                        break;
                    case TEST_ACTION_REBOOT:
                        CLog.d("Watch Reboot finished");
                        break;
                    default:
                        CLog.d("Unsupport action", mTestRunName);
                        listener.testFailed(id, "Unsupport action");
                }
                CLog.d("Checking for OTA download resumed");
                if (!mHelper.checkLogcatAfterMarker(
                        mWatchDevice, OTA_DOWNLOAD_PROGRESS, marker, OTA_DOWNLOAD_KEYWORD, 60)) {
                    listener.testFailed(id, "OTA download did not resume");
                    mHelper.reportFailure(listener, bugreportCount++, getDevice(), getCompanion());
                    listener.testEnded(id, Collections.emptyMap());
                    break;
                }
                CLog.d("Clean up OTA command");
                success++;
                runFakeOtaCmd(watchSerial, false, "", "");
                listener.testEnded(id, Collections.emptyMap());
            }
        } finally {
            Map<String, String> metrics = new HashMap<String, String>();
            metrics.put("success", Integer.toString(success));
            CLog.d("all done! success = %d", success);
            listener.testRunEnded(System.currentTimeMillis() - start, metrics);
        }
    }

    private void runFakeOtaCmd(String serial, boolean on, String urgency, String size) {
        CommandResult cr;
        long cmdTimeout = 30 * 1000;
        if (on) {
            cr =
                    RunUtil.getDefault()
                            .runTimedCmd(
                                    cmdTimeout, FAKE_OTA_PATH, "on", urgency, size, "-s", serial);
        } else {
            cr = RunUtil.getDefault().runTimedCmd(cmdTimeout, FAKE_OTA_PATH, "off", "-s", serial);
        }
        CommandStatus cs = cr.getStatus();
        if (cs != CommandStatus.SUCCESS) {
            throw new RuntimeException("Fake OTA command failed");
        }
    }
}
