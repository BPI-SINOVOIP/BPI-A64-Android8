// Copyright 2013 Google Inc. All Rights Reserved.
package com.google.android.frameworks.tests;

import com.android.ddmlib.Log;
import com.android.framework.tests.PackageManagerHostTestUtils;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.util.FileUtil;

import org.junit.Assert;

import java.io.File;

/**
 * Tests for the android.support.multidex library.<br>
 *
 * Run the tests with:<br>
 * {@code tradefed.sh run singleCommand google/framework/multidex --app-repository-path
 <path to the tests apps apk>}
 */
public class SupportMultidexHostTest extends DeviceTestCase {

    private static final String LOG_TAG = "SupportMultidexHostTest";

    private static final String MULTIDEX_LEGACY_APK = "MultiDexLegacyTestApp.apk";
    private static final String MULTIDEX_LEGACY_PKG = "com.android.multidexlegacytestapp";
    private static final String MULTIDEX_LEGACY_APK2 = "MultiDexLegacyTestApp2.apk";
    private static final String MULTIDEX_LEGACY_WITH_EXCEPTION_APK =
            "MultiDexLegacyAndException.apk";
    private static final String MULTIDEX_LEGACY_WITH_EXCEPTION_PKG =
            "com.android.multidexlegacyandexception";
    private static final String MULTIDEX_LEGACY_VERSIONED_V1_APK =
            "MultiDexLegacyVersionedTestApp_v1.apk";
    private static final String MULTIDEX_LEGACY_VERSIONED_V2_APK =
            "MultiDexLegacyVersionedTestApp_v2.apk";
    private static final String MULTIDEX_LEGACY_VERSIONED_V3_APK =
            "MultiDexLegacyVersionedTestApp_v3.apk";
    private static final String MULTIDEX_LEGACY_VERSIONED_PKG =
            "com.android.framework.multidexlegacyversionedtestapp";
    private static final String MULTIDEX_LEGACY_SERVICES_APK =
            "MultiDexLegacyTestServices.apk";
    private static final String MULTIDEX_LEGACY_SERVICES_PKG =
            "com.android.framework.multidexlegacytestservices";
    private static final String MULTIDEX_LEGACY_SERVICES_TEST_APK =
            "MultiDexLegacyTestServicesTests.apk";
    private static final String MULTIDEX_LEGACY_SERVICES_TEST_PKG =
            "com.android.framework.multidexlegacytestservices.test";

    private static final int WAIT_AFTER_STOP = 3000;

    private PackageManagerHostTestUtils mPMHostUtils = null;

    // TODO: consider fetching these files from build server instead.
    @Option(name = "app-repository-path", description =
            "path to the app repository containing large apks", importance = Importance.ALWAYS)
    private File mAppRepositoryPath = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // ensure apk path has been set before test is run
        assertNotNull("Missing --app-repository-path option", mAppRepositoryPath);

