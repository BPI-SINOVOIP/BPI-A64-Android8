// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.build.ISdkBuildInfo;
import com.android.tradefed.build.SdkBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil2;

import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import java.io.File;
import java.io.IOException;

/**
 * A {@link LaunchControlProvider} for a {@link ISdkBuildInfo}.
 * <p/>
 * Retrieves and extracts a SDK zip and tests zip from the Android build server.
 */
@OptionClass(alias = "sdk-launch-control")
public class SdkLaunchControlProvider extends LaunchControlProvider {

    static final String SDK_BUILD_ATTRIBUTE_KEY = "sdk";
    static final String TESTS_ZIP_HINT = "sdk_tests";

    @Option(name = "tests-zip-filter",
            description = "Optional tests zip file pattern to download from the build server. " +
            "An exception will be thrown if no files are found matching the given pattern." +
            "If left blank, no tests zip file will be downloaded.")
    private String mTestsZipPattern = null;

    @Option(name = "tools-branch", description = "the branch to download the tools build from." +
            "Defaults to not download tools separately")
    private String mToolsBranch = null;

    @Option(name = "tools-build-id", description =
            "the id of tools build. Defaults to latest green build in branch.")
    private String mToolsBuildId = null;

    @Option(name = "tools-flavor", description =
            "the id of tools build. Defaults to latest build in branch.")
    private String mToolsFlavor = "sdk_tools_linux";

    @Option(name = "tools-query-type", description = "the query type to use for tools build.")
    private QueryType mToolsQueryType = QueryType.QUERY_LATEST_BUILD;

    /**
     * Set the tools query type
     * @param toolsQueryType
     */
    public void setToolsQueryType(QueryType toolsQueryType) {
        this.mToolsQueryType = toolsQueryType;
    }

    /**
     * Set the tools flavor to query
     * @param toolsFlavor
     */
    public void setToolsFlavor(String toolsFlavor) {
        this.mToolsFlavor = toolsFlavor;
    }

    /**
     * Set the tools build id to query
     * @param toolsBuildId
     */
    public void setToolsBuildId(String toolsBuildId) {
        this.mToolsBuildId = toolsBuildId;
    }

    /**
     * Set the tools branch to query
     * @param toolsBranch
     */
    public void setToolsBranch(String toolsBranch) {
        this.mToolsBranch = toolsBranch;
    }

    public SdkLaunchControlProvider() {
        setBuildFlavor(SDK_BUILD_ATTRIBUTE_KEY);
    }

    File downloadTestZip(RemoteBuildInfo remoteBuild, IFileDownloader downloader)
            throws BuildRetrievalError {
        final String filesPathCSVString = remoteBuild.getAttribute(BuildAttributeKey.FILES);
        if (mTestsZipPattern == null || filesPathCSVString == null) {
            return null;
        }
        String[] filePaths;
        if (filesPathCSVString.isEmpty()) {
            filePaths = new String[] {};
        } else {
            filePaths = filesPathCSVString.split(",");
        }
        for (String filePath : filePaths) {
            if (filePath.matches(mTestsZipPattern)) {
               return downloader.downloadFile(filePath);
            }
        }
        throw new BuildRetrievalError(String.format(
                "Could not find the test zip file matching pattern '%s'", mTestsZipPattern));
    }

