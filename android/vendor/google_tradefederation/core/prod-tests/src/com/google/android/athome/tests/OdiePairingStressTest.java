// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.athome.tests;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.StreamUtil;

import org.junit.Assert;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * Runs Odie pairing stress tests.
 */
public class OdiePairingStressTest implements IRemoteTest, IDeviceTest, IBuildReceiver {
    private static final String ODIE_PAIRING_TEST_PACKAGE = "com.google.android.test.setupwraith";
    private static final String ODIE_PAIRING_TEST_CLASS_NAME =
            "com.google.android.test.setupwraith.PairingMonitorTest";
    private static final String ODIE_PAIRING_TEST_METHOD_NAME = "testPairing";
    private static final int PAIRING_TIMEOUT_MILLIS = 5 * 60 * 1000;  // 5 minutes
    private static final String SETUP_ITERATIONS = "setup_iterations";
    private static final String ODIE_RELAY_COMMAND = "python %s -c %s -e %s";
    private final static String RESET_ODIE_COMMAND =
            "am start -n com.google.android.aah.odietool/.AahOdieTool " +
            "--ez wipe_requested true --es bluetooth_address %s";
    private final static String RELAY_POWER_ON = "REL2.ON";
    private final static String RELAY_POWER_OFF = "REL2.OFF";
    private final static String RELAY_HOME_ON = "REL3.ON";
    private final static String RELAY_HOME_OFF = "REL3.OFF";
    private final static String RELAY_BACK_ON = "REL1.ON";
    private final static String RELAY_BACK_OFF = "REL1.OFF";
    private final static String RELAY_ALL_ON = "RELS.ON";
    private final static String RELAY_ALL_OFF = "RELS.OFF";
    private static final int RELAY_FORCE_PAIRING_MILLIS = 5 * 1000;
    private static final int RELAY_POWER_ON_MILLIS = 2 * 1000;

    @Option(name="target-iterations",
            description="The target number of iterations to run the odie pairing test")
    private int mTargetIterations = 100;

    @Option(name="failure-reset-threshold",
            description="Number of consecutive failures before forcing device into pairing mode")
    private int mFailureThreshold = 1;

    @Option(name="odie-mac-address",
            description="The MAC address of the Odie to pair with", mandatory = true)
    private String mOdieMacAddress;

    @Option(name="odie-whitelist-string",
            description="The string to use in the Odie whitelist file", mandatory = true)
    private String mOdieWhitelist;

    @Option(name="odie-test-apk",
            description="The name of the odie pairing test apk", mandatory = true)
    private String mOdieTestApkFilename;

    @Option(name="odie-mac-address-filepath",
            description="The path to push the whitelisted Odie MAC address")
    private String mOdieMacFilename = "odie-white-list";

    @Option(name="relay-controller-binary",
            description="The absolute path to the relay controller script", mandatory = true)
    private String mRelayControllerBinary;

    @Option(name="relay-fs-device",
            description="The fs device of the relay", mandatory = true)
    private String mRelayFSDevice;

    private ITestDevice mDevice;
    private IBuildInfo mBuildInfo;
    private int mFailures = 0;
    private InputStreamSource mLastBugreport = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        int currentIteration = 0;
        int successfulIterations = 0;

