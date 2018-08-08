// Copyright 2012 Google Inc. All Rights Reserved.
package com.google.android.compatibility;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.result.ITestInvocationListener;

class FailureCollectingListener implements ITestInvocationListener {
    private String mTestTrace = null;

    @Override
    public void testFailed(TestIdentifier test, String trace) {
        if (trace != null) {
            setStackTrace(trace);
        } else {
            setStackTrace("unknown failure");
        }
    }

    @Override
    public void testAssumptionFailure(TestIdentifier test, String trace) {
        if (trace != null) {
            setStackTrace(trace);
        } else {
            setStackTrace("unknown assumption failure");
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunFailed(String errorMessage) {
        setStackTrace(errorMessage);
    }

    /**
     * Fetches the stack trace if any.
     * @return the stack trace.
     */
    public String getStackTrace() {
        return mTestTrace;
    }

    /**
     * Sets the stack trace.
     * @param stackTrace {@link String} stack trace to set.
     */
    public void setStackTrace(String stackTrace) {
        this.mTestTrace = stackTrace;
    }
}