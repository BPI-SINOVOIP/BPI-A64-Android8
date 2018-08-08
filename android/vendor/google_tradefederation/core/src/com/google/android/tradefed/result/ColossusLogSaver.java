// Copyright 2014 Google Inc. All Rights Reserved
package com.google.android.tradefed.result;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.LogFileSaver;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A class that saves log files into Colossus by using fileutil.
 * @see <a href="http://goto.google.com/colossus">go/colossus</a>
 */
@OptionClass(alias = "colossus-log-saver")
public class ColossusLogSaver implements ILogSaver {

    /** Timeout for one-time overhead when running fileutil */
    private static final long FILEUTIL_WARMUP_DEADLINE_MS = 20 * 1000;
    /** Minimum timeout per-file when running fileutil */
    private static final long FILEUTIL_PER_FILE_UPLOAD_DEADLINE_MS = 5 * 1000;
    /** Minimum timeout per-megabyte when running fileutil */
    private static final long FILEUTIL_PER_MEGABYTE_UPLOAD_DEADLINE_MS = 5 * 1000;

    private static final String COLOSSUS_PATHSEP = "/";

    private static final String USER_VAR = "${USER}";
    @Option(name = "log-file-path", description = "root CNS path to use for storing logfiles. " +
            "If seen in the pathname, the ${USER} variable will be set to the username of the " +
            "user running TF.",
            mandatory = true)
    private String mRootReportPath = null;

    @Option(name = "log-file-staging-path", description = "root local path to hold logfiles " +
            "during the invocation.  Files will be moved from here to Colossus after the " +
            "invocation completes.")
    private File mStagingReportDir =
            new File(System.getProperty("java.io.tmpdir"), "stage-colossus");

    @Option(name = "log-file-viewer-pattern", description = "URL of log file viewer.  Assumes " +
            "that the first \"%s\" should be replaced by the full path of each logfile.")
    private String mReportUrl = "https://cnsviewer.corp.google.com%s";

    @Option(name = "compress-files", description =
            "whether to compress files which are not already compressed")
    private boolean mCompressFiles = true;

    @Option(name = "remove-staged-files", description = "Whether to remove staged logfiles after " +
            "they were successfully exported to Colossus.")
    private boolean mRemoveStagedFiles = true;

    /** Generated CNS dest path for log files */
    private String mLogOutputPath = null;
    /** Generated local staging directory for log files */
    private File mLogStagingDir = null;
    /**
     * A counter to control access to methods which modify this class's directories. Acting as a
     * non-blocking reentrant lock, this int blocks access to sharded child invocations from
     * attempting to create or delete directories.
     */
    private int mShardingLock = 0;

    /** a {@link LogFileSaver} to save the log locally. */
    private LogFileSaver mLogFileSaver = null;

    /**
     * {@inheritDoc}
     * <p>
     * Also, generate an ideally-unique CNS path under
     * {@code log-file-path/[branch/]build-id/test-tag/unique_dir} for saving logs.  Note that
     * actual directory creation is implicit, so we have no good way to guarantee uniqueness.
     * </p>
     */
    @Override
    public void invocationStarted(IInvocationContext context) {
        IBuildInfo buildInfo = context.getBuildInfos().get(0);
        // Note: we handle path segments here because CNS pathsep may differ from System pathsep
        synchronized(this) {
            if (mShardingLock == 0) {
                mLogFileSaver = new LogFileSaver(buildInfo, mStagingReportDir);
                mLogOutputPath = generateCnsPath(
                        mLogFileSaver.getInvocationLogPathSegments().toArray(new String[0]));
                mLogStagingDir = mLogFileSaver.getFileDir();
            }
            mShardingLock++;
        }
    }

    /**
     * Move all staged files into Colossus by invoking Kharon
     * <p />
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        final String[] stagedPaths = listStagedFiles(mLogStagingDir);
        if (stagedPaths == null) {
            // It is uncommon that we don't even have a host log to save, so log something
            CLog.w("No log files were saved; skipping export to Colossus.");
            return;
        }

        // Compute the timeout
        long fileutilTimeout = FILEUTIL_WARMUP_DEADLINE_MS;
        for (String path : stagedPaths) {
            fileutilTimeout += Math.max(FILEUTIL_PER_FILE_UPLOAD_DEADLINE_MS,
                    new File(path).length() / 1000000 * FILEUTIL_PER_MEGABYTE_UPLOAD_DEADLINE_MS);
        }

        final List<String[]> cmds = getCopyCmds(mLogOutputPath, stagedPaths);
        boolean uploaded = true;
        CommandResult result = null;
        int i = 0;
        for (String[] cmd: cmds) {
            result = getRunUtil().runTimedCmd(fileutilTimeout, cmd);
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                uploaded = false;
                break;
            }
        }
        if (!uploaded) {
            CLog.e("Failed to run command %s.", String.join(" ", cmds.get(i)));
            CLog.e("Failed to export logfiles to Colossus. Leaving them in %s", mLogStagingDir);
            CLog.e(result.getStderr());
            return;
        }
        CLog.d("Successfully exported %d logs to %s: %s", stagedPaths.length,
                mLogOutputPath, ArrayUtil.join(", ", (Object[]) stagedPaths));
        synchronized (this) {
            if (--mShardingLock == 0) {
                cleanupStagingDir(mLogStagingDir);
            }
            if (mShardingLock < 0) {
                CLog.w("Sharding lock exited more times than entered, possible " +
                        "unbalanced invocationStarted/Ended calls");
            }
        }
    }

    /**
     * Get the copy command used to copy the files to colossus by fileutil.
     * <p />
     * Exposed for unit testing
     *
     * @param destDirPath The destination dir path
     * @param srcPaths The paths to all the sources
     * @return The commands to run
     */
    List<String[]> getCopyCmds(String destDirPath, String[] srcPaths) {
        final String[] createDirCmd = new String[] {
                "fileutil", "mkdir", "-p", destDirPath };

        final List<String> copyCmd = new ArrayList<>(
                Arrays.asList(new String[] {
                        "fileutil", "cp", "-R", "-m", "0644" }));
        copyCmd.addAll(Arrays.asList(srcPaths));
        copyCmd.add(destDirPath);
        final List<String[]> cmds = new ArrayList<>();
        cmds.add(createDirCmd);
        cmds.add(copyCmd.toArray(new String[0]));
        return cmds;
    }

