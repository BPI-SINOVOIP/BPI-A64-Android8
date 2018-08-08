// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceManager.FastbootDevice;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.FastbootHelper;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.IMultiDeviceRecovery;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link IMultiDeviceRecovery} which resets USB buses for offline devices using usb_tool.
 */
public class UsbResetMultiDeviceRecovery implements IMultiDeviceRecovery {

    private static final long USB_TOOL_TIMEOUT = 60 * 1000;

    @Option(name = "usb-tool-path",
            description = "the path to usb tool binary.")
    private String mUsbToolPath = "usb_tool.par";

    private String mFastbootPath = "fastboot";

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFastbootPath(String fastbootPath) {
        mFastbootPath = fastbootPath;
    }

    /**
     * {@InheritDoc}
     */
    @Override
    public void recoverDevices(List<IManagedTestDevice> devices) {
        // This list holds device serials which are in 'Unknown' or 'Unavailable' state.
        List<String> deviceSerials = new ArrayList<>();
        List<ITestDevice> deviceToReboot = new ArrayList<>();

        final FastbootHelper fastboot = new FastbootHelper(getRunUtil(), mFastbootPath);
        Set<String> fastbootSerials = fastboot.getDevices();

        for (IManagedTestDevice device : devices) {
            // Make sure we do not skip discovered Fastboot device.
            if (device.getIDevice() instanceof StubDevice
                    && !(device.getIDevice() instanceof FastbootDevice)) {
                continue;
            }
            if (DeviceAllocationState.Unknown.equals(device.getAllocationState())
                    || DeviceAllocationState.Unavailable.equals(device.getAllocationState())
                    ||
                    // if device is in fastboot and not allocated, this is suspicious so we reset it.
                    (fastbootSerials.contains(device.getSerialNumber())
                            && !DeviceAllocationState.Allocated.equals(
                                    device.getAllocationState()))) {
                deviceSerials.add(device.getSerialNumber());
                deviceToReboot.add(device);
            }
        }

        if (!deviceSerials.isEmpty()) {
            final String[] commands = { mUsbToolPath, "--serials",
                    ArrayUtil.join(",", deviceSerials), "reset" };
            final IRunUtil runUtil = getRunUtil();
            final CommandResult result = runUtil.runTimedCmd(USB_TOOL_TIMEOUT, commands);
            if (result.getStatus() != CommandStatus.SUCCESS) {
                CLog.w("failed to reset USB: stdout=%s, stderr=%s", result.getStdout(),
                        result.getStderr());
            }
            // we only reboot device that were not in fastboot.
            fastbootSerials = fastboot.getDevices();
            if (fastbootSerials == null) {
                fastbootSerials = new HashSet<>();
            }
            for (ITestDevice device : deviceToReboot) {
                if (!fastbootSerials.contains(device.getSerialNumber())) {
                    try {
                        device.reboot();
                    } catch (DeviceNotAvailableException e) {
                        CLog.e(e);
                        CLog.w("Device '%s' did not come back online after USB reset.",
                                device.getSerialNumber());
                    }
                }
            }
        }
    }

    /**
     * Returns a {@link IRunUtil} instance.
     * Exposed for testing.
     *
     * @return a {@link IRunUtil} instance.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
