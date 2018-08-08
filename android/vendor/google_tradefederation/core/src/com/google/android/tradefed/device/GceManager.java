// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.android.tradefed.util.GceAvdInfo;
import com.google.android.tradefed.util.GceRemoteCmdFormatter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HostAndPort;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Helper that manages the GCE calls to start/stop and collect logs from GCE. */
public class GceManager {
    private static final int DEFAULT_ADB_PORT = 5555;
    private static final long BUGREPORT_TIMEOUT = 15 * 60 * 1000;
    private static final Pattern BUGREPORTZ_RESPONSE_PATTERN = Pattern.compile("(OK:)(.*)");

    private DeviceDescriptor mDeviceDescriptor;
    private GceAvdTestDeviceOptions mDeviceOptions;
    private IBuildInfo mBuildInfo;
    private File mGceBootFailureLogCat = null;
    private File mGceBootFailureSerialLog = null;

    private String mGceInstanceName = null;
    private String mGceHost = null;
    private GceAvdInfo mGceAvdInfo = null;

    /**
     * Ctor
     *
     * @param deviceDesc The {@link DeviceDescriptor} that will be associated with the GCE device.
     * @param deviceOptions A {@link GceAvdTestDeviceOptions} associated with the device.
     * @param buildInfo A {@link IBuildInfo} describing the gce build to start.
     */
    public GceManager(
            DeviceDescriptor deviceDesc,
            GceAvdTestDeviceOptions deviceOptions,
            IBuildInfo buildInfo) {
        mDeviceDescriptor = deviceDesc;
        mDeviceOptions = deviceOptions;
        mBuildInfo = buildInfo;
    }

    /**
     * Ctor, variation that can be used to provide the GCE instance name to use directly.
     *
     * @param deviceDesc The {@link DeviceDescriptor} that will be associated with the GCE device.
     * @param deviceOptions A {@link GceAvdTestDeviceOptions} associated with the device
     * @param buildInfo A {@link IBuildInfo} describing the gce build to start.
     * @param gceInstanceName The instance name to use.
     * @param gceHost The host name or ip of the instance to use.
     */
    public GceManager(
            DeviceDescriptor deviceDesc,
            GceAvdTestDeviceOptions deviceOptions,
            IBuildInfo buildInfo,
            String gceInstanceName,
            String gceHost) {
        this(deviceDesc, deviceOptions, buildInfo);
        mGceInstanceName = gceInstanceName;
        mGceHost = gceHost;
    }

    /**
     * Attempt to start a gce instance
     *
     * @return a {@link GceAvdInfo} describing the GCE instance. Could be a BOOT_FAIL instance.
     * @throws TargetSetupError
     */
    public GceAvdInfo startGce() throws TargetSetupError {
        mGceAvdInfo = null;
        // For debugging purposes bypass.
        if (mGceHost != null && mGceInstanceName != null) {
            mGceAvdInfo =
                    new GceAvdInfo(
                            mGceInstanceName,
                            HostAndPort.fromString(mGceHost).withDefaultPort(DEFAULT_ADB_PORT));
            return mGceAvdInfo;
        }
        List<String> gceArgs = ArrayUtil.list(getTestDeviceOptions().getAvdDriverBinary());
        gceArgs.add("create");
        // Add extra args.
        File reportFile = null;
        try {
            reportFile = FileUtil.createTempFile("gce_avd_driver", ".json");
            gceArgs = buildGceCmd(reportFile, mBuildInfo);

            CLog.i("Launching GCE with %s", gceArgs.toString());
            CommandResult cmd =
                    getRunUtil()
                            .runTimedCmd(
                                    getTestDeviceOptions().getGceCmdTimeout(),
                                    gceArgs.toArray(new String[gceArgs.size()]));
            CLog.i("GCE driver stderr: %s", cmd.getStderr());
            String instanceName = extractInstanceName(cmd.getStderr());
            if (instanceName != null) {
                mBuildInfo.addBuildAttribute("gce-instance-name", instanceName);
            } else {
                CLog.w("Could not extract an instance name for the gce device.");
            }
            if (CommandStatus.TIMED_OUT.equals(cmd.getStatus())) {
                throw new TargetSetupError(
                        String.format(
                                "acloud errors: timeout after %dms, " + "acloud did not return",
                                getTestDeviceOptions().getGceCmdTimeout()),
                        mDeviceDescriptor);
            } else if (!CommandStatus.SUCCESS.equals(cmd.getStatus())) {
                CLog.w("Error when booting the Gce instance, reading output of gce driver");
                mGceAvdInfo = GceAvdInfo.parseGceInfoFromFile(reportFile, mDeviceDescriptor);
                String errors = "";
                if (mGceAvdInfo != null) {
                    // We always return the GceAvdInfo describing the instance when possible
                    // The called can decide actions to be taken.
                    return mGceAvdInfo;
                } else {
                    errors =
                            "Could not get a valid instance name, check the gce driver's output."
                                    + "The instance may not have booted up at all.";
                    CLog.e(errors);
                    throw new TargetSetupError(
                            String.format("acloud errors: %s", errors), mDeviceDescriptor);
                }
            }
            mGceAvdInfo = GceAvdInfo.parseGceInfoFromFile(reportFile, mDeviceDescriptor);
            return mGceAvdInfo;
        } catch (IOException e) {
            throw new TargetSetupError("failed to create log file", e, mDeviceDescriptor);
        } finally {
            FileUtil.deleteFile(reportFile);
        }
    }

