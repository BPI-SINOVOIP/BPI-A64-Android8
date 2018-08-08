/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.power.tests;

import com.google.android.power.tests.PowerTimestamp.PowerTimestampStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PowerMeasurement {

    private Map<String, Float> mPowerStatistics = new HashMap<String, Float>();
    private final PowerTimestamp mTimestamp;

    public PowerMeasurement(PowerTimestamp timestamp) {
        mTimestamp = timestamp;
    }

    public String getTag() {
        return mTimestamp.getTag();
    }

    public PowerTimestampStatus getStatus() {
        return mTimestamp.getStatus();
    }

    public void addStatistic(String name, float value) {
        mPowerStatistics.put(name, value);
    }

    public float getStatistic(String name) {
        if (mPowerStatistics.containsKey(name))
            return mPowerStatistics.get(name);

        return Float.NaN;
    }

    public Map<String, Float> getStatistics(){
        // Return a unmodifiable version of the values.
        return Collections.unmodifiableMap(mPowerStatistics);
    }

    @Override
    public String toString() {
        String valuesString = "";
        valuesString = "values: {";
        for (String name : getStatistics().keySet()) {
            valuesString = String.format("%s [%s - %.2f] ", valuesString, name, getStatistic(name));
        }
        valuesString = String.format("%s}", valuesString);

        return String.format("%s: tag:%s duration:%d start:%d end:%d "
                        + "status:%s status_update_reason: %s %s",
                getClass().getSimpleName(), getTag(), mTimestamp.getDuration(), mTimestamp
                        .getStartTime(),
                mTimestamp.getEndTime(), getStatus().name
                        (), mTimestamp.getStatusReason(), valuesString);
    }

    public PowerTimestamp getTimestamp() {
        return mTimestamp;
    }
}

