// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.cluster;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Interface for any cluster event to be uploaded to TFC.
 */
public interface IClusterEvent {

    public JSONObject toJSON() throws JSONException;
}
