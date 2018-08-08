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

import com.google.android.utils.usb.switches.IUsbSwitch;

import com.android.ddmlib.Log;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.IMultiDeviceRecovery;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.log.LogUtil.CLog;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A {@link IMultiDeviceRecovery} which recovers devices with monsoons and tigertails. */
@OptionClass(alias = "power-recovery")
public class PowerMultiDeviceRecovery implements IMultiDeviceRecovery {

    // TODO(b/65034930) Remove this after the new functionality to recover only devices listed in
    // the map file has been verified to work fine.
    private List<String> mKnownSerials = new ArrayList<>();

    @Option(
        name = PowerTestRunnerBase.LAB_SETUP_MAP_FILE_OPTION,
        description = "path to file containing the peripherals' serials map",
        mandatory = true
    )
    private String mPeripheralsMapFile;

    @Option(
        name = MonsoonTestRunner.MONSOON_LIB_PATH_OPTION,
        description = "Path to the monsoon power monitor library",
        mandatory = true
    )
    private String mMonsoonLibPath;

    @Option(
        name = MonsoonTestRunner.MONSOON_VOLTAGE_OPTION,
        description = "Voltage to reset the monsoon to",
        mandatory = true
    )
    private float mMonsoonVoltage;

    @Option(
        name = PowerTestRunnerBase.TIGERTOOL_PATH_OPTION,
        description = "path to tigertool.py python script",
        mandatory = true
    )
    private String mTigertoolPath;

    @Override
    public void recoverDevices(List<IManagedTestDevice> managedDevices) {
        InputStreamReader reader = getLabSetupInputStream();
        if (reader == null) return;

        LabSetupInfoExtractor infoExtractor = new LabSetupInfoExtractor(reader);

        String hostname = getHostname();
        CLog.logAndDisplay(Log.LogLevel.INFO, "recovering devices in host %s", hostname);
        List<String> thisHostsDevices = infoExtractor.getDevicesByHost(hostname);

        List<String> currentInvocationSerials = new ArrayList<>();
        for (IManagedTestDevice device : managedDevices) {
            // ignore stub devices.
            if (device.getIDevice() instanceof StubDevice) {
                continue;
            }

            // Gather all the serials seen under this invocation.
            currentInvocationSerials.add(device.getSerialNumber());

            // TODO(b/65034930) Remove this after the new functionality to recover only devices
            // listed in the map file has been verified to work fine.
            if (!mKnownSerials.contains(device.getSerialNumber())) {
                mKnownSerials.add(device.getSerialNumber());
            }
        }

        List<String> needsRecovery = new ArrayList<>();

        // Add to recovery list all the devices in bad state.
        for (IManagedTestDevice device : managedDevices) {
            // ignore stub devices.
            if (device.getIDevice() instanceof StubDevice) {
                continue;
            }

            CLog.logAndDisplay(
                    Log.LogLevel.INFO, "Checking device state for %s", device.getSerialNumber());
            CLog.logAndDisplay(
                    Log.LogLevel.INFO,
                    "%s allocation state: %s",
                    device.getSerialNumber(),
                    device.getAllocationState());
            CLog.logAndDisplay(
                    Log.LogLevel.INFO,
                    "%s device state: %s",
                    device.getSerialNumber(),
                    device.getDeviceState());

            if (DeviceAllocationState.Unavailable.equals(device.getAllocationState())
                    && !TestDeviceState.FASTBOOT.equals(device.getDeviceState())) {
                needsRecovery.add(device.getSerialNumber());
                CLog.e("%s is in a bad state. It will be recovered", device.getSerialNumber());
            }
        }

        // Add to the recovery list all those devices from this hosts that are not visible.
        for (String serial : thisHostsDevices) {
            if (!currentInvocationSerials.contains(serial)) {
                needsRecovery.add(serial);
                CLog.e("%s is missing. It will be recovered", serial);
            }
        }

        // TODO(b/65034930) Remove this after the new functionality to recover only devices
        // listed in the map file has been verified to work fine.
        for (String serial : mKnownSerials) {
            if (!currentInvocationSerials.contains(serial) && !needsRecovery.contains(serial)) {
                needsRecovery.add(serial);
                CLog.e("%s was seen before and now is missing. It will be recovered", serial);
            }
        }

        if (needsRecovery.size() == 0) {
            CLog.logAndDisplay(Log.LogLevel.INFO, "No devices need to be recovered.");
            return;
        }

        // Perform recovery.
        for (String serial : needsRecovery) {
            recover(serial);
        }
    }

    /* Exposed for testing */
    protected InputStreamReader getLabSetupInputStream() {
        InputStreamReader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(mPeripheralsMapFile));
        } catch (FileNotFoundException e) {
            CLog.e("Couldn't perform recovery due to FileNotFoundException");
            CLog.e(e);
            return null;
        }
        return reader;
    }

    /* Exposed for testing */
    protected void recover(String serial) {
        try {
            CLog.logAndDisplay(Log.LogLevel.INFO, "recovering %s", serial);

            IUsbSwitch usbSwitch =
                    PowerTestUsbSwitchProvider.Builder()
                            .useTigertail(
                                    PowerTestUsbSwitchProvider.UseTigertailOption.USE_IF_AVAILABLE)
                            .tigertoolPath(mTigertoolPath)
                            .monsoonVoltage(mMonsoonVoltage)
                            .monsoonLibPath(mMonsoonLibPath)
                            .deviceSerial(serial)
                            .labSetupMapFilePath(mPeripheralsMapFile)
                            .build()
                            .getUsbSwitch();

            usbSwitch.powerCycle();

            CLog.logAndDisplay(Log.LogLevel.INFO, "Attempted recovery on %s", serial);
        } catch (FileNotFoundException | RuntimeException e) {
            CLog.e("Couldn't recover device %s", serial);
            CLog.e(e);
        }
    }

    /* Exposed for testing */
    protected String getHostname() {
        // First try to look for hostname in environment variables
        Map<String, String> env = System.getenv();
        if (env.containsKey("COMPUTERNAME")) {
            return env.get("COMPUTERNAME");
        } else if (env.containsKey("HOSTNAME")) {
            return env.get("HOSTNAME");
        }

        // As last resource, look for localhost name.
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            CLog.e("Hostname can not be resolved");
        }

        return null;
    }
}
