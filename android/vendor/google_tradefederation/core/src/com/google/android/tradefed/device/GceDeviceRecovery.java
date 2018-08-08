// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceStateMonitor;
import com.android.tradefed.device.WaitDeviceRecovery;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

/** A device recovery used for GCE AVD devices. */
@OptionClass(alias = "gce-device-recovery")
public class GceDeviceRecovery extends WaitDeviceRecovery {

    private static final long RETRY_INTERVAL_MS = 5000;
    private static final int MAX_RETRIES = 5;
    private static final long DEFAULT_SHORT_CMD_TIMEOUT = 20 * 1000;

    public static final long WAIT_FOR_ADB_CONNECT = 2 * 60 * 1000;
    private static final String ADB_SUCCESS_CONNECT_TAG = "connected to";
    private static final String ADB_ALREADY_CONNECTED_TAG = "already";
    private static final String ADB_CONN_REFUSED = "Connection refused";

    @Option(
        name = "standard-recovery",
        description = "skip the specific recovery and simply use the base class recovery."
    )
    private boolean mDisable = false;

    public GceDeviceRecovery() {
        super();
        mDisableUnresponsiveReboot = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recoverDevice(IDeviceStateMonitor monitor, boolean recoverUntilOnline)
            throws DeviceNotAvailableException {
        if (!mDisable) {
            reconnectAdb(monitor);
        }
        super.recoverDevice(monitor, recoverUntilOnline);
    }

    // Reconnect device.
    void reconnectAdb(IDeviceStateMonitor monitor) throws DeviceNotAvailableException {
        adbTcpConnect(monitor.getSerialNumber());
        waitForAdbConnect(WAIT_FOR_ADB_CONNECT, monitor.getSerialNumber());
    }

    /**
     * Helper method to adb connect to a given tcp ip Android device
     *
     * @param serial the hostname/ip:port of a tcp/ip Android device
     * @return true if we successfully connected to the device, false
     *         otherwise.
     */
    private boolean adbTcpConnect(String serial) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            CommandResult result = getRunUtil().runTimedCmd(DEFAULT_SHORT_CMD_TIMEOUT, "adb",
                    "connect", serial);
            if (CommandStatus.SUCCESS.equals(result.getStatus()) &&
                result.getStdout().contains(ADB_SUCCESS_CONNECT_TAG)) {
                CLog.d("adb connect output: status: %s stdout: %s stderr: %s",
                        result.getStatus(), result.getStdout(), result.getStderr());

                // It is possible to get a positive result without it being connected because of
                // the ssh bridge. Retrying to get confirmation, and expecting "already connected".
                if(confirmAdbTcpConnect(serial)) {
                    return true;
                }
            } else if (CommandStatus.SUCCESS.equals(result.getStatus()) &&
                    result.getStdout().contains(ADB_CONN_REFUSED)) {
                // If we find "Connection Refused", we bail out directly as more connect won't help
                return false;
            }
            CLog.d("adb connect output: status: %s stdout: %s stderr: %s, retrying.",
                    result.getStatus(), result.getStdout(), result.getStderr());
            getRunUtil().sleep((i + 1) * RETRY_INTERVAL_MS);
        }
        return false;
    }

    private boolean confirmAdbTcpConnect(String serial) {
        CommandResult resultConfirmation =
                getRunUtil().runTimedCmd(DEFAULT_SHORT_CMD_TIMEOUT, "adb", "connect", serial);
        if (CommandStatus.SUCCESS.equals(resultConfirmation.getStatus()) &&
                resultConfirmation.getStdout().contains(ADB_ALREADY_CONNECTED_TAG)) {
            CLog.d("adb connect confirmed:\nstdout: %s\nsterr: %s",
                    resultConfirmation.getStdout(), resultConfirmation.getStderr());
            return true;
        } else {
            CLog.d("adb connect confirmation failed:\nstatus:%s\nstdout: %s\nsterr: %s",
                    resultConfirmation.getStatus(), resultConfirmation.getStdout(),
                    resultConfirmation.getStderr());
        }
        return false;
    }

    /**
     * Check if the adb connection is enabled.
     */
    private void waitForAdbConnect(final long waitTime, String serial)
            throws DeviceNotAvailableException {
        CLog.i("Waiting %d ms for adb connection.", waitTime);
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < waitTime) {
            if (confirmAdbTcpConnect(serial)) {
                CLog.d("Adb connection confirmed.");
                return;
            }
            getRunUtil().sleep(RETRY_INTERVAL_MS);
        }
        throw new DeviceNotAvailableException(
                String.format("No adb connection after %sms.", waitTime), serial);
    }
}
