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

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PowerLogParser {

    private static final String TEST_START_TAG = "AUTOTEST_TEST_BEGIN";
    private static final String TEST_END_TAG = "AUTOTEST_TEST_SUCCESS";
    private static final Pattern mTestStartPattern = Pattern.compile("^(\\d+) " + TEST_START_TAG +
            " (\\S.*)");
    private static final Pattern mTestEndPattern = Pattern.compile("^(\\d+) " + TEST_END_TAG +
            " (\\S.*)");
    private InputStreamReader mReader;
    private List<PowerTimestamp> mTimestamps;

    public PowerLogParser(File file) throws FileNotFoundException {
        this(new InputStreamReader(new FileInputStream(file)));
    }

    public PowerLogParser(InputStreamReader streamReader) {
        mReader = streamReader;
    }

    /**
     * Parse test cases and their start and end times from device timestamp file.
     *
     * @return List of PowerTimeStamp
     */
    public List<PowerTimestamp> getPowerTimestamps() {
        if (mTimestamps != null){
            return mTimestamps;
        }

        if (mReader == null) {
            return new ArrayList<PowerTimestamp>();
        }

        Map<String, PowerTimestamp> timestampsMap = new HashMap<>();
        BufferedReader bufferReader = new BufferedReader(mReader);

        try {
            // Parse timestamp file
            String line;
            while ((line = bufferReader.readLine()) != null) {
                try {
                    Matcher testStartMatch = mTestStartPattern.matcher(line.trim());
                    if (testStartMatch.matches()) {
                        Long time = Long.valueOf(testStartMatch.group(1));
                        String tag = testStartMatch.group(2);
                        if (!timestampsMap.containsKey(tag)) {
                            timestampsMap.put(tag, new PowerTimestamp(tag));
                        }
                        timestampsMap.get(tag).setStartTime(time);
                    }

                    Matcher testEndMatch = mTestEndPattern.matcher(line.trim());
                    if (testEndMatch.matches()) {
                        Long time = Long.valueOf(testEndMatch.group(1));
                        String tag = testEndMatch.group(2);
                        if (!timestampsMap.containsKey(tag)) {
                            timestampsMap.put(tag, new PowerTimestamp(tag));
                        }
                        timestampsMap.get(tag).setEndTime(time);
                    }
                } catch (NumberFormatException | NullPointerException e) {
                    CLog.e("Failed to parse measurement: %s", e.toString());
                    CLog.e(e);
                }
            }
        } catch (IOException e) {
            CLog.e("Failed to parse the device's timestamp log: %s", e.toString());
            CLog.e(e);
        } finally {
            StreamUtil.close(bufferReader);
        }

        mTimestamps = new ArrayList<>(timestampsMap.values());
        validateTimestamps(mTimestamps);

        CLog.d("Timestamps parsed: ");
        for (PowerTimestamp result : mTimestamps) {
            CLog.d(result.toString());
        }

        return mTimestamps;
    }

    private void validateTimestamps(List<PowerTimestamp> timestamps) {
        // sorts the timestamps according to their start time.
        Collections.sort(timestamps);

        // invalidate all those with irregular start and end times.
        for (PowerTimestamp measurement : timestamps) {
            // both start and end time must be defined.
            if (measurement.getStartTime() == null || measurement.getEndTime() == null) {
                measurement.setStatus(PowerTimestampStatus.INVALID, "Either the start or end time "
                        + "isn't defined");
                continue;
            }

            // start time must be lower than end time.
            if (measurement.getStartTime() > measurement.getEndTime()) {
                measurement.setStatus(PowerTimestampStatus.INVALID, "Start time is greater than "
                        + "end time.");
                continue;
            }
        }

        // invalidate all those that intersect with some other.
        List<PowerTimestamp> overlappingTimestamps = new ArrayList<>();
        for (int i = 0; i < timestamps.size(); i++) {
            PowerTimestamp timestamp1 = timestamps.get(i);
            // ignore previously invalidated ones.
            if (timestamp1.getStatus() == PowerTimestampStatus.INVALID) {
                continue;
            }

            for (int j = i + 1; j < timestamps.size(); j++) {
                PowerTimestamp timestamp2 = timestamps.get(j);
                // ignore previously invalidated ones.
                if (timestamp2.getStatus() == PowerTimestampStatus.INVALID) {
                    continue;
                }

                if (timestamp2.getStartTime() < timestamp1.getEndTime()) {
                    if (!overlappingTimestamps.contains(timestamp1)) {
                        overlappingTimestamps.add(timestamp1);
                    }

                    if (!overlappingTimestamps.contains(timestamp2)) {
                        overlappingTimestamps.add(timestamp2);
                    }
                }
            }
        }

        for (PowerTimestamp timestamp : overlappingTimestamps) {
            timestamp.setStatus(PowerTimestampStatus.INVALID,
                    "Intersected with some other measurement");
        }

        // All the remaining timestamps are valid.
        for (PowerTimestamp timestamp : timestamps) {
            if (timestamp.getStatus() == PowerTimestampStatus.UNKNWON) {
                timestamp.setStatus(PowerTimestampStatus.VALID, "Time limits are well defined and "
                        + "is not intersected with other measurments");
            }
        }
    }
}
