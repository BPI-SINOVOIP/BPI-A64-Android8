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

import com.android.loganalysis.item.BugreportItem;
import com.android.loganalysis.item.DumpsysBatteryStatsItem;
import com.android.loganalysis.parser.BugreportParser;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

@OptionClass(alias = "battery-drain-test-runner")
public class BatteryDrainTestRunner extends PowerTestRunnerBase {

    private static final int BOOT_STABILIZE_TIME = 60 * 1000; // 1 min
    private static final int BUREPORT_MISSING_SECTIONS_WAIT_TIME = 20 * 1000; // 20 secs
    private static final String BATTERY_DRAIN_FILE = "batterydrain.log";
    private static final String BATTERY_DRAIN_BUGREPORT_FILE = "batteryDrain_bugreport.txt";
    private static final long CMD_TIME_OUT = 30 * 1000;
    private static final float TEST_TOLERENCE = 60 * 60 * 1000; // 1 hour
    private static final long ONE_HOUR_IN_MS = 60 * 60 * 1000;
    private static final int BUGREPORT_VALIDATION_ATTEMPS_LIMIT = 5;

    @Option(name = "drain-duration",
            description = "The duration to drain battery in seconds.", mandatory = true)
    protected long mDrainDuration = 0;

    @Option(name = "report-projected-battery-life",
            description = "Report projected battery life based on drain rate below 95% battery level", mandatory = true)
    private boolean mReportProjectedBatteryLife = false;

    private Long mTimeOnBattery = null;
    private Long mProjectedBatteryLife = null;
    private Integer mBatteryMinPercentage = null;

    @Override
    protected void setUp() throws DeviceNotAvailableException {
        super.setUp();

        if (mUSBHubType == null) {
            throw new IllegalArgumentException("No usb-hub type was provided (datamation/ncd)");
        }

        cleanTestFile(String.format("%s/%s", mSDcardPath, BATTERY_DRAIN_FILE));
    }

    @Override
    void measurePower() {
        CLog.d("Draining battery for %d seconds.", mDrainDuration);
        RunUtil.getDefault().sleep(mDrainDuration * 1000);
    }

    private boolean testValidation() {
        Long testDuration = getTestDurationInMs();

        if (testDuration == null) {
            CLog.e("Couldn't validate test duration.");
            return false;
        }

        Long timeOnBattery = getTimeOnBattery();

        if (timeOnBattery == null) {
            CLog.e("Couldn't get time on battery.");
            return false;
        }

        CLog.v(String.format("Estimated test duration %.2f hours",
                (float) testDuration / ONE_HOUR_IN_MS));

        CLog.v(String.format("Time on battery %.2f hours",
                (float) timeOnBattery / ONE_HOUR_IN_MS));

        // Test duration should be within TEST_TOLERANCE (45 m) difference.
        if ((Math.abs(timeOnBattery - testDuration)) > TEST_TOLERENCE) {
            CLog.e("Time on battery is much more longer than test duration.");
            return false;
        }

        // The last validations only applies to tests in which the battery should be fully drained.
        if (mReportProjectedBatteryLife) {
            return true;
        }

        Integer minPercentage = getBatteryMinPercentage();
        if (minPercentage == null) {
            CLog.e("Can't verify if the battery was fully drained.");
            return false;
        }

        CLog.v(String.format("Battery lowest level reached %d", minPercentage));
        if (minPercentage > 0) {
            CLog.e("Battery was not fully drained.");
            return false;
        }

        return true;
    }

    private Long getTestDurationInMs() {
        Long testDuration = null;
        try {
            File outputFile = FileUtil.createTempFile(BATTERY_DRAIN_FILE, "");
            getDevice()
                    .pullFile(String.format("%s/%s", mSDcardPath, BATTERY_DRAIN_FILE), outputFile);
            Scanner in = new Scanner(new FileReader(outputFile));
            while (in.hasNextLine()) {
                try {
                    testDuration = Long.valueOf(in.nextLine());
                } catch (NumberFormatException e) {
                    CLog.e(e.toString());
                    return null;
                }
            }
        } catch (IOException e) {
            CLog.e(e.toString());
            return null;
        } catch (DeviceNotAvailableException e) {
            CLog.e(e.toString());
            return null;
        }

        return testDuration;
    }

