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

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A {@link ITargetPreparer} that installs one Android Wear Companion App located on x20 folder.
 *
 * All release candidates of companion app will be stored under x20 folder:
 * /teams/clockwork/apks/companion/armeabi-v7a/signed/. This target preparer will find the right
 * app to install on device based on options provided. The subfolder follows
 * "clockwork.companion_<date>_<time>_RC<rc_num>" pattern.
 */
@OptionClass(alias = "install-companionApp-apk")
public class InstallCompanionAppApkSetup extends InstallApkSetup {

    private String mApkFoundPath = null;

    @Option(name = "companion-apk-name",
            description = "The apk name to install on device.")
    private String mCompanionApkName = "ClockworkCompanionGoogleRelease.apk";

    @Option(name = "disable", description = "Disable this target preparer.")
    private Boolean mDisable = false;

    @Option(name = "rc-number",
            description = "Release candidate number. Default set to be the largest. E.g. 3. "
                    + "Ignore if version number is unset.")
    private Integer mRcNumber = null;

    @Option(name = "root-folder-path",
            description = "The absolute path of apk root folder.")
    private String mRootFolderPath =
            "/google/data/ro/teams/clockwork/apks/companion/armeabi-v7a/signed";

    @Option(name = "version-number",
            description = "The version number of Companion App to install on device. "
                    + "This should be in date YYYYMMDD format. E.g. 20161213. "
                    + "Default set to be the latest.",
            importance = Importance.ALWAYS)
    private String mVersionNumber = null;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (mDisable) {
            CLog.d("This target preparer has been disabled.");
            return;
        }
        if (mVersionNumber == null) {
            // Version number is unset, directly find the latest apk.
            mApkFoundPath = String.format("%s/latest/%s", mRootFolderPath, mCompanionApkName);
        } else {
            // Version number is set, find corresponding folder.
            Path rootFolder = Paths.get(mRootFolderPath);
            DirectoryStream.Filter<Path> filter =
                    new DirectoryStream.Filter<Path>() {
                        @Override
                        public boolean accept(Path file) {
                            return (file.getFileName().toString().contains(mVersionNumber));
                        }
                    };
            String finalLinkName = null;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootFolder, filter)) {
                if (mRcNumber == null) {
                    // If rc-number is not set, then find the maximum rc number.
                    Integer maxRcNumber = -1;
                    for (Path linkPath : stream) {
                        String linkName = linkPath.getFileName().toString();
                        // The last two characters are rc numbers.
                        Integer tempRcNumber =
                                Integer.parseInt(linkName.substring(linkName.length() - 2));
                        if (tempRcNumber > maxRcNumber) {
                            maxRcNumber = tempRcNumber;
                            finalLinkName = linkName;
                        }
                    }
                } else {
                    for (Path linkPath : stream) {
                        String linkName = linkPath.getFileName().toString();
                        if (linkName.endsWith(String.format("RC%02d", mRcNumber))) {
                            finalLinkName = linkName;
                            // there should be only one.
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                throw new TargetSetupError(
                        String.format(
                                "Failed to find folder: %s. "
                                        + "Please make sure you have the right permission. If you "
                                        + "run the test locally, a workaround is to disable this "
                                        + "target preparer by --install-companionApp-apk:disable. "
                                        + "And install a local apk through InstallApkSetup target "
                                        + "preparer.",
                                mRootFolderPath),
                        device.getDeviceDescriptor());
            }
            if (finalLinkName == null) {
                throw new TargetSetupError(
                        String.format("Failed to find %s version (RC: %s).",
                                mVersionNumber, mRcNumber), device.getDeviceDescriptor());
            }
            mApkFoundPath = String.format("%s/%s/%s", mRootFolderPath, finalLinkName,
                    mCompanionApkName);
        }
        CLog.d("Companion app apk path: %s", mApkFoundPath);
        getApkPaths().add(new File(mApkFoundPath));
        super.setUp(device, buildInfo);
    }

    @VisibleForTesting
    public String getApkFoundPath() {
        return mApkFoundPath;
    }
}
