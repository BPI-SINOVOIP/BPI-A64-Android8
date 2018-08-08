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
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auxiliar class to parse a sweetberry raw file.
 * Sweetberry raw file sample:
 *
 * ts:1055232us, VBAT uW, VDD_MEM uW, VDD_CORE uW, VDD_GFX uW, VDD_1V8_PANEL uW
 * 328.346681, 12207.03, 15869.14, 57983.40, 2441.41, 0.00
 * 329.401939, 12207.03, 15869.14, 57983.40, 2441.41, 0.00
 * 330.457197, 12207.03, 15869.14, 57983.40, 2441.41, 0.00
 */
public class SweetberryRawDataParser implements RawPowerDataParser {

    public static final String SEPARATOR = ", ";
    public static final String ITERATION_SEPARATOR = "#";
    public static final int MAX_TIMESTAMP_VALUE_MS = 4295000;
    private final InputStreamReader mStreamReader;
    private static final String REAL_NUMBER_PATTERN = "-?\\d+(\\.\\d+)?";
    private static final Pattern RAW_DATA_PATT = Pattern.compile(String.format("^%s(, %s)+$",
            REAL_NUMBER_PATTERN,
            REAL_NUMBER_PATTERN));
    private final long mStartTime;
    private final List<PowerTimestamp> mTimestamps;
    private final List<PowerMeasurement> mMeasurements = new ArrayList<PowerMeasurement>();
    private Long mFirstTimestampMs;
    private Long mPreviousTimestampMs;
    private int mTimestampOverflowsCount = 0;
    private boolean parsed = false;

    /**
     * MonsoonRawDataParser constructor.
     *
     * @param streamReader StreamReader representing a monsoon file.
     * @param startTime    The number of milliseconds from epoch to when the sweetberry samples
     *                     taking samples. This is needed to correlate samples' timestamps with
     *                     sweetberry timestamps.
     */
    public SweetberryRawDataParser(InputStreamReader streamReader, List<PowerTimestamp>
            timestamps, long startTime) {
        mStreamReader = streamReader;
        mStartTime = startTime;
        mTimestamps = timestamps;
    }

    // Extracts measurements averaging each rail's samples. The units are micro watts (uW).
    @Override
    public List<PowerMeasurement> getPowerMeasurements() {
        if (mStreamReader == null) {
            return new ArrayList<PowerMeasurement>();
        }

        if (parsed)
        {
            return mMeasurements;
        }


        mTimestampOverflowsCount = 0;
        mFirstTimestampMs = null;
        mPreviousTimestampMs = null;

        BufferedReader bufferReader = null;
        CLog.d("The first sweetberry timestamp correlates with the host's timestamp: %d",
                mStartTime);
        try {
            bufferReader = new BufferedReader(mStreamReader);

            String firstLine = bufferReader.readLine();
            if (firstLine == null) {
                CLog.e("Sweetberry file is emtpy.");
                return new ArrayList<PowerMeasurement>();
            }

            String[] headers = firstLine.split(SEPARATOR);

            // For each test case
            for (PowerTimestamp timestamp : mTimestamps) {
                if (timestamp.getStatus() != PowerTimestampStatus.VALID) {
                    // Do not read Monsoon file if measurement is invalid
                    continue;
                }

                mMeasurements.add(computeStatisticsForTimestamp(bufferReader, headers, timestamp));
            }
        } catch (IOException | NumberFormatException e) {
            CLog.e("Fails to read monsoon raw file %s", e.toString());
        } finally {
            StreamUtil.close(bufferReader);
        }

        parsed = true;
        return mMeasurements;
    }

    // iterates through the sweetberry raw file lines looking for the samples that correspond
    // to the specified measurement and computes its statistics.
    private PowerMeasurement computeStatisticsForTimestamp(BufferedReader bufferReader, String[] headers,
            PowerTimestamp timestamp) throws IOException {
        String line;
        float[] totalPowers = new float[headers.length - 1];
        int numSamples = 0;
        PowerMeasurement measurement = new PowerMeasurement(timestamp);

        boolean passedFirstRelevantSample = false;
        // Keep reading file until EOF or timestamp is after test ended
        while ((line = bufferReader.readLine()) != null) {
            Matcher rawDataMatch = RAW_DATA_PATT.matcher(line.trim());

            if (!rawDataMatch.matches()) {
                // Invalid line. Skip to next sample.
                continue;
            }

            String[] fragments = line.split(SEPARATOR);
            long sampleTime = MassagedTimestamp(fragments[0]);


            if (sampleTime < timestamp.getStartTime()) {
                // Still haven't reached relevant samples. Skip to next sample.
                continue;
            }

            if(!passedFirstRelevantSample){
                passedFirstRelevantSample = true;
                CLog.d("First sample within boundaries for %s: %s", timestamp.getTag(), line);
            }

            if (sampleTime > timestamp.getEndTime()){
                // Already passed relevant samples.
                CLog.d("First sample out of boundaries for %s: %s", timestamp.getTag(), line);
                break;
            }

            numSamples++;
            for (int i = 0; i < totalPowers.length; i++) {
                totalPowers[i] += (Float.valueOf(fragments[i + 1]));
            }

            for (String name : measurement.getStatistics().keySet()) {
                CLog.d("average value for  %s: %s - %f uW", timestamp.getTag(), name,
                        measurement.getStatistic(name));
            }

            CLog.d("The test duration for %s is %d secs", timestamp.getTag(),
                    timestamp.getDuration());
            CLog.d(timestamp.toString());
        }

        // If line == null then the end of the file has been reached, which means that the
        // timestamps are out of the range of the monsoon file. This samples are invalid, there
        // should always be a buffer at the end of the monsoon file.
        if (line == null) {
            timestamp.setStatus(PowerTimestampStatus.INVALID, "Reached Monsoon file end "
                    + "before measurement.getEndTime().");
        } else {
            for (int i = 0; i < totalPowers.length; i++) {
                measurement.addStatistic(headers[i + 1], totalPowers[i] / numSamples);
            }
        }

        return measurement;
    }

    private long MassagedTimestamp(String timestampString) {
        long timestampMs = Math.round(Float.valueOf(timestampString) * 1000);

        if (mFirstTimestampMs == null){
            mFirstTimestampMs = timestampMs;
            mPreviousTimestampMs = timestampMs;
            return mStartTime;
        }

        // The integer for the seconds overflows when it reaches its maximum value and starts from
        // zero again.
        if (mPreviousTimestampMs > timestampMs){
            mTimestampOverflowsCount++;
        }

        mPreviousTimestampMs = timestampMs;
        return timestampMs - mFirstTimestampMs + mStartTime +
                (mTimestampOverflowsCount * MAX_TIMESTAMP_VALUE_MS);
    }
}
