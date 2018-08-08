/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.power.tests;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.companion.CompanionDeviceTracker;
import com.android.tradefed.testtype.InstrumentationTest;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BatteryDrainPairingTestRunner extends BatteryDrainTestRunner {
    private ITestDevice mCompanionDevice;
    private static final String PAIRED_DEVICE_BUGREPORT = "paired_device_bugreport";
    private static final long MAX_DRAIN_DURATION_SECS = 3600 * 24 * 2; // 48 hrs

    // Options for the instrumentation test to be run on companion.
    @Option(name = "run-companion-instrumentation",
            description = "Run an instrumentation test on companion")
    private boolean mRunCompanionInstrumentationTest = false;

    @Option(name = "companion_test_method",
            description = "Test method name. Will be ignored if multiple classes are specified")
    private String mCompanionTestMethod = "";

    @Option(name = "companion_test_class",
            description = "Instrumentation power test class name. Support multiple classes.")
    private String mCompanionTestClass = "";

    @Option(name = "companion_test_package",
            description = "Instrumentation power test package name")
    private String mCompanionTestPackage = "com.android.testing.platform.powertests";

    @Option(name = "companion_test_runner",
            description = "Instrumentation power test runner name")
    private String mCompanionTestRunner = "android.test.InstrumentationTestRunner";

    @Option(name = "companion_test_arguments",
            description = "Test package arguments")
    private Map<String, String> mCompanionTestArguments = new HashMap<>();

    // Helper thread class to start instrumentation test on companion
    private class CompanionInstrTestThread extends Thread {
        private long mSoakTimeInMs = 0;
        private InstrumentationTest instrTest;

        public CompanionInstrTestThread(String packageName, String testRunnerName,
                String className, String methodName, long soakTimeInSecs) {
            mSoakTimeInMs = soakTimeInSecs * 1000;

            instrTest = new InstrumentationTest();
            instrTest.setDevice(mCompanionDevice);
            instrTest.setPackageName(packageName);
            instrTest.setRunnerName(testRunnerName);
            instrTest.setClassName(String.format("%s.%s", packageName, className));
            instrTest.setMethodName(methodName);
            instrTest.setTestTimeout((int) (mDrainDuration * 1000));
            instrTest.setShellTimeout((int) (mDrainDuration * 1000));
            instrTest.setLogcatOnFailure(true);
            instrTest.setRerunMode(false);
            instrTest.setRunName(String.format("%s-%s", className, methodName));
            for (String compTestArguments : mCompanionTestArguments.keySet()) {
                instrTest.addInstrumentationArg(compTestArguments,
                        mCompanionTestArguments.get(compTestArguments));
            }
        }

        @Override
        public void run() {
            try {
                CLog.d("Companion thread created.");
                Thread.sleep(mSoakTimeInMs);

                CLog.d(String.format("Companion instrumentation test %s thread started.",
                        instrTest.getRunName()));
                instrTest.run(mListener);
            } catch (InterruptedException e) {
                CLog.d("Companion thread interrupted.");
            } catch (DeviceNotAvailableException e) {
                CLog.d("Companion device not available");
            }
        }
    }

    @Override
    protected void setUp() throws DeviceNotAvailableException {
        if (mDrainDuration > MAX_DRAIN_DURATION_SECS) {
            Assert.fail("Cannot run drain test longer than 48 hrs");
        }
        super.setUp();
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // Use a list to allow scheduling other type of companion activities in the future
        ArrayList<Thread> companionThreadList = new ArrayList<>();
        mCompanionDevice = CompanionDeviceTracker.getInstance().getCompanionDevice(getDevice());

        // Check existence of companion device
        if (mCompanionDevice == null) {
            throw new RuntimeException("No companion device is allocated");
        }

        // Create companion instrumentation test thread
        if (mRunCompanionInstrumentationTest) {
            companionThreadList.add(new CompanionInstrTestThread(mCompanionTestPackage,
                    mCompanionTestRunner, mCompanionTestClass, mCompanionTestMethod,
                    mDeviceStablizeTimeSecs));
        }

        // Start threads for companion device
        for (Thread companionThread : companionThreadList) {
            companionThread.start();
        }

        try {
            super.run(listener);
        } finally {
            // Stop threads for companion device
            for (Thread companionThread : companionThreadList) {
                try {
                    if (companionThread instanceof CompanionInstrTestThread) {
                        CompanionInstrTestThread t = (CompanionInstrTestThread) companionThread;
                        t.join();
                    }
                } catch (InterruptedException e) {
                    CLog.d("Companion thread interrupted.");
                }
            }
        }

        PowerAnalyzer.postBugreportFromDevice(mCompanionDevice, listener, PAIRED_DEVICE_BUGREPORT);
    }
}
