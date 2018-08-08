// Copyright 2014 Google Inc.  All Rights Reserved.
package com.google.android.tradefed.util;

import com.google.api.client.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

/**
 * A class representing a single task from Cloud Task Queue.
 */
public class CloudTask {

    private static final String ID = "id";
    private static final String PAYLOAD_BASE_64 = "payloadBase64";
    private static final String TAG = "tag";

    private String mId;
    private String mTag;
    private String mData;

    private CloudTask() {
    }

    public String getId() {
        return mId;
    }

    public String getTag() {
        return mTag;
    }

    public String getData() {
        return mData;
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject json = new JSONObject();
        json.put(ID, mId);
        json.put(TAG, mTag);
        if (mData != null) {
            json.put(PAYLOAD_BASE_64, Base64.encodeBase64URLSafeString(mData.getBytes()));
        }
        return json;
    }

    public static CloudTask fromJson(final JSONObject json) throws UnsupportedEncodingException {
        Builder builder = new Builder();
        builder.setId(json.optString(ID));
        builder.setTag(json.optString(TAG));
        final String payload = json.optString(PAYLOAD_BASE_64);
        if (payload != null) {
            builder.setData(new String(Base64.decodeBase64(payload), "UTF-8"));
        }
        return builder.build();
    }

    public static class Builder {
        private String mId;
        private String mTag;
        private String mData;

        public Builder() {
        }

        public Builder setId(String id) {
            mId = id;
            return this;
        }

        public Builder setTag(String tag) {
            mTag = tag;
            return this;
        }

        public Builder setData(String data) {
            mData = data;
            return this;
        }

        public CloudTask build() {
            final CloudTask task = new CloudTask();
            task.mId = mId;
            task.mTag = mTag;
            task.mData = mData;
            return task;
        }
    }
}
