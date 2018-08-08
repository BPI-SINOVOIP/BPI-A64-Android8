// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.compatibility;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.LogcatReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IStrictShardableTest;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.AaptParser;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.google.android.tradefed.result.CompatibilityTestResult;
import com.google.android.tradefed.util.PublicApkUtil;
import com.google.android.tradefed.util.PublicApkUtil.ApkInfo;

import org.json.JSONException;
import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test that determines application compatibility. The test iterates through the
 * apks in a given directory. The test installs, launches, and uninstalls each
 * apk.
 */
@OptionClass(alias = "app-compability")
public class AppCompatibilityTest implements IDeviceTest, IRemoteTest, IStrictShardableTest {
    @Option(name = "product",
            description = "The product, corresponding to the borgcron job product arg.",
            importance = Option.Importance.ALWAYS, mandatory = true)
    private String mProduct;

    @Option(
        name = "base-dir",
        description = "The directory of the results excluding the date.",
        importance = Option.Importance.ALWAYS
    )
    // TODO(b/36786754): Add `mandatory = true` when cmdfiles are moved over
    private String mBaseDir;

    @Option(name = "date",
            description = "The date to run, in the form YYYYMMDD. If not set, then the latest "
            + "results will be used.")
    private String mDate;

    @Option(name = "shards",
            description = "The number of shards.")
    private int mShardCount = 1;

    @Option(name = "shard-index",
            description = "Which shard to run.")
    private int mShardIndex = 0;

    @Option(name = "test-label",
            description = "Unique test identifier label.")
    private String mTestLabel = "AppCompatibility";

    @Option(name = "app-launch-timeout-ms",
            description = "Time to wait for app to launch in msecs.")
    private int mAppLaunchTimeoutMs = 15000;

    @Option(name = "workspace-launch-timeout-ms",
            description = "Time to wait when launched back into the workspace in msecs.")
    private int mWorkspaceLaunchTimeoutMs = 2000;

    @Option(name = "reboot-after-apks",
            description = "Reboot the device after a centain number of apks. 0 means no reboot.")
    private int mRebootNumber = 100;

    private static final String LAUNCH_TEST_RUNNER =
            "com.android.compatibilitytest.AppCompatibilityRunner";
    private static final String LAUNCH_TEST_PACKAGE = "com.android.compatibilitytest";
    private static final String PACKAGE_TO_LAUNCH = "package_to_launch";
    private static final String APP_LAUNCH_TIMEOUT_LABEL = "app_launch_timeout_ms";
    private static final String WORKSPACE_LAUNCH_TIMEOUT_LABEL = "workspace_launch_timeout_ms";
    private static final long DOWNLOAD_TIMEOUT_MS = 60 * 1000;
    private static final int DOWNLOAD_RETRIES = 3;
    private static final long JOIN_TIMEOUT_MS = 5 * 60 * 1000;
    private static final int LOGCAT_SIZE_BYTES = 20 * 1024 * 1024;

    private ITestDevice mDevice;
    private LogcatReceiver mLogcat;
    // The number of tests run so far
    private int mTestCount = 0;

