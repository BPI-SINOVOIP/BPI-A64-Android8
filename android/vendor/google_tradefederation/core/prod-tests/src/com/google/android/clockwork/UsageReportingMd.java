// Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.google.android.tradefed.testtype.ClockworkTest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class UsageReportingMd extends ClockworkTest {
    private static final String TRUE_RESULT = "name=\"OptInUsageReporting\" value=\"true\"";

    private static final String FALSE_RESULT = "name=\"OptInUsageReporting\" value=\"false\"";

    @Option(name = "iteration", description = "number of repetitions")
    private int mIteration = 2;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        listener.testRunStarted("UsageReportingMd", mIteration);
        long start = System.currentTimeMillis();
        int success = 0;
        for (int i = 0; i < mIteration; i++) {
            TestIdentifier id =
                    new TestIdentifier(
                            getClass().getCanonicalName(), String.format("UsageReporting%d", i));
            listener.testStarted(id);
            if (!testSetting("true", TRUE_RESULT)) {
                listener.testFailed(id, "opt in to usage reporting failed");
            } else if (!testSetting("false", FALSE_RESULT)) {
                listener.testFailed(id, "opt out of usage reporting failed");
            } else {
                listener.testEnded(id, Collections.<String, String>emptyMap());
                success++;
            }
        }
        Map<String, String> metrics = new HashMap<String, String>();
        metrics.put("success", String.format("%d", success));
        listener.testRunEnded(System.currentTimeMillis() - start, metrics);
    }

    private boolean testSetting(String input, String expectedOutput)
            throws DeviceNotAvailableException {
        getCompanion()
                .executeShellCommand(
                        "am startservice -n"
                                + "\"com.google.android.wearable.app/com.google.android.clockwork.companion"
                                + ".WearableSystemLoggingTestIntentService\" --ez enabled "
                                + input);
        String xml = null;
        for (int i = 0; i < 30; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            xml =
                    getDevice()
                            .executeShellCommand(
                                    "cat /data/data/com.google.android.gms/shared_prefs/usagereporting.xml");
            if (xml != null && xml.contains(expectedOutput)) {
                // success
                return true;
            }
        }
        return false;
    }
}