    /**
     * List the files which are staged for upload to Colossus
     * <p />
     * Exposed for unit testing
     */
    String[] listStagedFiles(File parentDir) {
        final File[] files = parentDir.listFiles();
        if (files == null) return null;

        final String[] paths = new String[files.length];
        for (int i = 0; i < files.length; ++i) {
            paths[i] = files[i].getPath();
        }

        return paths;
    }

    /**
     * Clean up the local staged files after a successful export to Colossus.
     * <p />
     * Exposed for unit testing
     */
    void cleanupStagingDir(File stagingDir) {
        if (mRemoveStagedFiles) {
            CLog.d("Deleting staging directory %s and all staged files",
                    mLogStagingDir.getAbsolutePath());
            FileUtil.recursiveDelete(mLogStagingDir);
        } else {
            CLog.d("Skipped logfile cleanup in staging dir %s", stagingDir.getPath());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Will gzip and save the log file if {@link LogDataType#isCompressed()} returns false for
     * {@code dataType} and {@code compress-files} is set, otherwise, the stream will be saved
     * verbatim.
     * </p>
     */
    @Override
    public LogFile saveLogData(String dataName, LogDataType dataType, InputStream dataStream)
            throws IOException {

        if (!mCompressFiles || dataType.isCompressed()) {
            File log = mLogFileSaver.saveLogData(dataName, dataType, dataStream);
            return new LogFile(getPath(log.getName()), getUrl(log.getName()),
                    dataType.isCompressed(), dataType.isText());
        }
        File log = mLogFileSaver.saveAndGZipLogData(dataName, dataType, dataStream);
        return new LogFile(getPath(log.getName()), getUrl(log.getName()),
                true /* compressed */, dataType.isText());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LogFile saveLogDataRaw(String dataName, String ext, InputStream dataStream)
            throws IOException {
        File log = mLogFileSaver.saveLogDataRaw(dataName, ext, dataStream);
        return new LogFile(getPath(log.getName()), getUrl(log.getName()),
                false, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LogFile getLogReportDir() {
        return new LogFile(getPath(""), getUrl(""), false, false);
    }

    /**
     * Convert the generated invocation path segments into a full, CNS-appropriate pathname
     */
    String generateCnsPath(String[] invPathSegments) {
        final String username = System.getProperty("user.name");
        final String rootReportPath =
                mRootReportPath.replaceAll(Pattern.quote(USER_VAR), username);
        if (!mRootReportPath.equals(rootReportPath)) {
            // path was changed.  Log stuff
            CLog.d("rootReportPath \"%s\" was generated from \"%s\" for user \"%s\"",
                    rootReportPath, mRootReportPath, username);
        }

        final String[] fullPath =
                ArrayUtil.buildArray(new String[] {rootReportPath}, invPathSegments);
        return ArrayUtil.join(COLOSSUS_PATHSEP, (Object[])fullPath);
    }

    /**
     * A helper method that returns a URL for a given file name.
     *
     * @param fileName the filename of the log
     * @return A URL that should allow visitors to view the logfile with the specified name
     */
    private String getUrl(String fileName) {
        // FIXME: Sanitize the URL.
        return String.format(mReportUrl, getPath(fileName));
    }

    /**
     * A helper method that returns a path for a given file name.
     *
     * @param fileName the filename of the log
     * @return A URL that should allow visitors to view the logfile with the specified name
     */
    private String getPath(String fileName) {
        if (mReportUrl == null || mLogOutputPath == null) {
            CLog.w("Failed to generate log access URL.  URL pattern or output path is null. " +
                    "ReportUrl is \"%s\" and LogOutputPath is \"%s\".", mReportUrl, mLogOutputPath);
            return null;
        }

        // FIXME: Sanitize the filepath.
        return String.format("%s%s%s", mLogOutputPath, COLOSSUS_PATHSEP, fileName);
    }

    /**
     * Return a {@link IRunUtil} instance to execute commands with
     * <p />
     * Exposed for unit testing
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
