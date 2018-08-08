// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.gcore.backup;

import com.android.tradefed.build.IAppBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * These are the E2E tests for Backup and Restore API.
 *
 * The tests are using a backup test app (com.google.android.gms.testapp.backupdollytestapp) to
 * generate a test data for the backup. Please see go/bhihy for more details.
 */
public class BackupRestoreHostTests extends DeviceTestCase implements IBuildReceiver {

    /**
     * BackupDollyApp apk name prefix.
     */
    private static final String BACKUP_DOLLY_APK_PREFIX = "BackupDollyApp";

    /**
     * Backup test app ID.
     */
    private final static String TEST_APP_ID = "com.google.android.gms.testapp.backupdollytestapp";

    /**
     * The test app private storage.
     */
    private static final String TESTAPP_FILES = "/data/data/" + TEST_APP_ID + "/files";

    /**
     * Test file name.
     */
    private static final String TEST_FILE_1K = "file1k";
    private static final String TEST_FILE_1K_TXT = TEST_FILE_1K + ".txt";

    /**
     * Backup test app activity that is used to create test data files.
     */
    private static final String
            ADD_FILE_ACTIVITY = "com.google.android.gms.testapp.backupdollytestapp.AddFileActivity";

    /**
     * An adb command template for creating test data files.
     */
    private static final String ADD_FILE_COMMAND_TEMPLATE =
            "am start -a android.intent.action.MAIN " + "-c android.intent.category.LAUNCHER "
            + "-n " + TEST_APP_ID + "/" + ADD_FILE_ACTIVITY + " -e file_name %s "
            + " -e file_size_in_bytes %d";

    /**
     * Max time to wait for a backup result message in the logcat.
     */
    private static final int BACKUP_TIMEOUT_SECONDS = 180;

    /** Wifi reconnect timeout in ms. */
    private static final int WIFI_RECONNECT_TIMEOUT_MS = 120 * 1000;

    /** Wifi reconnect check interval in ms. */
    private static final int WIFI_RECONNECT_CHECK_INTERVAL_MS = 10 * 1000;

    /**
     * Small delay while reading the logcat.
     */
    private static final int SMALL_DELAY = 1000;

    /**
     * Max time to wait for a test file to be created.
     */
    private static final int FILE_CREATE_DELAY_MS = 15 * 1000;

    private IBuildInfo mBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CLog.d("setup " + getName());

        assertTrue(getDevice().enableAdbRoot());

        // Clear the logcat log and start capturing.
        getDevice().clearLogcat();
        CLog.d("Cleared logs");

        // Sync time
        setupDateAndTime();

        assertNetworkConnectivityIsGood();

        // Clean up test app data. The test case will create new data files.
        removeDollyTestAppPrivateFiles();

