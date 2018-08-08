/*
 * Copyright 2017 Google Inc. All Rights Reserved
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
package com.google.android.paintbox;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/** A harness that launches tests on easel and reports results. */
public final class HammerTests implements IRemoteTest, IDeviceTest {

    private static final String PAINTBOX_X20_DIR = "/google/data/ro/teams/paintbox/tf_tests/";
    private static final String BUILD_DIR = "build-elinux-release-android";
    private static final String HOST_SIDE_DIR =
            System.getProperty("java.io.tmpdir") + "/EaselTestOutputs/";
    private static final String AP_SIDE_DIR = "/data/local/tmp/";
    private static String mTestLog;
    private ITestDevice mTestDevice = null;
    private List<File> mTestOutputs = new ArrayList<File>();

    @Option(name = "retry-time", description = "How many times we want to retry the failed tests.")
    private int mRetryLimit = 3;

    @Option(
        name = "log-dir",
        description = "Log directory for tests",
        importance = Importance.ALWAYS
    )
    private String mLogDir = System.getProperty("java.io.tmpdir");

    @Option(
        name = "firmware-dir",
        description = "Full path of easel firmware directory on host machine",
        importance = Importance.ALWAYS
    )
    private String mFirmwareDir =
            PAINTBOX_X20_DIR + "easel_software/system/output/mnh-busybox/uboot";

    @Option(
        name = "google3-dir",
        description = "Full path of Google3 top directory",
        importance = Importance.ALWAYS
    )
    private String mGoogle3TopDir = PAINTBOX_X20_DIR + "google3";

    @Option(
        name = "ipu-dir",
        description = "Full path of IPU top directory",
        importance = Importance.ALWAYS
    )
    private String mIpuTopDir = PAINTBOX_X20_DIR + "easel_software/IPU";

    @Option(
        name = "ipu-test",
        description = "Run Paintbox IPU tests",
        importance = Importance.ALWAYS
    )
    private boolean mIpuTest = false;

    @Option(
        name = "total-timeout",
        description = "Timeout set to run a test config, e.g., 30m15s",
        isTimeVal = true,
        importance = Importance.ALWAYS
    )
    private long mTotalTimeout = 30 * 60 * 1000; // 30m

    // Test configuration for each test
    @Option(name = "run-key", description = "Run key for the test", importance = Importance.ALWAYS)
    private String mRunKey = "HammerTests";

    @Option(
        name = "test-command",
        description = "Test command to run a test",
        importance = Importance.ALWAYS
    )
    private String mTestCommand = "ctest";

    @Option(
        name = "test-log-name",
        description = "Log file name for the test",
        importance = Importance.ALWAYS
    )
    private String mTestLogName = "test.log";

    @Option(
        name = "test-timeout",
        description = "Timeout for test, e.g., 2m1s",
        isTimeVal = true,
        importance = Importance.ALWAYS
    )
    private long mTestTimeout = 10 * 1000; // 10s

    @Option(
        name = "test-output-files",
        description = "Test output files",
        importance = Importance.ALWAYS
    )
    private List<String> mTestOutputFiles = new ArrayList<String>();

    @Option(
        name = "test-output-files-full-path",
        description = "Test output files with full path that to be pulled out from the device",
        importance = Importance.ALWAYS
    )
    private List<String> mTestOutputFilesFullPath = new ArrayList<String>();

    @Option(
        name = "test-output-dirs",
        description = "Test output dirs",
        importance = Importance.ALWAYS
    )
    private List<String> mTestOutputDirs = new ArrayList<String>();

