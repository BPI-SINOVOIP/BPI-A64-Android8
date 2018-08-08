// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;

import com.google.android.tradefed.account.RemoveAccounts.RemoveCallback;

/**
 * An instrumentation utility to remove a single Google account from device adb shell am instrument
 * -w -e account accountName com.google.android.tests.utilities/.RemoveAccount
 */
public class RemoveAccount extends Instrumentation {

    private static final String TAG = "RemoveAccount";

    private String mAccount;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mAccount = arguments.getString("account");
        start();
    }

    @Override
    public void onStart() {
        super.onStart();

        Log.d(TAG, "Attempting to remove Google account");
        Bundle results = new Bundle();

        AccountManager accountManager = AccountManager.get(getContext());
        Account[] accounts = accountManager.getAccountsByType(AddAccount.GOOGLE_ACCOUNT);
        for (Account account : accounts) {
            if (account.name.equals(mAccount)) {
                Log.i(TAG, String.format("Removing account %s", account.name));
                RemoveCallback callback = new RemoveCallback();
                accountManager.removeAccount(account, callback, null /* handler */);
                if (!callback.waitForRemoveCompletion()) {
                    results.putString("error", String.format(
                            "Failed to remove account %s: Reason: %s", account.name,
                            callback.getErrorMessage()));
                    finish(Activity.RESULT_CANCELED, results);
                    return;
                }
            }
        }
        results.putString("result", "SUCCESS");
        finish(Activity.RESULT_OK, results);
    }
}
