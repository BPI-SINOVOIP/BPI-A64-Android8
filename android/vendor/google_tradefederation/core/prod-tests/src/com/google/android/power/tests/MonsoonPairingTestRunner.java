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
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.companion.CompanionDeviceTracker;
import com.android.tradefed.testtype.InstrumentationTest;

import com.google.android.tradefed.targetprep.GoogleDeviceSetup;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Power test runner for using Monsoon with paired companion device
 * 1) Start threads for controlling companion device
 * 2) Run setup to check monsoon status and run extra pre-condition setups on device
 * 3) Start instrumentation test on device
 * 4) Start the monsoon power data collection
 * 5) Run data analysis
 * 6) Test clean up
 * 7) Stop threads for controlling companion device
 */
public class MonsoonPairingTestRunner extends MonsoonTestRunner {

    @Option(name = "send-notifications-low",
            description = "Enable sending low priority notifications from companion")
    private boolean mSendNotificationsLow = false;

    @Option(name = "send-notifications-high",
            description = "Enable sending high priority notifications from companion")
    private boolean mSendNotificationsHigh = false;

    @Option(name = "notification-low-interval",
            description = "Delay between sending each low priority notification in seconds; "
                    + "Defaults to 5 mins")
    private long mNotificationsLowInterval = 5 * 60;

    @Option(name = "notification-high-interval",
            description = "Delay between sending each high priority notifications in seconds; "
                    + "Defaults to 5 mins")
    private long mNotificationsHighInterval = 5 * 60;

    private static final String COMPANION_NOTIFICATION_LOW_INSTR = " am instrument -w -r "
            + "-e class android.test.clockwork.notification.NotificationSender"
            + "#testSendQuickNotification -e title @TEST@NOTIFICATION@%d "
            + "android.test.clockwork.notification/android.test.InstrumentationTestRunner";

    private static final String COMPANION_NOTIFICATION_HIGH_INSTR = " am instrument -w -r "
            + "-e class android.test.clockwork.notification.NotificationSender"
            + "#testSendHighPriorityNotification -e title @TEST@NOTIFICATION@%d "
            + "android.test.clockwork.notification/android.test.InstrumentationTestRunner";

    // Options for the instrumentation test to be run on companion.
    @Option(name = "run-companion-instrumentation",
            description = "Run an instrumentation test on companion")
    private boolean mRunCompanionInstrumentationTest = false;

    @Option(name = "companion_test_method",
            description = "Test method name. Will be ignored if multiple classes are specified")
    private String mCompanionTestMethod = "";

    @Option(name = "companion_test_class",
            description = "Companion instrumentation test class name")
    private String mCompanionTestClass = "";

    @Option(name = "companion_test_package",
            description = "Companion instrumentation test package name")
    private String mCompanionTestPackage = "com.android.testing.platform.powertests";

    @Option(name = "companion_test_runner",
            description = "Companion instrumentation test runner name")
    private String mCompanionTestRunner = "android.test.InstrumentationTestRunner";

    @Option(name = "companion_test_arguments",
            description = "Companion instrumentation test package arguments")
    private Map<String, String> mCompanionTestArguments = new HashMap<String, String>();

    @Option(name = "companion_test_timeout",
            description = "Companion instrumentation test timeout in seconds")
    private int mCompanionTestTimeoutSec = 0; //no timeout by default

    @Option(name = "run-companion-device-setup",
            description = "Run device setup on companion")
    private boolean mRunCompanionDeviceSetup = false;

    @Option(name = "companion-device-setup",
            description = "Device setups to be run on companion")
    private Map<String, String> mCompanionDeviceSetup = new HashMap<String, String>();

    @Option(
        name = "companion-device-reboot",
        description = "Reboot companion device before running tests"
    )
    private boolean mCompanionDeviceReboot = false;

    // Option to overwrite --companion-serial
    @Option(
        name = "overwrite-companion-serial",
        description =
                "overwrite companion serial with the serial number passed "
                        + "through --companion-serial"
    )
    private boolean mOverwriteCompanionSerial = false;

    private static final String NOTIFICATION_LOW_NAME = "Low Priority Notification";
    private static final String NOTIFICATION_HIGH_NAME = "High Priority Notification";
    private static final String PAIRED_DEVICE_BUGREPORT = "paired_device_bugreport";

    private static final String SINGLE_ITR_TEST_SUCCESS_OUTPUT = "OK (1 test)";

    private ITestDevice mCompanionDevice;

    // Helper thread class to send notifications from companion
    private class CompanionCommandThread extends Thread {
        private String mThreadName;
        private String mCompanionCommand;
        private boolean mKeepRunning = true;
        private long mIntervalInMs = 0;
        private long mSoakTimeInMs = 0;
        private boolean mAllSuccess = true;
        private boolean mOneShot = false;

