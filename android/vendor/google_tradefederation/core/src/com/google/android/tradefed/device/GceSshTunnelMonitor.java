// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.RemoteAndroidDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import com.google.android.tradefed.util.GceRemoteCmdFormatter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HostAndPort;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Thread Monitor for the Gce ssh tunnel. */
public class GceSshTunnelMonitor extends Thread {

    // stop/start adbd has longer retries in order to support possible longer reboot time.
    private static final long ADBD_RETRY_INTERVAL_MS = 15000;
    private static final int ADBD_MAX_RETRIES = 10;
    private static final long DEFAULT_LONG_CMD_TIMEOUT = 60 * 1000;
    private static final long DEFAULT_SHORT_CMD_TIMEOUT = 20 * 1000;
    private static final int WAIT_FOR_FIRST_CONNECT = 10 * 1000;
    private static final long WAIT_AFTER_REBOOT = 60 * 1000;

    // Format string for local hostname.
    private static final String DEFAULT_LOCAL_HOST = "127.0.0.1:%d";
    private static final int DEFAULT_ADB_PORT = 5555;

    // Format string for non-interactive SSH tunneling parameter;params in
    // order:local port, remote port
    private static final String TUNNEL_PARAM = "-L%d:127.0.0.1:%d";

    private boolean mQuit = false;
    private boolean mAdbRebootCalled = false;
    private ITestDevice mDevice;
    private GceAvdTestDeviceOptions mDeviceOptions;

    private HostAndPort mGceHostAndPort;
    private HostAndPort mLocalHostAndPort;
    private Process mSshTunnelProcess;
    private IBuildInfo mBuildInfo;
    private int mLastUsedPort = 0;

    private Exception mLastException = null;
    private boolean mSshChecked = false;

    /**
     * Constructor
     *
     * @param device {@link ITestDevice} the TF device to associate the remote GCE AVD with.
     * @param gce {@link HostAndPort} of the remote GCE AVD.
     */
    GceSshTunnelMonitor(
            ITestDevice device,
            IBuildInfo buildInfo,
            HostAndPort gce,
            GceAvdTestDeviceOptions deviceOptions) {
        super(
                String.format(
                        "GceSshTunnelMonitor-%s-%s-%s",
                        buildInfo.getBuildBranch(),
                        buildInfo.getBuildFlavor(),
                        buildInfo.getBuildId()));
        setDaemon(true);
        mDevice = device;
        mGceHostAndPort = gce;
        mBuildInfo = buildInfo;
        mDeviceOptions = deviceOptions;
        mLastException = null;
        mQuit = false;
    }

    /** Returns the instance of {@link IRunUtil}. */
    @VisibleForTesting
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /** Returns the {@link GceAvdTestDeviceOptions} the tunnel was initialized with. */
    GceAvdTestDeviceOptions getTestDeviceOptions() {
        return mDeviceOptions;
    }

    /** Returns True if the {@link GceSshTunnelMonitor} is still alive, false otherwise. */
    public boolean isTunnelAlive() {
        if (mSshTunnelProcess != null) {
            return mSshTunnelProcess.isAlive();
        }
        return false;
    }

    /** Set True when an adb reboot is about to be called to make sure the monitor expect it. */
    public void isAdbRebootCalled(boolean isCalled) {
        mAdbRebootCalled = isCalled;
    }

    /** Terminate the tunnel monitor */
    public void shutdown() {
        mQuit = true;
        closeConnection();
        getRunUtil().allowInterrupt(true);
        getRunUtil().interrupt(this, "shutting down the monitor thread.");
        interrupt();
    }

    /** Waits for this monitor to finish, as in {@link Thread#join()}. */
    public void joinMonitor() throws InterruptedException {
        // We use join with a timeout to ensure tearDown is never blocked forever.
        super.join(DEFAULT_LONG_CMD_TIMEOUT);
    }

