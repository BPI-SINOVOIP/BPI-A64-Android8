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
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link ITargetPreparer} that installs one GMS Core apk located on x20 folder.
 *
 * <p>All release candidates of GMS Core will be stored under x20 folder:
 * /teams/gmscore/gmscore_apks/releases. This target preparer will find the right app to install on
 * device based on options provided. The apk parent folder path follows
 * "v<version_number>/gmscore_v<version_number>_RC<rc_number>" pattern.
 *
 * <p>In the meanwhile, this target preparer also support installing GMS Core daily build. All daily
 * build apks com from /teams/gmscore/gmscore_apks/daily folder. The apk parent folder follows
 * "gmscore_apks_<date>.<number>_RC<rc_number>" pattern.
 *
 * <p>In addition, it supports auth module sideloading on top of GMS Core apk. All auth modules are
 * in /teams/gmscore/modules/auth folder. The apk parent folder follows
 * "gms.p.auth_<date>.<number>_RC<rc_number>" pattern.
 */
@OptionClass(alias = "install-gms-apk")
public class InstallGmsCoreApkSetup extends InstallApkSetup {

    @Option(
        name = "auth-module",
        description = "Whether to sideload auth module on top of GMS Core. "
    )
    private Boolean mAuthModule = false;

    @Option(
        name = "auth-module-date",
        description =
                "Date of auth module build. Should be in format YYYYMMDD. "
                        + "E.g. 20161213. Will be ignored if auth-module set to be false. "
                        + "Default set to be the latest."
    )
    private String mAuthModuleDate = null;

    @Option(
        name = "auth-module-file-name-prefix",
        description =
                "Filename prefix for auth module apk. "
                        + "Will be ignored if auth-module set to be false."
    )
    private String mAuthModuleFilenamePrefix = "module_policy_auth_prodmnc_x86_release-x86-";

    @Option(name = "auth-module-folder-path", description = "Folder path for auth module apk.")
    private String mAuthModuleFolderPath = "/google/data/ro/teams/gmscore/modules/auth";

    @Option(
        name = "auth-module-rc-number",
        description =
                "Release candidate number for auth module. E.g. 1. "
                        + "Will be ignored if auth-module set to be false. "
                        + "Default set to be the latest."
    )
    private Integer mAuthModuleRcNumber = null;

    @Option(
        name = "daily-build",
        description = "Whether to use daily build instead of release build."
    )
    private Boolean mDailyBuild = false;

    @Option(
        name = "daily-build-date",
        description =
                "Date of daily GMS Core build. Should be in format YYYYMMDD. "
                        + "E.g. 20161213. Will be ignored if daily-build set to be false. "
                        + "Default set to be the latest."
    )
    private String mDailyBuildDate = null;

    @Option(
        name = "daily-build-folder-path",
        description = "Folder path for all GMS Core daily build apks."
    )
    private String mDailyBuildFolderPath = "/google/data/ro/teams/gmscore/gmscore_apks/daily";

    @Option(name = "disable", description = "Disable this target preparer.")
    private Boolean mDisable = false;

    @Option(name = "file-name-prefix-32", description = "Filename prefix for 32 bit devices.")
    private String mFileNamePrefix32 = "GmsCore_prodmnc_armv7_alldpi_release-armeabi-v7a-gmscore_";

    @Option(name = "file-name-prefix-64", description = "Filename prefix for 64 bit devices.")
    private String mFileNamePrefix64 = "GmsCore_prodmnc_arm64_alldpi_release-arm64-v8a-gmscore_";

    @Option(
        name = "file-name-prefix-lmp-32",
        description = "Filename prefix for 32 bit devices, which on or below Lollipop."
    )
    private String mFileNamePrefixLmp32 = "GmsCore_prod_armv7_alldpi_release-armeabi-v7a-gmscore_";

    @Option(
        name = "rc-number",
        description =
                "Release candidate number for GMS Core apk, for release or daily build. "
                        + "E.g. 2. Default set to be the largest RC number."
    )
    private Integer mRcNumber = null;

    @Option(
        name = "release-folder-path",
        description = "Folder path for all release GMS Core apks."
    )
    private String mReleaseFolderPath = "/google/data/ro/teams/gmscore/gmscore_apks/releases";

