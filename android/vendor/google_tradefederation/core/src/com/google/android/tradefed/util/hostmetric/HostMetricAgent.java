// Copyright 2014 Google Inc.  All Rights Reserved.
package com.google.android.tradefed.util.hostmetric;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.hostmetric.HostMetric;

import com.google.android.tradefed.util.CloudTask;
import com.google.android.tradefed.util.CloudTaskQueue;
import com.google.android.tradefed.util.CloudTaskQueueException;
import com.google.android.tradefed.util.ICloudTaskQueue;
import com.google.common.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * An class to emit host metrics to TF Monitor.
 */
@OptionClass(alias = "host_metric_agent", global_namespace = false)
public class HostMetricAgent implements IHostMetricAgent {

    private static final String PROJECT = "s~google.com:tradefed";
    private static final int MAX_METRICS_PER_TASK = 50;

    @Option(name = "task-queue-name", description = "a task queue name")
    private String mTaskQueueName = "metric-queue";
    @Option(name = "task-queue-account", description = "a service account for task queue access",
            mandatory = true)
    private String mTaskQueueAccount = null;
    @Option(name = "task-queue-key-path", description = "path to task queue key location",
            mandatory = true)
    private File mTaskQueueKeyPath = null;

    private List<HostMetric> mMetrics = new LinkedList<>();
    private String mHostname = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void emitValue(String name, long value, Map<String, String> data) {
        data.put("hostname", getHostname());
        mMetrics.add(new HostMetric(name, System.currentTimeMillis(), value, data));
    }

    /**
     * Get local hostname
     *
     * @return hostname
     */
    private String getHostname() {
        try {
            if (mHostname == null) {
                mHostname = InetAddress.getLocalHost().getHostName();
            }
        } catch (Exception e) {
            CLog.w("Failed to get local host's name.");
            CLog.w(e);
        }
        return mHostname;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void flush() {
        if (mMetrics.isEmpty()) {
            return;
        }

        ICloudTaskQueue helper = getTaskQueueHelper();
        if (helper == null) {
            CLog.w("Failed to get TaskQueueHelper. Skipping flush.");
            return;
        }

        try {
            CLog.d("Flushing %d metrics...", mMetrics.size());
            while (!mMetrics.isEmpty()) {
                final int metricCount = Math.min(MAX_METRICS_PER_TASK, mMetrics.size());
                final JSONArray jsons = new JSONArray();
                for (int i = 0; i < metricCount; i++) {
                    jsons.put(mMetrics.get(i).toJson());
                }
                final CloudTask task = new CloudTask.Builder()
                        .setData(jsons.toString())
                        .build();
                helper.insertTask(mTaskQueueName, task);
                mMetrics.subList(0, metricCount).clear();
            }
        } catch (JSONException | CloudTaskQueueException e) {
            CLog.w("failed to upload metrics: %s", e.getMessage());
        }
    }

    @VisibleForTesting
    void setTaskQueueName(String name) {
        mTaskQueueName = name;
    }

    @VisibleForTesting
    void setTaskQueueAccount(String account) {
        mTaskQueueAccount = account;
    }

    @VisibleForTesting
    void setTaskQueueKeyPath(File keyPath) {
        mTaskQueueKeyPath = keyPath;
    }

    @VisibleForTesting
    List<HostMetric> getMetrics() {
        return mMetrics;
    }

    @VisibleForTesting
    ICloudTaskQueue getTaskQueueHelper() {
        CloudTaskQueue taskQueue = new CloudTaskQueue(PROJECT);
        try {
            taskQueue.setup(mTaskQueueAccount, mTaskQueueKeyPath);
            return taskQueue;
        } catch (GeneralSecurityException | IOException e) {
            CLog.e(e);
            return null;
        }
    }

}
