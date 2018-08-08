// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.FolderBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.build.ISdkBuildInfo;
import com.android.tradefed.build.LocalSdkBuildProvider;
import com.android.tradefed.build.SdkFolderBuildInfo;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * A {@link LocalSdkBuildProvider} that constructs a {@link ISdkBuildInfo} with a local sdk and
 * launch control sdk tools
 */
public class LocalSdkWithExtraToolsProvider extends LocalSdkBuildProvider implements
        IConfigurationReceiver {
    private IConfiguration mConfiguration;

    @Option(name = "sdk-tools-provider",
            description = "provider extra sdk tools; local or sdk-tools-lc-provider")
    private String mSdkToolsProvider = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        // if no extra tools provider, use the one in sdk
        if (mSdkToolsProvider == null || mSdkToolsProvider.trim().equals("local")) {
            return super.getBuild();
        }

        SdkToolsLaunchControlProvider toolsLcProvider = null;
        FolderBuildInfo toolsBuild = null;
        ISdkBuildInfo sdkBuild = null;

        try {
            // fetch and replace tools in sdk
            toolsLcProvider = (SdkToolsLaunchControlProvider) mConfiguration.getConfigurationObject(
                    mSdkToolsProvider);
            toolsBuild = (FolderBuildInfo) toolsLcProvider.getBuild();
            if (toolsBuild == null) {
                // no new tools build
                return null;
            }
            sdkBuild = (ISdkBuildInfo) super.getBuild();
            deleteToolsInSdk(sdkBuild);
            copyToolsIntoSdkAndMakeExecutable(sdkBuild, toolsBuild);
            SdkFolderBuildInfo toolsAndSdkBuild = null;
            toolsAndSdkBuild = new SdkFolderBuildInfo(toolsBuild.getBuildId(),
                    toolsBuild.getBuildTargetName());
            toolsAndSdkBuild.setSdkBuild(sdkBuild);
            return toolsAndSdkBuild;
        } catch (BuildRetrievalError e) {
            e.setBuildInfo(toolsBuild);
            throw (e);
        } finally {
            // once the tools folder is copied it's no longer useful
            if (toolsBuild != null) {
                toolsBuild.cleanUp();
            }
        }
    }

    protected void deleteToolsInSdk(ISdkBuildInfo sdkBuildInfo) {
        File toolsDir = new File(sdkBuildInfo.getSdkDir(), "tools");
        if (toolsDir.exists()) {
            FileUtil.recursiveDelete(toolsDir);
        }
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
}
