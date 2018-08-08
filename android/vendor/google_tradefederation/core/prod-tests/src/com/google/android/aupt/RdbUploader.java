// Copyright 2013 Google Inc. All Rights Reserved.
package com.google.android.aupt;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.invoker.IInvocationContext;

import com.google.android.tradefed.result.RdbRunMetricsResultReporter;

import java.io.Closeable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class that provides an ability to post data to the release dashboard.
 */
public class RdbUploader implements Closeable {
    private static final String TEST_ID = "AUPT";
    private RdbRunMetricsResultReporter mRdbReporter = new RdbRunMetricsResultReporter();
    private IInvocationContext mContext;

    /**
     * Initializes the uploader. All posted data will be associated with the provided build
     *
     * @param context build which will be associated with all the posted data.
     */
    public RdbUploader(IInvocationContext context) {
        mContext = context;
        mRdbReporter.invocationStarted(context);
    }

    /**
     * Finalizes the posting and cleans up any resources used by the uploader.
     */
    @Override
    public void close() {
        mRdbReporter.invocationEnded(1);
    }

    /**
     * Posts data to the specified reporting unit. The reporting unit schema will the the test-tag.
     * @param ruKey reporting unit to post to.
     * @param metric value to post to the dashboard.
     */
    public void post(String ruKey, Object metric) {
        mRdbReporter.testRunStarted(ruKey, 1);
        TestIdentifier testId = new TestIdentifier(ruKey, TEST_ID);
        mRdbReporter.testStarted(testId);
        mRdbReporter.testEnded(testId, Collections.<String, String>emptyMap());
        mRdbReporter.testRunEnded(0, createMetricsMap(metric));
    }

    /**
     * Creates a metrics map to encapsulate the value being posted.
     */
    private Map<String, String> createMetricsMap(Object metric) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(mContext.getTestTag(), metric.toString());
        return map;
    }
}