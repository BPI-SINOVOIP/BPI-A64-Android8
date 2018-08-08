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

package com.google.android.automotive.tests;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.IGlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import com.google.android.power.tests.PowerMonitor;

import org.junit.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * AutoMobilePowerTestRunner does the following
 * 1) Ensure the display device is available
 * 2) Sets up the display device
 * 3) Creates the bridge
 * 4) Starts the test on display device
 * 5) Starts monitoring power
 * 6) Uploads the average power consumption to database
 */
@OptionClass(alias = "automobile-power-test-runner")
public class AutoMobilePowerTestRunner implements IDeviceTest, IRemoteTest, IConfigurationReceiver {

    @Option(name = "Monsoon-SerialNo",
            description = "Unique monsoon serialno", mandatory = true)
    private String mMonsoonSerialno = null;

    @Option(name = "Monsoon-Voltage",
            description = "Set the monsoon voltage", mandatory = true)
    private float mMonsoonVoltage = 3.9f;

    @Option(name = "Monsoon-Samples",
            description = "The total number of the monsoon samples", mandatory = true)
    private long mMonsoonSamples = 1000;

    @Option(name = "RU-Name",
            description = "Report unit name", mandatory = true)
    private String mRUName = null;

    @Option(name = "Display-Device-SerialNo",
            description = "Display Device Serial No", mandatory = true)
    private String mDisplayDeviceSerialNo = null;

    @Option(name = "shell-script-path",
            description = "Local path to the shell script that has to run on device",
            mandatory = true)
    private String mShellScriptRemotePath = null;

    @Option(name = "Test-Name",
            description = "Test case name", mandatory = true)
    private String mTestName = null;

    @Option(name = "Timeout-Mins",
            description = "Timeout period in mins", mandatory = true)
    private int mTimeout = 30;

    @Option(name = "Bridge-Setup-Script", description = "Bridge Setup Script")
    private String mBridgeSetupScript = "usb-bridge.py";

    @Option(name = "clear-app-data",
            description = "Package name of app for which to clear data before running test. " +
            "May be repeated.")
    private List<String> mClearAppDataPkgsDUT = new ArrayList<String>();

    @Option(name = "clear-app-data-display-device",
            description = "Package name of app for which to clear data on display device " +
            "before running test. May be repeated.")
    private List<String> mClearAppDataPkgsDisplayDevice = new ArrayList<String>();

    @Option(name = "adb-path",
            description = "Path to adb binary")
    private String mAdbPath = "/usr/local/bin/adb";

    private IConfiguration mConfig = null;
    private ITestDevice mDUT = null;
    private ITestDevice mDisplayDevice = null;
    private IDeviceManager mDeviceManager = null;
    private IGlobalConfiguration mGlobalConfiguration = null;
    private DeviceSelectionOptions mDeviceSelectionOptions = null;
    private Map<String, String> mResults = null;
    private String mShellScriptLocalPath = null;
    private Process mBridgeProcess = null;

    private static final long WAIT_TIME_BEFORE_STARTING_POWER = 60 * 1000; // 60 secs
    private static final long BRIDGE_SETUP_TIME = 20 * 1000; // 20 secs
    private static final long SHORT_WAIT = 10 * 1000; // 10 secs
    private static final long REBOOT_TIMEOUT = 5 * 60 * 1000; // 5 minutes
    private static final String SCRIPT_LOCATION_LOCAL = "/data/local/tmp";
    private static final int MAX_BRIDGE_ATTEMPTS = 20; // max # of attempts to start bridge mode
    private static final int MAX_SCREEN_OFF_RETRY = 5;
    private static final int SCREEN_OFF_RETRY_DELAY_MS = 2 * 1000;

    @Override
    public void setConfiguration(IConfiguration configuration){
        mConfig = configuration;
    }

    private void clearAppData(ITestDevice device, String pkgName)
            throws DeviceNotAvailableException {
        String result = device.executeShellCommand(String.format("pm clear %s", pkgName));
        if (result.contains("Success")) {
            CLog.i("Cleared app data for pkg: %s", pkgName);
        } else {
            CLog.e("Failed to clear app data for pkg %s.", pkgName);
        }
    }

    private void clearAppDataAll() throws DeviceNotAvailableException {
        CLog.i("Clearing DUT app data before running tests...");
        for (String pkg : mClearAppDataPkgsDUT) {
            clearAppData(mDUT, pkg);
        }
        CLog.i("Clearing Display device app data before running tests...");
        for (String pkg : mClearAppDataPkgsDisplayDevice) {
            clearAppData(mDisplayDevice, pkg);
        }
    }

