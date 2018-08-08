// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.gcore;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.RunUtil;

import com.google.android.gcore.NlpTester.FailureListener;

import org.junit.Assert;

import java.io.File;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for test that checks that system features still work as expected after install of
 * GmsCore.
 */
public abstract class AbstractDeployTest extends DeviceTestCase {

    private static final String SYSTEMHEALTH_PKG = "com.google.android.gcore.systemhealth";
    protected static final String GCORE_PKG = "com.google.android.gms";
    protected static final String PLAYSTORE_PKG = "com.android.vending";

    private static final String AUTHENTICATOR_PROVIDER = "com.google.android.gsf";
    private static final Pattern AUTHENTICATOR_PATTERN = Pattern.compile(
            "AuthenticatorDescription\\s\\{type=com\\.google\\},\\sComponentInfo\\{.+/.+\\}");

    private static final Pattern GCM_CONNECTION_PATTERN = Pattern.compile(
            "connected=.+.google.com/");

    private static final Pattern GTALK_CONNECTION_PATTERN = Pattern.compile(
             "((num endpoints:\\s)|(Number of connections:\\s))[1-9][0-9]*");

    private static final Pattern CONNECTED_PATTERN = Pattern.compile("Connected:\\s");

    @Option(name = "gcm-wait-time", description =
            "time to wait for gcm connection, in seconds")
    private long mGcmWaitTime = 30;

    @Option(name = "apk-path", description = "path to filesystem location that contains apks",
            mandatory = true)
    protected String mApkPath;

    @Option(name = "teardown", description =
            "flag to control if teardown steps (play store, gmscore uninstall + reboot) " +
            "on test completion")
    private boolean mTearDown = true;

    @Option(name = "clean-setup", description =
            "flag to control if setup steps (play store, gmscore uninstalled + reboot" +
            "should be performed before test starts")
    private boolean mCleanOnSetup = true;

    @Override
    public void setUp() throws Exception {
        if (mCleanOnSetup) {
            uninstallPackages();
            getDevice().reboot();
        }
    }

    private void uninstallPackages() throws DeviceNotAvailableException {
        // first determine if pkgs need to be uninstalled
        // because attempting to uninstall system apps will result in error
        Set<String> uninstallablePkgs =  getDevice().getUninstallablePackageNames();
        if (uninstallablePkgs.contains(PLAYSTORE_PKG)) {
            log("uninstalling phonesky");
            assertNull(getDevice().uninstallPackage(PLAYSTORE_PKG));
        }
        if (uninstallablePkgs.contains(GCORE_PKG)) {
            log("uninstalling gmscore");
            assertNull(getDevice().uninstallPackage(GCORE_PKG));
        }
        if (uninstallablePkgs.contains(SYSTEMHEALTH_PKG)) {
            log("uninstalling system health pkg");
            assertNull(getDevice().uninstallPackage(SYSTEMHEALTH_PKG));
        }
    }

    @Override
    public void tearDown() throws Exception {
        if (mTearDown) {
            log("tearing down");
            uninstallPackages();
            getDevice().reboot();
        }
    }

    public void testSystemHealth() throws Exception {
        try {
            File systemHealthTester = new File(mApkPath, "GcoreSystemHealthCheck.apk");
            assertFileExists(systemHealthTester);
            assertNull(getDevice().installPackage(systemHealthTester, true));

            assertSystemHealth("initial check");

            triggerGmsCoreInstall();

            assertSystemHealth("after gcore install");

            log("rebooting");
            getDevice().reboot();

            assertSystemHealth("after reboot");
        } catch (AssertionError e) {
            log(e.toString());
            throw e;
        }
    }

    protected void assertFileExists(File foo) {
        assertTrue(foo.getAbsolutePath() + " does not exist", foo.exists());
    }

    /**
     * Triggers and wait for gms core install.
     * @throws DeviceNotAvailableException
     */
    abstract protected void triggerGmsCoreInstall() throws DeviceNotAvailableException;

    /**
     * Helper method to call CLog.i with device serial
     */
    protected void log(String format, Object... args) {
        String combinedFormat = getDevice().getSerialNumber() + ": " + format;
        CLog.i(combinedFormat, args);
    }

    /**
     * Check the health of the device's system features replaced by Gmscore.
     *
     * @param state user-friendly description of current state of test. Used for logging
     * @throws AssertionError
    */
    protected void assertSystemHealth(String state) throws DeviceNotAvailableException {
        log("checking system health");

        verifyMcsConnection(state);

        FailureListener l = new FailureListener();
        InstrumentationTest it = new InstrumentationTest();
        it.setPackageName(SYSTEMHEALTH_PKG);
        it.setDevice(getDevice());
        it.setShellTimeout(60*1000);
        it.run(l);
        Assert.assertTrue("No tests ran when checking system health at " + state,
                l.getNumTotalTests() > 0);
        Assert.assertNull(String.format("System health checks failed at %s\n%s", state, l.mFailureTrace),
                l.mFailureTrace);

        verifyAuthenticatorProvider(state);

        log("system health check passed");
    }

    /**
     * Check the device is connected to mcs through either gsf or gcore
     */
    private void verifyMcsConnection(String state) throws DeviceNotAvailableException {
        final long startTime = System.currentTimeMillis();
        final long waitTimeMs = mGcmWaitTime * 1000;
        String gcmOutput = "";
        String gsfOutput = "";
        Matcher gcmMatcher;
        Matcher gsfMatcher;
        Matcher connectionMatcher;
        while ((System.currentTimeMillis() - startTime) < waitTimeMs) {
            gcmOutput = getDevice().executeShellCommand("dumpsys activity service GcmService ");
            gsfOutput = getDevice().executeShellCommand("dumpsys activity service GTalkService ");
            gcmMatcher = GCM_CONNECTION_PATTERN.matcher(gcmOutput);
            gsfMatcher = GTALK_CONNECTION_PATTERN.matcher(gsfOutput);
            if (gcmMatcher.find()){
                return;
            } else if (gsfMatcher.find()){
                // find the first connection status in the dump, which is the main connection
                // for GTalkService and verify that the status is true
                connectionMatcher = CONNECTED_PATTERN.matcher(gsfOutput);
                if (connectionMatcher.find()) {
                    int startIdx = connectionMatcher.end();
                    String connectionStatus = gsfOutput.substring(startIdx, startIdx+4);
                    if (connectionStatus.equals("true")){
                        return;
                    }
                }
            }
            RunUtil.getDefault().sleep(2 * 1000);
        }
        CLog.d(gcmOutput);
        CLog.d(gsfOutput);
        Assert.fail(String.format(
                "MCS system health check failed at %s: No MCS connection found after %d s.", state,
                mGcmWaitTime));
    }

    /**
     * Check that the authenticator assoicated with com.google is from platform
     * (i.e.com.google.android.gsf)
     */
    private void verifyAuthenticatorProvider(String state) throws DeviceNotAvailableException{
        String output = getDevice().executeShellCommand("dumpsys account");
        Matcher matcher = AUTHENTICATOR_PATTERN.matcher(output);
        Assert.assertTrue(String.format("Auth system health check failed at %s. " +
        "Failed to find a matching authenticator provider.", state),
                matcher.find());
        Assert.assertTrue(String.format("Auth system health check failed at %s. " +
                "Unexpected authenticiator provider found: %s", state, matcher.group(0)),
                matcher.group(0).contains(AUTHENTICATOR_PROVIDER));
    }
}
