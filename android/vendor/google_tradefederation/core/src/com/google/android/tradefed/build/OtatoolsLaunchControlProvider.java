// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.OtatoolsBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil2;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;

/**
 * Build provider for {@link OtatoolsBuildInfo}
 */
public class OtatoolsLaunchControlProvider extends LaunchControlProvider {

    private static final String DIR_BIN = "bin";
    private static final String DIR_FRAMEWORK = "framework";
    private static final String DIR_RELEASETOOLS = "releasetools";
    private static final String DIR_SECURITY = "build/target/product/security";

    private File mBaseDir;

    // TODO: This is a hack to allow PythonUnitTestRunner to add files from this build provider
    // to the PYTHONPATH without any explicit relationship between the two
    @Option(name = "otatools-unzip-dir", description = "directory to unzip otatools.zip to")
    private File mOtaToolsUnzipDir = null;

    @Override
    public void cleanUp(IBuildInfo info) {
        FileUtil.recursiveDelete(mBaseDir);
    }

    @Override
    protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        final String otatoolsName = String.format("%s-linux-%s/%s/%s",
                getBranch(),
                getBuildFlavor(),
                remoteBuild.getBuildId(),
                "otatools.zip");
        File otaToolsFile = downloader.downloadFile(otatoolsName);
        try {
            if (mOtaToolsUnzipDir != null) {
                ZipUtil2.extractZip(new ZipFile(otaToolsFile), mOtaToolsUnzipDir);
                mBaseDir = mOtaToolsUnzipDir;
            } else {
                mBaseDir = ZipUtil2.extractZipToTemp(otaToolsFile, "otatools");
            }
            String buildTargetName = remoteBuild.getAttribute("target");
            OtatoolsBuildInfo buildInfo = new OtatoolsBuildInfo(getBuildId(), buildTargetName);
            buildInfo.setBinDir(new File(mBaseDir, DIR_BIN), getBuildId());
            buildInfo.setFrameworkDir(new File(mBaseDir, DIR_FRAMEWORK), getBuildId());
            buildInfo.setReleasetoolsDir(new File(mBaseDir, DIR_RELEASETOOLS), getBuildId());
            buildInfo.setSecurityDir(new File(mBaseDir, DIR_SECURITY), getBuildId());
            return buildInfo;
        } catch (IOException e) {
            deleteCacheEntry(otatoolsName);
            throw new BuildRetrievalError("Could not get otatools", e);
        } finally {
            FileUtil.deleteFile(otaToolsFile);
        }
    }

}

