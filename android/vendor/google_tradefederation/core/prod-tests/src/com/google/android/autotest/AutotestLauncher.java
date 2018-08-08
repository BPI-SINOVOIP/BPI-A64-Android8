// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.autotest;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

/**
 * AutotestLauncher is used to run Autotest test with the following steps:
 * 1) Run the given Autotest test using test_droid, and wait for the test to finish.
 * 2) Analyze the test_droid logs and report the test results to ITestInvocationListener.
 */
@OptionClass(alias = "autotest-launcher")
public class AutotestLauncher implements IRemoteTest, IDeviceTest {
    @Option(name = "autotest-test-name",
            description = "Name of the Autotest test", mandatory = true)
    private String mTestName = null;

    @Option(name = "test-timeout",
            description = "A timeout in ms for the test_droid.py to run",
            isTimeVal = true)
    private long mTestTimeout = 1 * 3600 * 1000;

    @Option(name = "autotest-dir",
            description = "Directory contains Autotest code")
    private String mAutotestDir = "/usr/local/google/autotest";

    // Constants for running the tests
    private static final String TEST_DROID = "site_utils/test_droid.py";

    private IRunUtil mRunUtil = RunUtil.getDefault();
    private ITestDevice mDevice;
    private File mAutotestResultsDir = null;

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Set IRunUtil.
     *
     * Exposed for unit testing.
     */
    public void setRunUtil(IRunUtil runUtil) {
        mRunUtil = runUtil;
    }

    /**
     * Set Autotest directory.
     *
     * Exposed for unit testing.
     */
    public void setAutotestDir(String autotestDir) {
        mAutotestDir = autotestDir;
    }

    /**
     * Set the test name to be run.
     *
     * Exposed for unit testing.
     */
    public void setTestName(String testName) {
        mTestName = testName;
    }

    /**
     * Set the result directory for Autotest test.
     *
     * Exposed for unit testing.
     */
    public void setAutotestResultsDir(File autotestResultsDir) {
        mAutotestResultsDir = autotestResultsDir;
    }

    /**
     * uploadLogFiles, upload all the files under the provided folder
     *
     * @param listener ITestInvocationListener to call testLog method to record logs
     * @param folderName The root folder for Autotest log files used for uploading
     */
    private void uploadLogFiles(ITestInvocationListener listener, String folderName) {
        File folder = new File(folderName);
        File[] listOfFiles = folder.listFiles();
        String filename, shortFilename, directoryName;
        InputStreamSource outputSource = null;
        for (File file : listOfFiles) {
            if (file.isFile()) {
                filename = file.getPath();
                String[] filePathItems =
                        filename.split(System.getProperty("java.io.tmpdir") + File.separator);
                shortFilename = filePathItems[filePathItems.length - 1];
                try {
                    outputSource = new FileInputStreamSource(file);
                    listener.testLog(shortFilename, LogDataType.TEXT, outputSource);
                } finally {
                    StreamUtil.cancel(outputSource);
                }
            } else if (file.isDirectory()) {
                directoryName = file.getAbsolutePath();
                uploadLogFiles(listener, directoryName);
            }
        }
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        CLog.i("Run Autotest test '%s' on device %s", mTestName, getDevice().getSerialNumber());

        TestIdentifier testId = new TestIdentifier(getClass().getCanonicalName(),
                "Running Autotest test " + mTestName);
        listener.testRunStarted(mTestName, 0);
        listener.testStarted(testId);

        String testResultsPath = null;
        try {
            CLog.d("Create temp directory to run test_droid command.");
            // Create a temp directory to store Autotest results. The directory could be already set
            // through setAutotestDir method in unit test (AutotestLauncherTest).
            if (mAutotestResultsDir == null) {
                mAutotestResultsDir = FileUtil.createTempDir("test_droid_");
            }
            testResultsPath = mAutotestResultsDir.getAbsolutePath();

            Path testDroidScript = Paths.get(mAutotestDir, TEST_DROID);
            CLog.d("Test if test_droid command exists.");

            CommandResult cr = mRunUtil.runTimedCmd(mTestTimeout,
                    testDroidScript.toString(), "-s", getDevice().getSerialNumber(),
                    "--results_dir", testResultsPath, mTestName);

            Path statusLogFile = Paths.get(testResultsPath, "results-1-" + mTestName, "status.log");
            String statusLog = null;
            CLog.d("Locating test status log file...");
            statusLog = FileUtil.readStringFromFile(new File(statusLogFile.toString()));

            if (statusLog != null) {
                CLog.d("status.log file found: " + statusLogFile.toString());
            }
            if (cr.getStatus() != CommandStatus.SUCCESS) {
                listener.testFailed(
                        testId, "Autotest test failed. Details in status.log: " + statusLog);
            }
        } catch (IOException e) {
            listener.testFailed(testId, e.toString());
            throw new RuntimeException(e);
        } finally {
            if (testResultsPath != null) {
                uploadLogFiles(listener, testResultsPath);
            }

            // Clean up test results generated by test_droid.
            FileUtil.recursiveDelete(mAutotestResultsDir);

            listener.testEnded(testId, Collections.<String, String>emptyMap());
            listener.testRunEnded(0, Collections.<String, String>emptyMap());
        }
    }
}
