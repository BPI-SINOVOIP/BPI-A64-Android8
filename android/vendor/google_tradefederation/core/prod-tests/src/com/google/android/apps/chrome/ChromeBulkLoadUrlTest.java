// Copyright 2013 Google Inc. All Rights Reserved.
package com.google.android.apps.chrome;

import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.UiAutomatorTest;

import java.util.HashMap;
import java.util.Map;

/*
 * Tests Chrome by loading a set of URLs.
 */
public class ChromeBulkLoadUrlTest extends UiAutomatorTest {

    @Option(name = "metrics-name", description = "name used to identify the metrics for reporting",
            importance = Importance.ALWAYS)
    private String mMetricsName;

    @Option(name = "schema-key",
            description = "the schema key that number of successful loads should be reported under",
            importance = Importance.ALWAYS)
    private String mSchemaKey;


    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // Run the uiautomator test, but don't report the results to our listener yet
        CollectingTestListener testListener = new CollectingTestListener();
        super.run(testListener);

        // Report the number of passed tests as a metric from a single test run. This is done to
        // preserve compatibility with existing tests.
        Map<String, String> metrics = new HashMap<String, String>();
        metrics.put(mSchemaKey, Integer.toString(testListener.getNumTestsInState(
                TestStatus.PASSED)));
        listener.testRunStarted(mMetricsName, 0);
        listener.testRunEnded(0, metrics);
    }
}