    /*
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull("Base dir cannot be null", mBaseDir);
        Assert.assertNotNull("Product cannot be null", mProduct);
        Assert.assertTrue(
                String.format("Shard index out of range: expected [0, %d), got %d",
                        mShardCount, mShardIndex),
                mShardIndex >= 0 && mShardIndex < mShardCount);

        File apkDir = null;
        try {
            apkDir = PublicApkUtil.constructApkDir(mBaseDir, mDate);
        } catch (IOException e) {
            CLog.e(e);
            throw new RuntimeException(e);
        }
        Assert.assertNotNull("Could not find the output dir", apkDir);
        List<ApkInfo> apkList = null;
        try {
            apkList = shardApkList(PublicApkUtil.getApkList(mProduct, apkDir));
        } catch (IOException e) {
            CLog.e(e);
            throw new RuntimeException(e);
        }
        Assert.assertNotNull("Could not download apk list", apkList);

        long start = System.currentTimeMillis();
        listener.testRunStarted(mTestLabel, 0);
        mLogcat = new LogcatReceiver(getDevice(), LOGCAT_SIZE_BYTES, 0);
        mLogcat.start();

        try {
            downloadAndTestApks(listener, apkDir, apkList);
        } catch (InterruptedException e) {
            CLog.e(e);
            throw new RuntimeException(e);
        }finally {
            mLogcat.stop();
            listener.testRunEnded(System.currentTimeMillis() - start,
                    Collections.<String, String> emptyMap());
        }
    }

    /**
     * Downloads and tests all the APKs in the apk list.
     *
     * @param listener The {@link ITestInvocationListener}.
     * @param kharonDir The {@link File} of the CNS dir containing the APKs.
     * @param apkList The sharded list of {@link ApkInfo} objects.
     * @throws DeviceNotAvailableException
     * @throws InterruptedException if a download thread was interrupted.
     */
    private void downloadAndTestApks(ITestInvocationListener listener, File kharonDir,
            List<ApkInfo> apkList) throws DeviceNotAvailableException, InterruptedException {
        ApkInfo testingApk = null;
        File testingFile = null;
        for (ApkInfo downloadingApk : apkList) {
            ApkDownloadRunnable downloader = new ApkDownloadRunnable(kharonDir, downloadingApk);
            Thread downloadThread = new Thread(downloader);
            downloadThread.start();

            testApk(listener, testingApk, testingFile);

            try {
                downloadThread.join(JOIN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                FileUtil.deleteFile(downloader.getDownloadedFile());
                throw e;
            }
            testingApk = downloadingApk;
            testingFile = downloader.getDownloadedFile();
        }
        // One more time since the first time through the loop we don't test
        testApk(listener, testingApk, testingFile);
    }

    /**
     * Attempts to install and launch an APK and reports the results.
     *
     * @param listener The {@link ITestInvocationListener}.
     * @param apkInfo The {@link ApkInfo} to run the test against.
     * @param apkFile The downloaded {@link File}.
     * @throws DeviceNotAvailableException
     */
    private void testApk(ITestInvocationListener listener, ApkInfo apkInfo, File apkFile)
            throws DeviceNotAvailableException {
        if (apkInfo == null || apkFile == null) {
            FileUtil.deleteFile(apkFile);
            return;
        }

        mTestCount++;
        if (mRebootNumber != 0 && mTestCount % mRebootNumber == 0) {
            mDevice.reboot();
        }
        mLogcat.clear();

        TestIdentifier testId = new TestIdentifier(LAUNCH_TEST_PACKAGE, apkInfo.packageName);
        listener.testStarted(testId, System.currentTimeMillis());

        CompatibilityTestResult result = new CompatibilityTestResult();
        result.rank = apkInfo.rank;
        // Default to package name since name is a required field. This will be replaced by
        // AaptParser in installApk()
        result.name = apkInfo.packageName;
        result.packageName = apkInfo.packageName;
        result.versionString = apkInfo.versionString;
        result.versionCode = apkInfo.versionCode;

        try {
            // Install the app.
            installApk(result, apkFile);
            if (result.status == null) {
                launchApk(result);
            }
            if (result.status == null) {
                result.status = CompatibilityTestResult.STATUS_SUCCESS;
            }
        } finally {
            reportResult(listener, testId, result);
            try {
                postLogcat(result, listener);
            } catch (JSONException e) {
                CLog.w("Posting failed: %s", e.getMessage());
            }
            listener.testEnded(testId, System.currentTimeMillis(),
                    Collections.<String, String> emptyMap());
            FileUtil.deleteFile(apkFile);
        }
    }

    /**
     * Checks that the file is correct and attempts to install it.
     * <p>
     * Will set the result status to error if the APK could not be installed or if it contains
     * conflicting information.
     * </p>
     *
     * @param result the {@link CompatibilityTestResult} containing the APK info.
     * @param apkFile the APK file to install.
     * @throws DeviceNotAvailableException
     */
    private void installApk(CompatibilityTestResult result, File apkFile)
            throws DeviceNotAvailableException {
        AaptParser parser = AaptParser.parse(apkFile);
        if (parser == null) {
            CLog.d("Failed to parse apk file %s, package: %s, error: %s",
                    apkFile.getAbsolutePath(), result.packageName, result.message);
            result.status = CompatibilityTestResult.STATUS_ERROR;
            result.message = "aapt fail";
            return;
        }

        result.name = parser.getLabel();

        if (!equalsOrNull(result.packageName, parser.getPackageName()) ||
                !equalsOrNull(result.versionString, parser.getVersionName()) ||
                !equalsOrNull(result.versionCode, parser.getVersionCode())) {
            CLog.d("Package info mismatch: want %s v%s (%s), got %s v%s (%s)",
                    result.packageName, result.versionCode, result.versionString,
                    parser.getPackageName(), parser.getVersionCode(), parser.getVersionName());
            result.status = CompatibilityTestResult.STATUS_ERROR;
            result.message = "package info mismatch";
            return;
        }

        try {
            String error = mDevice.installPackage(apkFile, true);
            if (error != null) {
                CLog.d("Failed to install apk file %s, package: %s, error: %s",
                        apkFile.getAbsolutePath(), result.packageName, result.message);
                result.status = CompatibilityTestResult.STATUS_ERROR;
                result.message = error;
                return;
            }
        } catch (DeviceUnresponsiveException e) {
            CLog.d("Installing apk file %s timed out, package: %s, error: %s",
                    apkFile.getAbsolutePath(), result.packageName, result.message);
            result.status = CompatibilityTestResult.STATUS_ERROR;
            result.message = "install timeout";
            return;
        }
    }

    /**
     * Method which attempts to launch an APK.
     * <p>
     * Will set the result status to failure if the APK could not be launched.
     * </p>
     *
     * @param result the {@link CompatibilityTestResult} containing the APK info.
     * @throws DeviceNotAvailableException
     */
    private void launchApk(CompatibilityTestResult result)
            throws DeviceNotAvailableException {
        InstrumentationTest instrTest = new InstrumentationTest();
        instrTest.setDevice(mDevice);
        instrTest.addInstrumentationArg(APP_LAUNCH_TIMEOUT_LABEL,
                Integer.toString(mAppLaunchTimeoutMs));
        instrTest.addInstrumentationArg(WORKSPACE_LAUNCH_TIMEOUT_LABEL,
                Integer.toString(mWorkspaceLaunchTimeoutMs));
        instrTest.setPackageName(LAUNCH_TEST_PACKAGE);
        instrTest.addInstrumentationArg(PACKAGE_TO_LAUNCH, result.packageName);
        instrTest.setRunnerName(LAUNCH_TEST_RUNNER);
        FailureCollectingListener failureListener = new FailureCollectingListener();
        instrTest.run(failureListener);

        if (failureListener.getStackTrace() != null) {
            CLog.w("Failed to launch package %s", result.packageName);
            result.status = CompatibilityTestResult.STATUS_FAILURE;
            result.message = failureListener.getStackTrace();
        }

        // Uninstall packages.
        mDevice.uninstallPackage(result.packageName);
        CLog.d("Completed testing on app package: %s", result.packageName);
    }

    /**
     * Helper method which reports a test failed if the status is either a failure or an error.
     */
    private void reportResult(ITestInvocationListener listener,
            TestIdentifier id, CompatibilityTestResult result) {
        String message = result.message != null ? result.message : "unknown";
        if (CompatibilityTestResult.STATUS_ERROR.equals(result.status)) {
            listener.testFailed(id, "ERROR:" + message);
        } else if (CompatibilityTestResult.STATUS_FAILURE.equals(result.status)) {
            listener.testFailed(id, "FAILURE:" + message);
        }
    }

    /**
     * Helper method which posts the logcat.
     */
    private void postLogcat(CompatibilityTestResult result,
            ITestInvocationListener listener) throws JSONException {
        InputStreamSource stream = null;
        String header = String.format("%s%s%s\n", CompatibilityTestResult.SEPARATOR,
                result.toJsonString(), CompatibilityTestResult.SEPARATOR);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStreamSource logcatData = mLogcat.getLogcatData()) {
            try {
                baos.write(header.getBytes());
                StreamUtil.copyStreams(logcatData.createInputStream(), baos);
                stream = new ByteArrayInputStreamSource(baos.toByteArray());
                baos.flush();
                baos.close();
            } catch (IOException e) {
                CLog.e("error inserting compatibility test result into logcat");
                CLog.e(e);
                // fallback to logcat data
                stream = logcatData;
            }
            listener.testLog("logcat_" + result.packageName, LogDataType.LOGCAT, stream);
        } finally {
            StreamUtil.cancel(stream);
        }
    }

