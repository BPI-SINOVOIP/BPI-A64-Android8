// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

/**
 * Cleanup the Gmail inbox by removing all the conversations into trash
 */
@OptionClass(alias = "google-gmail-inbox-cleanup")
public class GmailInboxCleanUpUtil implements ITargetPreparer {
    private static final String INSTRUMENTATION_CLASS = "com.google.android.gm.provider." +
            "GmailConversationsLabelUtil";
    static final String ACCOUNT_PKG_NAME = "com.google.android.gmtests";

    @Option(name = "account-name", description = "the full name of Google account to configure.",
            importance = Importance.IF_UNSET)
    private String mAccountName = null;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException, BuildError {
        if (mAccountName != null) {
            buildInfo.addBuildAttribute("account", mAccountName);
        }

        if (!cleanInbox(device)) {
            throw new TargetSetupError(String.format(
                    "Failed to clean inbox for account %s on device %s",
                    mAccountName, device.getSerialNumber()), device.getDeviceDescriptor());
        }
    }

    /**
     * Sets Gmail account will be updated.
     */
    public void setAccount(String accountName) {
        mAccountName = accountName;
    }

    /**
     * For the specified gmail account move all the conversations from inbox to trash
     *
     * @param device the {@link ITestDevice}
     * @return <code>true</code> if account was created successfully, <code>false</code>
     *         otherwise
     * @throws TargetSetupError
     */
    boolean cleanInbox(ITestDevice device) throws DeviceNotAvailableException, TargetSetupError {
        if (mAccountName != null) {
            CLog.i("Cleaning inbox %s on device %s", mAccountName, device.getSerialNumber());
            String instrCmd = String.format(
                    "am instrument -w -e account \"%s\" -e from_label ^i -e to_label ^k %s/%s",
                    mAccountName, ACCOUNT_PKG_NAME, INSTRUMENTATION_CLASS);

            CLog.v(instrCmd);
            String result = device.executeShellCommand(instrCmd);
            // expected result format is (on error):
            //
            // INSTRUMENTATION_RESULT: [errorCode=X]
            // INSTRUMENTATION_CODE: -1
            //
            // where errorCode will be present only on failure
            if (result.contains("errorCode")) {
                reportFailure(device, mAccountName, result);
                return false;
            } else if (!result.contains("INSTRUMENTATION_CODE")) {
                // this should never happen
                reportFailure(device, mAccountName, "Instrumentation run did not complete: " +
                        result);
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * @return the {@link IRunUtil} to use
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    private void reportFailure(ITestDevice device, String accountName, String reason) {
        CLog.w("Failed to cleanup inbox %s to device %s: %s", accountName, device.getSerialNumber(),
                reason);
    }
}
