// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.android.apps.plus;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import org.junit.Assert;

/**
 * Run the Es contacts sync performanct test.
 */
public class EsContactsSyncPerfTest implements IDeviceTest, IRemoteTest {

    private ITestDevice mTestDevice = null;

    // Define instrumentation test package and runner.
    private static final String TEST_PACKAGE_NAME = "com.google.android.apps.plus.test";
    private static final String TEST_RUNNER_NAME = ".EsContactsTestRunner";

    @Option(name = "contacts",
            description = "Total number of contacts of the test g+ account.",
            importance = Importance.IF_UNSET)
    private int mContacts = 0;

    @Option(name = "circles",
            description = "Total number of default visible circles of the test g+ account.",
            importance = Importance.IF_UNSET)
    private int mCircles = 0;

    @Option(name = "account-name",
            description = "G+ account under test.",
            importance = Importance.IF_UNSET)
    private String mAccount = null;

    /**
     * Run the Es contacts sync test
     */
    @Override
    public void run(ITestInvocationListener standardListener)
            throws DeviceNotAvailableException {
        if (mTestDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }
        if (mAccount == null) {
            throw new IllegalArgumentException("G+ test account has not been set");
        }
        if (mContacts == 0) {
            throw new IllegalArgumentException("Total number contacts has not been set");
        }
        if (mCircles == 0) {
            throw new IllegalArgumentException("Default number of visible circles has not been set");
        }

        CLog.d("mAccount %s", mAccount);
        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                TEST_PACKAGE_NAME, TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.addInstrumentationArg("contacts", Integer.toString(mContacts));
        runner.addInstrumentationArg("circles", Integer.toString(mCircles));
        runner.addInstrumentationArg("account", mAccount);

        Assert.assertTrue(mTestDevice.runInstrumentationTests(runner, standardListener));
    }

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }
}
