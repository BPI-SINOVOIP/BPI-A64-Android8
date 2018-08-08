// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * A Utility that supports configuring google accounts on a device
 */
public class GoogleAccountUtil {
    private static final String ADD_ACCOUNT_INSTRUMENTATION_CLASS = ".AddAccount";
    private static final String REMOVE_ACCOUNTS_INSTRUMENTATION_CLASS = ".RemoveAccounts";
    private static final String REMOVE_ACCOUNT_INSTRUMENTATION_CLASS = ".RemoveAccount";
    private static final String GOOGLE_ACCOUNT_SYNC_INSTRUMENTATION_CLASS =
            ".GoogleAccountSync";

    private static final String GOOGLE_ACCOUNT_SYNC_COMMAND =
            "am instrument -w -e interval %d -e timeout %d %s/%s";

    public static final String ACCOUNT_PKG_NAME = "com.google.android.tradefed.account";
    public static final String CHECK_INSTRUMENTATION_CMD =
            String.format("pm list instrumentation %s", ACCOUNT_PKG_NAME);
    public static final String UTIL_APK_NAME = "GoogleAccountUtil";

    /**
     * Install the account utility apk contained in jar if necessary
     *
     * @param device on which to install the utility.
     * @throws DeviceNotAvailableException
     */
    public static boolean installUtil(ITestDevice device) throws DeviceNotAvailableException {
        if (isAccountUtilInstalled(device)) {
            return true;
        } else {
            // Attempt to install utility
            File apkTempFile = null;
            try {
                apkTempFile = FileUtil.createTempFile(UTIL_APK_NAME, ".apk");
                InputStream apkStream = GoogleAccountUtil.class.getResourceAsStream(
                        String.format("/apks/%s.apk", UTIL_APK_NAME));
                FileUtil.writeToFile(apkStream, apkTempFile);

                CLog.i("Installing %s on %s", UTIL_APK_NAME, device.getSerialNumber());
                final String result = device.installPackage(apkTempFile, false);
                if (result != null) {
                    CLog.e("Unable to install AccountUtil utility: %s", result);
                    return false;
                }
            } catch (IOException e) {
                CLog.e("Failed to unpack AccountUtil utility: %s", e.getMessage());
                CLog.e(e);
                return false;
            } finally {
                FileUtil.deleteFile(apkTempFile);
            }
            return isAccountUtilInstalled(device);
        }
    }

