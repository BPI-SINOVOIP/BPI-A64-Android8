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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.junit.Assert;

/** Starts the acts bluetooth setup script and signals it to teardown */
@OptionClass(alias = "acts-bluetooth-preparer")
public class BluetoothPreparer implements ITargetPreparer, ITargetCleaner {

    @Option(name = "relay-conf-file-path", description = "Relay configuration file path")
    protected String mRelayConfigFilePath;

    @Option(name = "socket-port", description = "Port in which Setup script listen for connection")
    protected int mSocketPort = 8800;

    @Option(
        name = "socket-timeout-secs",
        description = "How long the relay should be closed and running"
    )
    protected double mSocketTimeoutInSecs = 900.0;

    @Option(name = "bluetooth-mac-address", description = "MAC address of the bluetooth device")
    protected String mMACAddress;

    private static final String BLUETOOTH_STATE_CMD =
            "dumpsys bluetooth_manager | grep mCurrentDevice";
    private static final long SMALL_DELAY_MSECS = 10 * 1000; // 10 secs
    private static final long BLUETOOTH_CONNECTION_DELAY = 120 * 1000; // 120 secs
    private static final long REBOOT_TIME = 30 * 1000; // 30 secs
    private static final long SOCKET_TIMEOUT_ADDER = REBOOT_TIME + BLUETOOTH_CONNECTION_DELAY;
    private static final String BLUETOOTH_PAIR_CMD =
            "am instrument -w -r -e skip_home True -e mac-address %s -e class "
                    + "com.google.android.platform.powertests.BluetoothTests#testPairWithDevice "
                    + "com.google.android.platform.powertests/"
                    + "android.test.InstrumentationTestRunner";

    private File mConfigFile;
    private Path mTempDir;

    /** {@inheritDoc} */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, DeviceNotAvailableException, BuildError {
        try {
            mTempDir =
                    Paths.get(
                            System.getProperty("java.io.tmpdir"),
                            "lc_cache",
                            String.valueOf(new Random().nextInt()));
            // Fetch the acts script
            ActUtility.fetchActsScripts(
                    buildInfo.getBuildFlavor(),
                    buildInfo.getBuildId(),
                    mTempDir.toAbsolutePath().toString());
            // Create the config file
            mConfigFile = ActUtility.createConfigFile("bt_setup", getConfigFileContents());
            final String testcase = "SetupBTPairingTest";
            // Start the setup script to enable bt pairing mode
            ActUtility.startSetupScript(
                    mConfigFile.getAbsolutePath(), testcase, mTempDir.toAbsolutePath().toString());
            enablePairing(device);
            // Wait for it to get connected
            final long timeout = System.currentTimeMillis() + BLUETOOTH_CONNECTION_DELAY;
            while (System.currentTimeMillis() < timeout) {
                if (isBluetoothPaired(device)) {
                    break;
                }
                RunUtil.getDefault().sleep(1000);
            }
            Assert.assertTrue("Unable to pair with bluetooth device", isBluetoothPaired(device));
        } catch (IOException e) {
            CLog.e(e);
            throw new TargetSetupError(e.getMessage(), device.getDeviceDescriptor());
        } catch (JSONException e) {
            CLog.e(e);
            throw new TargetSetupError(e.getMessage(), device.getDeviceDescriptor());
        }
    }

    private String getConfigFileContents() throws JSONException {
        JSONObject config = new JSONObject();
        JSONArray testbedList = new JSONArray();
        JSONObject testbed = new JSONObject();
        testbed.put("_description", "Config to setup wifi AP. No device needed");
        testbed.put("name", ActUtility.getHostName());
        testbed.put("RelayDevice", mRelayConfigFilePath);
        testbedList.put(testbed);
        config.put("testbed", testbedList);
        config.put("logpath", System.getProperty("java.io.tmpdir"));
        config.put(
                "testpaths",
                ActUtility.getScriptDir(mTempDir.toAbsolutePath().toString()).toAbsolutePath());
        config.put("socket_port", mSocketPort);
        config.put("socket_timeout_secs", mSocketTimeoutInSecs + SOCKET_TIMEOUT_ADDER);
        CLog.d(config.toString());
        return config.toString();
    }

    private void enablePairing(ITestDevice device) throws DeviceNotAvailableException {
        CLog.d("Going to bond with bluetooth device with the command");
        String response =
                device.executeShellCommand(String.format(BLUETOOTH_PAIR_CMD, mMACAddress));
        CLog.d(String.format("Response from bluetooth pair command:%s", response));
        Assert.assertFalse(
                "Unable to bond with the bt device", response.contains("AssertionFailedError"));
    }

    private boolean isBluetoothPaired(ITestDevice device) throws DeviceNotAvailableException {
        String output = device.executeShellCommand(BLUETOOTH_STATE_CMD);
        CLog.d(String.format("Output from dumpsys command:%s", output));
        if (output.contains("null") || output.length() <= 0) {
            return false;
        }
        return true;
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        try {
            Socket socket = new Socket("localhost", mSocketPort);
            socket.close();
            // Small sleep to ensure the script has fully terminated
            RunUtil.getDefault().sleep(SMALL_DELAY_MSECS);
            Assert.assertFalse(
                    "Unable to terminate script",
                    ActUtility.isSetupScriptRunning(mConfigFile.getAbsolutePath()));
        } catch (UnknownHostException exception) {
            CLog.e(exception);
        } catch (IOException exception) {
            CLog.e(exception);
        }
    }
}
