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
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Random;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;

/** Starts the acts wifi setup script and signals it to teardown */
@OptionClass(alias = "whirlwind-wifi-preparer")
public class WhirlWindWifiPreparer implements ITargetPreparer, ITargetCleaner {

    @Option(name = "whirlwind-AP-IP", description = "IPs of whirlwind AP")
    private List<String> mWhirlwindIPList = new ArrayList<String>();

    @Option(name = "network-type", description = "2G or 5G")
    private String mNetworkType = "5G";

    @Option(name = "network-details", description = "SSID and Passphrase of networks")
    private Map<String, String> mNetworkDetails = new HashMap<String, String>();

    @Option(
        name = "socket-port",
        description = "Port in which SetupNetwork script listen for connection"
    )
    protected int mSocketPort = 8800;

    @Option(
        name = "socket-timeout-secs",
        description = "How long the wifi AP should be up and running"
    )
    protected double mSocketTimeoutInSecs = 900.0;

    @Option(name = "wifi-security", description = "Security protocol for wifi AP")
    protected String mWifiSecurity = "none";

    @Option(
        name = "setup-method",
        description =
                "Function to be invoked in SetupWifiNetworkTest script. Possible values "
                        + "are test_set_up_single_ap, test_set_up_open_ap. The default value"
                        + " test_set_up_single_ap will setup a secured network. test_set_up_open_ap"
                        + " will setup a open network"
    )
    private String mSetupMethod = "test_set_up_single_ap";

    private static final String TEST_CASE = "SetupWifiNetworkTest";
    private static final String IDENTITY_FILE = "/home/android-test/.ssh/testing_rsa";
    private static final long SCRIPT_START_TIMEOUT_MSECS = 30 * 1000;
    private static final long SMALL_DELAY_MSECS = 3 * 1000;

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
            ActUtility.fetchActsScripts(
                    buildInfo.getBuildFlavor(),
                    buildInfo.getBuildId(),
                    mTempDir.toAbsolutePath().toString());
            mConfigFile = ActUtility.createConfigFile("wifi_setup", getConfigFileContents());
            final String testcase = String.format("%s:%s", TEST_CASE, mSetupMethod);
            ActUtility.startSetupScript(
                    mConfigFile.getAbsolutePath(), testcase, mTempDir.toAbsolutePath().toString());
            connectToNetwork(device);
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

        JSONArray accessPoint = new JSONArray();
        for (String IP : mWhirlwindIPList) {
            JSONObject sshConfig = new JSONObject();
            JSONObject sshValue = new JSONObject();
            sshValue.put("user", "root");
            sshValue.put("host", IP);
            sshValue.put("identity_file", IDENTITY_FILE);
            sshConfig.put("ssh_config", sshValue);
            accessPoint.put(sshConfig);
        }

        testbed.put("AccessPoint", accessPoint);
        testbedList.put(testbed);
        config.put("testbed", testbedList);
        config.put("security", mWifiSecurity);
        config.put("logpath", System.getProperty("java.io.tmpdir"));
        config.put(
                "testpaths",
                ActUtility.getScriptDir(mTempDir.toAbsolutePath().toString()).toAbsolutePath());
        config.put("ssid", mNetworkDetails.get("ssid"));
        if (!mWifiSecurity.equals("none")) {
            config.put("passphrase", mNetworkDetails.get("passphrase"));
        }
        config.put("network_type", mNetworkType);
        config.put("socket_port", mSocketPort);
        config.put("socket_timeout_secs", mSocketTimeoutInSecs);
        CLog.d(config.toString());
        return config.toString();
    }

    private void connectToNetwork(ITestDevice device) throws DeviceNotAvailableException {
        boolean isConnected =
                device.connectToWifiNetworkIfNeeded(
                        mNetworkDetails.get("ssid"), mNetworkDetails.get("passphrase"));
        Assert.assertTrue("Unable to connect to wifi", isConnected);
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        // Connecting socket will terminate the script
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
