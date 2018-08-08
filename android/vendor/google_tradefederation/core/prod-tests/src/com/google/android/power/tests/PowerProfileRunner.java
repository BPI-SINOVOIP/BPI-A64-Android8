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

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;

import java.util.Map;

public class PowerProfileRunner implements IDeviceTest, IRemoteTest, IConfigurationReceiver {
    private ITestDevice mTestDevice = null;

    private IConfiguration mConfig = null;

    @Option(name = "monsoon_serialno",
            description = "Unique monsoon serialno", mandatory = true)
    private String mMonsoonSerialno = null;

    @Option(name = "monsoon_voltage",
            description = "Monsoon Vout voltage", mandatory = true)
    private float mMonsoonVoltage = 4.2f;

    @Option(name = "monsoon_samples",
            description = "The total number of the monsoon samples", mandatory = true)
    private long mMonsoonSamples = 1000;

    @Option(name = "testcase",
            description = "Specific test case name", mandatory = true)
    private String mTestCaseName = null;

    @Option(name = "ru_name",
            description = "Report unit name", mandatory = true)
    private String mTestKey = "PowerProfile";

    private static final long WAIT_TIME_FOR_DEVICE_STABLE = 5 * 60 * 1000;

    @Override
    public void setConfiguration(IConfiguration configuration){
        mConfig = configuration;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        //Wait for device to stabilize
        RunUtil.getDefault().sleep(WAIT_TIME_FOR_DEVICE_STABLE);
        //Turn off the power wakelock explicitly
        Map<String, String> results = null;
        PowerProfileTestCase powerProfileTestCase = new PowerProfileTestCase(
                mTestDevice, mMonsoonSerialno, mMonsoonVoltage, mMonsoonSamples);
        if (mTestCaseName.equals("cpu.active")) {
            results = powerProfileTestCase.runCPUActiveTestCase();
        } else if (mTestCaseName.equals("screen.on")){
            results = powerProfileTestCase.runScreenOnTestCase();
        } else if (mTestCaseName.equals("screen.full")){
            results = powerProfileTestCase.runScreenFullTestCase();
        } else if (mTestCaseName.equals("gps.on")){
            results = powerProfileTestCase.runGPSOnTestCase();
        } else if (mTestCaseName.equals("wifi.active")) {
            results = powerProfileTestCase.runWifiActiveTestCase();
        }
        uploadResults(listener, results);
    }


    /**
     * Upload the results to release dashboard
    */
    private void uploadResults(ITestInvocationListener listener, Map<String,String> results) {
        listener.testRunStarted(mTestKey, 0);
        listener.testRunEnded(0, results);
    }


    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }
}
