// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.IDeviceStateMonitor;
import com.android.tradefed.device.WaitDeviceRecovery;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A device recovery mechanism that uses a ncd relay to toggle usb and device power.
 */
public class NcdDeviceRecovery extends WaitDeviceRecovery {

    private static final String LOG_TAG = "NcdDeviceRecovery";
    private static final long NCD_TIMEOUT = 60*1000;
    private static final int MAX_NCD_ATTEMPTS  = 3;

    @Option(name = "ncd-path",
            description = "the absolute file system path to ncd binary.")
    private String mNcdPath = "ncd.py";

    @Option(name = "device-reboot",
            description = "toggle the power for unresponsive devices.")
    private boolean mHardReboot = true;

    @Option(name = "ncd",
            description = "toggle ncd based recovery. If false will use WaitDeviceRecovery.")
    private boolean mNcdOn = true;

    /**
     * Set the path to the ncd binary.
     * <p/>
     * Exposed for testing.
     */
    void setNcdPath(String ncdPath) {
        mNcdPath = ncdPath;
    }

    /**
     * Get the path to the ncd binary.
     * <p/>
     * Exposed for testing.
     */
    String getNcdPath() {
        return mNcdPath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleDeviceNotAvailable(IDeviceStateMonitor monitor, boolean recoverUntilOnline)
            throws DeviceNotAvailableException {
        if (!mNcdOn) {
            super.handleDeviceNotAvailable(monitor, recoverUntilOnline);
            return;
        }
        // reset device's usb power
        if (!resetUsb(monitor.getSerialNumber())) {
            throw new DeviceNotAvailableException(String.format(
                    "Could not execute ncd %s to reset device %s. Env set up incorrectly?",
                    mNcdPath, monitor.getSerialNumber()), monitor.getSerialNumber());
        }
        if (monitor.waitForDeviceOnline(mOnlineWaitTime) == null) {
            if (mHardReboot) {
                recoverViaHardReboot(monitor, recoverUntilOnline);
                return;
            } else {
                throw new DeviceNotAvailableException(String.format("Could not recover device %s.",
                        monitor.getSerialNumber()), monitor.getSerialNumber());
            }
        }
        // occasionally device is erroneously reported as online - double check that we can shell
        // into device
        if (!monitor.waitForDeviceShell(mShellWaitTime)) {
            if (mHardReboot) {
                recoverViaHardReboot(monitor, recoverUntilOnline);
                return;
            } else {
                throw new DeviceNotAvailableException(String.format("Could not recover device %s.",
                        monitor.getSerialNumber()), monitor.getSerialNumber());
            }
        }
        if (!recoverUntilOnline) {
            if (monitor.waitForDeviceAvailable(20*1000) == null) {
                if (mHardReboot) {
                    recoverViaHardReboot(monitor, recoverUntilOnline);
                    return;
                } else {
                    throw new DeviceUnresponsiveException(String.format(
                            "Device %s is unresponsive after a usb reset.",
                            monitor.getSerialNumber()), monitor.getSerialNumber());
                }
            }
        }
        Log.i(LOG_TAG, String.format("Usb reset on device %s was successful",
                monitor.getSerialNumber()));
    }

    private void recoverViaHardReboot(IDeviceStateMonitor monitor, boolean recoverTillOnline)
            throws DeviceNotAvailableException {
        resetDevice(monitor.getSerialNumber());
        // use normal device boot time, because power was cut
        if (monitor.waitForDeviceOnline(mOnlineWaitTime) == null) {
            throw new DeviceNotAvailableException(String.format("Could not recover device %s.",
                    monitor.getSerialNumber()), monitor.getSerialNumber());
        }
        if (!monitor.waitForDeviceShell(mShellWaitTime)) {
            throw new DeviceNotAvailableException(String.format("Could not recover device %s.",
                    monitor.getSerialNumber()), monitor.getSerialNumber());
        }
        if (!recoverTillOnline) {
            if (monitor.waitForDeviceAvailable() == null) {
                throw new DeviceUnresponsiveException(String.format(
                        "Device %s is unresponsive after a hard reboot.",
                        monitor.getSerialNumber()), monitor.getSerialNumber());
            }
        }
        Log.i(LOG_TAG, String.format("Hard reset on device %s was successful",
                monitor.getSerialNumber()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleDeviceBootloaderNotAvailable(IDeviceStateMonitor monitor)
            throws DeviceNotAvailableException {
        if (!mNcdOn) {
            super.handleDeviceBootloaderNotAvailable(monitor);
            return;
        }
        // reset device's usb power
        if (!resetUsb(monitor.getSerialNumber())) {
            throw new DeviceNotAvailableException(String.format(
                    "Could not execute ncd %s to reset device %s. Env set up incorrectly?",
                    mNcdPath, monitor.getSerialNumber()), monitor.getSerialNumber());
        }
        if (monitor.waitForDeviceBootloader(mWaitTime)) {
            return;
        }
        if (mHardReboot) {
            hardResetIntoBootloader(monitor);
        } else {
            throw new DeviceNotAvailableException(String.format(
                    "Could not recover device %s in bootloader", monitor.getSerialNumber()),
                    monitor.getSerialNumber());
        }
    }

    /**
     * Perform a hard reset on device, and attempt to bring it back to bootloader state
     *
     * @param monitor
     * @throws DeviceNotAvailableException
     */
    private void hardResetIntoBootloader(IDeviceStateMonitor monitor)
            throws DeviceNotAvailableException {
        resetDevice(monitor.getSerialNumber());
        // device will boot back up in userspace/adb. Wait for online, then reset to bootloader
        // use normal device boot time, because power was cut
        IDevice device = monitor.waitForDeviceOnline(mOnlineWaitTime);
        if (device != null) {
            rebootDeviceIntoBootloader(device);
            if (monitor.waitForDeviceBootloader(mWaitTime)) {
                return;
            }
        }
        throw new DeviceNotAvailableException(String.format(
                "Could not recover device %s in bootloader", monitor.getSerialNumber()),
                monitor.getSerialNumber());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleDeviceUnresponsive(IDevice device, IDeviceStateMonitor monitor)
            throws DeviceNotAvailableException {
        if (!mNcdOn) {
            super.handleDeviceUnresponsive(device, monitor);
            return;
        }
        // TODO: A "unavailable" device may currently be incorrectly diagnosed as unresponsive
        // for now, handle this as an unavailable device
        handleDeviceNotAvailable(monitor, false);
        /*
        if (mHardReboot) {
            resetDevice(monitor.getSerialNumber());
            monitor.waitForDeviceNotAvailable(INITIAL_BOOTLOADER_PAUSE_TIME);
            // use normal device boot time, because power was cut
            if (monitor.waitForDeviceOnline() == null) {
                throw new DeviceNotAvailableException(String.format(
                        "Device %s not online after hard reset", monitor.getSerialNumber()));
            }
            if (monitor.waitForDeviceAvailable() != null) {
                return;
            }
        }
        throw new DeviceUnresponsiveException(String.format(
                "Device %s is online but unresponsive", monitor.getSerialNumber()));
        */
    }

    /**
     * {@inheritDoc}
     * @throws DeviceNotAvailableException
     */
    @Override
    protected void handleDeviceBootloaderUnresponsive(IDeviceStateMonitor monitor)
            throws DeviceNotAvailableException {
        try {
            // first try super's recovery mechanism of rebooting device
            super.handleDeviceBootloaderUnresponsive(monitor);
        } catch (DeviceNotAvailableException e) {
            CLog.w("Device in bootloader is unresponsive");
            if (!mNcdOn) {
                throw e;
            }
            // handle this as a not available bootloader
            handleDeviceBootloaderNotAvailable(monitor);
        }
    }

    /**
     * Performs the reset of USB connection for device
     * <p/>
     * Also used for testing
     *
     * @param deviceSerial the serial number of device to reset
     * @return true if command was successfully issued
     */
    public boolean resetUsb(String deviceSerial) {
        Log.i(LOG_TAG, String.format("Resetting usb on device %s", deviceSerial));
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.FAILED);
        for (int i=0; i < MAX_NCD_ATTEMPTS ; i++) {
            result = getRunUtil().runTimedCmd(NCD_TIMEOUT, mNcdPath, "--device",
                    deviceSerial, "--usb-only");
            if (result.getStatus().equals(CommandStatus.SUCCESS)) {
                return true;
            }
        }
        Log.e(LOG_TAG, String.format("Failed to execute %s. Status %s, stdout = %s, stderr = %s",
                mNcdPath, result.getStatus().toString(), result.getStdout(), result.getStderr()));
        return false;
    }

    /**
     * Performs the hard reset for device
     * <p/>
     * Also used for testing
     *
     * @param deviceSerial the serial number of device to reset
     * @return true if command was successfully issued
     */
    public boolean resetDevice(String deviceSerial) {
        Log.i(LOG_TAG, String.format("Hard resetting device %s", deviceSerial));
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.FAILED);
        for (int i=0; i < MAX_NCD_ATTEMPTS ; i++) {
            result = getRunUtil().runTimedCmd(NCD_TIMEOUT, mNcdPath, "--device",
                deviceSerial);
            if (result.getStatus().equals(CommandStatus.SUCCESS)) {
                return true;
            }
        }
        Log.e(LOG_TAG, String.format("Failed to execute %s. Status %s, stdout = %s, stderr = %s",
                mNcdPath, result.getStatus().toString(), result.getStdout(), result.getStderr()));
        return false;
    }

    /**
     * Get lists of device's currently visible on ncd.
     * <p/>
     * Used for diagnostic purposes.
     * @return the list of device serials.
     */
    Set<String> getDeviceList() {
        Log.i(LOG_TAG, "Getting list of devices on ncd");
        Set<String> deviceList = new HashSet<String>();
        CommandResult result = getRunUtil().runTimedCmd(NCD_TIMEOUT, mNcdPath, "--print");
        if (result.getStatus() == CommandStatus.SUCCESS) {
            String output = result.getStdout();
            Pattern ncdPattern = Pattern.compile("([\\w\\d]+)\\s+=\\s+\\d");
            Matcher ncdMatcher = ncdPattern.matcher(output);
            while (ncdMatcher.find()) {
                deviceList.add(ncdMatcher.group(1));
            }
        } else {
            Log.e(LOG_TAG, "Failed to get list of devices on ncd");
        }
        return deviceList;
    }
}
