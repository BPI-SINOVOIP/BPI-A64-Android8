// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.DeviceFolderBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;

import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link LaunchControlProvider} for {@link DeviceFolderBuildInfo}.
 * <p/>
 * Retrieves both a CTS build and a device build from the Android build server.
 * <p/>
 * The device build is retrieved first, and thus the build-flavor, etc arguments provided
 * to this class should match the device build you want to retrieve.
 * <p/>
 * By default, this class will attempt to download a CTS build with same build-id and
 * same branch as device build. The cts* options may be used to retrieve a different CTS build.
 */
@OptionClass(alias = "cts-device-launch-control")
public class CtsDeviceLaunchControlProvider extends DeviceLaunchControlProvider {

    // note: --branch option is defined in super class
    @Option(name = "cts-branch", description =
        "the branch of CTS build. Defaults to --branch.")
    private String mCtsBranch = null;

    @Option(name = "cts-build-id", description =
        "the id of CTS build. Defaults to build id of device build.")
    private String mCtsBuildId = null;

    @Option(name = "cts-build-flavor", description = "the build flavor of CTS. Defaults to cts")
    private String mCtsBuildFlavor = "cts";

    @Option(name = "cts-package-name", description = "the filename of CTS package to download. "
        + "Defaults to \"android-cts.zip\"")
    private String mCtsPackageName = "android-cts.zip";

    @Option(name = "cts-extra-artifact", description =
        "the filename of CTS extra artefact to download, may be repearted for multiple fetches.")
    private List<String> mCtsExtra = new ArrayList<String>();

    @Option(
        name = "cts-extra-alt-path",
        description =
                "Alternative location where to look for "
                        + "extra artifact if they are not found on the build server."
    )
    private File mExtraAltPath = null;

    @Option(name = "throw-if-extra-not-found", description = "Throw an exception if one of the "
            + "extra specified file by cts-extra-artifact is not found.")
    private boolean mThrowIfExtraNotFound = true;

    @Option(name = "cts-query-type", description = "the query type to use for CTS build.")
    private QueryType mCtsQueryType = QueryType.NOTIFY_TEST_BUILD;

    private CtsLaunchControlProvider mCtsLcProvider;

    @Override
    public RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
        // First query launch control to determine device build to test
        RemoteBuildInfo deviceBuild = super.queryForBuild();
        if (deviceBuild != null) {
            // now query launch control again to get CTS build
            // TODO: this is a bit of a hack. consider moving this logic to launch control server
            mCtsLcProvider = createCtsLcProvider();
            mCtsLcProvider.setTestTag(getTestTag());
            if (mCtsBranch == null) {
                // get CTS build from same branch as device build
                mCtsBranch = getBranch();
            }
            mCtsLcProvider.setBranch(mCtsBranch);

            if (mCtsBuildId == null && QueryType.NOTIFY_TEST_BUILD.equals(mCtsQueryType)) {
                // get same CTS build id as device build
                mCtsBuildId = deviceBuild.getBuildId();
            }
            mCtsLcProvider.setBuildId(mCtsBuildId);
            mCtsLcProvider.setQueryType(mCtsQueryType);
            mCtsLcProvider.setBuildFlavor(mCtsBuildFlavor);
            mCtsLcProvider.setCtsPackageName(mCtsPackageName);
            mCtsLcProvider.setCtsExtra(mCtsExtra);
            mCtsLcProvider.setCtsExtraAltPath(mExtraAltPath);
            mCtsLcProvider.setThrowIfExtraNotFound(mThrowIfExtraNotFound);
            // passing LC host params down
            mCtsLcProvider.setLcHostname(getLcHostname());
            mCtsLcProvider.setUseSsoClient(getUseSsoClient());
            mCtsLcProvider.setLcProtocol(getLcProtocol());

            RemoteBuildInfo ctsBuild = queryCtsBuild(deviceBuild.getBuildId(),
                    mCtsLcProvider);
            if (ctsBuild != null) {
                // copy cts attributes to the device remote build
                deviceBuild.addAttribute(BuildAttributeKey.CTS,
                        ctsBuild.getAttribute(BuildAttributeKey.CTS));
                return deviceBuild;
            }
        }
        return null;
    }

    /**
     * Factory method for creating a {@link CtsLaunchControlProvider} to query/download CTS
     * builds.
     * <p/>
     * Exposed for unit testing.
     */
    CtsLaunchControlProvider createCtsLcProvider() {
        return new CtsLaunchControlProvider();
    }

    /**
     * Query launch control for a CTS build to test.
     *
     * @param deviceBuildId build id from the device build
     * @param ctsLcProvider a launch control provider for cts build
     * @return the {link RemoteBuildInfo} of the Cts build
     * @throws BuildRetrievalError
     */
    private RemoteBuildInfo queryCtsBuild(String deviceBuildId,
            CtsLaunchControlProvider ctsLcProvider) throws BuildRetrievalError {
        RemoteBuildInfo ctsBuild = null;
        try {
            ctsBuild = ctsLcProvider.queryForBuild();
        } finally {
            if (ctsBuild == null) {
                CLog.i("Failed to retrieve CTS build %s on branch %s",
                        mCtsBuildId,  mCtsBranch);
                // reset the device build
                resetTestBuild(deviceBuildId);
            }
        }
        return ctsBuild;
    }

    @Override
    public IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        IDeviceBuildInfo deviceBuild = (IDeviceBuildInfo)super.downloadBuildFiles(remoteBuild,
                testTargetName, buildName, downloader);
        IFolderBuildInfo ctsBuild = null;
        try {
            ctsBuild = (IFolderBuildInfo)mCtsLcProvider.downloadBuildFiles(
                    remoteBuild, testTargetName, buildName, downloader);
            DeviceFolderBuildInfo ctsAndDeviceBuild = new DeviceFolderBuildInfo(
                    remoteBuild.getBuildId(), buildName);
            ctsAndDeviceBuild.setTestTag(testTargetName);
            ctsAndDeviceBuild.setFolderBuild(ctsBuild);
            ctsAndDeviceBuild.setDeviceBuild(deviceBuild);
            for (String extra : mCtsExtra) {
                if (ctsBuild.getFile(extra) != null) {
                    ctsAndDeviceBuild.setFile(extra, ctsBuild.getFile(extra), null);
                }
            }
            return ctsAndDeviceBuild;
        } finally {
            if (ctsBuild == null) {
                CLog.i("Failed to download CTS build %s on branch %s",
                       mCtsBuildId, mCtsBranch);
                deviceBuild.cleanUp();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        super.cleanUp(info);
        // Clean up extras if any
        if (mCtsLcProvider != null) {
            mCtsLcProvider.cleanExtra();
        }
    }
}
