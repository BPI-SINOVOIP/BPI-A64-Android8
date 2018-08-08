// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.ddmlib.EmulatorConsole;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.DeviceFailedToBootError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A {@link ITargetPreparer} that launches an emulator using crow. (go/crow)
 */
@OptionClass(alias = "crow-preparer")
public class CrowEmulatorPreparer implements ITargetPreparer, ITargetCleaner {

    // Absolute path of the crow binary used.
    private static final String CROW_BINARY = "/google/data/ro/teams/mobile_eng_prod/crow/crow.par";
    // by default when the adb server starts, it binds to local TCP port 5037 and listens for
    // commands sent from adb clients—all adb clients use port 5037 to communicate with the adb server
    private static final String DEFAULT_ABD_SERVER_PORT = "5037";

    @Option(name = "boot-time", description =
            "the maximum time in minutes to wait for emulator to boot.")
    private long mMaxBootTime = 1;

    @Option(name = "window", description = "launch emulator with a graphical window display.")
    private boolean mWindow = false;

    @Option(name = "launch-attempts", description = "max number of attempts to launch emulator")
    private int mLaunchAttempts = 1;

    @Option(name = "audio", description = "launch emulator with audio.")
    private boolean mAudio = false;

    @Option(name = "google-addons", description = "whether or not to use google images.")
    private boolean mGoogleAddOns = true;

    @Option(name = "emulator-device-type", description = "device type to launch.")
    private String mDeviceType = null;

    @Option(name = "api-level", description = "api level to launch.")
    private Integer mApiLevel = null;

    @Option(name = "headless", description = "whether or not the host is running headless.")
    private boolean mHeadless = true;

    @Option(name = "arch", description = "arch to use, currently only support x86 and arm.")
    private String mArch = "x86";

    @Option(name = "add_user", description = "User to be added. Format is user:password."
            + "If password is omitted, user will be prompted for password."
            + "Specify multiple times for multiple accounts")
    private String mAccount = null;

    @Option(name = "new-emulator", description = "launch a new emulator.")
    private boolean mNewEmulator = false;

    @Option(name = "disable", description = "skip this target preparer")
    private boolean mDisable = true;

