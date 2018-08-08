// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.asit;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.TargetSetupError;

import com.google.android.asit.BaseBootTest.RebootType;
import com.google.android.tradefed.targetprep.GceAvdPreparer;
import com.google.android.tradefed.util.GceAvdInfo;
import com.google.android.tradefed.util.GceAvdInfo.GceStatus;
import com.google.common.net.HostAndPort;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for {@link GceBootTest}. */
@RunWith(JUnit4.class)
public class GceBootTestTest {

    private static final String SERIAL = "tcp-device-99";
    private GceBootTest mBootTest;
    private ITestDevice mMockDevice;
    private IBuildInfo mMockBuildInfo;
    private ITestInvocationListener mMockListener;
    private GceAvdPreparer mMockPreparer;
    private IDevice mMockIDevice;

    @Before
    public void setUp() throws Exception {
        mMockPreparer = Mockito.mock(GceAvdPreparer.class);
        mBootTest =
                new GceBootTest() {
                    @Override
                    GceAvdPreparer createAvdPreparer() {
                        return mMockPreparer;
                    }

                    @Override
                    void captureBugreportz(ITestLogger testLogger) {
                        // ignore
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

    /** Test a success case for the first boot and reboot. Device comes online, reboot
     *  all tests are run and pass. */
    @Test
    public void testBringUp() throws Exception {
        mMockListener.testRunStarted(BaseBootTest.BOOT_TEST, 1);
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        EasyMock.expect(mMockDevice.getDeviceState()).andStubReturn(TestDeviceState.ONLINE);
        mMockDevice.setRecoveryMode(RecoveryMode.ONLINE);
        mMockDevice.waitForDeviceOnline();
        mMockDevice.setRecoveryMode(RecoveryMode.AVAILABLE);
        EasyMock.expectLastCall();
        mMockDevice.waitForDeviceAvailable(EasyMock.anyLong());
        EasyMock.expectLastCall();
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockDevice.reboot();
        expectLogcatBugreportTest();
        expectCountLogcatLine();
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        EasyMock.replay(mMockDevice, mMockBuildInfo, mMockListener);
        mBootTest.run(mMockListener);
        EasyMock.verify(mMockDevice, mMockBuildInfo, mMockListener);
        Mockito.verify(mMockPreparer).setUp(Mockito.eq(mMockDevice), Mockito.eq(mMockBuildInfo));
    }

    /** Test a failure case for reboot after flashing the device. */
    @Test
    public void test_reboot_fail() throws Exception {
        mMockListener.testRunStarted(BaseBootTest.BOOT_TEST, 1);
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        EasyMock.expect(mMockDevice.getDeviceState()).andStubReturn(TestDeviceState.ONLINE);
        mMockDevice.setRecoveryMode(RecoveryMode.ONLINE);
        mMockDevice.waitForDeviceOnline();
        mMockDevice.setRecoveryMode(RecoveryMode.AVAILABLE);
        EasyMock.expectLastCall();
        mMockDevice.waitForDeviceAvailable(EasyMock.anyLong());
        EasyMock.expectLastCall();
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testFailed(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockDevice.reboot();
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());
        expectLogcatBugreportTest();
        expectCountLogcatLine();
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        EasyMock.replay(mMockDevice, mMockBuildInfo, mMockListener);
        mBootTest.run(mMockListener);
        EasyMock.verify(mMockDevice, mMockBuildInfo, mMockListener);
        Mockito.verify(mMockPreparer).setUp(Mockito.eq(mMockDevice), Mockito.eq(mMockBuildInfo));
    }

    /**
     * Test a failure case where the setup fails, in this case we do not fail the run but only the
     * test.
     */
    @Test
    public void testBringUp_targetSetupError() throws Exception {
        mMockListener.testRunStarted(BaseBootTest.BOOT_TEST, 1);
        mMockListener.testRunFailed(EasyMock.anyObject());
        EasyMock.expect(mMockDevice.getDeviceState()).andStubReturn(TestDeviceState.ONLINE);
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        DeviceDescriptor nullDescriptor = null;
        doThrow(
                        new TargetSetupError(
                                "acloud errors: Device ins-A did not finish on boot within "
                                        + "timeout (300 secs)",
                                nullDescriptor))
                .when(mMockPreparer)
                .setUp(Mockito.eq(mMockDevice), Mockito.eq(mMockBuildInfo));

        EasyMock.replay(mMockDevice, mMockBuildInfo, mMockListener);
        try {
            mBootTest.run(mMockListener);
            fail("Should have thrown an exception");
        } catch (DeviceNotAvailableException expected) {
            // expected
        } finally {
            EasyMock.verify(mMockDevice, mMockBuildInfo, mMockListener);
            Mockito.verify(mMockPreparer)
                    .setUp(Mockito.eq(mMockDevice), Mockito.eq(mMockBuildInfo));
        }
    }

    /**
     * Test a failure case where the setup fails, in this case we do not fail the run but only the
     * test.
     */
    @Test
    public void testBringUp_targetSetupError_gceFail() throws Exception {
        mMockListener.testRunStarted(BaseBootTest.BOOT_TEST, 1);
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testFailed(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        EasyMock.expect(mMockDevice.getDeviceState()).andStubReturn(TestDeviceState.ONLINE);
        mBootTest.testRebootCommand(mMockListener, RebootType.REBOOT_TEST);
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        DeviceDescriptor nullDescriptor = null;
        doThrow(
                        new TargetSetupError(
                                "acloud errors: Device ins-A did not finish on boot within "
                                        + "timeout (300 secs)",
                                nullDescriptor))
                .when(mMockPreparer)
                .setUp(Mockito.eq(mMockDevice), Mockito.eq(mMockBuildInfo));
        doReturn(
                        new GceAvdInfo(
                                "ins-A",
                                HostAndPort.fromParts("127.0.0.1", 8888),
                                "it went bad",
                                GceStatus.BOOT_FAIL))
                .when(mMockPreparer)
                .getGceAvdInfo();

        EasyMock.replay(mMockDevice, mMockBuildInfo, mMockListener);
        try {
            mBootTest.run(mMockListener);
            //fail("Should have thrown an exception");
        } catch (DeviceNotAvailableException expected) {
            // expected
        } finally {
            EasyMock.verify(mMockDevice, mMockBuildInfo, mMockListener);
            Mockito.verify(mMockPreparer)
                    .setUp(Mockito.eq(mMockDevice), Mockito.eq(mMockBuildInfo));
        }
    }

    private void expectLogcatBugreportTest() {
        mMockDevice.setRecoveryMode(RecoveryMode.NONE);
        InputStreamSource fakeSource = new ByteArrayInputStreamSource("fake".getBytes());
        EasyMock.expect(mMockDevice.getLogcatDump()).andReturn(fakeSource);
        mMockListener.testLog(GceBootTest.LOGCAT_NAME, LogDataType.LOGCAT, fakeSource);

        InputStreamSource fakeSource2 = new ByteArrayInputStreamSource("fake".getBytes());
        EasyMock.expect(mMockDevice.getBugreport()).andReturn(fakeSource2);
        mMockListener.testLog(GceBootTest.BUGREPORT_NAME, LogDataType.BUGREPORT, fakeSource2);
    }

    private void expectCountLogcatLine() {
        InputStreamSource fakeSource = new ByteArrayInputStreamSource("fake\n".getBytes());
        EasyMock.expect(mMockDevice.getLogcat()).andReturn(fakeSource);
    }
}
