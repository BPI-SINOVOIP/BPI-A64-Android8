// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.automotive.tests;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.InstrumentationTest;


/**
 * Runs HeadUnit instrumentation test cases.
 * Currently includes Audio, Sensor, Navigation, and Input tests. (more to come)
 */
public class HeadUnitTest implements IRemoteTest, IDeviceTest {
    ITestDevice mDevice = null;

    @Option(name = "test-package-name", mandatory = true,
            description = "The name of the test package to run.")
    private String mTestPackageName = null;

    @Option(name = "test-runner-name", mandatory = true,
            description = "The name of the test runner to use.")
    private String mTestRunnerName = null;

    @Option(name = "ru-key", description = "The name of the reporting unit to use. " +
            "Default is HeadUnitTest")
    private String mRuKey = "HeadUnitTest";

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {

        InstrumentationTest instr = new InstrumentationTest();
        instr.setDevice(mDevice);
        instr.setPackageName(mTestPackageName);
        instr.setRunnerName(mTestRunnerName);
        instr.run(listener);
        listener.testRunStarted(mRuKey, 0);
    }

}
