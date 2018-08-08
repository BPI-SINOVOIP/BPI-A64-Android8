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

package com.google.android.power;

import com.google.android.power.tests.PowerMeasurement;
import com.google.android.power.tests.PowerMetric;
import com.google.android.power.tests.PowerTimestamp;
import com.google.android.power.tests.PowerTimestamp.PowerTimestampStatus;

import junit.framework.TestCase;

public class PowerMetricTests extends TestCase {

    public static final String STATISTIC_NAME_1 = "STATISTIC_NAME_1";
    public static final String STATISTIC_NAME_2 = "STATISTIC_NAME_2";
    public static final String STATISTIC_NAME_3 = "STATISTIC_NAME_3";

    private PowerTimestamp buildTestTimestamp(String tag, long start, long end){
        PowerTimestamp timestamp = new PowerTimestamp(tag);
        timestamp.setStartTime(start);
        timestamp.setEndTime(end);
        return timestamp;
    }

    public void testPowerMetricOnlyConsidersValidMeasurements() {
        PowerMetric pm = new PowerMetric("test");
        PowerMeasurement measurement = new PowerMeasurement(buildTestTimestamp("testtag", 1, 2));
        measurement.addStatistic(STATISTIC_NAME_1, 2f);
        measurement.getTimestamp().setStatus(PowerTimestampStatus.VALID, "test");
        assertTrue(pm.addMeasurement(measurement));

        measurement = new PowerMeasurement(buildTestTimestamp("testtag", 3, 4));
        measurement.addStatistic(STATISTIC_NAME_1, 8f);
        measurement.getTimestamp().setStatus(PowerTimestampStatus.VALID, "test");
        assertTrue(pm.addMeasurement(measurement));

        // measurements with INVALID status should be ignored.
        measurement = new PowerMeasurement(buildTestTimestamp("testag", 1, 2));
        measurement.addStatistic(STATISTIC_NAME_1, 10000f);
        measurement.getTimestamp().setStatus(PowerTimestampStatus.INVALID, "test");
        assertFalse(pm.addMeasurement(measurement));

        // measurements with UNKNOWN status should be ignored.
        measurement = new PowerMeasurement(buildTestTimestamp("testtag", 1, 2));
        measurement.addStatistic(STATISTIC_NAME_1, 10000f);
        measurement.getTimestamp().setStatus(PowerTimestampStatus.UNKNWON, "test");
        assertFalse(pm.addMeasurement(measurement));

        assertEquals(2, pm.getValidMeasurements().size());
        assertEquals(5f, pm.getAverages().get(STATISTIC_NAME_1));
    }

    public void testPowerMetricOnlyConsidersMeasurmentsOfSimilarDuration() {
        PowerMetric pm = new PowerMetric("test");

        // This is a valid measurement and its duration is 20s.
        PowerMeasurement measurement = new PowerMeasurement(
                buildTestTimestamp("testtag", 0, 20000));
        measurement.addStatistic(STATISTIC_NAME_1, 2f);
        measurement.getTimestamp().setStatus(PowerTimestampStatus.VALID, "test");
        assertTrue(pm.addMeasurement(measurement));
        assertEquals(20000L, measurement.getTimestamp().getDuration());
        assertEquals(20000L, (long) pm.getPivotDuration());

        // This is a valid measurement with duration within (10s-30s) range and should not be ignored.
        measurement = new PowerMeasurement(buildTestTimestamp("testtag", 0, 29999));
        measurement.addStatistic(STATISTIC_NAME_1, 4f);
        measurement.getTimestamp().setStatus(PowerTimestampStatus.VALID, "test");
        assertEquals(29999L, measurement.getTimestamp().getDuration());
        assertTrue(pm.addMeasurement(measurement));

        // This is a valid measurement with duration within (10s-30s) range and should not be ignored.
        measurement = new PowerMeasurement(buildTestTimestamp("testtag", 0, 10001));
        measurement.addStatistic(STATISTIC_NAME_1, 6f);
        measurement.getTimestamp().setStatus(PowerTimestampStatus.VALID, "test");
        assertEquals(10001L, measurement.getTimestamp().getDuration());
        assertTrue(pm.addMeasurement(measurement));

        // This measurement exceeds the range limit and should be ignored.
        measurement = new PowerMeasurement(buildTestTimestamp("testtag", 0, 30000));
        measurement.addStatistic(STATISTIC_NAME_1, 10000f);
        measurement.getTimestamp().setStatus(PowerTimestampStatus.VALID, "test");
        assertEquals(30000L, measurement.getTimestamp().getDuration());
        assertFalse(pm.addMeasurement(measurement));

        // This measurement falls behind the range limit and should be ignored.
        measurement = new PowerMeasurement(buildTestTimestamp("testtag", 0, 10000));
        measurement.addStatistic(STATISTIC_NAME_1, 10000f);
        measurement.getTimestamp().setStatus(PowerTimestampStatus.VALID, "test");
        assertEquals(10000L, measurement.getTimestamp().getDuration());
        assertFalse(pm.addMeasurement(measurement));

        assertEquals(3, pm.getValidMeasurements().size());
        assertEquals(4f, pm.getAverages().get(STATISTIC_NAME_1));
    }
}
