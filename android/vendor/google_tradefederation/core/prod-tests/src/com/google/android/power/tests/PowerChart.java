/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.google.android.power.tests.PowerMonitor.AverageCurrentResult;
import com.google.android.power.tests.PowerMonitor.PowerInfo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * This class is for the plotting monsoon power data
 */
public class PowerChart {
    private final static String TEST_START_TAG = "AUTOTEST_TEST_BEGIN";
    private final static String TEST_END_TAG = "AUTOTEST_TEST_SUCCESS";
    private final static String POWER_SAMPLE_TAG = "PowerSample";
    private final static SimpleDateFormat DATEFORMATTER = new SimpleDateFormat(
            "yyyy,MM,dd,HH,mm,ss,SSS");
    private final static SimpleDateFormat TOOLTIPFORMATTER = new SimpleDateFormat("HH:mm:ss.SSS");
    private final static String PREFIX = "PowerChart";
    private static Writer mBufferedWritter;

    public static void createPowerChart(
            ITestInvocationListener mListener,
            ITestDevice mTestDevice,
            float monsoonVoltage,
            @SuppressWarnings("unused") long monsoonSamples,
            List<AverageCurrentResult> averageResults,
            List<PowerInfo> sortedPowerResults,
            boolean smartSampling)
            throws IOException, DeviceNotAvailableException {
        File mPowerChartHTML = FileUtil.createTempFile(PREFIX, ".html");

        if (averageResults.size() < 1) {
            CLog.d("No Power Metrics found, skip generating Power Chart.");
            return;
        }

        float averagePowerInMw = monsoonVoltage * averageResults.get(0).mAverageCurrent;
        String mTitle = String.format(
                "'Power Usage  @ %.1f V - TestCase: %s (%s %s), Average Power = %.1f mW'",
                monsoonVoltage, averageResults.get(0).mTestCase, mTestDevice.getBuildFlavor(),
                mTestDevice.getBuildAlias(), averagePowerInMw);

        openHTMLFile(mPowerChartHTML);

        writeHTMLHeader();
        writeDataSeries(mTestDevice.getSerialNumber(), monsoonVoltage, sortedPowerResults,
                smartSampling);

        writeChartType("LineChart");
        writeChartOption("title", mTitle);
        writeChartOption("hAxis.title", "'Time'");
        writeChartOption("hAxis.format", "'HH:mm:ss'");
        writeChartOption("hAxis.gridlines.count", "20");
        writeChartOption("hAxis.slantedText", "true");
        writeChartOption("hAxis.slantedTextAngle", "90");
        writeChartOption("vAxis.title", "'Power [mW]'");
        writeChartOption("vAxis.gridlines.count", "20");

        if (averagePowerInMw <= 500) {
            writeChartOption("vAxis.minValue", "0");
            writeChartOption("vAxis.maxValue", "50");
        }

        writeChartOption("chartArea.left", "100");
        writeChartOption("chartArea.top", "50");
        writeChartOption("chartArea.height", "600");
        writeChartOption("chartArea.width", "1000");

        writeChartOption("explorer", "''");
        writeChartOption("explorer.maxZoomIn", "0.01");
        writeChartOption("explorer.actions", "['dragToZoom', 'rightClickToReset']");
        writeChartOption("explorer.axis", "'horizontal'");
        writeChartOption("explorer.keepInBounds", "true");

        writeChartSaveLink();
        writeChartDraw();

        writeDisplaySize(1400, 800);
        writeDivs();

        closeHTMLFile();

        // Add file for sponge submission
        InputStreamSource outputSource = null;
        try {
            outputSource = new FileInputStreamSource(mPowerChartHTML);
            mListener.testLog(PREFIX, LogDataType.HTML, outputSource);
        } finally {
            StreamUtil.cancel(outputSource);
        }
    }

    private static void writeHTMLHeader() throws IOException {
        mBufferedWritter.write(
                "<script type=\"text/javascript\" src=\"https://www.google.com/jsapi\"></script>\n"
                + "<script type=\"text/javascript\">\n"
                + "  google.load(\"visualization\", \"1\", {packages:[\"corechart\"]});\n"
                + "  google.setOnLoadCallback(drawChart);\n"
                + "  function drawChart() {\n"
                );
    }

