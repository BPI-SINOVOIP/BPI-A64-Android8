// Copyright 2015 Google Inc.  All Rights Reserved.

package com.google.android.ota;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.OtaDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.BackgroundDeviceAction;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ILogcatReceiver;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.LogcatReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.AndroidJUnitTest;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.LogcatUpdaterEventParser;
import com.android.tradefed.util.LogcatUpdaterEventParser.AsyncUpdaterEvent;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.UpdaterEventType;
import com.android.tradefed.util.ZipUtil;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A test which runs instrumentations at different points during an A/B update.
 */
@OptionClass(alias = "ab-ota")
public class OtaFunctionalTest extends AndroidJUnitTest implements IBuildReceiver {

    private static final String CHECKIN_CMD =
            "am broadcast -a android.server.checkin.CHECKIN com.google.android.gms";
    private static final String CLASS_DOWNLOAD_TEST =
            "com.android.functional.otatests.ab.DownloadTest";
    private static final String CLASS_APPLY_TEST =
            "com.android.functional.otatests.ab.ApplyTimeTest";
    private static final String CLASS_D2O_TEST =
            "com.android.functional.otatests.ab.Dex2OatTimeTest";
    private static final String CLASS_POST_REBOOT_TEST =
            "com.android.functional.otatests.ab.PostRebootTimeTest";
    private static final String CLASS_PRE_REBOOT_TEST =
            "com.android.functional.otatests.ab.PreRebootTimeTest";
    private static final String KILL_INSTR_CMD = "am broadcast -a android.test.ota.STOP_EXEC";
    private static final long LOGCAT_FILE_SIZE = 1024 * 10 * 10;
    private static final int LOGCAT_START_DELAY = 0;
    private static final String LOGCAT_RECEIVER_CMD = "logcat -s";
    private static final long MONITOR_TIMER_DELAY = 0;
    private static final long MONITOR_TIMER_PERIOD = 1000;
    private static final String PAYLOAD_BINARY = "payload.bin";
    private static final String PAYLOAD_PROPERTIES = "payload_properties.txt";

    @Option(name = "downloaded-build", description = "Whether or not to download the OTA package "
            + "via checkin.")
    private boolean mDownloadedBuild = false;

    @Option(name = "download-test-method", description = "Test method to run at download time;"
            + "can be repeated")
    private Collection<String> mDownloadMethods = new ArrayList<>();

    @Option(name = "apply-test-method", description = "Test method to run at apply time;"
            + "can be repeated")
    private Collection<String> mApplyMethods = new ArrayList<>();

    @Option(name = "d2o-test-method", description = "Test method to run at dex2oat time;"
            + "can be repeated")
    private Collection<String> mDex2OatMethods = new ArrayList<>();

    @Option(name = "pre-reboot-test-method", description = "Test method to run before reboot;"
            + "can be repeated")
    private Collection<String> mPreRebootMethods = new ArrayList<>();

    @Option(name = "post-reboot-test-method", description = "Test method to run after reboot;"
            + "can be repeated")
    private Collection<String> mPostRebootMethods = new ArrayList<>();

    @Option(name = "package-data-path", description =
            "path on /data for the package to be saved to")
    private String mPackageDataPath = "/data/ota_package/update.zip";

    @Option(name = "apply-timeout", description = "time to wait for apply to finish",
            isTimeVal = true)
    private long mApplyTimeoutMillis = 0;

    @Option(name = "echo-uec-output", description = "whether or not to print update_engine_client"
            + " output to the terminal")
    private boolean mEchoUecOutput = false;

    @Option(name = "logcat-spec", description = "tags to include in captured logcat output")
    private List<String> mLogcatSpecs = new ArrayList<String>();

    private LogcatUpdaterEventParser mLogcatParser = null;
    private ILogcatReceiver mLogcatReceiver = null;
    private OtaDeviceBuildInfo mOtaDeviceBuild;
    private File mOtaPackage;
    private ITestDevice mDevice;
    private BackgroundDeviceAction mLogcatEchoReceiver;