    @Option(
        name = "version-number",
        description =
                "Version number of GMS Core apk. E.g. v8.1."
                        + "Required if to find the release version.",
        importance = Importance.ALWAYS
    )
    private String mVersionNumber = null;

    private List<String> mApkFoundPathList = new ArrayList<>();

    private static final Pattern FOLDER_NAME = Pattern.compile(".*?RC[0-9][0-9]");

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (mDisable) {
            CLog.d("This target preparer has been disabled.");
            return;
        }
        Path gmsCoreApkFolderPath = null;
        if (mDailyBuild) {
            Path dailyBuildRootPath = Paths.get(mDailyBuildFolderPath);
            if (mDailyBuildDate == null) {
                mDailyBuildDate = traverseDirAndFindLargestVersion(dailyBuildRootPath, device);
            }
            gmsCoreApkFolderPath =
                    traverseDirAndFilter(dailyBuildRootPath, mDailyBuildDate, mRcNumber, device);
        } else {
            if (mVersionNumber == null) {
                throw new TargetSetupError(
                        "Version number needs to be specified if you want to "
                                + "install a release version of GMS Core apk.",
                        device.getDeviceDescriptor());
            }
            Path releaseRootPath =
                    Paths.get(String.format("%s/%s", mReleaseFolderPath, mVersionNumber));
            gmsCoreApkFolderPath =
                    traverseDirAndFilter(releaseRootPath, mVersionNumber, mRcNumber, device);
        }
        Boolean ifLollipop = checkIfBelowOrEqualLollipop(device);
        Boolean if64Supported = checkIf64Supported(device);
        String apkPrefixString =
                ifLollipop
                        ? mFileNamePrefixLmp32
                        : (if64Supported ? mFileNamePrefix64 : mFileNamePrefix32);
        Path gmsCoreApkPath = traverseDirAndFindApk(gmsCoreApkFolderPath, apkPrefixString, device);
        mApkFoundPathList.add(gmsCoreApkPath.toString());
        getApkPaths().add(gmsCoreApkPath.toFile());
        if (mAuthModule) {
            Path authModuleRootFolderPath = Paths.get(mAuthModuleFolderPath);
            if (mAuthModuleDate == null) {
                mAuthModuleDate =
                        traverseDirAndFindLargestVersion(authModuleRootFolderPath, device);
            }
            Path authModuleFolderPath =
                    traverseDirAndFilter(
                            authModuleRootFolderPath, mAuthModuleDate, mAuthModuleRcNumber, device);
            Path authModuleApkPath =
                    traverseDirAndFindApk(authModuleFolderPath, mAuthModuleFilenamePrefix, device);
            mApkFoundPathList.add(authModuleApkPath.toString());
            getApkPaths().add(authModuleApkPath.toFile());
        }
        CLog.d("GmsCore Apks to install: %s", mApkFoundPathList.toString());
        super.setUp(device, buildInfo);
    }

    /**
     * Utility method to traverse a path, do filter on folder name, and select rc number.
     *
     * @param dir {@link Path} object of root directory.
     * @param filterString String to filter the subfolder names.
     * @param rcNumber if Null then find the largest rc number.
     * @param device Test device instance.
     * @return {@link Path} object of the parent directory which contains apk.
     */
    Path traverseDirAndFilter(Path dir, String filterString, Integer rcNumber, ITestDevice device)
            throws TargetSetupError {
        Path resultPath = null;
        DirectoryStream.Filter<Path> filter =
                new DirectoryStream.Filter<Path>() {
                    @Override
                    public boolean accept(Path file) {
                        String fileName = file.getFileName().toString();
                        return (fileName.contains(filterString)
                                && FOLDER_NAME.matcher(fileName).matches());
                    }
                };
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filter)) {
            if (rcNumber != null) {
                // Directly find the correct rc number
                String rcString = String.format("RC%02d", rcNumber);
                for (Path subfolder : stream) {
                    if (subfolder.getFileName().toString().contains(rcString)) {
                        resultPath = subfolder;
                        // There should be only one.
                        break;
                    }
                }
            } else {
                // Search to find the largest rc number
                Path largestRcPath = null;
                Integer largestRcNumber = -1;
                for (Path subfolder : stream) {
                    String subfolderString = subfolder.getFileName().toString();
                    Integer tempRcNumber =
                            Integer.parseInt(
                                    subfolderString.substring(subfolderString.length() - 2));
                    if (tempRcNumber > largestRcNumber) {
                        largestRcNumber = tempRcNumber;
                        largestRcPath = subfolder;
                    }
                }
                resultPath = largestRcPath;
            }
        } catch (IOException e) {
            CLog.e(e);
            throwFolderNotFoundError(dir, device);
        }
        if (resultPath == null) {
            throw new TargetSetupError(
                    String.format("Failed to find folder with rc number: %s.", rcNumber),
                    device.getDeviceDescriptor());
        }
        return resultPath;
    }

    /**
     * Utility method to traverse directory and find apk file with specified prefix.
     *
     * @param dir {@link Path} object of apk parent directory.
     * @param filenamePrefix prefix string of apk to install.
     * @param device Test device instance.
     * @return {@link Path} object of the apk.
     */
    Path traverseDirAndFindApk(Path dir, String filenamePrefix, ITestDevice device)
            throws TargetSetupError {
        Path resultPath = null;
        DirectoryStream.Filter<Path> filter =
                new DirectoryStream.Filter<Path>() {
                    @Override
                    public boolean accept(Path file) {
                        return (file.getFileName().toString().startsWith(filenamePrefix));
                    }
                };
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filter)) {
            // There should be only one file.
            try {
                resultPath = stream.iterator().next();
            } catch (NoSuchElementException e) {
                throw new TargetSetupError(
                        String.format(
                                "Failed to find apk with prefix: %s in folder: %s.",
                                filenamePrefix, dir.toString()),
                        device.getDeviceDescriptor());
            }
        } catch (IOException e) {
            CLog.e(e);
            throwFolderNotFoundError(dir, device);
        }
        return resultPath;
    }

    /**
     * Utility method to traverse folder and find the maximum version number
     *
     * @param dir {@link Path} object of directory.
     * @param device Test device instance.
     * @return String of maximum version number.
     */
    String traverseDirAndFindLargestVersion(Path dir, ITestDevice device) throws TargetSetupError {
        String resultString = null;
        String patternString = ".*?(\\d+)\\.(\\d+)_RC.*";
        Integer maxDateIndex = 0;
        Pattern p = Pattern.compile(patternString);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path dirPath : stream) {
                Matcher m = p.matcher(dirPath.getFileName().toString());
                if (m.find()) {
                    Integer dateIndex =
                            Integer.parseInt(m.group(1)) * 100 + Integer.parseInt(m.group(2));
                    if (dateIndex > maxDateIndex) {
                        maxDateIndex = dateIndex;
                        resultString = String.format("%s.%s", m.group(1), m.group(2));
                    }
                }
            }
        } catch (IOException e) {
            CLog.e(e);
            throwFolderNotFoundError(dir, device);
        }
        if (resultString == null) {
            throw new TargetSetupError(
                    String.format(
                            "Failed to find largest version under folder: %s. ", dir.toString()),
                    device.getDeviceDescriptor());
        }
        return resultString;
    }

    /**
     * Utility method to check if the device support 64-bit abi.
     *
     * @param device device to be tested.
     * @return true if device support 64 bit, false if not.
     */
    boolean checkIf64Supported(ITestDevice device) throws DeviceNotAvailableException {
        return AbiFormatter.getDefaultAbi(device, "64").contains("64");
    }

    /**
     * Utility method to check if the device use api level lower or equal to 21.
     *
     * @param device device to be tested.
     * @return true if device is below or equal to version Lollipop.
     */
    boolean checkIfBelowOrEqualLollipop(ITestDevice device) throws DeviceNotAvailableException {
        return device.getApiLevel() <= 21;
    }

    List<String> getApkFoundPathList() {
        return mApkFoundPathList;
    }

    private void throwFolderNotFoundError(Path dir, ITestDevice device) throws TargetSetupError {
        throw new TargetSetupError(
                String.format(
                        "Failed to find folder: %s. Please make sure you have the right permission. "
                                + "If you run the test locally, a workaround is to disable this "
                                + "target preparer by --install-gms-apk:disable. And "
                                + "install a local apk through InstallApkSetup target preparer.",
                        dir.toString()),
                device.getDeviceDescriptor());
    }
}
