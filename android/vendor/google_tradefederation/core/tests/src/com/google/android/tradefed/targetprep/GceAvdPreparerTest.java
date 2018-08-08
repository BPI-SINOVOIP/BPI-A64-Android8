// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.TcpDevice;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.android.tradefed.device.RemoteAvdIDevice;
import com.google.android.tradefed.util.GceAvdInfo;
import com.google.android.tradefed.util.GceAvdInfo.GceStatus;
import com.google.common.net.HostAndPort;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * TestCases for GceAvdPreparer.
 */
@Deprecated
public class GceAvdPreparerTest extends TestCase {
    private final static String SERIAL = "tcp-device-0";
    private GceAvdPreparer mPrep = null;
    private IRunUtil mMockUtil;
    private IManagedTestDevice mMockDevice;
    private IBuildInfo mMockBuildInfo;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mPrep = new GceAvdPreparer();
        mMockUtil = EasyMock.createMock(IRunUtil.class);
        mMockDevice = EasyMock.createMock(IManagedTestDevice.class);
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andStubReturn(null);
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
    }

    /**
     * Test that an exception thrown in the parser should be propagated to the top level and
     * should not be caught.
     */
    public void testExceptionFromParser() {
        final String expectedException = "acloud errors:  null";
        mPrep = new GceAvdPreparer() {
            @Override
            protected IRunUtil getRunUtil() {
                return mMockUtil;
            }
            @Override
            protected List<String> buildGceCmd(File reportFile, IBuildInfo b) throws IOException {
                List<String> tmp = new ArrayList<String>();
                tmp.add("");
                return tmp;
            }
        };
        CommandResult cmd = new CommandResult();
        cmd.setStatus(CommandStatus.FAILED);
        cmd.setStdout("output err");
        EasyMock.expect(mMockUtil.runTimedCmd(EasyMock.anyLong(), (String[])EasyMock.anyObject()))
                .andReturn(cmd);
        EasyMock.expect(mMockDevice.getSerialNumber()).andReturn(SERIAL);
        EasyMock.replay(mMockUtil, mMockDevice);
        try {
            mPrep.launchGce(mMockDevice, new BuildInfo());
            fail("A TargetSetupError should have been thrown");
        } catch (TargetSetupError expected) {
            assertEquals(expectedException, expected.getMessage());
        }
    }

    /** Test that the exception for the ssh tunnel is carried to main thread to be reported. */
    public void testSurfaceSshException() throws Exception {
        mPrep =
                new GceAvdPreparer() {
                    @Override
                    GceAvdInfo startGce(IBuildInfo b, ITestDevice device) throws TargetSetupError {
                        return new GceAvdInfo(
                                "instance",
                                HostAndPort.fromString("127.0.0.1"),
                                null,
                                GceStatus.SUCCESS);
                    }

                    @Override
                    protected void launchGce(ITestDevice device, IBuildInfo buildInfo)
                            throws TargetSetupError {
                        super.launchGce(device, buildInfo);
                        // give a little time to the tunnel tread to finish.
                        RunUtil.getDefault().sleep(250);
                        // Ensure that the thread has terminated before letting the test proceed.
                        try {
                            getSshTunnelMonitor().join();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
        OptionSetter setter = new OptionSetter(mPrep);
        setter.setOptionValue("gce-private-key-path", "/tmp/doesnotexistsFile");
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn(SERIAL);
        EasyMock.expect(mMockDevice.getIDevice()).andStubReturn(new TcpDevice("tcp-device-0"));
        mMockDevice.setFastbootEnabled(false);
        EasyMock.expect(mMockDevice.getRecoveryMode()).andReturn(RecoveryMode.ONLINE);
        mMockDevice.setRecoveryMode(RecoveryMode.NONE);
        mMockDevice.setRecoveryMode(RecoveryMode.ONLINE);
        mMockDevice.waitForDeviceAvailable(EasyMock.anyLong());
        EasyMock.expectLastCall().anyTimes();
        EasyMock.replay(mMockUtil, mMockDevice, mMockBuildInfo);
        try {
            mPrep.setUp(mMockDevice, mMockBuildInfo);
            fail("Should have thrown an exception.");
        } catch (DeviceNotAvailableException expected) {
            assertEquals(
                    "Error occured during AVD boot up. Ssh private key is unavailable "
                            + "/tmp/doesnotexistsFile",
                    expected.getMessage());
        }
        EasyMock.verify(mMockUtil, mMockDevice, mMockBuildInfo);
    }

    /** Test that the preparer is skipped if the device is a RemoteAvdIDevice (new gce type) */
    public void testSkipPreparer_RemoteAvd() throws Exception {
        EasyMock.expect(mMockDevice.getIDevice()).andReturn(new RemoteAvdIDevice("gce-device-0"));
        EasyMock.replay(mMockUtil, mMockDevice, mMockBuildInfo);
        mPrep.setUp(mMockDevice, mMockBuildInfo);
        EasyMock.verify(mMockUtil, mMockDevice, mMockBuildInfo);
    }

    /** Test that the preparer is skipped if the device is not a tcpDevice */
    public void testSkipPreparer_notTcp() throws Exception {
        EasyMock.expect(mMockDevice.getIDevice()).andStubReturn(EasyMock.createMock(IDevice.class));
        EasyMock.replay(mMockUtil, mMockDevice, mMockBuildInfo);
        mPrep.setUp(mMockDevice, mMockBuildInfo);
        EasyMock.verify(mMockUtil, mMockDevice, mMockBuildInfo);
    }
}