    long mPayloadOffset = 0;
    long mPayloadLength = 0;
    String mPayloadName;
    List<String> mPayloadProperties = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mOtaDeviceBuild = (OtaDeviceBuildInfo)buildInfo;
        // If the build's OTA file reference is null, this is a full OTA. Check the other half
        // of the BuildInfo for the package.
        if (mOtaDeviceBuild.getOtaPackageFile() == null) {
            mOtaPackage = mOtaDeviceBuild.getOtaBuild().getOtaPackageFile();
        } else {
            mOtaPackage = mOtaDeviceBuild.getOtaPackageFile();
        }
        if (mOtaPackage == null) {
            throw new IllegalStateException("Received an OtaDeviceBuildInfo with no package");
        }
    }

    private void checkin() throws DeviceNotAvailableException {
        getDevice().executeShellCommand(CHECKIN_CMD);
    }

    private void startLogcatListener() {
        if (mLogcatReceiver == null) {
            mLogcatReceiver = new LogcatReceiver(getDevice(), LOGCAT_FILE_SIZE, LOGCAT_START_DELAY);
        }
        if (mLogcatParser == null) {
            mLogcatParser = new LogcatUpdaterEventParser(mLogcatReceiver);
        }
        mLogcatReceiver.start();
    }

    private void stopLogcatListener() {
        if (mLogcatReceiver != null) {
            mLogcatReceiver.stop();
        }
        StreamUtil.close(mLogcatParser);
    }

    private void addDefaultLogcatSpecs() {
        mLogcatSpecs.add("update_engine:I");
        mLogcatSpecs.add("update_engine_client:I");
        mLogcatSpecs.add("CmaSystemUpdateService:I");
        mLogcatSpecs.add("UpdateEngineTask:I");
        mLogcatSpecs.add("SystemUpdateTask:I");
        mLogcatSpecs.add("SystemUpdateClient:I");
    }

    private String getLogcatReceiverCmd() {
        StringBuilder sb = new StringBuilder();
        sb.append(LOGCAT_RECEIVER_CMD);
        sb.append(" ");
        sb.append(ArrayUtil.join(" ", mLogcatSpecs));
        return sb.toString();
    }

    private void runWithMethods(String clazz, Collection<String> methods,
            ITestInvocationListener listener) throws DeviceNotAvailableException {
        runWithMethods(clazz, methods, listener, null);
    }

    private void runWithMethods(String clazz, Collection<String> methods,
            ITestInvocationListener listener, final AsyncUpdaterEvent monitor)
                    throws DeviceNotAvailableException {
        setClassName(clazz);
        Timer timer = new Timer();
        try {
            if (monitor != null) {
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        synchronized(monitor) {
                            if (monitor.isCompleted()) {
                                try {
                                    // Forcibly end the running instrumentation
                                    getDevice().executeShellCommand(KILL_INSTR_CMD);
                                    this.cancel();
                                } catch (DeviceNotAvailableException e) {
                                    // super.run() should hit DNAE as well,
                                    // so we just exit this thread
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                }, MONITOR_TIMER_DELAY, MONITOR_TIMER_PERIOD);
            }
            for (String m : methods) {
                setMethodName(m);
                super.run(listener);
            }
        // If we exit the method
        } finally {
            timer.cancel();
        }
    }

    protected TestIdentifier createTestIdentifier(String stageName)
            throws DeviceNotAvailableException {
        return new TestIdentifier("OtaFunctionalTest", ArrayUtil.join("-",
                "ota",
                getDevice().getSerialNumber(),
                getDevice().getBuildId(),
                stageName));
    }

    protected int getNumTestStages() {
        return mDownloadedBuild ? 3 : 2;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        listener.testRunStarted("OtaFunctionalTest", getNumTestStages());
        long start = System.currentTimeMillis();
        mDevice = getDevice();
        addDefaultLogcatSpecs();
        startLogcatListener();
        if (mDownloadedBuild) {
            checkin();
            mLogcatParser.waitForEvent(UpdaterEventType.DOWNLOAD_COMPLETE);
            runWithMethods(CLASS_DOWNLOAD_TEST, mDownloadMethods, listener);
        } else {
            CLog.i("Pushing OTA package %s", mOtaPackage.getAbsolutePath());
            Assert.assertTrue(
                    mDevice.pushFile(mOtaPackage, mPackageDataPath));
            initPayload(mOtaPackage.getAbsolutePath());
            final String cmd = getUecCommand();
            if (mEchoUecOutput) {
                IShellOutputReceiver receiver = new MultiLineReceiver() {
                    @Override
                    public void processNewLines(String[] lines) {
                        for (String l : lines) {
                            CLog.d(l);
                        }
                    }
                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                };
                mLogcatEchoReceiver = new BackgroundDeviceAction(getLogcatReceiverCmd(),
                        "UpdateEngineLogcatWatcher", mDevice, receiver, 0);
                mLogcatEchoReceiver.start();
            }
            Thread uec =
                    new Thread() {
                        @Override
                        public void run() {
                            CLog.d(
                                    "Starting UpdateEngineRunner for %s.",
                                    mDevice.getSerialNumber());
                            try {
                                mDevice.getIDevice()
                                        .executeShellCommand(
                                                cmd,
                                                new NullOutputReceiver(),
                                                0,
                                                TimeUnit.MILLISECONDS);
                            } catch (TimeoutException
                                    | AdbCommandRejectedException
                                    | ShellCommandUnresponsiveException
                                    | IOException e) {
                                CLog.e("Received exception from UpdateEngineRunner");
                                CLog.e(e);
                            }
                        }
                    };
            uec.setDaemon(true);
            uec.start();
            CLog.i("Running update command %s", cmd);
        }
        AsyncUpdaterEvent patchCompleteEvent =
                mLogcatParser.waitForEventAsync(UpdaterEventType.PATCH_COMPLETE);
        runWithMethods(CLASS_APPLY_TEST, mApplyMethods, listener, patchCompleteEvent);
        // in case tests finish before the event is detected, wait
        while (!patchCompleteEvent.isCompleted()) {
            synchronized(patchCompleteEvent) {
                try {
                    patchCompleteEvent.setWaiting(true);
                    patchCompleteEvent.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        AsyncUpdaterEvent dex2OatCompleteEvent =
                mLogcatParser.waitForEventAsync(UpdaterEventType.D2O_COMPLETE);
        runWithMethods(CLASS_D2O_TEST, mDex2OatMethods, listener, dex2OatCompleteEvent);
        while (!dex2OatCompleteEvent.isCompleted()) {
            synchronized(dex2OatCompleteEvent) {
                try {
                    dex2OatCompleteEvent.setWaiting(true);
                    dex2OatCompleteEvent.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        UpdaterEventType result =
                mLogcatParser.waitForEvent(UpdaterEventType.UPDATE_COMPLETE, mApplyTimeoutMillis);
        if (result == UpdaterEventType.ERROR) {
            CLog.e("Update failed, not switching slot");
            listener.testRunFailed("A/B update failed");
        }
        runWithMethods(CLASS_PRE_REBOOT_TEST, mPreRebootMethods, listener);
        listener.testRunEnded(System.currentTimeMillis() - start,
                new HashMap<String, String>());
        try {
            getDevice().reboot();
            getDevice().uninstallPackage(getPackageName());
        } finally {
            stopLogcatListener();
            if (mLogcatEchoReceiver != null) {
                mLogcatEchoReceiver.cancel();
            }
        }
    }

    String getUecCommand() {
        List<String> cmd = new ArrayList<>();
        cmd.add("update_engine_client");
        cmd.add("--update");
        cmd.add("--follow");
        cmd.add("--payload=" + mPayloadName);
        cmd.add("--offset=" + mPayloadOffset);
        cmd.add("--size=" + mPayloadLength);
        cmd.add("--headers=\"" + ArrayUtil.join("\n", mPayloadProperties) + "\"");
        return ArrayUtil.join(" ", cmd);
    }

    void initPayload(final String filename) {
        boolean found = false;
        ZipFile zip = null;
        try {
            zip = new ZipFile(filename);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            long offset = 0;
            long length = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                long extraSize = entry.getExtra() == null ? 0 : entry.getExtra().length;
                offset += 30 + name.length() + extraSize;
                if (entry.isDirectory()) {
                    continue;
                }
                length = entry.getCompressedSize();
                if (PAYLOAD_BINARY.equals(name)) {
                    CLog.i("Found payload binary entry at offset " + offset
                            + " size of " + length);
                    if (entry.getMethod() != ZipEntry.STORED) {
                        CLog.e("Invalid compression method");
                        return;
                    }
                    found = true;
                    mPayloadOffset = offset;
                    mPayloadLength = length;
                } else if (PAYLOAD_PROPERTIES.equals(name)) {
                    CLog.i("Found payload properties entry at offset " + offset
                            + " size of " + length);
                    BufferedReader br = new BufferedReader(new InputStreamReader(
                            zip.getInputStream(entry)));
                    String line;
                    while ((line = br.readLine()) != null) {
                        mPayloadProperties.add(line);
                    }
                    br.close();
                }
                offset += length;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            ZipUtil.closeZip(zip);
        }
        if (!found) {
            throw new RuntimeException("Failed to find payload.bin in payload zip");
        }
        mPayloadName = "file://" + mPackageDataPath;
    }
}
