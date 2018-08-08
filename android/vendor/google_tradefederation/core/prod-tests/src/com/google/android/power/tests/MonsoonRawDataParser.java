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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class to extract information from a file generated with a monsoon power monitor.
 *
 * monsoon file format: [seconds since epoch] [current in Amperes]
 *
 * monsoon file sample:
 * 1468832232 0.003961
 * 1468832232 0.003924
 * 1468832232 0.003917
 * 1468832233 0.016210
 * 1468832233 0.003909
 */
public class MonsoonRawDataParser implements RawPowerDataParser {

    public static final String AVERAGE_CURRENT_VALUE_NAME = "average current mA";
    private final InputStreamReader mStreamReader;
    private static final Pattern RAW_DATA_PATT = Pattern.compile("^(\\d+) (\\d+(\\.\\d+)?).*");
    private final List<PowerTimestamp> mTimestamps;
    private final List<PowerMeasurement> mMeasurements = new ArrayList<PowerMeasurement>();
    private boolean parsed = false;

    public MonsoonRawDataParser(File monsoonRawFile, List<PowerTimestamp> timestamps)
            throws FileNotFoundException {
        this(new InputStreamReader(new FileInputStream(monsoonRawFile)), timestamps);
    }

    /**
     * MonsoonRawDataParser constructor.
     *
     * @param streamReader StreamReader representing a monsoon file.
     */
    public MonsoonRawDataParser(InputStreamReader streamReader, List<PowerTimestamp> timestamps) {
        mStreamReader = streamReader;
        mTimestamps = timestamps;
    }

    // Extracts measurements averaging the monsoon samples. The units are milli Amperes mA.
    @Override
    public List<PowerMeasurement> getPowerMeasurements() {
        if (mStreamReader == null) {
            return new ArrayList<PowerMeasurement>();
        }

        if (parsed) {
            return mMeasurements;
        }

        BufferedReader bufferReader = null;

        try {
            bufferReader = new BufferedReader(mStreamReader);

            // For each test case
            for (PowerTimestamp timestamp : mTimestamps) {
                if (timestamp.getStatus() != PowerTimestampStatus.VALID) {
                    // Do not read Monsoon file if timestamp is invalid
                    continue;
                }

                PowerMeasurement measurement = computeStatisticsForTimestamp(bufferReader,
                        timestamp);
                mMeasurements.add(measurement);
            }
        } catch (IOException | NumberFormatException e) {
            CLog.e("Fails to read monsoon raw file %s", e.toString());
        } finally {
            StreamUtil.close(bufferReader);
        }

        parsed = true;
        return mMeasurements;
    }

    private PowerMeasurement computeStatisticsForTimestamp(BufferedReader bufferReader,
            PowerTimestamp timestamp) throws IOException {
        String line;
        PowerMeasurement measurement = new PowerMeasurement(timestamp);

        int numSamples = 0;
        float totalCurrent = 0;

        // Keep reading file until EOF or timestamp is after test ended
        while ((line = bufferReader.readLine()) != null) {
            Matcher rawDataMatch = RAW_DATA_PATT.matcher(line.trim());

            if (!rawDataMatch.matches()) {
                // Invalid line. Skip to next sample.
                continue;
            }

            long sampleTime = Long.valueOf(rawDataMatch.group(1)).longValue();

            if (sampleTime * 1000 < timestamp.getStartTime()) {
                // Still haven't reached relevant samples. Skip to next sample.
                continue;
            }

            if (sampleTime * 1000 > timestamp.getEndTime()) {
                // Already passed relevant samples.
                break;
            }

            float current = Float.valueOf(rawDataMatch.group(2));
            numSamples++;
            totalCurrent += current;
        }

        // If line == null then the end of the file has been reached, which means that the
        // timestamps are out of the range of the monsoon file. This readings
        // are invalid, there should always be a buffer at the end of the monsoon file.
        if (line == null) {
            measurement.getTimestamp().setStatus(PowerTimestampStatus.INVALID,
                    "Reached Monsoon file end before timestamp.getEndTime().");
        } else {
            measurement.addStatistic(AVERAGE_CURRENT_VALUE_NAME,
                    totalCurrent * 1000 / numSamples);
        }

        CLog.d("average current for  %s: %f mA", timestamp.getTag(),
                measurement.getStatistic(AVERAGE_CURRENT_VALUE_NAME));
        CLog.d("duration for %s : %d", timestamp.getTag(),
                timestamp.getDuration());

        CLog.d(measurement.toString());
        return measurement;
    }
}
