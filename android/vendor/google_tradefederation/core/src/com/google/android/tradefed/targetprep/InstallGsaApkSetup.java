// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.InstallApkSetup;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.AbiFormatter;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A {@link ITargetPreparer} that installs one Google Search App located on x20 folder.
 *
 * <p>All release candidates of Gsa app will be stored under x20 folder: /teams/agsa-releases/. This
 * target preparer will find the right app to install on device based on options provided. The
 * subfolder follows "<version_number>.<rc_number>.release" pattern. The filename follows
 * velvet.<abi>.<api>.defaultperms.prodchan.alldpi.release.pgfull.mdxlegacy.apk.
 */
@OptionClass(alias = "install-gsa-apk")
public class InstallGsaApkSetup extends InstallApkSetup {

    private File mApkFoundPath = null;

    @Option(name = "disable", description = "Disable this target preparer.")
    private Boolean mDisable = false;

    @Option(name = "file-name-32", description = "The name prefix of apk to install on device.")
    private String mFileName_32 =
            "velvet.armeabi-v7a.21.defaultperms.prodchan.alldpi.release.pgfull.mdxlegacy.apk";

    @Option(name = "file-name-64", description = "The name prefix of apk to install on device.")
    private String mFileName_64 =
            "velvet.arm64-v8a.21.defaultperms.prodchan.alldpi.release.pgfull.mdxlegacy.apk";

    @Option(name = "rc-number",
            description = "Release candidate number. Default set to be the largest. "
                    + "E.g. 23")
    private Integer mRcNumber = null;

    @Option(name = "root-folder-path",
            description = "The absolute path for apk root folder")
    private String mRootFolderPath = "/google/data/ro/teams/agsa-releases";

    @Option(name = "version-number",
            description = "The version number of Google Search App to install on device. "
                    + "E.g. 6.3",
            mandatory = true,
            importance = Importance.ALWAYS)
    private String mVersionNumber;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (mDisable) {
            CLog.d("This target preparer has been disabled.");
            return;
        }
        if (mRcNumber == null) {
            // If rc-number is not set, then find the maximum rc number.
            Path rootFolder = Paths.get(mRootFolderPath);
            Integer maxRcNumber = -1;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootFolder)) {
                for (Path path : stream) {
                    String folderName = path.getFileName().toString();
                    if (folderName.startsWith(mVersionNumber) && folderName.endsWith("release")) {
                        Integer tempRcNumber = Integer.parseInt(folderName.split("\\.")[2]);
                        maxRcNumber = tempRcNumber > maxRcNumber ? tempRcNumber : maxRcNumber;
                    }
                }
            } catch (IOException e) {
                CLog.e(e);
                throw new TargetSetupError(
                        String.format(
                                "Failed to find folder: %s. "
                                        + "Please make sure you have the right permission. "
                                        + "If you run the test locally, a workaround is to disable "
                                        + "this target preparer by --install-gsa-apk:disable. And "
                                        + "install a local apk through InstallApkSetup "
                                        + "target preparer.",
                                mRootFolderPath),
                        device.getDeviceDescriptor());
            }
            if (maxRcNumber == -1) {
                throw new TargetSetupError(
                        String.format("Failed to find %s version.", mVersionNumber),
                        device.getDeviceDescriptor());
            }
            mRcNumber = maxRcNumber;
        }
        String folderPath = String.format("%s/%s.%s.release/",
                mRootFolderPath, mVersionNumber, mRcNumber);
        CLog.d(String.format("The folder path is: %s", folderPath));

        // Get name for its subfolder.
        File folder = new File(folderPath);
        if (!folder.isDirectory()) {
            throw new TargetSetupError(String.format("Failed to find %s version with %s rc number.",
                    mVersionNumber, mRcNumber), device.getDeviceDescriptor());
        }
        String subFolderName = folder.list()[0];
        String parentFolderPath = String.format("%s%s", folderPath, subFolderName);
        CLog.d(String.format("The parent folder path is: %s", parentFolderPath));

        // Find file name.
        String fileAbsolutePath = null;
        Path parentFolder = Paths.get(parentFolderPath);
        String fileName = checkIf64Supported(device) ? mFileName_64 : mFileName_32;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentFolder)) {
            for (Path path : stream) {
                if (path.getFileName().toString().equals(fileName)) {
                    CLog.i(String.format("The filename is: %s", fileName));
                    fileAbsolutePath = String.format("%s/%s", parentFolder.toString(), fileName);
                    // There should be only one of them.
                    break;
                }
            }
        } catch (IOException e) {
            CLog.e(e);
            throw new TargetSetupError(
                    String.format("Failed to find folder: %s.", parentFolderPath),
                    device.getDeviceDescriptor());
        }
        if (fileAbsolutePath == null) {
            throw new TargetSetupError(
                    String.format("Failed to find apk under folder: %s", parentFolderPath),
                    device.getDeviceDescriptor());
        }
        mApkFoundPath = new File(fileAbsolutePath);
        getApkPaths().add(mApkFoundPath);
        super.setUp(device, buildInfo);
    }

    // Utility method to check if the device support 64-bit abi.
    private boolean checkIf64Supported(ITestDevice device) throws DeviceNotAvailableException {
        return AbiFormatter.getDefaultAbi(device, "64").contains("64");
    }

    @VisibleForTesting
    public File getApkFoundPath() {
        return mApkFoundPath;
    }
}