    private void turnScreenOff() throws DeviceNotAvailableException {
        String output = mDUT.executeShellCommand("dumpsys power");
        int retries = 1;
        // screen on semantics have changed in newest API platform, checking for both signatures
        // to detect screen on state
        while (output.contains("mScreenOn=true") || output.contains("mInteractive=true")) {
            // KEYCODE_POWER = 26
            mDUT.executeShellCommand("input keyevent 26");
            // due to framework initialization, device may not actually turn off screen
            // after boot, recheck screen status with linear backoff
            RunUtil.getDefault().sleep(SCREEN_OFF_RETRY_DELAY_MS * retries);
            output = mDUT.executeShellCommand("dumpsys power");
            retries++;
            if (retries > MAX_SCREEN_OFF_RETRY) {
                CLog.w(String.format("screen still on after %d retries", retries));
                break;
            }
        }
    }

    private void rebootDisplayDevice() throws DeviceNotAvailableException {
        CLog.i("Rebooting display device: %s", mDisplayDevice.getSerialNumber());
        mDisplayDevice.reboot();
        mDisplayDevice.waitForDeviceAvailable();
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mGlobalConfiguration = GlobalConfiguration.getInstance();
        mDeviceManager = GlobalConfiguration.getDeviceManagerInstance();
        waitForDisplayDevice();
        Assert.assertNotNull("Display device is not available", mDisplayDevice);
        setupDisplayDevice();
        clearAppDataAll();
        mDUT.reboot();
        rebootDisplayDevice();
        dumpBugreport(listener, "initial_bugreport");
        setupUSBBridge();
        turnScreenOff();
        startTest();
        RunUtil.getDefault().sleep(WAIT_TIME_BEFORE_STARTING_POWER);
        measurePower();
        dumpBugreport(listener, "final_bugreport");
        freeDisplayDevice();
        uploadResults(listener);
        disconnectUSBBridge();
    }

    private void dumpBugreport(ITestInvocationListener listener, String prefix) {
        try (InputStreamSource source = mDUT.getBugreport()) {
            CLog.i("Grabbing bugreport for '%s'", prefix);
            listener.testLog(prefix, LogDataType.BUGREPORT, source);
        }
    }

    /**
     * Force reboot a device b/c it's stuck in a bad state before we could even allocate it.
     * TODO: Need to track down why the display device is not getting freed...perhaps from
     * previous runs?
     */
    private void forceRebootDevice(String serial, long timeout) {
        CommandResult result = RunUtil.getDefault().runTimedCmd(timeout, mAdbPath, "-s", serial,
                "reboot");
        if (result.getStatus() != CommandStatus.SUCCESS) {
            Assert.fail("Unable to force reboot the display device " + serial);
        }
        boolean booted = false;
        long timeoutInNano = TimeUnit.NANOSECONDS.convert(timeout, TimeUnit.MILLISECONDS);
        long startTime = System.nanoTime();
        while (true) {
            result = RunUtil.getDefault().runTimedCmd(timeout, mAdbPath, "-s",
                    mDisplayDeviceSerialNo, "wait-for-device", "shell", "getprop",
                    "sys.boot_completed");
            if (result.getStatus() == CommandStatus.SUCCESS && result.getStdout().equals("1")) {
                return;
            }
            if (System.nanoTime() - startTime > timeoutInNano) {
                Assert.fail("Timed out trying to reset the display device " + serial);
            }
            RunUtil.getDefault().sleep(SHORT_WAIT);
        }
    }

    private void waitForDisplayDevice() {
        long startTime = System.nanoTime();
        long timeoutInNano = TimeUnit.NANOSECONDS.convert(mTimeout, TimeUnit.MINUTES);
        while (System.nanoTime() - startTime < timeoutInNano) {
            if (getCurrentDeviceState(mDisplayDeviceSerialNo).equals
                    (DeviceAllocationState.Available)) {
                mDisplayDevice = allocateDevice(mDisplayDeviceSerialNo);
                break;
            }
            CLog.i("Forcing reboot of display device " + mDisplayDeviceSerialNo);
            forceRebootDevice(mDisplayDeviceSerialNo, REBOOT_TIMEOUT);
            mDeviceManager.freeDevice(mDisplayDevice, FreeDeviceState.AVAILABLE);
        }
    }

    /**
     * Retrieve the current state of the device from DeviceManager
     */
    private DeviceAllocationState getCurrentDeviceState(String serialNo) {
        DeviceAllocationState state = DeviceAllocationState.Unavailable;
        // Retrieve all the devices from DeviceManager queue
        List<DeviceDescriptor> allDevices = mDeviceManager.listAllDevices();
        for (int i = 0; i < allDevices.size(); i++) {
            DeviceDescriptor deviceDescriptor = allDevices.get(i);
            // Check if the serial matches
            if (deviceDescriptor.getSerial().equals(serialNo)) {
                state = deviceDescriptor.getState();
                break;
            }
        }
        return state;
    }

