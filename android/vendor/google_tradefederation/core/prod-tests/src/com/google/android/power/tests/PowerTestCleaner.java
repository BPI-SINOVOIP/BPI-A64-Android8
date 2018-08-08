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

import com.google.android.utils.MonsoonController.InteractionMode;
import com.google.android.utils.usb.switches.IUsbSwitch;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.FileNotFoundException;
import java.util.concurrent.TimeUnit;

/**
 * Closes the monsoon port and connects USB back. This works only on linux.
 */
@OptionClass(alias = "power-test-cleaner")
public class PowerTestCleaner implements ITargetCleaner {

    private static final int TIMETOUT_SHELL_RESPONSE = 5;
    private static final int RETRY_ATTEMPTS = 5;
    private IUsbSwitch mUsbSwitch;

    private static final int RECONNECTION_ATTEMPTS = 5;
    private static final long HALF_SECOND = 500;
    private static final int POLLING_TIMES = 240;
    private static final long SHORT_WAIT = 5 * 1000; // 5 seconds

    private ITestDevice mDevice;
    private PowerTestUsbSwitchProvider mUsbSwitchProvider;

    @Option(
        name = PowerTestRunnerBase.LAB_SETUP_MAP_FILE_OPTION,
        description =
                "Path to a file containing a map of device's serial - peripherals' descriptors map"
    )
    protected String mPeripheralsMapFile = null;

    // TODO(android-power-te): Remove default value from here and add it to config files.
    @Option(
        name = PowerTestRunnerBase.MONSOON_LIB_PATH_OPTION,
        description = "Path to the monsoon power monitor library"
    )
    protected String mMonsoonLibPath = "/google/data/ro/teams/tradefed/testdata/power/monsoon";

    // TODO(android-power-te): Remove default value from here and add it to config files.
    @Option(
        name = PowerTestRunnerBase.TIGERTOOL_PATH_OPTION,
        description = "Path to the monsoon power monitor library"
    )
    protected String mTigertoolPath =
            "/google/data/ro/teams/tradefed/testdata/power/tigertail/tigertail_tool/tigertool.py";

    /* Exposed so unit tests can mock */
    protected IUsbSwitch getUsbSwitch() throws FileNotFoundException {
        if (mUsbSwitchProvider == null) {
            mUsbSwitchProvider =
                    PowerTestUsbSwitchProvider.Builder()
                            .labSetupMapFilePath(mPeripheralsMapFile)
                            .deviceSerial(mDevice.getSerialNumber())
                            .monsoonLibPath(mMonsoonLibPath)
                            .tigertoolPath(mTigertoolPath)
                            .monsoonInteractionMode(InteractionMode.USE_SERIAL_PORT)
                            .build();
        }

        return mUsbSwitchProvider.getUsbSwitch();
    }

    /* Exposed so it can be */
    protected IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        // no op since this is a target cleaner
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        mDevice = device;

        try {
            mUsbSwitch = getUsbSwitch();
        } catch (FileNotFoundException fnfe) {
            CLog.e(
                    "Couldn't construct usb switch due to a file not found exception. "
                            + "Is the peripherals-map-file correct?");
            CLog.e(fnfe);
        }

        resetUsbSwitchConnection();
    }

    private void resetUsbSwitchConnection() {
        if (deviceIsOnline()) {
            CLog.i("Device is already online. Doing nothing.");
            return;
        }

        CLog.i("Device isn't online. Trying to recover.");

        // It might take long for a device whose battery was fully drained to show online.
        // we poll the device for up to two minutes checking if it is online.
        for (int i = 0; i < RECONNECTION_ATTEMPTS; i++) {
            mUsbSwitch.connectUsb();

            // Extra polling times will increase 20% each boot attempt.
            int extraPollingTimes = (POLLING_TIMES * i) / RECONNECTION_ATTEMPTS;
            for (int j = 0; j < POLLING_TIMES + extraPollingTimes; j++) {
                if (deviceIsOnline()) {
                    CLog.i("Device successfully recovered");
                    return;
                }
                getRunUtil().sleep(HALF_SECOND);
            }

            CLog.v("Couldn't connect usb. Trying to reconnect.");
            mUsbSwitch.disconnectUsb();
            mUsbSwitch.freeResources();
            mUsbSwitch.powerCycle();
            getRunUtil().sleep(SHORT_WAIT);
        }

        CLog.e("Couldn't connect usb device, leaving switch connected");
        mUsbSwitch.connectUsb();
    }

    private boolean deviceIsOnline() {
        return mDevice.getDeviceState().equals(TestDeviceState.ONLINE) && isAndroidRunning();
    }

    /* Exposed for testing */
    protected boolean isAndroidRunning() {
        // Some wear devices (Grant,Glacier) might still be shown as ONLINE at battery charging,
        // check whether device is inside Android system
        final String cmd = "pm path android";
        boolean inAndroid = true;
        try {
            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
            mDevice.executeShellCommand(
                    cmd, receiver, TIMETOUT_SHELL_RESPONSE, TimeUnit.SECONDS, RETRY_ATTEMPTS);
            String output = receiver.getOutput().trim();
            CLog.i(String.format("%s returned [%s]", cmd, output));
            if (!output.contains("package:")) {
                inAndroid = false;
            }
        } catch (Exception e) {
            inAndroid = false;
        }
        return inAndroid;
    }
}
