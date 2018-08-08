// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.InstallApkSetup;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.google.android.tradefed.util.PublicApkUtil;
import com.google.android.tradefed.util.PublicApkUtil.ApkInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ITargetPreparer} that supports installing public APKs from a Play Store dump using the
 * <a href="http://go/app-compatibility-readme">App Compatibility pipeline</a>.
 */
@OptionClass(alias = "install-public-apk")
public class InstallPublicApkSetup extends InstallApkSetup {
    @Option(name = "disable", description = "Disable this preparer if true.")
    private boolean mDisable = false;

    @Option(name = "quit-on-error", description = "Quit if any errors are encountered causing the"
            + "any packages not to be installed.")
    private boolean mQuitOnError = false;

    @Option(name = "package-list", description = "Application packages to install.")
    private List<String> mPackageList = new ArrayList<>();

    @Option(name = "base-dir", description = "Base directory with latest.txt.")
    private String mBaseDir;

    @Option(name = "subdir", description = "Optional dated target subdirectory, in the form of"
            + "YYYYMMDD. If unset, the latest results subdirectory will be used.")
    private String mDateSubdir;

    private static final long APK_DOWNLOAD_TIMEOUT_MS = 3 * 60 * 1000;
    private static final int APK_DOWNLOAD_RETRIES = 3;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws DeviceNotAvailableException,
            TargetSetupError, BuildError {
        if (mDisable) {
            return;
        }

        // Construct the CNS APK directory
        File apkDir = null;
        try {
            apkDir = PublicApkUtil.constructApkDir(mBaseDir, mDateSubdir);
        } catch (IOException e) {
            throw new TargetSetupError(
                    "Failed to construct directory.", e, device.getDeviceDescriptor());
        }
        // Process the CSV for a list of APKs
        List<ApkInfo> availableApkList = null;
        try {
            availableApkList = PublicApkUtil.getApkList(device.getProductType(), apkDir);
        } catch (IOException e) {
            throw new TargetSetupError("Failed to get the list of APKs.", e,
                    device.getDeviceDescriptor());
        }
        // Move the APK list to a map
        Map<String, ApkInfo> availableApkMap = new HashMap<>();
        for (ApkInfo info : availableApkList) {
            availableApkMap.put(info.packageName, info);
        }
        try {
            // Download the relevant APKs
            for (String pkg : mPackageList) {
                if (!availableApkMap.containsKey(pkg)) {
                    continue;
                }

                ApkInfo info = availableApkMap.get(pkg);
                File remoteApk = new File(apkDir, info.fileName);
                File localApk = null;
                try {
                    localApk =
                            PublicApkUtil.downloadFile(
                                    remoteApk, APK_DOWNLOAD_TIMEOUT_MS, APK_DOWNLOAD_RETRIES);
                } catch (IOException e) {
                    String errorMessage = String.format(
                            "Failed to download remote APK, %s", remoteApk.getName());
                    if (mQuitOnError) {
                        throw new TargetSetupError(errorMessage, e, device.getDeviceDescriptor());
                    } else {
                        CLog.e(errorMessage);
                        CLog.e(e);
                    }
                }
                // Store temporary APKs in the parent option
                getApkPaths().add(localApk);
            }
            // Rely on the existing installation method
            super.setUp(device, buildInfo);
        } finally {
            // Ensure the temporary APKs are removed
            for (File apk : getApkPaths()) {
                FileUtil.deleteFile(apk);
            }
        }
    }
}
