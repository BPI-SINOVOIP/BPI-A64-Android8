// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.security.tests;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.loganalysis.item.JavaCrashItem;
import com.android.loganalysis.item.LogcatItem;
import com.android.loganalysis.item.MiscLogcatItem;
import com.android.loganalysis.parser.LogcatParser;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GcoreSnetTest implements IRemoteTest, IDeviceTest {

    private ITestInvocationListener mListener;
    private ITestDevice mDevice;
    private static final String SAFETY_NET_LOGCAT_TAG_NAME = "Snet";
    private static final String SAFETY_NET_CATEGORY_NAME = "SAFETY_NET";

    private static final int SNET_VER_UNKNOWN = -1;
    private static final int SNET_VER_BASE = 6;
    private static final int SNET_VER_LATEST = 7;
    private static final int SNET_VER_BAD_SIG = 9;
    private static final String SNET_VER_BASE_HELLO_MSG = "Hello Snet 2!";
    private static final String SNET_VER_LATEST_HELLO_MSG = "Hello Snet (3)!";
    private static final String SNET_VER_BAD_SIG_MSG = "I am a bad, malicious snet.";

    private static final int SNET_WAKE_INTERVAL_MS = 5000;
    private static final String SNET_INSTALL_DIR =
            "/data/data/com.google.android.gms/snet/installed";
    private static final String SNET_DOWNLOAD_DIR =
            "/data/data/com.google.android.gms/snet/download";
    private static final Pattern SNET_VERSION_PATTERN = Pattern.compile("VERSION:(\\d*)");
    private static final String GSERVICES_OVERRIDE_PKG =
            "com.google.gservices.intent.action.GSERVICES_OVERRIDE";

    @Option(name = "snet-download-url", description =
            "the Snet download url regex. Must contain '%d' for different Snet versions.",
            importance = Importance.ALWAYS)
    private String mSnetDownloadUrl = "http://cannings.org/snet/test_version%d.snet";

    /**
     * The current test failed because of failureReason. This is used to halt the current
     * test upon a failure condition being met.
     */
    private class TestFailedException extends Exception {
        private static final long serialVersionUID = 1L;
        public TestFailedException(String failureReason) {
            super(failureReason);
        }
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Clean up Snet gserices_overrides and remove Snet jar and data
     */
    private void cleanupSnet() throws DeviceNotAvailableException {
        resetGServicesOverride("snet_wake_interval_ms");
        // set to empty instead of resetting to default, since default would point to Prod Snet
        setGServicesOverride("snet_package_url", "");
        rmDeviceDir(SNET_INSTALL_DIR);
        rmDeviceDir(SNET_DOWNLOAD_DIR);
    }

    /**
     * Runs the GcoreSnetTest test suite
     *
     * @param listener The test invocation listener.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mListener = listener;
        mListener.testRunStarted(GcoreSnetTest.class.getName(), 0);
        cleanupSnet();
        setGServicesOverride("snet_wake_interval_ms", Integer.toString(SNET_WAKE_INTERVAL_MS));

        snetInstallTest();
        snetUpdateTest();
        snetDowngradeTest();
        snetBadSignatureTest();

        cleanupSnet();
        mListener.testRunEnded(0, Collections.EMPTY_MAP);
    }

    /**
     * Runs the snetInstallTest, which verifies that GmsCore will download, install, and run
     * the base version of Snet. This will also verify
     * that Snet is saying the expected output, and that no GmsCore crashes occurred during test.
     *
     * @throws DeviceNotAvailableException
     */
    @SuppressWarnings("unchecked")
    public void snetInstallTest() throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(GcoreSnetTest.class.getName(),
                "snetInstallTest");
        mListener.testStarted(testId);

        Date testStartTime = getLatestLogcatTimestamp();
        setGServicesOverride("snet_package_url", String.format(mSnetDownloadUrl, SNET_VER_BASE));
        waitForSnetUpdate(SNET_VER_BASE_HELLO_MSG, testStartTime);

        try {
            assertSnetIsRunning();
            assertSnetVersionIs(SNET_VER_BASE);
            assertSnetSaid(SNET_VER_BASE_HELLO_MSG, testStartTime);
            assertNoGcoreCrashesAfter(testStartTime);
        } catch (TestFailedException tfe) {
            mListener.testFailed(testId, tfe.getMessage());
        }

        mListener.testEnded(testId, Collections.EMPTY_MAP);
    }

    /**
     * Runs the snetUpdateTest, which verifies that GmsCore will download, install, and run
     * the latest version of Snet. This will also verify
     * that Snet is saying the expected output, and that no GmsCore crashes occurred during test.
     */
    @SuppressWarnings("unchecked")
    public void snetUpdateTest() throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(GcoreSnetTest.class.getName(),
                "snetUpdateTest");
        mListener.testStarted(testId);

        Date testStartTime = getLatestLogcatTimestamp();
        setGServicesOverride("snet_package_url", String.format(mSnetDownloadUrl, SNET_VER_LATEST));
        waitForSnetUpdate(SNET_VER_LATEST_HELLO_MSG, testStartTime);

        try {
            assertSnetIsRunning();
            assertSnetVersionIs(SNET_VER_LATEST);
            assertSnetSaid(SNET_VER_LATEST_HELLO_MSG, testStartTime);
            assertNoGcoreCrashesAfter(testStartTime);
        } catch (TestFailedException tfe) {
            mListener.testFailed(testId, tfe.getMessage());
        }

        mListener.testEnded(testId, Collections.EMPTY_MAP);
    }

    /**
     * Runs the snetDowngradeTest, which verifies that GmsCore will NOT downgrade to an earlier
     * version of Snet if the download URL is changed to an older version. This will also verify
     * that Snet is saying the expected output, and that no GmsCore crashes occurred during test.
     */
    @SuppressWarnings("unchecked")
    public void snetDowngradeTest() throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(GcoreSnetTest.class.getName(),
                "snetDowngradeTest");
        mListener.testStarted(testId);

        Date testStartTime = getLatestLogcatTimestamp();
        setGServicesOverride("snet_package_url", String.format(mSnetDownloadUrl, SNET_VER_BASE));

        // since we're expecting Snet version to stay the same, we can't use waitForSnetUpdate()
        pauseForSnetUpdate();

        try {
            assertSnetIsRunning();
            assertSnetVersionIs(SNET_VER_LATEST);
            assertSnetSaid(SNET_VER_LATEST_HELLO_MSG, testStartTime);
            assertSnetNotSaid(SNET_VER_BASE_HELLO_MSG, testStartTime);
            assertNoGcoreCrashesAfter(testStartTime);
        } catch (TestFailedException tfe) {
            mListener.testFailed(testId, tfe.getMessage());
        }

        mListener.testEnded(testId, Collections.EMPTY_MAP);
    }

    /**
     * Runs the snetBadSignatureTest, which verifies that GmsCore will NOT update to a version of
     * Snet that has a bad signature. This will also verify that Snet is saying the expected
     * output, and that no GmsCore crashes occurred during test.
     */
    @SuppressWarnings("unchecked")
    public void snetBadSignatureTest() throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(GcoreSnetTest.class.getName(),
                "snetBadSignatureTest");
        mListener.testStarted(testId);

        Date testStartTime = getLatestLogcatTimestamp();
        setGServicesOverride("snet_package_url", String.format(mSnetDownloadUrl, SNET_VER_BAD_SIG));

        // since we're expecting Snet version to stay the same, we can't use waitForSnetUpdate(),
        pauseForSnetUpdate();

        try {
            assertSnetIsRunning();
            assertSnetVersionIs(SNET_VER_LATEST);
            assertSnetSaid(SNET_VER_LATEST_HELLO_MSG, testStartTime);
            assertSnetNotSaid(SNET_VER_BAD_SIG_MSG, testStartTime);
            assertNoGcoreCrashesAfter(testStartTime);
        } catch (TestFailedException tfe) {
            mListener.testFailed(testId, tfe.getMessage());
        }

        mListener.testEnded(testId, Collections.EMPTY_MAP);
    }

    /**
     * Waits up to 3 minutes for Snet update to occur, based on searching logcat for expectedMsg to
     * appear in any Snet log entry that occurs after the testStartTime.
     *
     * @param expectedMsg the message that Snet is supposed to say upon update
     * @param testStartTime the earliest place in the logcat that expectedMsg should appear
     */
    private void waitForSnetUpdate(String expectedMsg, Date testStartTime) {
        final long TIMEOUT_MS = 3 * 60 * 1000;
        long endTime = System.currentTimeMillis() + TIMEOUT_MS;
        CLog.d("Waiting up to %s ms for Snet update...", TIMEOUT_MS);
        while (System.currentTimeMillis() < endTime) {
            if (didSnetLogMsg(expectedMsg, testStartTime)) {
                return;
            }
            RunUtil.getDefault().sleep(SNET_WAKE_INTERVAL_MS);
        }
        CLog.d("Snet update timed out.");
    }

    /**
     * Pauses for 2 minutes for Snet update to occur.
     */
    private void pauseForSnetUpdate() {
        // let's wait 2 minutes for update, which should be enough time even if device is throttled
        final int PAUSE_MS = 2 * 60 * 1000;
        CLog.d("Pausing for %s ms for Snet update...", PAUSE_MS);
        RunUtil.getDefault().sleep(PAUSE_MS);
    }

    /**
     * Asserts that Snet is running, throwing a TestFailedException if it's not.
     *
     * @throws TestFailedException
     */
    private void assertSnetIsRunning() throws DeviceNotAvailableException, TestFailedException {
        if (!isSnetRunning()) {
            throw new TestFailedException("Snet is not running.");
        }
    }

    /**
     * Asserts that the version of Snet installed matches the expectedVer, throwing a
     * TestFailedException if it doesn't match.
     *
     * @param expectedVer the version of Snet you expected the device to have installed
     * @throws TestFailedException
     */
    private void assertSnetVersionIs(int expectedVer)
            throws DeviceNotAvailableException, TestFailedException {
        if (getSnetVersion() != expectedVer) {
            throw new TestFailedException(String.format(
                    "Snet version was %d, but expected %d", getSnetVersion(), expectedVer));
        }
    }

    /**
     * Asserts that Snet said the expectedMsg anywhere after testStartTime in logcat, throwing a
     * TestFailedException if it didn't match.
     *
     * @param expectedMsg the message you expected Snet to say in logcat
     * @param testStartTime the earliest place in logcat that you expect the msg to appear
     * @throws TestFailedException
     */
    private void assertSnetSaid(String expectedMsg, Date testStartTime)
            throws TestFailedException {
        if (!didSnetLogMsg(expectedMsg, testStartTime)) {
            throw new TestFailedException(String.format(
                    "Snet did not say '%s' after %s during test execution",
                    expectedMsg, testStartTime));
        }
    }

    /**
     * Asserts that Snet did NOT say the notExpectedMsg anywhere after testStartTime in logcat,
     * fails the test if it was said.
     *
     * @param notExpectedMsg the message you expect Snet to NOT say in logcat
     * @param testStartTime the earliest place in logcat that you expect the msg to not appear
     * @throws TestFailedException
     */
    private void assertSnetNotSaid(String notExpectedMsg, Date testStartTime)
            throws TestFailedException {
        if (didSnetLogMsg(notExpectedMsg, testStartTime)) {
            throw new TestFailedException(String.format(
                    "Snet said '%s' after %s during test execution",
                    notExpectedMsg, testStartTime));
        }
    }

    /**
     * Asserts that GmsCore had no crashes appear in the logs anytime after testStartTime,
     * fails the test if there were any.
     *
     * @param testStartTime the earliest place in logcat that you expect there to be no Gms crashes
     * @throws TestFailedException
     */
    private void assertNoGcoreCrashesAfter(Date testStartTime) throws TestFailedException {
        if (hasGmsCoreCrashAfter(testStartTime)) {
            throw new TestFailedException(
                    "A GmsCore crash occurred during testing - see host logs for more details.");
        }
    }

    /**
     * Verifies whether the Snet process is currently running on the device.
     *
     * @return True if running, false otherwise.
     * @throws DeviceNotAvailableException
     */
    private boolean isSnetRunning() throws DeviceNotAvailableException {
        String result = mDevice.executeShellCommand("su -c ps | grep com.google.android.gms:snet");
        if (result != null && result.length() > 0) {
            return true;
        }
        return false;
    }

    /**
     * Gets the current installed version of Snet installed on the device.
     *
     * @return the current installed version of Snet, or SNET_VERSION_UNKNOWN if undetermined.
     * @throws DeviceNotAvailableException
     */
    private int getSnetVersion() throws DeviceNotAvailableException {
        String cmd = String.format("cat %s/metadata", SNET_INSTALL_DIR);
        String result = mDevice.executeShellCommand(cmd);
        Matcher snetVerMatcher = SNET_VERSION_PATTERN.matcher(result);
        if (snetVerMatcher.find()) {
            int snetVersion = Integer.parseInt(snetVerMatcher.group(1));
            return snetVersion;
        }
        return SNET_VER_UNKNOWN;
    }

    /**
     * Verifies whether Snet logged the expected message sometime after startTime.
     *
     * @param msg The message that's expected to appear in the logs.
     * @param startTime Only look at messages that occurred after startTime.
     * @return True if Snet said the expected message after startTime, false otherwise.
     */
    private boolean didSnetLogMsg(String msg, Date startTime) {
        LogcatItem logcat = getLogcatItem();
        List<MiscLogcatItem> snetLogs = logcat.getMiscEvents(SAFETY_NET_CATEGORY_NAME);
        for (MiscLogcatItem snetLog : snetLogs) {
            if (snetLog.getEventTime().after(startTime)) {
                if (snetLog.getStack().contains(msg)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Verifies whether there were any crashes with GmsCore after the specified device timestamp.
     *
     * @param startTime Device timestamp - only look for GmsCore crashes occurring after this.
     * @return True if a GmsCore crash occurred after the specified time, false otherwise.
     */
    private boolean hasGmsCoreCrashAfter(Date startTime) {
        final String GMS_PACKAGE = "com.google.android.gms";
        LogcatItem logcat = getLogcatItem();
        List<JavaCrashItem> crashes = logcat.getJavaCrashes();
        for (JavaCrashItem crash : crashes) {
            if (crash.getEventTime().after(startTime) && crash.getApp() != null &&
                    crash.getApp().contains(GMS_PACKAGE)) {
                CLog.i("%s in %s occurred at %s (device-time) during testing:\n%s",
                        crash.getException(), crash.getApp(), crash.getEventTime(),
                        crash.getStack());
                return true;
            }
        }
        return false;
    }

    /**
     * Remove the specified directory (recursive) on the device.
     *
     * @param dirPath directory path to remove
     * @throws DeviceNotAvailableException
     */
    private void rmDeviceDir(String dirPath) throws DeviceNotAvailableException {
        String cmd = String.format("rm -r %s", dirPath);
        mDevice.executeShellCommand(cmd);
    }

    /**
     * Sets the specified GServices Override key to the specified value.
     *
     * @param key Name of the key to be set.
     * @param value Value to be set.
     * @throws DeviceNotAvailableException
     */
    private void setGServicesOverride(String key, String value)
            throws DeviceNotAvailableException {
        String cmd = String.format("am broadcast -a %s -e '%s' '%s'",
                GSERVICES_OVERRIDE_PKG, key, value);
        mDevice.executeShellCommand(cmd);
    }

    /**
     * Reset's the specified GServices Override key back to default.
     *
     * @param key Name of the key to reset.
     * @throws DeviceNotAvailableException
     */
    private void resetGServicesOverride(String key) throws DeviceNotAvailableException {
        String cmd = String.format("am broadcast -a %s --esn '%s'", GSERVICES_OVERRIDE_PKG, key);
        mDevice.executeShellCommand(cmd);
    }

    /**
     * Get the most recent logcat entry's timestamp.
     *
     * @return the device's current time
     * @throws DeviceNotAvailableException
     */
    private Date getLatestLogcatTimestamp() throws DeviceNotAvailableException {
        LogcatItem logcat = getLogcatItem();
        Assert.assertNotNull("logcat is null", logcat);
        Date stopTime = logcat.getStopTime();
        Assert.assertNotNull("logcat stopTime is null", stopTime);
        Assert.assertTrue("logcat stopTime is empty", stopTime.getTime() > 0);
        return stopTime;
    }

    /**
     * Gets a parsed LogcatItem from the device's logcat stream.
     *
     * @return the parsed LogcatItem
     */
    private LogcatItem getLogcatItem() {
        
        LogcatItem logcat = null;
        try (InputStreamSource logcatSource = mDevice.getLogcat()) {
            LogcatParser parser = new LogcatParser();
            parser.addPattern(null, null, SAFETY_NET_LOGCAT_TAG_NAME, SAFETY_NET_CATEGORY_NAME);
            logcat = parser.parse(new BufferedReader(new InputStreamReader(
                    logcatSource.createInputStream())));
        } catch (IOException e) {
            CLog.e(String.format("Failed to fetch and parse bugreport for device %s: %s",
                    mDevice.getSerialNumber(), e));
        }
        Assert.assertNotNull("logcat logs are null", logcat);
        return logcat;
    }
}
