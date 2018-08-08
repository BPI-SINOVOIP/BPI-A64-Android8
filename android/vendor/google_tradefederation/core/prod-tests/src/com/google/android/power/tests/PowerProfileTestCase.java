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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PowerProfileTestCase {

    private ITestDevice mTestDevice = null;
    private String mMonsoonSerialno= null;
    private float mMonsoonVoltage = 3.9f;
    private long mMonsoonSamples = 1000;
    private static String mShellScriptLocation = "/data/local";
    private static String mBusyboxLocation = "/data/local/busybox-android";

    private static final String CPU_SPEEDS_CMD =
            "cat /sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state";

    private static final String CPU_ACTIVE_CMD =
            " nohup " + mShellScriptLocation + "/cpu_active.sh >/dev/null 2>&1";

    private static final String SCREEN_OFF_CMD = "input keyevent 26";

    private static final String CUR_CPU_FREQ_CMD =
            "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";

    private static final String SCREEN_CMD =
            " nohup " + mShellScriptLocation + "/screen.sh %d >/dev/null 2>&1";

    private static final String GPS_ON_APK_CMD =
            "am start -n com.android.gpstest/.GPSTestActivity";

    private static final String WIFI_ACTIVE_CMD =
            " nohup " + mShellScriptLocation + "/wifi_active.sh >/dev/null 2>&1";

    private static final Pattern CPU_SPEED_PATTERN = Pattern.compile("\\s*(\\d+)\\s*(\\d+)");

    private static final long WAIT_TIME_BEFORE_DISCONNECT_USB = 5 * 1000;
    private static final long WAIT_TIME_FOR_TEST_STABLE = 2 * 60 * 1000;

    private Map<String, String> runMetrics = null;

    public PowerProfileTestCase(ITestDevice testDevice, String monsoonSerialno,
            float monsoonVoltage, long monsoonSamples){
        mTestDevice = testDevice;
        mMonsoonSerialno = monsoonSerialno;
        mMonsoonVoltage = monsoonVoltage;
        mMonsoonSamples = monsoonSamples;
    }

    /**
     * Query the list of available CPU frequencies and run cpu_active.sh for
     * each one of them. Results (mW) for every frequency is stored in the
     * results hash table
     */

    public Map<String,String> runCPUActiveTestCase() throws DeviceNotAvailableException {
        //Retrieve the list of the cpu frequencies
        String CPUSpeedsCMDOutput = mTestDevice.executeShellCommand(CPU_SPEEDS_CMD);
        String CPUSpeedsCMDOutputLines[] = CPUSpeedsCMDOutput.split("\n");
        float averagePowerInMw = 0f;
        String currentFrequency;
        for(String CPUSpeedCMDOutputLine: CPUSpeedsCMDOutputLines) {
            Matcher m = CPU_SPEED_PATTERN.matcher(CPUSpeedCMDOutputLine.trim());
            if(m.matches()) {
                String CPUSpeed = m.group(1);
                String testcase = "cpu.active_"+CPUSpeed;
                //Turn off the screen
                mTestDevice.executeShellCommand(SCREEN_OFF_CMD);
                //Start the shell script
                startTest(CPU_ACTIVE_CMD + " " + CPUSpeed);
                //Measure power consumption
                measurePower(testcase);

                currentFrequency = mTestDevice.executeShellCommand(CUR_CPU_FREQ_CMD);
                Assert.assertEquals(currentFrequency.trim(),CPUSpeed);

                mTestDevice.reboot();
            }
        }
        return runMetrics;
    }

    /**
     * Set the lcd brightness to zero.
     * Open the screen APK and measure power
     */

    public Map<String,String> runScreenOnTestCase() throws DeviceNotAvailableException {
        startTest(String.format(SCREEN_CMD, 0));
        measurePower("screen.on");
        return runMetrics;
    }

    /**
     * Set the lcd brightness to maximum.
     * Open the screen APK and measure power
     */

    public Map<String,String> runScreenFullTestCase() throws DeviceNotAvailableException {
        startTest(String.format(SCREEN_CMD, 255));
        measurePower("screen.full");
        return runMetrics;
    }

    /**
     * Open GPS test APK, turn off screen and measure power
     */

    public Map<String,String> runGPSOnTestCase() throws DeviceNotAvailableException {
        mTestDevice.executeShellCommand(GPS_ON_APK_CMD);
        startTest(SCREEN_OFF_CMD);
        measurePower("gps.on");
        return runMetrics;
    }

    /**
     * Turn off the screen and execute the shell script which will download continously
     */

    public Map<String,String> runWifiActiveTestCase() throws DeviceNotAvailableException {
        mTestDevice.executeShellCommand(SCREEN_OFF_CMD);
        startTest(WIFI_ACTIVE_CMD);
        measurePower("wifi.active");
        return runMetrics;
    }

    /**
     * Measure power and add results
    */
    private void measurePower(String testcase) {
        runMetrics = new HashMap<String, String>();
        float averagePowerInMw = getPowerConsumption(testcase);
        CLog.d("The average power for "+testcase+" is:"+averagePowerInMw);
        //Update the results to hashtable
        runMetrics.put(testcase, String.format("%.0f", Float.valueOf(averagePowerInMw)));
    }


    /**
     * Measure the power consumption and return the mW equivalent
    */
    private float getPowerConsumption(String testcase){
        float averageCurrent= PowerMonitor.getCurrentConsumption(
                testcase, mMonsoonSerialno, mMonsoonVoltage, mMonsoonSamples);
        return averageCurrent * mMonsoonVoltage;
    }

    /**
     * Start the command on the device and wait for it to get started.
     * Disconnect USB and wait for the test to stabilize
    */
    private void startTest(final String command) {
        new Thread() {
            @Override
            public void run() {
                CLog.i("Run command " + command);
                try {
                    mTestDevice.executeShellCommand(command);
                } catch (DeviceNotAvailableException e) {
                    Assert.fail("Device is not available");
                }
            }
        }.start();
        //Wait for the test to be launched
        RunUtil.getDefault().sleep(WAIT_TIME_BEFORE_DISCONNECT_USB);
    }


}