    @Override
    public void reportResults() throws DeviceNotAvailableException {
        CLog.v("Waiting 1 minute for device to stabilize after booting up from usb reconnection.");
        RunUtil.getDefault().sleep(BOOT_STABILIZE_TIME);

        CLog.v("Computing metrics.");
        float hours = -1f;
        collectBatteryStats();

        Long ms = mReportProjectedBatteryLife ? getProjectedBatteryLife() : getTimeOnBattery();

        if (ms != null && testValidation()) {
            hours = (float) ms / 60 / 60 / 1000;
        }

        String schema = mSchemaRUPair.keySet().iterator().next();
        String finalRu = PostingHelper.appendSuffix(mSchemaRUPair.get(schema), mRuSuffix);
        String finalSchema = PostingHelper.appendSuffix(schema, mSchemaSuffix);
        PostingHelper.postResult(mListener, finalRu, finalSchema, hours);

        File hostFile = PowerAnalyzer.pullFile(getDevice(), mSDcardPath, BATTERY_DRAIN_FILE);
        PostingHelper.postFile(mListener, hostFile, LogDataType.TEXT, BATTERY_DRAIN_FILE);
    }

    private Long getTimeOnBattery() {
        return mTimeOnBattery;
    }

    private Long getProjectedBatteryLife() {
        return mProjectedBatteryLife;
    }

    private Integer getBatteryMinPercentage() {
        return mBatteryMinPercentage;
    }

    private void collectBatteryStats() {
        try {
            for (int i = 0; i < BUGREPORT_VALIDATION_ATTEMPS_LIMIT; i++) {
                CLog.e("Waiting for bugreport's missing sections and retrying.");
                RunUtil.getDefault().sleep(BUREPORT_MISSING_SECTIONS_WAIT_TIME);

                CLog.v("Collecting bugreport. Attempt: %d", i + 1);
                InputStreamSource source = getDevice().getBugreport();
                InputStreamReader reader = new InputStreamReader(source.createInputStream());
                BufferedReader bufferedReader = new BufferedReader(reader);
                BugreportItem bugreport = new BugreportParser().parse(bufferedReader);

                if (verifyBugReport(bugreport)) {
                    extractStats(bugreport);
                    keepBatteryDrainBugReport(source);
                    return;
                }
            }

            String message = String.format("Couldn't retrieve a valid bugreport after %s attempts.",
                    BUGREPORT_VALIDATION_ATTEMPS_LIMIT);
            CLog.e(message);
        } catch (Exception e) {
            CLog.e("Error while trying to get the batter stats item.");
        }
    }

    private void extractStats(BugreportItem bugreport) {
        DumpsysBatteryStatsItem batteryStats = bugreport.getDumpsys().getBatteryStats();
        mTimeOnBattery = batteryStats.getDetailedBatteryStatsItem().getTimeOnBattery();
        mBatteryMinPercentage = batteryStats.getBatteryDischargeStatsItem().getMinPercentage();
        mProjectedBatteryLife = batteryStats.getBatteryDischargeStatsItem()
                .getProjectedBatteryLife();
    }

    // returns true if success.
    private boolean verifyBugReport(BugreportItem bugreport) {
        if (bugreport.getDumpsys() == null) {
            CLog.e("Dumpsys section is missing.");
            return false;
        }

        if (bugreport.getDumpsys().getBatteryStats() == null) {
            CLog.e("Battery stats section is missing.");
            return false;
        }

        DumpsysBatteryStatsItem batteryStats = bugreport.getDumpsys().getBatteryStats();
        if (batteryStats.getDetailedBatteryStatsItem() == null) {
            CLog.e("Detailed battery stats section is missing.");
            return false;
        }

        // Only needed for projected battery life.
        if (mReportProjectedBatteryLife &&
                batteryStats.getBatteryDischargeStatsItem() == null) {
            CLog.e("Battery discharge stats section is missing.");
            return false;
        }

        return true;
    }

    private void keepBatteryDrainBugReport(InputStreamSource bugReportSource) {
        try {
            mListener.testLog(BATTERY_DRAIN_BUGREPORT_FILE, LogDataType.BUGREPORT, bugReportSource);
        } catch (Exception e) {
            CLog.e("Couldn't save bugrepport used for battery drain metrics.");
            CLog.e(e.getMessage());
        }
    }
}
