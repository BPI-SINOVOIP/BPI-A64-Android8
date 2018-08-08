// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.ISdkBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A {@link ITargetPreparer} that installs one or more apps from a
 * {@link ISdkBuildInfo#getTestsDir()} folder onto device. It will throw a TargetSetupError if it
 * fails to find the app requested, or if it fails to install it.
 */
@OptionClass(alias = "emulator-tests-app-setup")
public class EmulatorTestAppInstallSetup implements ITargetPreparer {

    @Option(name = "test-app-name", description =
            "The name of a test app to install on device. Can be repeated.",
            importance = Importance.IF_UNSET)
    private Collection<String> mTestAppNames = new ArrayList<String>();

    /**
     * Adds an app to the list of apks to install
     *
     * @param appName
     */
    public void addTestAppName(String appName) {
        mTestAppNames.add(appName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException {
        if (!(buildInfo instanceof ISdkBuildInfo)) {
            throw new IllegalArgumentException(String.format("Provided buildInfo is not a %s",
                    ISdkBuildInfo.class.getCanonicalName()));
        }
        if (mTestAppNames.size() == 0) {
            CLog.i("No test apps to install, skipping");
            return;
        }
        File testsDir = ((ISdkBuildInfo) buildInfo).getTestsDir();
        if (testsDir == null || !testsDir.exists()) {
            throw new TargetSetupError(
                    "Provided buildInfo does not contain a valid tests directory",
                    device.getDeviceDescriptor());
        }

        for (String testAppName : mTestAppNames) {
            // Apps are located under /DATA/app/<app_name>/...
            File testAppFile = FileUtil.getFileForPath(testsDir, "DATA", "app",
                    testAppName.substring(0, testAppName.lastIndexOf(".apk")), testAppName);
            if (!testAppFile.exists()) {
                throw new TargetSetupError(
                        String.format(
                                "Could not find test app %s directory in extracted tests.zip",
                                testAppFile), device.getDeviceDescriptor());
            }
            String result = device.installPackage(testAppFile, true);
            if (result != null) {
                throw new TargetSetupError(
                        String.format("Failed to install %s on %s. Reason: '%s'", testAppName,
                                device.getSerialNumber(), result), device.getDeviceDescriptor());
            }
        }
    }
}
