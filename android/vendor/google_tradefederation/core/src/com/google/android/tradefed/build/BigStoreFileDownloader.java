// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.command.FatalHostError;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link IFileDownloader} that retrieves launch control files using a helper script.
 * <p/>
 * The script tries to intelligently find an auth method that will work.
 */
public class BigStoreFileDownloader implements IFileDownloader {

    /** The maximum time in ms to wait for `klist` to return. */
    static final int KRB_CHECK_TIMEOUT = 10 * 1000;
    static final String[] KRB_CHECK_CMD = {"klist", "-s"};
    static final String[] RUNCRON_CMD = {"runcron"};
    /** The actual download command */
    static final String[] FETCH_CMD = {"/google/data/ro/projects/android/fetch_artifact"};

    /** Due to a bug, fetch_artifact will sometimes only write exactly 4MB of a file. */
    private static final long BAD_FILE_SIZE = 4L * 1024 * 1024;

    /**
     * The maximum default time in ms to wait for the fetch command to complete.
     */
    private static final long FETCH_TIMEOUT = 10 * 60 * 1000;
    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;

    /** Whether to attempt to use kerberos for auth */
    private boolean mUseKrb = false;

    /** Extra arguments to be passed to fetch_artifact script. */
    private List<String> mFetchArtifactArgs = new LinkedList<>();

    /** File extensions that denote zip archives */
    private static final Set<String> ZIP_FILE_SUFFIXES = new HashSet<String>();

    static {
        ZIP_FILE_SUFFIXES.add(".zip");
        ZIP_FILE_SUFFIXES.add(".apk");
    }

    /** Standard platforms */
    private static final Set<String> STANDARD_PLATFORMS = new HashSet<String>();

    static {
        STANDARD_PLATFORMS.add("linux");
        STANDARD_PLATFORMS.add("mac");
    }

    /** Max timeout in ms for fetch_artifact to download a binary */
    private long mDownloadTimeout = FETCH_TIMEOUT;

    /**
     * Set whether the downloader should attempt to use Kerberos for authentication
     */
    public void setUseKrb(boolean useKrb) {
        mUseKrb = useKrb;
    }

    public void setFetchArtifactArgs(List<String> args) {
        mFetchArtifactArgs = new LinkedList<>(args);
    }

    public void setDownloadTimeout(long timeout) {
        mDownloadTimeout = timeout;
    }

    /**
     * Returns {@code false} if `klist` alone succeeds.  Returns {@code true} if `runcron klist`
     * succeeds but `klist` alone fails.  Logs a warning and returns {@code true} if both fail,
     * since `runcron` credentials could be updated while we're running.
     * <p />
     * Doesn't preemptively throw an exception since FETCH_CMD may still be able to use cached
     * credentials or some other magic.
     */
    boolean checkShouldUseRuncron() {
        if (!mUseKrb) {
            CLog.d("Kerberos disabled; skipping runcron check.");
            return false;
        }

        CLog.d("Checking for plain kerberos certificates...");
        CommandResult krbCheck = getRunUtil().runTimedCmd(KRB_CHECK_TIMEOUT, KRB_CHECK_CMD);
        if (krbCheck.getStatus().equals(CommandStatus.SUCCESS)) {
            // use `klist`
            CLog.d("Using plain kerberos certificates.");
            return false;
        }

        CLog.d("Checking for runcron kerberos certificates...");
        final String[] checkCmd = ArrayUtil.buildArray(RUNCRON_CMD, KRB_CHECK_CMD);
        krbCheck = getRunUtil().runTimedCmd(KRB_CHECK_TIMEOUT, checkCmd);
        if (krbCheck.getStatus().equals(CommandStatus.SUCCESS)) {
            // use `runcron klist`
            CLog.d("Using runcron kerberos certificates.");
            return true;
        }

        // hope is a strategy?
        CLog.w("Unable to detect active kerberos certificates; download may fail.");
        return true;
    }