    private final IRunUtil mRunUtil;
    private IDeviceManager mDeviceManager;
    private File mCrowTmpDir;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        if (mDisable || !mNewEmulator) {
            // Note: If we want to launch the emulator, we need to pass the --new-emulator flag
            // defined in DeviceSelectionOptions, which will create a stub emulator.
            // Also, need to explicitly enable this preparer and disable the LocalSdkAvdPreparer by adding the following
            // local-sdk-preparer:disable and crow-preparer:no-disable
            return;
        }
        try {
            // FIXME: this is a temporary workaround for crow. Crow creates temp dir, where the
            // emulator snapshots are stored, but it does not clean up after itself.
            // Here we specify a temp dir for crow to use and we later delete it during cleanup.
            mCrowTmpDir = FileUtil.createTempDir("CROW");
        } catch (IOException e) {
            throw new TargetSetupError(String.format(
                    "Failed to create tempdir for Crow emulator: %s", e.toString()),
                    device.getDeviceDescriptor());
        }
        launchEmulator(device);
    }

    /**
     * Creates a {@link CrowEmulatorPreparer}.
     */
    public CrowEmulatorPreparer() {
        this(new RunUtil(), null);
    }

    /**
     * Alternate constructor for injecting dependencies.
     *
     * @param runUtil
     * @param deviceManager
     */
    CrowEmulatorPreparer(IRunUtil runUtil, IDeviceManager deviceManager) {
        // declared as package-private so it can be visible for unit testing
        mRunUtil = runUtil;
        mDeviceManager = deviceManager;
    }

    /**
     * Launch Crow emulator and wait for it to become available.
     * Will launch the emulator on the port specified in the allocated {@link ITestDevice}
     *
     * @param device the placeholder {@link ITestDevice} representing allocated emulator device
     * @throws DeviceNotAvailableException
     */
    public void launchEmulator(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError, BuildError {
        if (!device.getDeviceState().equals(TestDeviceState.NOT_AVAILABLE)) {
            CLog.w("Emulator %s is already running, killing", device.getSerialNumber());
            getDeviceManager().killEmulator(device);
        } else if (!device.getIDevice().isEmulator()) {
            throw new TargetSetupError("Invalid stub device, it is not of type emulator",
                    device.getDeviceDescriptor());
        }

        // Set up environmental variable TMPDIR
        mRunUtil.setEnvVariable("TMPDIR", mCrowTmpDir.getAbsolutePath());
        List<String> emulatorArgs = ArrayUtil.list(CROW_BINARY);

        // Crow only supports x86 and arm emulators.
        if ("x86".equals(mArch)) {
            emulatorArgs.add("--arch");
            emulatorArgs.add("x86");
        } else if ("arm".equals(mArch)) {
            emulatorArgs.add("--arch");
            emulatorArgs.add("arm");
        } else {
            throw new TargetSetupError("Invalid arch specified, only x86 and arm are supported",
                    device.getDeviceDescriptor());
        }

        if (mGoogleAddOns) {
            emulatorArgs.add("--google_addons");
        } else {
            emulatorArgs.add("--nogoogle_addons");
        }

        if (mApiLevel != null) {
            emulatorArgs.add("--api");
            emulatorArgs.add(mApiLevel.toString());
        }

        if (mAccount != null) {
            emulatorArgs.add("--add_user");
            emulatorArgs.add(mAccount);
        }

        if (mDeviceType != null) {
            emulatorArgs.add("--device");
            emulatorArgs.add(mDeviceType);
        }

        if (mAudio) {
            emulatorArgs.add("--with_audio");
        } else {
            emulatorArgs.add("--nowith_audio");
        }

        // Ensure the emulator will launch on the same port as the allocated emulator device
        Integer port = EmulatorConsole.getEmulatorPort(device.getSerialNumber());
        if (port == null) {
            // Serial number is not in expected format <type>-<consolePort> as defined by ddmlib
            throw new TargetSetupError(String.format(
                    "Failed to determine emulator port for %s", device.getSerialNumber()),
                    device.getDeviceDescriptor());
        }

        // in Crow, port values can be random as they will take any available free port. We want to
        // force port numbers to be the same as we use when we launch the emulator using sdk tools
        // adb_port         is the adb port for the device
        // emulator_port    is the emulator console port
        // adb_server_port  is the host adb server port used to communicate with the adb server
        emulatorArgs.add("--adb_port");
        Integer adb_port = Integer.valueOf(port.intValue() + 1);
        emulatorArgs.add(adb_port.toString());

        // Crow is a wrapper around unified launcher. In order to pass options to unified launcher
        // that are not available in Crow, we need to precede them with "--".
        // unified launcher options must be kept at the end, we want -- once, and all the flag
        // options to be passed to the unified launcher to follow.
        emulatorArgs.add("--");
        emulatorArgs.add("--adb_server_port");
        emulatorArgs.add(DEFAULT_ABD_SERVER_PORT);
        emulatorArgs.add("--emulator_port");
        emulatorArgs.add(port.toString());
        if (!mWindow || mHeadless) {
            emulatorArgs.add("--noenable_display");
        }
        if (mHeadless) {
            if ("x86".equals(mArch)) {
                emulatorArgs.add("--emulator_x86=/google/src/head/depot/google3/third_party" +
                    "/java/android/android_sdk_linux/tools/emulator-x86.static");
            } else if ("arm".equals(mArch)) {
                emulatorArgs.add("--emulator_arm=/google/src/head/depot/google3/third_party/" +
                        "java/android/android_sdk_linux/tools/emulator-arm.static");
            }
        }
        launchEmulatorWithRelaunch(device, emulatorArgs);
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mDisable || !mNewEmulator) {
            return;
        }
        // Remove temp dir at the end.
        FileUtil.recursiveDelete(mCrowTmpDir);
    }

    private IDeviceManager getDeviceManager() {
        if (mDeviceManager == null) {
            mDeviceManager = GlobalConfiguration.getDeviceManagerInstance();
        }
        return mDeviceManager;
    }

    /**
     * Launch emulator, performing multiple attempts if necessary as specified.
     *
     * @param device  {@link ITestDevice} representing allocated emulator device
     * @param emulatorArgs  arguments used to start emulator with
     * @throws BuildError
     */
    void launchEmulatorWithRelaunch(ITestDevice device, List<String> emulatorArgs)
            throws BuildError {
        for (int i = 1; i <= mLaunchAttempts; i++) {
            try {
                getDeviceManager().launchEmulator(device, mMaxBootTime * 60 * 1000, mRunUtil,
                        emulatorArgs);
                // hack alert! adb to emulator communication on first boot is notoriously flaky
                // b/4644136
                // send it a few adb commands to ensure the communication channel is stable
                CLog.d("Testing adb to %s communication", device.getSerialNumber());
                for (int j = 0; j < 3; j++) {
                    mRunUtil.sleep(2 * 1000);
                    device.executeShellCommand("pm list instrumentation");
                }

                // hurray - launched!
                return;
            } catch (DeviceNotAvailableException e) {
                CLog.w("Crow emulator '%s' failed to launch on attempt %d of %d. Cause: %s",
                        device.getSerialNumber(), i, mLaunchAttempts, e);
            }
            try {
                // ensure process has been killed
                getDeviceManager().killEmulator(device);
            } catch (DeviceNotAvailableException e) {
                // ignore
            }
        }
        throw new DeviceFailedToBootError(String.format("Crow emulator '%s' failed to boot.",
                device.getSerialNumber()), device.getDeviceDescriptor());
    }
}
