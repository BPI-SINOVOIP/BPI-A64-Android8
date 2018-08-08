// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.IDeviceFlasher;
import com.android.tradefed.targetprep.IFlashingResourcesRetriever;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.ZipUtil2;
import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.lang.IllegalStateException;
import java.util.Collection;

/**
 * Flashes AndroidThings devices using the device flashing scripts provided by the flashfiles zip.
 */
public class AndroidThingsDeviceFlasher implements IDeviceFlasher {

    // Timeout for flashall in milliseconds
    private static final long FLASH_ALL_TIMEOUT_MS = 600 * 1000;

    // Variables to set for flash-all to find images
    private static final String OS_IMGS_VAR = "ANDROID_PROVISION_OS_PARTITIONS";
    private static final String VENDOR_IMGS_VAR = "ANDROID_PROVISION_VENDOR_PARTITIONS";

    // Name of flash-all script
    private static final String FLASHALL_SCRIPT = "flash-all.sh";

    /**
     * Flash the device using the device-specific flash-all.sh script. This is used to prevent
     * duplicate logic between test and prod.
     */
    @Override
    public void flash(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws TargetSetupError, DeviceNotAvailableException {
        CLog.i(
                "Flashing device %s with build %s.",
                device.getSerialNumber(), deviceBuild.getDeviceBuildId());

        File flashfiles = findFlashfiles(device, deviceBuild);
        File flashfilesDir = extractZip(device, flashfiles);

        RunUtil rUtil = new RunUtil();
        try {
            doFlash(device, rUtil, flashfilesDir);
        } finally {
            FileUtil.recursiveDelete(flashfilesDir);
        }
    }

    /**
     * Execute device flashing (separated from flash method for testing).
     *
     * @param device the {@link ITestDevice} to flash
     * @param rUtil the {@link IRunUtil} to execute flashing commands
     * @param flashfilesDir {@link File} containing extracted flashfiles zip
     * @throws TargetSetupError if flashing script missing or fails
     * @throws DeviceNotAvailableException if device is not available
     */
    @VisibleForTesting
    protected void doFlash(ITestDevice device, IRunUtil rUtil, File flashfilesDir)
            throws TargetSetupError, DeviceNotAvailableException {
        device.rebootIntoBootloader();
        String flashfilesDirPath = flashfilesDir.getAbsolutePath();

        // Prepare a command running environment
        // Set vars for image locations and to skip flashing _b partitions
        rUtil.setEnvVariable(OS_IMGS_VAR, flashfilesDirPath);
        rUtil.setEnvVariable(VENDOR_IMGS_VAR, flashfilesDirPath);
        rUtil.setEnvVariable("FLASHB", "false");

        // Verify script exists
        String flashAllCmd = flashfilesDirPath + File.separator + FLASHALL_SCRIPT;
        if (!new File(flashAllCmd).exists()) {
            throw new TargetSetupError(
                    String.format("%s not found in flashfiles zip.", FLASHALL_SCRIPT),
                    device.getDeviceDescriptor());
        }

        // Execute flashall script
        rUtil.allowInterrupt(false);
        CommandResult cmdR =
                rUtil.runTimedCmd(
                        FLASH_ALL_TIMEOUT_MS, flashAllCmd, "-s", device.getSerialNumber());
        rUtil.allowInterrupt(true);

        CLog.d("%s stdout: %s", FLASHALL_SCRIPT, cmdR.getStdout());
        CLog.d("%s stderr: %s", FLASHALL_SCRIPT, cmdR.getStderr());
        if (cmdR.getStatus().equals(CommandStatus.TIMED_OUT)) {
            throw new TargetSetupError(
                    String.format("%s timed out.", FLASHALL_SCRIPT), device.getDeviceDescriptor());
        } else if (!cmdR.getStatus().equals(CommandStatus.SUCCESS)) {
            throw new TargetSetupError(
                    String.format("%s failed.", FLASHALL_SCRIPT), device.getDeviceDescriptor());
        }
    }

    /**
     * Find the path to the flashfiles zip.
     *
     * @param device the {@link ITestDevice}.
     * @param deviceBuild the {@link IDeviceBuildInfo} containing build artifacts
     * @throws TargetSetupError if flashfiles is not found
     */
    @VisibleForTesting
    protected File findFlashfiles(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws TargetSetupError {
        Collection<VersionedFile> files = deviceBuild.getFiles();
        File flashfiles = null;
        for (VersionedFile candidate : files) {
            if (candidate.getFile().getAbsolutePath().contains("-flashfiles-")) {
                flashfiles = candidate.getFile();
                break;
            }
        }

        if (flashfiles == null) {
            throw new TargetSetupError(
                    "No flashfiles archive in downloaded artifacts.", device.getDeviceDescriptor());
        }

        return flashfiles;
    }

    /**
     * Extract a zip file and return temporary directory with contents. Also schedules zip to be
     * deleted on exit.
     *
     * @param device the {@link ITestDevice}.
     * @param zip {@link File} to unzip
     * @throws TargetSetupError if any operation fails
     */
    File extractZip(ITestDevice device, File zip) throws TargetSetupError {
        ZipFile zFile = null;
        File outputDir;
        try {
            zFile = new ZipFile(zip);
            outputDir = FileUtil.createTempDir("ATflashfiles");
            ZipUtil2.extractZip(zFile, outputDir);
        } catch (IOException | IllegalStateException exception) {
            throw new TargetSetupError(
                    exception.getMessage(), exception, device.getDeviceDescriptor());
        } finally {
            ZipUtil2.closeZip(zFile);
            zip.deleteOnExit();
        }

        return outputDir;
    }

    /** No-op, no options to override */
    @Override
    public void overrideDeviceOptions(ITestDevice device) {}

    /** No-op, no IFlashingResourcesRetriever needed for flashing */
    @Override
    public void setFlashingResourcesRetriever(IFlashingResourcesRetriever retriever) {}

    /** No-op, only the FLASH option is implemented. */
    @Override
    public void setUserDataFlashOption(UserDataFlashOption flashOption) {}

    /** No-op, no use of ITestsZipInstaller */
    @Override
    public void setDataWipeSkipList(Collection<String> dataWipeSkiplist) {}

    /** Return UserDataFlashOption.FLASH as userdata is always flashed. */
    @Override
    public UserDataFlashOption getUserDataFlashOption() {
        return UserDataFlashOption.FLASH;
    }

    /** No-op, wiping is part of flash-all script and governed by a single timeout. */
    @Override
    public void setWipeTimeout(long timeout) {}

    /** No-op, system is always flashed. */
    @Override
    public void setForceSystemFlash(boolean forceSystemFlash) {}
}
