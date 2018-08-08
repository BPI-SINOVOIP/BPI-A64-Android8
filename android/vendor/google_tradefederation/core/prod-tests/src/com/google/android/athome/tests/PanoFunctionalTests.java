// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.athome.tests;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRetriableTest;
import com.android.tradefed.testtype.UiAutomatorTest;

/**
 * Test harness for Pano UI Tests. The test harness sets up the device,
 * issues command to start the test and waits for test to finish.
 */
public class PanoFunctionalTests extends UiAutomatorTest implements IRetriableTest {
    private static final String PANO_TEST_RUNNER_NAME =
            "com.android.test.uiautomator.functional.pano.PanoTestRunner";

    @Option(name = "retry-on-failure", description = "Retry the test on failure")
    private boolean mRetryOnFailure = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        addRunArg("runner", PANO_TEST_RUNNER_NAME);
        super.run(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRetriable() {
        return mRetryOnFailure;
    }
}
