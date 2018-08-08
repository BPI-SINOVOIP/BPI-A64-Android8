// Copyright 2010 Google Inc. All Rights Reserved.
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
 * A {@link LaunchControlProvider} for a CTS build.
 * <p/>
 * Retrieves and extracts a CTS zip from the Android build server.
 * The extracted CTS is represented as a {@link IFolderBuildInfo}.
 */
@OptionClass(alias = "cts-launch-control")
public class CtsLaunchControlProvider extends LaunchControlProvider {

    @Option(name = "cts-package-name", description = "the filename of CTS package to download. "
            + "Defaults to \"android-cts.zip\"")
    private String mCtsPackageName = "android-cts.zip";

    @Option(name = "cts-extra-artifact", description = "the filename of CTS extra artefact to "
            + "download, may be repearted for multiple fetches.")
    private List<String> mExtraArtifact = new ArrayList<String>();

    @Option(
        name = "cts-extra-alt-path",
        description =
                "Alternative location where to look for extra artifact if they are not found on "
                        + "the build server."
    )
    private File mExtraAltPath = null;

    @Option(name = "throw-if-extra-not-found", description = "Throw an exception if one of the "
            + "extra specified file by cts-extra-artifact is not found.")
    private boolean mThrowIfExtraNotFound = true;

    private Map<String, String> mExtraWithPath = new HashMap<String, String>();
    private List<File> mFileCreated = new ArrayList<File>();

    public CtsLaunchControlProvider() {
        super();
        setBuildFlavor("cts");
    }

    public void setCtsPackageName(String ctsZipName) {
        mCtsPackageName = ctsZipName;
    }

    public void setCtsExtra(List<String> ctsExtra) {
        mExtraArtifact = ctsExtra;
    }

    public void setThrowIfExtraNotFound(boolean shouldThrow) {
        mThrowIfExtraNotFound = shouldThrow;
    }

    public void setCtsExtraAltPath(File altPath) {
        mExtraAltPath = altPath;
    }

    @Override
    protected RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
        RemoteBuildInfo ctsBuild = super.queryForBuild();
        if (ctsBuild != null) {
            String remoteCtsZipPath = findFilePath(
                ctsBuild.getAttribute(BuildAttributeKey.FILES), mCtsPackageName);
            ctsBuild.addAttribute(BuildAttributeKey.CTS, remoteCtsZipPath);

            //Build list of remote extra with path
            for (String extra : mExtraArtifact) {
                String remoteCtsExtraPath = findFilePath(
                        ctsBuild.getAttribute(BuildAttributeKey.FILES), extra);
                if (remoteCtsExtraPath == null) {
                    if (mThrowIfExtraNotFound) {
                        throw new BuildRetrievalError(
                                String.format("Artifact %s is not found on build server.", extra));
                    } else {
                        CLog.w("Artifact %s is not found on build server.", extra);
                        continue;
                    }
                }
                mExtraWithPath.put(extra, remoteCtsExtraPath);
            }
        }
        return ctsBuild;
    }

    String findFilePath(String lcFileInfo, String fileName) {
        if (lcFileInfo.isEmpty()) {
            return null;
        }
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
        // use a generic FolderBuildInfo to represent the CTS build, to avoid making
        // google_tradefederation dependent on CTS
        IFolderBuildInfo localBuild = new FolderBuildInfo(remoteBuild.getBuildId(), buildName);
        localBuild.setTestTag(testTargetName);
        String remoteCtsZipPath = remoteBuild.getAttribute(BuildAttributeKey.CTS);
        File ctsZip = null;

        try {
            // Get main package
            ctsZip = downloader.downloadFile(remoteCtsZipPath);
            File localCtsRootDir = extractCtsZip(ctsZip, remoteBuild.getBuildId());
            localBuild.setRootDir(localCtsRootDir);
            Set<String> extras = new HashSet<>();
            extras.addAll(mExtraArtifact);
            // Get extras from build server
            for (String extraName : mExtraWithPath.keySet()) {
                File ctsExtra = downloader.downloadFile(mExtraWithPath.get(extraName));
                localBuild.setFile(extraName, ctsExtra, null);
                mFileCreated.add(ctsExtra);
                extras.remove(extraName);
            }
            // if we have an alternative folder to look for the extra
            if (mExtraAltPath != null) {
                for (String extraName : extras) {
                    File ctsExtra = new File(mExtraAltPath, extraName);
                    if (ctsExtra.exists()) {
                        localBuild.setFile(extraName, ctsExtra, null);
                        mFileCreated.add(ctsExtra);
                    } else {
                        CLog.w(
                                "%s was not found on build server and in alternate location: %s",
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
            deleteCacheEntry(remoteCtsZipPath);
            throw new BuildRetrievalError("Failed to extract " + mCtsPackageName, e, localBuild);
        } finally {
            FileUtil.deleteFile(ctsZip);
        }
    }

    /**
     * Extracts the cts zip to a temporary folder.
     * <p/>
     * Exposed so unit tests can mock.
     */
    File extractCtsZip(File ctsZip, String buildId) throws IOException {
        File localCtsRootDir = null;
        try (ZipFile zipFile = new ZipFile(ctsZip)) {
            localCtsRootDir = FileUtil.createTempDir(
                String.format("%s_%s_", getBuildFlavor(), buildId));
            ZipUtil2.extractZip(zipFile, localCtsRootDir);
        } catch (IOException e) {
            FileUtil.recursiveDelete(localCtsRootDir);
            throw e;
        }
        return localCtsRootDir;
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
