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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Helper class to store the average test result of all the iterations.
public class PowerMetric {

    private final String mTag;
    private Map<String, Float> totals = new HashMap<String, Float>();
    private Map<String, Float> averages = new HashMap<String, Float>();
    private List<PowerMeasurement> mValidMeasurements = new ArrayList<>();
    private static final long TEST_DURATION_THRESHOLD = 10 * 1000; // 10 secs
    private long mPivotDuration;

    public PowerMetric(String tag) {
        mTag = tag;
    }

    public String getTag() {
        return mTag;
    }

    public List<PowerMeasurement> getValidMeasurements() {
        return mValidMeasurements;
    }

    /**
     * adds a measurement to the metric if it is valid and its duartion is within range.
     * The range is defined by the first valid measurement added and the TEST_DURATION_THRESHOLD.
     *
     * @return true if the measurement is added, false if not.
     */
    public boolean addMeasurement(PowerMeasurement measurement) {
        boolean firstMeasurement = false;
        // The first valid measurement defines the pivot.
        if (mValidMeasurements.isEmpty() && measurement.getStatus() == PowerTimestampStatus.VALID) {
            mPivotDuration = measurement.getTimestamp().getDuration();
            firstMeasurement = true;
        }

        // All the following measurements need to have a comparable duration.
        if (measurement.getStatus() == PowerTimestampStatus.VALID &&
                !isDurationValid(measurement.getTimestamp().getDuration())) {
            return false;
        }

        // Only valid durations are taken for the metric.
        if (measurement.getStatus() != PowerTimestampStatus.VALID) {
            return false;
        }

        Map<String, Float> statistics = measurement.getStatistics();

        // The first measurement defines the set of statistics.
        if (firstMeasurement) {
            for (String name : statistics.keySet()) {
                totals.put(name, 0f);
                averages.put(name, 0f);
            }
        }

        mValidMeasurements.add(measurement);

        for (String name : totals.keySet()) {
            totals.put(name, totals.get(name) + statistics.get(name));
            averages.put(name, totals.get(name) / mValidMeasurements.size());
        }

        return true;
    }

    public Map<String, Float> getAverages() {
        return Collections.unmodifiableMap(averages);
    }

    // The duration of the first valid measurement added.
    public Long getPivotDuration() {
        return mPivotDuration;
    }

    private boolean isDurationValid(long duration) {
        return Math.abs(duration - mPivotDuration) < TEST_DURATION_THRESHOLD;
    }
}
