// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.FolderBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil2;

import java.io.File;
import java.io.IOException;

/**
 * A {@link LaunchControlProvider} for a {@link IBuildInfo}.
 * <p/>
 * Retrieves and extracts a Sdk Tools zip from the Android build server.
 */
@OptionClass(alias = "sdk-tools-launch-control")
public class SdkToolsLaunchControlProvider extends LaunchControlProvider {

    static final String TOOLS_ZIP_PATTERN = ".*linux-tools.*\\.zip";

    @Option(name = "sdk-root", description = "the sdk root where the tools should be unzipped")
    private String mSdkRoot = null;

    /**
     * {@inheritdoc}
     */
    @Override
    protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {

        FolderBuildInfo folderBuildInfo = new FolderBuildInfo(remoteBuild.getBuildId(), buildName);

        String remoteFiles = remoteBuild.getAttribute("files");
        File toolsZip = null;

        try {
            toolsZip =
                downloadToolsZipFile(downloader, TOOLS_ZIP_PATTERN, remoteFiles);

            File toolsFolder = ZipUtil2.extractZipToTemp(toolsZip, "Tools");
            folderBuildInfo.setRootDir(toolsFolder);
            folderBuildInfo.setFile("Sdk Tools", toolsFolder, remoteBuild.getBuildId());

            return folderBuildInfo;
        } catch (BuildRetrievalError e) {
            e.setBuildInfo(folderBuildInfo);
            throw e;
        } catch (IOException e) {
            folderBuildInfo.cleanUp();
            throw new BuildRetrievalError("Failed to extract SDK or tests zips", e,
                                          folderBuildInfo);
        } finally {
            FileUtil.deleteFile(toolsZip);
        }
    }

    /** Download the tools zip which is the only file matching the pattern. */
    File downloadToolsZipFile(
            IFileDownloader downloader, String pattern, String commaSeparatedFiles)
            throws BuildRetrievalError {
        String[] filePaths;
        if (commaSeparatedFiles.isEmpty()) {
            filePaths = new String[] {};
        } else {
            filePaths = commaSeparatedFiles.split(",");
        }
        for (String filePath : filePaths) {
            if (filePath.matches(pattern)) {
                return downloader.downloadFile(filePath);
            }
        }
        throw new BuildRetrievalError(String.format(
                "Could not find the file matching pattern '%s'", pattern));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        info.cleanUp();
    }

}
