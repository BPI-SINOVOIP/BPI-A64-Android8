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

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * This class uploads a static link to web app in app engine. The web app uploads the bugreport and
 * monsoon to powerbug and retrieves the analysis
 */
public class BugreportAnalyzer extends PowerAnalyzer {

    private static final String POWER_ANALYSIS_FILE = "PowerAnalysis";
    private static final String TEXT_EXTENTION = ".txt";
    private static final String WEB_APP_LINK =
            "https://powerprocessor.googleplex.com/analysis.html";

    public BugreportAnalyzer(ITestInvocationListener listener, ITestDevice testDevice) {
        super(listener, testDevice);
    }

    private File getAnalysisFile() {
        File analysisFile = null;
        try {
            analysisFile = FileUtil.createTempFile(POWER_ANALYSIS_FILE, TEXT_EXTENTION);
            FileWriter fileWriter = new FileWriter(analysisFile.getAbsoluteFile());
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printWriter.println(WEB_APP_LINK);
            printWriter.close();
        } catch (IOException e) {
            CLog.e(e);
        }
        return analysisFile;
    }

    public void run() {
        PostingHelper.postFile(mListener, getAnalysisFile(), LogDataType.TEXT, POWER_ANALYSIS_FILE);
    }
}
