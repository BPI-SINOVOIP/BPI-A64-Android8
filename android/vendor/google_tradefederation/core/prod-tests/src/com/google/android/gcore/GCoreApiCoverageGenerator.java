// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.gcore;

import com.android.tradefed.build.AppBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.google.api.client.util.Joiner;

import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A class for running the GMS Core API Coverage gradle JAR tool
 * to generate the API Coverage xml/html files on the host.
 */
public class GCoreApiCoverageGenerator implements IRemoteTest, IBuildReceiver {

    @Option(
        name = "api-coverage-jar-filename",
        description =
                "Filename of the API Coverage jar file to retrieve. It's the tool used for "
                        + "generating the output.",
        importance = Option.Importance.ALWAYS
    )
    private String mApiCoverageJarFilename = null;

    @Option(
        name = "api-xml-input-filename",
        description = "Filename of the API XML input file that gets fed into the jar.",
        importance = Option.Importance.ALWAYS
    )
    private String mApiInputXmlFilename = null;

    @Option(
        name = "test-apk-filename",
        description = "Filename(s) of the APK(s) to run the ApiCoverage against.",
        importance = Option.Importance.ALWAYS
    )
    private Collection<String> mTestApkNames = new ArrayList<>();

    @Option(
        name = "output-filename",
        description = "The name for the api coverage xml & html output files.",
        importance = Option.Importance.ALWAYS
    )
    private String mOutputFilename = "api-coverage";

    @Option(
        name = "test-package",
        description = "Package(s) containing the API tests",
        importance = Option.Importance.ALWAYS
    )
    private Collection<String> mTestPackages = new ArrayList<>();

    @Option(
        name = "max-run-time",
        description = "the maximum time in milliseconds to allow the coverage tool to run."
    )
    private long mMaxRunTimeMs = 1000 * 60 * 3; // 3 minutes

    private static final String API_COVERAGE_LOG_NAME = "gcore-api-coverage";
    private static final String GMS_PACKAGE = "+com.google.android.gms.*";
    private AppBuildInfo mBuildInfo;
    private ITestInvocationListener mListener;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = (AppBuildInfo) buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mListener = listener;
        if (mTestPackages.isEmpty()) {
            mTestPackages.addAll(Arrays.asList(new String[] {
                    "+com.google.android.gms.apitest.*",
                    "+com.google.android.gms.icing.testcommon.*"}));
        }
        try {
            runApiCoverageTool(LogDataType.XML);
            runApiCoverageTool(LogDataType.HTML);
        } catch (IOException e) {
            CLog.e(e.getMessage());
        }
    }

    public void runApiCoverageTool(LogDataType outputFormat) throws IOException {
        File apiCoverageJar = getArtifact(mApiCoverageJarFilename);
        File outputFile = FileUtil.createTempFile(mOutputFilename, "." + outputFormat.getFileExt());
        IRunUtil runUtil = new RunUtil();

        List<File> testApks = new ArrayList<>();
        for (String apkName : mTestApkNames) {
            File artifact = getArtifact(apkName);
            if (artifact != null && artifact.exists()) {
                testApks.add(artifact);
            }
        }

        File artifactsDir = apiCoverageJar.getParentFile();
        assert(artifactsDir.exists() && artifactsDir.isDirectory());
        CLog.d("artifactsDir: %s", artifactsDir);
        runUtil.setWorkingDir(artifactsDir);

        String[] apiCoverageCmd = new CommandBuilder(apiCoverageJar)
                .inputXmlFile(getArtifact(mApiInputXmlFilename))
                .gmsPackage(GMS_PACKAGE)
                .testPackages(mTestPackages)
                .testApks(testApks)
                .outputFile(outputFile.getAbsolutePath(), outputFormat.getFileExt())
                .build();

        CommandResult result = runUtil.runTimedCmd(mMaxRunTimeMs, apiCoverageCmd);
        if (!result.getStatus().equals(CommandStatus.SUCCESS)) {
            CLog.d("Failed to run API Coverage tool for build %s. stdout: %s\n, stderr: %s",
                    mBuildInfo.getBuildId(), result.getStdout(), result.getStderr());
        }

        attachFile(API_COVERAGE_LOG_NAME, outputFormat, outputFile);
    }

    private File getArtifact(String filename) {
        return mBuildInfo.getFile(filename);
    }

    private void attachFile(String filename, LogDataType outputFormat, File outputFile) {
        assert(outputFile.exists());
        try (FileInputStreamSource stream = new FileInputStreamSource(outputFile)) {
            mListener.testLog(filename, outputFormat, stream);
        }
    }

    private static class CommandBuilder {

        private List<String> mArgs = new ArrayList<>();
        private Collection<File> mTestApks;

        public CommandBuilder(File jarFile) {
            Assert.assertNotNull(jarFile);
            assert(jarFile.exists());
            mArgs.add("java");
            mArgs.add("-jar");
            mArgs.add(jarFile.getName());
        }

        public CommandBuilder inputXmlFile(File xmlFile) {
            Assert.assertNotNull(xmlFile);
            assert(xmlFile.exists());
            mArgs.add("-a");
            mArgs.add(xmlFile.getName());
            return this;
        }

        public CommandBuilder gmsPackage(String gmsPackage) {
            mArgs.add("-p");
            mArgs.add(gmsPackage);
            return this;
        }

        public CommandBuilder testPackages(Collection<String> mTestPackages) {
            mArgs.add("-t");
            mArgs.add(Joiner.on(',').join(mTestPackages));
            return this;
        }

        public CommandBuilder testApks(Collection<File> testApks) {
            mTestApks = testApks;
            return this;
        }

        public CommandBuilder outputFile(String outputFilePath, String fileExt) {
            Assert.assertNotNull(outputFilePath);
            assert(new File(outputFilePath).exists());
            mArgs.add("-f");
            mArgs.add(fileExt);
            mArgs.add("-o");
            mArgs.add(outputFilePath);
            return this;
        }

        public String[] build() {
            // test apks must be last args
            for (File apkFile : mTestApks) {
                mArgs.add(apkFile.getName());
            }
            return mArgs.toArray(new String[0]);
        }
    }
}