        long startTime = System.currentTimeMillis();
        listener.testRunStarted("odie_setup", 0);
        while (currentIteration++ < mTargetIterations) {
            CLog.i(String.format("----Starting iteration %d----", currentIteration));

            CLog.i("Wiping molly");
            wipeMolly();

            CLog.i("Pushing Odie whitelist file");
            pushOdieMacWhitelist();

            CLog.i("Rebooting Molly once more...");
            mDevice.reboot();
            mDevice.waitForDeviceAvailable();

            CLog.i("Turning on Odie");
            turnOnOdie();

            CLog.i("Starting remote instrumentation test");
            InstrumentationTest instrTest = new InstrumentationTest();
            instrTest.setDevice(mDevice);
            instrTest.setPackageName(ODIE_PAIRING_TEST_PACKAGE);
            instrTest.setClassName(ODIE_PAIRING_TEST_CLASS_NAME);
            instrTest.setMethodName(ODIE_PAIRING_TEST_METHOD_NAME);
            instrTest.setShellTimeout(PAIRING_TIMEOUT_MILLIS);
            CollectingTestListener testListener = new CollectingTestListener();
            instrTest.run(testListener);
            if (testListener.hasFailedTests()) {
                CLog.e(String.format("Test iteration %d failed.", currentIteration));
                dumpBugreport(currentIteration, listener);
                mFailures++;
            } else {
                CLog.e(String.format("Test iteration %d passed.", currentIteration));
                successfulIterations++;
                mFailures = 0;
            }
            resetOdiePairing();
            stashCurrentBugreport();
        }

        CLog.i(String.format("Finished with %d successful iterations", successfulIterations));

