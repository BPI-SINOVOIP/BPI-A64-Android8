// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.asit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceDisconnectedException;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;

import com.google.android.asit.BaseBootTest.SELinuxDenialCounter;
import com.google.android.tradefed.targetprep.GoogleDeviceFlashPreparer;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for {@link DeviceBootTest}. */
@RunWith(JUnit4.class)
public class DeviceBootTestTest {

    private static final String SERIAL = "serial";
    private DeviceBootTest mBootTest;
    private ITestDevice mMockDevice;
    private IBuildInfo mMockBuildInfo;
    private ITestInvocationListener mMockListener;
    private GoogleDeviceFlashPreparer mMockPreparer;
    private IDevice mMockIDevice;

    @Before
    public void setUp() throws Exception {
        mMockPreparer = Mockito.mock(GoogleDeviceFlashPreparer.class);
        mBootTest =
                new DeviceBootTest() {
                    @Override
                    GoogleDeviceFlashPreparer createDeviceFlasher() {
                        return mMockPreparer;
                    }
                };
        OptionSetter setter = new OptionSetter(mBootTest);
        setter.setOptionValue("skip-battery-test", "true");
        mMockIDevice = Mockito.mock(IDevice.class);
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getIDevice()).andStubReturn(mMockIDevice);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn(SERIAL);
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
        mBootTest.setBuild(mMockBuildInfo);
        mBootTest.setDevice(mMockDevice);
    }

    /** Test a success case for the first boot, reboot bootloader and reboot.
     *  Device comes online all tests are run and pass. */
    @Test
    public void testBringUp() throws Exception {
        mMockListener.testRunStarted(BaseBootTest.BOOT_TEST, 1);
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        EasyMock.expect(mMockDevice.getDeviceState()).andStubReturn(TestDeviceState.ONLINE);
        EasyMock.expect(mMockDevice.executeShellCommand(DeviceBootTest.RAMDUMP_CHECK))
                .andReturn("");

        mMockDevice.setRecoveryMode(RecoveryMode.ONLINE);
        mMockDevice.waitForDeviceOnline();
        mMockDevice.setRecoveryMode(RecoveryMode.AVAILABLE);
        mMockDevice.waitForDeviceAvailable(EasyMock.anyLong());
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockDevice.rebootIntoBootloader();
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockDevice.reboot();
        expectCountLogcatLine();

        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        EasyMock.replay(mMockDevice, mMockBuildInfo, mMockListener);
        mBootTest.run(mMockListener);
        EasyMock.verify(mMockDevice, mMockBuildInfo, mMockListener);
    }

    /**
     * Test a failure case when device is not online after flashing. The Boot test is never called,
     * only the invocation run is failed.
     */
    @Test
    public void testBringUp_deviceNotOnlineafterFlashing() throws Exception {
        mMockListener.testRunStarted(BaseBootTest.BOOT_TEST, 1);
        EasyMock.expect(mMockDevice.getDeviceState()).andStubReturn(TestDeviceState.ONLINE);
        EasyMock.expect(mMockDevice.executeShellCommand(DeviceBootTest.RAMDUMP_CHECK))
                .andReturn("");

        mMockDevice.setRecoveryMode(RecoveryMode.ONLINE);
        mMockDevice.waitForDeviceOnline();
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());

        mMockListener.testRunFailed(DeviceBootTest.ERROR_NOT_ONLINE);
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        EasyMock.replay(mMockDevice, mMockBuildInfo, mMockListener);
        try {
            mBootTest.run(mMockListener);
            fail("Should have thrown an exception");
        } catch (DeviceDisconnectedException expected) {
            assertEquals(DeviceBootTest.ERROR_NOT_ONLINE, expected.getMessage());
        } finally {
            EasyMock.verify(mMockDevice, mMockBuildInfo, mMockListener);
        }
    }

    /** Test failure case after "reboot bootloader". Device does not get in to
     * fastboot. */
    @Test
    public void test_rebootBootLoader_fail() throws Exception {
        mMockListener.testRunStarted(BaseBootTest.BOOT_TEST, 1);
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        EasyMock.expect(mMockDevice.getDeviceState()).andStubReturn(TestDeviceState.ONLINE);
        EasyMock.expect(mMockDevice.executeShellCommand(DeviceBootTest.RAMDUMP_CHECK))
                .andReturn("");

        mMockDevice.setRecoveryMode(RecoveryMode.ONLINE);
        mMockDevice.waitForDeviceOnline();
        mMockDevice.setRecoveryMode(RecoveryMode.AVAILABLE);
        mMockDevice.waitForDeviceAvailable(EasyMock.anyLong());
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testFailed(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockDevice.rebootIntoBootloader();
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockDevice.reboot();
        expectCountLogcatLine();
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        EasyMock.replay(mMockDevice, mMockBuildInfo, mMockListener);
        mBootTest.run(mMockListener);
        EasyMock.verify(mMockDevice, mMockBuildInfo, mMockListener);
    }

    /** Test failure case after "reboot". Device does not come back online. */
    @Test
    public void test_reboot_fail() throws Exception {
        mMockListener.testRunStarted(BaseBootTest.BOOT_TEST, 1);
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        EasyMock.expect(mMockDevice.getDeviceState()).andStubReturn(TestDeviceState.ONLINE);
        EasyMock.expect(mMockDevice.executeShellCommand(DeviceBootTest.RAMDUMP_CHECK))
                .andReturn("");

        mMockDevice.setRecoveryMode(RecoveryMode.ONLINE);
        mMockDevice.waitForDeviceOnline();
        mMockDevice.setRecoveryMode(RecoveryMode.AVAILABLE);
        mMockDevice.waitForDeviceAvailable(EasyMock.anyLong());
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockDevice.rebootIntoBootloader();
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testFailed(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockDevice.reboot();
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());
        expectCountLogcatLine();
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        EasyMock.replay(mMockDevice, mMockBuildInfo, mMockListener);
        mBootTest.run(mMockListener);
        EasyMock.verify(mMockDevice, mMockBuildInfo, mMockListener);
    }

    private void expectCountLogcatLine() {
        InputStreamSource fakeSource = new ByteArrayInputStreamSource("fake\n".getBytes());
        EasyMock.expect(mMockDevice.getLogcat()).andReturn(fakeSource);
    }

    /**
     * Test that when the output of the logcat contains selinux denials we properly capture them and
     * log them.
     */
    @Test
    public void testBringUp_withDenials() throws Exception {
        SELinuxDenialCounter counter = new SELinuxDenialCounter();
        String[] lines = {
            "regular line",
            "avc: denied you got denied",
            "next line",
            "avc: denied denied again!",
            "final line"
        };
        counter.processNewLines(lines);
        mMockListener.testLog(
                EasyMock.eq("denials_log"), EasyMock.eq(LogDataType.TEXT), EasyMock.anyObject());
        EasyMock.replay(mMockListener);
        counter.logDenialsLine(mMockListener);
        EasyMock.verify(mMockListener);
    }
}