    /** Close all the connections from the monitor (adb and ssh tunnel). */
    public void closeConnection() {
        // shutdown adb connection first, if we reached where there could be a connection
        CLog.d("closeConnection is triggered.");
        if (mLocalHostAndPort != null) {
            if (!((RemoteAndroidDevice) mDevice)
                    .adbTcpDisconnect(
                            mLocalHostAndPort.getHostText(),
                            Integer.toString(mLocalHostAndPort.getPort()))) {
                CLog.d("Failed to disconnect from local host %s", mLocalHostAndPort.toString());
            }
        }
        if (mSshTunnelProcess != null) {
            mSshTunnelProcess.destroy();
            try {
                boolean res =
                        mSshTunnelProcess.waitFor(DEFAULT_SHORT_CMD_TIMEOUT, TimeUnit.MILLISECONDS);
                if (!res) {
                    CLog.e("SSH tunnel may not have properly terminated.");
                }
            } catch (InterruptedException e) {
                CLog.e("SSH tunnel interrupted during shutdown: %s", e.getMessage());
            }
        }
    }

    /** Check that the ssh key file is readable so that our commands can go through. */
    @VisibleForTesting
    void checkSshKey() {
        if (!getTestDeviceOptions().getSshPrivateKeyPath().canRead()) {
            if (mSshChecked) {
                // The key was available before and for some reason is not now.
                getRunUtil().sleep(DEFAULT_SHORT_CMD_TIMEOUT);
                if (getTestDeviceOptions().getSshPrivateKeyPath().canRead()) {
                    CLog.w("ssh key was not available for a temporary period of time.");
                    // TODO: Add metric logging
                    return;
                }
            }
            throw new RuntimeException(
                    String.format(
                            "Ssh private key is unavailable %s",
                            getTestDeviceOptions().getSshPrivateKeyPath().getAbsolutePath()));
        }
        mSshChecked = true;
    }

