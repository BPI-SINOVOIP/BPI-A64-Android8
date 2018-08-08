// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.StringEscapeUtils;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link IRemoteTest} class to launch a command from TFC via a subprocess TF. FIXME: this needs
 * to be extended to support multi-device tests.
 */
@OptionClass(alias = "cluster", global_namespace = false)
public class ClusterCommandLauncher implements IRemoteTest {

    @Option(name = "root-dir", description = "A root directory", mandatory = true)
    private File mRootDir;

    @Option(name = "env-var", description = "Environment variables")
    private Map<String, String> mEnvVars = new HashMap<>();

    @Option(name = "setup-script", description = "Setup scripts")
    private List<String> mSetupScripts = new ArrayList<>();

    @Option(
        name = "command-line",
        description = "An original command line to launch.",
        mandatory = true
    )
    private String mCommandLine = null;

    @Option(
        name = "command-timeout",
        description = "The maximum time to allow a command to run",
        isTimeVal = true
    )
    private long mCommandTimeout = 24 * 60 * 60 * 1000;

    private IRunUtil mRunUtil;

    /**
     * Expands environment variables in a string.
     *
     * @param str a string with environment variables.
     * @param envVars a map of environment vairables.
     * @return an expanded string.
     */
    private String expandEnvVars(final String str, Map<String, String> envVars) {
        final StringBuffer sb = new StringBuffer();
        final Pattern p = Pattern.compile("\\$\\{(.+)\\}");
        final Matcher m = p.matcher(str);
        while (m.find()) {
            final String key = m.group(1);
            final String value = envVars.getOrDefault(key, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mEnvVars.put("TF_WORK_DIR", mRootDir.getAbsolutePath());
        String tfPath = mEnvVars.get("TF_PATH");
        if (tfPath == null) {
            tfPath = System.getenv("TF_PATH");
        }
        tfPath = expandEnvVars(tfPath, mEnvVars);
        CLog.i("TF_PATH=%s", tfPath);
        final List<String> jars = new ArrayList<>();
        final File[] files = new File(tfPath).listFiles();
        if (files == null) {
            throw new RuntimeException("cannot find any files under TF_PATH");
        }
        for (final File file : files) {
            if (file.getName().endsWith(".jar")) {
                jars.add(file.getAbsolutePath());
            }
        }
        final String classpath = ArrayUtil.join(":", jars);
        final List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add("java");
        cmdArgs.add("-cp");
        cmdArgs.add(classpath);
        for (final Entry<String, String> entry : mEnvVars.entrySet()) {
            final String value = expandEnvVars(entry.getValue(), mEnvVars);
            cmdArgs.add(String.format("-D%s=%s", entry.getKey(), value));
        }
        cmdArgs.add("com.android.tradefed.command.CommandRunner");
        cmdArgs.addAll(StringEscapeUtils.paramsToArgs(ArrayUtil.list(mCommandLine)));

        IRunUtil runUtil = getRunUtil();
        runUtil.setWorkingDir(mRootDir);
        // clear the TF_GLOBAL_CONFIG env, so another tradefed will not reuse the global config file
        runUtil.unsetEnvVariable("TF_GLOBAL_CONFIG");

        // FIXME: Run setup scripts

        File logDir = new File(mRootDir, "logs");
        File stdoutFile = null;
        File stderrFile = null;
        FileOutputStream stdout = null;
        FileOutputStream stderr = null;
        try {
            stdoutFile = new File(logDir, "stdout.txt");
            stderrFile = new File(logDir, "stderr.txt");
            stderr = new FileOutputStream(stderrFile);
            stdout = new FileOutputStream(stdoutFile);

            CLog.i("Running a command line: %s", mCommandLine);
            CLog.i("args = %s", cmdArgs);
            CommandResult result =
                    getRunUtil()
                            .runTimedCmd(
                                    mCommandTimeout,
                                    stdout,
                                    stderr,
                                    cmdArgs.toArray(new String[0]));
            if (!result.getStatus().equals(CommandStatus.SUCCESS)) {
                String error = null;
                if (result.getStatus().equals(CommandStatus.TIMED_OUT)) {
                    error = "timeout";
                } else {
                    error = FileUtil.readStringFromFile(stderrFile);
                }
                throw new RuntimeException(String.format("Command failed to run: %s", error));
            }
            CLog.i("Successfully ran a command");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            StreamUtil.close(stdout);
            StreamUtil.close(stderr);
        }
    }

    /**
     * Returns a {@link IRunUtil} object.
     *
     * @return a {@link IRunUtil} object.
     */
    IRunUtil getRunUtil() {
        if (mRunUtil == null) {
            mRunUtil = new RunUtil();
        }
        return mRunUtil;
    }
}