    @Option(
        name = "test-output-dirs-full-path",
        description = "Test output dirs with full path that to be pulled out from the device",
        importance = Importance.ALWAYS
    )
    private List<String> mTestOutputDirsFullPath = new ArrayList<String>();

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {

        mTestLog = mLogDir + "/" + mTestLogName;
        mTestCommand = getFullPath(mTestCommand);
        mTestOutputFiles = getFullPath(mTestOutputFiles);
        mTestOutputDirs = getFullPath(mTestOutputDirs);
        mTestOutputFilesFullPath.addAll(mTestOutputFiles);
        mTestOutputDirsFullPath.addAll(mTestOutputDirs);
        CLog.d(getTestOutputPath());

        TestIdentifier testId = new TestIdentifier(getClass().getCanonicalName(), mRunKey);
        ITestDevice device = getDevice();
        long testStartTime = System.currentTimeMillis();
        // create test output temporary directory on host
        File hostOutputDir = null;

        try {
            hostOutputDir = new File(HOST_SIDE_DIR);
            FileUtil.recursiveDelete(hostOutputDir);
            hostOutputDir.mkdirs();

            CLog.d("Start to run tests on Easel");
            listener.testRunStarted(mRunKey, 1);
            listener.testStarted(testId);

            CollectingOutputReceiver receiver = new CollectingOutputReceiver();

            String cmd = getAdbShellCommand();
            CLog.d("Executing: " + cmd);
            device.executeShellCommand(cmd, receiver, mTotalTimeout, TimeUnit.SECONDS, mRetryLimit);

            File testLog = new File(mTestLog);
            // Pull out test log file to host machine
            if (!device.pullFile(AP_SIDE_DIR + mTestLog, testLog)) {
                CLog.e("ERROR: Failed to pull out test log to " + mTestLog);
            }
            device.executeShellCommand(String.format("rm -rf %s", mTestLog));
            // Pull out requested test output files
            pullFileFromEasel(device, mTestOutputFilesFullPath, true); // true: file
            pullFileFromEasel(device, mTestOutputDirsFullPath, false); // false: dir

            if (testLog.exists() && logHasMessage(mTestLog, "PASSED")) {
                CLog.i("[  PASSED  ] Paintbox test: " + mTestCommand + " passed.");
                listener.testEnded(testId, Collections.emptyMap());
            } else {
                CLog.e(receiver.getOutput());
                String errMsg = "[  FAILED  ] Failed to run test: " + mTestCommand;
                CLog.e(errMsg);
                listener.testFailed(testId, errMsg);
                listener.testEnded(testId, Collections.emptyMap());
                listener.testRunFailed(errMsg);
            }

            if (testLog.exists()) {
                try (FileInputStreamSource logFile = new FileInputStreamSource(testLog, true)) {
                    listener.testLog("easel_log", LogDataType.TEXT, logFile);
                }
                FileUtil.deleteFile(testLog);
            }

            // save output files if any
            listFilesAndFilesSubDirectories(HOST_SIDE_DIR);
            for (File output : mTestOutputs) {
                try (FileInputStreamSource outputFile = new FileInputStreamSource(output, false)) {
                    String fileName = FileUtil.getBaseName(output.getName());
                    listener.testLog(
                            fileName, getContentType(output.getAbsolutePath()), outputFile);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            // remove everything inside HOST_SIDE_DIR
            FileUtil.recursiveDelete(hostOutputDir);

            long durationMs = System.currentTimeMillis() - testStartTime;
            listener.testRunEnded(durationMs, Collections.emptyMap());
        }
    }

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    private boolean pullFileFromEasel(ITestDevice device, List<String> filePaths, boolean isFile)
            throws DeviceNotAvailableException {
        for (String filePath : filePaths) {
            if (!pullFileFromEasel(device, filePath, isFile)) {
                return false;
            }
        }
        return true;
    }

    private boolean pullFileFromEasel(ITestDevice device, String filePath, boolean isFile)
            throws DeviceNotAvailableException {
        for (int i = 0; i < mRetryLimit; i++) {
            boolean succeed = true;
            pullFileFromEaselToDevice(device, filePath, isFile);
            if (!pullFileFromDeviceToHost(device, filePath, isFile)) {
                CLog.e("ERROR: Failed to pull <%s> from device to host", filePath);
                succeed = false;
            }
            if (succeed) {
                return succeed;
            }
        }
        return false;
    }

    private void pullFileFromEaselToDevice(ITestDevice device, String filePath, boolean isFile)
            throws DeviceNotAvailableException, RuntimeException {
        File file = new File(filePath);
        String devicePath = (isFile ? filePath : file.getParent());
        String cmd = String.format("ezlsh pull %s %s", filePath, AP_SIDE_DIR + devicePath);
        device.executeShellCommand(cmd);
    }

    private boolean pullFileFromDeviceToHost(ITestDevice device, String filePath, boolean isFile)
            throws DeviceNotAvailableException, RuntimeException {
        if (isFile) {
            return device.pullFile(AP_SIDE_DIR + filePath, new File(HOST_SIDE_DIR + filePath));
        } else {
            return device.pullDir(AP_SIDE_DIR + filePath, new File(HOST_SIDE_DIR));
        }
    }

    private String getAdbShellCommand() {
        // If we run IPU tests with ctest, we have to go to build directory
        String easelCommand = "";
        if (mIpuTest && mTestCommand.contains("ctest")) {
            easelCommand = String.format("cd %s && ", mIpuTopDir + "/" + BUILD_DIR);
        }
        // Find where the binary is located, and run commmand from there.
        // It's due to some tests, e.g., burst process, run test with the script
        // with many path variables relative to where the script is.
        if (!mTestCommand.contains("ctest")) {
            String binaryDirName = mTestCommand.substring(0, mTestCommand.lastIndexOf("/"));
            easelCommand += String.format("cd %s && ", binaryDirName);
        }
        easelCommand += mTestCommand;

        return String.format(
                "pbticlient --command '%s' --log_path '%s' --timeout_seconds %d",
                easelCommand, AP_SIDE_DIR + mTestLog, mTestTimeout);
    }

    private boolean logHasMessage(String logPath, String msg) throws FileNotFoundException {
        File logFile = new File(logPath);
        try (Scanner scanner = new Scanner(logFile)) {
            while (scanner.hasNextLine()) {
                final String lineFromFile = scanner.nextLine();
                if (lineFromFile.contains(msg)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getFullPath(String relativePath) {
        return (mIpuTest ? mIpuTopDir : mGoogle3TopDir) + "/" + relativePath;
    }

    private List<String> getFullPath(List<String> relativePaths) {
        String dirName = (mIpuTest ? mIpuTopDir : mGoogle3TopDir);
        List<String> fullPaths = new ArrayList<String>();
        for (String relativePath : relativePaths) {
            fullPaths.add(dirName + "/" + relativePath);
        }
        return fullPaths;
    }

    public void listFilesAndFilesSubDirectories(String dirName) {
        File directory = new File(dirName);
        File[] fList = directory.listFiles();
        for (File file : fList) {
            if (file.isFile()) {
                mTestOutputs.add(file);
            } else if (file.isDirectory()) {
                listFilesAndFilesSubDirectories(file.getAbsolutePath());
            }
        }
    }

    private String getTestOutputPath() {
        String configString = "\n*********************************\n";
        for (String output : mTestOutputFilesFullPath) {
            configString += "Output File: " + output + "\n";
        }
        for (String output : mTestOutputDirsFullPath) {
            configString += "Output Dir: " + output + "\n";
        }
        configString += "*********************************\n";

        return configString;
    }

    private LogDataType getContentType(String filePath) {
        String ext = FileUtil.getExtension(filePath);
        // LogDataType doesn't have .jpg and .log extension, so check first
        if (".jpg".equals(ext)) {
            return LogDataType.JPEG;
        }
        if (".log".equals(ext)) {
            return LogDataType.TEXT;
        }

        LogDataType[] dataTypes = LogDataType.values();
        for (LogDataType dataType : dataTypes) {
            if (ext.equals(dataType.getFileExt())) {
                return dataType;
            }
        }

        return LogDataType.UNKNOWN;
    }
}
