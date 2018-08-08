// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.roboelectric;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.JUnitXmlParser;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.xml.AbstractXmlParser.ParseException;
import com.google.common.base.Joiner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * A class for running RoboElectric unit tests on the host.
 */
public class RoboElectricTest implements IRemoteTest, IBuildReceiver {

    @Option(name = "runner", description = "Test runner to use.")
    private String mRunner = "com.google.android.gms.testing.AntXmlTestRunner";

    @Option(name = "test-suite", description = "Test suite(s) to run." +
            "May be repeated to specify multiple test suites.",
            importance = Option.Importance.ALWAYS)
    private Collection<String> mSuite = new LinkedList<String>();

    @Option(name = "android-sdk", description = "Path to the android sdk.")
    private String mSdkPath = null;

    @Option(name = "max-run-time", description =
            "the maximum time in minutes to allow for a roboelectic test run.")
    private long mMaxTestRunTimeMin = 20;

    @Option(name = "output-file", description = "The name for the junit test xml result file.")
    private String mXmlOutputFile = "test-results-xml";

    @Option(name = "stdout-file", description = "The name for the junit test stdout.")
    private String mStdoutFile = "test-stdout";

    @Option(name = "test-label", description = "The label for the test. ")
    private String mTestLabel = "roboelectric_tests";

    RoboElectricBuildInfo mBuildInfo;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = (RoboElectricBuildInfo) buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        File xmlResultFile = null;
        try {
            xmlResultFile = FileUtil.createTempFile("robotest", ".xml");
        } catch (IOException e) {
            CLog.d("Failed to create temp file.");
        }

        File libDir = mBuildInfo.getRootDir();
        List<String> classPathEntries = new ArrayList<String>();
        // order of classpath entries needs to be very specific
        classPathEntries.add(mBuildInfo.getTestTargetFile().getName());
        for (File testSuiteJar : mBuildInfo.getTestSuiteFiles()) {
            classPathEntries.add(testSuiteJar.getName());
        }
        // TODO: would be nice not to have to hardcode these
        classPathEntries.add("org.robolectric.Config.properties");
        classPathEntries.add("maven_ant_tasks.jar");
        classPathEntries.add("com.android.media.remotedisplay.jar");
        classPathEntries.add("android.jar");
        // Note that "*" in the classpath parsed specially by the JVM.  See the
        // "Understanding class path wildcards" section of:
        // http://docs.oracle.com/javase/7/docs/technotes/tools/windows/classpath.html
        classPathEntries.add("*");

        String jarClassPath = Joiner.on(":").skipNulls().join(classPathEntries);
        File manifest = new File(libDir, "AndroidManifest.xml");
        assert (manifest.exists());
        File res = new File(libDir, "res");
        assert (res.exists() && res.isDirectory());
        File assets = new File(libDir, "assets");
        assert (assets.exists() && assets.isDirectory());
        List<String> args = new ArrayList<String>();
        args.add("java");
        args.add("-cp");
        args.add(jarClassPath);
        if (mSdkPath != null) {
            args.add("-Dandroid.sdk.path=" + mSdkPath);
        }
        buildDebugFlags(args);
        args.add("-XX:MaxPermSize=256m");
        args.add("-XX:-UseSplitVerifier");
        if (xmlResultFile != null) {
            args.add("-Djunit.output.xml.path=" + xmlResultFile.getAbsolutePath());
        } else {
            // Default output file.
            xmlResultFile = new File(libDir, "out.xml");
        }
        args.add(mRunner);
        for (String suite : mSuite) {
          args.add(suite);
        }
        IRunUtil runUtil = new RunUtil();
        runUtil.setWorkingDir(libDir);
        CommandResult result = runUtil.runTimedCmd(mMaxTestRunTimeMin * 60 * 1000,
                args.toArray(new String[0]));
        if (!result.getStatus().equals(CommandStatus.SUCCESS)) {
            CLog.d("Failed to run RoboElectric tests for build %s. stdout: %s\n, stderr: %s",
                    mBuildInfo.getBuildId(), result.getStdout(), result.getStderr());
        }
        String stdOut = result.getStdout();
        if (stdOut != null) {
            try (InputStreamSource is = new ByteArrayInputStreamSource(stdOut.getBytes())) {
                listener.testLog(mStdoutFile, LogDataType.TEXT, is);
            }
        }
        assert (xmlResultFile.exists());
        // Save and Parse XML
        try (FileInputStreamSource xml = new FileInputStreamSource(xmlResultFile)) {
            listener.testLog(mXmlOutputFile, LogDataType.TEXT, xml);
        }
        parseOutputAndReport(xmlResultFile, listener);

        FileUtil.deleteFile(xmlResultFile);
    }

    /**
     * Build the debug flags to pass to JVM
     * @param args
     */
    protected void buildDebugFlags(List<String> args) {
        args.add("-Drobolectric.path.manifest=./AndroidManifest.xml");
        args.add("-Drobolectric.path.res=./res");
        args.add("-Drobolectric.path.assets=./assets");
    }

    /**
     * Parse the summary of the tests from the file.
     *
     * @param xmlFile the xml output file
     */
    private void parseOutputAndReport(File xmlFile, ITestInvocationListener listener) {
        JUnitXmlParser parser = new JUnitXmlParser(listener);
        try {
            parser.parse(new FileInputStream(xmlFile));
        } catch (FileNotFoundException e) {
            CLog.e("Could not find result file %s", xmlFile.getAbsolutePath());
        } catch (ParseException e) {
            CLog.e("Could not parse result file %s: %s", xmlFile.getAbsolutePath(), e.getMessage());
        }
    }
}
