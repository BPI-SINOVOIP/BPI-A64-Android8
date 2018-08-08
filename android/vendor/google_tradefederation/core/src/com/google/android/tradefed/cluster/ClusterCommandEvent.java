// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.cluster;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * A class to encapsulate cluster command events to be uploaded.
 */
public class ClusterCommandEvent implements IClusterEvent {

    public static final String DATA_KEY_ERROR = "error";
    public static final String DATA_KEY_SUMMARY = "summary";
    public static final String DATA_KEY_TOTAL_TEST_COUNT = "total_test_count";
    public static final String DATA_KEY_FAILED_TEST_COUNT = "failed_test_count";
    public static final String DATA_KEY_TEST_RUN_NAME = "test_run_name";
    public static final String EVENT_QUEUE = "command-event-queue";

    public enum Type {
        AllocationFailed,
        ConfigurationError,
        FetchFailed,
        ExecuteFailed,
        InvocationStarted,
        InvocationFailed,
        InvocationEnded,
        InvocationCompleted,
        TestRunInProgress,
        TestEnded
    }

    private long mTimestamp;
    private Type mType;
    private String mCommandTaskId;
    private String mAttemptId;
    private String mHostName;
    private String mDeviceSerial;
    private Map<String, String> mData = new HashMap<>();

    private ClusterCommandEvent() {
    }

    public String getHostName() {
        return mHostName;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public Type getType() {
        return mType;
    }

    public String getCommandTaskId() {
        return mCommandTaskId;
    }

    public String getAttemptId() {
        return mAttemptId;
    }

    public String getDeviceSerial() {
        return mDeviceSerial;
    }

    public Map<String, String> getData() {
        return mData;
    }

    public static class Builder {

        private long mTimestamp = System.currentTimeMillis();
        private Type mType;
        private String mCommandTaskId;
        private String mAttemptId;
        private String mHostName;
        private String mDeviceSerial;
        private Map<String, String> mData = new HashMap<>();

        public Builder() {
        }

        public Builder setTimestamp(final long timestamp) {
            mTimestamp = timestamp;
            return this;
        }

        public Builder setType(final Type type) {
            mType = type;
            return this;
        }

        public Builder setCommandTaskId(final String commandTaskId) {
            mCommandTaskId = commandTaskId;
            return this;
        }

        public Builder setAttemptId(final String attemptId) {
            mAttemptId = attemptId;
            return this;
        }

        public Builder setHostName(final String hostName) {
            mHostName = hostName;
            return this;
        }

        public Builder setDeviceSerial(final String deviceSerial) {
            mDeviceSerial = deviceSerial;
            return this;
        }

        public Builder setData(final String name, final String value) {
            mData.put(name, value);
            return this;
        }

        public ClusterCommandEvent build() {
            final ClusterCommandEvent obj = new ClusterCommandEvent();
            obj.mTimestamp = mTimestamp;
            obj.mType = mType;
            obj.mCommandTaskId = mCommandTaskId;
            obj.mAttemptId = mAttemptId;
            obj.mHostName = mHostName;
            obj.mDeviceSerial = mDeviceSerial;
            obj.mData = new HashMap<>(mData);
            return obj;
        }
    }

    /**
     * Creates a base {@link Builder}.
     * @return a {@link Builder}.
     */
    public static Builder createEventBuilder() {
        return createEventBuilder(null);
    }

    /**
     * Creates a base {@link Builder} for the given {@link ClusterCommand}.
     * @return a {@link Builder}.
     */
    public static Builder createEventBuilder(final ClusterCommand command) {
        final ClusterCommandEvent.Builder builder = new ClusterCommandEvent.Builder();
        if (command != null) {
            builder.setCommandTaskId(command.getTaskId());
            builder.setAttemptId(command.getAttemptId());
            builder.setDeviceSerial(command.getDeviceSerial());
        }
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JSONObject toJSON() throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("type", this.getType().toString());
        // event time should be in POSIX timestamp.
        json.put("time", this.getTimestamp() / 1000);
        json.put("task_id", this.getCommandTaskId());
        json.put("attempt_id", this.getAttemptId());
        json.put("hostname", this.getHostName());
        json.put("device_serial", this.getDeviceSerial());
        json.put("data", new JSONObject(this.getData()));
        return json;
    }

    @Override
    public String toString() {
        String str = null;
        try {
            str = toJSON().toString();
        } catch(final JSONException e) {
            // ignore
        }
        return str;
    }
}
