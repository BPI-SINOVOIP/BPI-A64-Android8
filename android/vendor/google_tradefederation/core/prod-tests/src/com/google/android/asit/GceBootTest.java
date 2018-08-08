// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.asit;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import com.google.android.tradefed.device.GceAvdTestDeviceOptions;
import com.google.android.tradefed.device.GceManager;
import com.google.android.tradefed.targetprep.GceAvdPreparer;
import com.google.android.tradefed.util.GceAvdInfo;
import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Starts an AVD GCE instance as part of the test, and reports status as test results */
@OptionClass(alias = "gce-boot-test")
public class GceBootTest extends BaseBootTest {

    protected static final String BUGREPORT_NAME = "bugreport_gce_end_invoc";
    protected static final String LOGCAT_NAME = "logcat_gce_dump";

    @Option(name = "gce-driver-path", description = "path of the binary to launch GCE devices")
    private String mAvdDriverBinary;

    @Option(name = "gce-driver-config-path",
            description = "path of the config to use to launch GCE devices.")
    private String mAvdConfigFile;

    @Option(name = "gce-available-time", description = "max time to wait for GCE to become "
            + "available after startup.", isTimeVal = true)
    private long mGceAvailableTime = 2 * 60 * 1000;

    @Option(name = "skip-teardown-on-failure", description = "if the GCE instance should be kept "
            + "for investigation in case of a boot failure")
    private boolean mShouldSkipTearDown = false;

    @Option(
        name = "gce-private-key-path",
        description = "path to the ssh key private key location."
    )
    private File mSshPrivateKeyPath = null;

    private GceAvdPreparer mPreparer;
    private GceAvdInfo mGceAvd;

    /** Helper to create the preparer. */
    @VisibleForTesting
    GceAvdPreparer createAvdPreparer() {
        return new GceAvdPreparer();
    }

    /** {@inheritDoc} */
    @Override
    public long bringUp(ITestInvocationListener listener, Map<String, String> result)
            throws DeviceNotAvailableException {
        long bootStart = INVALID_TIME_DURATION;
        long onlineTime = INVALID_TIME_DURATION;
        mGceAvd = null;
        mPreparer = createAvdPreparer();
        mPreparer.setTestLogger(listener);
        try {
            // avoid entering the block if there are no options to inject, since initializing
            // OptionSetter involves lots of reflection
            if (mAvdDriverBinary != null || mAvdConfigFile != null || mSshPrivateKeyPath != null) {
                try {
                    OptionSetter setter = new OptionSetter(mPreparer);
                    // duping and passing parameters down to preparer
                    if (mAvdDriverBinary != null) {
                        setter.setOptionValue("gce-driver-path", mAvdDriverBinary);
                    }
                    if (mAvdConfigFile != null) {
                        setter.setOptionValue("gce-driver-config-path", mAvdConfigFile);
                    }
                    if (mSshPrivateKeyPath != null) {
                        setter.setOptionValue(
                                "gce-private-key-path", mSshPrivateKeyPath.getAbsolutePath());
                    }
                } catch (ConfigurationException ce) {
                    // this really shouldn't happen, but if it does, it'll indicate a setup problem
                    // so this should be exposed, even at the expense of categorizing the build as
                    // having a critical failure
                    listener.testRunFailed(BOOT_TEST);
                    throw new RuntimeException("failed to set options for GceAvdPreparer", ce);
                }
            }
            // launch GCE
            try {
                mPreparer.setUp(getDevice(), getBuildInfo());
                mGceAvd = mPreparer.getGceAvdInfo();
                bootStart = System.currentTimeMillis();
            } catch (DeviceNotAvailableException|TargetSetupError|BuildError e) {
                // note: for GCE, these may happen due to infrastructure flakiness, not reporting as
                // fatal failure
                CLog.e("Exception during AVD GCE startup");
                CLog.e(e);

                // Throw exception to fail invocation and skip tear down.
                mFirstBootSuccess = false;
                mGceAvd = mPreparer.getGceAvdInfo();

                if (mGceAvd != null && GceAvdInfo.GceStatus.BOOT_FAIL.equals(mGceAvd.getStatus())) {
                    // the GCE failed to boot it's not infrastructure.
                    captureBugreportz(listener);
                    return 0l;
                }
                // Some exception occured during the setup.
                listener.testRunFailed(
                        String.format("Exception '%s' during AVD startup", e.getMessage()));
                throw new DeviceNotAvailableException(
                        "Exception during AVD GCE startup", e, getDevice().getSerialNumber());
            }
            // check if device is online after GCE bringup, i.e. if adb is broken
            CLog.v("Waiting for device %s online", getDevice().getSerialNumber());
            getDevice().setRecoveryMode(RecoveryMode.ONLINE);
            try {
                getDevice().waitForDeviceOnline();
                onlineTime = System.currentTimeMillis() - bootStart;
            } catch (DeviceNotAvailableException dnae) {
                CLog.e("AVD GCE not online after startup");
                CLog.e(dnae);
                listener.testRunFailed("AVD GCE not online after startup");
                mFirstBootSuccess = false;
                // unlike device boot test, not throwing an exception here because it won't cause
                // large scale device offline
                return 0l;
            }
        } finally {
            CLog.d("Device online time: %dms", onlineTime);
            if (onlineTime != INVALID_TIME_DURATION) {
                result.put(ONLINE, Double.toString(((double) onlineTime) / 1000));
            }
        }
        return bootStart;
    }