    /** Perform some initial setup on the GCE AVD. */
    void initGce() {
        checkSshKey();

        // HACK: stop/start adbd first, otherwise device seems to be in offline mode.
        List<String> stopAdb =
                GceRemoteCmdFormatter.getSshCommand(
                        getTestDeviceOptions().getSshPrivateKeyPath(),
                        null,
                        mGceHostAndPort.getHostText(),
                        "stop",
                        "adbd");
        CLog.d("Running %s", stopAdb);
        CommandResult result =
                getRunUtil()
                        .runTimedCmdSilentlyRetry(
                                DEFAULT_SHORT_CMD_TIMEOUT,
                                ADBD_RETRY_INTERVAL_MS,
                                ADBD_MAX_RETRIES,
                                stopAdb.toArray(new String[0]));
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            CLog.w("failed to stop adbd %s", result.getStderr());
            throw new RuntimeException("failed to stop adbd");
        }
        if (mQuit) {
            throw new RuntimeException("Shutdown has been requested. stopping init.");
        }
        List<String> startAdb =
                GceRemoteCmdFormatter.getSshCommand(
                        getTestDeviceOptions().getSshPrivateKeyPath(),
                        null,
                        mGceHostAndPort.getHostText(),
                        "start",
                        "adbd");
        result =
                getRunUtil()
                        .runTimedCmdSilentlyRetry(
                                DEFAULT_SHORT_CMD_TIMEOUT,
                                ADBD_RETRY_INTERVAL_MS,
                                ADBD_MAX_RETRIES,
                                startAdb.toArray(new String[0]));
        CLog.d("Running %s", startAdb);
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            CLog.w("failed to start adbd", result);
            throw new RuntimeException("failed to start adbd");
        }
    }

    @Override
    public void run() {
        while (!mQuit) {
            try {
                if (mQuit) {
                    CLog.d("Final shutdown of the tunnel has been requested. terminating.");
                    return;
                }
                initGce();
            } catch (RuntimeException e) {
                mLastException = e;
                CLog.d("Failed to init remote GCE. Terminating due to:");
                CLog.e(e);
                return;
            }
            mSshTunnelProcess =
                    createSshTunnel(
                            mDevice,
                            mGceHostAndPort.getHostText(),
                            mGceHostAndPort.getPortOrDefault(DEFAULT_ADB_PORT));
            if (mSshTunnelProcess == null) {
                CLog.e("Failed creating the ssh tunnel to GCE.");
                return;
            }
            // Device serial should contain tunnel host and port number.
            getRunUtil().sleep(WAIT_FOR_FIRST_CONNECT);
            // Checking if it is actually running.
            if (isTunnelAlive()) {
                mLocalHostAndPort = HostAndPort.fromString(mDevice.getSerialNumber());
                if (!((RemoteAndroidDevice) mDevice)
                        .adbTcpConnect(
                                mLocalHostAndPort.getHostText(),
                                Integer.toString(mLocalHostAndPort.getPort()))) {
                    CLog.e("Adb connect failed, re-init GCE connection.");
                    closeConnection();
                    continue;
                }
                try {
                    mSshTunnelProcess.waitFor();
                } catch (InterruptedException e) {
                    CLog.d("SSH tunnel terminated %s", e.getMessage());
                }
                CLog.d("Reached end of loop, tunnel is going to re-init.");
                if (mAdbRebootCalled) {
                    mAdbRebootCalled = false;
                    CLog.d(
                            "Tunnel reached end of loop due to adbReboot, "
                                    + "waiting a little for device to come online");
                    getRunUtil().sleep(WAIT_AFTER_REBOOT);
                }
            } else {
                CLog.e("SSH Tunnel is not alive after starting it. It must have returned.");
            }
        }
    }

    /**
     * Create an ssh tunnel to a given remote host and assign the endpoint to a device.
     *
     * @param device {@link ITestDevice} to which we want to associate this ssh tunnel.
     * @param remoteHost the hostname/ip of the remote tcp ip Android device.
     * @param remotePort the port of the remote tcp ip device.
     * @return {@link Process} of the ssh command.
     */
    @VisibleForTesting
    Process createSshTunnel(ITestDevice device, String remoteHost, int remotePort) {
        try {
            ServerSocket s = null;
            try {
                s = new ServerSocket(mLastUsedPort);
            } catch (SocketException se) {
                // if we fail to allocate the previous port, we take a random available one.
                s = new ServerSocket(0);
                CLog.w(
                        "Our previous port: %s was already in use, switching to: %s",
                        mLastUsedPort, s.getLocalPort());
            }
            // even after being closed, socket may remain in TIME_WAIT state
            // reuse address allows to connect to it even in this state.
            s.setReuseAddress(true);
            mLastUsedPort = s.getLocalPort();
            String serial = String.format(DEFAULT_LOCAL_HOST, mLastUsedPort);
            if (mQuit) {
                CLog.d("Shutdown has been requested. Skipping creation of the ssh process");
                StreamUtil.close(s);
                return null;
            }
            CLog.d("Setting device %s serial to %s", device.getSerialNumber(), serial);
            ((IManagedTestDevice) device).setIDevice(new RemoteAvdIDevice(serial));
            ((IManagedTestDevice) device).setFastbootEnabled(false);
            mBuildInfo.setDeviceSerial(serial);
            StreamUtil.close(s);
            // Note there is a race condition here. between when we close
            // the server socket and when we try to connect to the tunnel.
            List<String> tunnelParam = new ArrayList<>();
            tunnelParam.add(String.format(TUNNEL_PARAM, mLastUsedPort, remotePort));
            tunnelParam.add("-N");
            List<String> sshTunnel =
                    GceRemoteCmdFormatter.getSshCommand(
                            getTestDeviceOptions().getSshPrivateKeyPath(),
                            tunnelParam,
                            remoteHost,
                            "" /* no command */);
            Process p = getRunUtil().runCmdInBackground(sshTunnel.toArray(new String[0]));
            return p;
        } catch (IOException e) {
            CLog.d("Failed to connect to remote GCE using ssh tunnel %s", e.getMessage());
        }
        return null;
    }

    /** Returns the last exception captured in the ssh tunnel thread. */
    public Exception getLastException() {
        return mLastException;
    }
}
