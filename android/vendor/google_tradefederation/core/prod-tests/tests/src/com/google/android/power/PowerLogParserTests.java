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
import com.google.android.power.tests.PowerTimestamp;
import com.google.android.power.tests.PowerTimestamp.PowerTimestampStatus;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public class PowerLogParserTests extends TestCase {

    public void testPowerMeasurements() {
        List<String> input = Arrays.asList(
                "11111111111 AUTOTEST_TEST_BEGIN test1",
                "11111111112 AUTOTEST_TEST_SUCCESS test1");

        List<PowerTimestamp> timestamps = new PowerLogParser(listToStream(input))
                .getPowerTimestamps();

        assertEquals(1, timestamps.size());
        PowerTimestamp timestamp = timestamps.get(0);
        assertEquals(String.format("Unexpected start time: %s", timestamp.toString()),
                (Long) 11111111111L, timestamps.get(0).getStartTime());
        assertEquals(String.format("Unexpected end time: %s", timestamp.toString()),
                (Long) 11111111112L, timestamps.get(0).getEndTime());
        assertEquals(String.format("Unexpected status: %s", timestamp.toString()),
                PowerTimestampStatus.VALID, timestamps.get(0).getStatus());
    }

    public void testInvalidTimestampsPowerMeasurments() {
        List<String> input = Arrays.asList(
                "11111111112 AUTOTEST_TEST_BEGIN test1",
                "11111111111 AUTOTEST_TEST_SUCCESS test1");

        List<PowerTimestamp> timestamps = new PowerLogParser(listToStream(input))
                .getPowerTimestamps();

        PowerTimestamp timestamp = timestamps.get(0);
        assertEquals(String.format("Unexpected status: %s", timestamp.toString()),
                PowerTimestampStatus.INVALID, timestamp.getStatus());
    }

    public void testIntersectedPowerMeasurments() {
        List<String> input = Arrays.asList(
                "11111111111 AUTOTEST_TEST_BEGIN test1",
                "11111111115 AUTOTEST_TEST_SUCCESS test1",
                "11111111112 AUTOTEST_TEST_BEGIN test2",
                "11111111116 AUTOTEST_TEST_SUCCESS test2");

        List<PowerTimestamp> timestamps = new PowerLogParser(listToStream(input))
                .getPowerTimestamps();

        assertEquals(2, timestamps.size());
        PowerTimestamp timestamp = timestamps.get(0);
        assertEquals(String.format("Unexpected status: %s", timestamp.toString()),
                PowerTimestampStatus.INVALID, timestamp.getStatus());
        timestamp = timestamps.get(1);
        assertEquals(String.format("Unexpected status: %s", timestamp.toString()),
                PowerTimestampStatus.INVALID, timestamp.getStatus());
    }

    public void testIncompleteTimestampsPowerMeasurements() {
        List<String> input = Arrays.asList(
                "11111111111 AUTOTEST_TEST_BEGIN test1",
                "11111111113 AUTOTEST_TEST_SUCCESS test2");

        List<PowerTimestamp> timestamps = new PowerLogParser(listToStream(input))
                .getPowerTimestamps();

        PowerTimestamp timestamp = timestamps.get(0);
        assertEquals(String.format("Unexpected status: %s", timestamp.toString()),
                PowerTimestampStatus.INVALID, timestamp.getStatus());
    }

    private InputStreamReader listToStream(List<String> input) {
        String stringthing = String.join("\n", input);

        return new InputStreamReader(new ByteArrayInputStream(stringthing
                .getBytes()));
    }
}
