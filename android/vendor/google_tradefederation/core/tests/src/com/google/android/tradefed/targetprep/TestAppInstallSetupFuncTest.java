/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.google.android.tradefed.util.GoogleAccountUtil;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * A functional test for {@link TestAppInstallSetup}.
 * <p/>
 * This test is in google-tradefed-tests as opposed to tradefed-tests because it depends on
 * a fixed file in Google NFS.
 */
public class TestAppInstallSetupFuncTest extends DeviceTestCase {

    private static final String TEST_APK_PKG_NAME = GoogleAccountUtil.ACCOUNT_PKG_NAME;
    private String mTestApkName = GoogleAccountUtil.UTIL_APK_NAME + ".apk";

    /**
     * Test {@link TestAppInstallSetup#setUp(ITestDevice, IBuildInfo)} when the test dir is set on
     * the build info.
     */
    public void testSetup_testDir() throws DeviceNotAvailableException, TargetSetupError {
        TestAppInstallSetup installer = new TestAppInstallSetup();
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub");
        File googleAccountApk = getTestApkFile();
        try {
            stubBuild.setTestsDir(googleAccountApk, "0");
            // install the Google account util apk, since its more of a known entity
            installer.addTestFileName(mTestApkName);
            getDevice().uninstallPackage(TEST_APK_PKG_NAME);
            assertFalse(isPackagePresent(getDevice(), TEST_APK_PKG_NAME));
            installer.setUp(getDevice(), stubBuild);
            assertTrue(isPackagePresent(getDevice(), TEST_APK_PKG_NAME));
        } finally {
            FileUtil.recursiveDelete(googleAccountApk);
        }
    }

    /**
     * Test {@link TestAppInstallSetup#setUp(ITestDevice, IBuildInfo)} when the test dir is set on
     * the build info but an alternative dir is also provided to search for the apks.
     */
    public void testSetup_altDir() throws DeviceNotAvailableException, TargetSetupError {
        TestAppInstallSetup installer = new TestAppInstallSetup();
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub");
        // install the Google account util apk, since its more of a known entity
        File googleAccountApk = getTestApkFile();
        try {
            installer.setAltDir(googleAccountApk);
            installer.addTestFileName(mTestApkName);
            getDevice().uninstallPackage(TEST_APK_PKG_NAME);
            assertFalse(isPackagePresent(getDevice(), TEST_APK_PKG_NAME));
            installer.setUp(getDevice(), stubBuild);
            assertTrue(isPackagePresent(getDevice(), TEST_APK_PKG_NAME));
        } finally {
            FileUtil.recursiveDelete(googleAccountApk);
        }
    }

    /**
     * Test {@link TestAppInstallSetup#setUp(ITestDevice, IBuildInfo)} when the test dir is not
     * available in the build info. It should throw a {@link TargetSetupError}.
     */
    public void testSetup_no_dir() throws DeviceNotAvailableException {
        TestAppInstallSetup installer = new TestAppInstallSetup();
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub");
        // install the Google account util apk, since its more of a known entity
        File googleAccountApk = getTestApkFile();
        installer.addTestFileName(mTestApkName);
        try {
            getDevice().uninstallPackage(TEST_APK_PKG_NAME);
            assertFalse(isPackagePresent(getDevice(), TEST_APK_PKG_NAME));
            installer.setUp(getDevice(), stubBuild);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // expected
        } finally {
            FileUtil.recursiveDelete(googleAccountApk);
        }
        assertFalse(isPackagePresent(getDevice(), TEST_APK_PKG_NAME));
    }

    /**
     * Helper method to determine if given package is installed on device.
     *
     * TODO: consider moving this to ITestDevice
     * @throws DeviceNotAvailableException
     */
    private boolean isPackagePresent(ITestDevice device, String pkgName)
            throws DeviceNotAvailableException {
        String response = device.executeShellCommand(String.format("pm list packages %s",
                pkgName));
        return response.trim().equals(String.format("package:%s", pkgName));
    }

    /**
     * Helper to get a known apk to use for the test.
     */
    private File getTestApkFile() {
        File apkTmpDirParent = null;
        File dataTmpdir = null;
        File apkTempFile = null;
        try {
            apkTmpDirParent = FileUtil.createTempDir("test-apk");
            // Expected hierarchy of the preparer
            dataTmpdir = new File(new File(apkTmpDirParent, "DATA"), "app");
            dataTmpdir.mkdirs();
            apkTempFile = FileUtil.createTempFile(GoogleAccountUtil.UTIL_APK_NAME, ".apk",
                    dataTmpdir);
            InputStream apkStream = GoogleAccountUtil.class.getResourceAsStream(
                    String.format("/apks/%s.apk", GoogleAccountUtil.UTIL_APK_NAME));
            FileUtil.writeToFile(apkStream, apkTempFile);
            mTestApkName = apkTempFile.getName();
            FileUtil.chmodGroupRWX(apkTempFile);
        } catch (IOException e) {
            CLog.e("Failed to unpack AccountUtil utility: %s", e.getMessage());
            CLog.e(e);
            return null;
        }
        return apkTmpDirParent;
    }
}
