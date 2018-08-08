// Copyright 2014 Google Inc.  All Rights Reserved.
package com.google.android.tradefed.util.hostmetric;

import com.android.tradefed.util.hostmetric.IHostHealthAgent;

/** An interface to emit host metrics to TF Monitor. */
public interface IHostMetricAgent extends IHostHealthAgent {

    static final String METRIC_PREFIX = "/android/tradefed/";
    static final String DEVICE_COUNT_METRIC = METRIC_PREFIX + "device/count";
    static final String DEVICE_ALLOCATED_COUNT_METRIC = METRIC_PREFIX + "device/allocated_count";
    static final String DEVICE_BATTERY_METRIC = METRIC_PREFIX + "device/battery";
    static final String DISK_TOTAL_SPACE_METRIC = METRIC_PREFIX + "disk/total_space";
    static final String DISK_FREE_SPACE_METRIC = METRIC_PREFIX + "disk/free_space";
    static final String HTTP_REQUEST_METRIC = METRIC_PREFIX + "http_request/count";
}
