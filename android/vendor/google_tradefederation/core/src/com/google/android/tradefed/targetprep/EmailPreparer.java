// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.util.concurrent.TimeUnit;

/**
 * An {@link ITargetPreparer} which adds an email account for the email
 * application on the device.
 */
@OptionClass(alias = "email-account")
public class EmailPreparer implements ITargetPreparer {
    private static final String EMAIL_AOSP_PACKAGE =
            "com.android.email";
    private static final String EMAIL_GOOGLE_PACKAGE =
            "com.google.android.email";
    private static final String EMAIL_CMD_TEMPLATE =
            "am start -a %s.FORCE_CREATE_ACCOUNT -e EMAIL %s -e PASSWORD %s %s";
    private static final String SYNC_WINDOW_ALL = "-e SYNC_LOOKBACK ALL";
    private static final String CHECK_EMAIL_RESULT_TEMPLATE =
            "sqlite3 /data/data/%s/databases/EmailProvider.db" +
                    " \"select count(*) from Account where emailAddress='%s';\"";

    @Option(name = "email-username", description = "The email address")
    private String mEmailUsername = null;

    @Option(name = "email-password", description = "The password")
    private String mEmailPassword = null;

    @Option(name = "email-sync-all", description = "Whether to sync all mail")
    private boolean mSyncWindowAll = true;

    @Option(name = "google-version",
            description = "Whether the Email APK we want to invoke is Google's version")
    private boolean mGoogleVersion = false;

    @Option(name = "max-attempts", description = "maximum number of add account attempts.")
    private int mMaxAttempts = 5;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws DeviceNotAvailableException, TargetSetupError {
        if (mEmailUsername != null && mEmailPassword != null) {
            // enable Email logs for debugging
            device.executeShellCommand("setprop log.tag.Email VERBOSE");

            CLog.i("Adding email account %s", mEmailUsername);
            addEmailAccount(device, mEmailUsername, mEmailPassword);
        }
    }

    /**
     * Add an email account to the device.
     */
    private void addEmailAccount(ITestDevice device, String username, String password)
            throws DeviceNotAvailableException, TargetSetupError {
        if (username.split("@").length != 2) {
            CLog.e("Got invalid email username %s to add.", username);
            return;
        }

        String emailCmd = String.format(EMAIL_CMD_TEMPLATE,
                mGoogleVersion ? EMAIL_GOOGLE_PACKAGE : EMAIL_AOSP_PACKAGE, username, password,
                mSyncWindowAll ? SYNC_WINDOW_ALL : "").trim();

        for (int i = 0; i < mMaxAttempts; i++) {
            CLog.i("About to run email command %s", emailCmd);
            CollectingOutputReceiver collectingOutputReceiver = new CollectingOutputReceiver();
            device.executeShellCommand(emailCmd, collectingOutputReceiver,
                    5 * 60 * 1000, TimeUnit.MILLISECONDS, 0);
            String output = collectingOutputReceiver.getOutput().trim();
            CLog.d("Output from add account command: %s", output);

            if (isEmailAccountAdded(device, mEmailUsername)) {
                return;
            }
        }

        throw new TargetSetupError(String.format(
                "Failed to add account %s on device %s after %d attempts",
                username, device.getSerialNumber(), mMaxAttempts), device.getDeviceDescriptor());
    }

    /**
     * Issues a query to Email's sqlite database to check whether the target
     * Email account was added successfully.
     *
     * @param device the {@link ITestDevice}
     * @param username The target Email Address to add
     * @return <code>true</code> if the account was added successfully, <code>false
     *         </code> otherwise
     */
    private boolean isEmailAccountAdded(ITestDevice device, String username)
            throws DeviceNotAvailableException {
        final String checkResultCmd = String.format(CHECK_EMAIL_RESULT_TEMPLATE,
                mGoogleVersion ? EMAIL_GOOGLE_PACKAGE : EMAIL_AOSP_PACKAGE,
                username);
        String output = null;
        for (int i = 0; i < 5; i++) {
            // We need some time for the database transaction to complete. With
            // that said it is still possible that we fail during our first run
            // due to the database still being locked for a long period of time,
            // hence why we sleep every iteration before making the check
            getRunUtil().sleep(10 * 1000);

            CollectingOutputReceiver collectingOutputReceiver = new CollectingOutputReceiver();
            CLog.i("About to run email command %s", checkResultCmd);
            device.executeShellCommand(checkResultCmd, collectingOutputReceiver,
                    5 * 60 * 1000, TimeUnit.MILLISECONDS, 0);
            output = collectingOutputReceiver.getOutput().trim();
            CLog.d("Output from sqlite query: %s", output);

            // Return true if there was no error and the query returns 1 account
            if (!output.contains("Error") && "1".equals(output)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get a {@link IRunUtil} instance. Exposed for unit testing.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
