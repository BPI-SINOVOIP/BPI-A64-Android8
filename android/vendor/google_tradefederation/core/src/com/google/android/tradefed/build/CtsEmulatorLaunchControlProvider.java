// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.build.ISdkBuildInfo;
import com.android.tradefed.build.SdkFolderBuildInfo;
import com.android.tradefed.config.Option;

import java.util.Map;

/**
 * A {@link LaunchControlProvider} for {@link SdkFolderBuildInfo}.
 * <p/>
 * Retrieves both a CTS build and a SDK build from the Android build server.
 * <p/>
 * The SDK build is retrieved first, and thus the build-flavor, etc arguments provided
 * to this class should match the build you want to retrieve.
 * <p/>
 * By default, this class will attempt to download a CTS build with same build-id and
 * same branch as the SDK build. The cts* options may be used to retrieve a different CTS build.
 */
public class CtsEmulatorLaunchControlProvider extends SdkLaunchControlProvider {

 // note: --branch option is defined in super class
    @Option(name = "cts-branch", description =
        "the branch of CTS build. Defaults to --branch.")
    private String mCtsBranch = null;

    @Option(name = "cts-build-id", description =
        "the id of CTS build. Defaults to build id of SDK build.")
    private String ctsBuildId = null;

    @Option(name = "cts-query-type", description = "the query type to use for CTS build.")
    private QueryType mCtsQueryType = QueryType.NOTIFY_TEST_BUILD;

    @Option(name = "cts-build-flavor", description =
            "the build flavor of CTS build. Defaults to cts.")
    private String mCtsBuildFlavor = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        SdkFolderBuildInfo ctsAndSdkBuild = null;
        ISdkBuildInfo sdkBuild = null;
        IFolderBuildInfo ctsBuild = null;
        try {
            // get the sdk and sdk tools
            sdkBuild = (ISdkBuildInfo) super.getBuild();
            if (sdkBuild == null) {
                return null;
            }
            // query launch control for CTS build to test.
            CtsLaunchControlProvider ctsLcProvider = createCtsLcProvider();
            ctsLcProvider.setTestTag(getTestTag());
            if (mCtsBranch == null) {
                // get CTS build from same branch as SDK build
                mCtsBranch = getBranch();
            }
            if (mCtsBuildFlavor != null) {
                ctsLcProvider.setBuildFlavor(mCtsBuildFlavor);
            }
            ctsLcProvider.setBranch(mCtsBranch);

            if (ctsBuildId == null && QueryType.NOTIFY_TEST_BUILD.equals(mCtsQueryType)) {
                // get same CTS build id as SDK build
                ctsBuildId = sdkBuild.getBuildId();
            }
            ctsLcProvider.setBuildId(ctsBuildId);
            ctsLcProvider.setQueryType(mCtsQueryType);

            final RemoteBuildInfo ctsRemoteBuild = ctsLcProvider.getRemoteBuild();
            if (ctsRemoteBuild == null) {
                throw new BuildRetrievalError("Failed to retrieve CTS build");
            }
            ctsBuild = (IFolderBuildInfo) ctsLcProvider.fetchRemoteBuild(ctsRemoteBuild);
            ctsAndSdkBuild = new SdkFolderBuildInfo(sdkBuild.getBuildId(),
                    sdkBuild.getBuildTargetName());
            ctsAndSdkBuild.setSdkBuild(sdkBuild);
            ctsAndSdkBuild.setBuildBranch(sdkBuild.getBuildBranch());
            ctsAndSdkBuild.setBuildFlavor(sdkBuild.getBuildFlavor());
            for (Map.Entry<String, String> mapEntry : sdkBuild.getBuildAttributes().entrySet()) {
                ctsAndSdkBuild.addBuildAttribute(mapEntry.getKey(), mapEntry.getValue());
            }
            ctsAndSdkBuild.setFolderBuild(ctsBuild);
            return ctsAndSdkBuild;
        } catch (BuildRetrievalError e) {
            if (sdkBuild != null) {
                resetTestBuild(sdkBuild.getBuildId());
                sdkBuild.cleanUp();
            }
            throw e;
        }
    }

    CtsLaunchControlProvider createCtsLcProvider() {
        return new CtsLaunchControlProvider();
    }
}
