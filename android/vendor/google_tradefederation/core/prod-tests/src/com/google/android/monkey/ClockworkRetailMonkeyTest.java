// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.monkey;

import com.android.monkey.MonkeyBase;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Test for running monkey test on clockwork. Adds automatic refreshing of the cards.
 */
public class ClockworkRetailMonkeyTest extends MonkeyBase {

    private Thread mRefreshThread;
    private CardRefresher mRefresher = new CardRefresher();

    @Option(name="card-refresh-interval", description="How often the cards are refreshed (ms)")
    private long mCardRefreshInterval = 2 * 60 * 1000;

    private static final String REFRESH_COMMAND =
            "am broadcast -a com.google.android.clockwork.home.retail.action.STARTED_RETAIL_DREAM";

    private class CardRefresher extends Thread {

        private boolean mCanceled = false;
        @Override
        public synchronized void run() {
            while (!mCanceled) {
                try {
                    this.wait(mCardRefreshInterval);
                    CLog.i("Refreshing cards");
                    getDevice().executeShellCommand(REFRESH_COMMAND);
                } catch (InterruptedException e) {
                    // ignore
                } catch (DeviceNotAvailableException e) {
                    // ignore
                }
            }
        }

        public synchronized void cancel() {
            mCanceled = true;
            this.notifyAll();
        }
    }

    @Override
    protected void onMonkeyStart() {
        mRefreshThread = new Thread(mRefresher);
        mRefreshThread.start();
    }

    @Override
    protected void onMonkeyFinish() {
        mRefresher.cancel();
        try {
            mRefreshThread.join();
        } catch (InterruptedException e) {
            // ignore
        }
    }
}
