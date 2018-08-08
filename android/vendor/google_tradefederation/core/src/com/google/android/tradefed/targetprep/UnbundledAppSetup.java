// Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IAppBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.multi.IMultiTargetPreparer;
import com.android.tradefed.util.AaptParser;
import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A {@link IMultiTargetPreparer} that installs an apk and its tests from the second "device" build
 * info onto the first device.
 */
@OptionClass(alias = "ub-app-setup")
public class UnbundledAppSetup implements IMultiTargetPreparer {

    @Option(name = "install", description = "install all apks in build.")
    private boolean mInstall = true;

    @Option(
        name = "install-flag",
        description = "optional flag(s) to provide when installing apks."
    )
    private ArrayList<String> mInstallFlags = new ArrayList<>();

    @Option(
        name = "post-install-cmd",
        description = "optional post-install adb shell commands; can be repeated."
    )
    private List<String> mPostInstallCmds = new ArrayList<>();

    @Option(
        name = "post-install-cmd-timeout",
        description =
                "max time allowed in ms for a post-install adb shell command."
                        + "DeviceUnresponsiveException will be thrown if it is timed out."
    )
    private long mPostInstallCmdTimeout = 2 * 60 * 1000; // default to 2 minutes

    @Option(
        name = "check-min-sdk",
        description =
                "check app's min sdk prior to install and skip if device api level is too low."
    )
    private boolean mCheckMinSdk = false;

    @Option(
        name = "device-label",
        description = "the label for the device to flash and install apks to."
    )
    private String mDeviceLabel = "device";

    @Option(
        name = "app-label",
        description = " the label for the null-device used to store the unbundled app information."
    )
    private String mAppLabel = "ub_app";

    @SuppressWarnings("deprecation")
    @Override
    public void setUp(IInvocationContext context)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        Map<ITestDevice, IBuildInfo> deviceBuildInfo = context.getDeviceBuildMap();
        if (deviceBuildInfo.isEmpty()) {
            throw new TargetSetupError("No device build info available.");
        }
        // Device 1 is deemed to be the actual device.
        ITestDevice device = context.getDevice(mDeviceLabel);
        // Do some sanity checks.
        if (device == null) {
            throw new TargetSetupError(
                    String.format(
                            "The UnbundledAppSetup did not find the device labelled %s",
                            mDeviceLabel));
        }
        if (context.getDevice(mAppLabel) == null) {
            throw new TargetSetupError(
                    String.format(
                            "The UnbundledAppSetup did not find the device labelled %s",
                            mAppLabel));
        }
        if (deviceBuildInfo.entrySet().size() != 2) {
            throw new TargetSetupError(
                    "The UnbundledAppSetup assumes 2 devices only.", device.getDeviceDescriptor());
        }
        if (!(context.getBuildInfo(mAppLabel) instanceof IAppBuildInfo)) {
            throw new TargetSetupError(
                    String.format("The %s device is not a AppBuildInfo.", mAppLabel),
                    device.getDeviceDescriptor());
        }
        IAppBuildInfo appBuild = (IAppBuildInfo) context.getBuildInfo(mAppLabel);

        if (mInstall) {
            installApks(device, appBuild);
        }

        if (!mPostInstallCmds.isEmpty()) {
            executeCommands(device, mPostInstallCmds);
        }
    }

    /**
     * Helper method to run commands on device.
     *
     * @param device device on which to execute shell commands.
     * @param Commands to execute on device
     * @throws DeviceNotAvailableException
     */
    private void executeCommands(ITestDevice device, List<String> Commands)
            throws DeviceNotAvailableException {
        for (String cmd : Commands) {
            // If the command had any output, the executeShellCommand method
            // will log it at the
            // VERBOSE level; so no need to do any logging from here.
            CLog.d("About to run setup command on device %s: %s", device.getSerialNumber(), cmd);
            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
            device.executeShellCommand(
                    cmd, receiver, mPostInstallCmdTimeout, TimeUnit.MILLISECONDS, 1);
            CLog.d("Output for cmd: %s", receiver.getOutput());
        }
    }

    /**
     * Helper method to install apks to the device.
     *
     * @param device device on which to install apks
     * @param appBuild {@link IAppBuildInfo} to fetch apks to install from.
     * @throws TargetSetupError if installation fails
     * @throws DeviceNotAvailableException if device goes offline
     * @throws BuildError
     */
    private void installApks(ITestDevice device, IAppBuildInfo appBuild)
            throws TargetSetupError, DeviceNotAvailableException, BuildError {
        for (VersionedFile apkFile : appBuild.getAppPackageFiles()) {
            if (mCheckMinSdk) {
                AaptParser aaptParser = doAaptParse(apkFile.getFile());
                if (aaptParser == null) {
                    throw new TargetSetupError(
                            String.format(
                                    "Failed to extract info from '%s' using aapt",
                                    apkFile.getFile().getName()),
                            device.getDeviceDescriptor());
                }
                if (device.getApiLevel() < aaptParser.getSdkVersion()) {
                    CLog.w(
                            "Skipping installing apk %s on device %s because "
                                    + "SDK level require is %d, but device SDK level is %d",
                            apkFile.toString(),
                            device.getSerialNumber(),
                            aaptParser.getSdkVersion(),
                            device.getApiLevel());
                    continue;
                }
            }
            String result =
                    device.installPackage(
                            apkFile.getFile(),
                            true,
                            mInstallFlags.toArray(new String[mInstallFlags.size()]));
            if (result != null) {
                // typically install failures means something is wrong with apk.
                // TODO: in future add more logic to throw targetsetup vs build
                // vs
                // devicenotavail depending on error code
                throw new BuildError(
                        String.format(
                                "Failed to install %s on %s. Reason: %s",
                                apkFile.getFile().getName(), device.getSerialNumber(), result),
                        device.getDeviceDescriptor());
            }
        }
    }

    /** Helper to parse an apk file with aapt. */
    @VisibleForTesting
    AaptParser doAaptParse(File apkFile) {
        return AaptParser.parse(apkFile);
    }
}