        // setup the PackageManager host tests utilities class, and get various
        // paths we'll need...
        mPMHostUtils = new PackageManagerHostTestUtils(getDevice());
        mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_PKG);
        mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_WITH_EXCEPTION_PKG);
        mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_VERSIONED_PKG);
        mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_SERVICES_PKG);
        mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_SERVICES_TEST_PKG);
    }

    /**
     * Regression test to verify that a legacy multidex app runs without class loading error.
     * <p/>
     * Assumes adb is running as root in device under test.
     */
    public void testInstallAndLaunchMultiDexLegacy() throws Exception {
        Log.i(LOG_TAG, "Test installing and launching a multidex app with the legacy library.");

        try {
            // install the app and verify we can launch it without error
            installAppAndVerifyExists(
                    MULTIDEX_LEGACY_APK, MULTIDEX_LEGACY_PKG, false /*overwrite*/);
            boolean testsPassed = mPMHostUtils.runDeviceTestsDidAllTestsPass(MULTIDEX_LEGACY_PKG);
            assertTrue(testsPassed);

            // uninstall and check all is clean
            mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_PKG);

       }
        // cleanup test app
        finally {
            mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_PKG);
        }
    }

    /**
     * Regression test to verify that a legacy multidex app using java 7 multi catch runs without
     * error.
     * <p/>
     * Assumes adb is running as root in device under test.
     */
    public void testInstallAndLaunchMultiDexLegacyWithMulticatch() throws Exception {
        Log.i(LOG_TAG, "Test installing and launching a multidex app with the legacy library.");

        try {
            // install the app and verify we can launch it without error
            installAppAndVerifyExists(
                    MULTIDEX_LEGACY_WITH_EXCEPTION_APK, MULTIDEX_LEGACY_WITH_EXCEPTION_PKG,
                    false /*overwrite*/);
            boolean testsPassed = mPMHostUtils.runDeviceTestsDidAllTestsPass(MULTIDEX_LEGACY_WITH_EXCEPTION_PKG);
            assertTrue(testsPassed);

            // uninstall and check all is clean
            mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_WITH_EXCEPTION_PKG);

       }
        // cleanup test app
        finally {
            mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_WITH_EXCEPTION_PKG);
        }
    }

    /**
     * Regression test to verify that an updated legacy multidex app run without class loading
     * error.
     * <p/>
     * Assumes adb is running as root in device under test.
     */
    public void testReInstallAndLaunchMultiDexLegacy() throws Exception {
        Log.i(LOG_TAG, "Test updating a multidex app with the legacy library.");

        try {
            // install the app and verify we can launch it without error
            installAppAndVerifyExists(
                    MULTIDEX_LEGACY_APK, MULTIDEX_LEGACY_PKG, false /*overwrite*/);

            // update the app
            installAppAndVerifyExists(
                MULTIDEX_LEGACY_APK2, MULTIDEX_LEGACY_PKG, false /*overwrite*/);

            assertTrue("Test failed after the update.",
                    mPMHostUtils.runDeviceTestsDidAllTestsPass(MULTIDEX_LEGACY_PKG));

            // uninstall and check all is clean
            mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_PKG);
       }
        // cleanup test app
        finally {
            mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_PKG);
        }
    }

    /**
     * Regression test to verify that a legacy multidex app can run without class loading
     * error after an installation by OTA.
     * <p/>
     * Assumes adb is running as root in device under test.
     */
    public void testInstallMultiDexLegacyAndOTA() throws Exception {
        Log.i(LOG_TAG, "Test launching a multidex app with the legacy library after a simulated"
                + " installation by OTA.");

        try {
            // install the app
            installAppAndVerifyExists(
                    MULTIDEX_LEGACY_APK, MULTIDEX_LEGACY_PKG, false /*overwrite*/);

            simulateApplicationsOTA();

            // verify we can launch it without error
            assertTrue("Test failed after the simulated OTA.",
                    mPMHostUtils.runDeviceTestsDidAllTestsPass(MULTIDEX_LEGACY_PKG));
       }
        // cleanup test app
        finally {
            mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_PKG);
        }
    }

    /**
     * Regression test to verify that a legacy multidex app can run without class loading
     * error after an update by OTA.
     * <p/>
     * Assumes adb is running as root in device under test.
     */
    public void testUpdateMultiDexLegacyAndOTA() throws Exception {
        Log.i(LOG_TAG, "Test launching a multidex app with the legacy library after a simulated"
                + " update by OTA.");

        try {
            // install the app
            installAppAndVerifyExists(
                    MULTIDEX_LEGACY_VERSIONED_V1_APK, MULTIDEX_LEGACY_VERSIONED_PKG,
                    false /*overwrite*/);
            // just let MultiDex perform a pre OTA installation
            assertTrue("Test failed before the simulated OTA.",
                    mPMHostUtils.runDeviceTestsDidAllTestsPass(MULTIDEX_LEGACY_VERSIONED_PKG));
            // update the app
            installAppAndVerifyExists(
                    MULTIDEX_LEGACY_VERSIONED_V2_APK, MULTIDEX_LEGACY_VERSIONED_PKG,
                    false /*overwrite*/);

            simulateApplicationsOTA();

            // verify we can launch it without error
            assertTrue("Test failed after the simulated OTA.",
                    mPMHostUtils.runDeviceTestsDidAllTestsPass(MULTIDEX_LEGACY_VERSIONED_PKG));
       }
        // cleanup test app
        finally {
            mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_VERSIONED_PKG);
        }
    }

    /**
     * Regression test to verify that an app with legacy multidex can be updated correctly.
     * <p/>
     * Assumes adb is running as root in device under test.
     */
    public void testMultidexLegacyUpdate() throws Exception {
        try {
            Log.i(LOG_TAG, "Test updating a multidex app several times.");
            // control group
            installAppAndVerifyExists(
                    MULTIDEX_LEGACY_VERSIONED_V1_APK,
                    MULTIDEX_LEGACY_VERSIONED_PKG, true /*overwrite*/);
            boolean testsPassed =
                    mPMHostUtils.runDeviceTestsDidAllTestsPass(MULTIDEX_LEGACY_VERSIONED_PKG);
            Assert.assertTrue(MULTIDEX_LEGACY_VERSIONED_V1_APK + " failed to pass " +
                    "nominal tests.", testsPassed);

            installAppAndVerifyExists(
                    MULTIDEX_LEGACY_VERSIONED_V2_APK,
                    MULTIDEX_LEGACY_VERSIONED_PKG, true /*overwrite*/);
            testsPassed = mPMHostUtils.runDeviceTestsDidAllTestsPass(MULTIDEX_LEGACY_VERSIONED_PKG);
            Assert.assertTrue(MULTIDEX_LEGACY_VERSIONED_V2_APK + " failed to pass " +
                    "nominal tests.", testsPassed);

            installAppAndVerifyExists(
                    MULTIDEX_LEGACY_VERSIONED_V3_APK,
                    MULTIDEX_LEGACY_VERSIONED_PKG, true /*overwrite*/);
            testsPassed = mPMHostUtils.runDeviceTestsDidAllTestsPass(MULTIDEX_LEGACY_VERSIONED_PKG);
            Assert.assertTrue(MULTIDEX_LEGACY_VERSIONED_V3_APK + " failed to pass " +
                    "nominal tests.", testsPassed);

            // clean after control group
            mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_VERSIONED_PKG);

            // verify 2 updates with one never executed
            installAppAndVerifyExists(
                    MULTIDEX_LEGACY_VERSIONED_V1_APK,
                    MULTIDEX_LEGACY_VERSIONED_PKG, true /*overwrite*/);
            testsPassed =
                    mPMHostUtils.runDeviceTestsDidAllTestsPass(MULTIDEX_LEGACY_VERSIONED_PKG);
            Assert.assertTrue(MULTIDEX_LEGACY_VERSIONED_V1_APK + " failed to pass " +
                    "nominal tests.", testsPassed);

            installAppAndVerifyExists(
                    MULTIDEX_LEGACY_VERSIONED_V2_APK,
                    MULTIDEX_LEGACY_VERSIONED_PKG, true /*overwrite*/);
            // no v2 execution

            installAppAndVerifyExists(
                    MULTIDEX_LEGACY_VERSIONED_V3_APK,
                    MULTIDEX_LEGACY_VERSIONED_PKG, true /*overwrite*/);
            testsPassed = mPMHostUtils.runDeviceTestsDidAllTestsPass(MULTIDEX_LEGACY_VERSIONED_PKG);
            Assert.assertTrue(MULTIDEX_LEGACY_VERSIONED_V3_APK + " failed to pass " +
                    "tests after sequential update V1 (runned) to V2 (never runned) then to V3.", testsPassed);

        }
        // cleanup test app
        finally {
            mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_VERSIONED_PKG);
        }

    }

    /**
     * Stress test to verify that MultiDex supports concurrent extractions at first launch.
     */
    public void testStressConcurrentExtractions() throws Exception {
        try {
            Log.i(LOG_TAG, "Stress test performing multiple concurrent multidex extractions.");

            // Ensure services is not installed
            mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_SERVICES_PKG);

            // install service and test.
            installAppAndVerifyExists(
                    MULTIDEX_LEGACY_SERVICES_APK,
                    MULTIDEX_LEGACY_SERVICES_PKG, true /*overwrite*/);
            installAppAndVerifyExists(
                    MULTIDEX_LEGACY_SERVICES_TEST_APK,
                    MULTIDEX_LEGACY_SERVICES_TEST_PKG, true /*overwrite*/);
            boolean testsPassed =
                    mPMHostUtils.runDeviceTestsDidAllTestsPass(MULTIDEX_LEGACY_SERVICES_TEST_PKG);
            Assert.assertTrue(MULTIDEX_LEGACY_SERVICES_APK + " failed to pass " +
                    "concurrent start stress tests.", testsPassed);

        }
        // cleanup test app
        finally {
            mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_SERVICES_PKG);
            mPMHostUtils.uninstallApp(MULTIDEX_LEGACY_SERVICES_TEST_PKG);
        }

    }

    /**
     * Get the absolute file system location of test app with given filename
     *
     * @param fileName the file name of the test app apk
     * @return {@link String} of absolute file path
     */
    public File getTestAppFilePath(String fileName) {
        return FileUtil.getFileForPath(mAppRepositoryPath, fileName);
    }

    private void simulateApplicationsOTA() throws DeviceNotAvailableException {
        // stop everything to avoid putting the phone in a bad state.
        getDevice().executeShellCommand("stop");
        sleep(WAIT_AFTER_STOP);

        // simulate OTA by deleting cache file
        // Dalvik cache files
        getDevice().executeShellCommand("rm -f /data/dalvik-cache/data*.dex");


        // Art cache files
        getDevice().executeShellCommand(
                "cd /data/dalvik-cache/;for a in `ls`; do cd $a; rm -f data*.dex; cd ..; done");

        getDevice().reboot();
    }

    private void sleep(int minimumMillis) {
        long time = System.currentTimeMillis() + minimumMillis;
        while (System.currentTimeMillis() < time) {
            try {
                Thread.sleep(minimumMillis);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    private void installAppAndVerifyExists(String apkName, String packageName,
            boolean overwrite) throws DeviceNotAvailableException {
        // Start with a clean state if we're not overwriting
        if (!overwrite) {
            // cleanup test app just in case it already exists
            mPMHostUtils.uninstallApp(packageName);
        }

        mPMHostUtils.installFile(getTestAppFilePath(apkName), overwrite);
        // TODO: is this necessary?
        mPMHostUtils.waitForPackageManager();

        // grep for package to make sure it is installed
        assertTrue(mPMHostUtils.doesPackageExist(packageName));
    }

}
