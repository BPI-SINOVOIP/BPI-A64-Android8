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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Helper class for managing power measurement result files stored on local host machine */
public class ResultFileUtil {

    private static final int NUM_TOKENS_PER_LINE = 2;

    private String mProduct;
    private String mBuild;

    public ResultFileUtil(ITestDevice device) {
        try {
            mProduct = device.getProductType();
            mBuild = device.getBuildAlias();
        } catch (DeviceNotAvailableException e) {
            CLog.e(e);
            mProduct = "unknown";
            mBuild = "unknown";
        }
    }

    /**
     * Extract a list of power measurement results stored on the local host for a test. Please refer
     * to the doc for writeResultToFile for the format of the result file.
     *
     * @param ruName: test's RU name
     * @param schema: test's schema
     * @return a list of floating point numbers, each is a valid power measurement. A empty list is
     *     returned if no valid measurement is found, or if test result file is not found.
     */
    public List<Float> extractResultsFromFile(String ruName, String schema) {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File resultFile = FileUtil.findFile(tempDir, buildResultFileName(ruName, schema));

        List<Float> validResults = new ArrayList<Float>();

        CLog.d(String.format("Reading results for %s %s", mProduct, mBuild));
        if (resultFile != null) {
            BufferedReader input = null;
            try {
                CLog.d(String.format("Reading from file %s", resultFile.toString()));
                input = new BufferedReader(new FileReader(resultFile));
                String line;

                /* Each line in the result file is one test result.  It consists of a
                   timestamp and the test result.
                */
                while ((line = input.readLine()) != null) {
                    String[] tokens = line.split(" ");
                    Float value;

                    if (tokens.length != NUM_TOKENS_PER_LINE) {
                        continue;
                    }
                    try {
                        value = Float.valueOf(tokens[1]);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    if (value.isInfinite() || value.isNaN()) {
                        continue;
                    }

                    validResults.add(value.floatValue());
                }
            } catch (IOException e) {
                CLog.e(String.format("Fail to read from file %s", resultFile.toString()));
            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException e) {
                        CLog.e(String.format("Fail to close file %s", resultFile.toString()));
                    }
                }
            }
        }

        return validResults;
    }

    /**
     * Write a test's power measurement to its result file on the local host machine. Each result
     * file is a human readable txt file. Each line represent the result of one test run's power
     * measurement. It contains two items, a Unix timestamp in millisecond precision, and the
     * average power in mA.
     *
     * @param ruName: test's RU name
     * @param schema: test's schema
     * @param timeStamp: Unix timestamp value at the time when the result is generated
     * @param result: test's power measurement result
     * @param append: if true, the result is appended to the existing result file; if false, the
     *     result overwrites the existing result file
     * @return true if write is successful
     */
    public boolean writeResultToFile(
            String ruName, String schema, long timeStamp, double result, boolean append) {

        String resultFilePath =
                String.format(
                        "%s/%s",
                        System.getProperty("java.io.tmpdir"), buildResultFileName(ruName, schema));
        File resultFile = new File(resultFilePath);

        /* Each line in the result file is one test result.  It consists of a
           timestamp and the test result.
        */
        String resultString = String.format("%d %f\n", timeStamp, result);
        boolean writeSuccess = true;
        FileWriter fileOut = null;
        try {
            CLog.d(String.format("Record result to file %s", resultFile.toString()));
            fileOut = new FileWriter(resultFile, append);
            fileOut.write(resultString);
        } catch (Exception e) {
            CLog.e(e);
            writeSuccess = false;
        } finally {
            if (fileOut != null) {
                try {
                    fileOut.close();
                } catch (IOException e) {
                    CLog.e(String.format("Fail to close file %s", resultFile.toString()));
                }
            }
        }

        return writeSuccess;
    }

    /**
     * Result file is stored in the default temp directory on host machine. The name of the file is
     * <product_name>-<release_id>-<ru>-<schema>.txt. i.e.
     * marlin-ORF69B-VerizonDoU-DoUVerizonTests-testIdle-WIFIIdleTest.txt
     *
     * <p>Multiple test runs of the same test on the same local host will be written to the same
     * file. New results are always appended to the end of the file.
     *
     * <p>Runs on different devices with the same product and build id are treated as multiple test
     * runs of the same test.
     *
     * @param ruName: test's RU name
     * @param schema: test's schema name
     * @return result file name String
     */
    private String buildResultFileName(String ruName, String schema) {
        String resultFilePath = String.format("%s-%s-%s-%s.txt", mProduct, mBuild, ruName, schema);
        return resultFilePath;
    }
}
