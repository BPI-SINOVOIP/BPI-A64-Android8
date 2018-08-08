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
 * Retrieves both a VTS build and a device build from the Android build server.
 * <p/>
 * The device build is retrieved first, and thus the build-flavor, etc arguments provided
 * to this class should match the device build you want to retrieve.
 * <p/>
 * By default, this class will attempt to download a VTS build with same build-id and
 * same branch as device build. The vts* options may be used to retrieve a different VTS build.
 */
@OptionClass(alias = "vts-device-launch-control")
public class VtsDeviceLaunchControlProvider extends DeviceLaunchControlProvider {
    // note: --branch option is defined in super class
    @Option(name = "vts-branch", description = "the branch of VTS build. Defaults to --branch.")
    private String mVtsBranch = null;

    @Option(name = "vts-build-id",
            description = "the id of VTS build. Defaults to build id of device build.")
    private String mVtsBuildId = null;

    @Option(name = "vts-build-flavor", description = "the build flavor of VTS. Defaults to vts")
    private String mVtsBuildFlavor = "vts";

    @Option(name = "vts-package-name", description = "the filename of VTS package to download. "
                    + "Defaults to \"android-vts.zip\"")
    private String mVtsPackageName = "android-vts.zip";

    @Option(name = "vts-extra-artifact",
            description =
                    "the filename of VTS extra artefact to download, may be repearted for multiple fetches.")
    private List<String> mVtsExtra = new ArrayList<String>();

    @Option(name = "vts-extra-alt-path", description = "Alternative location where to look for "
                    + "extra artifact if they are not found on the build server.")
    private File mExtraAltPath = null;

    @Option(name = "throw-if-extra-not-found", description = "Throw an exception if one of the "
                    + "extra specified file by vts-extra-artifact is not found.")
    private boolean mThrowIfExtraNotFound = true;

    @Option(name = "vts-query-type", description = "the query type to use for VTS build.")
    private QueryType mVtsQueryType = QueryType.NOTIFY_TEST_BUILD;

    private VtsLaunchControlProvider mVtsLcProvider;

    /**
     * {@inheritDoc}
     */
    @Override
    public RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
        // First query launch control to determine device build to test
        RemoteBuildInfo deviceBuild = super.queryForBuild();
        if (deviceBuild != null) {
            // now query launch control again to get VTS build
            // TODO: this is a bit of a hack. consider moving this logic to launch control server
            mVtsLcProvider = createVtsLcProvider();
            mVtsLcProvider.setTestTag(getTestTag());
            if (mVtsBranch == null) {
                // get VTS build from same branch as device build
                mVtsBranch = getBranch();
            }
            mVtsLcProvider.setBranch(mVtsBranch);

            if (mVtsBuildId == null && QueryType.NOTIFY_TEST_BUILD.equals(mVtsQueryType)) {
                // get same VTS build id as device build
                mVtsBuildId = deviceBuild.getBuildId();
            }
            mVtsLcProvider.setBuildId(mVtsBuildId);
            mVtsLcProvider.setQueryType(mVtsQueryType);
            mVtsLcProvider.setBuildFlavor(mVtsBuildFlavor);
            mVtsLcProvider.setVtsPackageName(mVtsPackageName);
            mVtsLcProvider.setVtsExtra(mVtsExtra);
            mVtsLcProvider.setVtsExtraAltPath(mExtraAltPath);
            mVtsLcProvider.setThrowIfExtraNotFound(mThrowIfExtraNotFound);
            // passing LC host params down
            mVtsLcProvider.setLcHostname(getLcHostname());
            mVtsLcProvider.setUseSsoClient(getUseSsoClient());
            mVtsLcProvider.setLcProtocol(getLcProtocol());

            RemoteBuildInfo vtsBuild = queryVtsBuild(deviceBuild.getBuildId(), mVtsLcProvider);
            if (vtsBuild != null) {
                // copy vts attributes to the device remote build
                deviceBuild.addAttribute(
                        BuildAttributeKey.VTS, vtsBuild.getAttribute(BuildAttributeKey.VTS));
                return deviceBuild;
            }
        }
        return null;
    }

    /**
     * Factory method for creating a {@link VtsLaunchControlProvider} to query/download VTS
     * builds.
     * <p/>
     * Exposed for unit testing.
     */
    VtsLaunchControlProvider createVtsLcProvider() {
        return new VtsLaunchControlProvider();
    }

    /**
     * Query launch control for a VTS build to test.
     *
     * @param deviceBuildId build id from the device build
     * @param vtsLcProvider a launch control provider for vts build
     * @return the {link RemoteBuildInfo} of the Vts build
     * @throws BuildRetrievalError
     */
    private RemoteBuildInfo queryVtsBuild(String deviceBuildId,
            VtsLaunchControlProvider vtsLcProvider) throws BuildRetrievalError {
        RemoteBuildInfo vtsBuild = null;
        try {
            vtsBuild = vtsLcProvider.queryForBuild();
        } finally {
            if (vtsBuild == null) {
                CLog.i("Failed to retrieve VTS build %s on branch %s", mVtsBuildId, mVtsBranch);
                // reset the device build
                resetTestBuild(deviceBuildId);
            }
        }
        return vtsBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        IDeviceBuildInfo deviceBuild = (IDeviceBuildInfo) super.downloadBuildFiles(
                remoteBuild, testTargetName, buildName, downloader);
        IFolderBuildInfo vtsBuild = null;
        try {
            vtsBuild = (IFolderBuildInfo) mVtsLcProvider.downloadBuildFiles(
                    remoteBuild, testTargetName, buildName, downloader);
            DeviceFolderBuildInfo vtsAndDeviceBuild =
                    new DeviceFolderBuildInfo(remoteBuild.getBuildId(), buildName);
            vtsAndDeviceBuild.setTestTag(testTargetName);
            vtsAndDeviceBuild.setFolderBuild(vtsBuild);
            vtsAndDeviceBuild.setDeviceBuild(deviceBuild);
            for (String extra : mVtsExtra) {
                if (vtsBuild.getFile(extra) != null) {
                    vtsAndDeviceBuild.setFile(extra, vtsBuild.getFile(extra), null);
                }
            }
            return vtsAndDeviceBuild;
        } finally {
            if (vtsBuild == null) {
                CLog.i("Failed to download VTS build %s on branch %s", mVtsBuildId, mVtsBranch);
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
        if (mVtsLcProvider != null) {
            mVtsLcProvider.cleanExtra();
        }
    }
}
