// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IStrictShardableTest;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.StringEscapeUtils;
import com.android.tradefed.util.SubprocessTestResultsParser;
import com.android.tradefed.util.TimeUtil;
import com.android.tradefed.util.UniqueMultiMap;

import com.google.common.annotations.VisibleForTesting;

import org.junit.Assert;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link IRemoteTest} for running tests against a separate CTS installation.
 *
 * <p>Launches an external java process to run the tests. Used for running a specific downloaded
 * version of CTS and its configuration It will use the current version of tradefed that launch it
 * to complete missing jars
 */
public class CtsTestLauncher
        implements IRemoteTest,
                IDeviceTest,
                IBuildReceiver,
                IStrictShardableTest,
                IInvocationContextReceiver,
                IConfigurationReceiver {

    private static final String TF_GLOBAL_CONFIG = "TF_GLOBAL_CONFIG";
    private static final String TF_JAR_DIR = "TF_JAR_DIR";
    private static final int MIN_CTS_VERSION = 1;
    private static final int MAX_CTS_VERSION = 2;
    /** Timeout to wait for the events received from subprocess to finish being processed.*/
    private static final long EVENT_THREAD_JOIN_TIMEOUT_MS = 60 * 1000;

    @Option(name = "max-run-time", isTimeVal = true,
            description = "the maximum time to allow the CTS runner (sub process) to run,"
            + "supports complex time format like 1h30m15s")
    private long mMaxTfRunTime = 3600000L;

    @Option(name = "config-name", description = "the config that runs the TF tests",
            mandatory = true)
    private String mConfigName;

    // Provide the reporter to build the --template:map option
    @Option(name = "reporter-template", shortName = 't',
            description = "use a specific reporter for the template")
    private String mReporterName = null;

    @Option(
        name = "coverage-reporter-template",
        description = "use a coverage reporter for the template"
    )
    private String mCoverageReporterName = null;

    @Option(
        name = "metadata-reporter-template",
        description = "use a metadata reporter for the template"
    )
    private String mMetadataReporterName = null;

    @Option(name = "run-jar", description = "additional jars required to run the sub process,"
            + "assume jars are under a base dir")
    private List<String> mRunJar = new ArrayList<>();

    @Option(name = "throw-if-jar-not-found", description = "Throw an exception if one of the jar "
            + "specified by run-jar is not found.")
    private boolean mThrowIfJarNotFound = true;

    // represents all the args to be passed to the sub process
    // example:  --cts-params "--plan CTS-hardware --disable-reboot"
    @Option(name = "cts-params", description = "All the cts parameters to feed the sub process")
    private List<String> mCtsParams = new ArrayList<>();

    @Option(name = "cts-version", description = "Integer representing the version of cts to use."
            + "Default is version 1.")
    private int mCtsVersion = MIN_CTS_VERSION;

    @Option(name = "rootdir-var", description = "Name of the variable to be passed as -D "
            + "parameter to the java call to specify the root directory.")
    private String mRootdirVar = "CTS_ROOT";

    @Option(name = "run-as-root", description = "If sub process CTS should be triggered with root "
            + "identity.")
    private boolean mRunAsRoot = true;

    @Option(name = "report-subprocess-events", description = "If the sub process test events "
            + "should be mirrored at parent process side.")
    private boolean mReportSubprocessEvents = true;

    @Option(name = "need-device", description =
            "flag if the subprocess is going to need an actual device to run.")
    private boolean mNeedDevice = true;

    @Option(name = "use-event-streaming", description = "Use a socket to receive results as they"
            + "arrived instead of using a temporary file and parsing at the end.")
    private boolean mEventStreaming = false;

    @Option(
        name = "inject-invocation-data",
        description = "Pass the invocation-data to the subprocess if enabled."
    )
    private boolean mInjectInvocationData = false;

    @Option(name = "skip-build-info",
            description = "Don't use parameters to pass build info to sub process.")
    private boolean mSkipBuildInfo = false;

    @Option(
        name = "multi-devices",
        description = "Whether the subprocess requires multiple devices or not."
    )
    private boolean mMultiDevice = false;

    private IRunUtil mRunUtil;

    private IInvocationContext mContext = null;

    private IConfiguration mConfig = null;

    private CommandResult mResult = null;

    private IBuildInfo mBuildInfo = null;

    private ITestDevice mTestDevice = null;

    private int mShardCount = -1;

    private int mShardIndex = -1;

    private File mTmpDir;
    private File mHeapDump;

    public CtsTestLauncher() {
        super();
    }

    public CtsTestLauncher(int shardCount, int shardIndex) {
        this();
        mShardCount = shardCount;
        mShardIndex = shardIndex;
    }

    public CommandResult getResult() {
        return mResult;
    }

    public String getConfigName() {
        return mConfigName;
    }

    public void setConfigName(String config) {
        mConfigName = config;
    }

    protected void addRunJar(String jarName) {
        if (jarName != null)
            mRunJar.add(jarName);
    }

    public void setCtsVersion(int version) {
        mCtsVersion = version;
    }

    protected IRunUtil getRunUtil() {
        if (mRunUtil == null) {
            mRunUtil = new RunUtil();
        }
        return mRunUtil;
    }

    protected void setRunAsRoot(boolean runAsRoot) {
        mRunAsRoot = runAsRoot;
    }

    @Override
    public void setInvocationContext(IInvocationContext invocationContext) {
        mContext = invocationContext;
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }

    /**
     * Creates the classpath used by the sub process to run CTS
     *
     * @return A String with the full classpath.
     */
    public String buildClasspath() throws FileNotFoundException {
        List<File> classpathList = new ArrayList<>();

        // Recover the property (set in tradefed.sh) and build the path
        if (System.getProperty(TF_JAR_DIR) == null)
            throw new NullPointerException("TF_JAR_DIR env variable is not set");

        for (VersionedFile vf : mBuildInfo.getFiles()) {
            if (vf.getFile() != null && vf.getFile().getName().endsWith(".jar")) {
                File tempJar = vf.getFile();
                if (!tempJar.exists()) {
                    throw new FileNotFoundException("Couldn't find the jar file: " + tempJar);
                }
                classpathList.add(tempJar);
            }
        }

        if (!(mBuildInfo instanceof IFolderBuildInfo))
            throw new IllegalArgumentException("Build info needs to be of type IFolderBuildInfo");

        File ctsRoot = ((IFolderBuildInfo) mBuildInfo).getRootDir();
        if (!ctsRoot.exists()) {
            throw new FileNotFoundException("Couldn't find the build directory: " + ctsRoot);
        }

        // Safe to assume single dir from extracted zip
        if (ctsRoot.list().length != 1) {
            throw new RuntimeException("List of sub directory does not contain only one item "
                + "current list is:" + Arrays.toString(ctsRoot.list()));
        }
        String mainDirName = ctsRoot.list()[0];
        // Jar files from the downloaded cts/xts
        File jarCtsPath = new File(new File(ctsRoot, mainDirName), "tools");
        for (String jarFile : mRunJar) {
            File tempJar = new File(jarCtsPath, jarFile);
            if (!tempJar.exists()) {
                if (mThrowIfJarNotFound) {
                    throw new FileNotFoundException("Couldn't find the jar file: " + tempJar);
                } else {
                    CLog.w("Couldn't find the jar file: %s", tempJar);
                    continue;
                }
            }
            classpathList.add(tempJar);
        }

        if (mCtsVersion == 2) {
            // Cts V2 requires an additional path to be added
            File additionalPath = new File(new File(ctsRoot, mainDirName), "testcases");
            if (!additionalPath.exists()) {
                throw new FileNotFoundException("testcases directory not found for cts v2");
            }
            // include everything in the directory
            classpathList.add(new File(additionalPath, "/*"));
        }

        return ArrayUtil.join(":", classpathList);
    }

    /** Create a tmp dir and add it to the java options. */
    @VisibleForTesting
    void createSubprocessTmpDir(List<String> args) {
        try {
            mTmpDir = FileUtil.createTempDir("cts-subprocess-");
            args.add(String.format("-Djava.io.tmpdir=%s", mTmpDir.getAbsolutePath()));
        } catch (IOException e) {
            CLog.e(e);
            throw new RuntimeException(e);
        }
    }

    /** Create a tmp dir and add it to the java options. */
    @VisibleForTesting
    void createHeapDumpTmpDir(List<String> args) {
        try {
            mHeapDump = FileUtil.createTempDir("heap-dump");
            args.add("-XX:+HeapDumpOnOutOfMemoryError");
            args.add(String.format("-XX:HeapDumpPath=%s", mHeapDump.getAbsolutePath()));
        } catch (IOException e) {
            CLog.e(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates the java command line that will be run in a sub process.
     *
     * @param classpath java classpath to the jar files requires for CTS/XTS to run
     * @return An ArrayList<String> with the java command line and parameters.
     */
    public List<String> buildJavaCmd(String classpath) {
        List<String> args = new ArrayList<>();
        args.add("java");
        createHeapDumpTmpDir(args);
        createSubprocessTmpDir(args);
        args.add("-cp");
        args.add(classpath);

        if (mCtsVersion == 2) {
            // Cts V2 requires CTS_ROOT to be set or VTS_ROOT for vts run
            args.add(String.format("-D%s=%s", mRootdirVar,
                    ((IFolderBuildInfo)mBuildInfo).getRootDir().getAbsolutePath()));
        }

        args.add("com.android.tradefed.command.CommandRunner");
        args.add(mConfigName);

        if (mReporterName != null) {
            args.add("--template:map");
            args.add("reporters");
            // example: google/template/reporters/cts-google-reporters
            args.add(mReporterName);
        }
        if (mCoverageReporterName != null) {
            args.add("--template:map");
            args.add("coverage-reporter");
            args.add(mCoverageReporterName);
        }
        if (mMetadataReporterName != null) {
            args.add("--template:map");
            args.add("metadata-reporters");
            args.add(mMetadataReporterName);
        }

        // Tokenize args to be passed to CtsTest/XtsTest
        if (!mCtsParams.isEmpty()) {
            args.addAll(StringEscapeUtils.paramsToArgs(mCtsParams));
        }

        // ensure the sub process is always logging for debug purpose
        args.add("--log-level");
        args.add("VERBOSE");

        args.add("--log-level-display");
        args.add("VERBOSE");

        // always match the serial that was picked by the parent
        if (mNeedDevice) {
            // If we enabled multi-devices for subprocess and we have multiple devices we pass them
            // all
            if (mMultiDevice && mContext.getDevices().size() > 1) {
                for (ITestDevice device : mContext.getDevices()) {
                    args.add("--serial");
                    args.add(device.getSerialNumber());
                }
            } else {
                args.add("--serial");
                args.add(mTestDevice.getSerialNumber());
            }
        } else {
            args.add("-n");
        }

        if (mBuildInfo.getBuildBranch() != null) {
            args.add("--branch");
            args.add(mBuildInfo.getBuildBranch());
        }

        if (!mSkipBuildInfo) {
            if (mBuildInfo.getBuildId() != null) {
                args.add("--build-id");
                args.add(mBuildInfo.getBuildId());
            }

            if (mBuildInfo.getBuildFlavor() != null) {
                args.add("--build-flavor");
                args.add(mBuildInfo.getBuildFlavor());
            }

            if (mBuildInfo.getBuildAttributes().get("build_target") != null) {
                args.add("--build-attribute");
                args.add("build_target=" + mBuildInfo.getBuildAttributes().get("build_target"));
            }
        }

        if (mCtsVersion == 1) {
            //cts/xts install path for cts version 1
            args.add(String.format("--%s-install-path", mConfigName));
            args.add(((IFolderBuildInfo)mBuildInfo).getRootDir().getAbsolutePath());
        }

        if (!mRunAsRoot) {
            args.add("--no-enable-root");
        }

        if (mInjectInvocationData) {
            UniqueMultiMap<String, String> data = mConfig.getCommandOptions().getInvocationData();
            for (String key : data.keySet()) {
                for (String value : data.get(key)) {
                    args.add("--invocation-data");
                    args.add(key);
                    args.add(value);
                }
            }
        }

        if (0 <= mShardCount && 0 <= mShardIndex) {
            args.add("--shard-count");
            args.add(Integer.toString(mShardCount));
            args.add("--shard-index");
            args.add(Integer.toString(mShardIndex));
        }

        return args;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mBuildInfo);
        Assert.assertNotNull(mConfigName);
        if (mCtsVersion < MIN_CTS_VERSION || mCtsVersion > MAX_CTS_VERSION) {
            throw new RuntimeException(
                    String.format("Invalid Cts version requested: %s", mCtsVersion));
        }

        if (mRunJar.isEmpty()) {
            // Typical dependencies would be: hostestlib.jar, tradefed.jar
            throw new RuntimeException(
                String.format("Cannot run without additional dependencies, use --run-jar\n"));
        }

        String classpath = null;
        try {
            classpath = buildClasspath();
        } catch (FileNotFoundException e) {
            CLog.e(e);
        }

        List<String> args = buildJavaCmd(classpath);

        // clear the TF_GLOBAL_CONFIG env, so another tradefed won't reuse the
        // global config file
        getRunUtil().unsetEnvVariable(TF_GLOBAL_CONFIG);

        File stdoutFile = null;
        File stderrFile = null;
        File eventFile = null;
        SubprocessTestResultsParser eventParser = null;
        OutputStream stdout = null;
        OutputStream stderr = null;
        long startTime = 0l;
        long elapsedTime = -1l;
        try {
            stdoutFile = FileUtil.createTempFile("stdout_subprocess_", ".log");
            stderrFile = FileUtil.createTempFile("stderr_subprocess_", ".log");
            stderr = new FileOutputStream(stderrFile);
            stdout = new FileOutputStream(stdoutFile);
            if (mEventStreaming) {
                eventParser = new SubprocessTestResultsParser(listener, true, mContext);
                args.add("--subprocess-report-port");
                args.add(Integer.toString(eventParser.getSocketServerPort()));
            } else if (mReportSubprocessEvents) {
                eventFile = FileUtil.createTempFile("event_subprocess_", ".log");
                eventParser = new SubprocessTestResultsParser(listener, false, mContext);
                args.add("--subprocess-report-file");
                args.add(eventFile.getAbsolutePath());
            }

            if (!mRunAsRoot) {
                // run unroot then tell sub process to not enable root (if device is rebooted)
                getDevice().disableAdbRoot();
            }
            startTime = System.currentTimeMillis();
            mResult = getRunUtil().runTimedCmd(mMaxTfRunTime, stdout, stderr,
                    args.toArray(new String[0]));
            elapsedTime = System.currentTimeMillis() - startTime;
            // We possibly allow for a little more time if the thread is still processing events.
            if (!eventParser.joinReceiver(EVENT_THREAD_JOIN_TIMEOUT_MS)) {
                elapsedTime = -1l;
                throw new RuntimeException(String.format("Event receiver thread did not complete:"
                        + "\n%s", FileUtil.readStringFromFile(stderrFile)));
            }
            if (mResult.getStatus().equals(CommandStatus.SUCCESS)) {
                CLog.i("Successfully ran %s tests for build %s", mConfigName,
                        mBuildInfo.getBuildId());
            } else {
                CLog.w("Failed ran %s tests for build %s, status %s", mConfigName,
                        mBuildInfo.getBuildId(), mResult.getStatus());
                // Only log the runner session in case of failure or timeout.
                CLog.d("%s tests output:\nstdout:\n%s\nstderror:\n%s", mConfigName,
                        mResult.getStdout(), mResult.getStderr());
                String errMessage = null;
                if (mResult.getStatus().equals(CommandStatus.TIMED_OUT)) {
                    errMessage = String.format("Timeout after %s",
                            TimeUtil.formatElapsedTime(mMaxTfRunTime));
                } else {
                    errMessage = FileUtil.readStringFromFile(stderrFile);
                }
                throw new RuntimeException(
                    String.format("%s Tests subprocess failed due to:\n %s\n", mConfigName,
                            errMessage));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            reportElapsedTime(elapsedTime, listener);
            StreamUtil.close(stdout);
            StreamUtil.close(stderr);
            logAndCleanFile(stdoutFile, listener);
            logAndCleanFile(stderrFile, listener);
            if (eventFile != null) {
                eventParser.parseFile(eventFile);
                logAndCleanFile(eventFile, listener);
            }
            StreamUtil.close(eventParser);
            FileUtil.recursiveDelete(mTmpDir);
            logAndCleanHeapDump(mHeapDump, listener);
        }
    }

    private void logAndCleanFile(File fileToExport, ITestInvocationListener listener) {
        if (fileToExport != null) {
            FileInputStreamSource stderrInputStream = new FileInputStreamSource(fileToExport);
            listener.testLog(fileToExport.getName(), LogDataType.TEXT, stderrInputStream);
            StreamUtil.cancel(stderrInputStream);
            FileUtil.deleteFile(fileToExport);
        }
    }

    @VisibleForTesting
    void logAndCleanHeapDump(File heapDumpDir, ITestLogger logger) {
        try {
            if (heapDumpDir != null && heapDumpDir.listFiles().length != 0) {
                for (File f : heapDumpDir.listFiles()) {
                    FileInputStreamSource fileInput = new FileInputStreamSource(f);
                    logger.testLog(f.getName(), LogDataType.HPROF, fileInput);
                    StreamUtil.cancel(fileInput);
                }
            }
        } finally {
            FileUtil.recursiveDelete(heapDumpDir);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    @Override
    public IRemoteTest getTestShard(int shardCount, int shardIndex) {
        IRemoteTest shard = new CtsTestLauncher(shardCount, shardIndex);
        try {
            OptionCopier.copyOptions(this, shard);
        } catch (ConfigurationException e) {
            // Bail out rather than run tests with unexpected options
            throw new RuntimeException("failed to copy options", e);
        }
        return shard;
    }

    /**
     * Report an elapsed-time metric to keep track of it.
     *
     * @param elapsedTime time it took the subprocess to run.
     * @param listener the {@link ITestInvocationListener} where to report the metric.
     */
    private void reportElapsedTime(long elapsedTime, ITestInvocationListener listener) {
        if (elapsedTime == -1l) {
            return;
        }
        listener.testRunStarted("elapsed-time", 1);
        TestIdentifier tid = new TestIdentifier("elapsed-time", mContext.getTestTag());
        listener.testStarted(tid);
        Map<String, String> runMetrics = new HashMap<>();
        String key = "elapsed-time";
        if (mShardIndex != -1) {
            key = key + "-shard-" + mShardIndex;
        }
        runMetrics.put(key, Long.toString(elapsedTime));
        listener.testEnded(tid, runMetrics);
        listener.testRunEnded(elapsedTime, runMetrics);
    }
}
