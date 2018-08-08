// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.ITestDeviceMutator;
import com.android.tradefed.device.TcpDevice;
import com.android.tradefed.device.TestDeviceMutator;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.TarUtil;

import com.google.android.tradefed.device.RemoteAndroidVirtualDevice;
import com.google.android.tradefed.util.GceAvdInfo;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HostAndPort;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link ITargetPreparer} / {@link ITargetCleaner} that launches and connects to a GCE Android
 * device.
 *
 * @deprecated use {@link RemoteAndroidVirtualDevice} instead.
 */
@Deprecated
@OptionClass(alias = "gce-avd-preparer")
public class GceAvdPreparer implements ITargetCleaner, ITestLoggerReceiver {

    private static final long RETRY_INTERVAL_MS = 5000;
    private static final int MAX_RETRIES = 5;
    private static final int WAIT_FOR_FIRST_CONNECT = 10 * 1000;
    private static final String ADB_SUCCESS_CONNECT_TAG = "connected to";
    private static final String ADB_CONN_REFUSED = "Connection refused";

    @Option(name = "disable", description = "skip this target preparer")
    private boolean mDisable = false;

    @Option(name = "gce-driver-path", description = "path of the binary to launch GCE devices")
    private String mAvdDriverBinary = "/google/data/ro/projects/android/treehugger/acloud/acloud";

    @Option(name = "gce-driver-config-path",
            description = "path of the config to use to launch GCE devices.")
    private String mAvdConfigFile =
        "/google/data/ro/projects/android/treehugger/acloud/treehugger_postsubmit.config";

    @Option(name = "gce-boot-timeout",
            description = "timeout to wait in ms for GCE to be online.",
            isTimeVal = true)
    private long mGceCmdTimeout = 20 * 60 * 1000; // 20 minutes.

    @Option(name = "timeout-to-shutdown", description = "amount of time to wait before we delete "
            + "the GCE instance after we lost contact with GCE via SSH tunnel in msecs.",
            isTimeVal = true)
    private long mTimeoutToShutdown = 60 * 1000;

    @Option(
        name = "gce-private-key-path",
        description = "path to the ssh key private key location."
    )
    private File mSshPrivateKeyPath =
            new File("/google/data/ro/teams/tradefed/configs/gce-ssh/id_rsa");

    @Option(name = "gce-host", description = "remote gce host used for debugging.")
    private String mGceHost = null;

    @Option(name = "gce-instance-name",
            description = "remote gce host instance name used for debugging.")
    private String mGceInstanceName = null;

    @Option(name = "gce-driver-log-level", description ="Log level for gce driver")
    private LogLevel mGceDriverLogLevel = LogLevel.DEBUG;

    @Option(name = "gce-account", description = "email account to use with GCE driver.")
    private String mGceAccount = null;

    @Option(name = "max-gce-attempt", description = "Maximum number of attempts to start Gce "
            + "before throwing an exception.")
    private int mGceMaxAttempt = 1;

    private static final long DEFAULT_SHORT_CMD_TIMEOUT = 20 * 1000;
    private static final long DEFAULT_LONG_CMD_TIMEOUT = 60 * 1000;
    private static final int DEFAULT_ADB_PORT = 5555;

    // Format string for SSH command into GCE; params in order: private key,
    // options, remote
    // IP, command
    private static final String SSH_CMD = "ssh -o UserKnownHostsFile=/dev/null " +
            "-o StrictHostKeyChecking=no -o ServerAliveInterval=10 -i %s %s root@%s %s";

    // Format string for local hostname.
    private static final String DEFAULT_LOCAL_HOST = "127.0.0.1:%d";

    // Format string for non-interactive SSH tunneling parameter;params in
    // order:local port, remote port
    private static final String TUNNEL_PARAM = "-L%d:127.0.0.1:%d -N";

    private static final int WAIT_TIME_DIVISION = 4;

