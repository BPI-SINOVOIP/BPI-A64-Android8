// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.adb;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

/** Performance test for common adb tasks (mainly file transfer) */
public class AdbPerformanceTest implements IRemoteTest, IBuildReceiver {

    @Option(
        name = "small-file-size",
        description = "File size in bytes for testing transfer one small file"
    )
    private int mSmallFileSize = 1024 * 1024;

    @Option(
        name = "large-file-size",
        description = "File size in bytes for testing transfer one large file"
    )
    private int mLargeFileSize = 100 * 1024 * 1024;

    @Option(
        name = "multi-file-size-each",
        description = "File size (each) in bytes for testing transfer multiple files"
    )
    private int mMultiFileSize = 1024 * 1024 / 32;

    @Option(
        name = "multi-file-number",
        description = "Number of files for testing transfer multiple files"
    )
    private int mMultiFileNum = 100 * 32;

    // Roughly each run takes less than 2 minutes
    @Option(name = "iterations", description = "Number of iterations to repeat each test")
    private int mRuns = 5;

    @Option(name = "timeout", description = "Max time in ms to wait for adb command to complete")
    private long mTimeout = 300 * 1000L;

    @Option(name = "device-temp-dir", description = "Temp directory on device for the test")
    private String mDeviceScratchDirPath = "data/local/tmp";

    private String mDeviceTestFilePath = mDeviceScratchDirPath + "/adb_perf_test_file";
    private String mDeviceTestDirPath = mDeviceScratchDirPath + "/adb_perf_test_dir";

    // Common setup and cleanup commands - initiate after we have adb path
    private String[] mRmDeviceTestFile;
    private String[] mRmDeviceTestDir;
    private String[] mMkdirDeviceTestDir;

    private IBuildInfo mBuildInfo;
    private IRunUtil mRunUtil = new RunUtil();
    private String mAdbPath;
    private Random mRandom;
    private MultiRunResultHelper mMultiResults;

    private static final String FILE_TRANSFER_SPEED_TEST = "FileTransferSpeedTest";

    private static final String ADB_PUSH_ONE = "Push 1 file of %s with \"adb push\"";
    private static final String ADB_PULL_ONE = "Pull 1 file of %s with \"adb pull\"";
    private static final String ADB_PUSH_MULTIPLE = "Push %d files of %s with \"adb push\"";
    private static final String ADB_PULL_MULTIPLE = "Pull %d files of %s with \"adb pull\"";
    private static final String SHELL_PUSH_ONE = "Push 1 file of %s with shell pipeline";
    private static final String SHELL_PULL_ONE = "Pull 1 file of %s with shell pipeline";

    private static final String MIN_OF_RUNS = "Min of %d runs (ms)";
    private static final String MAX_OF_RUNS = "Max of %d runs (ms)";
    private static final String AVG_OF_RUNS = "Average of %d runs (ms)";

    /**
     * Helper class to summarize results from multiple test runs (of the same test). Output summary
     * statistics to {@link ITestInvocationListener}, grouped by test name.
     *
     * Example usage:
     *
     * MultiRunResultHelper results = new MultiRunResultHelper("FileTransferSpeedTest");
     * results.putRawResult("TestPush", 5000);
     * results.putRawResult("TestPush", 6000);
     * results.putRawResult("TestPush", 5000);
     * results.putRawResult("TestPush", 4000);
     * results.reportResults(listener);
     *
     * The result sent to listener will look like:
     * "FileTransferSpeedTest#TestPush"
     * ..."Min of 4 runs (ms)": 4000
     * ..."Max of 4 runs (ms)": 6000
     * ..."Avg of 4 runs (ms)": 5000
     */
    private class MultiRunResultHelper {
        // raw result entries (test, list of timeInNanoSec)
        private Map<String, ArrayList<Long>> mRawResults = new HashMap<>();
        private String mTestClass;

        public MultiRunResultHelper(String testClass) {
            mTestClass = testClass;
        }

        public void putRawResult(String testKey, long timeInNanoSec) {
            if (!mRawResults.containsKey(testKey)) {
                mRawResults.put(testKey, new ArrayList<Long>());
            }
            mRawResults.get(testKey).add(timeInNanoSec);
        }

