// Copyright 2007 Google Inc. All Rights Reserved.
package com.google.android.tradefed.account;

import java.io.IOException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncAdapterType;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

/**
 * An instrumentation utility to add accounts and, while so doing, enable or disable automatic sync
 *
 * Current abilities:
 *   - Run with "-e account ... -e password ..." to add that account, defaulting to sync off
 *   - Run with "-e account ... -e password ... -e sync (bool)" to add that account, if it doesn't
 *     already exist, and set the autosync settings to the bool specified
 *   - Add a "-e wait-for-checkin true" argument to block until checkin has been performed
 *
 * Current known limitations:
 *   - Doesn't support accounts of types other than com.google
 *   - Doesn't do anything useful if you run it with an account that already exists, but without
 *     specifying "-e sync (bool)"
 *   - Error messages could be more useful
 *
 * adb shell am instrument -e account test_account@gmail.com -e password ******
 * -w com.google.android.tests.utilities/.AddAccount
 *
 * Copied from vendor/google/tests/Util
 */
public class AddAccount extends Instrumentation {

    private Bundle mArguments;
    private static final String TAG = "AddAccount";
    private static final String AUTH_TOKEN_TYPE = "mail";
    /** the maximum time to wait for a checkin update in seconds */
    private static final int CHECKIN_WAIT_TIME_SEC = 60;
    private static final long WAIT_BEFORE_UPDATE_SYNC = 15 * 1000;
    protected AccountManager mAccountManager;
    static final String GOOGLE_ACCOUNT = "com.google";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mArguments = arguments;
        start();
    }

    @Override
    public void onStart() {
        super.onStart();

        Bundle results = new Bundle();

        final String username = mArguments.getString("account");
        final String password = mArguments.getString("password");
        final boolean sync = Boolean.parseBoolean(mArguments.getString("sync"));
        final boolean waitForCheckin = Boolean.parseBoolean(mArguments.getString(
                "wait-for-checkin"));
        Account account = new Account(username, GOOGLE_ACCOUNT);

        if (TextUtils.isEmpty(username)) {
            Log.e(TAG, "Error: must specify account");
            results.putString("error", "must specify account");
            finish(Activity.RESULT_CANCELED, results);
        }

        mAccountManager = AccountManager.get(getContext());

        // If the account already exists, set sync settings if specified, otherwise bail
        for(Account acct : mAccountManager.getAccountsByType(GOOGLE_ACCOUNT)) {
            Log.v(TAG, String.format("Checking if username %s matches account %s", username,
                    acct.toString()));
            if(acct.name.equals(username) && acct.type.equals(GOOGLE_ACCOUNT)) {
                if (mArguments.containsKey("sync")) {
                    Log.d(TAG, String.format("Found existing account %s, setting sync to %s",
                            acct.name, sync));
                    // They passed a sync argument; set sync options and call it a day
                    setSyncAutomatically(acct, sync);
                    results.putString("result", "SUCCESS");
                    finish(Activity.RESULT_OK, results);
                    return;
                } else {
                    // No sync passed; bail out
                    String errMsg = String.format("Error: account %s already exists", username);
                    Log.e(TAG, errMsg);
                    results.putString("error", errMsg);
                    finish(Activity.RESULT_CANCELED, results);
                    return;
                }
            }
        }

        CheckinReceiver receiver = null;
        if (waitForCheckin) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("com.google.android.checkin.CHECKIN_COMPLETE");
            receiver = new CheckinReceiver();
            getContext().registerReceiver(receiver, intentFilter);
        }

        // At this point, we're adding a new account.
        Bundle options = new Bundle();
        options.putString("username", username);
        options.putString("password", password);
        AuthenticationCallback authCallback = new AuthenticationCallback();
        mAccountManager.addAccount(GOOGLE_ACCOUNT, AUTH_TOKEN_TYPE,
                null /* required features */, options,
                null /* don't spawn activity */, authCallback, null /* handler */);
        Log.i(TAG, "Attempting to add account");

        Bundle authResult = authCallback.waitForAuthCompletion();
        if (authResult.containsKey(AccountManager.KEY_ERROR_CODE)) {
            int errorCode = ((Integer) authResult.get(AccountManager.KEY_ERROR_CODE)).intValue();
            // workaround for b/11774229 where in KLP account is added but errorCode 8 is returned
            if (Build.VERSION.SDK_INT > 18 && errorCode == 8) {
                authResult.remove(AccountManager.KEY_ERROR_CODE);
            } else {
                Log.e(TAG, String.format("AddAccount failed. Reason: %s",
                        authResult.get(AccountManager.KEY_ERROR_MESSAGE)));
                finish(Activity.RESULT_CANCELED, authResult);
                return;
            }
        }

        String authToken = authResult.getString(AccountManager.KEY_AUTHTOKEN);

        if (authToken == null) {
            Log.d(TAG, "Auth Token is null; trying to fetch");
            authToken = fetchAuthToken(account);
            if (authToken == null) {
                // still failed, abort
                results.putString("error", "Failed to get auth token");
                finish(Activity.RESULT_CANCELED, results);
                return;
            }
            // add retrieved auth token back to bundle
            authResult.putString(AccountManager.KEY_AUTHTOKEN, authToken);
        }

        Log.i(TAG, String.format("Got authtoken %s", authToken));

        // Wait until all the sync adapters are registered before trying to turn sync off
        try {
            Thread.sleep(WAIT_BEFORE_UPDATE_SYNC);
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
        // turn on/off sync
        setSyncAutomatically(account, sync);

        // wait for checkin
        if (receiver != null && !receiver.waitForCheckin(CHECKIN_WAIT_TIME_SEC * 1000)) {
            String errMsg = String.format(
                    "Error: checkin did not complete within %d seconds after adding account %s",
                    CHECKIN_WAIT_TIME_SEC, username);
            Log.e(TAG, errMsg);
            results.putString("error", errMsg);
            finish(Activity.RESULT_CANCELED, results);
            return;
        }
        authResult.putString("result", "SUCCESS");
        finish(Activity.RESULT_OK, authResult);
    }

    /**
     * Perform a blocking call to get authentication token.
     * <p/>
     * This method must not be called from main thread.
     *
     * @param account
     * @return the authentication token, or null if it could not be retrieved
     */
    private String fetchAuthToken(Account account) {
        try {
            return mAccountManager.blockingGetAuthToken(account, AUTH_TOKEN_TYPE,
                    true /* return null on authentication failure */);
        } catch (OperationCanceledException e) {
            Log.e(TAG, "Failed to get auth token", e);
        } catch (IOException e) {
            Log.e(TAG, "Failed to get auth token", e);
        } catch (AuthenticatorException e) {
            Log.e(TAG, "Failed to get auth token", e);
        }
        return null;
    }

    private static void setSyncAutomatically(Account account, boolean sync) {
        for (SyncAdapterType adapter : ContentResolver.getSyncAdapterTypes()) {
            if (adapter.accountType.equals(account.type)) {
                ContentResolver.setSyncAutomatically(account, adapter.authority, sync);
                Log.v(TAG, String.format("Auto sync for %s -> %s", adapter.authority,
                        String.valueOf(sync)));
            }
        }
    }

    private class AuthenticationCallback implements AccountManagerCallback<Bundle> {
        /** stores the result of account authentication. null means not finished */
        private Bundle mResultBundle = null;

        /**
         * Block and wait for the authentication callback to complete.
         *
         * @return the {@link Bundle} result from the authentication.
         */
        public synchronized Bundle waitForAuthCompletion() {
            while (mResultBundle == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            return mResultBundle;
        }

        @Override
        public void run(AccountManagerFuture<Bundle> future) {
            try {
                mResultBundle = future.getResult();
            } catch (OperationCanceledException e) {
                mResultBundle = buildExceptionBundle(e);
            } catch (IOException e) {
                mResultBundle = buildExceptionBundle(e);
            } catch (AuthenticatorException e) {
                mResultBundle = buildExceptionBundle(e);
            }
            synchronized (this) {
                notifyAll();
            }
        }

        /**
         * Create a result bundle for given exception
         */
        private Bundle buildExceptionBundle(Exception e) {
            Bundle bundle = new Bundle();
            bundle.putInt(AccountManager.KEY_ERROR_CODE,
                    AccountManager.ERROR_CODE_INVALID_RESPONSE);
            bundle.putString(AccountManager.KEY_ERROR_MESSAGE, e.toString());
            return bundle;
        }
    }

    private static class CheckinReceiver extends BroadcastReceiver {

        private boolean mCheckinComplete = false;
        private boolean mCheckinFailed = false;
        private static final String EXTRA_CHECKIN_SUCCESS = "success";

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received checkin complete broadcast");
            synchronized (this) {
                if (intent != null && intent.getExtras() != null &&
                        intent.getExtras().getBoolean(EXTRA_CHECKIN_SUCCESS, false)) {
                    mCheckinComplete = true;
                } else {
                    Log.e(TAG, "Checkin has failed");
                }
                notifyAll();
            }
        }

        /**
         * Block until a checkin complete intent was received.
         *
         * @param time the max number of milliseconds to wait
         * @return <code>true</code> if checkin was completed, <code>false</code> otherwise
         */
        public synchronized boolean waitForCheckin(long time) {
            Log.d(TAG, "Waiting for checkin");
            long endTime = System.currentTimeMillis() + time;
            while (!mCheckinComplete && time > 0 && !mCheckinFailed) {
                try {
                    wait(time);
                } catch (InterruptedException e) {
                    // ignore
                }
                time = endTime - System.currentTimeMillis();
            }
            return mCheckinComplete;
        }
    }
}
