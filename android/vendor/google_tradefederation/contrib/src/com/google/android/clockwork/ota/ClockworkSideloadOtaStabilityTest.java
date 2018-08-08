/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.clockwork.ota;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.build.OtaDeviceBuildInfo;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.ota.tests.SideloadOtaStabilityTest;

import com.google.android.tradefed.testtype.ClockworkTest;
import com.google.android.clockwork.ConnectivityHelper;

import org.junit.Assert;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A test that will perform repeated flash + install OTA actions on a paired clockwork device.
 *
 * <p>adb must have root.
 *
 * <p>Expects a {@link OtaDeviceBuildInfo}.
 *
 * <p>Note: this test assumes that the {@link ITargetPreparer}s included in this test's {@link
 * IConfiguration} will flash the device back to a baseline build, and prepare the device to receive
 * the OTA to a new build.
 */
@OptionClass(alias = "cw-ota-stability")
public class ClockworkSideloadOtaStabilityTest extends ClockworkTest
        implements IBuildReceiver, IConfigurationReceiver {

    private static final String KMSG_CMD = "cat /proc/kmsg";
    private static final long SHORT_WAIT_UNCRYPT = 3 * 1000;

    private OtaDeviceBuildInfo mOtaDeviceBuild;
    private IConfiguration mConfiguration;
    private ITestDevice mDevice;

    private ITestDevice mWatchDevice;
    private IBuildInfo mWatchBuildInfo;
    private ConnectivityHelper mHelper;
    private SideloadOtaStabilityTest mSideloadOtaTest;

    @Option(
        name = "run-name",
        description = "The name of the ota stability test run. Used to report metrics."
    )
    private String mRunName = "cw-ota-stability-incremental";

    @Option(
        name = "max-connectivity-check-time",
        description = "The maximum time to wait for connecivity check in seconds."
    )
    private int mMaxConnectivityCheckTimeSec = 15 * 60;

    @Option(
        name = "package-data-path",
        description = "path on /data for the package to be saved to"
    )
    /* This is currently the only path readable by uncrypt on the userdata partition */
    private String mPackageDataPath = "/data/data/com.google.android.gsf/app_download/update.zip";

    @Option(
        name = "max-install-time",
        description = "The maximum time to wait for an ota to install in seconds."
    )
    private int mMaxInstallOnlineTimeSec = 5 * 60;

    @Option(
        name = "max-reboot-time",
        description = "The maximum time to wait for a device to reboot out of recovery if it fails"
    )
    private long mMaxRebootTimeSec = 5 * 60;

    @Option(name = "uncrypt-only", description = "if true, only uncrypt then exit")
    private boolean mUncryptOnly = false;

    /** controls if this test should be resumed. Only used if mResumeMode is enabled */
    private boolean mResumable = true;

    private long mUncryptDuration = 0;
    private LogReceiver mKmsgReceiver;

    /** {@inheritDoc} */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mOtaDeviceBuild = (OtaDeviceBuildInfo) buildInfo;
    }

    /** {@inheritDoc} */
    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    /** Set the run name */
    void setRunName(String runName) {
        mRunName = runName;
    }

    /** {@inheritDoc} */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mHelper = new ConnectivityHelper();
        mSideloadOtaTest = new SideloadOtaStabilityTest();
        mDevice = getDevice();
        mResumable = false;
        mKmsgReceiver = new LogReceiver(getDevice(), KMSG_CMD, "kmsg");
        checkFields();
        boolean testPass = false;
        testPass = connectivityCheck(listener, "start", 0);
        long startTime = System.currentTimeMillis();
        if (testPass) {
            try {
                OptionSetter setter = new OptionSetter(mSideloadOtaTest);
                setter.setOptionValue("run-name", mRunName);
                setter.setOptionValue("package-data-path", mPackageDataPath);
                setter.setOptionValue("iterations", Integer.toString(1));
                setter.setOptionValue(
                        "max-install-time", Integer.toString(mMaxInstallOnlineTimeSec));
                setter.setOptionValue("max-reboot-time", Long.toString(mMaxRebootTimeSec));
                setter.setOptionValue("uncrypt-only", Boolean.toString(mUncryptOnly));
            } catch (ConfigurationException ce) {
                String trace = "Failed to set options for SideloadOtaStabilityTest";
                listener.testRunFailed(trace);
                throw new RuntimeException(trace, ce);
            }
            mSideloadOtaTest.setBuild(mOtaDeviceBuild);
            mSideloadOtaTest.setDevice(mDevice);
            mSideloadOtaTest.setDevice(mDevice);
            mSideloadOtaTest.setConfiguration(mConfiguration);
            mSideloadOtaTest.run(listener);
            connectivityCheck(listener, "end", 1);
        }
        Map<String, String> metrics = new HashMap<>(1);
        long endTime = System.currentTimeMillis() - startTime;
        listener.testRunEnded(endTime, metrics);
    }

    private void checkFields() {
        if (mDevice == null) {
            throw new IllegalArgumentException("missing device");
        }
        if (mOtaDeviceBuild == null) {
            throw new IllegalArgumentException("missing build info");
        }
    }

    private void reportFailure(ITestInvocationListener listener, int iteration)
            throws DeviceNotAvailableException {
        mHelper.captureLogs(listener, iteration, WATCH, getDevice());
        mHelper.captureLogs(listener, iteration, PHONE, getCompanion());
    }

    private boolean connectivityCheck(
            ITestInvocationListener listener, String testStage, int iteration)
            throws DeviceNotAvailableException {
        boolean testPass = true;
        TestIdentifier test =
                new TestIdentifier(
                        getClass().getName(),
                        String.format("Verify connectivity at test %s[%s]", testStage, mRunName));
        listener.testStarted(test);
        CLog.i("Confirm bluetooth connection is up at the test " + testStage);
        if (!mHelper.validateConnectionState(getDevice(), mMaxConnectivityCheckTimeSec)) {
            reportFailure(listener, iteration);
            listener.testFailed(test, "Bluetooth connection is not up at test " + testStage);
            testPass = false;
        } else {
            CLog.i("Sending notification at the test " + testStage);
            Assert.assertTrue(
                    "Send notification from phone at " + testStage,
                    mHelper.sendNotification(getCompanion(), iteration));
            if (!mHelper.validateNotificationViaDumpsys(
                    getDevice(), iteration, mMaxConnectivityCheckTimeSec)) {
                reportFailure(listener, iteration);
                listener.testFailed(test, "Notificaition did not reach at test " + testStage);
                testPass = false;
            }
        }
        listener.testEnded(test, Collections.emptyMap());
        return testPass;
    }
}
