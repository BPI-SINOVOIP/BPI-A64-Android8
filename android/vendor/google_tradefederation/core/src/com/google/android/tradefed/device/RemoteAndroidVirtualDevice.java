// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.IDeviceStateMonitor;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.RemoteAndroidDevice;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.TarUtil;

import com.google.android.tradefed.util.GceAvdInfo;
import com.google.android.tradefed.util.GceAvdInfo.GceStatus;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HostAndPort;

import java.io.File;
import java.io.IOException;

/**
 * Extends {@link RemoteAndroidDevice} behavior for a full stack android device running in the
 * Google Compute Engine (Gce).
 * Assume the device serial will be in the format <hostname>:<portnumber> in adb.
 */
public class RemoteAndroidVirtualDevice extends RemoteAndroidDevice
        implements ITestLoggerReceiver {

    private String mInitialSerial;
    private GceAvdInfo mGceAvd;
    private ITestLogger mTestLogger;

    private GceManager mGceHandler = null;
    private GceSshTunnelMonitor mGceSshMonitor;

    private static final long WAIT_FOR_TUNNEL_ONLINE = 2 * 60 * 1000;
    private static final long WAIT_AFTER_REBOOT = 60 * 1000;
    private static final long WAIT_FOR_TUNNEL_OFFLINE = 5 * 1000;
    private static final int WAIT_TIME_DIVISION = 4;

    /**
     * Creates a {@link RemoteAndroidVirtualDevice}.
     *
     * @param device the associated {@link IDevice}
     * @param stateMonitor the {@link IDeviceStateMonitor} mechanism to use
     * @param allocationMonitor the {@link IDeviceMonitor} to inform of allocation state changes.
     */
    public RemoteAndroidVirtualDevice(IDevice device, IDeviceStateMonitor stateMonitor,
            IDeviceMonitor allocationMonitor) {
        super(device, stateMonitor, allocationMonitor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preInvocationSetup(IBuildInfo info)
            throws TargetSetupError, DeviceNotAvailableException {
        try {
            mGceAvd = null;
            mGceSshMonitor = null;
            // We create a brand new GceManager each time to ensure clean state.
            mGceHandler = new GceManager(getDeviceDescriptor(), getTestDeviceOptions(), info);
            mInitialSerial = getSerialNumber();
            setFastbootEnabled(false);

            // Launch GCE helper script.
            long startTime = getCurrentTime();
            launchGce(info);
            long remainingTime = getTestDeviceOptions().getGceCmdTimeout()
                    - (getCurrentTime() - startTime);
            if (remainingTime < 0) {
                throw new DeviceNotAvailableException(
                        String.format("Failed to launch GCE after %sms",
                                getTestDeviceOptions().getGceCmdTimeout()),
                        getSerialNumber());
            }
            CLog.d("%sms left before timeout after GCE launch returned", remainingTime);
            // Wait for device to be ready.
            RecoveryMode previousMode = getRecoveryMode();
            setRecoveryMode(RecoveryMode.NONE);
            try {
                for (int i = 0; i < WAIT_TIME_DIVISION; i++) {
                    // We don't have a way to bail out of waitForDeviceAvailable if the Gce Avd
                    // boot up and then fail some other setup so we check to make sure the monitor
                    // thread is alive and we have an opportunity to abort and avoid wasting time.
                    if (getMonitor().waitForDeviceAvailable(remainingTime / WAIT_TIME_DIVISION)
                            != null) {
                        break;
                    }
                    waitForTunnelOnline(WAIT_FOR_TUNNEL_ONLINE);
                    waitForAdbConnect(WAIT_FOR_ADB_CONNECT);
                }
            } finally {
                setRecoveryMode(previousMode);
            }
            if (!DeviceState.ONLINE.equals(getIDevice().getState())) {
                throw new DeviceNotAvailableException(
                        String.format("AVD device booted but was in %s state",
                        getIDevice().getState()), getSerialNumber());
            }
            enableAdbRoot();
        } catch (DeviceNotAvailableException|TargetSetupError e) {
            throw e;
        } finally {
            // always log the gce boot up logs
            logGceBootupLogs(mTestLogger);
        }
        // make sure we start logcat directly, device is up.
        setLogStartDelay(0);
        // For virtual device we only start logcat collection after we are sure it's online.
        if (getTestDeviceOptions().isLogcatCaptureEnabled()) {
            startLogcat();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postInvocationTearDown() {
        super.postInvocationTearDown();
        CLog.i("Shutting down GCE device %s", getSerialNumber());
        // Terminate SSH tunnel process.
        stopLogcat();
        if (getGceSshMonitor() != null) {
            getGceSshMonitor().shutdown();
            try {
                getGceSshMonitor().joinMonitor();
            } catch (InterruptedException e1) {
                CLog.i("Interrupted while waiting for GCE SSH monitor to shutdown.");
            }
        }
        if (!waitForDeviceNotAvailable(DEFAULT_SHORT_CMD_TIMEOUT)) {
            CLog.w("Device %s still available after timeout.", getSerialNumber());
        }

        if (mGceAvd != null) {
            // attempt to get a bugreport if Gce Avd is a failure
            if (!GceStatus.SUCCESS.equals(mGceAvd.getStatus())) {
                File bugreportFile = null;
                try {
                    bugreportFile =
                            GceManager.getBugreportzWithSsh(
                                    mGceAvd, getTestDeviceOptions(), getRunUtil());
                    if (bugreportFile != null) {
                        InputStreamSource bugreport = new FileInputStreamSource(bugreportFile);
                        mTestLogger.testLog("bugreportz-ssh", LogDataType.BUGREPORTZ, bugreport);
                        StreamUtil.cancel(bugreport);
                    }
                } catch (IOException e) {
                    CLog.e(e);
                } finally {
                    FileUtil.deleteFile(bugreportFile);
                }
            }
            // Cleanup GCE first to make sure ssh tunnel has nowhere to go.
            getGceHandler().shutdownGce();
        }

        if (mInitialSerial != null) {
            setIDevice(new RemoteAvdIDevice(mInitialSerial));
        }
        setFastbootEnabled(false);

        if (getGceHandler() != null) {
            getGceHandler().cleanUp();
        }
    }

    /** Launch the actual gce device based on the build info. */
    protected void launchGce(IBuildInfo buildInfo) throws TargetSetupError {
        TargetSetupError exception = null;
        for (int attempt = 0; attempt < getTestDeviceOptions().getGceMaxAttempt(); attempt++) {
            try {
                mGceAvd = getGceHandler().startGce();
                if (mGceAvd != null) break;
            } catch (TargetSetupError tse) {
                CLog.w("Failed to start Gce with attempt: %s out of %s. With Exception: %s",
                        attempt + 1, getTestDeviceOptions().getGceMaxAttempt(), tse);
                exception = tse;
            }
        }
        CLog.e("gce avd info: %s", mGceAvd);
        if (mGceAvd == null) {
            throw exception;
        } else if (GceAvdInfo.GceStatus.BOOT_FAIL.equals(mGceAvd.getStatus())) {
            throw new TargetSetupError(mGceAvd.getErrors(), getDeviceDescriptor());
        }
        createGceSshMonitor(this, buildInfo, mGceAvd.hostAndPort(), this.getTestDeviceOptions());
    }

    /** Create an ssh tunnel, connect to it, and keep the connection alive. */
    void createGceSshMonitor(
            ITestDevice device,
            IBuildInfo buildInfo,
            HostAndPort hostAndPort,
            GceAvdTestDeviceOptions deviceOptions) {
        mGceSshMonitor = new GceSshTunnelMonitor(device, buildInfo, hostAndPort, deviceOptions);
        mGceSshMonitor.start();
    }

    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mTestLogger = testLogger;
    }

    /**
     * Recover logs from the preparer and add them to the reporting if a failure occured.
     */
    private void logGceBootupLogs(ITestLogger listener) {
        File tmpLogcat = getGceHandler().getGceBootLogcatLog();
        try {
            if (tmpLogcat != null && tmpLogcat.canRead()) {
                TarUtil.extractAndLog(listener, tmpLogcat, "gce_logcat_bootup");
            }
            File tmpSerialLog = getGceHandler().getGceBootSerialLog();
            if (tmpSerialLog != null && tmpSerialLog.canRead()) {
                TarUtil.extractAndLog(listener, tmpSerialLog, "gce_serial_bootup");
            }
        } catch (SecurityException se) {
            CLog.e("Could not read the gce boot logs: %s", se);
        } catch (IOException io) {
            CLog.e("Issue when untarring the file: %s", io);
            CLog.e(io);
        }
    }

    /**
     * {@inherit}
     */
    @Override
    public void postBootSetup() throws DeviceNotAvailableException  {
        // After reboot, restart the tunnel
        if (!getTestDeviceOptions().shouldDisableReboot()) {
            CLog.e("Remote Avd postBootSetup");
            getRunUtil().sleep(WAIT_FOR_TUNNEL_OFFLINE);
            if (!getGceSshMonitor().isTunnelAlive()) {
                getGceSshMonitor().closeConnection();
                getRunUtil().sleep(WAIT_FOR_TUNNEL_OFFLINE);
                waitForTunnelOnline(WAIT_FOR_TUNNEL_ONLINE);
            }
            waitForAdbConnect(WAIT_FOR_ADB_CONNECT);
        }
        super.postBootSetup();
    }

    /**
     * Check if the tunnel monitor is running.
     */
    protected void waitForTunnelOnline(final long waitTime) throws DeviceNotAvailableException {
        CLog.i("Waiting %d ms for tunnel to be restarted", waitTime);
        long startTime = getCurrentTime();
        while (getCurrentTime() - startTime < waitTime) {
            if (getGceSshMonitor() == null) {
                CLog.e("Tunnel Thread terminated, something went wrong with the device.");
                break;
            }
            if (getGceSshMonitor().isTunnelAlive()) {
                CLog.d("Tunnel online again, resuming.");
                return;
            }
            getRunUtil().sleep(RETRY_INTERVAL_MS);
        }
        throw new DeviceNotAvailableException(
                String.format("Tunnel did not come back online after %sms", waitTime),
                getSerialNumber());
    }

    @Override
    public void recoverDevice() throws DeviceNotAvailableException {
        // Re-init tunnel when attempting recovery
        CLog.e("Attempting RemoteAvdDevice recovery");
        getGceSshMonitor().closeConnection();
        getRunUtil().sleep(WAIT_FOR_TUNNEL_OFFLINE);
        waitForTunnelOnline(WAIT_FOR_TUNNEL_ONLINE);
        waitForAdbConnect(WAIT_FOR_ADB_CONNECT);
        // Then attempt regular recovery
        super.recoverDevice();
    }

    @Override
    protected void doAdbReboot(String into) throws DeviceNotAvailableException {
        // We catch that adb reboot is called to expect it from the tunnel.
        getGceSshMonitor().isAdbRebootCalled(true);
        super.doAdbReboot(into);
        // We allow a little time for instance to reboot and be reachable.
        getRunUtil().sleep(WAIT_AFTER_REBOOT);
        // after the reboot we wait for tunnel to be online and device to be reconnected
        getRunUtil().sleep(WAIT_FOR_TUNNEL_OFFLINE);
        waitForTunnelOnline(WAIT_FOR_TUNNEL_ONLINE);
        waitForAdbConnect(WAIT_FOR_ADB_CONNECT);
    }

    /** Returns the {@link GceSshTunnelMonitor} of the device. Exposed for testing. */
    protected GceSshTunnelMonitor getGceSshMonitor() {
        return mGceSshMonitor;
    }

    /**
     * Returns the current system time. Exposed for testing.
     */
    protected long getCurrentTime() {
        return System.currentTimeMillis();
    }

    /**
     * Helper to use for Google specific device instead of relying on the attributes directly.
     */
    protected GceAvdTestDeviceOptions getTestDeviceOptions() {
        if (mOptions instanceof GceAvdTestDeviceOptions) {
            return (GceAvdTestDeviceOptions)mOptions;
        }
        throw new RuntimeException("RemoteAndroidVirtualDevice needs to be configured with "
                + "GoogleTestDeviceOptions.");
    }

    /** Returns the instance of the {@link GceManager}. */
    @VisibleForTesting
    GceManager getGceHandler() {
        return mGceHandler;
    }
}
