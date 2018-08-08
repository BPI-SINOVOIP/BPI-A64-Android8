// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.buildstats;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil2;

import com.google.android.tradefed.build.LaunchControlProvider;
import com.google.android.tradefed.build.RemoteBuildInfo;
import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;

/**
 * A {@link LaunchControlProvider} to fetches the build image zip file and extracts it.
 */
@OptionClass(alias = "build-stats-launch-control")
public class BuildStatsLaunchControlProvider extends LaunchControlProvider {

    static final String SYSTEM_IMG_TAG = "SYSTEM";

    /**
     * {@inheritDoc}
     */
    @Override
    protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        StatsBuildInfo buildInfo = new StatsBuildInfo(remoteBuild.getBuildId(), buildName);
        buildInfo.setTestTag(testTargetName);
        // FIXME: replace with actual attribute once it becomes available.
        String remoteImgPath = remoteBuild.getAttribute(BuildAttributeKey.TARGET_FILES);

        File imageFile = null;

        if (remoteImgPath == null) {
            throw new BuildRetrievalError(String.format(
                    "Missing expected %s in launch control response",
                    BuildAttributeKey.TARGET_FILES), null, buildInfo);
        }
        imageFile = downloader.downloadFile(remoteImgPath);
        try {
            File unzipDir = extractZip(imageFile, SYSTEM_IMG_TAG);
            if (unzipDir.isDirectory()) {
                if (unzipDir.exists() && unzipDir.isDirectory()) {
                    buildInfo.setSystemRoot(unzipDir);
                }  else {
                    throw new BuildRetrievalError("Failed to download image file.", null, buildInfo);
                }
            }
            return buildInfo;
        } catch (IOException ioException) {
            throw new BuildRetrievalError("Failed to extract file.", null, buildInfo);
        } finally {
            imageFile.delete();
        }
    }

    /**
     * Extracts the SYSTEM directory from a target zip file.
     *
     * @param zipFile the {@link File} to extract.
     * @param nameHint the {@link String} used for the temp directory.
     * @return the {@link File} path to the extracted SYSTEM files
     * @throws IOException
     */
    File extractZip(File zipFile, String nameHint) throws IOException {
        File localRootDir = FileUtil.createTempDir(nameHint);
        ZipUtil2.extractZip(new ZipFile(zipFile), localRootDir);
        File systemImgDir = new File(localRootDir, SYSTEM_IMG_TAG);
        File localSystemDir = FileUtil.createTempDir(nameHint);
        FileUtil.recursiveCopy(systemImgDir, localSystemDir);
        FileUtil.recursiveDelete(localRootDir);
        return localSystemDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        info.cleanUp();
    }
}
