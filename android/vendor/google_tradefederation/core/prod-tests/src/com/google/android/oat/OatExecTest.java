// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.oat;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A Test that runs all oatexec tests on given device.
 */
@OptionClass(alias = "oatexec-test")
public class OatExecTest implements IDeviceTest, IRemoteTest {

    private static final String OATEXEC_TAG = "OatExecTest";
    private static final String OATEXEC_CMD = "ANDROID_DATA=%s && LD_LIBRARY_PATH=/data/lib &&"+
            " (dalvikvm -Xcompiler:/data/bin/dex2oatd -XXlib:/data/lib/libartd.so" +
            " -classpath %s -Djava.library.path=%s %s " +
            " && echo OATEXECTEST_PASSED) || (echo OATEXECTEST_FAILED)";
    private static final String VALGRIND_OATEXEC_CMD = "ANDROID_DATA=%s &&" +
            " LD_LIBRARY_PATH=/data/lib && (valgrind dalvikvm" +
            " -Xcompiler:/data/bin/dex2oatd -XXlib:/data/lib/libartd.so -classpath %s " +
            "-Djava.library.path=%s %s && echo OATEXECTEST_PASSED) || (echo OATEXECTEST_FAILED)";

    static final String DEFAULT_TEST_PATH = "/data/art-test";

    private static final String TEMP_PATH = "/data/local/tmp/oatexec-tests";
    private ITestDevice mDevice = null;

    @Option(name = "oatexec-test-device-path",
            description="The path on the device where the oatexec tests are located.")
    private String mTestDevicePath = DEFAULT_TEST_PATH;

    @Option(name = "test-timeout", description =
            "The max time in ms for an oatexec test to run. " +
            "Test run will be aborted if any test takes longer.", isTimeVal = true)
    private long mMaxTestTimeMs = 1 * 60 * 1000;

    @Option(name = "test-retries", description = "The max number of retries to do if test fails. ")
    private int mTestRetryAttempts = 0;

    @Option(name = "valgrind",
            description = "Whether to run this test with Valgrind enabled or not.")
    private boolean mValgrind = false;

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
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Set the max time in ms for a test to run.
     */
    void setMaxTestTimeMs(int timeout) {
        mMaxTestTimeMs = timeout;
    }

    /**
     * Executes all oatexec tests in a folder as well as in all subfolders recursively.
     *
     * @param rootEntry The root folder to begin searching for oatexec tests
     * @param testDevice The device to run tests on
     * @param listener the {@link ITestRunListener}
     * @throws DeviceNotAvailableException
     */
    void doRunAllTestsInSubdirectory(IFileEntry rootEntry, ITestDevice testDevice,
            ITestRunListener listener) throws DeviceNotAvailableException {

        if (rootEntry.isDirectory()) {
            // recursively run tests in all subdirectories
            for (IFileEntry childEntry : rootEntry.getChildren(false)) {
                doRunAllTestsInSubdirectory(childEntry, testDevice, listener);
            }
        } else {
            String fullPath = rootEntry.getFullEscapedPath();
            String testName = rootEntry.getName();
            if (!fullPath.endsWith(".jar")) {
                CLog.i("Skipping %s since it is not a .jar file", fullPath);
                return;
            }
            CLog.i("Running oatexec test: '%s' on %s", fullPath, mDevice.getSerialNumber());
            // Force file to be executable
            testDevice.executeShellCommand(String.format("chmod 755 %s", fullPath));
            runTest(testDevice, fullPath, testName, listener);
        }
    }

    /**
     * Run the given oatexec test
     *
     * @param testDevice the {@link ITestDevice}
     * @param fullPath absolute file system path to the jar file on device
     * @param testName the name of the jar test file
     * @param listener the {@link ITestRunListener}
     * @throws DeviceNotAvailableException
     */
    private void runTest(final ITestDevice testDevice, final String fullPath, final String testName,
            ITestRunListener listener) throws DeviceNotAvailableException {
        try {
            String className = testName.replace(".jar", "").replace("oat-test-dex-", "");
            String oatexec_cmd = OATEXEC_CMD;
            if (mValgrind) {
                oatexec_cmd = VALGRIND_OATEXEC_CMD;
            }
            String cmd  = String.format(oatexec_cmd, TEMP_PATH, fullPath, mTestDevicePath,
                    className);
            CLog.d("About to run oatexec test command: %s", cmd);
            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
            testDevice.executeShellCommand(cmd, receiver,
                    mMaxTestTimeMs /* maxTimeToShellOutputResponse */,
                    TimeUnit.MILLISECONDS,
                    mTestRetryAttempts /* retryAttempts */);
            String output = receiver.getOutput();
            CLog.v("%s on %s returned %s", cmd, testDevice.getSerialNumber(), output);

            TestIdentifier testId = new TestIdentifier(OATEXEC_TAG, className);
            listener.testRunStarted(className, 1);
            listener.testStarted(testId);

            if (output.contains("OATEXECTEST_PASSED")) {
                CLog.i("%s PASSED", className);
            } else if (output.contains("OATEXECTEST_FAILED")) {
                CLog.i("%s FAILED", className);
                listener.testFailed(testId,
                        output);
            } else {
                CLog.e("Failed to get result for test %s on %s", fullPath,
                        mDevice.getSerialNumber());
                listener.testFailed(testId,
                        output);
            }
            // For all cases (pass or fail), we ultimately need to report test has ended
            Map <String, String> emptyMap = Collections.emptyMap();
            listener.testEnded(testId, emptyMap);
            listener.testRunEnded(0, emptyMap);
        } catch (DeviceNotAvailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }
        // Create tmp art-cache for tests to store the oat files.
        if (!mDevice.doesFileExist(TEMP_PATH)) {
            mDevice.executeShellCommand(String.format("mkdir %s", TEMP_PATH));
        }
        File dalvikCache = new File(TEMP_PATH, "dalvik-cache");
        if (!mDevice.doesFileExist(dalvikCache.getAbsolutePath())) {
            mDevice.executeShellCommand(String.format("mkdir %s", dalvikCache.getAbsolutePath()));
        }
        IFileEntry TestDirectory = mDevice.getFileEntry(mTestDevicePath);
        if (TestDirectory == null) {
            CLog.w("Could not find oat-exec test directory %s in %s!",
                    mTestDevicePath, mDevice.getSerialNumber());
            return;
        }
        doRunAllTestsInSubdirectory(TestDirectory, mDevice, listener);
    }
}
