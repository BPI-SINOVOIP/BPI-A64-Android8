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

import com.google.android.power.collectors.PowerTestLog;
import com.google.android.utils.MonsoonController;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.List;
import org.junit.Assert;

/**
 * Power test runner for using Monsoon
 * 1) Run setup to check monsoon status and run extra pre-condition setups on device
 * 2) Start instrumentation test on device
 * 3) Start the monsoon power data collection
 * 4) Run data analysis
 * 5) Test clean up
 */
@OptionClass(alias = "monsoon-test-runner")
public class MonsoonTestRunner extends PowerTestRunnerBase {

    // TODO(b/67715975): Rename this option to monsoon-duration.
    @Option(name = "monsoon_duration", description = "Duration of monsoon measurement in seconds")
    private Long mMonsoonDuration;

    @Option(
        name = "monsoon_frequency",
        description = "Monsoon sampling frequency in Hz",
        mandatory = true
    )
    private int mMonsoonFrequency = 10;

    @Option(name = "save_result_on_host", description = "Save test result on host machine")
    private boolean mSaveResultOnHost = false;

    @Option(name = "append_result_on_host", description = "Append test result on host machine")
    private boolean mAppendResultOnHost = true;

    // Monsoon options
    private static final long MINMONSOONFREQUENCY = 10;
    private static final String MONSOONRAWFILE = "MonsoonRawData";
    private String mFinalMonsoonSerial;
    private LabSetupInfoExtractor mLabSetupInfoExtractor;
    private MonsoonController mMonsoon;
    private File mRawFileOut;

    @Override
    protected void setUp() throws DeviceNotAvailableException {
        checkParameters();

        if (mMonsoonLibPath == null) {
            throw new IllegalArgumentException("No path to monsoon library was provided.");
        }

        if (!new File(mMonsoonLibPath).exists()) {
            String message =
                    String.format("File %s does not exist or is unreachable.", mMonsoonLibPath);
            throw new IllegalArgumentException(message);
        }

        if (mMonsoonDuration == null) {
            throw new IllegalArgumentException("No monsoon duration was provided");
        }

        if (mMonsoonVoltage == null) {
            throw new IllegalArgumentException("No monsoon voltage was provided");
        }

        mMonsoon = new MonsoonController(mMonsoonLibPath, getMonsoonSerial());
        checkMonsoonStatus();
        super.setUp();
    }

    @Override
    public void tearDown() throws DeviceNotAvailableException {
        super.tearDown();
        resetMonsoon();
    }

    /**
     * Execute monsoon command to collect current samples and write to a text file
     */
    @Override
    protected void measurePower() {
        mMonsoon.startMeasurement(mMonsoonFrequency);
        RunUtil.getDefault().sleep(mMonsoonDuration * 1000);
        mMonsoon.stopMeasurement();
        mRawFileOut = mMonsoon.getResultFile();
    }

    /**
     * This field will store the final value of the monsoon serial number. If the monsoon serial is
     * not defined by --monsoon_serialno it will be looked up in the monsoon serial map file.
     */
    private String getMonsoonSerial() {
        if (mFinalMonsoonSerial != null) {
            return mFinalMonsoonSerial;
        }

        if (mMonsoonSerialNo != null) {
            mFinalMonsoonSerial = mMonsoonSerialNo;
            CLog.v("Monsoon serial %s passed as argument.", mFinalMonsoonSerial);
            return mFinalMonsoonSerial;
        }

        if (mLabSetupMap != null) {
            mFinalMonsoonSerial = extractMonsoonSerialFromMapFile();
            CLog.v(
                    "Monsoon serial %s for device serial %s found in %s",
                    mFinalMonsoonSerial, getDevice().getSerialNumber(), mLabSetupMap);
            return mFinalMonsoonSerial;
        }

        throw new IllegalArgumentException(
                "At least one of --monsoon_serialno or --peripherals-map-file must be provided.");
    }