    /**
     * Uninstall the account utility apk contained in jar
     *
     * @param device to uninstall the account utility from.
     * @throws DeviceNotAvailableException
     */
    public static boolean uninstallUtil(ITestDevice device) throws DeviceNotAvailableException {
        if (device.uninstallPackage(ACCOUNT_PKG_NAME) != null) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Check if the account utility is currently installed
     *
     * @param device on which to check if the account utility is installed
     * @throws DeviceNotAvailableException
     */
    public static boolean isAccountUtilInstalled(ITestDevice device)
            throws DeviceNotAvailableException {
        final String inst = device.executeShellCommand(CHECK_INSTRUMENTATION_CMD);
        if ((inst != null) && inst.contains(ACCOUNT_PKG_NAME)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Builds the instrumentation command to add the account
     *
     * @param accountName name of the account to be targetted by the command
     * @param password associated with the account
     * @param sync flag to enable account sync after log in with the account
     * @param waitForCheckin true to enable wait for checking after adding the account
     */
    private static String buildAddAccountCmd(String accountName, String password, boolean sync,
            boolean waitForCheckin) {
        return String.format(
                "am instrument -w -e account \"%s\" -e password \"%s\" -e sync %s " +
                        "-e wait-for-checkin %s %s/%s",
                        accountName, password, Boolean.toString(sync),
                        Boolean.toString(waitForCheckin),
                        ACCOUNT_PKG_NAME, ADD_ACCOUNT_INSTRUMENTATION_CLASS);
    }

    /**
     * Attempt to configures device under test with given Google account.
     * <p/>
     * Account must already exist on server.
     * <p/>
     * Exposed for unit testing.
     *
     * @param device the {@link ITestDevice}
     * @param accountName full name of Google account to add
     * @param password password of account
     * @param sync <code>true</code> if automatic sync should be enabled for this account
     * @param waitForCheckin <code>true</code> to wait for checkin after
     *            adding account
     * @return <code>true</code> if account was created successfully, <code>false</code> otherwise
     */
    public static boolean addAccountAttempt(ITestDevice device, String accountName, String password,
            boolean sync, boolean waitForCheckin) throws DeviceNotAvailableException {
        String instrCmd = buildAddAccountCmd(accountName, password, sync, waitForCheckin);
        return runAddAccountInstrumentation(device, instrCmd, accountName);
    }

    /**
     * Utility function for running the AddAccount instrumentation
     */
    private static boolean runAddAccountInstrumentation(ITestDevice device,
            String instrCmd, String accountName) throws DeviceNotAvailableException {
        CLog.v(instrCmd);
        String result = device.executeShellCommand(instrCmd);
        // expected result format is (on error):
        //
        // INSTRUMENTATION_RESULT: result=SUCCESS
        // INSTRUMENTATION_CODE: -1
        //
        // where errorCode will be present only on failure
        if (result.contains("result=SUCCESS")) {
            return true;
        } else {
            reportFailure(device, accountName, "Failed to add account: " + result);
            return false;
        }
    }

    /**
     * Builds the instrumentation command to turn off sync
     */
    private static String buildSyncOffCmd(String accountName) {
        return String.format("am instrument -w -e account \"%s\" -e sync false %s/%s",
                accountName, ACCOUNT_PKG_NAME, ADD_ACCOUNT_INSTRUMENTATION_CLASS);
    }

    /**
     * Builds the instrumentation command to turn off sync
     */
    private static String buildSyncOnCmd(String accountName) {
        return String.format("am instrument -w -e account \"%s\" -e sync true %s/%s",
                accountName, ACCOUNT_PKG_NAME, ADD_ACCOUNT_INSTRUMENTATION_CLASS);
    }

    private static void reportFailure(ITestDevice device, String accountName, String reason) {
        CLog.w("Failed to add account %s to device %s: %s", accountName, device.getSerialNumber(),
                reason);
    }

    private static void reportRemoveFailure(ITestDevice device, String reason) {
        CLog.w("Failed to remove accounts from device %s: %s", device.getSerialNumber(),
                reason);
    }

    /**
     * Remove the single account specified
     * @param device
     * @return true if removal is a success, false otherwise.
     * @throws DeviceNotAvailableException
     */
    public static boolean removeAccountAttempt(ITestDevice device, String accountName)
            throws DeviceNotAvailableException {
        String removeCmd = "";
        removeCmd = String.format("am instrument -w -e account \"%s\" %s/%s",
                accountName, ACCOUNT_PKG_NAME,
                REMOVE_ACCOUNT_INSTRUMENTATION_CLASS);

        CLog.v(removeCmd);
        String result = device.executeShellCommand(removeCmd);
        // expected result format is (on success):
        //
        // INSTRUMENTATION_RESULT: result=SUCCESS
        // INSTRUMENTATION_CODE: -1
        //
        if (result.contains("result=SUCCESS")) {
            return true;
        } else {
            reportRemoveFailure(device, result);
            return false;
        }
    }

     /**
      * Remove  all accounts of the device
      * @param device
      * @return true if removal is a success, false otherwise.
      * @throws DeviceNotAvailableException
      */
     public static boolean removeAllAccountAttempt(ITestDevice device)
             throws DeviceNotAvailableException {
        String removeCmd = "";
        removeCmd = String.format("am instrument -w %s/%s", ACCOUNT_PKG_NAME,
                REMOVE_ACCOUNTS_INSTRUMENTATION_CLASS);

        CLog.v(removeCmd);
        String result = device.executeShellCommand(removeCmd);
        // expected result format is (on success):
        //
        // INSTRUMENTATION_RESULT: result=SUCCESS
        // INSTRUMENTATION_CODE: -1
        //
        if (result.contains("result=SUCCESS")) {
            return true;
        } else {
            reportRemoveFailure(device, result);
            return false;
        }
    }

    /**
     * Turns off syncing with the given Google account.
     * Assume syncing is On.
     *
     * @param device the {@link ITestDevice}
     * @param accountName full name of Google account to turn sync off
     * @param syncThenOff seconds to allow sync to run before turning it off
     * @return <code>true</code> if the sync was turned off successfully
     * @throws DeviceNotAvailableException
     */
    public static boolean syncAndOff(ITestDevice device, String accountName, long syncThenOff)
             throws DeviceNotAvailableException {
        CLog.i("Sleeping for %d seconds to allow for syncing...", syncThenOff);
        getRunUtil().sleep(syncThenOff * 1000);
        CLog.i("Done sleeping. Turning sync off...");
        String instrCmd = buildSyncOffCmd(accountName);
        return runAddAccountInstrumentation(device, instrCmd, accountName);
    }

    /**
     * Turns on syncing with the given Google account.
     * Assume syncing is false;
     *
     * @param device the {@link ITestDevice}
     * @param accountName full name of Google account to turn sync on
     * @return <code>true</code> if the sync was turned off successfully
     * @throws DeviceNotAvailableException
     */
    public static boolean syncOn(ITestDevice device, String accountName)
            throws DeviceNotAvailableException {
        String instrCmd = buildSyncOnCmd(accountName);
        return runAddAccountInstrumentation(device, instrCmd, accountName);
    }

    /**
     */
    public static boolean waitForAccountSync(ITestDevice device, long interval, long timeout)
            throws DeviceNotAvailableException {
        String instrCmd = String.format(GOOGLE_ACCOUNT_SYNC_COMMAND,
                interval, timeout, ACCOUNT_PKG_NAME, GOOGLE_ACCOUNT_SYNC_INSTRUMENTATION_CLASS);
        CLog.v(instrCmd);
        String result = device.executeShellCommand(instrCmd);
        // Success result format:
        //
        // INSTRUMENTATION_RESULT: result=SUCCESS
        // INSTRUMENTATION_CODE: -1
        //
        if (result.contains("result=SUCCESS")) {
            return true;
        } else {
            reportRemoveFailure(device, result);
            return false;
        }
    }

    /**
     * @return the {@link IRunUtil} to use
     */
    private static IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