    /**
     * Helper method which takes a list of {@link ApkInfo} objects and returns the sharded list.
     */
    private List<ApkInfo> shardApkList(List<ApkInfo> apkList) {
        List<ApkInfo> shardedList = new ArrayList<>(apkList.size() / mShardCount + 1);
        for (int i = mShardIndex; i < apkList.size(); i += mShardCount) {
            shardedList.add(apkList.get(i));
        }
        return shardedList;
    }

    /**
     * Returns true if either object is null or if both objects are equal.
     */
    private static boolean equalsOrNull(Object a, Object b) {
        return a == null || b == null || a.equals(b);
    }

    /**
     * Helper {@link Runnable} which downloads a file, and can be used in another thread.
     */
    private class ApkDownloadRunnable implements Runnable {
        private final File mKharonDir;
        private final ApkInfo mApkInfo;

        private File mDownloadedFile = null;

        public ApkDownloadRunnable(File kharonDir, ApkInfo apkInfo) {
            mKharonDir = kharonDir;
            mApkInfo = apkInfo;
        }

        @Override
        public void run() {
            // No-op if mApkInfo is null
            if (mApkInfo == null) {
                return;
            }

            File sourceFile = new File(mKharonDir, mApkInfo.fileName);
            try {
                mDownloadedFile =
                        PublicApkUtil.downloadFile(
                                sourceFile, DOWNLOAD_TIMEOUT_MS, DOWNLOAD_RETRIES);
            } catch (IOException e) {
                // Log and ignore
                CLog.e("Could not download apk from %s", sourceFile);
                CLog.e(e);
            }
        }

        public File getDownloadedFile() {
            return mDownloadedFile;
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Return a {@link IRunUtil} instance to execute commands with.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public IRemoteTest getTestShard(int shardCount, int shardIndex) {
        CLog.i("Strict sharding: overriding shard-index param, was %d, now %d",
                mShardIndex, shardIndex);
        mShardIndex = shardIndex;
        CLog.i("Strict sharding: overriding shards param, was %d, now %d",
                mShardCount, shardCount);
        mShardCount = shardCount;
        return this;
    }
}
