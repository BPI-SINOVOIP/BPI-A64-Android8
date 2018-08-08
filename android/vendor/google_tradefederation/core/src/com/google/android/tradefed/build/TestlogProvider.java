// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.FolderBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;
import com.google.android.tradefed.build.RemoteBuildInfo.InvalidResponseException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A {@link LaunchControlProvider} to retrieve logs of tests that were run outside of TF
 * (for example on the GCE buildbots).
 */
@OptionClass(alias = "test-log-provider")
public class TestlogProvider extends LaunchControlProvider {


    @Option(name = "testlog-regex",
            description = "regex pattern to identify relevant logs, relative to $build/$target/",
            importance = Importance.IF_UNSET, mandatory = true)
    private String mTestlogRegex = null;

    @Option(name = "testlog-buildinfo-path",
            description = "A RemoteBuildInfo from which to retrieve the test logs. This should be "
                    + "a path to file containing a text response, as returned by the lcproxy. If "
                    + "not set, the latest RemoteBuildInfo is retrieved from the lcproxy.")
    private String mProvidedBuildInfo = null;

    /**
     * {@inheritDoc}
     */
    @Override
    protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        // Options we need that are declared in the base class, but not marked as mandatory.
        Preconditions.checkNotNull(getBranch());
        Preconditions.checkNotNull(getBuildFlavor());

        IFolderBuildInfo localBuild = new FolderBuildInfo(remoteBuild.getBuildId(), buildName);
        localBuild.setTestTag(testTargetName);
        File tmpDir = setupTmpDir(localBuild);

        for (String remoteLogPath : getLogPaths(remoteBuild)) {
            CLog.d("Fetching log: %s", remoteLogPath);
            try {
                File localLogFile = downloader.downloadFile(remoteLogPath);
                addLogToLocalBuild(remoteLogPath, localBuild, tmpDir, localLogFile);
            } catch (BuildRetrievalError e) {
                e.setBuildInfo(localBuild);
                throw e;
            }
        }

        return localBuild;
    }

    private File setupTmpDir(IFolderBuildInfo localBuild) throws BuildRetrievalError {
        File localRootDir = null;
        try {
            localRootDir = FileUtil.createTempDir("logs");
        } catch (IOException e) {
            localBuild.cleanUp();
            throw new BuildRetrievalError("Failed to create local tmp dir", e, localBuild);
        }
        localBuild.setRootDir(localRootDir);
        return localRootDir;
    }

    @VisibleForTesting
    List<String> getLogPaths(RemoteBuildInfo remoteBuild) {
        String logFileRegex = String.format(
                "%s/%s/%s",
                remoteBuild.getAttribute(
                        BuildAttributeKey.BUILD_TARGET_NAME.getRemoteValue()),
                remoteBuild.getBuildId(),
                mTestlogRegex);
        Pattern logFilePattern = Pattern.compile(logFileRegex);
        String remoteFiles = remoteBuild.getAttribute(BuildAttributeKey.FILES);
        CLog.d("Log file regex: %s", logFileRegex);
        CLog.d("All remote files: %s", remoteFiles);
        List<String> result = new ArrayList<String>();
        String[] filePaths;
        if (remoteFiles.isEmpty()) {
            filePaths = new String[] {};
        } else {
            filePaths = remoteFiles.split(",");
        }
        for (String f : filePaths) {
            if (logFilePattern.matcher(f).find()) {
                result.add(f);
            }
        }
        return result;
    }

    private void addLogToLocalBuild(
            String remoteLogPath, IFolderBuildInfo localBuild, File tmpDir, File localLogFile)
            throws BuildRetrievalError {
        try {
            FileUtil.copyFile(localLogFile, new File(tmpDir, localLogFile.getName()));
            localBuild.setFile(
                    localLogFile.getAbsolutePath(), new File(tmpDir, localLogFile.getName()), "1");
        } catch (IOException e) {
            deleteCacheEntry(remoteLogPath);
            localBuild.cleanUp();
            throw new BuildRetrievalError("Failed to copy log file", e, localBuild);
        } finally {
            FileUtil.deleteFile(localLogFile);
        }
    }

    /**
     * {@inheritDoc}
     */
    // For easier manual testing, check for custom RemoteBuildInfo.
    @Override
    public RemoteBuildInfo getRemoteBuild() throws BuildRetrievalError {
        if (mProvidedBuildInfo != null) {
            try {
                return RemoteBuildInfo.parseRemoteBuildInfo(
                        FileUtil.readStringFromFile(new File(mProvidedBuildInfo)));
            } catch (IOException|InvalidResponseException e) {
                throw new RuntimeException(e);
            }
        }
        return super.getRemoteBuild();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        info.cleanUp();
    }
}
