// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.gcore;

import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.DeviceTestCase;

import java.util.Map;

/**
 * Harness for running on device test that checks for presence of network location provider
 */
class NlpTester extends DeviceTestCase {

    static class FailureListener implements ITestInvocationListener {
        String mFailureTrace = null;
        int mNumTests;

        @Override
        public void testFailed(TestIdentifier test, String trace) {
            mFailureTrace = trace;
        }

        @Override
        public void testAssumptionFailure(TestIdentifier test, String trace) {
            mFailureTrace = trace;
        }

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> metrics) {
            mNumTests++;
        }

        public int getNumTotalTests() {
            return mNumTests;
        }
    }

    static FailureListener runLocationTester(ITestDevice device) throws DeviceNotAvailableException {
        FailureListener l = new FailureListener();
        RemoteAndroidTestRunner r = new RemoteAndroidTestRunner(
                "com.google.android.nlptester", device.getIDevice());
        device.runInstrumentationTests(r, l);
        return l;
    }
}