    @Override
    public void extraDeviceCheck(ITestInvocationListener listener, TestIdentifier testId) {
        getDevice().setRecoveryMode(RecoveryMode.NONE);
        // We are considering logcat dump and bugreport failures as test failure in this case:
        // A freshly booted Gce Avd instance should be able to pull a logcat/bugreport without
        // failing. We have seen issue where tunnel drops while doing these actions, we want to
        // make sure these issues are caught.
        InputStreamSource logcatSource = getDevice().getLogcatDump();
        try {
            if (logcatSource == null || logcatSource.size() == 0) {
                listener.testFailed(testId, "Failed to collect logcat dump");
                getDevice().setRecoveryMode(RecoveryMode.AVAILABLE);
                return;
            } else {
                listener.testLog(LOGCAT_NAME, LogDataType.LOGCAT, logcatSource);
            }
        } finally {
            StreamUtil.cancel(logcatSource);
        }

        InputStreamSource bugreportSource = getDevice().getBugreport();
        try {
            if (bugreportSource == null || bugreportSource.size() == 0) {
                listener.testFailed(testId, "Failed to collect bugreport");
                getDevice().setRecoveryMode(RecoveryMode.AVAILABLE);
                return;
            } else {
                listener.testLog(BUGREPORT_NAME, LogDataType.BUGREPORT, bugreportSource);
            }
        } finally {
            StreamUtil.cancel(bugreportSource);
        }
    }

    @Override
    public void finalTearDown() throws DeviceNotAvailableException {
        if (mPreparer != null) {
            if (!mFirstBootSuccess && mShouldSkipTearDown) {
                CLog.i("Skipping GCE tearDown requested. Instance won't be deleted.");
                // if we skip tear down, we still stop the bridge.
                mPreparer.shutdownGceSshMonitor(getDevice());
                // still take the logs even if we don't tearDown the instance.
                mPreparer.logGceBootupLogs();
            } else {
                // delete the instance
                mPreparer.tearDown(getDevice(), getBuildInfo(), null);
            }
        }
    }

    /** Capture and log a bugreportz. */
    @VisibleForTesting
    void captureBugreportz(ITestLogger testLogger) {
        if (mGceAvd != null) {
            File bugreportFile = null;
            try {
                bugreportFile =
                        GceManager.getBugreportzWithSsh(
                                mGceAvd, getTestDeviceOptions(getDevice()), RunUtil.getDefault());
                if (bugreportFile != null) {
                    InputStreamSource bugreport = new FileInputStreamSource(bugreportFile);
                    testLogger.testLog("bugreportz-ssh", LogDataType.BUGREPORTZ, bugreport);
                    StreamUtil.cancel(bugreport);
                } else {
                    CLog.w("get bugreport via ssh returned null.");
                }
            } catch (IOException e) {
                CLog.e(e);
            } finally {
                FileUtil.deleteFile(bugreportFile);
            }
        } else {
            CLog.w("No Avd info available to describe the instance");
        }
    }

    /** Helper to use for Google specific device instead of relying on the attributes directly. */
    protected GceAvdTestDeviceOptions getTestDeviceOptions(ITestDevice device) {
        if (device.getOptions() instanceof GceAvdTestDeviceOptions) {
            return (GceAvdTestDeviceOptions) device.getOptions();
        }
        throw new RuntimeException(
                "GceBootTest needs to be configured with GoogleTestDeviceOptions.");
    }

    @Override
    public void testRebootCommand(ITestInvocationListener listener, RebootType rebootType) {
        // fastboot is not available in gce and cannot reboot into bootloader
        if (RebootType.REBOOT_BOOTLOADER_TEST.equals(rebootType)) {
            CLog.d(String.format("Skipping %s for GCE because it is not supported", rebootType));
            return;
        }
        TestIdentifier bootloaderTestId =
                new TestIdentifier(String.format("%s.%s", BOOT_TEST, BOOT_TEST),
                        rebootType.toString());
        listener.testStarted(bootloaderTestId);
        try {
            getDevice().reboot();
        } catch (DeviceNotAvailableException dnae) {
            CLog.e(String.format(ERROR_NOT_REBOOT, rebootType));
            CLog.e(dnae);
            // only report as test failure, not test run failure because we
            // were able to run the test until the end, despite the failure verdict, and the
            // device is returned to the pool in a useable state
            mFirstBootSuccess = false;
            listener.testFailed(bootloaderTestId, String.format(ERROR_NOT_REBOOT, rebootType));
            return;
        } finally {
            listener.testEnded(bootloaderTestId, new HashMap<String, String>());
        }
    }
}
