// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.build.ISdkBuildInfo;
import com.android.tradefed.build.SdkFolderBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionUpdateRule;

import java.util.Map;

/**
 * A {@link LaunchControlProvider} for {@link SdkFolderBuildInfo}. Retrieves a SDK tool and a SDK
 * build from the Android build server. This is used for testing SDK tool.
 */
public class EmulatorLaunchControlProvider extends SdkLaunchControlProvider {

    @Option(name = "min-tools-build-id", description = "Minimum tools build id to test. " +
            "Builds less than this value will be skipped.", updateRule = OptionUpdateRule.GREATEST)
    private Integer mMinToolsBuildId = -1;

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        SdkFolderBuildInfo toolsAndSdkBuild = null;
        IFolderBuildInfo toolsFolderBuildInfo = null;

        final RemoteBuildInfo remoteSdkBuild = getRemoteBuild();
        if (remoteSdkBuild == null) {
            return null;
        }
        ISdkBuildInfo sdkBuildInfo = (ISdkBuildInfo) fetchRemoteBuild(remoteSdkBuild);

        try {
            // query launch control to determine tools build to test
            SdkToolsLaunchControlProvider toolsLcProvider = createToolsLcProvider();
            copyOptions(toolsLcProvider);
            toolsLcProvider.setMinBuildId(mMinToolsBuildId);

            toolsFolderBuildInfo = (IFolderBuildInfo) toolsLcProvider.getBuild();
            // If no build is retrieved for tools LCP will return null
            // here we also want to test newest build tools, so if no tools we will return
            // null
            if (toolsFolderBuildInfo == null) {
                cleanUp(sdkBuildInfo);
                return null;
            }
            copyToolsIntoSdkAndMakeExecutable(sdkBuildInfo, toolsFolderBuildInfo);

            // here we use the tools build id as the build id, because we want to follow
            // the tools branch, not the sdk branch. but currently we only need to check one
            // tools branch and we need to run this tools branch on multi sdk, so we still use
            // the sdk branch as the build branch
            toolsAndSdkBuild = new SdkFolderBuildInfo(toolsFolderBuildInfo.getBuildId(),
                    sdkBuildInfo.getBuildTargetName());
            toolsAndSdkBuild.setSdkBuild(sdkBuildInfo);
            toolsAndSdkBuild.setBuildBranch(sdkBuildInfo.getBuildBranch());
            toolsAndSdkBuild.setBuildFlavor(sdkBuildInfo.getBuildFlavor());
            for (Map.Entry<String, String> mapEntry :
                    sdkBuildInfo.getBuildAttributes().entrySet()) {
                if (!mapEntry.getKey().equals("build_alias")) {
                    toolsAndSdkBuild.addBuildAttribute(mapEntry.getKey(), mapEntry.getValue());
                }
            }
            return toolsAndSdkBuild;

        } catch (BuildRetrievalError e) {
            cleanUp(sdkBuildInfo);
            throw e;
        } finally {
            // once the tools folder is copied it's no longer useful
            cleanUp(toolsFolderBuildInfo);
        }
    }
}
