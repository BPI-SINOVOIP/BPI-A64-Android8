// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.asit;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceDisconnectedException;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.IDeviceFlasher.UserDataFlashOption;
import com.android.tradefed.targetprep.TargetSetupError;

import com.google.android.tradefed.targetprep.GoogleDeviceFlashPreparer;
import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;

/** Device boot test for a physical device. */
@OptionClass(alias = "device-boot-test")
public class DeviceBootTest extends BaseBootTest {

    @Option(name = "userdata-flash", description = "Specify handling of userdata partition.")
    private UserDataFlashOption mUserDataFlashOption = UserDataFlashOption.WIPE;

    @Option(name = "concurrent-flasher-limit", description = "The maximum number of concurrent"
            + " flashers (may be useful to avoid memory constraints)")
    private Integer mConcurrentFlashLimit = null;

    @Option(
        name = "skip-pre-flash-product-check",
        description = "Specify if device product type should be checked before flashing"
    )
    private boolean mSkipPreFlashProductType = false;

    protected static final String ERROR_NOT_ONLINE = "Device not online after flashing";
    protected static final String RAMDUMP_CHECK = "which ramdump";
    private static final String RAMDUMP_DISABLE = "ramdump -d";

    /** Helper to create the device flasher. */
    @VisibleForTesting
    GoogleDeviceFlashPreparer createDeviceFlasher() {
        return new GoogleDeviceFlashPreparer();
    }

    @Override
    public long bringUp(ITestInvocationListener listener, Map<String, String> result)
            throws DeviceNotAvailableException {
        long bootStart = INVALID_TIME_DURATION;
        long onlineTime = INVALID_TIME_DURATION;
        mRamDumpPath = getDevice().executeShellCommand(RAMDUMP_CHECK);
        if (!mRamDumpPath.isEmpty()) {
            getDevice().executeShellCommand(RAMDUMP_DISABLE);
        }
        GoogleDeviceFlashPreparer flasher = createDeviceFlasher();
        try {
            try {
                OptionSetter setter = new OptionSetter(flasher);
                // duping and passing parameters down to flasher
                setter.setOptionValue("device-boot-time", Long.toString(mDeviceBootTime));
                setter.setOptionValue("userdata-flash", mUserDataFlashOption.toString());
                if (mConcurrentFlashLimit != null) {
                    setter.setOptionValue("concurrent-flasher-limit",
                            mConcurrentFlashLimit.toString());
                }
                // always to skip because the test needs to detect device online
                // and available state
                // individually
                setter.setOptionValue("skip-post-flashing-setup", Boolean.TRUE.toString());
                setter.setOptionValue("force-system-flash", Boolean.TRUE.toString());
                setter.setOptionValue(
                        "skip-pre-flash-product-check", Boolean.toString(mSkipPreFlashProductType));
            } catch (ConfigurationException ce) {
                // this really shouldn't happen, but if it does, it'll indicate a setup problem
                // so this should be exposed, even at the expense of categorizing the build as
                // having a critical failure
                String trace = "failed to set options for flasher";
                listener.testRunFailed(trace);
                throw new RuntimeException(trace, ce);
            }
            // flash it!
            CLog.v("Flashing device %s", getDevice().getSerialNumber());
            try {
                flasher.setUp(getDevice(), getBuildInfo());
                // we are skipping post boot setup so this is the start of boot process
                bootStart = System.currentTimeMillis();
            } catch (TargetSetupError | BuildError e) {
                // setUp() may thrown DeviceNotAvailableException, TargetSetupError and BuildError.
                // DNAE is allowed to get thrown here so that a tool failure is triggered and build
                // maybe retried; the other 2x types are also rethrown as RuntimeException's for
                // the same purpose. In general, these exceptions reflect flashing or infra related
                // flakiness, so retrying is a reasonable mitigation.
                String trace = "Exception during device flashing";
                listener.testRunFailed(trace);
                throw new RuntimeException(trace, e);
            }
            // check if device is online after flash, i.e. if adb is broken
            CLog.v("Waiting for device %s online", getDevice().getSerialNumber());
            getDevice().setRecoveryMode(RecoveryMode.ONLINE);
            try {
                getDevice().waitForDeviceOnline();
                onlineTime = System.currentTimeMillis() - bootStart;
            } catch (DeviceNotAvailableException dnae) {
                CLog.e(ERROR_NOT_ONLINE);
                CLog.e(dnae);
                listener.testRunFailed(ERROR_NOT_ONLINE);
                throw new DeviceDisconnectedException(
                        ERROR_NOT_ONLINE, getDevice().getSerialNumber());
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
    public void testRebootCommand(ITestInvocationListener listener, RebootType rebootType) {
        TestIdentifier bootloaderTestId =
                new TestIdentifier(String.format("%s.%s", BOOT_TEST, BOOT_TEST),
                        rebootType.toString());
        listener.testStarted(bootloaderTestId);
        try {
            if (RebootType.REBOOT_BOOTLOADER_TEST.equals(rebootType)) {
                getDevice().rebootIntoBootloader();
            } else if (RebootType.REBOOT_TEST.equals(rebootType)) {
                getDevice().reboot();
            }
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
