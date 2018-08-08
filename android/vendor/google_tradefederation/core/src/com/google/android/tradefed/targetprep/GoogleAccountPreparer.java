// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.google.android.tradefed.util.GoogleAccountUtil;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.DeviceFailedToBootError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.WaitForDeviceDatetimePreparer;

/**
 * A {@link ITargetPreparer} that supports configuring google accounts.
 * If no account is specified and gaia account creation is enable, then a gaia account will be
 * created and added to the device.
 */
@OptionClass(alias = "google-account")
public class GoogleAccountPreparer extends WaitForDeviceDatetimePreparer implements ITargetCleaner {

    @Option(name = "initial-wait-time", description = "initial wait in ms between failed add " +
            "account attempts. Wait time will increase quadratically for each attempt")
    private long mInitialWaitTimeMs = 2000;

    @Option(name = "max-attempts", description = "maximum number of add account attempts.")
    private int mMaxAttempts = 5;

    @SuppressWarnings("unused") // kept for backwards compatibility
    @Deprecated
    @Option(name="wait-for-data", description ="wait for data connectivity before adding account."
            + " (deprecated, data connection no longer verified here)")
    private boolean mWaitForData = false;

    @Option(name = "account-name", description = "the full name of Google account to configure.",
            importance = Importance.IF_UNSET)
    private String mAccountName = null;

    @Option(name = "account-password", description = "the password of the Google account " +
            "(specified in account-name) to configure.", importance = Importance.IF_UNSET)
    private String mAccountPassword = null;

    @Option(name = "account-sync", description = "enable sync on Google account.")
    private boolean mAccountSync = false;

    @Option(name = "wait-for-checkin", description =
            "wait for checkin to complete after adding Google account.")
    private boolean mWaitForCheckin = false;

    @Option(name = "sync-then-off", description =
            "after adding account, allow for sync then turn sync off, in secs")
    private long mSyncThenOff = 0;

    @Option(name = "remove-accounts", description =
            "Remove all accounts on test completion")
    private boolean mRemoveAccounts = false;

    @Option(name = "remove-account", description =
            "Remove account that was added on test completion")
    private boolean mRemoveAccount = false;

    @Option(name = "wait-for-sync", description =
            "Wait for the account to finish all sync operations before proceeding.")
    private boolean mWaitForSync = false;

    @Option(name = "wait-for-sync-timeout", description =
            "The timeout associated with wait-for-sync in milliseconds.")
    private long mWaitForSyncTimeout = 5 * 60 * 1000;

    @Option(name = "wait-for-sync-poll-interval", description =
            "The polling interval associated with wait-for-sync in milliseconds.")
    private long mWaitForSyncInterval = 10 * 1000;

    @Option(name = "disable", description = "Disable the Google account preparer.")
    private boolean mDisable = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException, BuildError {
        // don't run this preparer if it's disabled
        if(mDisable) {
            return;
        }
        if (mAccountName == null && mAccountPassword == null) {
            // Keeping the previous behavior for backward compatibility
            CLog.i("No account name or password specified, skipping GooglePreparer.");
            return;
        }
        // throw for the error state where account name is defined and account password is not
        if (mAccountName != null && mAccountPassword == null) {
            throw new TargetSetupError("account-password is not defined",
                    device.getDeviceDescriptor());
        }

        buildInfo.addBuildAttribute("account", mAccountName);

        super.setUp(device, buildInfo);

        if (!GoogleAccountUtil.installUtil(device)) {
            throw new TargetSetupError(String.format(
                    "Failed to install account util on device %s", device.getSerialNumber()),
                    device.getDeviceDescriptor());
        }

        try {
            // remove accounts first just in case
            if (mRemoveAccounts && !GoogleAccountUtil.removeAccountAttempt(device, mAccountName)) {
                throw new TargetSetupError(String.format(
                        "Failed to remove accounts on device %s", device.getSerialNumber()),
                        device.getDeviceDescriptor());
            }

            if (!addAccount(device)) {
                throw new TargetSetupError(String.format(
                        "Failed to add account %s on device %s after %d attempts",mAccountName,
                        device.getSerialNumber(), mMaxAttempts), device.getDeviceDescriptor());
            }

            if (mWaitForSync && !GoogleAccountUtil.waitForAccountSync(
                    device, mWaitForSyncInterval, mWaitForSyncTimeout)) {
                CLog.w("Not all sync operations are complete before continuing.");
            }
        } finally {
            GoogleAccountUtil.uninstallUtil(device);
        }
    }

