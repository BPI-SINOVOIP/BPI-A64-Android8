// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.IDeviceStateMonitor;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.device.WaitDeviceRecovery;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.Set;

/**
 * Unit tests for {@link NcdDeviceRecovery}.
 */
public class NcdDeviceRecoveryTest extends TestCase {

    private static final String SERIAL = "serial";
    private IDeviceStateMonitor mMockMonitor;
    private NcdDeviceRecovery mRecovery;
    private IRunUtil mMockRunUtil;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockMonitor = EasyMock.createMock(IDeviceStateMonitor.class);
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mRecovery = new NcdDeviceRecovery() {
            @Override
            protected IRunUtil getRunUtil() {
                return mMockRunUtil;
            }

        };
        EasyMock.expect(mMockMonitor.getSerialNumber()).andReturn(SERIAL).anyTimes();
        mMockRunUtil.sleep(EasyMock.anyLong());
        EasyMock.expectLastCall().anyTimes();
    }

    /**
     * Test {@link NcdDeviceRecovery#recoverDevice(IDeviceStateMonitor, boolean)}
     * when usb reset succeeds.
     */
    public void testRecoverDevice_usbReset() throws DeviceNotAvailableException {
        setRecoverDeviceExpectations(CommandStatus.SUCCESS);
        setAvailableExpectations();

        EasyMock.replay(mMockMonitor, mMockRunUtil);
        mRecovery.recoverDevice(mMockMonitor, false);
        EasyMock.verify(mMockMonitor, mMockRunUtil);
    }

    /**
     * Test {@link NcdDeviceRecovery#recoverDevice(IDeviceStateMonitor, boolean)}
     * when device is unavailable after a usb reset.
     */
    public void testRecoverDevice_usbResetUnavailable() {
        setRecoverDeviceExpectations(CommandStatus.FAILED);

        EasyMock.replay(mMockMonitor, mMockRunUtil);
        try {
            mRecovery.recoverDevice(mMockMonitor, false);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
        EasyMock.verify(mMockMonitor, mMockRunUtil);
    }

    /**
     * Test {@link NcdDeviceRecovery#recoverDevice(IDeviceStateMonitor, boolean)}
     * when device is unresponsive after a usb reset.
     */
    public void testRecoverDevice_usbResetUnresponsive() throws DeviceNotAvailableException {
        setRecoverDeviceExpectations(CommandStatus.SUCCESS);
        setUnresponsiveExpectations();
        // expect hard reboot to recover from unresponsive device
        setHardRebootExpectations(CommandStatus.SUCCESS);
        setUnresponsiveExpectationsAfterReboot();

        EasyMock.replay(mMockMonitor, mMockRunUtil);
        try {
            mRecovery.recoverDevice(mMockMonitor, false);
            fail("DeviceUnresponsiveException not thrown");
        } catch (DeviceUnresponsiveException e) {
            // expected
        }
        EasyMock.verify(mMockMonitor, mMockRunUtil);
    }

    /**
     * Test {@link NcdDeviceRecovery#recoverDevice(IDeviceStateMonitor, boolean)}
     * when hard reboot succeeds.
     */
    public void testRecoverDevice_hardReboot() throws DeviceNotAvailableException {
        setRecoverDeviceExpectations(CommandStatus.SUCCESS);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(
                null);
        setHardRebootExpectations(CommandStatus.SUCCESS);
        setAvailableExpectationsAfterReboot();

        EasyMock.replay(mMockMonitor, mMockRunUtil);
        mRecovery.recoverDevice(mMockMonitor, false);
        EasyMock.verify(mMockMonitor, mMockRunUtil);
    }

    /**
     * Test {@link NcdDeviceRecovery#recoverDevice(IDeviceStateMonitor, boolean)}
     * when hard reboot fails to bring device back online.
     */
    public void testRecoverDevice_hardRebootUnavailable() {
        setRecoverDeviceExpectations(CommandStatus.SUCCESS);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(null);
        setHardRebootExpectations(CommandStatus.SUCCESS);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(null);

        EasyMock.replay(mMockMonitor, mMockRunUtil);
        try {
            mRecovery.recoverDevice(mMockMonitor, false);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
        EasyMock.verify(mMockMonitor, mMockRunUtil);
    }

    /**
     * Test {@link NcdDeviceRecovery#recoverDevice(IDeviceStateMonitor, boolean)}
     * when device is unresponsive after a hard reboot.
     */
    public void testRecoverDevice_hardRebootUnresponsive() throws DeviceNotAvailableException {
        setRecoverDeviceExpectations(CommandStatus.SUCCESS);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(null);
        setHardRebootExpectations(CommandStatus.SUCCESS);
        setUnresponsiveExpectationsAfterReboot();

        EasyMock.replay(mMockMonitor, mMockRunUtil);
        try {
            mRecovery.recoverDevice(mMockMonitor, false);
            fail("DeviceUnresponsiveException not thrown");
        } catch (DeviceUnresponsiveException e) {
            // expected
        }
        EasyMock.verify(mMockMonitor, mMockRunUtil);
    }



    /**
     * Test {@link NcdDeviceRecovery#recoverDeviceBootloader(IDeviceStateMonitor)} when usb reset
     * succeeds.
     */
    public void testRecoverDeviceBootloader_usbReset() throws DeviceNotAvailableException {
        setRecoverDeviceBootloaderExpectations(CommandStatus.SUCCESS);
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                Boolean.TRUE);

        EasyMock.replay(mMockMonitor, mMockRunUtil);
        mRecovery.recoverDeviceBootloader(mMockMonitor);
        EasyMock.verify(mMockMonitor, mMockRunUtil);
    }

    /**
     * Test {@link NcdDeviceRecovery#recoverDeviceBootloader(IDeviceStateMonitor)} when all attempts
     * to make device available fail.
     */
    public void testRecoverDeviceBootloader_fail() {
        setRecoverDeviceBootloaderExpectations(CommandStatus.SUCCESS);
        // device not in bootloader after usb reset
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                Boolean.FALSE);
        setHardRebootExpectations(CommandStatus.SUCCESS);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(null);

        EasyMock.replay(mMockMonitor, mMockRunUtil);
        try {
            mRecovery.recoverDeviceBootloader(mMockMonitor);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
        EasyMock.verify(mMockMonitor, mMockRunUtil);
    }

    /**
     * Test {@link NcdDeviceRecovery#recoverDeviceBootloader(IDeviceStateMonitor)} when hard reset
     * recovers the device.
     */
    public void testRecoverDeviceBootloader_hardReset() throws DeviceNotAvailableException {
        setRecoverDeviceBootloaderExpectations(CommandStatus.SUCCESS);
        // device not in bootloader after usb reset
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                Boolean.FALSE);
        setHardRebootExpectations(CommandStatus.SUCCESS);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(
                EasyMock.createMock(IDevice.class));
        // now expect reboot into bootloader
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                Boolean.TRUE);

        EasyMock.replay(mMockMonitor, mMockRunUtil);
        mRecovery.recoverDeviceBootloader(mMockMonitor);
        EasyMock.verify(mMockMonitor, mMockRunUtil);
    }

    /**
     * Test {@link NcdDeviceRecovery#handleDeviceBootloaderUnresponsive(IDeviceStateMonitor)}
     */
    public void testRecoverDeviceBootloader_unresponsive() throws DeviceNotAvailableException {
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                Boolean.TRUE);
        CommandResult fastbootResponseResult = new CommandResult(CommandStatus.SUCCESS);
        // expect 'fastboot reboot-bootloader'
        EasyMock.expect(
                mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("fastboot"),
                        EasyMock.eq("-s"), EasyMock.eq(SERIAL), EasyMock.eq("reboot-bootloader")))
                .andReturn(fastbootResponseResult);
        // expect calls to ensure device will be not available, followed by bootloader
        EasyMock.expect(mMockMonitor.waitForDeviceNotAvailable(EasyMock.anyLong())).andReturn(
                true);
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                true);
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("fastboot"),
                EasyMock.eq("-s"), EasyMock.eq("serial"), EasyMock.eq("getvar"),
                EasyMock.eq("product"))).
                andReturn(new CommandResult(CommandStatus.SUCCESS));

        EasyMock.replay(mMockMonitor, mMockRunUtil);
        mRecovery.recoverDeviceBootloader(mMockMonitor);
        EasyMock.verify(mMockMonitor, mMockRunUtil);
    }

    /**
     * Test {@link NcdDeviceRecovery#getNcdPath()} parses expected input correctly.
     */
    public void testGetDeviceList() {
        CommandResult ncdResult = new CommandResult();
        ncdResult.setStatus(CommandStatus.SUCCESS);
        ncdResult.setStdout(
                "HT9CYP806252 = 0\n" +
                "HT9AVP800306 = 1\n" +
                "HT99TP800121 = 2\n");
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("ncd.py"),
                (String)EasyMock.anyObject())).andReturn(ncdResult);

        EasyMock.replay(mMockRunUtil);
        Set<String> serials = mRecovery.getDeviceList();
        assertEquals(3, serials.size());
        assertTrue(serials.contains("HT9CYP806252"));
        assertTrue(serials.contains("HT9AVP800306"));
        assertTrue(serials.contains("HT99TP800121"));
    }

    /**
     * Configure the mock objects for a recoverDevice call that triggers a usb reset with given
     * status.
     */
    private void setRecoverDeviceExpectations(CommandStatus status) {
        EasyMock.expect(mMockMonitor.getDeviceState()).andReturn(TestDeviceState.NOT_AVAILABLE);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(null);
        mMockMonitor.waitForDeviceBootloaderStateUpdate();
        setResetUsbExpectations(status);
    }

    /**
     * Configure the mock objects for a recoverDevice call that triggers a usb reset with given
     * status.
     */
    private void setRecoverDeviceBootloaderExpectations(CommandStatus status) {
        EasyMock.expect(mMockMonitor.getDeviceState())
                .andReturn(TestDeviceState.NOT_AVAILABLE)
                .times(WaitDeviceRecovery.BOOTLOADER_POLL_ATTEMPTS);
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                Boolean.FALSE).times(WaitDeviceRecovery.BOOTLOADER_POLL_ATTEMPTS);
        setResetUsbExpectations(status);
    }

    /**
     * Configure the mock objects for a usb reset command.
     */
    private void setResetUsbExpectations(CommandStatus status) {
        CommandResult ncdResult = new CommandResult();
        ncdResult.setStatus(status);
        EasyMock.expect(
                mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("ncd.py"),
                        EasyMock.eq("--device"), EasyMock.eq(SERIAL), EasyMock.eq("--usb-only")))
                .andReturn(ncdResult).anyTimes();
    }

    /**
     * Configure the mock objects for a hard reset command.
     */
    private void setHardRebootExpectations(CommandStatus status) {
        CommandResult ncdResult = new CommandResult();
        ncdResult.setStatus(status);
        EasyMock.expect(
                mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("ncd.py"),
                        EasyMock.eq("--device"), EasyMock.eq(SERIAL)))
                .andReturn(ncdResult).anyTimes();
    }

    /**
     * Configures the monitor mock to simulate an online but unresponsive device.
     */
    private void setUnresponsiveExpectations() {
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(
                EasyMock.createMock(IDevice.class));
        EasyMock.expect(mMockMonitor.waitForDeviceShell(EasyMock.anyLong())).andReturn(true);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(null);
    }

    /**
     * Configures the monitor mock to simulate an online but unresponsive device, with timeouts set
     * to the normal reboot timeout
     */
    private void setUnresponsiveExpectationsAfterReboot() {
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(
                EasyMock.createMock(IDevice.class));
        EasyMock.expect(mMockMonitor.waitForDeviceShell(EasyMock.anyLong())).andReturn(true);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable()).andReturn(null);
    }

    /**
     * Configures the monitor mock to simulate an online and responsive device.
     */
    private void setAvailableExpectations() {
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(
                EasyMock.createMock(IDevice.class));
        EasyMock.expect(mMockMonitor.waitForDeviceShell(EasyMock.anyLong())).andReturn(true);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(
                EasyMock.createMock(IDevice.class));
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(
                EasyMock.createMock(IDevice.class));
    }

    /**
     * Configures the monitor mock to simulate an available device, with timeouts set to the normal
     * reboot timeout
     */
    private void setAvailableExpectationsAfterReboot() {
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(
                EasyMock.createMock(IDevice.class));
        EasyMock.expect(mMockMonitor.waitForDeviceShell(EasyMock.anyLong())).andReturn(true);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable()).andReturn(EasyMock.createMock(
                IDevice.class));
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(
                EasyMock.createMock(IDevice.class));
    }
}
