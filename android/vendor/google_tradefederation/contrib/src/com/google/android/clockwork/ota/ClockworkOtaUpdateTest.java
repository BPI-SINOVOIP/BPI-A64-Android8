// Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.clockwork.ota;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;

import com.google.android.clockwork.ConnectivityHelper;
import com.google.android.ota.FakeOtaPreparer;
import com.google.android.tradefed.testtype.ClockworkTest;
import com.google.common.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;

/** A {@link ClockworkTest} that check for Ota update and apply Ota when it is available */
@OptionClass(alias = "cw-ota-update")
public class ClockworkOtaUpdateTest extends ClockworkTest {

    @Option(
        name = "fake-ota",
        description =
                "Use fake-ota command instead of real ota to trigger "
                        + "ota notification; defaults to true"
    )
    private boolean mFakeOta = true;

    @Option(name = "test-run-name", description = "Test run name for different reports")
    protected String mTestRunName = "ClockworkOtaUpdate";

    /** The time in ms to wait for a device to become unavailable. Should usually be short */
    private static final int DEFAULT_UNAVAILABLE_TIMEOUT = 60 * 1000;

    private static final int DUMP_SYS_TIMEOUT_SEC = 5 * 60;
    private static final int CMD_TIME_OUT_SEC = 5 * 60;
    private static final String SINGLE_ITR_TEST_SUCCESS_OUTPUT = "OK (1 test)";
    private static final String OTA_FAST_TEST_INSTR =
            "am instrument -w -r -e class "
                    + "com.google.android.wear.ota.OtaFastTest "
                    + "com.google.android.wear.ota/android.support.test.runner.AndroidJUnitRunner";
    private static final String OTA_URL_SMALL =
            "http://android.clients.google.com/packages/internal/"
                    + "dummy-ota-1M.dev.8a4bfc52.zip";
    private ConnectivityHelper mHelper;

    private ITestDevice mWatchDevice;
    private IBuildInfo mWatchBuildInfo;

    /** Helper to create the device flasher. */
    @VisibleForTesting
    FakeOtaPreparer createFakeOta() {
        return new FakeOtaPreparer();
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mHelper = new ConnectivityHelper();
        mWatchDevice = getDevice();
        mWatchBuildInfo = getInfoMap().get(mWatchDevice);
        FakeOtaPreparer fakeOta = createFakeOta();

        int success = 0;
        listener.testRunStarted(mTestRunName, 0);
        long start = System.currentTimeMillis();
        TestIdentifier id = new TestIdentifier(getClass().getCanonicalName(), "OTA update test");
        listener.testStarted(id);
        // Check for OTA notification
        String keyword;
        boolean testPass = true;

        String expectedBuild = "";
        String currentBuild = getDevice().getBuildId();
        String latestBuild = "";
        if (mFakeOta) {
            try {
                OptionSetter setter = new OptionSetter(fakeOta);
                setter.setOptionValue("update-priority", "mandatory");
                setter.setOptionValue("update-url", OTA_URL_SMALL);
            } catch (ConfigurationException ce) {
                String trace = "Failed to set options for FakeOTA";
                listener.testRunFailed(trace);
                throw new RuntimeException(trace, ce);
            }
            try {
                fakeOta.setUp(mWatchDevice, mWatchBuildInfo);
            } catch (TargetSetupError | BuildError e) {
                String trace = "Exception during FAKE OTA setup command";
                listener.testRunFailed(trace);
                throw new RuntimeException(trace, e);
            }
            keyword = "fake-ota";
        } else {
            keyword = "updating OTA";
            CLog.d("Current build: %s, OTA build: %s", currentBuild, expectedBuild);
        }
        Boolean flag = true;
        while (flag) {
            //Only run the code once
            flag = false;
            CLog.d("Checking for OTA notification");
            if (!testPass
                    || !mHelper.validateNotificationKeywordViaDumpsys(
                            getDevice(), keyword, DUMP_SYS_TIMEOUT_SEC)) {
                listener.testFailed(id, "Not able to get OTA update notification at the beginning");
                reportFailure(listener);
                break;
            }
            // OTA is ready, we should start UI automation on watch
            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
            getDevice()
                    .executeShellCommand(
                            OTA_FAST_TEST_INSTR, receiver, CMD_TIME_OUT_SEC, TimeUnit.SECONDS, 0);
            String buffer = receiver.getOutput();
            CLog.d("OtaFast Test output:");
            CLog.d(buffer);
            testPass = buffer.contains(SINGLE_ITR_TEST_SUCCESS_OUTPUT);
            if (!testPass) {
                listener.testFailed(id, "UI automation failed for OTA update");
                reportFailure(listener);
                break;
            }
            //Waiting for system to boot up after OTA
            getDevice().waitForDeviceNotAvailable(DEFAULT_UNAVAILABLE_TIMEOUT);
            getDevice().waitForDeviceAvailable();
            if (mFakeOta) {
                CLog.d("Check device notification after update");
                if (mHelper.validateNotificationKeywordViaDumpsys(
                        getDevice(), "Update complete", DUMP_SYS_TIMEOUT_SEC)) {
                    success = 1;
                } else {
                    listener.testFailed(id, "No notification received after OTA finished");
                    reportFailure(listener);
                }
                fakeOta.tearDown(mWatchDevice, mWatchBuildInfo, null);
            } else {
                //Compare build to expected one
                latestBuild = getDevice().getBuildId();
                expectedBuild = mWatchBuildInfo.getBuildId();
                CLog.d(
                        "After pairing current build: %s, OTA build: %s",
                        latestBuild, expectedBuild);
                if (expectedBuild.equals(latestBuild)) {
                    success = 1;
                } else {
                    listener.testFailed(id, "OTA build did not match expected at the end");
                    reportFailure(listener);
                }
            }
        }
        listener.testEnded(id, Collections.emptyMap());
        Map<String, String> metrics = new HashMap<String, String>();
        metrics.put("previousBuild", currentBuild);
        metrics.put("latestBuild", latestBuild);
        metrics.put("expectedBuild", expectedBuild);
        metrics.put("success", Integer.toString(success));
        CLog.d("all done! success = %d", success);
        listener.testRunEnded(System.currentTimeMillis() - start, metrics);
    }

    private void reportFailure(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        mHelper.captureLogs(listener, 0, WATCH, getDevice());
        mHelper.captureLogs(listener, 0, PHONE, getCompanion());
    }
}