    private String extractMonsoonSerialFromMapFile() {
        String monsoonSerial =
                getLabSetupInfoExtractor().extractMonsoonSerialNo(getDevice().getSerialNumber());

        if (monsoonSerial == null) {
            String message =
                    String.format(
                            "Couldn't find a monsoon serial number associated with the android device's "
                                    + "serial number %s in file %s",
                            getDevice().getSerialNumber(), mLabSetupMap);
            throw new IllegalArgumentException(message);
        }

        return monsoonSerial;
    }

    private LabSetupInfoExtractor getLabSetupInfoExtractor() {
        if (mLabSetupInfoExtractor != null) {
            return mLabSetupInfoExtractor;
        }

        File file = new File(mLabSetupMap);

        InputStreamReader reader = null;

        try {
            reader = new InputStreamReader(new FileInputStream(mLabSetupMap));
        } catch (FileNotFoundException e) {
            String message = String.format("Couldn't read from file %s", file.getAbsolutePath());
            IllegalArgumentException iae = new IllegalArgumentException(message, e);
            CLog.e(iae);
            throw iae;
        }

        mLabSetupInfoExtractor = new LabSetupInfoExtractor(reader);
        return mLabSetupInfoExtractor;
    }

    private void checkMonsoonStatus() {
        CLog.v("Checking monsoon status");
        Assert.assertTrue(
                String.format("Monsoon executable is not found at: %s", mMonsoonLibPath),
                isFilePresentInHost(mMonsoonLibPath));

        String status = mMonsoon.dumpMonsoonStatus();
        CLog.i("Monsoon status: %s", status);
        boolean reachable = status.contains(mMonsoon.getMonsoonSerialNumber());
        Assert.assertTrue(
                String.format("Couldn't communicate with monsoon %s.", getMonsoonSerial()),
                reachable);
    }

    // TODO: Reset monsoon
    private void resetMonsoon() {
    }

    // Post Monsoon raw data file and run data analysis
    @Override
    protected void reportResults() throws DeviceNotAvailableException {
        PostingHelper.postFile(mListener, mRawFileOut, LogDataType.TEXT, MONSOONRAWFILE);



        File timeStampFile = extractAndPostPowerLog();
        MonsoonRawDataParser parser = null;
        try {
            parser = new MonsoonRawDataParser(mRawFileOut, extractTimestamps(timeStampFile));
        } catch (FileNotFoundException e) {
            CLog.e("Monsoon file %s was not found", mRawFileOut.getAbsoluteFile());
            CLog.e(e);
        }

        // Parse monsoon raw data and post test results
        MonsoonPowerStats powerStats =
                new MonsoonPowerStats(
                        mListener,
                        getDevice(),
                        parser.getPowerMeasurements(),
                        mSchemaRUPair,
                        mRuSuffix,
                        mSchemaSuffix,
                        mMonsoonVoltage,
                        mReportInmW,
                        mReportBatteryLife,
                        mBatterySize,
                        mDouWeightsMap,
                        mSaveResultOnHost,
                        mAppendResultOnHost,
                        mLowerLimits,
                        mUpperLimits);
        powerStats.run(mDecimalPlaces);

        // Generate graphs
        PowerGraph powerGraph =
                new PowerGraph(
                        mRawFileOut,
                        mMonsoonVoltage,
                        mMonsoonFrequency,
                        parser.getPowerMeasurements(),
                        getDevice());

        List<PowerTestLog> charts = powerGraph.getPowerCharts();
        for (PowerTestLog chart : charts) {
            PostingHelper.postFile(
                    mListener, chart.getLogFile(), chart.getDataType(), chart.getName());
        }

        // Parse Bugreport
        BugreportAnalyzer bugreportAnalyzer = new BugreportAnalyzer(mListener, getDevice());
        bugreportAnalyzer.run();
    }

    // Clean device test logs and monsoon files
    @Override
    protected void cleanTestFiles() throws DeviceNotAvailableException {
        super.cleanTestFiles();
        FileUtil.deleteFile(mRawFileOut);
    }

    private void checkParameters() {
        if (mMonsoonFrequency < MINMONSOONFREQUENCY) {
            Assert.fail("Monsoon frequency has to be greater than 10Hz");
        }
    }
}