        // Verify backup account is set and the transport is available.
        assertBackupTransportIsPresent();
    }

    @Override
    protected void tearDown() throws Exception {
        String testCaseName = getName();
        String logFileName = testCaseName + "_log.txt";
        executeShellCommand(String.format(
            "mkdir -p /sdcard/Download; cd /sdcard/Download; touch %s; " +
            "logcat -v threadtime -d > %s",
            logFileName, logFileName));
        File logFile = FileUtil.createTempFile(testCaseName, ".txt");
        getDevice().pullFile("/sdcard/Download/" + logFileName, logFile);
        CLog.i("Saved log: " + logFile.getAbsolutePath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = buildInfo;
    }

    /**
     * Try to back-up a blob that is larger than the max allowed blob size, and verify through
     * logcat that it doesn’t get backed-up
     */
    public void testBackup_NotExceedingMaxBlobSize_Succeeds() throws Exception {
        // Create a test app has a file not exceeding the max Blob size.
        createTestDataFileForBackup("file123.txt", 10); // 10b file

        // Request to perform a full backup.
        requestFullBackup(TEST_APP_ID);

        // Verify the backup is successful.
        String[] keys = {
                "Transport rejected backup of " + TEST_APP_ID + ", skipping",
                "GmsBackupTransport: Scotty response: res=200"
        };
        boolean[] results = waitForLogcatString(keys, BACKUP_TIMEOUT_SECONDS);
        String msg = "Backup of files not exceeding max blob size must succeed.";
        if (results[0]) {
            msg += " The ransport has rejected backup of " + TEST_APP_ID;
        }
        assertTrue(msg, results[1]);
    }

    /**
     * Try to back-up a blob that is larger than the max allowed blob size, and verify through
     * logcat that it doesn’t get backed-up
     */
    public void testBackup_ExceedingMaxBlobSize_IsRejected() throws Exception {
        // Create a test app has a file exceeding max Blob size.
        createTestDataFileForBackup("file123.txt", 6291456); // 6Mb file

        // Request a full backup.
        requestFullBackup(TEST_APP_ID);

        // Verify the backup is rejected because of the size.
        assertLogcatContains("Backup of files exceeding max blob size must be rejected.",
                "GmsBackupTransport: Package " + TEST_APP_ID + " failed pre-flight size check");
    }

    /**
     * Try to run backup on an application with no files, and verify through
     * logcat that it doesn’t get backed-up
     */
    public void testBackup_NothingToBackup_IsRejected() throws Exception {
        // Request a full backup.
        requestFullBackup(TEST_APP_ID);

        // Verify the backup is rejected because of the size.
        assertLogcatContains(
            "Backup should be rejected when there is nothing to backup",
            "Package " + TEST_APP_ID + " doesn't have any backup data.");
    }

    /**
     * Try to run a backup two times in a row with the same file.
     * The second try should send metadata only (about 5-10 bytes).
     */
    public void testBackup_SecondBackupRequestWithSameDataSendSmallMetadata() throws Exception {
        // Create a test app that has a file not exceeding the max Blob size.
        createTestDataFileForBackup("file456.txt", 100); // 100b file

        // Request to perform a full backup.
        requestFullBackup(TEST_APP_ID);

        // Verify the backup is successful.
        String[] keys = {
                "Transport rejected backup of " + TEST_APP_ID + ", skipping",
                "GmsBackupTransport: Scotty response: res=200"
        };
        boolean[] results = waitForLogcatString(keys, BACKUP_TIMEOUT_SECONDS);
        String msg = "Backup of files not exceeding max blob size must succeed.";
        if (results[0]) {
            msg += " The transport has rejected backup of " + TEST_APP_ID;
        }
        assertTrue(msg, results[1]);

        // Request to perform a second full backup.
        requestFullBackup(TEST_APP_ID);
        // Verify that Scotty sent a small amount of metadata.
        assertLogcatContains(
            "Second backup sent data should be small number of bytes (7-ish)",
            "Scotty bytes uploaded : 7");
    }

    /**
     * Verify that restore takes place as expected on adb install.
     */
    public void testRestore_AppPrivateData_IsRestored_After_Reinstall() throws Exception {
        // Create a test app file that does not exceed the max Blob size.
        createTestDataFileForBackup(TEST_FILE_1K_TXT, 1024); // 1k file

        // Request a full backup.
        requestFullBackup(TEST_APP_ID);

        assertNetworkConnectivityIsGood();

        // Verify the backup is successful.
        String key = "GmsBackupTransport: Scotty response: res=200";
        assertLogcatContains("Backup of " + TEST_FILE_1K_TXT + " has failed.", key);

        // Pull the file and keep it as an original for further verification.
        File testFile = FileUtil.createTempFile(TEST_FILE_1K, "_1.txt");
        getDevice().pullFile(TESTAPP_FILES + "/" + TEST_FILE_1K_TXT, testFile);
        CLog.i("Pulled the original test file: " + testFile.getAbsolutePath());

        // Find the apk file and reinstall the app.
        IAppBuildInfo appBuild = (IAppBuildInfo) mBuild;
        List<VersionedFile> files = appBuild.getAppPackageFiles();
        File backupDollyAppApkFile = null;
        for (VersionedFile file: files) {
            String fileName = file.getFile().getName();
            CLog.d("file: " + fileName);
            if (fileName.startsWith(BACKUP_DOLLY_APK_PREFIX)) {
                backupDollyAppApkFile = file.getFile();
                break;
            }
        }
        assertNotNull("The BackupDollyApp should be in the list of apps for this test.",
                backupDollyAppApkFile);

        // Uninstall the test app apk.
        String uninstallResult = getDevice().uninstallPackage(TEST_APP_ID);
        assertNull("BackupDolly test app uninstall has failed.", uninstallResult);

        assertNetworkConnectivityIsGood();

        // Install the test app apk. The Dolly backup framework should restore the app data
        // automatically.
        String installResult = getDevice().installPackage(backupDollyAppApkFile, true,
                new String[] {});
        assertNull("BackupDolly test app install has failed.", installResult);
        RunUtil.getDefault().sleep(BACKUP_TIMEOUT_SECONDS);

        assertFalse("The restore of " + TEST_APP_ID + " has failed.",
                waitForLogcatString("BackupManagerService: Timeout restoring application " +
                        TEST_APP_ID, BACKUP_TIMEOUT_SECONDS));

        String restoredFiles = getDevice().executeShellCommand("ls -l " + TESTAPP_FILES);
        CLog.i("Restored files: " + restoredFiles);
        assertTrue("The backup file has not been restored: " + restoredFiles,
                restoredFiles.contains(TEST_FILE_1K_TXT));

        // Verify that the app data is restored.
        assertLogcatContains("Restore should complete.",
                "GmsBackupTransport: Reach end of http content -- NO MORE DATA");
        // Pull the restored file in order to compare it with the original.
        File restoredTestFile = FileUtil.createTempFile(TEST_FILE_1K, "_2.txt");
        getDevice().pullFile(TESTAPP_FILES + "/" + TEST_FILE_1K_TXT, restoredTestFile);
        CLog.i("Pulled the restored test file: " + restoredTestFile.getAbsolutePath() +
                ", size: " + restoredTestFile.length());

        // Verify the original and restored files are the same.
        assertTrue("The original and restored files are different",
                FileUtil.compareFileContents(testFile, restoredTestFile));
    }

    /**
     * Asserts the device is connected to the network and its quality is acceptable.
     *
     * @throws Exception
     */
    public void assertNetworkConnectivityIsGood() throws Exception {
        long startTime = System.currentTimeMillis();
        boolean isConnected = getDevice().checkConnectivity();
        while(!isConnected && (System.currentTimeMillis() - startTime) < WIFI_RECONNECT_TIMEOUT_MS) {
            RunUtil.getDefault().sleep(SMALL_DELAY);
            isConnected = getDevice().checkConnectivity();
        }
        assertTrue("Poor network connectivity", isConnected);
    }

    /**
     * Parses the logcat and searches for a line containing a key
     *
     * @param key
     *            a string to search for.
     */
    public void assertLogcatContains(String errorMsg, String key) throws IOException {
        boolean keyMatchFound = waitForLogcatString(key, BACKUP_TIMEOUT_SECONDS);
        CLog.i(String.format("assertLogcatContains: %s returns %b", key, keyMatchFound));
        assertTrue(errorMsg, keyMatchFound);
    }

    /**
     * Scans a logcat for a string with a key until the string is found or until the mat timeout is
     * reached.
     *
     * @param key
     *            a string to search for.
     * @param maxTimeoutInSeconds
     *            the max time to wait for the string.
     */
    public boolean waitForLogcatString(String key, int maxTimeoutInSeconds) throws IOException {
        long timeout = System.currentTimeMillis() + maxTimeoutInSeconds * 1000;
        boolean keyMatchFound = false;
        while (timeout >= System.currentTimeMillis() && !keyMatchFound) {
            try (BufferedReader log =
                    new BufferedReader(
                            new InputStreamReader(getDevice().getLogcat().createInputStream()))) {
                String line;
                while ((line = log.readLine()) != null) {
                    if (line.contains(key)) {
                        keyMatchFound = true;
                        break;
                    }
                }
            }
            // In case the key has not been found, wait for the log to update before
            // performing the next search.
            RunUtil.getDefault().sleep(SMALL_DELAY);
        }
        return keyMatchFound;
    }

    /**
     * Scans a logcat for a string with a key until the string is found or until the max timeout is
     * reached.
     *
     * @param keys an array of strings to search for.
     * @param maxTimeoutInSeconds the max time to wait for the string.
     */
    public boolean[] waitForLogcatString(String[] keys, int maxTimeoutInSeconds)
            throws IOException {
        long timeout = System.currentTimeMillis() + maxTimeoutInSeconds * 1000;
        int numberOfKeysFound = 0;
        boolean[] keyMatchFound = new boolean[keys.length];
        while (timeout >= System.currentTimeMillis() && numberOfKeysFound < keys.length) {
            try (BufferedReader log =
                    new BufferedReader(
                            new InputStreamReader(getDevice().getLogcat().createInputStream()))) {
                String line;
                while ((line = log.readLine()) != null) {
                    for (int i = 0; i < keys.length; i++) {
                        if (line.contains(keys[i])) {
                            keyMatchFound[i] = true;
                            numberOfKeysFound++;
                            break;
                        }
                    }
                }
            }
            // In case the key has not been found, wait for the log to update before
            // performing the next search.
            RunUtil.getDefault().sleep(SMALL_DELAY);
        }
        return keyMatchFound;
    }

    /**
     * Performs a full backup of the app data.
     *
     * @param appId
     *            the application id.
     * @throws DeviceNotAvailableException
     */
    public void requestFullBackup(String appId) throws DeviceNotAvailableException, IOException {
        CLog.i("Run K/V backup.");
        executeShellCommand("bmgr backup @pm@ com.android.providers.settings");
        executeShellCommand("bmgr run");
        assertLogcatContains("Expecting the backup pass to finish",
                             "BackupManagerService: Backup pass finished.");
        CLog.i("Run test app backup.");
        executeShellCommand("bmgr fullbackup " + appId);
    }

    /**
     * Uses the DollyTestApp to create a data file.
     *
     * @param fileName a name of the file to be created.
     * @param sizeInBytes the file size in bytes.
     *
     * @throws DeviceNotAvailableException
     */
    private void createTestDataFileForBackup(String fileName, int sizeInBytes)
            throws DeviceNotAvailableException {
        // Start the backup app.
        String startBackupAppCommand = "am start -a android.intent.action.MAIN " + "-n "
                + TEST_APP_ID + "/.MainActivity";
        getDevice().executeShellCommand(startBackupAppCommand);

        // Create a backup data
        String createBackupDataExceedingMaxBlobSizeCommand = String.format(
                ADD_FILE_COMMAND_TEMPLATE, fileName, sizeInBytes);
        getDevice().executeShellCommand(createBackupDataExceedingMaxBlobSizeCommand);

        // Wait for the file to be created.
        RunUtil.getDefault().sleep(FILE_CREATE_DELAY_MS);
    }

    /**
     * A wrapper for getDevice().executeShellCommand() that logs the command and the command output.
     */
    private String executeShellCommand(String command) throws DeviceNotAvailableException {
        String result = getDevice().executeShellCommand(command);
        CLog.i(String.format("executeShellCommand: %s \n result: %s", command, result));
        return result;
    }

    /**
     * Sets the time and date on the device to be the same as on the host machine.
     * Some of the local networks, for example GoogleGuest, does not provide a valid time
     * for the device. This is a workaround to keep the host log and device logcat time in sync
     * for easy debugging.
     *
     * @throws Exception
     */
    private void setupDateAndTime() throws Exception {
        CLog.d("Setting up date and time. Current timezone and time:");
        // Display new settings
        executeShellCommand("getprop persist.sys.timezone");
        executeShellCommand("date");
        CLog.d("Changing time settings");
        executeShellCommand("settings put global auto_time_zone 0");
        executeShellCommand("settings put global auto_time 0");
        executeShellCommand("setprop persist.sys.timezone \"America/Los_Angeles\"");
        // Sample format: 20150617.161225
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd.HHmmss");
        String currentDateTime = formatter.format(new Date());
        CLog.d("Chaging date and time to " + currentDateTime);
        executeShellCommand("date -s " + currentDateTime);
        CLog.d("New time settings: ");
        // Display new settings
        executeShellCommand("getprop persist.sys.timezone");
        executeShellCommand("date");
    }

    private void removeDollyTestAppPrivateFiles() throws Exception {
        executeShellCommand(
                "rm /data/data/com.google.android.gms.testapp.backupdollytestapp/files/f*.txt");
        executeShellCommand("ls -lR /data/data/com.google.android.gms.testapp.backupdollytestapp");
    }

    /**
     * Checks to see that BackupTransport is available. It is required for backup and restore
     * operations.
     * @throws Exception
     */
    private void assertBackupTransportIsPresent() throws Exception {
        String transports = getDevice().executeShellCommand("bmgr list transports");
        CLog.i("Backup transports: " + transports);
        assertTrue("The BackupTransportService is not available.",
                   transports.contains("com.google.android.gms/.backup.BackupTransportService"));
    }

}