    /**
     * A method that invokes the zip verification routines
     *
     * @return {@code false} if the zip file was deemed corrupt, or {@code true} if the file
     *         checks out, or is not a zip file.
     */
    boolean isZipFileValid(File zip) {
        try {
            final String filename = zip.getCanonicalPath();
            final String extension = FileUtil.getExtension(filename);

            // Make sure we're dealing with something that should be a zip file
            if (!ZIP_FILE_SUFFIXES.contains(extension)) return true;

            return ZipUtil.isZipFileValid(zip, true /* thorough check */);
        } catch (IOException e) {
            CLog.d("Caught IOException during Zip verification: %s", e.getMessage());
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File downloadFile(final String remoteFilePath) throws BuildRetrievalError {
        File destFile = createTempFile(remoteFilePath, null);
        try {
            downloadFile(remoteFilePath, destFile);
            return destFile;
        } catch (BuildRetrievalError e) {
            destFile.delete();
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void downloadFile(final String remoteFilePath, File destFile)
            throws BuildRetrievalError {
        CLog.i("Downloading %s to %s", remoteFilePath, destFile.getAbsolutePath());
        final Map<String, String> attribs = LCUtil.parseAttributeLine(remoteFilePath);
        if (attribs == null) {
            throw new BuildRetrievalError(String.format(
                    "Failed to parse file path %s as LC path", remoteFilePath));
        }
        String target = attribs.get("flavor");
        final String platform = attribs.get("os");
        if (!STANDARD_PLATFORMS.contains(platform)) {
            // As a platform parameter gets deprecated in fetch_artifact, non-standard platform
            // string(ex)fastbuild_linux) should be appended to flavor to generate a correct build
            // target.
            target += "_" + platform;
        }
        final String branch = attribs.get("branch");
        final String build = attribs.get("bid");
        final String remoteFile = attribs.get("filename");
        final boolean isKernel = attribs.containsKey("kernel");

        doDownload(remoteFile, destFile, target, platform, branch, build, isKernel,
                MAX_DOWNLOAD_ATTEMPTS);
    }

    /**
     * Builds a command to use for downloading files.  Exposed for testing.
     *
     * @param remoteFilePattern the regular expression of file to download
     * @param destFile the local download destination. Can be a file or directory
     * @param target the build flavor aka target
     * @param branch the build server branch name
     * @param build the build id
     * @param isKernel true if downloading from kernel server
     */
    String[] buildCommand(final String remoteFilePattern, final File destFile, final String target,
            final String branch, final String build, final boolean isKernel) {
        return buildCommand(remoteFilePattern, destFile, target, null, branch, build, isKernel);
    }

    /**
     * Builds a command to use for downloading files.  Exposed for testing.
     *
     * @param remoteFilePattern the regular expression of file to download
     * @param destFile the local download destination. Can be a file or directory
     * @param target the build flavor aka target
     * @param platform the build platform
     * @param branch the build server branch name
     * @param build the build id
     * @param isKernel true if downloading from kernel server
     */
    String[] buildCommand(final String remoteFilePattern, final File destFile, final String target,
            final String platform, final String branch, final String build,
            final boolean isKernel) {
        final boolean useRuncron = checkShouldUseRuncron();

        final String[] emptyAry = {};
        final String[] runCronArgs = RUNCRON_CMD;
        final String[] fetchArgs = FETCH_CMD;
        final String[] kernelArgs = {"--kernel"};
        final String[] targetArgs = {"--target", target};
        final String[] platformArgs = {"--platform", platform};
        final String buildType = build.startsWith("P") ? "pending" : null;
        final String[] buildTypeArgs = {"--build_type", buildType};
        final String[] krbArgs = {"--use_kerberos", "--nouse_loas"};
        final String[] extraArgs = mFetchArtifactArgs.toArray(new String[mFetchArtifactArgs.size()]);
        final String[] tailArgs = {"--bid", build, "--branch", branch, remoteFilePattern,
                destFile.getAbsolutePath()};

        // fetch_artifact --bid 200429 --target mysid-tests '*-tests-*.zip' ./
        // fetch_artifact --bid BUILD_ID --target TARGET FILENAME DEST_DIR
        final String[] fetchCmd = ArrayUtil.buildArray(
                useRuncron ? runCronArgs : emptyAry,
                fetchArgs,
                isKernel ? kernelArgs : emptyAry,
                isKernel ? emptyAry : targetArgs,
                platform == null ? emptyAry : platformArgs,
                buildType == null ? emptyAry : buildTypeArgs,
                mUseKrb ? krbArgs : emptyAry,
                extraArgs,
                tailArgs);

        return fetchCmd;
    }

    /**
     * Runs the fetch command to download files
     *
     * @param remoteFilePattern the regular expression of file to download
     * @param destFile the local download destination. Can be a file or directory
     * @param target the build flavor aka target
     * @param branch the build server branch name
     * @param build the build id
     * @param isKernel true if downloading from kernel server
     *
     * @throws BuildRetrievalError if download fails
     */
    public void doDownload(final String remoteFilePattern, final File destFile, final String target,
            final String branch, final String build, final boolean isKernel, int downloadAttempts)
            throws BuildRetrievalError {
        doDownload(remoteFilePattern, destFile, target, null, branch, build, isKernel,
                downloadAttempts);
    }

    /**
     * Runs the fetch command to download files
     *
     * @param remoteFilePattern the regular expression of file to download
     * @param destFile the local download destination. Can be a file or directory
     * @param target the build flavor aka target
     * @param platform the build platform
     * @param branch the build server branch name
     * @param build the build id
     * @param isKernel true if downloading from kernel server
     *
     * @throws BuildRetrievalError if download fails
     */
    public void doDownload(final String remoteFilePattern, final File destFile, final String target,
            final String platform, final String branch, final String build, final boolean isKernel,
            int downloadAttempts) throws BuildRetrievalError {
        final String[] fetchCmd = buildCommand(remoteFilePattern, destFile, target, platform,
                branch, build, isKernel);

        assertHasProdAccess();
        String reason = null;
        CommandResult result = null;
        for (int i = 0; i < downloadAttempts; ++i) {
            result = getRunUtil().runTimedCmd(mDownloadTimeout, fetchCmd);

            if (result.getStatus().equals(CommandStatus.SUCCESS)) {
                // File verification doesn't apply to directories
                if (destFile.isDirectory()) return;

                // Verify that downloaded file is valid
                if (!destFile.exists()) {
                    reason =
                            String.format(
                                    "Downloader indicated success but destfile %s doesn't exist!",
                                    remoteFilePattern);
                    CLog.i(reason);
                } else if (destFile.length() == BAD_FILE_SIZE) {
                    reason =
                            String.format(
                                    "Got bad file size when downloading %s", remoteFilePattern);
                    CLog.i(reason);
                } else if (!isZipFileValid(destFile)) {
                    reason = String.format("Downloaded file %s was corrupted!", remoteFilePattern);
                } else {
                    // None of the double-check failure conditions were met, so file is valid!
                    return;
                }
            } else {
                // command failed.  Log error message
                reason = "Command failed";
                CLog.e("Failed to download %s", remoteFilePattern);
                CLog.e("stdout:\n'''\n%s'''\n", result.getStdout());
                CLog.d("stderr:\n'''\n%s'''\n", result.getStderr());
            }
        }

        throw new BuildRetrievalError(
                String.format(
                        "failed to download file %s to %s.\ncommand status: %s.\nreason: %s",
                        remoteFilePattern,
                        destFile.getAbsolutePath(),
                        result.getStatus().toString(),
                        reason));
    }

    /**
     * Asserts that prodaccess is still granted.
     *
     * @throws FatalHostError if prodaccess is expired or state is unknown.
     */
    void assertHasProdAccess() {
        final int PROD_CMD_TIMEOUT = 30 * 1000;
        String[] prodCmd = {"prodcertstatus"};
        CommandResult result = getRunUtil().runTimedCmd(PROD_CMD_TIMEOUT, prodCmd);

        if (result.getStatus().equals(CommandStatus.EXCEPTION) ||
                    result.getStatus().equals(CommandStatus.TIMED_OUT)) {
            CLog.d("prodcertstatus failed to run (%s): stdout '%s', stderr '%s'",
                        result.getStatus(), result.getStdout(), result.getStderr());
        }

        if (result.getStderr() != null && result.getStderr().contains("No valid LOAS certs")) {
            throw new RuntimeException(String.format("prodaccess is expired. stderr '%s'",
                        result.getStderr().trim()));
        }
    }

    /**
     * Get {@link IRunUtil} to use. <p/>Exposed so unit tests
     * can mock.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Creates a unique file on temporary disk to house downloaded file with given path.
     * <p/>
     * Constructs the file name based on base file name from path
     *
     * @param remoteFilePath the remote path to construct the name from
     */
    File createTempFile(String remoteFilePath, File rootDir) throws BuildRetrievalError {
        try {
            // create a unique file.
            File tmpFile =  FileUtil.createTempFileForRemote(remoteFilePath, rootDir);
            // now delete it so name is available
            tmpFile.delete();
            return tmpFile;
        } catch (IOException e) {
            String msg = String.format("Failed to create tmp file for %s", remoteFilePath);
            throw new BuildRetrievalError(msg, e);
        }
    }
}
