// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.athome.tests;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IAppBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.google.android.tradefed.build.DeviceWithAppLaunchControlProvider;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A {@link DeviceWithAppLaunchControlProvider} that can copy additional test apks from device
 * branch into the app build.
 * <p/>
 */
@OptionClass(alias = "aah-apps-device-launch-control")
public class DeviceWithAahAppsLaunchControlProvider extends DeviceWithAppLaunchControlProvider {

    @Option(name = "test-apk-for-phone", description =
            "name of apk from device build tungsten test.zip, that should be installed on " +
            "phone controller.")
    private Collection<String> mTestApkNames = new ArrayList<String>();

    @Option(name = "dependent-apk-dir", description =
            "optionally filesystem path of apks to install on phone")
    private File mPhoneApkDir = null;

    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        IBuildInfo build = super.getBuild();
        if (build instanceof IAppBuildInfo && build instanceof IDeviceBuildInfo) {

            // copy the test apks needed for phone into the apps build
            for (String apkName : mTestApkNames) {
                copyTestFile((IDeviceBuildInfo)build, (IAppBuildInfo)build, apkName);
            }
        }
        if (build instanceof IAppBuildInfo && mPhoneApkDir != null) {
            copyPhoneApks((IAppBuildInfo)build, mPhoneApkDir);
        }
        return build;

    }

    /**
     * Copy the given test apk file from the deviceBuild testsZip into the appBuild.
     *
     * @param deviceBuild the {@link IDeviceBuildInfo}
     * @param appBuild the {@link IAppBuildInfo}
     * @param apkName name of test zip to copy
     * @throws BuildRetrievalError
     */
    private void copyTestFile(IDeviceBuildInfo deviceBuild, IAppBuildInfo appBuild, String apkName)
            throws BuildRetrievalError {
        CLog.i("Copying %s to apps build for phone", apkName);
        try {
            File apkCopy = FileUtil.createTempFile(apkName, ".apk");
            File origApk = FileUtil.getFileForPath(deviceBuild.getTestsDir(), "DATA", "app",
                    apkName);
            if (!origApk.exists()) {
                throw new BuildRetrievalError(String.format("Could not find %s in build", apkName));
            }
            FileUtil.copyFile(origApk, apkCopy);
            appBuild.addAppPackageFile(apkCopy, deviceBuild.getBuildId());
        } catch (IOException e) {
            CLog.e(e);
            throw new BuildRetrievalError(String.format("Failed to copy %s", apkName));
        }
    }

    private void copyPhoneApks(IAppBuildInfo appBuild, File phoneApkDir)
            throws BuildRetrievalError {
        File[] files = phoneApkDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".apk");
            }
        });
        if (files != null) {
            for (File apkFile : files) {
                File copiedFile = copyFile(apkFile);
                // TODO: put proper build id here
                appBuild.addAppPackageFile(copiedFile, appBuild.getBuildId());
            }
        } else {
            CLog.e("Unable to find apk files in %s", phoneApkDir.getAbsolutePath());
        }

    }

    private File copyFile(File apkFile) throws BuildRetrievalError {
        try {
            // Only using createTempFile to create a unique dest filename
            File copyFile = FileUtil.createTempFile(FileUtil.getBaseName(apkFile.getName()),
                    FileUtil.getExtension(apkFile.getName()));
            FileUtil.copyFile(apkFile, copyFile);
            return copyFile;
        } catch (IOException e) {
            CLog.e(e);
            throw new BuildRetrievalError(String.format("Failed to copy %s",
                    apkFile.getAbsolutePath()));
        }
    }
}
