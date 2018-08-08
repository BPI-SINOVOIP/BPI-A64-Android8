// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.IDeviceStateMonitor;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;

import com.google.android.tradefed.util.GceAvdInfo;
import com.google.android.tradefed.util.GceAvdInfo.GceStatus;
import com.google.common.net.HostAndPort;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link RemoteAndroidVirtualDevice}. */
@RunWith(JUnit4.class)
public class RemoteAndroidVirtualDeviceTest {

    private static final String MOCK_DEVICE_SERIAL = "localhost:1234";
    private static final long WAIT_FOR_TUNNEL_TIMEOUT = 10;
    private IDevice mMockIDevice;
    private ITestLogger mTestLogger;
    private IDeviceStateMonitor mMockStateMonitor;
    private IRunUtil mMockRunUtil;
    private IDeviceMonitor mMockDvcMonitor;
    private IDeviceRecovery mMockRecovery;
    private RemoteAndroidVirtualDevice mTestDevice;
    private GceSshTunnelMonitor mGceSshMonitor;
    private boolean mUseRealTunnel = false;

    private GceManager mGceHandler;
    private IBuildInfo mMockBuildInfo;

    /**
     * A {@link TestDevice} that is suitable for running tests against
     */
    private class TestableRemoteAndroidVirtualDevice extends RemoteAndroidVirtualDevice {
        public TestableRemoteAndroidVirtualDevice() {
            super(mMockIDevice, mMockStateMonitor, mMockDvcMonitor);
            mOptions = new GceAvdTestDeviceOptions();
        }

        @Override
        protected IRunUtil getRunUtil() {
            return mMockRunUtil;
        }

        @Override
        protected GceSshTunnelMonitor getGceSshMonitor() {
            if (mUseRealTunnel) {
                return super.getGceSshMonitor();
            }
            return mGceSshMonitor;
        }

        @Override
        public IDevice getIDevice() {
            return mMockIDevice;
        }

        @Override
        public DeviceDescriptor getDeviceDescriptor() {
            DeviceDescriptor desc =
                    new DeviceDescriptor(
                            "", false, DeviceAllocationState.Allocated, "", "", "", "", "");
            return desc;
        }
    }


