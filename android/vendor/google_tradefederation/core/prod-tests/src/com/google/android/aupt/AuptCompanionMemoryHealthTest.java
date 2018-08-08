// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.aupt;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;

import java.util.Map;

/**
 * This is a variation on the test harness for the Automated User profile tests
 * (AUPT).  It is designed for a memory health report on the Android Wear
 * companion application.
 */
public class AuptCompanionMemoryHealthTest extends AuptTest {
    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mListeners.add(new KillAppWrapper());
        super.run(listener);
    }

    /**
     * Wraps an existing listener, kill the companion application after each
     * dumpsys of meminfo for the health report.
     */
    protected class KillAppWrapper implements ITestInvocationListener {
        public KillAppWrapper() {}

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            try {
                getDevice().executeShellCommand("am force-stop com.google.android.wearable.app");
            } catch (Exception ex) {
                CLog.e("Could not kill the companion application.");
            }
        }
    }
}