        public CompanionCommandThread(String threadName, String companionCommand,
                long soakTimeInSecs, long intervalInSecs, boolean oneshot) {
            mThreadName = threadName;
            mCompanionCommand = companionCommand;
            mSoakTimeInMs = soakTimeInSecs * 1000;
            mIntervalInMs = intervalInSecs * 1000;
            mOneShot = oneshot;
        }

        public void end() {
            mKeepRunning = false;
        }

        public boolean allSuccess() {
            return mAllSuccess;
        }

        // Launch an intent on companion to send notification
        private boolean executeCommand(int iteration) throws DeviceNotAvailableException {
            String buffer = "";
            buffer = mCompanionDevice
                    .executeShellCommand(String.format(mCompanionCommand, iteration));
            if (buffer.indexOf(SINGLE_ITR_TEST_SUCCESS_OUTPUT) > 0) {
                CLog.d(String.format("Command %d ran successfully", iteration));
                return true;
            }
            CLog.d(String.format("Command %d failed to run ", iteration));
            return false;
        }

        @Override
        public void run() {
            try {
                int iteration = 0;
                boolean cmdSuccess = true;

                CLog.d(String.format("Companion thread [%s] started.", mThreadName));
                Thread.sleep(mSoakTimeInMs);
                while (mKeepRunning && (mOneShot ? (iteration < 1) : true)) {
                    Thread.sleep(mIntervalInMs);
                    cmdSuccess = executeCommand(iteration);
                    iteration++;
                    if (!cmdSuccess) {
                        mAllSuccess = false;
                    }
                }
            } catch (InterruptedException e) {
                CLog.d("Companion thread interrupted.");
            } catch (DeviceNotAvailableException e) {
                CLog.d("Companion device not available, ending companion thread.");
            } finally {
                CLog.d(String.format("Companion thread [%s] ended.", mThreadName));
                end();
            }
        }
    }

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
            instrTest.setTestTimeout(mCompanionTestTimeoutSec * 1000);
            instrTest.setShellTimeout(mCompanionTestTimeoutSec * 1000);
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
                CLog.d(String.format("Companion instrumentation test %s started.",
                        instrTest.getRunName()));
                Thread.sleep(mSoakTimeInMs);

                instrTest.run(mListener);
            } catch (InterruptedException e) {
                CLog.d("Companion thread interrupted.");
            } catch (DeviceNotAvailableException e) {
                CLog.d("Companion device not available");
            }
        }
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // Use a list to allow scheduling other type of companion activities in the future
        ArrayList<Thread> companionThreadList = new ArrayList<Thread>();
        mCompanionDevice = getCompanionDevice();

        // Overwrite companion serial number
        if (mOverwriteCompanionSerial) {
            mCompanionDevice = CompanionDeviceTracker.getInstance().getCompanionDevice(getDevice());
        }

        // Check existence of companion device
        if (mCompanionDevice == null) {
            throw new RuntimeException("No companion device is allocated");
        }

        if (mRunCompanionDeviceSetup) {
            runCompanionDeviceSetup();
        }

        if (mCompanionDeviceReboot) {
            mCompanionDevice.reboot();
        }

        // Create low priority notification thread
        if (mSendNotificationsLow) {
            companionThreadList.add(new CompanionCommandThread(NOTIFICATION_LOW_NAME,
                    COMPANION_NOTIFICATION_LOW_INSTR, mDeviceStablizeTimeSecs,
                    mNotificationsLowInterval, false));
        }

        // Create high priority notification thread
        if (mSendNotificationsHigh) {
            companionThreadList.add(new CompanionCommandThread(NOTIFICATION_HIGH_NAME,
                    COMPANION_NOTIFICATION_HIGH_INSTR, mDeviceStablizeTimeSecs,
                    mNotificationsHighInterval, false));
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
                    if (companionThread instanceof CompanionCommandThread) {
                        CompanionCommandThread t = (CompanionCommandThread) companionThread;
                        t.end();
                        t.join();
                        if (!t.allSuccess()) {
                            Assert.fail("One or more command failed to run on companion");
                        }
                    }

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

    /**
     * Run device setup on companion
     */
    private void runCompanionDeviceSetup() {
        try {
            GoogleDeviceSetup deviceSetup = new GoogleDeviceSetup();
            OptionSetter setter = new OptionSetter(deviceSetup);

            // Skip setting any system properties as that will force a device reboot
            // System properties should be set with CompanionDeviceSetup preparer in test configs
            setter.setOptionValue("force-skip-system-props", "true");

            for (String setup : mCompanionDeviceSetup.keySet()) {
                setter.setOptionValue(setup, mCompanionDeviceSetup.get(setup));
            }
            deviceSetup.setUp(mCompanionDevice, getBuildInfo());
        } catch (Exception e) {
            CLog.d("Unable to run device setup on companion");
        }
    }
}
