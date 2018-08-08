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

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IGlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This runner is used for spawning the same test on multiple devices. It does the following a)
 * Parses the input paramters b) For every device, it marks them "allocated" and executes the test
 * command
 */
public class SideBySideTestRunner implements IRemoteTest {

    @Option(name = "command-file",
            description = "Command file which contains the test to run", mandatory = true)
    private String mCommandFile = null;

    @Option(name = "timeout-mins",
            description = "Timeout period in mins", mandatory = true)
    private int mTimeout = 60;

    @Option(name = "side-by-side-test-tag",
            description = "Test tag", mandatory = true)
    private String mTestTag = null;

    @Option(name = "clockwork",
            description = "Clockwork side by side test. Required both companion device" +
                          " and the wear devcie on the same build")
    private boolean mClockwork = false;

    @Option(name = "side-by-side-test-branch",
            description = "Branch")
    private String mBranch = null;

    @Option(name = "side-by-side-test-build-flavor",
            description = "Build flavor")
    private String mBuildFlavor = null;

    @Option(name = "side-by-side-min-build",
            description = "Min build id for running the side by side test")
    private int mMinBuild = 0;

    @Option(name = "side-by-side-baseline-build-id",
            description = "Baseline build id for reporting to dashboard")
    private int mBaselineBuildId = 0;

    @Option(name = "lcp-host",
            description = "LCP host to pull the latest whitelisted build")
    private String mLCPHost = "lcproxy.googleplex.com";

    private ITestDevice mTestDevice;
    private IDeviceManager mDeviceManager;
    private IGlobalConfiguration mGlobalConfiguration;
    private ICommandScheduler mCommandScheduler;
    private DeviceSelectionOptions mDeviceSelectionOptions;

    private InvocationListener mInvocationListener;

    private List<String> mDeviceList;
    private String mBuildId;
    private BuildUtility mBuildUtility;

    private Map<String, String> mDeviceCommandMap;
    private Map<String, DeviceAllocationState> mDeviceStatusMap;
    private Map<String, ITestDevice> mDeviceSerialObjectMap;

