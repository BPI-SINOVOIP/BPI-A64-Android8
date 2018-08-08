// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.clockwork;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.google.android.tradefed.testtype.ClockworkTest;
import com.google.android.tradefed.util.RollupClFetcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WEED (Wear End-to-End test Driver) test driver in TradeFed.
 *
 * <p>WEED is an automated end-to-end test driver, which can send adb commands to multiple devices
 * on the linux host. It is developed and maintained by the Android wear team. For more information,
 * please refer to go/cw-weed.
 *
 * <p>WeedTestRunner is the integration of WEED into TradeFed so WEED tests can be in the lab.
 */
public class WeedTestRunner extends ClockworkTest {

    private static final Pattern TEST_NAME_PATTERN =
            Pattern.compile(".*/(\\w+)_binary_deploy\\.jar");
    private static final String FILE_NAME_PATTERN_STRING = "(\\w+)_binary_deploy\\.jar";
    private static final String UNDECLARAED_FOLDER_ENV = "TEST_UNDECLARED_OUTPUTS_DIR";
    private static final String FAILURE_KEY = "Failure";
    private static final String HOME_APK_ROLLUP_CL_KEY = "home_apk_rollup_cl";
    private static final String CLOCKWORK_HOME_PROJECT_NAME = "clockwork.home";
    private static final String CLOCKWORK_MPMS_PATH_NAME =
            "/google/data/ro/teams/clockwork/apks/home/armeabi-v7a/";
    private static final String CLOCKWORK_LATEST_MPM_PATH_NAME =
            CLOCKWORK_MPMS_PATH_NAME + "latest/";
    private static final String UNKNOWN_CL_NUMBER = "0";
    private static final Pattern CLOCKWORK_HOME_CANDIDATE_NAME_PATTERN =
            Pattern.compile(String.format("^%s.*?_RC[0-9][0-9]$", CLOCKWORK_HOME_PROJECT_NAME));

    @Option(name = "adb-path", description = "adb instance path for interacting with device.")
    private String mAdbPath = "adb";

    @Option(
        name = "extra-args",
        description = "Any extra arguments that need to pass to WEED binary. E.g. key=value"
    )
    private Map<String, String> mExtraArgs = new HashMap<>();

    @Option(
        name = "test-jar-path",
        description = "Path to test jar or folder. Tests in folder will be run in series.",
        mandatory = true,
        importance = Importance.ALWAYS
    )
    private String mTestJarPath;

    @Option(name = "test-run-name", description = "Name to identify test run")
    private String mTestRunName = "WeedTest";

