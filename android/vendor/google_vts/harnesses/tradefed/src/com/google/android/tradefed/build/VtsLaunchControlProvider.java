/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.FolderBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil2;

import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link LaunchControlProvider} for a VTS build.
 * <p/>
 * Retrieves and extracts a VTS zip from the Android build server.
 * The extracted VTS is represented as a {@link IFolderBuildInfo}.
 */
@OptionClass(alias = "vts-launch-control")
public class VtsLaunchControlProvider extends LaunchControlProvider {
    @Option(name = "vts-package-name", description = "the filename of VTS package to download. "
                    + "Defaults to \"android-vts.zip\"")
    private String mVtsPackageName = "android-vts.zip";

    @Option(name = "vts-extra-artifact", description = "the filename of VTS extra artefact to "
                    + "download, may be repearted for multiple fetches.")
    private List<String> mExtraArtifact = new ArrayList<String>();

    @Option(name = "vts-extra-alt-path",
            description =
                    "Alternative location where to look for extra artifact if they are not found on "
                    + "the build server.")
    private File mExtraAltPath = null;

    @Option(name = "throw-if-extra-not-found", description = "Throw an exception if one of the "
                    + "extra specified file by vts-extra-artifact is not found.")
    private boolean mThrowIfExtraNotFound = true;

    private Map<String, String> mExtraWithPath = new HashMap<String, String>();
    private List<File> mFileCreated = new ArrayList<File>();

    public VtsLaunchControlProvider() {
        super();
        setBuildFlavor("vts");
    }

    public void setVtsPackageName(String vtsZipName) {
        mVtsPackageName = vtsZipName;
    }

    public void setVtsExtra(List<String> vtsExtra) {
        mExtraArtifact = vtsExtra;
    }

    public void setThrowIfExtraNotFound(boolean shouldThrow) {
        mThrowIfExtraNotFound = shouldThrow;
    }

    public void setVtsExtraAltPath(File altPath) {
        mExtraAltPath = altPath;
    }

    @Override
    protected RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
        RemoteBuildInfo vtsBuild = super.queryForBuild();
        if (vtsBuild != null) {
            String remoteVtsZipPath =
                    findFilePath(vtsBuild.getAttribute(BuildAttributeKey.FILES), mVtsPackageName);
            vtsBuild.addAttribute(BuildAttributeKey.VTS, remoteVtsZipPath);

            // Build list of remote extra with path
            for (String extra : mExtraArtifact) {
                String remoteVtsExtraPath =
                        findFilePath(vtsBuild.getAttribute(BuildAttributeKey.FILES), extra);
                if (remoteVtsExtraPath == null) {
                    if (mThrowIfExtraNotFound) {
                        throw new BuildRetrievalError(
                                String.format("Artifact %s is not found on build server.", extra));
                    } else {
                        CLog.w("Artifact %s is not found on build server.", extra);
                        continue;
                    }
                }
                mExtraWithPath.put(extra, remoteVtsExtraPath);
            }
        }
        return vtsBuild;
    }

    private String findFilePath(String lcFileInfo, String fileName) {
        String[] files = lcFileInfo.split(",");
        for (int i = 0; i < files.length; i++) {
            if (files[i].endsWith(fileName)) {
                return files[i];
            }
        }
        return null;
    }

    @Override
    protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        // use a generic FolderBuildInfo to represent the VTS build, to avoid making
        // google_tradefederation dependent on VTS
        IFolderBuildInfo localBuild = new FolderBuildInfo(remoteBuild.getBuildId(), buildName);
        localBuild.setTestTag(testTargetName);
        String remoteVtsZipPath = remoteBuild.getAttribute(BuildAttributeKey.VTS);
        File vtsZip = null;

        try {
            // Get main package
            vtsZip = downloader.downloadFile(remoteVtsZipPath);
            File localVtsRootDir = extractVtsZip(vtsZip, remoteBuild.getBuildId());
            localBuild.setRootDir(localVtsRootDir);
            Set<String> extras = new HashSet<>();
            extras.addAll(mExtraArtifact);
            // Get extras from build server
            for (String extraName : mExtraWithPath.keySet()) {
                File vtsExtra = downloader.downloadFile(mExtraWithPath.get(extraName));
                localBuild.setFile(extraName, vtsExtra, null);
                mFileCreated.add(vtsExtra);
                extras.remove(extraName);
            }
            // if we have an alternative folder to look for the extra
            if (mExtraAltPath != null) {
                for (String extraName : extras) {
                    File vtsExtra = new File(mExtraAltPath, extraName);
                    if (vtsExtra.exists()) {
                        localBuild.setFile(extraName, vtsExtra, null);
                        mFileCreated.add(vtsExtra);
                    } else {
                        CLog.w("%s was not found on build server and in alternate location: %s",
                                extraName, mExtraAltPath);
                    }
                }
            }

            return localBuild;
        } catch (BuildRetrievalError e) {
            e.setBuildInfo(localBuild);
            throw e;
        } catch (IOException e) {
            // clear the cache before returning
            deleteCacheEntry(remoteVtsZipPath);
            throw new BuildRetrievalError("Failed to extract " + mVtsPackageName, e, localBuild);
        } finally {
            FileUtil.deleteFile(vtsZip);
        }
    }

    /**
     * Extracts the vts zip to a temporary folder.
     * <p/>
     * Exposed so unit tests can mock.
     */
    File extractVtsZip(File vtsZip, String buildId) throws IOException {
        File localVtsRootDir = null;
        try (ZipFile zipFile = new ZipFile(vtsZip)) {
            localVtsRootDir =
                    FileUtil.createTempDir(String.format("%s_%s_", getBuildFlavor(), buildId));
            ZipUtil2.extractZip(zipFile, localVtsRootDir);
        } catch (IOException e) {
            FileUtil.recursiveDelete(localVtsRootDir);
            throw e;
        }
        return localVtsRootDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        info.cleanUp();
        cleanExtra();
    }

    public void cleanExtra() {
        // Clean up extras if any
        for (File f : mFileCreated) {
            if (f.exists()) {
                FileUtil.deleteFile(f);
            }
        }
    }
}
