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

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.Pair;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auxiliar class to look for a monsoon's serial number corresponding to a device's serial number
 * from a file.
 */
public class LabSetupInfoExtractor {

    // File format expected
    // <device serial> <monsoon serial>
    //
    // Trailing and leading whitespaces are ignored (use leading whitespaces to indent).
    // Anything after a pound sign is ignored (use it for comments).
    // Lines not matching '\S+\s+\S+' will be ignored (avoid adding not matching lines that aren't
    // commented).
    //
    // Example:
    // #This device is on host HAL-9000
    //   device:abcd12345 monsoon:1122  #this device is cool
    //   device:xyzz12345 tigertail:1123 monsoon:1124 #this device is cooler
    //   device:asdf12345 monsoon:1125 tigertail:1126
    private static final String COMMENT_DELIMITER = "#";
    private final InputStreamReader mReader;
    private static final Pattern VALIDITY_FORMAT_CONTAINS_DUT_REGEX =
            Pattern.compile("^(:?|.*\\s)device:\\S+.*");
    private static final Pattern VALIDITY_FORMAT_SPACE_SEPARATED_PATTERN =
            Pattern.compile("^(:?\\S+:\\S+)(:?\\s+\\S+:\\S+)*$");

    private Map<Pair<InfoToken, String>, String> mInfo = new HashMap<>();
    private Map<Pair<InfoToken, String>, List<String>> mSerialsGroupedByInfo = new HashMap<>();
    private List<String> mDevicesSerials = new ArrayList<>();

    private enum InfoToken {
        DATAMATION("datamation"),
        DEVICE("device"),
        HOST("host"),
        MONSOON("monsoon"),
        NCD("ncd"),
        TIGERTAIL("tigertail");


        private final Pattern mPattern;

        InfoToken(String label) {
            mPattern = Pattern.compile(String.format("%s:(\\S+)", label));
        }

        public Pattern getPattern() {
            return mPattern;
        }
    }

    /**
     * Creates a LabSetupInfoExtractor.
     *
     * @param inputStreamReader An InputStreamReader containing the map.
     */
    public LabSetupInfoExtractor(InputStreamReader inputStreamReader) {
        mReader = inputStreamReader;
        extractInfo();
    }

    public List<String> getDevicesByHost(String host) {
        Pair<InfoToken, String> key = new Pair<>(InfoToken.HOST, host);
        if (mSerialsGroupedByInfo.containsKey(key)) {
            return mSerialsGroupedByInfo.get(key);
        }

        return new ArrayList<>();
    }

    public String extractTigertailSerialNo(String serialNumber) {
        Pair<InfoToken, String> key = new Pair<>(InfoToken.TIGERTAIL, serialNumber);

        if (mInfo.containsKey(key)) {
            return mInfo.get(key);
        }

        return null;
    }

    public String extractMonsoonSerialNo(String serialNumber) {
        Pair<InfoToken, String> key = new Pair<>(InfoToken.MONSOON, serialNumber);

        if (mInfo.containsKey(key)) {
            return mInfo.get(key);
        }

        return null;
    }

    public String extractDatamationPort(String serialNumber) {
        Pair<InfoToken, String> key = new Pair<>(InfoToken.DATAMATION, serialNumber);

        if (mInfo.containsKey(key)) {
            return mInfo.get(key);
        }

        return null;
    }

    public String extractNcdPort(String serialNumber) {
        Pair<InfoToken, String> key = new Pair<>(InfoToken.NCD, serialNumber);

        if (mInfo.containsKey(key)) {
            return mInfo.get(key);
        }

        return null;
    }

    public boolean deviceSerialExists(String serialNumber) {
        return mDevicesSerials.contains(serialNumber);
    }

    private void extractInfo() {
        try {
            BufferedReader reader = new BufferedReader(mReader);
            String line;
            while ((line = reader.readLine()) != null) {
                int poundIndex = line.indexOf(COMMENT_DELIMITER);
                if (poundIndex >= 0) {
                    line = line.substring(0, poundIndex);
                }

                line = line.trim();
                Matcher m = VALIDITY_FORMAT_CONTAINS_DUT_REGEX.matcher(line);
                if (!m.find()) {
                    continue;
                }

                Matcher m2 = VALIDITY_FORMAT_SPACE_SEPARATED_PATTERN.matcher(line);
                if (!m2.find()) {
                    continue;
                }

                String serialNumber = extractInfo(line, InfoToken.DEVICE);
                mDevicesSerials.add(serialNumber);

                for (InfoToken infoToken : InfoToken.values()) {
                    String value = extractInfo(line, infoToken);
                    storeInfo(infoToken, serialNumber, value);
                }
            }
            reader.close();
        } catch (IOException e) {
            CLog.e(e);
        }
    }

    private void storeInfo(InfoToken infoToken, String serialNumber, String value) {
        if (value == null) {
            return;
        }

        Pair<InfoToken, String> key = new Pair<>(infoToken, serialNumber);
        if (!mInfo.containsKey(key)) {
            mInfo.put(key, value);
        }

        key = new Pair<>(infoToken, value);
        if (!mSerialsGroupedByInfo.containsKey(key)) {
            mSerialsGroupedByInfo.put(key, new ArrayList<>());
        }

        if (!mSerialsGroupedByInfo.get(key).contains(serialNumber)) {
            mSerialsGroupedByInfo.get(key).add(serialNumber);
        }
    }

    private String extractInfo(String peripheralsLine, InfoToken infoToken) {
        if (peripheralsLine == null) {
            return null;
        }

        String[] tokens = peripheralsLine.split("\\s+");
        for (String token : tokens) {
            Matcher m = infoToken.getPattern().matcher(token);
            if (m.matches()) {
                return m.group(1);
            }
        }

        return null;
    }
}
