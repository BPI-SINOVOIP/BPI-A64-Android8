// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.tradefed.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.Instrumentation;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;

/**
 * An instrumentation utility to remove all Google accounts from device adb shell am instrument -w
 * com.google.android.tests.utilities/.RemoveAccounts
 */
public class RemoveAccounts extends Instrumentation {

    private static final String TAG = "RemoveAccounts";

    private static final boolean IS_GB_OR_OLDER = (Build.VERSION.SDK_INT <= 10);

    private boolean mIsFirstFailure = true;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        super.onStart();

        Log.d(TAG, "Attempting to remove Google accounts");
        Bundle results = new Bundle();

        AccountManager accountManager = AccountManager.get(getContext());
        Account[] accounts = accountManager.getAccountsByType(AddAccount.GOOGLE_ACCOUNT);
        for (Account account : accounts) {
            Log.i(TAG, String.format("Removing account %s", account.name));
            RemoveCallback callback = new RemoveCallback();
            accountManager.removeAccount(account, callback, null /* handler */);
            if (!callback.waitForRemoveCompletion()) {
                // Assume the failure is for the primary account if this is the first failure
                // for GB and older devices and the error message is null.
                if (IS_GB_OR_OLDER && mIsFirstFailure && callback.getErrorMessage() == null) {
                    mIsFirstFailure = false;
                    continue;
                }
                results.putString("error", String.format("Failed to remove account %s: Reason: %s",
                        account.name, callback.getErrorMessage()));
                finish(Activity.RESULT_CANCELED, results);
                return;
            }
        }
        results.putString("result", "SUCCESS");
        finish(Activity.RESULT_OK, results);
    }

    static class RemoveCallback implements AccountManagerCallback<Boolean> {
        /** stores the result of account removal. null means not finished */
        private Boolean mResult = null;

        private String mErrorMessage = null;

        /**
         * Block and wait for the remove callback to complete.
         * 
         * @return the {@link Bundle} result from the remove op.
         */
        public synchronized Boolean waitForRemoveCompletion() {
            while (mResult == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            return mResult;
        }

        @Override
        public void run(AccountManagerFuture<Boolean> future) {
            try {
                mResult = future.getResult();
            } catch (OperationCanceledException e) {
                handleException(e);
            } catch (IOException e) {
                handleException(e);
            } catch (AuthenticatorException e) {
                handleException(e);
            }
            synchronized (this) {
                notifyAll();
            }
        }

        public String getErrorMessage() {
            return mErrorMessage;
        }

        /**
         * Create a result bundle for given exception
         */
        private void handleException(Exception e) {
            Log.e(TAG, "Failed to remove account", e);
            mResult = false;
            mErrorMessage = e.toString();
        }
    }
}