    protected void copyOptions(SdkToolsLaunchControlProvider toolsLcProvider)
            throws BuildRetrievalError {
        try {
            OptionCopier.copyOptions(this, toolsLcProvider);
        } catch (ConfigurationException e) {
            throw new BuildRetrievalError("Failed to copy options to device lc provider", e);
        }
        toolsLcProvider.setBuildFlavor(mToolsFlavor);
        toolsLcProvider.setBranch(mToolsBranch);
        toolsLcProvider.setQueryType(mToolsQueryType);
        toolsLcProvider.setBuildId(mToolsBuildId);
        toolsLcProvider.setMinBuildId(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        ISdkBuildInfo sdkBuildInfo = null;
        IFolderBuildInfo toolsFolderBuildInfo = null;

        final RemoteBuildInfo remoteSdkBuild = getRemoteBuild();

        if (remoteSdkBuild == null) {
            return null;
        }

        sdkBuildInfo = (ISdkBuildInfo) fetchRemoteBuild(remoteSdkBuild);
        if (mToolsBranch != null) {
            try {
                // query launch control to determine tools build to test
                SdkToolsLaunchControlProvider toolsLcProvider = createToolsLcProvider();
                copyOptions(toolsLcProvider);

                toolsFolderBuildInfo = (IFolderBuildInfo) toolsLcProvider.getBuild();
                // If no build is retrieved for tools LCP will return null
                // Since tools is required for a usable sdk, throw a BuildRetrievalError
                if (toolsFolderBuildInfo == null) {
                    throw new BuildRetrievalError("Failed to download tools build."+
                            " There was no build available for download.");
                }
                copyToolsIntoSdkAndMakeExecutable(sdkBuildInfo, toolsFolderBuildInfo);
            } catch (BuildRetrievalError e) {
                // When tools fails to download the sdk would be leaked unless we clean it up here
                cleanUp(sdkBuildInfo);
                throw (e);
            } finally {
                // once the tools folder is copied it's no longer useful
                cleanUp(toolsFolderBuildInfo);
            }
        }
        return sdkBuildInfo;

    }

    protected void copyToolsIntoSdkAndMakeExecutable(ISdkBuildInfo sdkBuildInfo,
            IFolderBuildInfo toolsFolderBuildInfo) throws BuildRetrievalError {
        try {
            FileUtil.recursiveCopy(toolsFolderBuildInfo.getRootDir(), sdkBuildInfo.getSdkDir());
            sdkBuildInfo.makeToolsExecutable();
        } catch (IOException e) {
            throw new BuildRetrievalError("Failed to copy tools into SDK.");
        }
    }

    protected SdkToolsLaunchControlProvider createToolsLcProvider() {
       return new SdkToolsLaunchControlProvider();
    }

    @Override
    protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        ISdkBuildInfo localBuild = new SdkBuildInfo(remoteBuild.getBuildId(), buildName);
        localBuild.setTestTag(testTargetName);

        String remoteSdkZipPath = remoteBuild.getAttribute(SDK_BUILD_ATTRIBUTE_KEY);
        if (remoteSdkZipPath == null) {
            throw new BuildRetrievalError(String.format("Invalid launch control response. " +
                    "Could not find %s key in response %s", SDK_BUILD_ATTRIBUTE_KEY,
                    remoteBuild.toString()), null, localBuild);
        }

        File sdkZip = null;
        File testZip = null;

        try {
            sdkZip = downloader.downloadFile(remoteSdkZipPath);
            File sdkParentDir = ZipUtil2.extractZipToTemp(sdkZip, SDK_BUILD_ATTRIBUTE_KEY);
            // this will create localRootDir/<root dir sdk>/<sdkcontents> directory structure
            // we need path to just <root dir sdk>
            localBuild.setSdkDir(getSubDir(sdkParentDir), true);
            testZip = downloadTestZip(remoteBuild, downloader);
            if (testZip != null) {
                localBuild.setTestsDir(ZipUtil2.extractZipToTemp(testZip, TESTS_ZIP_HINT));
            } else {
                CLog.w("Did not fetch remote test zip file");
            }
            return localBuild;
        } catch (BuildRetrievalError e) {
            cleanUp(localBuild);
            e.setBuildInfo(localBuild);
            throw e;
        } catch (IOException e) {
            cleanUp(localBuild);
            throw new BuildRetrievalError("Failed to extract SDK or tests zips", e, localBuild);
        } finally {
            FileUtil.deleteFile(sdkZip);
            FileUtil.deleteFile(testZip);
        }
    }



    /**
     * Gets the single subdirectory of given parent dir.
     * <p/>
     * If single sub dir does not exist, BuildRetrievalError will be thrown and parentDir will be
     * deleted
     *
     * @param parentDir
     * @return the subdirectory
     * @throws BuildRetrievalError
     */
    File getSubDir(File parentDir) throws BuildRetrievalError {
        File[] childFiles = parentDir.listFiles();
        if (childFiles != null && childFiles.length == 1) {
            return childFiles[0];
        } else {
            FileUtil.recursiveDelete(parentDir);
            throw new BuildRetrievalError(
                    "Unrecognized sdk zip contents. Could not find sub-directory");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        if(info != null) {
            info.cleanUp();
        }
    }
}
