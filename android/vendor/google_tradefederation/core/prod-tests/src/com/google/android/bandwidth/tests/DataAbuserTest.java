/// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.bandwidth.tests;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import org.junit.Assert;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Test that tests the naughty box feature for the bandwidth feature. When an app is moved to the
 * naughty box, it is not allowed any data connection. The dataabuser app tries to connect to the
 * Internet, and outputs its results to logcat as well as an output file, which this test checks.
 */
public class DataAbuserTest implements IDeviceTest, IRemoteTest {

    private static final long FETCH_FILE_TIMEOUT_MS = 5 * 60 * 1000;
    private static final long FETCH_FILE_POLL_MS = 30 * 1000;
    // Small delay to give time for the settings to take effect.
    private static final long SETTINGS_DELAY_MS = 30 * 1000;

    private static final String OPEN_BLANK_WEB = "am start \"about:blank\"";

    private static final String START_SERVICE_TEMPLATE = ("am startservice -e " +
            "BandwidthEnforcementTestServiceOutputFile %s -n %s/%s");
    private static final String ADD_NAUGHTY_BOX_TEMPLATE = "ndc bandwidth addnaughtyapps %s";
    private static final String REMOVE_NAUGHTY_BOX_TEMPLATE = "ndc bandwidth removenaughtyapps %s";
    private static final String SUCCESSFUL_BANDWIDTH_RESULT = "Bandwidth command succeeeded";
    private static final String PACKAGE_INFO_TEMPLATE = "dumpsys package %s";

    private static final String NAUGHTY_BOX_DISABLES_DATA = "Naughty box disables data";
    private static final String BACKGROUND_DATA_WORKS =
            "Background data works when not in naughty box";

    private static final String TRUE = "1";
    private static final String FALSE = "0";

    ITestDevice mTestDevice = null;

    @Option(name = "test-package-name", description = "Android test package name.")
    private String mTestPackageName;

    @Option(name = "test-service-name", description = "Android test service name.")
    private String mTestServiceName;

    @Option(name = "output-file-name", description = "Test output filename.")
    private String mOutputFile;

    @Option(name = "dashboard-test-label", description = "Test label when posting to dashboard.")
    private String mDashboardTestLabel;