        cleanupLastBugreport();
        dumpLogcat(listener);
        Map<String, String> metrics = new HashMap<String, String>();
        metrics.put("success_iterations", Integer.toString(successfulIterations));
        long totalTime = System.currentTimeMillis() - startTime;
        listener.testRunEnded(totalTime, metrics);
    }

    /**
     * Cleans up the last bugreport
     */
    private void cleanupLastBugreport() {
        StreamUtil.cancel(mLastBugreport);
        mLastBugreport = null;
    }

    /**
     * Stashes the current bugreport
     *
     * @throws DeviceNotAvailableException
     */
    private void stashCurrentBugreport() throws DeviceNotAvailableException {
        cleanupLastBugreport();
        CLog.w("Stashing current bugreport");
        mLastBugreport = mDevice.getBugreport();
    }

    /**
     * Resets the Odie pairing between device and Odie
     *
     * @throws DeviceNotAvailableException
     */
    private void resetOdiePairing() throws DeviceNotAvailableException {
        String cmd = String.format(RESET_ODIE_COMMAND, mOdieMacAddress);
        mDevice.executeShellCommand(cmd);

        try {
            Thread.sleep(10 * 1000);  // Pause for 10 seconds for unpairing
        } catch (InterruptedException e) {
            // ignore
        }
    }

    /**
     * Dumps the device logcat to the listener
     *
     * @param listener The listener to attach the final logcat to
     */
    void dumpLogcat(ITestInvocationListener listener) {
        InputStreamSource logcatStream = mDevice.getLogcat();
        try {
            listener.testLog("final_logcat", LogDataType.LOGCAT, logcatStream);
        } finally {
            StreamUtil.cancel(logcatStream);
        }
    }

    /**
     * Dumps the device bugreport to the listener
     *
     * @param currentIteration The current iteration
     * @param listener The listener to attach the bugreport dump to
     */
    void dumpBugreport(int currentIteration, ITestInvocationListener listener) {
        CLog.w("Pairing failed on iteration %d", currentIteration);
        try (InputStreamSource bugStream = mDevice.getBugreport()) {
            listener.testLog(String.format("iteration_%d_bugreport", currentIteration),
                    LogDataType.BUGREPORT, bugStream);

            // If we have a last bugreport, upload those logs as well
            if (mLastBugreport != null) {
                listener.testLog(String.format("stashed_bugreport_iteration_%d",
                        currentIteration-1), LogDataType.BUGREPORT, mLastBugreport);
            }
        }
    }

    /**
     * Turns on the Odie controller
     *
     * Only turns on Odie; assumes the Odie is already unpaired, will not
     * reset Odie pairing information.
     */
    private void turnOnOdie() {
        CLog.i("Attempting to turn on Odie...");
        try {
            if (mFailures >= mFailureThreshold) {
                // If we hit the threshold of consecutive failures, the test may have gotten out
                // of sync. So force the Odie into pairing mode by simultaneously pressing
                // Home + Back buttons for 5 seconds
                CLog.i("Failure threshold hit. Attempting to force Odie into pairing mode...");

                String homeOn = String.format(ODIE_RELAY_COMMAND, mRelayControllerBinary,
                        mRelayFSDevice, RELAY_HOME_ON);
                CLog.i(String.format("Odie Home on command: %s", homeOn));
                Runtime.getRuntime().exec(homeOn).waitFor();

                String backOn = String.format(ODIE_RELAY_COMMAND, mRelayControllerBinary,
                        mRelayFSDevice, RELAY_BACK_ON);
                CLog.i(String.format("Odie Back on command: %s", backOn));
                Runtime.getRuntime().exec(backOn).waitFor();

                // Leave relay closed for 5 seconds
                try {
                    Thread.sleep(RELAY_FORCE_PAIRING_MILLIS);
                } catch (InterruptedException e) {
                    // ignore
                }
            } else {
                String powerOn = String.format(ODIE_RELAY_COMMAND, mRelayControllerBinary,
                        mRelayFSDevice, RELAY_POWER_ON);
                CLog.i(String.format("Odie Power on command: %s", powerOn));
                Runtime.getRuntime().exec(powerOn).waitFor();

                // Leave relay closed for 2 seconds
                try {
                    Thread.sleep(RELAY_POWER_ON_MILLIS);
                } catch (InterruptedException e) {
                    // ignore
                }
            }

            // Turn off Odie relay
            String relayOffCmd = String.format(ODIE_RELAY_COMMAND, mRelayControllerBinary,
                    mRelayFSDevice, RELAY_ALL_OFF);
            CLog.i(String.format("odie off command: %s", relayOffCmd));
            Runtime.getRuntime().exec(relayOffCmd).waitFor();

        } catch (SecurityException | IOException | NullPointerException |
                IllegalArgumentException | InterruptedException e) {
            Assert.fail("Exception while trying to power on Odie into pairing mode.");
        }
        // Give a second for Odie relay to settle
        try {
            Thread.sleep(1 * 1000);
        } catch (Exception e) {
            // ignore
        }

        CLog.i("Odie now on, starting test...");
    }

    /**
     * Wipes Molly
     *
     * @throws DeviceNotAvailableException
     */
    private void wipeMolly() throws DeviceNotAvailableException {
        mDevice.rebootIntoBootloader();
        CommandResult result = mDevice.executeFastbootCommand("oem", "recovery:wipe_data");
        if (result.getStatus().equals(CommandStatus.SUCCESS)) {
            mDevice.waitForDeviceAvailable();
            setUpMolly();
        }
    }

    /**
     * Performs setup on a wiped Molly
     *
     * Setup only includes installing the Odie test apk to the device.
     * Does not use GoogleDeviceSetup since we do not want ro.test_harness set (as this will
     * bypass the setup screen, which we actually want)
     *
     * @throws DeviceNotAvailableException
     */
    private void setUpMolly() throws DeviceNotAvailableException {
        try {
            TestAppInstallSetup appInstaller = new TestAppInstallSetup();
            appInstaller.addTestFileName(mOdieTestApkFilename);
            appInstaller.setUp(mDevice, mBuildInfo);
        } catch (TargetSetupError e) {
            CLog.e(e);
            Assert.fail("failed to setup molly");
        }
    }

    /**
     * Pushes an MAC whitelist file to the Molly enabling it to pair to listed Odies
     *
     * @throws DeviceNotAvailableException
     */
    private void pushOdieMacWhitelist() throws DeviceNotAvailableException {
        String externalStorage = mDevice.getIDevice().getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        String odieMacAddressFullPath = String.format("%s/%s", externalStorage, mOdieMacFilename);
        String cmd = String.format("echo '%s' > %s", mOdieWhitelist, odieMacAddressFullPath);
        mDevice.executeShellCommand(cmd);
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
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }
}
