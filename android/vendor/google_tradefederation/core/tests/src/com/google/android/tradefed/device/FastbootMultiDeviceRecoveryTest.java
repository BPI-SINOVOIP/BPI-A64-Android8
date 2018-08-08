// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link FastbootMultiDeviceRecovery}.
 */
public class FastbootMultiDeviceRecoveryTest extends TestCase {

    private static final String SERIAL = "SERIAL";

    private IRunUtil mMockRunUtil;
    private IManagedTestDevice mMockTestDevice;
    private IDevice mMockIDevice;
    private IDeviceManager mMockDeviceManager;
    private ICommandScheduler mMockScheduler;

    private FastbootMultiDeviceRecovery recoverer;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mMockTestDevice = EasyMock.createMock(IManagedTestDevice.class);
        mMockDeviceManager = EasyMock.createMock(IDeviceManager.class);
        mMockScheduler = EasyMock.createMock(ICommandScheduler.class);
        mMockIDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andStubReturn(SERIAL);
        EasyMock.expect(mMockTestDevice.getIDevice()).andStubReturn(mMockIDevice);
        recoverer =
                new FastbootMultiDeviceRecovery() {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    IDeviceManager getDeviceManager() {
                        return mMockDeviceManager;
                    }

                    @Override
                    ICommandScheduler getCommandScheduler() {
                        return mMockScheduler;
                    }
                };
    }

    /**
     * Test for {@link FastbootMultiDeviceRecovery#recoverDevices(List)} when one fastboot device
     * is allocated it should be ignored.
     */
    public void testRecoverDevice_allocatedFastboot() {
        List<IManagedTestDevice> devices = new ArrayList<>();
        devices.add(mMockTestDevice);
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout(SERIAL + "  fastboot\n");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmdSilently(
                                EasyMock.anyLong(),
                                EasyMock.eq("fastboot"),
                                EasyMock.eq("devices")))
                .andReturn(result);
        EasyMock.expect(mMockTestDevice.getAllocationState())
                .andReturn(DeviceAllocationState.Allocated);
        EasyMock.replay(mMockRunUtil, mMockTestDevice);
        recoverer.recoverDevices(devices);
        EasyMock.verify(mMockRunUtil, mMockTestDevice);
    }

    /**
     * Test for {@link FastbootMultiDeviceRecovery#recoverDevices(List)} when one fastboot device
     * is not allocated it will be considered for recovery.
     */
    public void testRecoverDevice() throws Exception {
        List<IManagedTestDevice> devices = new ArrayList<>();
        devices.add(mMockTestDevice);
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout(SERIAL + "  fastboot\n");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmdSilently(
                                EasyMock.anyLong(),
                                EasyMock.eq("fastboot"),
                                EasyMock.eq("devices")))
                .andReturn(result);
        EasyMock.expect(mMockTestDevice.getAllocationState())
                .andReturn(DeviceAllocationState.Available);
        EasyMock.expect(mMockDeviceManager.forceAllocateDevice(SERIAL)).andReturn(mMockTestDevice);
        mMockScheduler.execCommand((IScheduledInvocationListener)EasyMock.anyObject(),
                EasyMock.eq(mMockTestDevice), (String[])EasyMock.anyObject());
        EasyMock.expect(mMockTestDevice.getProductType()).andStubReturn("bullhead");
        EasyMock.replay(mMockRunUtil, mMockTestDevice, mMockDeviceManager, mMockScheduler);
        recoverer.recoverDevices(devices);
        EasyMock.verify(mMockRunUtil, mMockTestDevice, mMockDeviceManager, mMockScheduler);
    }

    /**
     * Test for {@link FastbootMultiDeviceRecovery#recoverDevices(List)} when one device is in
     * recovery mode, it will attempt to reboot it in bootloader.
     */
    public void testRecoverDevice_recoveryMode() throws Exception {
        List<IManagedTestDevice> devices = new ArrayList<>();
        devices.add(mMockTestDevice);
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout("\n");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmdSilently(
                                EasyMock.anyLong(),
                                EasyMock.eq("fastboot"),
                                EasyMock.eq("devices")))
                .andReturn(result);
        EasyMock.expect(mMockTestDevice.getAllocationState())
                .andReturn(DeviceAllocationState.Available);
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.RECOVERY);
        mMockIDevice.reboot("bootloader");
        EasyMock.replay(mMockRunUtil, mMockTestDevice, mMockIDevice);
        recoverer.recoverDevices(devices);
        EasyMock.verify(mMockRunUtil, mMockTestDevice, mMockIDevice);
    }

    /**
     * Test for {@link FastbootMultiDeviceRecovery#recoverDevices(List)} when using a pairing that
     * was added by option.
     */
    public void testRecoverDevice_withExtraPairing() throws Exception {
        final String prod = "fakeprod";
        final String branch = "git_fakebranch";
        final String flavor = "fakeflavor";
        OptionSetter setter = new OptionSetter(recoverer);
        setter.setOptionValue(
                "product-branch-pairing", String.format("%s:%s:%s", prod, branch, flavor));
        EasyMock.expect(mMockTestDevice.getProductType()).andStubReturn(prod);
        List<IManagedTestDevice> devices = new ArrayList<>();
        devices.add(mMockTestDevice);
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout(SERIAL + "  fastboot\n");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmdSilently(
                                EasyMock.anyLong(),
                                EasyMock.eq("fastboot"),
                                EasyMock.eq("devices")))
                .andReturn(result);
        EasyMock.expect(mMockTestDevice.getAllocationState())
                .andReturn(DeviceAllocationState.Available);
        EasyMock.expect(mMockDeviceManager.forceAllocateDevice(SERIAL)).andReturn(mMockTestDevice);
        Capture<String[]> capturedArgs = new Capture<>();
        mMockScheduler.execCommand(
                (IScheduledInvocationListener) EasyMock.anyObject(),
                EasyMock.eq(mMockTestDevice),
                EasyMock.capture(capturedArgs));
        EasyMock.replay(mMockRunUtil, mMockTestDevice, mMockDeviceManager, mMockScheduler);
        recoverer.recoverDevices(devices);
        EasyMock.verify(mMockRunUtil, mMockTestDevice, mMockDeviceManager, mMockScheduler);
        List<String> args = Arrays.asList(capturedArgs.getValue());
        assertTrue(args.contains(branch));
        assertTrue(args.contains("--build-flavor"));
        assertTrue(args.contains(flavor));
    }

    /**
     * Test for {@link FastbootMultiDeviceRecovery#recoverDevices(List)} when using a pairing that
     * was added by option with the second format
     */
    public void testRecoverDevice_withExtraPairing_secondFormat() throws Exception {
        final String prod = "fakeprod2";
        final String branch = "git_fakebranch2";
        OptionSetter setter = new OptionSetter(recoverer);
        // No flavor is specified
        setter.setOptionValue("product-branch-pairing", String.format("%s:%s", prod, branch));
        EasyMock.expect(mMockTestDevice.getProductType()).andStubReturn(prod);
        List<IManagedTestDevice> devices = new ArrayList<>();
        devices.add(mMockTestDevice);
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout(SERIAL + "  fastboot\n");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmdSilently(
                                EasyMock.anyLong(),
                                EasyMock.eq("fastboot"),
                                EasyMock.eq("devices")))
                .andReturn(result);
        EasyMock.expect(mMockTestDevice.getAllocationState())
                .andReturn(DeviceAllocationState.Available);
        EasyMock.expect(mMockDeviceManager.forceAllocateDevice(SERIAL)).andReturn(mMockTestDevice);
        Capture<String[]> capturedArgs = new Capture<>();
        mMockScheduler.execCommand(
                (IScheduledInvocationListener) EasyMock.anyObject(),
                EasyMock.eq(mMockTestDevice),
                EasyMock.capture(capturedArgs));
        EasyMock.replay(mMockRunUtil, mMockTestDevice, mMockDeviceManager, mMockScheduler);
        recoverer.recoverDevices(devices);
        EasyMock.verify(mMockRunUtil, mMockTestDevice, mMockDeviceManager, mMockScheduler);
        List<String> args = Arrays.asList(capturedArgs.getValue());
        assertTrue(args.contains(branch));
        // no build flavor was requested with second format
        assertFalse(args.contains("--build-flavor"));
    }

    /**
     * Test for {@link FastbootMultiDeviceRecovery#recoverDevices(List)} when the option product has
     * an invalid format, it cannot be matched.
     */
    public void testRecoverDevice_failParing() throws Exception {
        OptionSetter setter = new OptionSetter(recoverer);
        // bad format
        setter.setOptionValue("product-branch-pairing", "thisisnotvalid");
        EasyMock.expect(mMockTestDevice.getProductType()).andStubReturn("doesnotexists");
        List<IManagedTestDevice> devices = new ArrayList<>();
        devices.add(mMockTestDevice);
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout(SERIAL + "  fastboot\n");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmdSilently(
                                EasyMock.anyLong(),
                                EasyMock.eq("fastboot"),
                                EasyMock.eq("devices")))
                .andReturn(result);
        EasyMock.expect(mMockTestDevice.getAllocationState())
                .andReturn(DeviceAllocationState.Available);
        EasyMock.replay(mMockRunUtil, mMockTestDevice, mMockDeviceManager, mMockScheduler);
        recoverer.recoverDevices(devices);
        EasyMock.verify(mMockRunUtil, mMockTestDevice, mMockDeviceManager, mMockScheduler);
    }

    /**
     * Test for {@link FastbootMultiDeviceRecovery#recoverDevices(List)} when using a pairing that
     * was added by option and the product already exists, the option branch replace the old one.
     */
    public void testRecoverDevice_replaceExisting() throws Exception {
        final String prod = "angler";
        final String branch = "git_fakebranch";
        OptionSetter setter = new OptionSetter(recoverer);
        setter.setOptionValue("product-branch-pairing", String.format("%s:%s", prod, branch));
        EasyMock.expect(mMockTestDevice.getProductType()).andStubReturn(prod);
        List<IManagedTestDevice> devices = new ArrayList<>();
        devices.add(mMockTestDevice);
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout(SERIAL + "  fastboot\n");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmdSilently(
                                EasyMock.anyLong(),
                                EasyMock.eq("fastboot"),
                                EasyMock.eq("devices")))
                .andReturn(result);
        EasyMock.expect(mMockTestDevice.getAllocationState())
                .andReturn(DeviceAllocationState.Available);
        EasyMock.expect(mMockDeviceManager.forceAllocateDevice(SERIAL)).andReturn(mMockTestDevice);
        Capture<String[]> capturedArgs = new Capture<>();
        mMockScheduler.execCommand(
                (IScheduledInvocationListener) EasyMock.anyObject(),
                EasyMock.eq(mMockTestDevice),
                EasyMock.capture(capturedArgs));
        EasyMock.replay(mMockRunUtil, mMockTestDevice, mMockDeviceManager, mMockScheduler);
        recoverer.recoverDevices(devices);
        EasyMock.verify(mMockRunUtil, mMockTestDevice, mMockDeviceManager, mMockScheduler);
        List<String> args = Arrays.asList(capturedArgs.getValue());
        assertTrue(args.contains(branch));
        assertFalse(args.contains("--build-flavor"));
    }
}