    /**
     * Sets the Google account to create.
     */
    public void setAccount(String accountName, String accountPassword) {
        mAccountName = accountName;
        mAccountPassword = accountPassword;
    }

    /**
     * Sets whether to enable sync on the account
     */
    public void setAccountSync(boolean value) {
        mAccountSync = value;
    }

    /**
     * Sets whether to wait for checkin to complete after adding account
     */
    public void setWaitForCheckin(boolean value) {
        mWaitForCheckin = value;
    }

    /**
     * Sets whether to enable sync on the account
     */
    public void setSyncThenOff(long syncThenOff) {
        mSyncThenOff = syncThenOff;
    }

    /**
     * Add account specified in options to device, attempting multiple times if necessary.
     * Does nothing if no account is specified.
     * <p/>
     * Exposed for unit testing.
     *
     * @param device the {@link ITestDevice}
     * @return <code>true</code> if account was created successfully, <code>false</code> otherwise
     * @throws TargetSetupError
     */
    boolean addAccount(ITestDevice device) throws DeviceNotAvailableException, TargetSetupError {
        CLog.i("Adding account %s on device %s", mAccountName, device.getSerialNumber());
        for (int i = 0; i < mMaxAttempts; i++) {
            if (GoogleAccountUtil.addAccountAttempt(device, mAccountName, mAccountPassword,
                    mAccountSync, mWaitForCheckin)) {
                if (mSyncThenOff != 0 && mAccountSync) {
                    // if sync and off is set and the sync is On on the account
                    if (!GoogleAccountUtil.syncAndOff(device, mAccountName, mSyncThenOff)) {
                        CLog.w("Failed to turn off sync");
                    }
                }
                return true;
            }

            // pause a short, escalating time before retrying
            long increaseFactor = (long)Math.pow(2, i);
            getRunUtil().sleep(mInitialWaitTimeMs * increaseFactor);
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mDisable) {
            return;
        }
        if (e instanceof DeviceNotAvailableException || e instanceof DeviceFailedToBootError) {
            return;
        }
        removeAccount(device);
    }

    /**
     * Remove the account from the device provided.
     * Remove all accounts from the device if --remove-accounts flag is on.
     * @param device target to remove the account(s)
     * @throws DeviceNotAvailableException
     */
    void removeAccount(ITestDevice device) throws DeviceNotAvailableException {
        if (mRemoveAccounts || mRemoveAccount) {
            if (!GoogleAccountUtil.installUtil(device)) {
                // cannot remove accounts! Bad things may happen, just remove device from queue for
                // now
                throw new DeviceNotAvailableException(String.format(
                        "Failed to install account util on device %s", device.getSerialNumber()),
                        device.getSerialNumber());
            }
            try {
                if (mRemoveAccounts) {
                    if (!GoogleAccountUtil.removeAllAccountAttempt(device)) {
                        // cannot remove accounts! Bad things may happen
                        // just remove device from queue for now
                        throw new DeviceNotAvailableException(String.format(
                                "Failed to remove accounts on device %s",
                                device.getSerialNumber()), device.getSerialNumber());
                    }
                } else {
                    if (mRemoveAccount) {
                        if (!GoogleAccountUtil.removeAccountAttempt(device, mAccountName)) {
                            // cannot remove accounts! Bad things may happen
                            // just remove device from queue for now
                            throw new DeviceNotAvailableException(String.format(
                                    "Failed to remove accounts on device %s",
                                    device.getSerialNumber()), device.getSerialNumber());
                        }
                    }
                }
            } finally {
                GoogleAccountUtil.uninstallUtil(device);
            }
        }
    }

    void setRemoveAccount(boolean b) {
        mRemoveAccount = b;
    }

    protected String getAccountName() {
        return mAccountName;
    }

    protected String getAccountPassword() {
        return mAccountPassword;
    }
}
