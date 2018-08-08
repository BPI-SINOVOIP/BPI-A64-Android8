// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.athome.tests;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IAppBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IResumableTest;
import com.android.tradefed.testtype.IShardableTest;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Test that runs performs repeated setup operations between a phone controller and an connected
 * tungsten.
 * <p/>
 * Assumes that the phone has been prepared as follows:
 * <ul>
 * <li>phone is already running desired platform build
 * <li>phone either has the desired apks already installed, or they will be passed in within a
 * {@link IAppBuildInfo}.
 * <li>connected to desired wifi network.</li>
 * <li>configured with only one desired account.</li>
 * <li>bluetooth is on.</li>
 * </ul>
 * <p/>
 * The {@link ITestDevice} passed into this test must be the tungsten. It will look for any
 * connected phone (ie non-tungsten), wipe data on the tungsten and phone apks, and then perform
 * setup.
 */
public class SetupTest implements IRemoteTest, IDeviceTest, IBuildReceiver, IShardableTest,
        IResumableTest, IConfigurationReceiver {

    private ITestDevice mDevice;

    @Option(name = "iterations", description = "the number of setup iterations to perform.")
    private int mIterations = 1;

    @Option(name = "run-name", description = "the name used to report the setup metrics.")
    private String mRunName = "aah_setup";

    @Option(name = "shards", description = "Optional number of shards to split test into. "
            + "Iterations will be split evenly among shards.", importance = Importance.IF_UNSET)
    private Integer mShards = null;

    @Option(name = "resume", description = "Resume the run if an device unavailable error "
            + "stopped the previous test run.")
    private boolean mResumeMode = false;

    /** controls if this test should be resumed. Only used if mResumeMode is enabled */
    private boolean mResumable = true;

    private IBuildInfo mBuildInfo = null;

    /** the current iteration position when reporting iteration counts. Used when sharding */
    private int mCurrentIteration = 0;

    /** the number of setup attempts */
    private int mAttemptedIterations = 0;

    /** the number of successful setup attempts */
    private int mSuccessfulIterations = 0;

    /** total setup time for all <var>mSuccessfulIterations</var> in ms. */
    private long mTotalSetupTime = 0;

    private IConfiguration mConfig;

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
    public boolean isResumable() {
        return mResumeMode && mResumable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    void setRunName(String runName) {
        mRunName = runName;
    }

    void setIterations(int iterations, int startIndex) {
        mIterations = iterations;
        mCurrentIteration = startIndex;
    }

    void setResumeMode(boolean resumeMode) {
        mResumeMode = resumeMode;
    }

    void setShards(Integer shards) {
        mShards = shards;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<IRemoteTest> split() {
        if (mShards == null || mShards <= 1) {
            return null;
        }
        try {
            Collection<IRemoteTest> shards = new ArrayList<IRemoteTest>(mShards);
            int remainingIterations = mIterations;
            int iterationStartPos = 0;
            int iterationEndPos = 0;
            for (int i = mShards; i > 0; i--) {
                SetupTest testShard = new SetupTest();
                // device and build will be set by test invoker
                OptionCopier.copyOptions(this, testShard);
                testShard.setShards(null);
                // attempt to divide iterations evenly among shards with no remainder
                int iterationsForShard = remainingIterations / i;
                iterationEndPos += iterationsForShard;
                if (iterationsForShard > 0) {
                    testShard.setIterations(iterationEndPos, iterationStartPos);
                    remainingIterations -= iterationsForShard;
                    iterationStartPos = iterationEndPos;
                    shards.add(testShard);
                }
            }
            return shards;
        } catch (ConfigurationException e) {
            // TODO: consider throwing runtime exception
            CLog.e("Failed to shard: %s", e.getMessage());
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mDevice);

        TungstenSetup setupHelper = createSetupHelper();

        ITestDevice phoneDevice = setupHelper.allocatePhone();
        ITestDevice tungstenDevice = mDevice;

        long startTime = System.currentTimeMillis();
        listener.testRunStarted(mRunName, 0);
        try {
            setupHelper.preparePhone(mBuildInfo, phoneDevice);
            mResumable = false;
            while (mCurrentIteration < mIterations) {
                mCurrentIteration++;
                try {
                    long setupTime = setupHelper.performSetup(mCurrentIteration, listener,
                            phoneDevice, tungstenDevice);
                    if (setupTime > 0) {
                        mSuccessfulIterations++;
                        mTotalSetupTime += setupTime;
                    }
                } catch (DeviceNotAvailableException e) {
                    CLog.logAndDisplay(LogLevel.WARN, "iteration %d: Setup failed: %s",
                            mCurrentIteration, e.toString());
                    // one or more devices are gone - skip taking reports in this case
                    mResumable = true;
                    throw e;
                } finally {
                    mAttemptedIterations++;
                }
            }
        } finally {
            Map<String, String> metrics = new HashMap<String, String>(1);
            setupHelper.reportPhoneLog(listener, phoneDevice);
            String phoneBuild = phoneDevice.getIDevice().getProperty("ro.build.id");
            if (phoneBuild != null) {
                metrics.put("phone_build", phoneBuild);
            }
            setupHelper.freePhoneDevice(phoneDevice);
            long durationMs = System.currentTimeMillis() - startTime;
            metrics.put("iterations", Integer.toString(mSuccessfulIterations));
            metrics.put("attempted_iterations", Integer.toString(mAttemptedIterations));
            metrics.put("total_setup_time", Long.toString(mTotalSetupTime/1000L));
            String buildVer = TungstenSetup.getPhoneApkVersion(mBuildInfo);
            if (buildVer != null) {
                // TODO: this is ugly, but ensure buildVer is not treated as an integer because
                // reporter may sum its value in multiple/sharded runs cases
                metrics.put("apk_version", String.format("b_%s", buildVer));
            }

            listener.testRunEnded(durationMs, metrics);
        }
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