    /**
     * Marks the device as "Allocated" and returns the ITestDevice object
    */
    private ITestDevice allocateDevice(String serialNo) {
        mDeviceSelectionOptions = new DeviceSelectionOptions();
        mDeviceSelectionOptions.addSerial(serialNo);
        CLog.i("Allocated Device %s", serialNo);
        return mDeviceManager.allocateDevice(mDeviceSelectionOptions);
    }

    private void freeDisplayDevice() {
        CLog.i("Freed display device: %s", mDisplayDevice.getSerialNumber());
        mDeviceManager.freeDevice(mDisplayDevice, FreeDeviceState.AVAILABLE);
    }

    private void setupDisplayDevice() throws DeviceNotAvailableException {
        // TODO: Install the APK from NFS
        File scriptFile = new File(mShellScriptRemotePath);
        String shellScriptName = scriptFile.getName();
        File scriptDestination = new File(SCRIPT_LOCATION_LOCAL, shellScriptName);
        mShellScriptLocalPath = scriptDestination.getPath();
        CLog.i("Pushing file '%s' to '%s' on device %s.", mShellScriptRemotePath,
                mShellScriptLocalPath, mDisplayDeviceSerialNo);

        if (!mDisplayDevice.pushFile(scriptFile, mShellScriptLocalPath)) {
            Assert.fail(String.format("Failed to push '%s'", mShellScriptRemotePath));
        }
        mDisplayDevice.executeShellCommand(String.format("chmod 777 '%s'", mShellScriptLocalPath));
    }

    private void setupUSBBridge() {
        // make sure we're not connected to the bridge
        disconnectUSBBridge();
        boolean bridgeSetup = false;
        try {
            mBridgeProcess = RunUtil.getDefault().runCmdInBackground(mBridgeSetupScript);
            RunUtil.getDefault().sleep(BRIDGE_SETUP_TIME);
            for (int attempt = 0; attempt < MAX_BRIDGE_ATTEMPTS; attempt++) {
                if (isBridgeConnected()) {
                    bridgeSetup = true;
                    break;
                }
                RunUtil.getDefault().sleep(SHORT_WAIT);
            }
            Assert.assertTrue("Unable to setup USB Bridge to run test!", bridgeSetup);
        } catch (Exception e) {
            freeDisplayDevice();
            Assert.fail(e.getMessage());
        }
    }

    private boolean isBridgeConnected() {
        try {
            String commandResponse = mDUT.executeShellCommand("ps");
            if (commandResponse.contains("com.google.android.projection")) {
                return true;
            }
        } catch (DeviceNotAvailableException e) {
            freeDisplayDevice();
            Assert.fail("Exception while querying device is in bridge mode");
        }
        return false;
    }

    private void disconnectUSBBridge() {
        if (mBridgeProcess != null) {
            mBridgeProcess.destroy();
            mBridgeProcess = null;
            CLog.i("Bridge process destroyed.");
        } else {
            CLog.i("No bridge process to destroy.");
        }
        RunUtil.getDefault().sleep(SHORT_WAIT);
    }

    private void startTest() {
        RunUtil.getDefault().sleep(SHORT_WAIT);
        try {
            String commandResponse = mDisplayDevice.executeShellCommand(mShellScriptLocalPath);
            // TODO: Validate command response
        } catch (DeviceNotAvailableException e) {
            freeDisplayDevice();
            Assert.fail("Unable to start test on display device");
        }
    }

    private void measurePower() {
        mResults = new HashMap<String, String>();
        float averagePowerInMw = getPowerConsumption(mTestName);
        CLog.d("The average power for " + mTestName + " is:"+averagePowerInMw);
        //Update the results to hashtable
        mResults.put(mTestName, String.format("%.0f", Float.valueOf(averagePowerInMw)));
    }

    /**
     * Measure the power consumption and return the mW equivalent
    */
    private float getPowerConsumption(String testcase){
        float averageCurrent= PowerMonitor.getCurrentConsumption(
                testcase, mMonsoonSerialno, mMonsoonVoltage, mMonsoonSamples, false);
        return averageCurrent * mMonsoonVoltage;
    }

    /**
     * Upload the results to release dashboard
    */
    private void uploadResults(ITestInvocationListener listener) {
        listener.testRunStarted(mRUName, 0);
        listener.testRunEnded(0, mResults);
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDUT = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDUT;
    }
}

