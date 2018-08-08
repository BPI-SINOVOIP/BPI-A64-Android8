// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.athome.tests;

import com.android.loganalysis.item.BugreportItem;
import com.android.monkey.MonkeyBase;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IBuildReceiver;

import org.junit.Assert;

/**
 * Test that runs sets up a phone controller and a tungsten, then runs app monkey on phone.
 */
public class AtHomeAppMonkey extends MonkeyBase implements IBuildReceiver,
        IConfigurationReceiver {
    public static final String DEVICE_PREFIX = "device_";
    public static final String PHONE_PREFIX = "phone_";

    @Option(name = "max-setup-attempts", description =
            "max number of attempts to make to setup tungsten")
    private int mMaxSetupAttempts = 3;

    private IBuildInfo mBuildInfo = null;

    private ITestDevice mTungstenDevice;

    private IConfiguration mConfig;

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
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(getDevice());

        TungstenSetup setupHelper = createSetupHelper();

        ITestDevice phoneDevice = setupHelper.allocatePhone();
        mTungstenDevice = getDevice();
        try {
            setupHelper.preparePhone(mBuildInfo, phoneDevice);
            performTungstenSetup(setupHelper, listener, phoneDevice, mTungstenDevice);
            performMusicSetup(setupHelper, listener, phoneDevice, mTungstenDevice);
            // do a switcherroo - tell super class to test phone not tungsten
            setDevice(phoneDevice);
            super.run(listener);
        } finally {
            setupHelper.reportPhoneLog(listener, phoneDevice);
            setupHelper.freePhoneDevice(phoneDevice);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BugreportItem takeBugreport(ITestInvocationListener listener, String bugreportName) {
        super.takeBugreport(listener, String.format("%s%s", PHONE_PREFIX, bugreportName));

        // Swap devices to take a bugreport from tungsten
        ITestDevice phoneDevice = getDevice();
        try {
            setDevice(mTungstenDevice);
            return super.takeBugreport(listener,
                    String.format("%s%s", DEVICE_PREFIX, bugreportName));
        } finally {
            setDevice(phoneDevice);
        }
    }

    private void performMusicSetup(TungstenSetup setupHelper, ITestInvocationListener listener,
            ITestDevice phoneDevice, ITestDevice tungstenDevice)
            throws DeviceNotAvailableException {
        for (int i = 1; i <= mMaxSetupAttempts; i++) {
            if (setupHelper.performMusicSetup(i, listener, phoneDevice, tungstenDevice)) {
                return;
            }
        }
        Assert.fail(String.format("Failed to setup music tungsten after %d attempts",
                mMaxSetupAttempts));
    }

    private void performTungstenSetup(TungstenSetup setupHelper, ITestInvocationListener listener,
            ITestDevice phoneDevice, ITestDevice tungstenDevice)
                    throws DeviceNotAvailableException {
        for (int i=1; i <= mMaxSetupAttempts; i++) {
            if (setupHelper.performSetup(i, listener, phoneDevice, tungstenDevice) > 0) {
                return;
            }
        }
        Assert.fail(String.format("Failed to setup tungsten after %d attempts",
                mMaxSetupAttempts));
    }

    private TungstenSetup createSetupHelper() {
        Assert.assertNotNull(mConfig);
        try {
            return TungstenSetup.loadSetup(mConfig);
        } catch (ConfigurationException e) {
            Assert.fail(e.toString());
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }
}