    @Option(
        name = "test-timeout",
        description = "A timeout in MS for single WEED test jar to run.",
        isTimeVal = true
    )
    private long mTestTimeoutMs = 10 * 60 * 1000; // 10m

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        long start = System.currentTimeMillis();
        try {
            File testFolderFile = new File(mTestJarPath);
            Set<String> testJarSet = FileUtil.findFiles(testFolderFile, FILE_NAME_PATTERN_STRING);
            listener.testRunStarted(mTestRunName, testJarSet.size());
            String runName;
            Map<String, String> resultMap = new HashMap<>();
            resultMap.put(HOME_APK_ROLLUP_CL_KEY, findHomeApkRollupCl());
            for (String testJar : testJarSet) {
                File tempFolder = null;
                int failureCount = 0;
                try {
                    Matcher testNameMatcher = TEST_NAME_PATTERN.matcher(testJar);
                    if (testNameMatcher.matches()) {
                        runName = testNameMatcher.group(1);
                    } else {
                        runName = mTestRunName;
                    }
                    TestIdentifier id = new TestIdentifier(getClass().getCanonicalName(), runName);
                    listener.testStarted(id);
                    tempFolder = FileUtil.createTempDir(runName);
                    ByteArrayOutputStream stderrOutputStream = new ByteArrayOutputStream();
                    ByteArrayOutputStream stdoutOutputStream = new ByteArrayOutputStream();
                    IRunUtil runUtil = new RunUtil();
                    runUtil.setEnvVariable(UNDECLARAED_FOLDER_ENV, tempFolder.getAbsolutePath());
                    CommandResult cr =
                            runUtil.runTimedCmd(
                                    mTestTimeoutMs,
                                    stdoutOutputStream,
                                    stderrOutputStream,
                                    constructCommand(testJar).toArray(new String[0]));
                    CommandStatus cs = cr.getStatus();
                    InputStreamSource stdoutStream =
                            new ByteArrayInputStreamSource(stdoutOutputStream.toByteArray());
                    listener.testLog(
                            String.format("%s_stdout", runName), LogDataType.TEXT, stdoutStream);
                    InputStreamSource stderrStream =
                            new ByteArrayInputStreamSource(stderrOutputStream.toByteArray());
                    listener.testLog(
                            String.format("%s_stderr", runName), LogDataType.TEXT, stderrStream);
                    try (DirectoryStream<Path> stream =
                            Files.newDirectoryStream(Paths.get(tempFolder.getAbsolutePath()))) {
                        for (Path filesPath : stream) {
                            InputStreamSource unDeclaredOutputFileStream =
                                    new FileInputStreamSource(filesPath.toFile());
                            listener.testLog(
                                    String.format(
                                            "%s_%s", runName, filesPath.getFileName().toString()),
                                    LogDataType.TEXT,
                                    unDeclaredOutputFileStream);
                            unDeclaredOutputFileStream.cancel();
                        }
                    } catch (IOException e) {
                        CLog.e("Cannot read unDeclaredOutputFolder. %s", e);
                    }
                    String metricKey = String.format("%s_%s", runName, FAILURE_KEY);
                    if (cs != CommandStatus.SUCCESS) {
                        failureCount++;
                        listener.testFailed(
                                id,
                                String.format(
                                        "WEED test failed to run. Command status is %s",
                                        cs.toString()));
                    } else if (stdoutOutputStream.toString().contains("testFailure")) {
                        // TODO (yuanlang) Parse binary output to get useful information. b/37514350.
                        // Right now only test if a test failed or not.
                        failureCount++;
                        listener.testFailed(id, String.format("Test failed. Please check log"));
                    }
                    resultMap.put(metricKey, String.valueOf(failureCount));
                    listener.testEnded(id, Collections.emptyMap());
                } catch (IOException e) {
                    CLog.d("Cannot create temp file. Error: %s", e.toString());
                } finally {
                    FileUtil.recursiveDelete(tempFolder);
                }
            }
            CLog.d("Reporting matrix: %s", resultMap.toString());
            listener.testRunEnded(System.currentTimeMillis() - start, resultMap);
        } catch (IOException e) {
            CLog.e("IOException when to find test jar folder. %s", e);
        }
    }

    @VisibleForTesting()
    List<String> constructCommand(String testJar) {
        List<String> commandBuilder = new ArrayList<>();
        commandBuilder.add("java");
        commandBuilder.add("-jar");
        commandBuilder.add(testJar);
        commandBuilder.add("--watchId");
        commandBuilder.add(getDevice().getSerialNumber());
        commandBuilder.add("--androidPhoneId");
        commandBuilder.add(getCompanion().getSerialNumber());
        commandBuilder.add("--adbPath");
        commandBuilder.add(mAdbPath);
        for (String s : mExtraArgs.keySet()) {
            commandBuilder.add(String.format("--%s", s));
            if (mExtraArgs.get(s) != null) {
                commandBuilder.add(mExtraArgs.get(s));
            }
        }
        CLog.d("Final command to run is %s", commandBuilder.toString());
        return commandBuilder;
    }

    private String findHomeApkRollupCl() {
        Path latestPath = Paths.get(CLOCKWORK_LATEST_MPM_PATH_NAME);
        Path apkPath = Paths.get(CLOCKWORK_MPMS_PATH_NAME);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(apkPath)) {
            Path latestLinkDestPath = Files.readSymbolicLink(latestPath);
            for (Path rcFilePath : stream) {
                if (Files.isSymbolicLink(rcFilePath)
                        && Files.readSymbolicLink(rcFilePath)
                                .toString()
                                .equals(latestLinkDestPath.toString())) {
                    String candidateName = rcFilePath.getFileName().toString();
                    Matcher m = CLOCKWORK_HOME_CANDIDATE_NAME_PATTERN.matcher(candidateName);
                    if (!m.matches()) {
                        CLog.e("Candidate name %s doesn't have correct format.", candidateName);
                        return UNKNOWN_CL_NUMBER;
                    }
                    // Remove last five characters "_RC00" to get release name
                    String releaseName = candidateName.substring(0, candidateName.length() - 5);
                    CLog.d(
                            "Calling util method to get cl number for releaseName: %s,"
                                    + "candidateName: %s",
                            releaseName, candidateName);
                    return new RollupClFetcher()
                            .fetch(CLOCKWORK_HOME_PROJECT_NAME, releaseName, candidateName);
                }
            }
        } catch (IOException ioe) {
            CLog.e(ioe);
        } catch (RollupClFetcher.RollupClException re) {
            CLog.e(re);
        }
        return UNKNOWN_CL_NUMBER;
    }
}
