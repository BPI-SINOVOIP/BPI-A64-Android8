// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.TargetSetupError;

import java.util.concurrent.Semaphore;

/**
 * This is a preparer used to use token to serialize test excution in tradefed host.
 * only the device acquire token will be allow to start the test. Others will wait until it released
 * This can't be only used when you have one test in tradefed and use shared resources.
 * Please make sure only a single test running on the host with different DUTs
 * User need to add --semaphore-token:no-disable in the command file.
 * FIXME, once the new ATP is online, we might be able to swtich to it and remove this preparer.
*/
@OptionClass(alias = "semaphore-token")
public class SemaphoreTokenTargetPreparer implements ITargetCleaner {
    @Option(name = "disable", description = "Disable this preparer")
    private boolean mDisable = true;
    private boolean mTokenAcquired = true;
    static final Semaphore mRunToken = new Semaphore(1);
    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException {
        if (mDisable) {
            CLog.v("Semaphore preparer is disabled");
            return;
        }
        try {
            CLog.v("Waiting to acquire run token");
            mRunToken.acquire();
            mTokenAcquired = true;
            CLog.v("Token acquired");
        } catch (InterruptedException e) {
            mTokenAcquired = false;
            CLog.e(e);
            CLog.e("Interrupted error during token acquire");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mDisable) {
            CLog.v("Semaphore preparer is disabled");
            return;
        }
        if (mTokenAcquired) {
            CLog.v("Releasing run token");
            mRunToken.drainPermits();
            mRunToken.release();
        } else {
            CLog.v("Did not acquire token, skip releasing run token");
        }
    }
}

