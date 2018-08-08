// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.StreamUtil;

import java.util.HashMap;

/**
 * Stub test used for ATP integration testing.
 */
public class ATPStubTest implements IRemoteTest {

    @Option(name = "num-succeeded-tests", description = "number of succeeded stub"
            + " tests per test run.")
    private int mNumSucceededTests = 0;

    @Option(name = "num-failed-tests", description = "number of failed stub tests per test run.")
    private int mNumFailedTests = 0;

    @Option(name = "num-test-runs", description = "number of succeeded test runs.")
    private int mNumTestRuns = 1;

    @Option(name = "throw-device-not-available", description = "Test throw"
            + " DeviceNotAvailableException error. Used for testing.")
    private boolean mThrowDeviceNotAvailable = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mThrowDeviceNotAvailable) {
            throw new DeviceNotAvailableException();
        }
        for (int i = 0; i < mNumTestRuns; ++i) {
            runTestRun(listener, i);
        }
    }

    private void runTestRun(ITestInvocationListener listener, int testRunIndex) {
        String className = String.format("%s_succeeded_test_run%d",
                ATPStubTest.class.getSimpleName(),
                testRunIndex);
        listener.testRunStarted(className, mNumSucceededTests + mNumFailedTests);
        if (mNumSucceededTests > 0 || mNumFailedTests > 0) {
            for (int i = 0; i < mNumFailedTests; ++i) {
                TestIdentifier stubTest = new TestIdentifier(className, "failed_stub_test" + i);
                listener.testStarted(stubTest);
                listener.testFailed(stubTest, StreamUtil.getStackTrace(new Throwable()));
                listener.testEnded(stubTest, new HashMap<String, String>());
            }
            for (int i = 0; i < mNumSucceededTests; ++i) {
                TestIdentifier stubTest = new TestIdentifier(className, "succeeded_stub_test" + i);
                listener.testStarted(stubTest);
                listener.testEnded(stubTest, new HashMap<String, String>());
            }
        } else {
            CLog.i("nothing to test!");
        }
        listener.testRunEnded(0, new HashMap<String, String>());
    }
}