        /**
         * Calculate min, max, and average from list of results. Return a map that can go directly
         * to {@link ITestInvocationListener}
         */
        private Map<String, String> getOutputResult(ArrayList<Long> timesInNanoSec) {
            int n = timesInNanoSec.size();
            if (n != mRuns) {
                CLog.w("Number of test results inconsistent with number of test runs.");
            }
            if (n == 0) {
                // should never happen, unless mRawResults has entry with empty timesInNanoSec array
                CLog.e("No test result found.");
                return null;
            }

            long min = timesInNanoSec.get(0);
            long max = timesInNanoSec.get(0);
            long sum = 0;
            for (Long t : timesInNanoSec) {
                if (t < min) {
                    min = t;
                }
                if (t > max) {
                    max = t;
                }
                sum += t;
            }
            long avg = sum / n;

            Map<String, String> result = new HashMap<>();
            // put results and convert nano sec to mill sec
            result.put(String.format(MIN_OF_RUNS, n), String.valueOf(min / 1e6));
            result.put(String.format(MAX_OF_RUNS, n), String.valueOf(max / 1e6));
            result.put(String.format(AVG_OF_RUNS, n), String.valueOf(avg / 1e6));
            return result;
        }

        public void reportResults(ITestInvocationListener listener) {
            for (Entry<String, ArrayList<Long>> entry : mRawResults.entrySet()) {
                TestIdentifier testId = new TestIdentifier(mTestClass, entry.getKey());
                listener.testStarted(testId);
                listener.testEnded(testId, getOutputResult(entry.getValue()));
            }
        }
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    private void findAdbBinary() {
        for (VersionedFile f : mBuildInfo.getFiles()) {
            // match name that starts with "adb" but not contains "test"
            if (f.getFile().getName().matches("^adb(?!.*test).*")) {
                mAdbPath = f.getFile().getAbsolutePath();
                f.getFile().setExecutable(true);
                return;
            }
        }
        throw new RuntimeException("Cannot find adb binary in build artifacts.");
    }

    private void startAdbServer() {
        String[] cmd = {mAdbPath, "start-server"};
        CommandResult cr = runCommand(cmd);
        if (cr.getStatus() != CommandStatus.SUCCESS) {
            throw new RuntimeException("Cannot start adb server");
        }
        // Wait 1000ms for "adb start-server" to finish. See b/37104408
        mRunUtil.sleep(1 * 1000);
    }

    private void buildCommonAdbCommands() {
        mRmDeviceTestFile = new String[] {mAdbPath, "shell", "rm", "-f", mDeviceTestFilePath};
        mRmDeviceTestDir = new String[] {mAdbPath, "shell", "rm", "-rf", mDeviceTestDirPath};
        mMkdirDeviceTestDir = new String[] {mAdbPath, "shell", "mkdir", "-p", mDeviceTestDirPath};
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        findAdbBinary();
        startAdbServer();
        buildCommonAdbCommands();

        mRandom = new Random(0);
        mMultiResults = new MultiRunResultHelper(FILE_TRANSFER_SPEED_TEST);
        for (int i = 0; i < mRuns; i++) {
            testPushPullFile(mSmallFileSize);
            testPushPullFile(mLargeFileSize);
            testPushPullMultipleFiles(mMultiFileSize, mMultiFileNum);
            testShellCatPushPullFile(mSmallFileSize);
            testShellCatPushPullFile(mLargeFileSize);
        }
        mMultiResults.reportResults(listener);
    }

    /** Create a temp file of given size (in bytes) under system temp dir. */
    private File createSizedTempFile(int size) {
        return createSizedTempFile(size, 0, null);
    }