    private String mOutputFilePath;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Map<String, String> testStatus = new HashMap<String, String>();
        try {
            Assert.assertNotNull("Have a null device.", mTestDevice);

            String extStore = mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
            mOutputFilePath = new File(extStore, mOutputFile).getAbsolutePath();
            Long uid = fetchUidForPackage(mTestPackageName);
            CLog.d("UID for TestPackage %s: %d", mTestPackageName, uid);

            Assert.assertNotNull("Could not find UID for naughty app.", uid);
            Assert.assertTrue("Failed to add app to naughty list.", putUidInNaughtyBox(uid));
            Thread.sleep(SETTINGS_DELAY_MS);
            if (connectivityTestsPassed()) {
                testStatus.put(NAUGHTY_BOX_DISABLES_DATA, TRUE);
            } else {
                testStatus.put(NAUGHTY_BOX_DISABLES_DATA, FALSE);
            }
            // When the app is no longer in the naughty box, the tests should fail.
            Assert.assertTrue("Failed to remove app from naughty list.", removeUidFromNaughtyBox(uid));
            Thread.sleep(SETTINGS_DELAY_MS);
            if(connectivityTestsPassed()) {
                testStatus.put(BACKGROUND_DATA_WORKS, FALSE);
            } else {
                testStatus.put(BACKGROUND_DATA_WORKS, TRUE);
            }
        } catch (InterruptedException e) {
            CLog.e("Interrupted in the middle of the test.");
            CLog.e(e);
        } finally {
            reportMetrics(listener, mDashboardTestLabel, testStatus);
        }
    }

    /**
     * Sends an intent to the service running in the background that tries to connect to the
     * Internet
     *
     * @return true if the app is unable to connect to the Internet.
     * @throws DeviceNotAvailableException
     */
    private boolean connectivityTestsPassed() throws DeviceNotAvailableException {
        cleanOutputFile();

        // Open the web browser, so as to send the test app into the background.
        mTestDevice.executeShellCommand(OPEN_BLANK_WEB);
        // Start intent for the naughty app.
        mTestDevice.executeShellCommand(String.format(START_SERVICE_TEMPLATE, mOutputFile,
                mTestPackageName, mTestServiceName));
        return parseOutputFile();
    }

    /**
     * Clean up the tmp output file from previous test runs
     */
    private void cleanOutputFile() throws DeviceNotAvailableException {
        mTestDevice.executeShellCommand(String.format("rm %s", mOutputFilePath));
        Assert.assertFalse("Failed to clean up output file.",
                mTestDevice.doesFileExist(mOutputFilePath));
    }

    /**
     * Put a given uid into the naughty box via adb command.
     *
     * @param uid of the app
     * @return true if the command was successfully sent
     * @throws DeviceNotAvailableException
     */
    private boolean putUidInNaughtyBox(Long uid) throws DeviceNotAvailableException {
        String result = mTestDevice.executeShellCommand(String.format(
                ADD_NAUGHTY_BOX_TEMPLATE, uid.toString()));
        return result.contains(SUCCESSFUL_BANDWIDTH_RESULT);
    }

    /**
     * Removes a given uid from the naughty box via adb command.
     *
     * @param uid of the app
     * @return true if the command was successfully sent
     * @throws DeviceNotAvailableException
     */
    private boolean removeUidFromNaughtyBox(Long uid) throws DeviceNotAvailableException {
        String result = mTestDevice.executeShellCommand(String.format(
                REMOVE_NAUGHTY_BOX_TEMPLATE, uid.toString()));
        return result.contains(SUCCESSFUL_BANDWIDTH_RESULT);
    }

    /**
     * Fetch the Uid for a given package name
     * @param packageName
     * @return
     * @throws DeviceNotAvailableException
     */
    private Long fetchUidForPackage(String packageName) throws DeviceNotAvailableException {
        String result = mTestDevice.executeShellCommand(String.format(
                PACKAGE_INFO_TEMPLATE, packageName));
        return parseUidFromDumpSys(result);
    }

    /**
     * Parse the uid from the dumpsys string.
     * <p>
     * Exposed for unit testing.
     *
     * @param dumpSysString {@link String} returned by the dumpsys command
     * @return the {@link Long} uid of the package, if found. Null, otherwise.
     */
    static Long parseUidFromDumpSys(String dumpSysString) {
        String[] lines = dumpSysString.split("\n");
        for (String line : lines) {
            String[] sections = line.split(" ");
            for (String section : sections) {
                String[] values = section.split("=");
                if (values.length != 2 || !values[0].equalsIgnoreCase("userid")) {
                    continue;
                }
                return Long.parseLong(values[1].trim());
            }
        }
        return null;
    }

    /**
     * Fetch output file from device.
     *
     * @param timeout in milliseconds
     * @param pollDelay in milliseconds
     * @return the {@link File} of the output file, null if unavailable.
     * @throws DeviceNotAvailableException
     */
    private File fetchOutputFile(long timeout, long pollDelay) throws DeviceNotAvailableException {
        long endTime = System.currentTimeMillis() + timeout;
        try {
            // Wait for the File to become available.
            // FIXME: not the most efficient way to do this.
            while (System.currentTimeMillis() < endTime) {
                if (mTestDevice.doesFileExist(mOutputFilePath)) {
                    break;
                }
                Thread.sleep(pollDelay);
            }
        } catch (InterruptedException e) {
            CLog.e("Failed to fetch output file");
            CLog.e(e);
            return null;
        }
        return mTestDevice.pullFileFromExternal(mOutputFile);
    }

    /**
     * Pull the output file from the device, and checks for the connectivity tests.
     *
     * @return true if the app is unable to connect to the Internet, false otherwise.
     */
    private boolean parseOutputFile() throws DeviceNotAvailableException {
        File outputFile = fetchOutputFile(FETCH_FILE_TIMEOUT_MS, FETCH_FILE_POLL_MS);
        if (outputFile == null) {
            CLog.w("Failed to fetch output file: %s", mOutputFile);
            return false;
        }
        InputStreamSource outputSource = null;
        boolean success = true;
        try {
            FileInputStream fs = new FileInputStream(outputFile);
            BufferedInputStream dataStream = new BufferedInputStream(fs);
            String contents = StreamUtil.getStringFromStream(dataStream);
            List<String> lines = Arrays.asList(contents.split("\n"));
            for (String line : lines) {
                String[] segments = line.split(":");
                if (segments.length != 2) {
                    CLog.w("Invalid value parsed from %s: %s", outputFile.toString(), line);
                    continue;
                }
                if (segments[1].equalsIgnoreCase("fail")) {
                    CLog.i("Test %s failed! We were able to send data.", segments[0]);
                    success = false;
                } else if (segments[1].equalsIgnoreCase("pass")) {
                    CLog.i("Test %s passed! We were unable to send data.", segments[0]);
                } else {
                    CLog.w("Invalid value parsed from %s: %s", outputFile.toString(), line);
                    continue;
                }
            }
        } catch (IOException e) {
            CLog.e(e);
        } finally {
            FileUtil.deleteFile(outputFile);
            StreamUtil.cancel(outputSource);
        }
        return success;
    }

    /**
     * Report tests results by creating an empty test run to stick them in.
     *
     * @param listener the {@link ITestInvocationListener} of test results
     * @param runName the test name
     * @param metrics the {@link Map} that contains metrics for the given test
     */
    void reportMetrics(ITestInvocationListener listener, String runName,
            Map<String, String> metrics) {
        // Create an empty testRun to report the parsed runMetrics
        CLog.d("About to report metrics: %s", metrics);
        listener.testRunStarted(runName, 0);
        listener.testRunEnded(0, metrics);
    }

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }
}