    /**
     * Retrieve the instance name from the gce boot logs. Search for the 'name': 'gce-<name>'
     * pattern to extract the name of it. We extract from the logs instead of result file because on
     * gce boot failure, the attempted instance name won't show in json.
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

    /** Build and return the command to launch GCE. Exposed for testing. */
    protected List<String> buildGceCmd(File reportFile, IBuildInfo b) throws IOException {
        List<String> gceArgs = ArrayUtil.list(getTestDeviceOptions().getAvdDriverBinary());
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
        gceArgs.add(getTestDeviceOptions().getAvdConfigFile());
        gceArgs.add("--report_file");
        gceArgs.add(reportFile.getAbsolutePath());
        switch (getTestDeviceOptions().getGceDriverLogLevel()) {
            case DEBUG:
                gceArgs.add("-v");
                break;
            case VERBOSE:
                gceArgs.add("-vv");
                break;
            default:
                break;
        }
        if (getTestDeviceOptions().getGceAccount() != null) {
            gceArgs.add("--email");
            gceArgs.add(getTestDeviceOptions().getGceAccount());
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

    /** Shutdown the Gce instance associated with the {@link #startGce()}. */
    public void shutdownGce() {
        if (!(new File(getTestDeviceOptions().getAvdDriverBinary()).canExecute())) {
            throw new RuntimeException(
                    String.format(
                            "GCE launcher %s is invalid",
                            getTestDeviceOptions().getAvdDriverBinary()));
        }
        List<String> gceArgs = ArrayUtil.list(getTestDeviceOptions().getAvdDriverBinary());
        gceArgs.add("delete");
        // Add extra args.
        File f = null;
        try {
            gceArgs.add("--instance_names");
            gceArgs.add(mGceAvdInfo.instanceName());
            gceArgs.add("--config_file");
            gceArgs.add(getTestDeviceOptions().getAvdConfigFile());
            f = FileUtil.createTempFile("gce_avd_driver", ".json");
            gceArgs.add("--report_file");
            gceArgs.add(f.getAbsolutePath());
            CLog.i("Tear down of GCE with %s", gceArgs.toString());
            CommandResult cmd =
                    getRunUtil()
                            .runTimedCmd(
                                    getTestDeviceOptions().getGceCmdTimeout(),
                                    gceArgs.toArray(new String[gceArgs.size()]));
            if (!CommandStatus.SUCCESS.equals(cmd.getStatus())) {
                CLog.w(
                        "Failed to tear down GCE %s with the following arg, %s",
                        mGceAvdInfo.instanceName(), gceArgs);
            }
        } catch (IOException e) {
            CLog.e("failed to create log file for GCE Teardown");
            CLog.e(e);
        } finally {
            FileUtil.deleteFile(f);
        }
    }

    /**
     * Get a bugreportz from the device using ssh to avoid any adb connection potential issue.
     *
     * @param gceAvd The {@link GceAvdInfo} that describe the device.
     * @param options a {@link GceAvdTestDeviceOptions} describing the device options to be used for
     *     the GCE device.
     * @param runUtil a {@link IRunUtil} to execute commands.
     * @return A file pointing to the zip bugreport, or null if an issue occurred.
     * @throws IOException
     */
    public static File getBugreportzWithSsh(
            GceAvdInfo gceAvd, GceAvdTestDeviceOptions options, IRunUtil runUtil)
            throws IOException {
        List<String> sshCmd =
                GceRemoteCmdFormatter.getSshCommand(
                        options.getSshPrivateKeyPath(),
                        null,
                        gceAvd.hostAndPort().getHostText(),
                        "bugreportz");
        CommandResult res = runUtil.runTimedCmd(BUGREPORT_TIMEOUT, sshCmd.toArray(new String[0]));
        if (!CommandStatus.SUCCESS.equals(res.getStatus())) {
            CLog.e("issue when attempting to get ssh bugreport:");
            CLog.e("%s", res.getStderr());
        }
        // We still attempt to find the file from bugreportz.
        String output = res.getStdout().trim();
        Matcher match = BUGREPORTZ_RESPONSE_PATTERN.matcher(output);
        if (!match.find()) {
            CLog.e("Something went went wrong during bugreportz collection: '%s'", output);
            return null;
        } else {
            String remoteFilePath = match.group(2);
            File localTmpFile = FileUtil.createTempFile("bugreport-ssh", ".zip");
            List<String> scpCmd =
                    GceRemoteCmdFormatter.getScpCommand(
                            options.getSshPrivateKeyPath(),
                            null,
                            gceAvd.hostAndPort().getHostText(),
                            remoteFilePath,
                            localTmpFile.getAbsolutePath());
            CommandResult resScp =
                    runUtil.runTimedCmd(BUGREPORT_TIMEOUT, scpCmd.toArray(new String[0]));
            if (!CommandStatus.SUCCESS.equals(resScp.getStatus())) {
                CLog.e("issue when fetching the ssh bugreport:");
                CLog.e("%s", resScp.getStderr());
                if (localTmpFile.length() != 0) {
                    // If we partially fetched something, we return it just in case.
                    return localTmpFile;
                }
                FileUtil.deleteFile(localTmpFile);
                return null;
            } else {
                return localTmpFile;
            }
        }
    }

    public void cleanUp() {
        // Clean up logs file if any was created.
        FileUtil.deleteFile(mGceBootFailureLogCat);
        FileUtil.deleteFile(mGceBootFailureSerialLog);
    }

    /** Returns the instance of the {@link IRunUtil}. */
    @VisibleForTesting
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Returns the {@link GceAvdTestDeviceOptions} associated with the device that the gce manager
     * was initialized with.
     */
    private GceAvdTestDeviceOptions getTestDeviceOptions() {
        return mDeviceOptions;
    }

    /** Returns the boot logcat of the gce instance. */
    public File getGceBootLogcatLog() {
        return mGceBootFailureLogCat;
    }

    /** Returns the boot serial log of the gce instance. */
    public File getGceBootSerialLog() {
        return mGceBootFailureSerialLog;
    }
}