    private String mInitialSerial;
    private GceSshTunnelMonitor mGceSshMonitor;
    private ITestDeviceMutator mDeviceMutator = new TestDeviceMutator();
    private GceAvdInfo mGceAvd;
    private ITestLogger mLogger;
    private String mErrorFromTunnel = "";

    private File mGceBootFailureLogCat = null;
    private File mGceBootFailureSerialLog = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (mDisable) {
            CLog.d("Skipping GCE Target Preparer due to --disable flag.");
            return;
        }
        if (!TcpDevice.class.equals(device.getIDevice().getClass())) {
            // We skip if it's not a tcpDevice
            CLog.d("Skipping GCE Target Preparer, not a placeholder device.");
            mDisable = true;
            return;
        }
        mInitialSerial = device.getSerialNumber();
        try {
            mDeviceMutator.setFastbootEnabled(device, false);
        } catch (ClassCastException e) {
            throw new TargetSetupError("Invalid class.", e, device.getDeviceDescriptor());
        }

        // Launch GCE helper script.
        long startTime = System.currentTimeMillis();
        launchGce(device, buildInfo);
        long remainingTime = mGceCmdTimeout - (System.currentTimeMillis() - startTime);
        if (remainingTime < 0) {
            remainingTime = 0;
        }
        CLog.d("%sms left before timeout after GCE launch returned", remainingTime);
        // Wait for device to be ready.
        RecoveryMode previousMode = device.getRecoveryMode();
        device.setRecoveryMode(RecoveryMode.NONE);
        try {
            for (int i = 0; i < WAIT_TIME_DIVISION; i++) {
                // We don't have a way to bail out of waitForDeviceAvailable if the Gce Avd boot up
                // and then we fail some other setup so we check to make sure the monitor thread is
                // still working in case we have an opportunity to abort and avoid wasting time.
                device.waitForDeviceAvailable(remainingTime / WAIT_TIME_DIVISION);
                if (!mGceSshMonitor.isAlive()) {
                    throw new DeviceNotAvailableException(
                            "Error occured during AVD boot up. " + mErrorFromTunnel,
                            device.getSerialNumber());
                }
            }
        } finally {
            device.setRecoveryMode(previousMode);
        }
        if (!DeviceState.ONLINE.equals(device.getIDevice().getState())) {
            CLog.e("Issue with device: %s, disabling tearDown for investigation.");
            mDisable = true;
            throw new DeviceNotAvailableException(
                    String.format("AVD device booted but was in %s state",
                    device.getIDevice().getState()), device.getSerialNumber());
        }
        device.enableAdbRoot();
        if (device.getOptions().isLogcatCaptureEnabled()) {
            device.startLogcat();
        }
    }

    public File getLocatFailureLog() {
        return mGceBootFailureLogCat;
    }

    public File getSerialFailureLog() {
        return mGceBootFailureSerialLog;
    }

    public GceAvdInfo getGceAvdInfo() {
        return mGceAvd;
    }

    protected void launchGce(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError {
        TargetSetupError exception = null;
        for (int attempt = 0; attempt < mGceMaxAttempt; attempt++) {
            try {
                mGceAvd = startGce(buildInfo, device);
                if (mGceAvd != null) break;
            } catch (TargetSetupError tse) {
                CLog.w("Failed to start Gce with attempt: %s out of %s. With Exception: %s",
                        attempt, mGceMaxAttempt, tse);
                exception = tse;
            }
        }

        if (mGceAvd == null || !GceAvdInfo.GceStatus.SUCCESS.equals(mGceAvd.getStatus())) {
            throw exception;
        }
        // Create an ssh tunnel, connect to it, and keep the connection alive.
        mGceSshMonitor = new GceSshTunnelMonitor(device, buildInfo, mGceAvd.hostAndPort());
        mGceSshMonitor.start();
    }

    @VisibleForTesting
    GceSshTunnelMonitor getSshTunnelMonitor() {
        return mGceSshMonitor;
    }

    @VisibleForTesting
    GceAvdInfo startGce(IBuildInfo b, ITestDevice device) throws TargetSetupError {
        // For debugging purposes bypass.
        if (mGceHost != null && mGceInstanceName != null) {
            return new GceAvdInfo(mGceInstanceName,
                    HostAndPort.fromString(mGceHost).withDefaultPort(DEFAULT_ADB_PORT));
        }

        List<String> gceArgs = ArrayUtil.list(mAvdDriverBinary);
        gceArgs.add("create");
        // Add extra args.
        File reportFile = null;
        try {
            reportFile = FileUtil.createTempFile("gce_avd_driver", ".json");
            gceArgs = buildGceCmd(reportFile, b);
            long startTimestamp = System.currentTimeMillis();

            while (true) {
                CLog.i("Launching GCE with %s", gceArgs.toString());
                CommandResult cmd = getRunUtil().runTimedCmd(mGceCmdTimeout,
                        gceArgs.toArray(new String[gceArgs.size()]));
                CLog.v("GCE driver stderr: %s", cmd.getStderr());
                String instanceName = extractInstanceName(cmd.getStderr());
                if (instanceName != null) {
                    b.addBuildAttribute("gce-instance-name", instanceName);
                }
                if (CommandStatus.TIMED_OUT.equals(cmd.getStatus())) {
                    throw new TargetSetupError(String.format("acloud errors: timeout after %dms, "
                            + "acloud did not return", mGceCmdTimeout),
                            device.getDeviceDescriptor());
                } else if (!CommandStatus.SUCCESS.equals(cmd.getStatus())) {
                    CLog.w("Error when booting the Gce instance, reading output of gce driver");
                    GceAvdInfo failure = GceAvdInfo.parseGceInfoFromFile(reportFile,
                            device.getDeviceDescriptor());
                    String errors = "";
                    if (failure != null) {
                        // set the Avd info so it can be shutdown in teardown.
                        mGceAvd = failure;
                        errors = failure.getErrors();
                    } else {
                        errors = "Shutting down the instance was not attempted, check the gce " +
                                "driver's output. The instance may not have booted up at all.";
                        CLog.w(errors);
                    }
                    // If we have yet to exceed the timeout for launching GCE; retry.
                    if (System.currentTimeMillis() - startTimestamp < DEFAULT_LONG_CMD_TIMEOUT) {
                        CLog.w("Retrying to launch GCE once more.");
                        continue;
                    }
                    throw new TargetSetupError(String.format("acloud errors: %s", errors),
                            device.getDeviceDescriptor());
                }
                return GceAvdInfo.parseGceInfoFromFile(reportFile, device.getDeviceDescriptor());
            }
        } catch (IOException e) {
            throw new TargetSetupError("failed to create log file", e,
                    device.getDeviceDescriptor());
        } finally {
            FileUtil.deleteFile(reportFile);
        }
    }

    /**
     * Retrieve the instance name from the gce boot logs.
     * Search for the 'name': 'gce-<name>' pattern to extract the name of it.
     */
    protected String extractInstanceName(String bootupLogs) {
        if (bootupLogs != null) {
            final String pattern = "'name': '(((gce-)|(ins-))(.*?))'";
            Pattern namePattern = Pattern.compile(pattern);
            Matcher matcher = namePattern.matcher(bootupLogs);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * Build and return the command to launch GCE.
     * Exposed for testing.
     */
    protected List<String> buildGceCmd(File reportFile, IBuildInfo b) throws IOException {
        List<String> gceArgs = ArrayUtil.list(mAvdDriverBinary);
        gceArgs.add("create");
        gceArgs.add("--build_target");
        if (b.getBuildAttributes().containsKey("build_target")) {
            // If BuildInfo contains the attribute for a build target, use that.
            gceArgs.add(b.getBuildAttributes().get("build_target"));
        } else {
            gceArgs.add(b.getBuildFlavor());
        }
        gceArgs.add("--branch");
        gceArgs.add(b.getBuildBranch());
        gceArgs.add("--build_id");
        gceArgs.add(b.getBuildId());
        gceArgs.add("--config_file");
        gceArgs.add(mAvdConfigFile);
        gceArgs.add("--report_file");
        gceArgs.add(reportFile.getAbsolutePath());
        switch (mGceDriverLogLevel) {
            case DEBUG:
                gceArgs.add("-v");
                break;
            case VERBOSE:
                gceArgs.add("-vv");
                break;
            default:
                break;
        }
        if (mGceAccount != null) {
            gceArgs.add("--email");
            gceArgs.add(mGceAccount);
        }
        // Add flags to collect logcat and serial logs in case of boot failures.
        mGceBootFailureLogCat = FileUtil.createTempFile("gce_logcat_boot", ".tar.gz");
        gceArgs.add("--logcat_file");
        gceArgs.add(mGceBootFailureLogCat.getAbsolutePath());
        mGceBootFailureSerialLog = FileUtil.createTempFile("gce_serial_boot", ".tar.gz");
        gceArgs.add("--serial_log_file");
        gceArgs.add(mGceBootFailureSerialLog.getAbsolutePath());
        return gceArgs;
    }

    private void shutdownGce(GceAvdInfo gceAvd) {
        if (!(new File(mAvdDriverBinary).canExecute())) {
            throw new RuntimeException(String.format("GCE launcher %s is invalid",
                    mAvdDriverBinary));
        }
        List<String> gceArgs = ArrayUtil.list(mAvdDriverBinary);
        gceArgs.add("delete");
        // Add extra args.
        File f = null;
        try {
            gceArgs.add("--instance_names");
            gceArgs.add(gceAvd.instanceName());
            gceArgs.add("--config_file");
            gceArgs.add(mAvdConfigFile);
            f = FileUtil.createTempFile("gce_avd_driver", ".json");
            gceArgs.add("--report_file");
            gceArgs.add(f.getAbsolutePath());
            CLog.i("Tear down of GCE with %s", gceArgs.toString());
            CommandResult cmd = getRunUtil().runTimedCmd(mGceCmdTimeout,
                    gceArgs.toArray(new String[gceArgs.size()]));
            if (!CommandStatus.SUCCESS.equals(cmd.getStatus())) {
                CLog.w("Failed to tear down GCE %s with the following arg, %s",
                        gceAvd.instanceName(), gceArgs);
            }
        } catch (IOException e) {
            CLog.e("failed to create log file for GCE Teardown");
            CLog.e(e);
        } finally {
            FileUtil.deleteFile(f);
        }
    }

    protected IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mDisable) {
            CLog.d("Skipping GCE Target Cleanup due to --disable flag.");
            return;
        }

        // log the gce boot up logs
        logGceBootupLogs();

        shutdownGceSshMonitor(device);

        // Cleanup GCE
        if (mGceAvd != null) {
            shutdownGce(mGceAvd);
        }
    }

    /** Log the gce boot up logcat and serial for debugging. */
    public void logGceBootupLogs() {
        File tmpLogcat = getLocatFailureLog();
        try {
            if (tmpLogcat != null && tmpLogcat.canRead()) {
                TarUtil.extractAndLog(mLogger, tmpLogcat, "gce_logcat_bootup");
            }
            File tmpSerialLog = getSerialFailureLog();
            if (tmpSerialLog != null && tmpSerialLog.canRead()) {
                TarUtil.extractAndLog(mLogger, tmpSerialLog, "gce_serial_bootup");
            }
        } catch (IOException io) {
            CLog.e("Issue when untarring the file: %s", io);
            CLog.e(io);
        } finally {
            // Clean up logs file if any was created.
            FileUtil.deleteFile(mGceBootFailureLogCat);
            FileUtil.deleteFile(mGceBootFailureSerialLog);
        }
    }

    /**
     * Shutdown the Gce Ssh monitor process that controls the ssh bridge.
     * Using {@link #tearDown(ITestDevice, IBuildInfo, Throwable)} is the recommended approach.
     */
    public void shutdownGceSshMonitor(ITestDevice device) {
        device.stopLogcat();
        CLog.i("Shutting down GCE device %s", device.getSerialNumber());
        // Terminate SSH tunnel process.
        if (mGceSshMonitor != null) {
            mGceSshMonitor.shutdown();
            try {
                // We use a join with timeout to ensure tearDown is never blocked forever.
                mGceSshMonitor.join(DEFAULT_LONG_CMD_TIMEOUT);
            } catch (InterruptedException e1) {
                CLog.i("Interrupted while waiting for GCE SSH monitor to shutdown.");
            }
        }
        if (!device.waitForDeviceNotAvailable(DEFAULT_SHORT_CMD_TIMEOUT)) {
            CLog.w("Device %s still available after timeout.", device.getSerialNumber());
        }

        try {
            mDeviceMutator.setIDevice(device, new TcpDevice(mInitialSerial));
            mDeviceMutator.setFastbootEnabled(device, false);
        } catch (ClassCastException e1) {
            CLog.e("Invalid class");
        }
    }

    /**
     * This is a private helper class used to maintain a constant ssh tunnel to a remote GCE AVD,
     * while the remote GCE AVD is alive.
     */
    class GceSshTunnelMonitor extends Thread {
        private boolean mQuit = false;
        ITestDevice mDevice;
        HostAndPort mGceHostAndPort;
        HostAndPort mLocalHostAndPort;
        private Process mSshTunnelProcess;
        private IBuildInfo mBuildInfo;
        private int mLastUsedPort = 0;
        private boolean mSshChecked = false;

        /**
         * Ctor
         *
         * @param device {@link ITestDevice} the TF device to associate the
         *            remote GCE AVD with.
         * @param gce {@link HostAndPort} of the remote GCE AVD.
         */
        GceSshTunnelMonitor(ITestDevice device, IBuildInfo buildInfo, HostAndPort gce) {
            super("GceSshTunnelMonitor");
            setDaemon(true);
            mDevice = device;
            mGceHostAndPort = gce;
            mBuildInfo = buildInfo;
        }

        /**
         * Shutdown method, used to terminate our monitor.
         */
        public void shutdown() {
            mQuit = true;
            closeConnection();
            interrupt();
        }

        /**
         * Terminate the connection with tunnel and the tunnel itself, but does not interrupt.
         */
        public void closeConnection() {
            // shutdown adb connection first, if we reached where there could be a connection
            if (mLocalHostAndPort != null) {
                if (!adbTcpDisconnect(mLocalHostAndPort.getHostText(),
                        mLocalHostAndPort.getPort())) {
                    CLog.d("Failed to disconnect from local host %s",
                            mLocalHostAndPort.toString());
                }
            }
            if (mSshTunnelProcess != null) {
                mSshTunnelProcess.destroy();
                try {
                    boolean res = mSshTunnelProcess.waitFor(
                            DEFAULT_SHORT_CMD_TIMEOUT, TimeUnit.MILLISECONDS);
                    if (!res) {
                        CLog.e("SSH tunnel may not have properly terminated.");
                    }
                } catch (InterruptedException e) {
                    CLog.e("SSH tunnel interrupted during shutdown: %s", e.getMessage());
                }
            }
        }

        private void checkSshKey() {
            if (!mSshPrivateKeyPath.canRead()) {
                if (mSshChecked) {
                    // The key was available before and for some reason is not now.
                    RunUtil.getDefault().sleep(DEFAULT_SHORT_CMD_TIMEOUT);
                    if (mSshPrivateKeyPath.canRead()) {
                        CLog.w("ssh key was not available for a temporary period of time.");
                        // TODO: Add metric logging
                        return;
                    }
                }
                throw new RuntimeException(
                        String.format(
                                "Ssh private key is unavailable %s",
                                mSshPrivateKeyPath.getAbsolutePath()));
            }
            mSshChecked = true;
        }

        /**
         * Perform some initial setup on the GCE AVD.
         */
        void initGce() {
            checkSshKey();
            // HACK: stop/start adbd first, otherwise device seems to be in
            // offline mode.
            String cmd = String.format(SSH_CMD,
                    mSshPrivateKeyPath.getAbsolutePath(),
                    "" /* no options */,
                    mGceHostAndPort.getHostText(),
                    "stop adbd");
            CLog.d("Running %s", cmd);
            CommandResult result = getRunUtil().runTimedCmdSilentlyRetry(DEFAULT_SHORT_CMD_TIMEOUT,
                    RETRY_INTERVAL_MS,
                    MAX_RETRIES,
                    cmd.split("\\s+"));
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                CLog.w("failed to stop adbd %s", result);
                throw new RuntimeException("failed to stop adbd");
            }
            cmd = String.format(SSH_CMD,
                    mSshPrivateKeyPath.getAbsolutePath(),
                    "" /* no options */,
                    mGceHostAndPort.getHostText(),
                    "start adbd");
            result = getRunUtil().runTimedCmdSilentlyRetry(DEFAULT_SHORT_CMD_TIMEOUT,
                    RETRY_INTERVAL_MS,
                    MAX_RETRIES,
                    cmd.split("\\s+"));
            CLog.d("Running %s", cmd);
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                CLog.w("failed to start adbd", result);
                throw new RuntimeException("failed to start adbd");
            }
        }

        @Override
        public void run() {
            while (!mQuit) {
                try {
                    initGce();
                } catch (RuntimeException e) {
                    mErrorFromTunnel = e.getMessage();
                    CLog.e(
                            "Failed to init remote GCE. Terminating tunnel due to: '%s'",
                            mErrorFromTunnel);
                    CLog.e(e);
                    return;
                }
                mSshTunnelProcess = createSshTunnel(
                        mDevice, mGceHostAndPort.getHostText(),
                        mGceHostAndPort.getPortOrDefault(DEFAULT_ADB_PORT));
                if (mSshTunnelProcess == null) {
                    mErrorFromTunnel = "Failed creating the ssh tunnel to GCE.";
                    CLog.e(mErrorFromTunnel);
                    return;
                }
                // Device serial should contain tunnel host and port number.
                getRunUtil().sleep(WAIT_FOR_FIRST_CONNECT);
                // Checking if it is actually running.
                if (mSshTunnelProcess.isAlive()) {
                    mLocalHostAndPort = HostAndPort.fromString(mDevice.getSerialNumber());
                    if (!adbTcpConnect(mLocalHostAndPort.getHostText(),
                            mLocalHostAndPort.getPort())) {
                        closeConnection();
                        CLog.d("adb connect failed, init sequence will restart.");
                        continue;
                    }
                    try {
                        mSshTunnelProcess.waitFor();
                    } catch (InterruptedException e) {
                        CLog.d("SSH tunnel terminated %s", e.getMessage());
                    }
                    CLog.d("Reached end of loop, tunnel is going to re-init.");
                } else {
                    CLog.e("SSH Tunnel is not alive after starting it. It must have returned.");
                }
            }
        }

        /**
         * Create an ssh tunnel to a given remote host and assign the endpoint
         * to a device.
         *
         * @param device {@link ITestDevice} to which we want to associate this
         *            ssh tunnel.
         * @param remoteHost the hostname/ip of the remote tcp ip Android
         *            device.
         * @param remotePort the port of the remote tcp ip device.
         * @return {@link Process} of the ssh command.
         */
        Process createSshTunnel(ITestDevice device, String remoteHost, int remotePort) {
            try {
                ServerSocket s = null;
                try {
                    s = new ServerSocket(mLastUsedPort);
                } catch (SocketException se) {
                    // if we fail to allocate the previous port, we take a random available one.
                    s = new ServerSocket(0);
                    CLog.w("Our previous port: %s was already in use, switching to: %s",
                            mLastUsedPort, s.getLocalPort());
                }
                // even after being closed, socket may remain in TIME_WAIT state
                // reuse address allows to connect to it even in this state.
                s.setReuseAddress(true);
                mLastUsedPort = s.getLocalPort();
                String serial = String.format(DEFAULT_LOCAL_HOST, mLastUsedPort);
                CLog.d("Setting device %s serial to %s", mInitialSerial, serial);
                mDeviceMutator.setIDevice(device, new TcpDevice(serial));
                mDeviceMutator.setFastbootEnabled(device, false);
                mBuildInfo.setDeviceSerial(serial);
                s.close();
                // Note there is a race condition here. between when we close
                // the server socket and when we try to connect to the tunnel.
                Process p = getRunUtil().runCmdInBackground(String.format(SSH_CMD,
                        mSshPrivateKeyPath.getAbsolutePath(),
                        String.format(TUNNEL_PARAM, mLastUsedPort, remotePort),
                        remoteHost, "" /* no command */).split("\\s+"));
                return p;
            } catch (IOException e) {
                CLog.d("Failed to connect to remote GCE using ssh tunnel %s", e.getMessage());
            }
            return null;
        }

        /**
         * Helper method to adb connect to a given tcp ip Android device
         *
         * @param host the hostname/ip of a tcp/ip Android device
         * @param port the port number of a tcp/ip device
         * @return true if we successfully connected to the device, false
         *         otherwise.
         */
        boolean adbTcpConnect(String host, int port) {
            for (int i = 0; i < MAX_RETRIES; i++) {
                CommandResult result = getRunUtil().runTimedCmd(DEFAULT_SHORT_CMD_TIMEOUT, "adb",
                        "connect", String.format("%s:%d", host, port));
                if (CommandStatus.SUCCESS.equals(result.getStatus()) &&
                    result.getStdout().contains(ADB_SUCCESS_CONNECT_TAG)) {
                    CLog.d("adb connect output: status: %s stdout: %s stderr: %s",
                            result.getStatus(), result.getStdout(), result.getStderr());
                    return true;
                } else if (CommandStatus.SUCCESS.equals(result.getStatus()) &&
                        result.getStdout().contains(ADB_CONN_REFUSED)) {
                    // If we find "Connection Refused", we bail out directly as more connect
                    // won't help
                    return false;
                }
                CLog.d("adb connect output: status: %s stdout: %s stderr: %s, retrying.",
                        result.getStatus(), result.getStdout(), result.getStderr());
                if (!mSshTunnelProcess.isAlive()) {
                    // ensure that tunnel is still alive.
                    CLog.d("Found tunnel down during adb connect, exiting the sequence.");
                    return false;
                }
                getRunUtil().sleep((i + 1) * RETRY_INTERVAL_MS);
            }
            return false;
        }

        /**
         * Helper method to adb disconnect from a given tcp ip Android device
         *
         * @param host the hostname/ip of a tcp/ip Android device
         * @param port the port number of a tcp/ip device
         * @return true if we successfully disconnected to the device, false
         *         otherwise.
         */
        boolean adbTcpDisconnect(String host, int port) {
            CommandResult result = getRunUtil().runTimedCmd(DEFAULT_SHORT_CMD_TIMEOUT, "adb",
                    "disconnect",
                    String.format("%s:%d", host, port));
            return CommandStatus.SUCCESS.equals(result.getStatus());
        }
    }

    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mLogger = testLogger;
    }
}
