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

import com.google.android.power.tests.MonsoonRawDataParser;
import com.google.android.power.tests.PowerLogParser;
import com.google.android.power.tests.PowerMeasurement;
import com.google.android.power.tests.PowerTimestamp;
import com.google.android.power.tests.PowerTimestamp.PowerTimestampStatus;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public class MonsoonRawDataParaserTests extends TestCase {

    public void testParserIgnoresSamplesOutOfBoundaries() {
        // autotester.log file format
        // <tiemstamp in milliseconds> <AUTOTEST_TEST_BEGIN|AUTOTEST_TEST_SUCESS> <tag>
        List<String> powerLog = Arrays.asList(
                "1000 AUTOTEST_TEST_BEGIN test1",
                "1001 AUTOTEST_TEST_SUCCESS test1");

        // monsoon file format
        // <tiemstamp in seconds> <current in amperes>
        List<String> monsoonLog = Arrays.asList(
                "0 100",
                "1 1",
                "1 3",
                "2 100",
                "3 100");
        List<PowerTimestamp> timestamps = (new PowerLogParser(listToStream(powerLog)))
                .getPowerTimestamps();
        List<PowerMeasurement> measurements = new MonsoonRawDataParser(listToStream(monsoonLog),
                timestamps).getPowerMeasurements();
        assertEquals(1, measurements.size());
        assertEquals(PowerTimestampStatus.VALID, measurements.get(0).getStatus());
        assertEquals(2000f, measurements.get(0).getStatistic(MonsoonRawDataParser
                .AVERAGE_CURRENT_VALUE_NAME));
    }

    public void testParserMultipleMeasures() {
        // autotester.log file format
        // <tiemstamp in milliseconds> <AUTOTEST_TEST_BEGIN|AUTOTEST_TEST_SUCESS> <tag>
        List<String> powerLog = Arrays.asList(
                "1000 AUTOTEST_TEST_BEGIN test1",
                "2000 AUTOTEST_TEST_SUCCESS test1",
                "4000 AUTOTEST_TEST_BEGIN test2",
                "6000 AUTOTEST_TEST_SUCCESS test2");

        // monsoon file format
        // <tiemstamp in seconds> <current in amperes>
        List<String> monsoonLog = Arrays.asList(
                "1 1",
                "1 2",
                "2 3",
                "2 4",
                "3 1000",
                "3 1000",
                "4 2",
                "4 4",
                "5 6",
                "5 8",
                "6 10",
                "6 12",
                "7 1000",
                "7 1000");
        List<PowerTimestamp> timestamps = (new PowerLogParser(listToStream(powerLog)))
                .getPowerTimestamps();
        List<PowerMeasurement> measurements = new MonsoonRawDataParser(listToStream(monsoonLog),
                timestamps).getPowerMeasurements();
        assertEquals(2, measurements.size());
        assertEquals(PowerTimestampStatus.VALID, measurements.get(0).getStatus());
        assertEquals(2500f, measurements.get(0).getStatistic(MonsoonRawDataParser
                .AVERAGE_CURRENT_VALUE_NAME));
        assertEquals(PowerTimestampStatus.VALID, measurements.get(1).getStatus());
        assertEquals(7000f, measurements.get(1).getStatistic(MonsoonRawDataParser
                .AVERAGE_CURRENT_VALUE_NAME));
    }

    public void testParserInvalidatesMeasureIfItReachesEOF() {
        // autotester.log file format
        // <tiemstamp in milliseconds> <AUTOTEST_TEST_BEGIN|AUTOTEST_TEST_SUCESS> <tag>
        List<String> powerLog = Arrays.asList(
                "1000 AUTOTEST_TEST_BEGIN test1",
                "3000 AUTOTEST_TEST_SUCCESS test1");

        // monsoon file format
        // <tiemstamp in seconds> <current in amperes>
        List<String> monsoonLog = Arrays.asList(
                "0 1",
                "1 1",
                "1 1",
                "2 1",
                "2 1");
        List<PowerTimestamp> timestamps = (new PowerLogParser(listToStream(powerLog)))
                .getPowerTimestamps();
        List<PowerMeasurement> measurements = new MonsoonRawDataParser(listToStream(monsoonLog),
                timestamps).getPowerMeasurements();
        assertEquals(1, measurements.size());
        assertEquals(PowerTimestampStatus.INVALID, measurements.get(0).getStatus());
        assertEquals(Float.NaN, measurements.get(0).getStatistic(MonsoonRawDataParser
                .AVERAGE_CURRENT_VALUE_NAME));
    }

    private InputStreamReader listToStream(List<String> input) {
        String stringthing = String.join("\n", input);
        return new InputStreamReader(new ByteArrayInputStream(stringthing.getBytes()));
    }
}
