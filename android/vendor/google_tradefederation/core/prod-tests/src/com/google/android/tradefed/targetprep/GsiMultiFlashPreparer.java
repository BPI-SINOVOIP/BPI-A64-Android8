// Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.BuildSerializedVersion;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.IDeviceFlasher.UserDataFlashOption;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.multi.IMultiTargetPreparer;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;
import com.android.tradefed.util.ZipUtil2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@link IMultiTargetPreparer} that combines a target image with a system image and flashes the
 * device.
 */
@OptionClass(alias = "gsi-multi-preparer")
public class GsiMultiFlashPreparer implements IMultiTargetPreparer {
    @Option(
        name = "post-install-cmd",
        description = "optional post-install adb shell commands; can be repeated."
    )
    private List<String> mPostInstallCmds = new ArrayList<>();

    @Option(
        name = "post-install-cmd-timeout",
        isTimeVal = true,
        description =
                "max time allowed in ms for a post-install adb shell command."
                        + "DeviceUnresponsiveException will be thrown if it is timed out."
    )
    private long mPostInstallCmdTimeout = 2 * 60 * 1000; // default to 2 minutes

    @Option(name = "device-label", description = "the label for the device to flash.")
    private String mDeviceLabel = "device";

    @Option(name = "disable", description = "Disable the device flasher.")
    private boolean mDisable = false;

    @Option(
        name = "system-label",
        description = "the label for the null-device used to store the system image information."
    )
    private String mSystemLabel = "system_build";

    @Option(
        name = "concurrent-flasher-limit",
        description =
                "The maximum number of concurrent flashers (may be useful to avoid memory"
                        + "constraints) This will be overriden if one is set in the host options."
    )
    private Integer mConcurrentFlasherLimit = null;

    @Option(
        name = "device-boot-time",
        description = "max time in ms to wait for device to boot.",
        isTimeVal = true
    )
    private long mDeviceBootTime = 5 * 60 * 1000l;

    @Override
    public void setUp(IInvocationContext context)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (mDisable) {
            CLog.i("Skipping device flashing.");
            return;
        }

        ITestDevice device = context.getDevice(mDeviceLabel);
        ITestDevice nullDevice = context.getDevice(mSystemLabel);

        IBuildInfo deviceBuild = context.getBuildInfo(device);
        IBuildInfo systemBuild = context.getBuildInfo(nullDevice);

        IBuildInfo gsiBuild = null;

        try {
            gsiBuild = createGsiBuild(deviceBuild, systemBuild);

            GoogleDeviceFlashPreparer devicePrep = new GoogleDeviceFlashPreparer();
            // GSI requires wiping userdata or the device may not boot.
            devicePrep.setUserDataFlashOption(UserDataFlashOption.WIPE);
            // GSI changes the product flavor to aosp_arm64_ab regardless of device flavor.
            OptionSetter optionSetter = new OptionSetter(devicePrep);
            optionSetter.setOptionValue("skip-pre-flash-product-check", "true");
            if (mConcurrentFlasherLimit != null) {
                optionSetter.setOptionValue(
                        "concurrent-flasher-limit", mConcurrentFlasherLimit.toString());
            }
            optionSetter.setOptionValue("device-boot-time", Long.toString(mDeviceBootTime));
            // Flash the new build.
            devicePrep.setUp(device, gsiBuild);

            if (!mPostInstallCmds.isEmpty()) {
                executeCommands(device, mPostInstallCmds);
            }
        } catch (IOException e) {
            CLog.e(e);
            throw new BuildError("Could not create GSI build", device.getDeviceDescriptor());
        } catch (ConfigurationException e) {
            throw new TargetSetupError(
                    "Could not configure device", e, device.getDeviceDescriptor());
        } finally {
            if (gsiBuild != null) {
                gsiBuild.cleanUp();
            }
        }
    }

    /** Helper to create GSI IBuildInfo. */
    public static IBuildInfo createGsiBuild(IBuildInfo deviceBuild, IBuildInfo systemBuild)
            throws IOException {
        IDeviceBuildInfo deviceBuildInfo = (IDeviceBuildInfo) deviceBuild;
        DeviceBuildInfo systemBuildInfo = (DeviceBuildInfo) systemBuild;

        WriteableDeviceBuildInfo gsiBuildInfo = new WriteableDeviceBuildInfo(deviceBuildInfo);
        gsiBuildInfo.setBuildFlavor(systemBuild.getBuildFlavor());

        File deviceBuildDir = null;
        File systemBuildDir = null;
        ArrayList<String> gsiFilenames = new ArrayList<String>(Arrays.asList("system.img"));

        try {
            deviceBuildDir =
                    ZipUtil2.extractZipToTemp(deviceBuildInfo.getDeviceImageFile(), "deviceFiles");
            ArrayList<File> deviceBuildFiles = new ArrayList<File>();

            // Get everything but the system and vbmeta image from the device build.
            for (File file : deviceBuildDir.listFiles()) {
                String filename = file.getName();
                // Only include vbmeta if included with the device build.
                if ("vbmeta.img".equals(filename)) {
                    gsiFilenames.add(filename);
                } else if (!gsiFilenames.contains(filename)) {
                    deviceBuildFiles.add(file);
                }
            }
            // Get the system image and conditionally the vbmeta from the GSI build.
            systemBuildDir =
                    ZipUtil2.extractZipToTemp(systemBuildInfo.getDeviceImageFile(), "systemFiles");
            File[] systemBuildFiles =
                    Arrays.stream(systemBuildDir.listFiles())
                            .filter(file -> gsiFilenames.contains(file.getName()))
                            .toArray(File[]::new);
            // Create new archive that includes the device files and GSI system.img.
            ArrayList<File> gsiFiles =
                    new ArrayList<File>(deviceBuildFiles.size() + systemBuildFiles.length);
            gsiFiles.addAll(deviceBuildFiles);
            gsiFiles.addAll(Arrays.asList(systemBuildFiles));
            // Pass the list of files to avoid including the parent directory name.
            File gsi = ZipUtil.createZip(gsiFiles);
            // Replace device build images with the new one.
            gsiBuildInfo.setDeviceImageFile(gsi, systemBuildInfo.getBuildId());
        } catch (IOException e) {
            throw e;
        } finally {
            FileUtil.recursiveDelete(deviceBuildDir);
            FileUtil.recursiveDelete(systemBuildDir);
        }

        return gsiBuildInfo;
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
            // will log it at the VERBOSE level; so no need to do any logging from here.
            CLog.d("About to run setup command on device %s: %s", device.getSerialNumber(), cmd);
            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
            device.executeShellCommand(
                    cmd, receiver, mPostInstallCmdTimeout, TimeUnit.MILLISECONDS, 1);
        }
    }

    /** Allow overwriting of the device image file. */
    private static class WriteableDeviceBuildInfo extends DeviceBuildInfo {
        private static final long serialVersionUID = BuildSerializedVersion.VERSION;

        public WriteableDeviceBuildInfo(IDeviceBuildInfo buildInfo) {
            super((BuildInfo) buildInfo);
        }

        /** Applies the file with the given key and allows overwriting. */
        @Override
        public void setFile(String name, File file, String version) {
            getVersionedFileMap().put(name, new VersionedFile(file, version));
        }
    }
}
