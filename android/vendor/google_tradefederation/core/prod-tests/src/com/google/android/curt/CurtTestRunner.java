// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.curt;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IMultiDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.google.android.tradefed.util.EarCompressionStrategy;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CURT test runner is the main test runner which do the followings: 1) Prepare CURT command based
 * on specified options. 2) Launch the CURT binary and wait until it finished. 3) Log the stdout and
 * stderr of binary, as long as CURT report.
 */
public class CurtTestRunner implements IMultiDeviceTest, IRemoteTest {

    @Option(name = "binary-bin", description = "The path to binary file.")
    private String mBinaryBin = "/google/data/ro/teams/wear-eng-prod/curt";

    @Option(
        name = "crawler-config",
        description = "Path to the JSON file containing the crawler config.",
        mandatory = true,
        importance = Importance.ALWAYS
    )
    private String mCrawlerConfig;

    @Option(name = "debug-mode", description = "Whether to print debug message in output.")
    private Boolean mDebugMode = true;

    @Option(
        name = "enable-now",
        description = "True if the Google Now card pusher should be enabled for the test."
    )
    private Boolean mEnableNow = false;

    @Option(name = "report-title", description = "Customize the report title")
    private String mReportTitle = "CURT Report";

    @Option(name = "test-timeout", description = "A timeout in MS for CURT binary to run.")
    private long mTestTimeoutMs = 3600 * 1000; // 1h

    @Option(
        name = "extra-args",
        description = "Any extra arguments that need to pass to CURT binary. E.g. key=value"
    )
    private Map<String, String> mExtraArgs = new HashMap<>();

    private Map<ITestDevice, IBuildInfo> mTestDeviceInfo = null;

    public Map<ITestDevice, IBuildInfo> getDeviceInfos() {
        return mTestDeviceInfo;
    }

    @Override
    public void setDeviceInfos(Map<ITestDevice, IBuildInfo> map) {
        mTestDeviceInfo = map;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        long start = System.currentTimeMillis();
        String runName = mCrawlerConfig.split("\\.")[0];
        if (runName.equals("")) {
            runName = "curt_test";
        }
        listener.testRunStarted(runName, 1);
        TestIdentifier id = new TestIdentifier(getClass().getCanonicalName(), runName);
        listener.testStarted(id);

        try {
            File tempFolder = FileUtil.createTempDir("curt");
            CommandResult cr =
                    RunUtil.getDefault()
                            .runTimedCmd(
                                    mTestTimeoutMs,
                                    constructCommand(tempFolder.getAbsolutePath())
                                            .toArray(new String[0]));
            CommandStatus cs = cr.getStatus();
            InputStreamSource stdoutStream =
                    new ByteArrayInputStreamSource(cr.getStdout().getBytes());
            listener.testLog("Binary_stdout", LogDataType.TEXT, stdoutStream);
            InputStreamSource stderrStream =
                    new ByteArrayInputStreamSource(cr.getStderr().getBytes());
            listener.testLog("Binary_stderr", LogDataType.TEXT, stderrStream);
            if (cs == CommandStatus.SUCCESS) {
                File curtReportFile = new File(String.format("%s/curt_report.html", tempFolder));
                if (curtReportFile.exists()) {
                    CLog.d("Test run successfully.");
                    EarCompressionStrategy ecs = new EarCompressionStrategy();
                    File archivedCurtReport = ecs.compress(tempFolder);
                    InputStreamSource reportStream = new FileInputStreamSource(archivedCurtReport);
                    listener.testLog("Curt_report", ecs.getLogDataType(), reportStream);
                } else {
                    listener.testFailed(
                            id,
                            String.format(
                                    "Cannot get report file: %s",
                                    curtReportFile.getAbsolutePath()));
                }
            } else {
                listener.testFailed(
                        id,
                        String.format(
                                "CURT test failed to run. Please check log. "
                                        + "Command status is %s",
                                cs.toString()));
            }
            listener.testEnded(id, Collections.emptyMap());

            listener.testRunEnded(System.currentTimeMillis() - start, Collections.emptyMap());
        } catch (IOException e) {
            CLog.e(e);
        }
    }

    List<String> constructCommand(String outputFolder) {
        List<String> commandBuilder = new ArrayList<>();
        commandBuilder.add(mBinaryBin);
        commandBuilder.add("--crawler_config");
        commandBuilder.add(mCrawlerConfig);
        if (mDebugMode) {
            commandBuilder.add("--debug");
        }
        if (mEnableNow) {
            commandBuilder.add("--enable_now");
        }
        commandBuilder.add("--output_dir");
        commandBuilder.add(outputFolder);
        commandBuilder.add("--report_title");
        commandBuilder.add(mReportTitle);
        commandBuilder.add("--serials");
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<ITestDevice, IBuildInfo> e : getDeviceInfos().entrySet()) {
            if (!sb.toString().equals("")) {
                sb.append(", ");
            }
            sb.append(e.getKey().getSerialNumber());
        }
        commandBuilder.add(sb.toString());
        for (String s : mExtraArgs.keySet()) {
            commandBuilder.add(String.format("--%s", s));
            if (mExtraArgs.get(s) != null) {
                commandBuilder.add(mExtraArgs.get(s));
            }
        }
        CLog.d("Final command to run is %s", commandBuilder.toString());
        return commandBuilder;
    }
}
