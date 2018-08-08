// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.athome.tests;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IBuildReceiver;

import org.junit.Assert;

/**
 * Test that runs the ping rpc tests on between two peer devices.
 * <p/>
 * Tradefed framework doesn't currently support a test being directly allocated more than one
 * device, so this test attempts to allocate an additional one.
 * <p/>
 * Assumes that the PingEndpoint and PingClient apks are already installed on provided device,
 * and assume {@link ITargetPreparer}s will correctly prepare additional device.
 */
public class PeerPingRpcHostTest extends LocalPingRpcHostTest implements IConfigurationReceiver,
        IBuildReceiver {

    private IConfiguration mConfig;
    private IBuildInfo mBuild;

    private IDeviceManager getDeviceManager(){
        return GlobalConfiguration.getDeviceManagerInstance();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(getDevice());
        Assert.assertNotNull(mBuild);
        Assert.assertNotNull(mConfig);

        ITestDevice endPointDevice = getDevice();
        Assert.assertNotNull("endpoint device does not have an IP", endPointDevice.getIpAddress());
        ITestDevice clientDevice = null;
        try {
            clientDevice = allocateClientDevice();
            prepareClientDevice(clientDevice);

            int port = startPingEndpoint(getDevice());
            runPingTests(clientDevice, listener, endPointDevice.getIpAddress(), port);
        } catch (TargetSetupError e) {
            CLog.e(e);
            Assert.fail("TargetSetupError occurred when setting up second device");
        } catch (BuildError e) {
            CLog.e(e);
            Assert.fail("BuildError occurred when setting up second device");
        } finally {
            if (clientDevice != null) {
                try (InputStreamSource logcat = clientDevice.getLogcat()) {
                    listener.testLog("client_device_logcat", LogDataType.LOGCAT, logcat);
                }


                getDeviceManager().freeDevice(clientDevice, FreeDeviceState.AVAILABLE);
            }
        }
    }

    private ITestDevice allocateClientDevice() {
        ITestDevice clientDevice = getDeviceManager().allocateDevice(
                mConfig.getDeviceRequirements());
        Assert.assertNotNull("Failed to allocate a second device", clientDevice);
        return clientDevice;
    }

    private void prepareClientDevice(ITestDevice clientDevice) throws TargetSetupError,
        BuildError, DeviceNotAvailableException {
        CLog.i("Running target preparers on allocated client device %s with build %s",
                clientDevice.getSerialNumber(), mBuild.getBuildId());
        for (ITargetPreparer preparer : mConfig.getTargetPreparers()) {
            preparer.setUp(clientDevice, mBuild);
        }
    }
}