    @Before
    public void setUp() throws Exception {
        mUseRealTunnel = false;
        mTestLogger = EasyMock.createMock(ITestLogger.class);
        mMockIDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mMockIDevice.getSerialNumber()).andReturn(MOCK_DEVICE_SERIAL).anyTimes();
        mMockRecovery = EasyMock.createMock(IDeviceRecovery.class);
        mMockStateMonitor = EasyMock.createMock(IDeviceStateMonitor.class);
        mMockDvcMonitor = EasyMock.createMock(IDeviceMonitor.class);
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);

        // A TestDevice with a no-op recoverDevice() implementation
        mTestDevice = new TestableRemoteAndroidVirtualDevice();
        mTestDevice.setRecovery(mMockRecovery);

        mGceSshMonitor = Mockito.mock(GceSshTunnelMonitor.class);
        mGceHandler = Mockito.mock(GceManager.class);

        mMockBuildInfo = new BuildInfo();
    }

    /**
     * Test that an exception thrown in the parser should be propagated to the top level and should
     * not be caught.
     */
    @Test
    public void testExceptionFromParser() {
        final String expectedException =
                "acloud errors: Could not get a valid instance name, check the gce driver's "
                        + "output.The instance may not have booted up at all. [ : ]";
        mTestDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    void createGceSshMonitor(
                            ITestDevice device,
                            IBuildInfo buildInfo,
                            HostAndPort hostAndPort,
                            GceAvdTestDeviceOptions deviceOptions) {
                        // ignore
                    }

                    @Override
                    GceManager getGceHandler() {
                        return new GceManager(
                                getDeviceDescriptor(),
                                new GceAvdTestDeviceOptions(),
                                mMockBuildInfo) {
                            @Override
                            protected List<String> buildGceCmd(File reportFile, IBuildInfo b)
                                    throws IOException {
                                FileUtil.deleteFile(reportFile);
                                List<String> tmp = new ArrayList<String>();
                                tmp.add("");
                                return tmp;
                            }
                        };
                    }
                };
        EasyMock.replay(mMockRunUtil);
        try {
            mTestDevice.launchGce(mMockBuildInfo);
            fail("A TargetSetupError should have been thrown");
        } catch (TargetSetupError expected) {
            assertEquals(expectedException, expected.getMessage());
        }
        EasyMock.verify(mMockRunUtil);
    }

    /**
     * Test {@link RemoteAndroidVirtualDevice#waitForTunnelOnline(long)} return without exception
     * when tunnel is online.
     */
    @Test
    public void testWaitForTunnelOnline() throws Exception {
        doReturn(true).when(mGceSshMonitor).isTunnelAlive();
        EasyMock.replay(mMockRunUtil);
        mTestDevice.waitForTunnelOnline(WAIT_FOR_TUNNEL_TIMEOUT);
        EasyMock.verify(mMockRunUtil);
    }

    /**
     * Test {@link RemoteAndroidVirtualDevice#waitForTunnelOnline(long)} throws an exception when
     * the tunnel returns not alive.
     */
    @Test
    public void testWaitForTunnelOnline_notOnline() throws Exception {
        mMockRunUtil.sleep(EasyMock.anyLong());
        EasyMock.expectLastCall().anyTimes();
        doReturn(false).when(mGceSshMonitor).isTunnelAlive();
        EasyMock.replay(mMockRunUtil);
        try {
            mTestDevice.waitForTunnelOnline(WAIT_FOR_TUNNEL_TIMEOUT);
            fail("Should have thrown an exception.");
        } catch (DeviceNotAvailableException expected) {
            // expected.
        }
        EasyMock.verify(mMockRunUtil);
    }

    /**
     * Test {@link RemoteAndroidVirtualDevice#waitForTunnelOnline(long)} throws an exception when
     * the tunnel object is null, meaning something went wrong during its setup.
     */
    @Test
    public void testWaitForTunnelOnline_tunnelTerminated() throws Exception {
        mGceSshMonitor = null;
        EasyMock.replay(mMockRunUtil);
        try {
            mTestDevice.waitForTunnelOnline(WAIT_FOR_TUNNEL_TIMEOUT);
            fail("Should have thrown an exception.");
        } catch (DeviceNotAvailableException expected) {
            assertEquals(
                    String.format(
                            "Tunnel did not come back online after %sms", WAIT_FOR_TUNNEL_TIMEOUT),
                    expected.getMessage());
        }
        EasyMock.verify(mMockRunUtil);
    }

    /** Test {@link RemoteAndroidVirtualDevice#preInvocationSetup(IBuildInfo)}. */
    @Test
    public void testPreInvocationSetup() throws Exception {
        IBuildInfo mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        TestableRemoteAndroidVirtualDevice testDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    protected void launchGce(IBuildInfo buildInfo) throws TargetSetupError {
                        // ignore
                    }

                    @Override
                    public IDevice getIDevice() {
                        return mMockIDevice;
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public void startLogcat() {
                        // ignore
                    }
                };
        EasyMock.expect(mMockStateMonitor.waitForDeviceAvailable(EasyMock.anyLong()))
                .andReturn(mMockIDevice);
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE);
        replayMocks(mMockBuildInfo);
        testDevice.preInvocationSetup(mMockBuildInfo);
        verifyMocks(mMockBuildInfo);
    }

    /**
     * Test {@link RemoteAndroidVirtualDevice#preInvocationSetup(IBuildInfo)} when the device does
     * not come up online at the end should throw an exception.
     */
    @Test
    public void testPreInvocationSetup_fails() throws Exception {
        IBuildInfo mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        TestableRemoteAndroidVirtualDevice testDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    protected void launchGce(IBuildInfo buildInfo) throws TargetSetupError {
                        // ignore
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }
                };
        EasyMock.expect(mMockStateMonitor.waitForDeviceAvailable(EasyMock.anyLong()))
                .andReturn(mMockIDevice);
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.OFFLINE).times(2);
        replayMocks(mMockBuildInfo);
        try {
            testDevice.preInvocationSetup(mMockBuildInfo);
            fail("Should have thrown an exception.");
        } catch (DeviceNotAvailableException expected) {
            // expected
        }
        verifyMocks(mMockBuildInfo);
    }

    /** Test {@link RemoteAndroidVirtualDevice#postInvocationTearDown()}. */
    @Test
    public void testPostInvocationTearDown() throws Exception {
        EasyMock.expect(mMockStateMonitor.waitForDeviceNotAvailable(EasyMock.anyLong()))
                .andReturn(true);
        // Initial serial is not set because we call postInvoc directly.
        replayMocks();
        mTestDevice.postInvocationTearDown();
        verifyMocks();
        Mockito.verify(mGceSshMonitor).shutdown();
        Mockito.verify(mGceSshMonitor).joinMonitor();
    }

    /** Test that in case of BOOT_FAIL, RemoteAndroidVirtualDevice choose to throw exception. */
    @Test
    public void testLaunchGce_bootFail() throws Exception {
        mTestDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    void createGceSshMonitor(
                            ITestDevice device,
                            IBuildInfo buildInfo,
                            HostAndPort hostAndPort,
                            GceAvdTestDeviceOptions deviceOptions) {
                        // ignore
                    }

                    @Override
                    GceManager getGceHandler() {
                        return mGceHandler;
                    }

                    @Override
                    public DeviceDescriptor getDeviceDescriptor() {
                        return null;
                    }
                };
        doReturn(
                        new GceAvdInfo(
                                "ins-name",
                                HostAndPort.fromHost("127.0.0.1"),
                                "acloud error",
                                GceStatus.BOOT_FAIL))
                .when(mGceHandler)
                .startGce();
        EasyMock.replay(mMockRunUtil, mMockIDevice);
        try {
            mTestDevice.launchGce(new BuildInfo());
            fail("Should have thrown an exception");
        } catch (TargetSetupError expected) {
            // expected
        }
        EasyMock.verify(mMockRunUtil, mMockIDevice);
    }

    /**
     * Sets all member mock objects into replay mode.
     *
     * @param additionalMocks extra local mock objects to set to replay mode
     */
    private void replayMocks(Object... additionalMocks) {
        EasyMock.replay(
                mMockIDevice, mMockRunUtil, mMockStateMonitor, mMockDvcMonitor, mTestLogger);
        for (Object mock : additionalMocks) {
            EasyMock.replay(mock);
        }
    }

    /**
     * Verify all member mock objects.
     *
     * @param additionalMocks extra local mock objects to set to verify
     */
    private void verifyMocks(Object... additionalMocks) {
        EasyMock.verify(
                mMockIDevice, mMockRunUtil, mMockStateMonitor, mMockDvcMonitor, mTestLogger);
        for (Object mock : additionalMocks) {
            EasyMock.verify(mock);
        }
    }

    /**
     * Test that when running on the same device a second time, no shutdown state is preserved that
     * would prevent the tunnel from init again.
     */
    @Test
    public void testDeviceNotStoreShutdownState() throws Exception {
        mUseRealTunnel = true;
        IRunUtil mockRunUtil = Mockito.mock(IRunUtil.class);
        IBuildInfo mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        EasyMock.expect(mMockBuildInfo.getBuildBranch()).andStubReturn("branch");
        EasyMock.expect(mMockBuildInfo.getBuildFlavor()).andStubReturn("flavor");
        EasyMock.expect(mMockBuildInfo.getBuildId()).andStubReturn("id");
        TestableRemoteAndroidVirtualDevice testDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    public IDevice getIDevice() {
                        return mMockIDevice;
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public void startLogcat() {
                        // ignore
                    }

                    @Override
                    GceManager getGceHandler() {
                        return mGceHandler;
                    }

                    @Override
                    public DeviceDescriptor getDeviceDescriptor() {
                        return null;
                    }

                    @Override
                    protected IRunUtil getRunUtil() {
                        return mockRunUtil;
                    }
                };
        testDevice.setTestLogger(mTestLogger);
        File tmpKeyFile = FileUtil.createTempFile("test-gce", "key");
        try {
            OptionSetter setter = new OptionSetter(testDevice.getTestDeviceOptions());
            setter.setOptionValue("gce-private-key-path", tmpKeyFile.getAbsolutePath());
            // We use a missing ssh to prevent the real tunnel from running.
            FileUtil.deleteFile(tmpKeyFile);

            EasyMock.expect(mMockStateMonitor.waitForDeviceAvailable(EasyMock.anyLong()))
                    .andReturn(mMockIDevice)
                    .times(2);
            EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE).times(2);
            EasyMock.expect(mMockStateMonitor.waitForDeviceNotAvailable(EasyMock.anyLong()))
                    .andReturn(true)
                    .times(2);
            mMockStateMonitor.setIDevice(EasyMock.anyObject());
            EasyMock.expectLastCall().times(2);
            doReturn(
                            new GceAvdInfo(
                                    "ins-name",
                                    HostAndPort.fromHost("127.0.0.1"),
                                    null,
                                    GceStatus.SUCCESS))
                    .when(mGceHandler)
                    .startGce();

            replayMocks(mMockBuildInfo);
            // Run device a first time
            testDevice.preInvocationSetup(mMockBuildInfo);
            testDevice.getGceSshMonitor().joinMonitor();
            testDevice.postInvocationTearDown();
            // We expect to find our Runtime exception for the ssh key
            assertNotNull(testDevice.getGceSshMonitor().getLastException());
            // run a second time on same device should yield exact same exception.
            testDevice.preInvocationSetup(mMockBuildInfo);
            testDevice.getGceSshMonitor().joinMonitor();
            testDevice.postInvocationTearDown();
            // Should have the same result, the run time exception from ssh key
            assertNotNull(testDevice.getGceSshMonitor().getLastException());
            verifyMocks(mMockBuildInfo);
        } finally {
            FileUtil.deleteFile(tmpKeyFile);
        }
    }
}
