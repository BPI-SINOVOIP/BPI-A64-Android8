// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.LogFileSaver;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** A {@ILogSaver} class to upload test outputs to TFC. */
@OptionClass(alias = "cluster", global_namespace = false)
public class ClusterLogSaver implements ILogSaver {

    @Option(name = "root-dir", description = "A root directory", mandatory = true)
    private File mRootDir;

    @Option(name = "attempt-id", description = "A command attempt ID", mandatory = true)
    private String mAttemptId;

    @Option(
        name = "output-file-upload-url",
        description = "URL to upload output files to",
        mandatory = true
    )
    private String mOutputFileUploadUrl;

    @Option(name = "output-file-pattern", description = "Output file patterns")
    private List<String> mOutputFilePatterns = new ArrayList<>();

    private File mLogDir;
    private LogFileSaver mLogFileSaver = null;
    private IRunUtil mRunUtil = null;

    @Override
    public void invocationStarted(IInvocationContext context) {
        mLogDir = new File(mRootDir, "logs");
        mLogFileSaver = new LogFileSaver(mLogDir);
    }

    private void findFilesRecursively(
            final File dir, final String regex, final List<File> fileList) {
        final Pattern pattern = Pattern.compile(regex);
        try (final Stream<Path> stream =
                Files.find(
                        dir.toPath(),
                        Integer.MAX_VALUE,
                        (path, attr) ->
                                attr.isRegularFile()
                                        && pattern.matcher(String.valueOf(path)).matches())) {
            stream.map((path) -> path.toFile()).forEachOrdered(fileList::add);
        } catch (IOException e) {
            throw new RuntimeException("failed to collect output files", e);
        }
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        // Get a list of log files to upload
        final List<File> outputFiles = new ArrayList<>();
        findFilesRecursively(mLogDir, ".*", outputFiles);
        // Collect output files to upload
        if (0 < mOutputFilePatterns.size()) {
            final String regex =
                    mOutputFilePatterns
                            .stream()
                            .map((s) -> "(" + s + ")")
                            .collect(Collectors.joining("|"));
            CLog.i("Collecting output files matching regex: " + regex);
            findFilesRecursively(mRootDir, regex, outputFiles);
        }

        CLog.i("Collected %d files to upload", outputFiles.size());
        for (final File f : outputFiles) {
            CLog.i(f.getAbsolutePath());
        }

        final TestOutputUploader uploader = new TestOutputUploader();
        try {
            uploader.setUploadUrl(mOutputFileUploadUrl);
        } catch (MalformedURLException e) {
            throw new RuntimeException("failed to set upload URL", e);
        }
        int index = 1;
        for (final File file : outputFiles) {
            CLog.i(
                    "Uploading file %d of %d: %s",
                    index, outputFiles.size(), file.getAbsolutePath());
            uploader.uploadFile(file);
            FileUtil.deleteFile(file);
            index++;
        }
    }

    IRunUtil getRunUtil() {
        if (mRunUtil == null) {
            mRunUtil = new RunUtil();
        }
        return mRunUtil;
    }

    @Override
    public LogFile saveLogData(String dataName, LogDataType dataType, InputStream dataStream)
            throws IOException {
        File log = mLogFileSaver.saveLogData(dataName, dataType, dataStream);
        return new LogFile(log.getAbsolutePath(), null, dataType.isCompressed(), dataType.isText());
    }

    @Override
    public LogFile saveLogDataRaw(String dataName, String ext, InputStream dataStream)
            throws IOException {
        File log = mLogFileSaver.saveLogDataRaw(dataName, ext, dataStream);
        return new LogFile(log.getAbsolutePath(), null, false, false);
    }

    @Override
    public LogFile getLogReportDir() {
        return new LogFile(mLogDir.getAbsolutePath(), null, false, false);
    }
}
