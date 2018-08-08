// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.roboelectric;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil2;

import com.google.android.tradefed.build.LaunchControlProvider;
import com.google.android.tradefed.build.RemoteBuildInfo;
import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

/**
 * A {@link LaunchControlProvider} to fetches the RoboElectric framework and
 * extracts it, it also fetches the RoboElectric tests and puts them in the same
 * directory.
 */
@OptionClass(alias = "roboelectric-launch-control")
public class RoboElectricLaunchControlProvider extends LaunchControlProvider {

    @Option(name = "robo-framework-zip",
            description = "The RoboElectric Test Framework zip file.",
            importance = Importance.ALWAYS, mandatory = true)
    private String mRoboElectricFrameworkZip;

    @Option(name = "robo-test-target-jar",
            description = "The file name of the RoboElectric Test target jar.",
            importance = Importance.ALWAYS, mandatory = true)
    private String mTestTargetFileName;

    @Option(name = "robo-tests-jar",
            description = "The RoboElectric Test Suite Jar file(s). " +
            "May be repeated to specify multiple jars",
            importance = Importance.ALWAYS, mandatory = true)
    private Collection<String> mTestJarFiles = new LinkedList<String>();

    @Override
    public void cleanUp(IBuildInfo info) {
        info.cleanUp();
    }

    static final String ROBO_ELECTRIC_TAG = "ROBOELECTRIC";

    @Override
    protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        RoboElectricBuildInfo buildInfo = new RoboElectricBuildInfo(remoteBuild.getBuildId(),
                buildName);
        buildInfo.setTestTag(testTargetName);
        final String filesPathCSVString = remoteBuild.getAttribute(BuildAttributeKey.FILES);

        String[] filePaths = filesPathCSVString.split(",");
        try {
            File unzipDir = FileUtil.createTempDir(ROBO_ELECTRIC_TAG);
            buildInfo.setRootDir(unzipDir);
            for (String filePath : filePaths) {
                if (filePath.matches(mRoboElectricFrameworkZip)) {
                    CLog.d("Downloading and unzipping %s to %s", filePath,
                            unzipDir.getAbsolutePath());
                    File zip = downloader.downloadFile(filePath);
                    ZipUtil2.extractZip(new ZipFile(zip), unzipDir);
                    zip.delete();
                    buildInfo.setTestTargetFileName(mTestTargetFileName);
                }
                for (String testJarPath : mTestJarFiles) {
                    if (filePath.matches(testJarPath)) {
                        String destFileName = new File(filePath).getName();
                        File destFile = new File(unzipDir, destFileName);
                        CLog.d("Downloading file %s to %s", filePath, destFile.getAbsolutePath());
                        File testJar = downloader.downloadFile(filePath);

                        FileUtil.copyFile(testJar, destFile);
                        testJar.delete();
                        buildInfo.addTestSuiteFile(destFile);
                    }
                }
            }


        } catch (IOException ioException) {
            throw new BuildRetrievalError("Failed to download RoboElectric test file.", null,
                    buildInfo);
        }
        return buildInfo;
    }
}