    // Import monsoon data into data container
    private static void writeDataSeries(String serialNo, float monsoonVoltage,
            List<PowerInfo> sortedPowerResults, boolean smartSampling) throws IOException {
        mBufferedWritter.write("    var data = new google.visualization.DataTable();\n"
                + "    data.addColumn('datetime','Time');\n"
                + "    data.addColumn('number','S/N:" + serialNo + "');\n"
                + "    data.addColumn({type: 'string', role: 'tooltip'});\n"
                + "    data.addRows([\n"
                );

        // Only include the data points for reporting metrics calculation
        boolean validMeasurement = false;
        boolean testEnded = false;
        int skippedSamples = 0;
        boolean writeSample = true;
        for (int i = 1; i < sortedPowerResults.size() - 1; i++) {
            PowerInfo currPowerInfo = sortedPowerResults.get(i);

            if (TEST_START_TAG.equalsIgnoreCase(currPowerInfo.mTag)) {
                validMeasurement = true;
            } else if (TEST_END_TAG.equalsIgnoreCase(currPowerInfo.mTag)) {
                validMeasurement = false;
                testEnded = true;
            } else if (validMeasurement && !testEnded
                    && POWER_SAMPLE_TAG.equalsIgnoreCase(currPowerInfo.mTag)) {

                // If smart_sampling is enabled, skip writing data points based on delta
                if (smartSampling){
                    writeSample = false;

                    PowerInfo lastPowerInfo = sortedPowerResults.get(i - 1);
                    PowerInfo nextPowerInfo = sortedPowerResults.get(i + 1);
                    Float deltaLast = absDeltaPercentage(currPowerInfo, lastPowerInfo);
                    Float deltaNext = absDeltaPercentage(currPowerInfo, nextPowerInfo);

                    // Skip only if the delta is less than 3% of previous or next data point
                    if (deltaLast < 0.03 && deltaNext < 0.03) {
                        skippedSamples++;
                    } else {
                        writeSample = true;
                        skippedSamples = 0;
                    }

                    // Skip at most 9 consecutive data points
                    if (skippedSamples == 10) {
                        writeSample = true;
                        skippedSamples = 0;
                    }
                }

                if (writeSample) {
                    writeData(currPowerInfo.mTimeStamp,
                            monsoonVoltage * Float.valueOf(currPowerInfo.mPowerData));
                }
            }
        }

        mBufferedWritter.write("\n]);\n");
    }

    private static Float absDeltaPercentage(PowerInfo powerInfo1, PowerInfo powerInfo2) {
        // If any of the compared PowerInfo is not a power sample, then return 1 to write data point
        if (!POWER_SAMPLE_TAG.equalsIgnoreCase(powerInfo1.mTag)
                || !POWER_SAMPLE_TAG.equalsIgnoreCase(powerInfo2.mTag)) {
            return 1.0f;
        }

        Float powerData1 = Float.valueOf(powerInfo1.mPowerData);
        Float powerData2 = Float.valueOf(powerInfo2.mPowerData);
        Float deltaPercentage = (powerData1 - powerData2) / powerData1;

        return Math.abs(deltaPercentage);
    }

    private static void writeData(long timeStamp, float power) throws IOException {
        mBufferedWritter.write(String.format("   [new Date(%s),%.3f,'%s,%.3f'],\n",
                DATEFORMATTER.format(timeStamp), power,
                TOOLTIPFORMATTER.format(timeStamp), power));
    }

    private static void writeChartType(String type) throws IOException {
        mBufferedWritter.write("    var wrap = new google.visualization.ChartWrapper();\n" +
                "    wrap.setChartType('" + type + "');\n" +
                "    wrap.setContainerId('chart');\n" +
                "    wrap.setDataTable(data);\n");
    }

    private static void writeChartOption(String name, String value) throws IOException {
        mBufferedWritter.write(String.format("    wrap.setOption('%s',%s);\n", name, value));
    }

    private static void writeChartSaveLink() throws IOException {
        mBufferedWritter.write(
                "    google.visualization.events.addListener(wrap, 'ready', function () {\n" +
                        "        document.getElementById('png').innerHTML = '<a href=\"'" +
                        " + wrap.getChart().getImageURI() + '\">Save chart as .png file</a>';\n" +
                        "    });\n");
    }

    private static void writeChartDraw() throws IOException {
        mBufferedWritter.write("    wrap.draw();\n" +
                "  }\n </script>\n\n");
    }

    private static void writeDisplaySize(int width, int height) throws IOException {
        mBufferedWritter.write(String.format(
                "<div id=\"chart\" style=\"width: %dpx; height: %dpx;\"></div>", width, height));
    }

    private static void writeDivs() throws IOException {
        mBufferedWritter.write("<div id=\"png\" style=\"margin-left: 50px;\"></div>\n");
    }

    private static void openHTMLFile(File file) throws IOException {
        mBufferedWritter = new BufferedWriter(new FileWriter(file, true));
    }

    private static void closeHTMLFile() throws IOException {
        mBufferedWritter.close();
    }
}
