/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.google.android.power.collectors.PowerTestLog;
import com.google.common.io.CharStreams;

// TODO(htellez): Remove dependency from tradefed classes so that this class can be easily exported.
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.LogDataType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Class that generates charts from Monsoon files. */
public class PowerGraph {
    private static final String PREFIX = "PowerChart_";
    private static final String HTML_URI = "/html/PowerChart_template.html";
    private final File mMonsoonFile;
    private final List<PowerMeasurement> mMeasurements;
    private final double mVoltage;

    // Tags used in HTML template
    private static final String BUILDFLAVOR_TAG = "[$BUILDFLAVOR]";
    private static final String BUILDALIAS_TAG = "[$BUILDALIAS]";
    private static final String VOLTAGE_TAG = "[$VOLTAGE]";
    private static final String TESTCASE_TAG = "[$TESTCASE]";
    private static final String AVGPOWER_TAG = "[$AVGPOWER]";
    private static final String POWERDATA_TAG = "[$POWERDATA]";
    private final int mSamplingFrequency;
    private final ITestDevice mDevice;
    private BufferedWriter mBufferedWritter;
    private List<PowerTestLog> mCharts;

    public PowerGraph(
            File monsoonFile,
            double voltage,
            int samplingFrequency,
            List<PowerMeasurement> measurements,
            ITestDevice device) {
        mMeasurements = measurements;
        mVoltage = voltage;
        mMonsoonFile = monsoonFile;
        mSamplingFrequency = samplingFrequency;
        mDevice = device;
    }

    public List<PowerTestLog> getPowerCharts() {
        if (mCharts != null) {
            return mCharts;
        }
        mCharts = new ArrayList<>();

        // Open monsoon raw data file for read
        try {
            BufferedReader bufferedReader =
                    new BufferedReader(new InputStreamReader(new FileInputStream(mMonsoonFile)));
            for (PowerMeasurement measurement : mMeasurements) {
                File chart = createPowerChart(measurement, bufferedReader);
                mCharts.add(
                        new PowerTestLog(chart, PREFIX + measurement.getTag(), LogDataType.HTML));
            }
        } catch (DeviceNotAvailableException | IOException e) {
            throw new RuntimeException(e);
        }

        return mCharts;
    }

    private File createPowerChart(PowerMeasurement measurement, BufferedReader bufferedReader)
            throws IOException, DeviceNotAvailableException {

        String testTag = measurement.getTag();
        if (measurement.getStatus() == PowerTimestamp.PowerTimestampStatus.INVALID) {
            return null;
        }

        double averagePowerInMw =
                measurement.getStatistic(MonsoonRawDataParser.AVERAGE_CURRENT_VALUE_NAME)
                        * (float) mVoltage;

        String powerChartTemplate = CharStreams
                .toString(new InputStreamReader(getClass().getResourceAsStream(HTML_URI)));

        // Replace TAGs with test information
        powerChartTemplate =
                powerChartTemplate
                        .replace(BUILDFLAVOR_TAG, mDevice.getBuildFlavor())
                        .replace(BUILDALIAS_TAG, mDevice.getBuildAlias())
                        .replace(VOLTAGE_TAG, String.format("%.1f V", mVoltage))
                        .replace(AVGPOWER_TAG, String.format("%.1f mW", averagePowerInMw))
                        .replace(TESTCASE_TAG, testTag);

        File chart = File.createTempFile(PREFIX + testTag, ".html");
        mBufferedWritter = new BufferedWriter(new FileWriter(chart, true));
        BufferedReader bufReader = new BufferedReader(new StringReader(powerChartTemplate));

        String line;
        while ((line = bufReader.readLine()) != null) {
            if (line.contains(POWERDATA_TAG)) {
                // Write power data into HTML
                writeDataSeries(
                        measurement.getTimestamp().getStartTime(),
                        measurement.getTimestamp().getEndTime(),
                        bufferedReader);
            } else {
                mBufferedWritter.write(line + "\n");
            }
        }

        mBufferedWritter.close();
        return chart;
    }

    // Import monsoon data into data container
    private void writeDataSeries(long timeStart, long timeEnd, BufferedReader mBufferedReader)
            throws IOException {
        mBufferedWritter.write(String.format("\"time,%s\\n", mDevice.getSerialNumber()));
        // Only include the data points for reporting metrics calculation
        String line = null;
        Pattern rawDataPatt = Pattern.compile("^(\\d+) (\\S.*)");
        long timeStamp = timeStart;
        long previousTimeSTamp = timeStart;

        long subSecs = 0;
        double currentInmA;

        // Keep reading file until EOF or timestamp is after test ended
        while ((line = mBufferedReader.readLine()) != null) {
            Matcher rawDataMatch = rawDataPatt.matcher(line.trim());

            if (!rawDataMatch.matches()) {
                // Invalid line. Skip to next sample.
                continue;
            }

            timeStamp = Long.valueOf(rawDataMatch.group(1));

            if (timeStamp * 1000 < timeStart) {
                // Still haven't reached relevant samples. Skip to next sample.
                continue;
            }

            if (timeStamp * 1000 > timeEnd) {
                // Already passed relevant samples.
                break;
            }

            currentInmA = Double.valueOf(rawDataMatch.group(2)) * 1000;

            // Reset the milliseconds only when time stamp changes (representing a new second)
            if (timeStamp > previousTimeSTamp) {
                subSecs = 0;
            }
            previousTimeSTamp = timeStamp;

            // Monsoon sometimes log more samples than the sampling rate within a second
            // This avoids going over .999 second
            subSecs = Math.min(subSecs, mSamplingFrequency - 1);

            // Convert timestamp to be in millisecond resolution
            long timeStampGraph =
                    timeStamp * 1000 + (subSecs % mSamplingFrequency) * 1000 / mSamplingFrequency;
            double power = mVoltage * currentInmA;
            mBufferedWritter.write(String.format("%d,%.3f\\n", timeStampGraph, power));
            subSecs++;
        }

        mBufferedWritter.write("\",\n");
    }
}