    private static final long DEVICE_TIMEOUT = 10 * 1000; // 10 secs
    private static final long SLEEP_BEFORE_RETRY = 60 * 1000; // 60 secs

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // Initialize all the objects
        mGlobalConfiguration = GlobalConfiguration.getInstance();
        mDeviceManager = GlobalConfiguration.getDeviceManagerInstance();
        mInvocationListener = new InvocationListener();
        mCommandScheduler = mGlobalConfiguration.getCommandScheduler();
        mBuildUtility = new BuildUtility();
        // Populate device command map
        populateDeviceCommandMap();
        // Check if there is any build to run, if not, return.
        if (mBuildId == null){
            CLog.i("No build to test");
            return;
        }
        waitForDevices();
       // Spawn the test on all devices
       runTest();
    }

    /**
     * Iterates through the list of devices and spawns the test
    */
    private void runTest() {
        try {
            String deviceSerialNo;
            for (Entry<String, ITestDevice> pair : mDeviceSerialObjectMap.entrySet()) {
                deviceSerialNo = pair.getKey();
                mTestDevice = pair.getValue();
                // Start the test on the device
                startTest(mTestDevice, getCommands(deviceSerialNo));
            }
        } catch (Exception e) {
            freeAllDevices();
            Assert.fail(e.getMessage());
        }
    }

    /**
     * Constructs the command args by tokenizing the command file by space
    */
    private String[] getCommands(String serialNo) {
        // Retrieve the command for that device
        String command = mDeviceCommandMap.get(serialNo);
        StringBuilder commandBuilder = new StringBuilder(command);
        if (!isBaseLineDevice(command)) {
            commandBuilder.append(" --build-id ");
        } else {
            commandBuilder.append(" --rdb-side-by-side:build-id ");
        }
        if (mBaselineBuildId > 0) {
            commandBuilder.append(mBaselineBuildId);
        } else {
            commandBuilder.append(mBuildId);
        }

        return commandBuilder.toString().split("\\s+");
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

    /**
     * Spawn the test on the allocated device
     */
    private void startTest(ITestDevice testDevice, String commandsList[]) {
        try {
            CLog.i("Executing %s command", Arrays.toString(commandsList));
            mCommandScheduler.execCommand(mInvocationListener, testDevice, commandsList);
        } catch (ConfigurationException e) {
            freeAllDevices();
            Assert.fail(e.getMessage());
        }
    }

    /**
     * Parse the command file and populate the device command mapping table
     */
    private void populateDeviceCommandMap() {
        try {
            // Initialize the mapping tables
            mDeviceCommandMap = new HashMap<String, String>();
            mDeviceList = new ArrayList<String>();
            // Read the file
            BufferedReader bufferedReader = new BufferedReader(new FileReader(mCommandFile));
            String line, serialNo;
            while ((line = bufferedReader.readLine()) != null) {
                if (line != null && !line.isEmpty()) {
                    serialNo = getSerialNumber(line);
                    // Assert a valid serial number is retrieved
                    Assert.assertNotNull(serialNo);
                    // Populate the mapping tables
                    mDeviceCommandMap.put(serialNo, line);
                    mDeviceList.add(serialNo);
                    // Check if its not a baseline build or clockwork
                    if ((!isBaseLineDevice(line) || mClockwork) && (mBuildId == null)) {
                        // Use this build id for both baseline and regular run
                        mBuildId = getBuildId(line);
                    }
                }
            }
        } catch (IOException e) {
            freeAllDevices();
            Assert.fail(e.getMessage());
        }
    }

    /**
     * Retrieve the serial number from the command
     */
    private String getSerialNumber(String command) {
        return getParameterValue(command, "serial");
    }

    /**
     * If the build id parameter is present in the command file,
     * then extract from the command file. Otherwise, query LCP
     * to retrieve the build
     */
    private String getBuildId(String command) {
        String buildId = null;
        try {
            buildId = getParameterValue(command, "build-id");

            if (buildId == null) {
                //Query the build from build server
                buildId = mBuildUtility.getTestBuild(
                        mBranch, mBuildFlavor, mTestTag, mLCPHost);
                if (buildId == null){
                    CLog.i("Query return null, no new build to test");
                    //No build to test
                    return null;
                }
            }
            if (Integer.parseInt(buildId) < mMinBuild){
                CLog.i("New build is smaller than the min build, no build to test");
                //No new build to test
                return null;
            }
        } catch (BuildRetrievalError e) {
            Assert.fail(e.getMessage());
        }
        CLog.i("Retrieved the build id %s", buildId);
        return buildId;
    }

    /**
     * Parse the command and retrieve the value for a given param
     */
    private String getParameterValue(String command, String paramName) {
        String value = null;
        Pattern PARAM_PATTERN = Pattern.compile("(.*)--"+paramName+"\\s+(\\S*)\\s+(.*)");
        Matcher patternMatcher = PARAM_PATTERN.matcher(command);
        if (patternMatcher.matches()) {
            value = patternMatcher.group(2);
            value = value.trim();
        }
        return value;
    }

    /**
     * Returns true if the baseline parameter is set
     */
    private boolean isBaseLineDevice(String command) {
        if (command.contains("baseline")) {
            return true;
        }
        return false;
    }

    /**
     * Iterates through the list of devices and checks if its available.
     * If it's available, mark them allocated and add it to the map.
     * If any device does not become available within the timeout, invocation
     * is exited
     */
    private void waitForDevices() {
        // Initialize the mapping tables
        mDeviceStatusMap = new HashMap<String, DeviceAllocationState>();
        mDeviceSerialObjectMap = new HashMap<String, ITestDevice>();

        int attempts = 0;
        String serialNo;
        // Iterates through the list of devices
        while (mDeviceStatusMap.size() != mDeviceList.size()) {
            for (int i = 0; i < mDeviceList.size(); i++) {
                serialNo = mDeviceList.get(i);
                // Check if the device is already in the allocated table
                if (!mDeviceStatusMap.containsKey(serialNo)) {
                    // Check if the new device is Available
                    if (getCurrentDeviceState(serialNo).equals(
                            DeviceAllocationState.Available)) {
                        // Mark the device as allocated
                        ITestDevice deviceObject = allocateDevice(serialNo);
                        Assert.assertNotNull(deviceObject);
                        // Add the new device to the table
                        mDeviceStatusMap.put(serialNo, DeviceAllocationState.Allocated);
                        mDeviceSerialObjectMap.put(serialNo, deviceObject);
                    }
                }
            }
            //Exit the loop if retry attemps have reached the max limit
            if (attempts >= mTimeout) {
                break;
            }
            attempts++;
            CLog.i("Sleeping for " + SLEEP_BEFORE_RETRY + " ms");
            RunUtil.getDefault().sleep(SLEEP_BEFORE_RETRY);
        }
        if (mDeviceList.size() != mDeviceStatusMap.size()) {
            freeAllDevices();
            Assert.fail("All the devices are not available. Aborting the invocation");
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
     * Free the allocated device
     */
    private void freeDevice(ITestDevice device) {
        CLog.i("Freed device: %s", device.getSerialNumber());
        mDeviceManager.freeDevice(device, FreeDeviceState.AVAILABLE);
    }

    /**
     * Iterate through all the test devices and free them
     */
    private void freeAllDevices() {
        for (ITestDevice device : mDeviceSerialObjectMap.values()) {
            freeDevice(device);
        }
    }

    /**
     * Handler to free up the device once the invocation completes
     */
    class InvocationListener implements IScheduledInvocationListener, ITestInvocationListener {

        @Override
        public void invocationComplete(IInvocationContext metadata,
                Map<ITestDevice, FreeDeviceState> devicesStates) {
            for (ITestDevice device : metadata.getDevices()) {
                freeDevice(device);
            }
            CLog.i("Invocaton complete");
        }
    }

}
