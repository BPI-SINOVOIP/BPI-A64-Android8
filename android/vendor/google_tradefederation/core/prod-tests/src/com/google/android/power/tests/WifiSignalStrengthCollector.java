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

import com.google.android.tradefed.util.AttenuatorController;
import com.google.android.tradefed.util.AttenuatorState;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WifiSignalStrengthCollector implements IDeviceTest, IRemoteTest {

    public static final int ATTENUATION_INTERVAL = 60 * 1000; // 60 seconds
    private static final long POLL_INTERVAL = 3; // 3 seconds

    private static final String WIFI_SCAN_INSTRUMENTATION = "am instrument -w "
            + "com.android.test.util.wifistrengthscanner/.WifiStrengthScannerInstrumentation";

    public static final int MAX_ATTENUATION_LEVEL = 95;
    private static final long MAX_RESPONSE_TIMEOUT = 30 * 1000; // 30 seconds
    public static final Pattern WIFI_INFO_WIFI_LEVEL_PATTERN =
            Pattern.compile(".* wifi_info_wifi_level=(\\d+).*");
    public static final Pattern WIFI_INFO_RSSI_PATTERN =
            Pattern.compile(".* wifi_info_rssi=(-?\\d+).*");
    public static final Pattern SCAN_RESULT_WIFI_LEVEL_PATTERN =
            Pattern.compile(".* scan_result_wifi_level=(\\d+).*");
    public static final Pattern SCAN_RESULT_RSSI_PATTERN =
            Pattern.compile(".* scan_result_rssi=(-?\\d+).*");
    private static final Pattern DUMPSYS_WIFI_LEVEL_PATTERN =
            Pattern.compile(".* dumpsys_wifi_level=(\\d+).*");
    public static final Pattern DUMPSYS_RSSI_PATTERN =
            Pattern.compile(".* dumpsys_rssi=(-?\\d+).*");
    private static final String CSV_HEADER = "timestamp,attenuation_level,wifi_info_wifi_level,"
            + "wifi_info_rssi,scan_result_wifi_level,scan_result_rssi,"
            + "dumsys_wifi_level,dumpsys_rssi,status";
    private ITestDevice mDevice;
    private AttenuatorController mAttenuatorController;
    private StringBuilder mCsvSummary;
    private StringBuilder mFullWifiLogBuilder;

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Option(name = "wifi-att-ip", description = "Wifi attenuator IP", mandatory = true)
    private String mIp = null;

    @Option(name = "wifi-att-channel", description = "Channel number in case the attenuator is a "
            + "multichannel attenuator. Leave undefined if it is a single channel attenuator.")
    private Integer mChannel = null;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        setUp();
        loopThroughAttenuationLevels();
        tearDown(listener);
    }

    private void loopThroughAttenuationLevels() throws DeviceNotAvailableException {
        int totalDuration = MAX_ATTENUATION_LEVEL * ATTENUATION_INTERVAL;
        long start = System.currentTimeMillis();
        int lastConnectedAttenuationLevel = -1;
        while (System.currentTimeMillis() < start + (totalDuration * 1000)) {
            getDevice().executeShellCommand("input keyevent 82");
            String instrumentationResult = triggerWifiStrengthScannerInstrumentation();

            boolean isConnected = isConnected(instrumentationResult);
            int attenuationLevel = getAttenuatorController().getCurrentLevel();
            if (isConnected) {
                lastConnectedAttenuationLevel = attenuationLevel;
            } else if (attenuationLevel - lastConnectedAttenuationLevel > 5) {
                CLog.i("Stopping wifi scanner. DUT has been disconnected for over 5 "
                        + "attenuation levels.");
                break;
            }

            appendWifiLogMessage(instrumentationResult);
            appendCsvSummary(instrumentationResult);

            RunUtil.getDefault().sleep(POLL_INTERVAL * 1000);
        }
    }

    private void tearDown(ITestInvocationListener listener) throws DeviceNotAvailableException {
        getDevice().executeShellCommand("svc power stayon false");
        getAttenuatorController().stop();

        writeLogAndPostIt(listener, "wifi_signal_summary_log", getSummaryCsvBuilder().toString());
        writeLogAndPostIt(listener, "wifi_signal_full_log", getFullWifiLogBuilder().toString());
    }

    private void setUp() throws DeviceNotAvailableException {
        getAttenuatorController().run();
        getDevice().executeShellCommand("svc power stayon true");
    }

    private void appendWifiLogMessage(String instrumentationResult) {
        getFullWifiLogBuilder().append("========================\n");
        getFullWifiLogBuilder()
                .append(String.format("timestamp: %d\n", System.currentTimeMillis()));
        getFullWifiLogBuilder().append(String
                .format("attenuation: %d\n", getAttenuatorController().getCurrentLevel()));
        getFullWifiLogBuilder().append(instrumentationResult);
        getFullWifiLogBuilder().append("========================\n");
    }

    private StringBuilder getFullWifiLogBuilder() {
        if (mFullWifiLogBuilder == null) {
            mFullWifiLogBuilder = new StringBuilder();
        }

        return mFullWifiLogBuilder;
    }

    private String appendCsvSummary(String instrumentationResult) {
        String summary = String.format("%d,%d,%s",
                System.currentTimeMillis(),
                getAttenuatorController().getCurrentLevel(),
                extractSummary(instrumentationResult));

        getSummaryCsvBuilder().append(summary);
        getSummaryCsvBuilder().append("\n");

        CLog.i(CSV_HEADER);
        CLog.i(summary);
        return summary;
    }

    private StringBuilder getSummaryCsvBuilder() {
        if (mCsvSummary != null) {
            return mCsvSummary;
        }

        mCsvSummary = new StringBuilder();
        mCsvSummary.append(CSV_HEADER);
        mCsvSummary.append("\n");
        return mCsvSummary;
    }

    private AttenuatorController getAttenuatorController() {
        if (mAttenuatorController != null) {
            return mAttenuatorController;
        }
        List<AttenuatorState> states = new ArrayList<>();
        for (int i = 0; i <= MAX_ATTENUATION_LEVEL; i++) {
            states.add(new AttenuatorState(i, ATTENUATION_INTERVAL));
        }

        CLog.d("Controlling attenuator at %s channel %d", mIp, mChannel);
        mAttenuatorController = new AttenuatorController(mIp, mChannel, states);
        return mAttenuatorController;
    }

    private void writeLogAndPostIt(ITestInvocationListener listener, String filename,
            String content) {
        File hostFile = null;
        try {
            hostFile = FileUtil.createTempFile(filename, null);
            FileWriter outputWriter = new FileWriter(hostFile, true);
            outputWriter.write(content);
            outputWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        PostingHelper.postFile(listener, hostFile, LogDataType.TEXT, filename);
    }

    private String extractSummary(String text) {
        String summary;
        Integer wifiInfoLevel = extractFirstInteger(text, WIFI_INFO_WIFI_LEVEL_PATTERN, 0);
        Integer wifiInfoRssi = extractFirstInteger(text, WIFI_INFO_RSSI_PATTERN, -127);
        Integer scanResultLevel = extractFirstInteger(text, SCAN_RESULT_WIFI_LEVEL_PATTERN, 0);
        Integer scanResultRssi = extractFirstInteger(text, SCAN_RESULT_RSSI_PATTERN, -127);
        Integer dumpsysWifiLevel = extractFirstInteger(text, DUMPSYS_WIFI_LEVEL_PATTERN, 0);
        Integer dumpsysRssi = extractFirstInteger(text, DUMPSYS_RSSI_PATTERN, -127);
        String status = isConnected(text) ? "connected" : "disconnected";

        summary = String.format("%d,%d,%d,%d,%d,%d,%s",
                wifiInfoLevel,
                wifiInfoRssi,
                scanResultLevel,
                scanResultRssi,
                dumpsysWifiLevel,
                dumpsysRssi,
                status);
        return summary;
    }

    private Integer extractFirstInteger(String text, Pattern pattern, Integer defaultValue) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return defaultValue;
    }

    private boolean isConnected(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.contains("wifi_strength_scanner_failure=no_scan_received")) {
                CLog.d("Wifi disconnected. No scan info.");
                return false;
            }

            if (line.contains("suplicant_state=DISCONNECTED")) {
                CLog.d("Wifi disconnected. Supplicant disconnected.");
                return false;
            }

            if (line.contains("suplicant_state=SCANNING")) {
                CLog.d("Wifi disconnected. Supplicant scanning");
                return false;
            }
        }

        return true;
    }

    private String triggerWifiStrengthScannerInstrumentation() throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        getDevice().executeShellCommand(WIFI_SCAN_INSTRUMENTATION, receiver,
                MAX_RESPONSE_TIMEOUT, TimeUnit.MILLISECONDS, 1);

        return receiver.getOutput();
    }
}
