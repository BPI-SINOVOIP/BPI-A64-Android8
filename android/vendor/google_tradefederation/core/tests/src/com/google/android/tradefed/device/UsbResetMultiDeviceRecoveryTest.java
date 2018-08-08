// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceManager.FastbootDevice;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link UsbResetMultiDeviceRecovery}. */
@RunWith(JUnit4.class)
public class UsbResetMultiDeviceRecoveryTest {

    private static final String SERIAL = "SERIAL";
    private UsbResetMultiDeviceRecovery mRecoverer;
    private IManagedTestDevice mMockTestDevice;
    private IRunUtil mMockRunUtil;

    @Before
    public void setUp() {
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mRecoverer =
                new UsbResetMultiDeviceRecovery() {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }
                };
        mMockTestDevice = EasyMock.createMock(IManagedTestDevice.class);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andStubReturn(SERIAL);
    }

    private void injectFastbootDevice() {
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout(SERIAL + "  fastboot\n");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmdSilently(
                                EasyMock.anyLong(),
                                EasyMock.eq("fastboot"),
                                EasyMock.eq("devices")))
                .andStubReturn(result);
    }

    /** Test that a StubDevice gets ignored and nothing is done. */
    @Test
    public void testIgnoreStub() {
        EasyMock.expect(mMockTestDevice.getIDevice()).andStubReturn(new StubDevice(SERIAL));
        List<IManagedTestDevice> devices = new ArrayList<>();
        devices.add(mMockTestDevice);
        injectFastbootDevice();
        EasyMock.replay(mMockTestDevice, mMockRunUtil);
        mRecoverer.recoverDevices(devices);
        EasyMock.verify(mMockTestDevice, mMockRunUtil);
    }

    /** Test that an allocated fastboot device is not considered for recovery. */
    @Test
    public void testFastbootDevice_allocated() {
        EasyMock.expect(mMockTestDevice.getIDevice()).andStubReturn(new FastbootDevice(SERIAL));
        List<IManagedTestDevice> devices = new ArrayList<>();
        devices.add(mMockTestDevice);
        injectFastbootDevice();
        EasyMock.expect(mMockTestDevice.getAllocationState())
                .andReturn(DeviceAllocationState.Allocated)
                .times(3);
        EasyMock.replay(mMockTestDevice, mMockRunUtil);
        mRecoverer.recoverDevices(devices);
        EasyMock.verify(mMockTestDevice, mMockRunUtil);
    }

    /** Test that a device in Fastboot get attempted for reset and is not rebooted. */
    @Test
    public void testFastbootDevice() {
        EasyMock.expect(mMockTestDevice.getIDevice()).andStubReturn(new FastbootDevice(SERIAL));
        List<IManagedTestDevice> devices = new ArrayList<>();
        devices.add(mMockTestDevice);

        EasyMock.expect(mMockTestDevice.getAllocationState())
                .andReturn(DeviceAllocationState.Unavailable)
                .times(2);
        // The device is found in fastboot
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout(SERIAL + "  fastboot\n");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmdSilently(
                                EasyMock.anyLong(),
                                EasyMock.eq("fastboot"),
                                EasyMock.eq("devices")))
                .andReturn(result)
                .times(2);
        // The device is attempted for reset
        CommandResult resReset = new CommandResult(CommandStatus.SUCCESS);
        EasyMock.expect(
                        mMockRunUtil.runTimedCmd(
                                EasyMock.anyLong(),
                                EasyMock.eq("usb_tool.par"),
                                EasyMock.eq("--serials"),
                                EasyMock.eq(SERIAL),
                                EasyMock.eq("reset")))
                .andReturn(resReset);

        EasyMock.replay(mMockTestDevice, mMockRunUtil);
        mRecoverer.recoverDevices(devices);
        EasyMock.verify(mMockTestDevice, mMockRunUtil);
    }
}
