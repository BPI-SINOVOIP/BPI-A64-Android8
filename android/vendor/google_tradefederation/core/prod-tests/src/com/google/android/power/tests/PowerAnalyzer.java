/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tradefed.result.DeviceFileReporter;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Base class for power data analysis
 */
public class PowerAnalyzer {

    protected ITestDevice mTestDevice = null;
    protected ITestInvocationListener mListener;

    public PowerAnalyzer(ITestInvocationListener listener, ITestDevice testDevice) {
        mListener = listener;
        mTestDevice = testDevice;
    }

    // Helper method to pull a file from device
    public static File pullFile(ITestDevice testDevice, String filePath, String deviceFileName)
            throws DeviceNotAvailableException {
        File hostFile = null;

        try {
            hostFile = FileUtil.createTempFile(deviceFileName, null);
            testDevice.pullFile(String.format("%s/%s", filePath, deviceFileName), hostFile);
        } catch (IOException e) {
            CLog.e(e);
        }

        return hostFile;
    }

    /**
     * Helper method to post multiple files that match a pattern directly from the device.
     * @param device The {@link ITestDevice} to pull files from.
     * @param listener The {@link ITestInvocationListener} used to post the files.
     * @param pattern The String pattern that the files to be uploaded should match. Accepts
     * wildcards.
     * @param dataType The {@link LogDataType} to upload the files as.
     * @throws DeviceNotAvailableException
     */
    public static void postFilesFromDevice(ITestDevice device, ITestInvocationListener listener,
        String pattern, LogDataType dataType) throws DeviceNotAvailableException {

        DeviceFileReporter dfr = new DeviceFileReporter(device, listener);
        dfr.addPatterns(pattern);
        dfr.setDefaultLogDataType(dataType);
        List<String> result = dfr.run();

        CLog.d(String.format("Uploaded the following files: %s", String.join("\n", result)));
    }

    /**
     * Helper method to post bugreport from device
     *
     * @param device The {@link ITestDevice} to pull bugreport from.
     * @param listener The {@link ITestInvocationListener} used to post the files.
     * @param fileName The name of the bugreport to be posted
     */
    public static void postBugreportFromDevice(ITestDevice device, ITestInvocationListener listener,
            String fileName) {
        InputStreamSource bugreport = null;
        try {
            bugreport = device.getBugreport();
            listener.testLog(String.format("%s_%s", device.getSerialNumber(), fileName),
                    LogDataType.TEXT, bugreport);
        } finally {
            StreamUtil.cancel(bugreport);
        }
    }
}
