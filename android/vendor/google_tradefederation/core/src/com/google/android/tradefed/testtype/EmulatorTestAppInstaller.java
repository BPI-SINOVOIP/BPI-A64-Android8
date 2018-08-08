// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.StreamUtil;
import com.google.android.tradefed.targetprep.EmulatorTestAppInstallSetup;

import java.util.HashMap;

/**
 * A class to install test apps to emualtor, and post the result
 */
public class EmulatorTestAppInstaller implements IDeviceTest, IRemoteTest, IBuildReceiver,
        IConfigurationReceiver {
    private IConfiguration mConfiguration;
    private String mTestLabel = "emulator_test_app_install_test";
    private EmulatorTestAppInstallSetup mEmulatorTestAppPreparer;
    private IBuildInfo mBuildInfo;
    private ITestDevice mDevice;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        TestIdentifier testAppInstallTest = new TestIdentifier(
                EmulatorTestAppInstaller.class.getSimpleName(), mTestLabel);
        listener.testRunStarted(EmulatorTestAppInstaller.class.getSimpleName(), 1);
        listener.testStarted(testAppInstallTest);
        try {
            mEmulatorTestAppPreparer = (EmulatorTestAppInstallSetup) mConfiguration
                    .getConfigurationObject("smoketests-apk-preparer");
            mEmulatorTestAppPreparer.setUp(mDevice, mBuildInfo);
        } catch (RuntimeException e) {
            listener.testFailed(testAppInstallTest, StreamUtil.getStackTrace(e));
            throw e;
        } catch (TargetSetupError e) {
            listener.testFailed(testAppInstallTest, StreamUtil.getStackTrace(e));
            throw new RuntimeException(e);
        } finally {
            listener.testEnded(testAppInstallTest, new HashMap<String, String>());
            listener.testRunEnded(0, new HashMap<String, String>());
        }
    }
}