    /** Create a temp file of given size (in bytes). */
    private File createSizedTempFile(int size, int seed, File parentDir) {
        File tempFile = null;
        FileOutputStream outputStream = null;
        try {
            byte[] contents = new byte[size];
            mRandom.nextBytes(contents);
            tempFile = FileUtil.createTempFile("adb_perf_test", "", parentDir);
            outputStream = new FileOutputStream(tempFile);
            outputStream.write(contents);
        } catch (IOException e) {
            CLog.e(e);
            throw new RuntimeException("Cannot create temp file.");
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    CLog.e("Cannot close output stream: " + e.getMessage());
                }
            }
        }
        return tempFile;
    }

    /** Create a temp dir with n temp files of given size (in bytes) each. */
    private File createTempDirWithSizedFiles(int sizeEach, int n) {
        File tempDir = null;
        try {
            tempDir = FileUtil.createTempDir("adb_perf_test");
            for (int i = 0; i < n; i++) {
                createSizedTempFile(sizeEach, i, tempDir);
            }
        } catch (IOException e) {
            CLog.e(e);
            throw new RuntimeException("Cannot create temp directory.");
        }
        return tempDir;
    }

    private void putResult(String testKey, long timeElapsedInNanoSec) {
        mMultiResults.putRawResult(testKey, timeElapsedInNanoSec);
        CLog.i(String.format("%s: %.2f ms", testKey, timeElapsedInNanoSec / 1e6));
    }

    private CommandResult runCommand(String[] cmd) {
        return mRunUtil.runTimedCmd(mTimeout, cmd);
    }

    private void verifyCommandResult(String[] cmd, CommandResult cr) {
        if (cr.getStatus() != CommandStatus.SUCCESS) {
            CLog.e(Arrays.toString(cmd));
            CLog.e("Status: " + cr.getStatus().toString());
            // Print only the first 1000 chars, in case it's too long (e.g. when cat a random file)
            if (cr.getStdout().length() < 1000) {
                CLog.e("Stdout: " + cr.getStdout());
            } else {
                CLog.e("Stdout (first 1000 chars): " + cr.getStdout().subSequence(0, 1000));
            }
            CLog.e("Stderr: " + cr.getStderr());
        }
    }

    private long timedRunCommand(String[] cmd) {
        long t0 = System.nanoTime();
        CommandResult cr = mRunUtil.runTimedCmd(mTimeout, cmd);
        long t1 = System.nanoTime();
        verifyCommandResult(cmd, cr);
        return t1 - t0;
    }

    private long timedRunCommand(OutputStream stdout, OutputStream stderr, String[] cmd) {
        long t0 = System.nanoTime();
        CommandResult cr = mRunUtil.runTimedCmd(mTimeout, stdout, stderr, cmd);
        long t1 = System.nanoTime();
        verifyCommandResult(cmd, cr);
        return t1 - t0;
    }

    /** Run cmd1 and redirect its stdout to cmd2 as input (i.e. cmd1 | cmd2). */
    private long timedRun2CommandsWithCommand1StdoutAsCommand2Stdin(String[] cmd1, String[] cmd2) {
        long t0 = System.nanoTime();
        CommandResult cr1 = mRunUtil.runTimedCmd(mTimeout, cmd1);
        CommandResult cr2 = mRunUtil.runTimedCmdWithInput(mTimeout, cr1.getStdout(), cmd2);
        long t1 = System.nanoTime();
        verifyCommandResult(cmd1, cr1);
        verifyCommandResult(cmd2, cr2);
        return t1 - t0;
    }

    private void testPushPullFile(int size) {
        File testFile = createSizedTempFile(size);

        String[] cmdPush = {mAdbPath, "push", testFile.getAbsolutePath(), mDeviceTestFilePath};
        long tPush = timedRunCommand(cmdPush);
        putResult(String.format(ADB_PUSH_ONE, FileUtil.convertToReadableSize(size)), tPush);

        String[] cmdPull = {mAdbPath, "pull", mDeviceTestFilePath, testFile.getAbsolutePath()};
        long tPull = timedRunCommand(cmdPull);
        putResult(String.format(ADB_PULL_ONE, FileUtil.convertToReadableSize(size)), tPull);

        FileUtil.deleteFile(testFile);
        runCommand(mRmDeviceTestFile);
    }

    private void testShellCatPushPullFile(int size) {
        File testFile = createSizedTempFile(size);

        // cat foo.txt | adb shell 'cat > bar.txt'
        String[] cmdPush1 = {"cat", testFile.getAbsolutePath()};
        String[] cmdPush2 = {mAdbPath, "shell", "cat", ">", mDeviceTestFilePath};
        long tPush = timedRun2CommandsWithCommand1StdoutAsCommand2Stdin(cmdPush1, cmdPush2);
        putResult(String.format(SHELL_PUSH_ONE, FileUtil.convertToReadableSize(size)), tPush);

        // adb shell cat bar.txt > foo.txt
        String[] cmdPull = {mAdbPath, "shell", "cat", mDeviceTestFilePath};
        try (FileOutputStream outputStream = new FileOutputStream(testFile.getAbsoluteFile())) {
            long tPull = timedRunCommand(outputStream, null, cmdPull);
            putResult(String.format(SHELL_PULL_ONE, FileUtil.convertToReadableSize(size)), tPull);
        } catch (IOException e) {
            CLog.e(e);
        }

        FileUtil.deleteFile(testFile);
        runCommand(mRmDeviceTestFile);
    }

    private void testPushPullMultipleFiles(int sizeEach, int n) {
        File testDir = createTempDirWithSizedFiles(sizeEach, n);
        runCommand(mRmDeviceTestDir);
        runCommand(mMkdirDeviceTestDir);

        // if we don't add the "/." adb won't push the whole thing. (And has to be "/." not "/")
        String[] cmdPush = {mAdbPath, "push", testDir.getAbsolutePath() + "/.", mDeviceTestDirPath};
        long tPush = timedRunCommand(cmdPush);
        putResult(
                String.format(ADB_PUSH_MULTIPLE, n, FileUtil.convertToReadableSize(sizeEach)),
                tPush);

        String[] cmdPull = {mAdbPath, "pull", mDeviceTestDirPath + "/.", testDir.getAbsolutePath()};
        long tPull = timedRunCommand(cmdPull);
        putResult(
                String.format(ADB_PULL_MULTIPLE, n, FileUtil.convertToReadableSize(sizeEach)),
                tPull);

        FileUtil.recursiveDelete(testDir);
        runCommand(mRmDeviceTestDir);
    }
}
