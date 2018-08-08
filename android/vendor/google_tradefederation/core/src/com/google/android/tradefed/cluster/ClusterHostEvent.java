// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.cluster;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class to encapsulate cluster host events to be uploaded.
 */
public class ClusterHostEvent implements IClusterEvent {
    private long mTimestamp;
    private String mHostName;
    private String mTfVersion;
    private String mClusterId;
    private List<ClusterDeviceInfo> mDeviceInfos = new ArrayList<>();
    private Map<String, String> mData = new HashMap<>();
    public static final String EVENT_QUEUE = "host-event-queue";

    /**
     * Enums of the different types of host events.
     */
    public enum HostEventType {
        DeviceSnapshot("DeviceSnapshot");

        private String mName;

        private HostEventType(final String name) {
            mName = name;
        }

        @Override
        public String toString() {
            return mName;
        }
    }

    private HostEventType mType;

    private ClusterHostEvent() {
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public String getHostName() {
        return mHostName;
    }

    public String getTfVersion() {
        return mTfVersion;
    }

    public String getClusterId() {
        return mClusterId;
    }

    public List<ClusterDeviceInfo> getDeviceInfos() {
        return mDeviceInfos;
    }

    public Map<String, String> getData() {
        return mData;
    }

    public HostEventType getType() {
        return mType;
    }

    public static class Builder {
        private HostEventType mType;
        private long mTimestamp = System.currentTimeMillis();
        private String mHostName;
        private String mTfVersion;
        private String mClusterId;
        private List<ClusterDeviceInfo> mDeviceInfos = new ArrayList<ClusterDeviceInfo>();
        private Map<String, String> mData = new HashMap<>();

        public Builder() {
        }

        public Builder setHostEventType(final HostEventType type) {
            mType = type;
            return this;
        }

        public Builder setTimestamp(final long timestamp) {
            mTimestamp = timestamp;
            return this;
        }

        public Builder setClusterId(final String clusterId) {
            mClusterId = clusterId;
            return this;
        }

        public Builder setHostName(final String hostname) {
            mHostName = hostname;
            return this;
        }

        public Builder setTfVersion(final String tfVersion) {
            mTfVersion = tfVersion;
            return this;
        }

        public Builder addDeviceInfo(final ClusterDeviceInfo deviceInfo){
            mDeviceInfos.add(deviceInfo);
            return this;
        }

        public Builder addDeviceInfos(List<ClusterDeviceInfo> deviceInfos) {
            mDeviceInfos.addAll(deviceInfos);
            return this;
        }

        public Builder setData(final String name, final String value) {
            mData.put(name, value);
            return this;
        }

        public Builder setData(Map<String, String> data) {
            mData.putAll(data);
            return this;
        }

        public ClusterHostEvent build() {
            final ClusterHostEvent event = new ClusterHostEvent();
            event.mType = mType;
            event.mTimestamp = mTimestamp;
            event.mHostName = mHostName;
            event.mTfVersion = mTfVersion;
            event.mClusterId = mClusterId;
            event.mDeviceInfos = new ArrayList<>(mDeviceInfos);
            event.mData = new HashMap<>(mData);
            return event;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JSONObject toJSON() throws JSONException {
        final JSONObject json = new JSONObject();
        // event time should be in POSIX timestamp.
        json.put("time", this.getTimestamp() / 1000);
        json.put("type", this.getType().toString());
        json.put("hostname", this.getHostName());
        json.put("tf_version", this.getTfVersion());
        json.put("cluster", this.getClusterId());
        JSONArray deviceInfos = new JSONArray();
        for (ClusterDeviceInfo d : this.getDeviceInfos()) {
            deviceInfos.put(d.toJSON());
        }
        json.put("device_infos",deviceInfos);
        json.put("data", new JSONObject(this.getData()));
        return json;
    }
}
