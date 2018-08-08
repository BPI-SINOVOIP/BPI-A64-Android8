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

import com.google.android.power.tests.PowerLogParser;
import com.google.android.power.tests.PowerMeasurement;
import com.google.android.power.tests.PowerTimestamp;
import com.google.android.power.tests.PowerTimestamp.PowerTimestampStatus;
import com.google.android.power.tests.SweetberryRawDataParser;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public class SweetberryRawDataParserTests extends TestCase {

    public void testParserIgnoresSamplesOutOfBoundaries() {
        // autotester.log file format
        // <tiemstamp in milliseconds> <AUTOTEST_TEST_BEGIN|AUTOTEST_TEST_SUCESS> <tag>
        List<String> powerLog = Arrays.asList(
                "1000 AUTOTEST_TEST_BEGIN test1",
                "2000 AUTOTEST_TEST_SUCCESS test1");

        // sweetberry file format
        // ts:<frequency>, rail1 uW, rail2 uW...
        // <tiemstamp in seconds>, <power in milliwatts>, <power in milliwatts>...
        List<String> sweetberryLog = Arrays.asList(
                "ts:1000000us, rail1 uW, rail2 uW",
                "0, 100, 100",
                "1, 1, 2",
                "2, 3, 6",
                "3, 100, 100",
                "4, 100, 100");
        List<PowerTimestamp> timestamps = (new PowerLogParser(listToStream(powerLog)))
                .getPowerTimestamps();
        List<PowerMeasurement> measurements = new SweetberryRawDataParser(
                listToStream(sweetberryLog), timestamps, 0L)
                .getPowerMeasurements();
        assertEquals(1, measurements.size());
        assertEquals(PowerTimestampStatus.VALID, measurements.get(0).getStatus());
        assertEquals(2, measurements.get(0).getStatistics().size());
        assertEquals(2f, measurements.get(0).getStatistic("rail1 uW"));
        assertEquals(4f, measurements.get(0).getStatistic("rail2 uW"));
    }

    public void testParserMultipleMeasures() {
        // autotester.log file format
        // <tiemstamp in milliseconds> <AUTOTEST_TEST_BEGIN|AUTOTEST_TEST_SUCESS> <tag>
        List<String> powerLog = Arrays.asList(
                "1000 AUTOTEST_TEST_BEGIN test1",
                "4000 AUTOTEST_TEST_SUCCESS test1",
                "7000 AUTOTEST_TEST_BEGIN test2",
                "12000 AUTOTEST_TEST_SUCCESS test2");

        // sweetberry file format
        // <tiemstamp in seconds> <current in amperes>
        List<String> sweetberryLog = Arrays.asList(
                "ts:1000000us, rail1 uW, rail2 uW",
                "0, 1000, 1000",
                "1, 1, 2",
                "2, 2, 4",
                "3, 3, 6",
                "4, 4, 8",
                "5, 1000, 1000",
                "6, 1000, 1000",
                "7, 2, 4",
                "8, 4, 8",
                "9, 6, 12",
                "10, 8, 16",
                "11, 10, 20",
                "12, 12, 24",
                "11, 1000, 1000",
                "12, 1000, 1000");

        List<PowerTimestamp> timestamps = (new PowerLogParser(listToStream(powerLog)))
                .getPowerTimestamps();
        List<PowerMeasurement> measurements = new SweetberryRawDataParser(
                listToStream(sweetberryLog), timestamps, 0L)
                .getPowerMeasurements();
        assertEquals(2, measurements.size());
        assertEquals(PowerTimestampStatus.VALID, measurements.get(0).getStatus());
        assertEquals(2.5f, measurements.get(0).getStatistic("rail1 uW"));
        assertEquals(5f, measurements.get(0).getStatistic("rail2 uW"));
        assertEquals(PowerTimestampStatus.VALID, measurements.get(1).getStatus());
        assertEquals(7f, measurements.get(1).getStatistic("rail1 uW"));
        assertEquals(14f, measurements.get(1).getStatistic("rail2 uW"));
    }

    public void testParserInvalidatesMeasureIfItReachesEOF() {
        // autotester.log file format
        // <tiemstamp in milliseconds> <AUTOTEST_TEST_BEGIN|AUTOTEST_TEST_SUCESS> <tag>
        List<String> powerLog = Arrays.asList(
                "1000 AUTOTEST_TEST_BEGIN test1",
                "5000 AUTOTEST_TEST_SUCCESS test1");

        // sweetberry file format
        // <tiemstamp in seconds> <current in amperes>
        List<String> sweetberryLog = Arrays.asList(
                "ts:1000000us, rail1 uW, rail2 uW",
                "0, 1, 1",
                "1, 1, 1",
                "2, 2, 2",
                "3, 3, 3",
                "4, 4, 4");

        List<PowerTimestamp> timestamps = (new PowerLogParser(listToStream(powerLog)))
                .getPowerTimestamps();
        List<PowerMeasurement> measurements = new SweetberryRawDataParser(
                listToStream(sweetberryLog), timestamps, 0L)
                .getPowerMeasurements();
        assertEquals(1, measurements.size());
        assertEquals(PowerTimestampStatus.INVALID, measurements.get(0).getStatus());
        assertEquals(Float.NaN, measurements.get(0).getStatistic("rail1 uW"));
        assertEquals(Float.NaN, measurements.get(0).getStatistic("rail2 uW"));
    }

    public void testParserWithNonTrivialOffset() {
        // autotester.log file format
        // <tiemstamp in milliseconds> <AUTOTEST_TEST_BEGIN|AUTOTEST_TEST_SUCESS> <tag>
        List<String> powerLog = Arrays.asList(
                "3000 AUTOTEST_TEST_BEGIN test1",
                "4000 AUTOTEST_TEST_SUCCESS test1");

        // sweetberry file format
        // ts:<frequency>, rail1 uW, rail2 uW...
        // <tiemstamp in seconds>, <power in milliwatts>, <power in milliwatts>...
        List<String> sweetberryLog = Arrays.asList(
                "ts:1000000us, rail1 uW, rail2 uW",
                "0, 100, 100",
                "1, 100, 100",
                "2, 1, 1",
                "3, 1, 1",
                "4, 100, 100");
        List<PowerTimestamp> timestamps = (new PowerLogParser(listToStream(powerLog)))
                .getPowerTimestamps();
        List<PowerMeasurement> measurements = new SweetberryRawDataParser(
                listToStream(sweetberryLog), timestamps, 1000L)
                .getPowerMeasurements();
        assertEquals(1, measurements.size());
        assertEquals(PowerTimestampStatus.VALID, measurements.get(0).getStatus());
        assertEquals(1f, measurements.get(0).getStatistic("rail1 uW"));
        assertEquals(1f, measurements.get(0).getStatistic("rail2 uW"));
    }

    public void testTimestampOverflowWithinMeasurementLimits() {
        // autotester.log file format
        // <tiemstamp in milliseconds> <AUTOTEST_TEST_BEGIN|AUTOTEST_TEST_SUCESS> <tag>
        List<String> powerLog = Arrays.asList(
                "1000 AUTOTEST_TEST_BEGIN test1",
                "4000 AUTOTEST_TEST_SUCCESS test1");

        // sweetberry file format
        // ts:<frequency>, rail1 uW, rail2 uW...
        // <tiemstamp in seconds>, <power in milliwatts>, <power in milliwatts>...
        List<String> sweetberryLog = Arrays.asList(
                "ts:1000000us, rail1 uW, rail2 uW",
                "4292, 100, 100",
                "4293, 1, 2",
                "4294, 2, 4",
                "0, 3, 6",
                "1, 4, 8",
                "2, 100, 100");

        List<PowerTimestamp> timestamps = (new PowerLogParser(listToStream(powerLog)))
                .getPowerTimestamps();
        List<PowerMeasurement> measurements = new SweetberryRawDataParser(listToStream
                (sweetberryLog), timestamps, 0L).getPowerMeasurements();
        assertEquals(1, measurements.size());
        assertEquals(PowerTimestampStatus.VALID, measurements.get(0).getStatus());
        assertEquals(2.5f, measurements.get(0).getStatistic("rail1 uW"));
        assertEquals(5f, measurements.get(0).getStatistic("rail2 uW"));
    }

    public void testTimestampOverflowOutsideMeasurementLimits() {
        // autotester.log file format
        // <tiemstamp in milliseconds> <AUTOTEST_TEST_BEGIN|AUTOTEST_TEST_SUCESS> <tag>
        List<String> powerLog = Arrays.asList(
                "1000 AUTOTEST_TEST_BEGIN test1",
                "2000 AUTOTEST_TEST_SUCCESS test1",
                "7000 AUTOTEST_TEST_BEGIN test2",
                "8000 AUTOTEST_TEST_SUCCESS test2");

        // sweetberry file format
        // ts:<frequency>, rail1 uW, rail2 uW...
        // <tiemstamp in seconds>, <power in milliwatts>, <power in milliwatts>...
        List<String> sweetberryLog = Arrays.asList(
                "ts:1000000us, rail1 uW, rail2 uW",
                "4289, 100, 100",
                "4290, 1, 1",
                "4291, 1, 1",
                "4292, 100, 100",
                "4293, 100, 100",
                "4294, 100, 100",
                "0, 100, 100",
                "1, 2, 2",
                "2, 2, 2",
                "3, 100, 100",
                "4, 100, 100");

        List<PowerTimestamp> timestamps = (new PowerLogParser(listToStream(powerLog)))
                .getPowerTimestamps();
        List<PowerMeasurement> measurements = new SweetberryRawDataParser(listToStream
                (sweetberryLog), timestamps, 0L)
                .getPowerMeasurements();
        assertEquals(2, measurements.size());
        assertEquals(PowerTimestampStatus.VALID, measurements.get(0).getStatus());
        assertEquals(1f, measurements.get(0).getStatistic("rail1 uW"));
        assertEquals(1f, measurements.get(0).getStatistic("rail2 uW"));

        assertEquals(PowerTimestampStatus.VALID, measurements.get(1).getStatus());
        assertEquals(2f, measurements.get(1).getStatistic("rail1 uW"));
        assertEquals(2f, measurements.get(1).getStatistic("rail2 uW"));
    }

    private InputStreamReader listToStream(List<String> input) {
        String stringthing = String.join("\n", input);
        return new InputStreamReader(new ByteArrayInputStream(stringthing.getBytes()));
    }
}
