/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.tradefed.account;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * A utility for Google account syncing
 */
public class GoogleAccountSync extends Instrumentation {
    private static final String LOG_TAG = GoogleAccountSync.class.getSimpleName();

    // Option: long to indicate the sync-checking interval
    private static final String PARAM_INTERVAL = "interval";
    // Option: total timeout to quit sync-checking
    private static final String PARAM_TIMEOUT = "timeout";

    // Keys for Bundle messages
    private static final String BUNDLE_ERROR_KEY = "error";
    private static final String BUNDLE_MESSAGE_KEY = "message";
    private static final String BUNDLE_RESULT_KEY = "result";
    // Values for Bundle messages
    private static final String BUNDLE_ERROR_MESSAGE = "ERROR";
    private static final String BUNDLE_SUCCESS_MESSAGE = "SUCCESS";

    private long mInterval;
    private long mTimeout;
    private Runnable mSyncCheckRunnable = new Runnable() {
        private long mStartTime;
        @Override
        public void run() {
            mStartTime = SystemClock.uptimeMillis();
            // Loop with remaining time
            while (SystemClock.uptimeMillis() - mStartTime < mTimeout) {
                int syncSize = getCurrentSyncsSize();
                if (syncSize == 0) {
                    finishInstrumentation(Activity.RESULT_OK, "All sync completed.");
                    return;
                } else {
                    // Active syncs
                    long sleepDuration = Math.min(mInterval, mTimeout -
                            (SystemClock.uptimeMillis() - mStartTime));
                    sendStatusMessage(Activity.RESULT_OK, String.format(
                            "Active syncs: %d, sleep for %d ms", syncSize, sleepDuration));
                    try {
                        Thread.sleep(sleepDuration);
                    } catch (InterruptedException e) {
                        Log.e(LOG_TAG, "InterruptedException in GoogleAccountSync", e);
                        finishInstrumentation(Activity.RESULT_CANCELED, "Interrupted thread.");
                        return;
                    }
                }
            }
            // Timeout exceeded
            finishInstrumentation(Activity.RESULT_CANCELED, "Sync utility timed out.");
        }
    };

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);

        String intervalString = arguments.getString(PARAM_INTERVAL);
        if (intervalString != null) {
            mInterval = Long.parseLong(intervalString);
        } else {
            finishInstrumentation(Activity.RESULT_CANCELED, "No polling interval specified.");
        }

        String timeoutString = arguments.getString(PARAM_TIMEOUT);
        if (timeoutString != null) {
            mTimeout = Long.parseLong(timeoutString);
        } else {
            finishInstrumentation(Activity.RESULT_CANCELED, "No polling timeout specified.");
        }

        start();
    }

    @Override
    public void onStart() {
        super.onStart();

        Thread syncThread = new Thread(mSyncCheckRunnable);
        syncThread.start();
    }

    public int getCurrentSyncsSize() {
        return getContext().getContentResolver().getCurrentSyncs().size();
    }

    public void sendStatusMessage (int result, String message) {
        Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_MESSAGE_KEY, message);
        sendStatus(result, bundle);
    }

    public void finishInstrumentation (int result, String message) {
        Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_MESSAGE_KEY, message);
        if (result == Activity.RESULT_OK) {
            bundle.putString(BUNDLE_RESULT_KEY, BUNDLE_SUCCESS_MESSAGE);
        } else {
            bundle.putString(BUNDLE_ERROR_KEY, BUNDLE_ERROR_MESSAGE);
        }
        finish(result, bundle);
    }
}
