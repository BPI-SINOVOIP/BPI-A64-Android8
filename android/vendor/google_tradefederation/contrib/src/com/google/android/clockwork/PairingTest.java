package com.google.android.clockwork;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.BluetoothUtils;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.util.Collections;

// TODO: Refactor this class to be the base class of Pairing Stress
public class PairingTest extends PairingStress implements IBuildReceiver {
    protected ITestDevice mPhoneDevice;
    protected ITestDevice mWatchDevice;
    private String mWatchBluetoothMac;
    private IBuildInfo mBuildInfo;

    private static final String START_PAIRING_ON_WATCH =
            "am broadcast -a com.google.android.clockwork.action.START_PAIRING";
    private static final String PHONE_PAIR_CMD =
            "am start -n "
                    + "com.google.android.wearable.app/"
                    + "com.google.android.clockwork.companion.StatusActivity -e EXTRA_AUTO_PAIR \"%s\"";
    private static final String CLOUDSYNC_CMD = " --ez EXTRA_OPT_INTO_CLOUD_SYNC true";
    private static final String TEST_RUN_NAME = "PairingSetup";
    private static final long PAIRING_FINISH_WAIT = 180000; // 3min

    @Option(name = "phone-primary", description = "Is phone the primary device?", mandatory = true)
    private boolean mPhonePrimary = true;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        TestIdentifier id = new TestIdentifier(getClass().getCanonicalName(), TEST_RUN_NAME);
        listener.testRunStarted(TEST_RUN_NAME, 0);
        listener.testStarted(id);

        long startTime = System.currentTimeMillis();

        // Make sure Watch has root
        mWatchDevice.enableAdbRoot();
        mWatchDevice.executeShellCommand("svc power stayon true");

        // Initial setup
        mWatchBluetoothMac = BluetoothUtils.getBluetoothMac(mWatchDevice);
        Assert.assertNotNull("no bluetooth device address detected", mWatchBluetoothMac);
        Assert.assertTrue(
                "failed to enable bluetooth on companion", BluetoothUtils.enable(mPhoneDevice));

        String phonePairingCmd = String.format(PHONE_PAIR_CMD, mWatchBluetoothMac);
        if (mCloudsync) {
            phonePairingCmd += CLOUDSYNC_CMD;
        }

        CLog.d("Starting pairing commands");
        // start pairing on clockwork with default locale
        mWatchDevice.executeShellCommand(START_PAIRING_ON_WATCH);
        // disable pairing screens
        disablePairingScreens(mWatchDevice);
        RunUtil.getDefault().sleep(5000);
        // Issue pair command to companion
        mPhoneDevice.executeShellCommand(phonePairingCmd);
        // Press pair button as needed
        String model = getModelName(mWatchDevice);
        String pairingUiCmd =
                String.format(
                        "am instrument -w -r -e model %s -e class %s.%s %s/%s",
                        model,
                        UI_AUTOMATION_PKG_NAME,
                        UI_AUTOMATION_METHOD_NAME,
                        UI_AUTOMATION_PKG_NAME,
                        UI_AUTOMATION_RUNNER_NAME);
        mPhoneDevice.executeShellCommand(pairingUiCmd);

        long pollStart = System.currentTimeMillis();
        boolean pairingDone = false;
        while (System.currentTimeMillis() < pollStart + mMaxPairingTimeout * 1000 && !pairingDone) {
            pairingDone = validatePairing(mWatchDevice);
            RunUtil.getDefault().sleep(PAIRING_VALIDATE_INTERVAL * 1000);
        }

        if (mWatchDevice.getApiLevel() < 24) {
            mWatchDevice.executeShellCommand(DISABLE_TUTORIAL_E);
        } else {
            mWatchDevice.executeShellCommand(DISABLE_TUTORIAL_F);
        }

        if (!pairingDone) {
            listener.testFailed(id, "Timeout on clockwork");
            listener.invocationFailed(new Throwable("Pairing failure"));
            listener.invocationEnded(0);

            // Throw RunTimeException to stop invocation if pairing was not completed
            throw new RuntimeException("Pairing failure");
        }
        RunUtil.getDefault().sleep(PAIRING_FINISH_WAIT);

        listener.testEnded(id, Collections.<String, String>emptyMap());
        listener.testRunEnded(
                System.currentTimeMillis() - startTime, Collections.<String, String>emptyMap());
    }

    @Override
    public void setDevice(ITestDevice device) {
        if (mPhonePrimary) {
            mPhoneDevice = device;
            mWatchDevice = getCompanion();
        }
    }

    @Override
    public ITestDevice getDevice() {
        return mPhoneDevice;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }
}
