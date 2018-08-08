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

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.FileUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;

public class SweetberryTestRunner extends PowerTestRunnerBase {

    @Option(name = "sweetberry-exec",
            description = "Path to the sweetberry executable file.", mandatory = true)
    private String sweetberryExec = null;

    @Option(name = "sweetberry-board-json",
            description = "Path to the sweetberry board json file. ", mandatory = true)
    private String sweetberryBoard = null;

    @Option(name = "sweetberry-scenario-json",
            description = "Path to the sweetberry scenario json file. ", mandatory = true)
    private String sweetberryScenario = null;

    private static final String SWEETBERRY_RAW_FILE = "sweetberry_raw";
    private File mRawFileOut;

    @Option(name = "sweetberry-duration",
            description = "Duration of sweetberry measurement in seconds", mandatory = true)
    private long mSweetBerryDuration = 0;

    private long mSweetberryStartTime;


    @Override
    protected void measurePower() {
        try {
            String[] cmd =
                    new String[] {
                        sweetberryExec,
                        "-b",
                        sweetberryBoard,
                        "-c",
                        sweetberryScenario,
                        "-t",
                        "20000"
                    };

            mRawFileOut = FileUtil.createTempFile(SWEETBERRY_RAW_FILE, ".txt");
            Writer writer = new BufferedWriter(new FileWriter(mRawFileOut, true));


            CLog.d("Run sweetberry command : %s", java.util.Arrays.toString(cmd));
            CLog.d("Sweetberry command will run for: %d seconds", mSweetBerryDuration);
            Process pr = Runtime.getRuntime().exec(cmd);
            BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));

            mSweetberryStartTime = System.currentTimeMillis();
            CLog.d("Sweetberry process started at: %d", mSweetberryStartTime);
            long currentTime = System.currentTimeMillis();
            int nullLineCount = 0;
            while (currentTime - mSweetberryStartTime < (mSweetBerryDuration * 1000)) {
                String line = input.readLine();
                currentTime = System.currentTimeMillis();

                if (line != null) {
                    writer.write(String.format("%s\n", line));
                    nullLineCount = 0;
                    continue;
                }

                nullLineCount++;
                if (nullLineCount > 1000) {
                    throw new IllegalStateException("Sweetberry process got stuck.");
                }

                try{
                    if(pr.exitValue() == 1) {
                        BufferedReader error = new BufferedReader(
                                new InputStreamReader(pr.getErrorStream()));
                        StringBuilder errorMessage = new StringBuilder();
                        errorMessage.append("Sweetberry command failed.");
                        errorMessage.append("\n");
                        String errorLine;
                        while ((errorLine = error.readLine()) != null) {
                            errorMessage.append(errorLine);
                            errorMessage.append("\n");
                        }

                        throw new IllegalStateException(errorMessage.toString());
                    }
                } catch (IllegalThreadStateException e){
                    // process hasn't exited. keep running.
                }
            }

            CLog.d("Sweetberry process ended at: %d", currentTime);
            CLog.d("Destroying sweetberry process");
            pr.destroy();
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void cleanTestFiles() throws DeviceNotAvailableException {
        super.cleanTestFiles();
        FileUtil.deleteFile(mRawFileOut);
    }

    @Override
    protected void reportResults() throws DeviceNotAvailableException {
        PostingHelper.postFile(mListener, mRawFileOut, LogDataType.TEXT, SWEETBERRY_RAW_FILE);

        InputStreamReader stream = null;
        try {
            stream = new InputStreamReader(new FileInputStream(mRawFileOut));
        } catch (FileNotFoundException e) {
            CLog.e("Sweetberry raw file not found.");
            CLog.e(e);
        }

        File timeStampFile = extractAndPostPowerLog();
        SweetberryRawDataParser parser = new SweetberryRawDataParser(stream,
                extractTimestamps(timeStampFile), mSweetberryStartTime);
        SweetberryPowerStats stats =
                new SweetberryPowerStats(
                        mListener,
                        getDevice(),
                        parser.getPowerMeasurements(),
                        mSchemaRUPair,
                        mRuSuffix,
                        mSchemaSuffix);
        stats.run(mDecimalPlaces);

        // Parse Bugreport and post results file.
        BugreportAnalyzer bugreportAnalyzer = new BugreportAnalyzer(mListener, getDevice());
        bugreportAnalyzer.run();
    }
}
