// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.FolderBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil2;

import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;

/**
 * A {@link LaunchControlProvider} to retrieve a google-tradefed build as a
 * {@link IFolderBuildInfo}.
 * <p/>
 * Retrieves and extracts a google-tradefed.zip from the Android build server.
 */
@OptionClass(alias = "tf-launch-control")
public class TfLaunchControlProvider extends LaunchControlProvider {

    private static final String TF_PACKAGE_NAME = "google-tradefed.zip";

    /**
     * {@inheritDoc}
     */
    @Override
    public RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
        RemoteBuildInfo tfBuild = super.queryForBuild();
        if (tfBuild != null) {
            String remoteTfZipPath = findFilePath(
                    tfBuild.getAttribute(BuildAttributeKey.FILES), TF_PACKAGE_NAME);
            if (remoteTfZipPath == null) {
                throw new BuildRetrievalError("Failed to locate TradeFed artifact " +
                        TF_PACKAGE_NAME);
            }
            tfBuild.addAttribute(BuildAttributeKey.TF, remoteTfZipPath);
        }
        return tfBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        IFolderBuildInfo localBuild = new FolderBuildInfo(remoteBuild.getBuildId(), buildName);
        String remoteTfZipPath = remoteBuild.getAttribute(BuildAttributeKey.TF);
        File tfZip = null;

        try {
            tfZip = downloader.downloadFile(remoteTfZipPath);
            File localTfRootDir = extractZip(tfZip, remoteBuild.getBuildId());

            localBuild.setRootDir(localTfRootDir);
            return localBuild;
        } catch (BuildRetrievalError e) {
            e.setBuildInfo(localBuild);
            throw e;
        } catch (IOException e) {
            // clear the cache before returning
            deleteCacheEntry(remoteTfZipPath);
            localBuild.cleanUp();
            throw new BuildRetrievalError("Failed to extract " + TF_PACKAGE_NAME, e, localBuild);
        } finally {
            FileUtil.deleteFile(tfZip);
        }
    }

    /**
     * Search for expected file in the file names returned by remote build.
     *
     * @param lcFileInfo A comma separated string of all files in the build output.
     * @param fileName Name of the file to look for.
     */
    String findFilePath(String lcFileInfo, String fileName) {
        if (lcFileInfo.isEmpty()) {
            return null;
        }
        String[] files = lcFileInfo.split(",");
        for (int i = 0; i < files.length; i++) {
            // The lcFileInfo is parsed from Launch Control API return value, and the path separator
            // is fixed as "/".
            if (files[i].endsWith("/" + fileName)) {
                return files[i];
            }
        }
        return null;
    }

    /**
     * Extracts a zip to a temporary folder.
     */
    private File extractZip(File zipFile, String buildId) throws IOException {
        File localRootDir = null;
        try (ZipFile zip = new ZipFile(zipFile)) {
            localRootDir = FileUtil.createTempDir(
                    String.format("%s_%s_", getBuildFlavor(), buildId));
            ZipUtil2.extractZip(zip, localRootDir);
        } catch (IOException e) {
            FileUtil.recursiveDelete(localRootDir);
            throw e;
        }
        return localRootDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        info.cleanUp();
    }
}
