/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.AltDirBehavior;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.BuildTestsZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ITargetPreparer} that invokes permission utility to grant all revoked runtime
 * permissions.
 */
@OptionClass(alias = "grant-permission")
public class GrantPermissionPreparer implements ITargetPreparer {

    private static final String UTIL_PKG_NAME = "com.android.permissionutils";
    private static final String CHECK_INSTRUMENTATION_CMD =
            "pm list instrumentation " + UTIL_PKG_NAME;
    private static final String UTIL_APK_NAME = "PermissionUtils.apk";
    private static final String PERMISSION_COMMAND = "am instrument -w -e command %s "
            + UTIL_PKG_NAME + "/.PermissionInstrumentation";
    private static final String INSTR_SUCCESS = "INSTRUMENTATION_CODE: -1";

    @Option(name = "alt-dir",
            description = "Alternate directory to look for the apk if the apk is not in the tests "
                    + "zip file. For each alternate dir, will look in //, //data/app, //DATA/app, "
                    + "//DATA/app/apk_name/ and //DATA/priv-app/apk_name/. Can be repeated. "
                    + "Look for apks in last alt-dir first.")
    private List<File> mAltDirs = new ArrayList<>();

    @Option(
        name = "alt-dir-behavior",
        description =
                "The order of alternate directory to be used "
                        + "when searching for apks to install"
    )
    private AltDirBehavior mAltDirBehavior = AltDirBehavior.FALLBACK;

    @Option(name = "disable", description = "disable the grant permission preparer")
    private boolean mDisable = false;

    /**
     * Install the permission utility
     */
    boolean installTestUtil(IBuildInfo buildInfo, ITestDevice device)
            throws DeviceNotAvailableException {
        //TODO: This is directly lifted from GoogleAccountPreparer, and similar code exists in
        // WiFiHelper, so refactoring a util class for installing embedded apks is needed
        final String inst = device.executeShellCommand(CHECK_INSTRUMENTATION_CMD);
        if ((inst != null) && inst.contains(UTIL_PKG_NAME)) {
            // Good to go
            return true;
        } else {
            // Attempt to install utility
            File apkTempFile = null;
            try {
                apkTempFile =
                        BuildTestsZipUtils.getApkFile(
                                buildInfo,
                                UTIL_APK_NAME,
                                mAltDirs,
                                mAltDirBehavior,
                                true, /* also look up in test harness resource as fallback */
                                device.getBuildSigningKeys());
                CLog.i("Installing %s on %s",
                        apkTempFile.getAbsolutePath(), device.getSerialNumber());
                final String result = device.installPackage(apkTempFile, false);
                if (result != null) {
                    CLog.e("Unable to install utility: %s", result);
                    return false;
                }
            } catch (IOException e) {
                CLog.e("Failed to unpack utility: %s", e.getMessage());
                CLog.e(e);
                return false;
            } finally {
                FileUtil.deleteFile(apkTempFile);
            }
            return true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        if (mDisable) {
            return;
        }
        if (!device.isRuntimePermissionSupported()) {
            return;
        }
        if (!installTestUtil(buildInfo, device)) {
            throw new TargetSetupError(String.format(
                    "Failed to install permission util on device %s", device.getSerialNumber()),
                    device.getDeviceDescriptor());
        }
        try {
            CLog.i("Granting all revoked runtime permissions");
            String result = device.executeShellCommand(String.format(
                    PERMISSION_COMMAND, "grant-all"));
            if (result == null || !result.contains(INSTR_SUCCESS)) {
                CLog.w("Failed to grant all revoked runtime permissions, output: %s", result);
                throw new TargetSetupError("failure in grant runtime permission util",
                        device.getDeviceDescriptor());
            }
            // do a dump of revoked runtime permissions: there shouldn't be any, but just FTR
            device.executeShellCommand(String.format(PERMISSION_COMMAND, "dump"));
        } finally {
            device.uninstallPackage(UTIL_PKG_NAME);
        }
    }

}
