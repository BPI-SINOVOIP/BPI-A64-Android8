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

package com.google.android.power;

import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.TestDeviceState;

import com.google.android.power.tests.PowerMultiDeviceRecovery;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for {@link PowerMultiDeviceRecovery} */
@RunWith(JUnit4.class)
public class PowerMultiDeviceRecoveryTest {

    private static final String ALLOCATED_DEVICE_SERIAL = "allocatedserial";
    private static final String AVAILABLE_DEVICE_SERIAL = "availableserial";
    private static final String BAD_STATE_DEVICE_SERIAL = "badstateserial";
    private static final String MISSING_DEVICE_SERIAL = "missingserial";
    private static final String HOSTNAME = "thishost";
    private static final String ALLOCATED_DEVICE_LAB_SETUP =
            String.format("host:%s device:%s", HOSTNAME, ALLOCATED_DEVICE_SERIAL);
    private static final String AVAILABLE_DEVICE_LAB_SETUP =
            String.format("host:%s device:%s", HOSTNAME, AVAILABLE_DEVICE_SERIAL);
    private static final String BAD_STATE_DEVICE_LAB_SETUP =
            String.format("host:%s device:%s", HOSTNAME, BAD_STATE_DEVICE_SERIAL);
    private static final String MISSING_DEVICE_LAB_SETUP =
            String.format("host:%s device:%s", HOSTNAME, MISSING_DEVICE_SERIAL);
    private static final String LAB_SETUP_DEVICE_FROM_ANOTHER_HOST =
            String.format("host:another device:anotherdeviceserial");

    private List<String> mDevicesRecovered;
    private PowerMultiDeviceRecovery mRecoverer;
    private InputStreamReader mInputStream;
    private IManagedTestDevice mAllocatedDevice = Mockito.mock(IManagedTestDevice.class);
    private IManagedTestDevice mAvailableDevice = Mockito.mock(IManagedTestDevice.class);
    private IManagedTestDevice mBadStateDevice = Mockito.mock(IManagedTestDevice.class);

    @org.junit.Before
    public void setup() {
        mDevicesRecovered = new ArrayList<>();
        mRecoverer =
                new PowerMultiDeviceRecovery() {

                    @Override
                    protected void recover(String serial) {
                        mDevicesRecovered.add(serial);
                    }

                    @Override
                    protected String getHostname() {
                        return HOSTNAME;
                    }

                    @Override
                    protected InputStreamReader getLabSetupInputStream() {
                        return mInputStream;
                    }
                };

        Mockito.when(mAvailableDevice.getSerialNumber()).thenReturn(AVAILABLE_DEVICE_SERIAL);
        Mockito.when(mAvailableDevice.getAllocationState())
                .thenReturn(DeviceAllocationState.Available);

        Mockito.when(mAllocatedDevice.getSerialNumber()).thenReturn(ALLOCATED_DEVICE_SERIAL);
        Mockito.when(mAllocatedDevice.getAllocationState())
                .thenReturn(DeviceAllocationState.Allocated);

        Mockito.when(mBadStateDevice.getSerialNumber()).thenReturn(BAD_STATE_DEVICE_SERIAL);
        Mockito.when(mBadStateDevice.getAllocationState())
                .thenReturn(DeviceAllocationState.Unavailable);
        Mockito.when(mBadStateDevice.getDeviceState()).thenReturn(TestDeviceState.NOT_AVAILABLE);
    }

    @Test
    public void testFromAnotherHostsDoesNotGetRecovered() {
        mInputStream = listToStream(Arrays.asList(LAB_SETUP_DEVICE_FROM_ANOTHER_HOST));
        mRecoverer.recoverDevices(new ArrayList<>());

        Assert.assertEquals(
                "A device from another host shouldn't be recovered.", 0, mDevicesRecovered.size());
    }

    @Test
    public void testAvailableDeviceDoesNotGetRecovered() {
        mInputStream = listToStream(Arrays.asList(AVAILABLE_DEVICE_LAB_SETUP));
        List<IManagedTestDevice> devices = Arrays.asList(mAvailableDevice);
        mRecoverer.recoverDevices(devices);

        Assert.assertEquals(
                "An available device shouldn't be recovered.", 0, mDevicesRecovered.size());
    }

    @Test
    public void testAllocatedDeviceDoesNotGetRecovered() {
        mInputStream = listToStream(Arrays.asList(ALLOCATED_DEVICE_LAB_SETUP));
        List<IManagedTestDevice> devices = Arrays.asList(mAllocatedDevice);

        mRecoverer.recoverDevices(devices);
        Assert.assertEquals(
                "An allocated device shouldn't be recovered.", 0, mDevicesRecovered.size());
    }

    @Test
    public void testDeviceInBadStateGetsRecovered() {
        mInputStream = listToStream(Arrays.asList(BAD_STATE_DEVICE_LAB_SETUP));
        List<IManagedTestDevice> devices = Arrays.asList(mBadStateDevice);

        mRecoverer.recoverDevices(devices);
        Assert.assertEquals(
                "A device in a bad state should be recovered.", 1, mDevicesRecovered.size());

        Assert.assertEquals(BAD_STATE_DEVICE_SERIAL, mDevicesRecovered.get(0));
    }

    @Test
    public void testMissingDevicesGetRecovered() {
        mInputStream = listToStream(Arrays.asList(MISSING_DEVICE_LAB_SETUP));
        mRecoverer.recoverDevices(new ArrayList<>());

        Assert.assertEquals(
                "A device that is not visible but should should be recovered.",
                1,
                mDevicesRecovered.size());

        Assert.assertEquals(MISSING_DEVICE_SERIAL, mDevicesRecovered.get(0));
    }

    @Test
    public void testDevicesThatDisappearOverTimeAreRecovered() {
        mInputStream = listToStream(Arrays.asList());

        // Let the recoverer see the device for the first time.
        mRecoverer.recoverDevices(Arrays.asList(mAvailableDevice));

        Assert.assertEquals(
                "Device was available and shouldn't be recovered.", 0, mDevicesRecovered.size());

        // Streams can only be used once, so it is needed to refresh it before using it again.
        mInputStream = listToStream(Arrays.asList());

        // device has disappeared.
        mRecoverer.recoverDevices(Arrays.asList());
        Assert.assertEquals(
                "Device wen't missing and should have been recovered.",
                1,
                mDevicesRecovered.size());

        Assert.assertEquals(AVAILABLE_DEVICE_SERIAL, mDevicesRecovered.get(0));
    }

    private InputStreamReader listToStream(List<String> input) {
        String stringthing = String.join("\n", input);
        return new InputStreamReader(new ByteArrayInputStream(stringthing.getBytes()));
    }
}
