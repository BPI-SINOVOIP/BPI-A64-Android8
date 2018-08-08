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

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.BulkEmailer;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.util.Map;

public class PowerUtil {

    private static final long MAX_HOST_DEVICE_TIME_OFFSET = 5 * 1000;
    private static final long CMD_TIMEOUT = 25 * 1000; // 25 secs

    public static void setDeviceTime(ITestDevice testDevice) throws DeviceNotAvailableException {

        // Only sync the time if the offset is larger than 5 seconds. This can
        // preserve the time zone that shown in the device.
        if (!isTimeSynced(testDevice)) {
            java.util.Date timeNow = new java.util.Date();
            long hostTime = timeNow.getTime();

            if (testDevice.getApiLevel() < 23) {
                // Set date using Epoch format
                testDevice.executeShellCommand(String.format("date -u %d", hostTime / 1000));
                CLog.i("Set device clock with Epoch format : %s",
                        String.format("date -u %d", hostTime / 1000));
            } else {
                // Set date using "MMDDhhmmCCYY.ss" format
                java.text.SimpleDateFormat SDformat = new java.text.SimpleDateFormat(
                        "MMddHHmmyyyy.ss");
                SDformat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                String timeNowStr = SDformat.format(timeNow);

                testDevice.executeShellCommand(String.format("date -u %s", timeNowStr));
                CLog.i("Set device clock with \"MMDDhhmmCCYY.ss\": %s",
                        String.format("date -u %s", timeNowStr));
            }

            // verify the change is success
            if (!isTimeSynced(testDevice)) {
                CLog.e("Fails to reset the device clock");
                Assert.fail("Fails to reset the device clock");
                return;
            }
        }
    }

    private static boolean isTimeSynced(ITestDevice testDevice) throws DeviceNotAvailableException {
        long timeOffset = getDeviceTimeOffset(testDevice);
        return Math.abs(timeOffset) <= MAX_HOST_DEVICE_TIME_OFFSET;
    }

    public static void startBulkEmailer(IConfiguration config) {
        // start the bulk email sender in a separate thread
        BulkEmailer mailer;
        try {
            mailer = BulkEmailer.loadMailer(config);
            mailer.sendEmailsBg();
        } catch (ConfigurationException e) {
            CLog.e("Unable to send email.");
            return;
        }
    }

    public static void startGoogleSync(Map<String, Long> syncInfo, long testDurationMs) {
        if (syncInfo != null) {
            for (Map.Entry<String, Long> syncDetails: syncInfo.entrySet()) {
                final long numEntries = testDurationMs/syncDetails.getValue();
                startGoogleSync(syncDetails.getKey(), syncDetails.getValue().longValue(),
                        numEntries);
            }
        }
    }

    private static void startGoogleSync(final String url, final long delay, final long numEntries) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int i = 0;
                while (i < numEntries) {
                    CommandResult result = RunUtil.getDefault().runTimedCmd(CMD_TIMEOUT,
                            "/usr/bin/curl", "-L", url);
                    if (!result.getStdout().trim().contains("successfully")) {
                        CLog.e(result.getStdout().trim());
                        Assert.fail("Google sync failed");
                        break;
                    }
                    RunUtil.getDefault().sleep(delay);
                    i++;
                }
            }
        }).start();
    }

    private static long getDeviceTimeOffset(ITestDevice testDevice)
            throws DeviceNotAvailableException {
        String deviceTime = testDevice.executeShellCommand("date +%s");
        java.util.Date date = new java.util.Date();
        long hostTime = date.getTime();
        long offset = 0;

        if (deviceTime != null) {
            offset = hostTime - (Long.valueOf(deviceTime.trim()) * 1000);
        }
        CLog.d("Time offset = " + offset);
        return offset;
    }

}
