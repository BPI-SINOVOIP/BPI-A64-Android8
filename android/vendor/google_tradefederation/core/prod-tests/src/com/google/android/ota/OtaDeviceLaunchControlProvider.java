// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.ota;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.OtaDeviceBuildInfo;
import com.android.tradefed.build.OtaToolsDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ZipUtil2;

import com.google.android.tradefed.build.DeviceLaunchControlProvider;
import com.google.android.tradefed.build.LaunchControlProvider;
import com.google.android.tradefed.build.QueryType;
import com.google.android.tradefed.build.RemoteBuildInfo;
import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import java.io.File;
import java.io.IOException;

/**
 * A {@link LaunchControlProvider} for {@link OtaDeviceBuildInfo}.
 * <p/>
 * Retrieves both a baseline build and a OTA build from the Android build server.
 * <p/>
 * The baseline build is retrieved first, and thus the build-flavor, etc arguments provided
 * to this class should match the device build you want to retrieve.
 * <p/>
 */
@OptionClass(alias = "ota-device-launch-control")
public class OtaDeviceLaunchControlProvider extends DeviceLaunchControlProvider {

    // note: --branch option is defined in super class
    @Option(name = "ota-branch", description =
        "the branch of build to OTA to. Defaults to --branch.", importance = Importance.IF_UNSET)
    private String mOtaBranch = null;

    @Option(name = "ota-build-id", description =
        "the optional id of build to OTA.")
    private String mOtaBuildId = null;

    @Option(name = "ota-build-os", description =
        "the optional build OS of the OTA build.")
    private String mOtaBuildOs = null;

    @Option(name = "ota-query-type", description = "the query type to use for build to OTA.")
    private QueryType mOtaQueryType = QueryType.QUERY_LATEST_BUILD;

    @Option(name = "allow-downgrade", description = "whether to allow downgrade OTAs")
    private boolean mAllowDowngrade = true;

    @Option(name = "include-tools", description = "whether or not to include otatools in the build")
    private boolean mIncludeTools = false;

    @Option(name = "report-target-build", description = "whether or not to use the target build " +
            "as the reporting build")
    private boolean mReportTargetBuild = false;

    @Option(name = "ota-build-as-source", description = "if enabled, use the secondary build"
            + "(i.e. ota-build-id) as the source build, and the primary build as target")
    private boolean mOtaBuildAsSource = false;

    @Option(name = "skip-tests", description = "whether or not to skip downloading tests.zip")
    private boolean mSkipTests = true;

    private DeviceLaunchControlProvider mOtaLcProvider;

    private RemoteBuildInfo mOtaRemoteBuild;

    @Override
    public RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
        // First query launch control to determine baseline build to test
        if (mOtaBuildAsSource) {
            addDownloadKey(BuildAttributeKey.OTA_PACKAGE);
        }
        RemoteBuildInfo deviceBuild = super.queryForBuild();
        if (deviceBuild != null) {
            // now query launch control again to get OTA build
            mOtaLcProvider = createOtaLcProvider();
            mOtaLcProvider.setTestTag(getTestTag());
            if (mOtaBranch == null) {
                // get OTA build from same branch as device build
                mOtaBranch = getBranch();
            }
            if (mOtaBuildOs == null) {
                mOtaBuildOs = getBuildOs();
            }
            mOtaLcProvider.setBranch(mOtaBranch);
            mOtaLcProvider.setBuildId(mOtaBuildId);
            mOtaLcProvider.setBuildOs(mOtaBuildOs);
            mOtaLcProvider.addDownloadKey(BuildAttributeKey.OTA_PACKAGE);
            if (mSkipTests) {
                mOtaLcProvider.skipDownload(BuildAttributeKey.TESTS_ZIP);
            }
            mOtaLcProvider.skipDownload(BuildAttributeKey.USER_DATA);
            mOtaLcProvider.skipDownload(BuildAttributeKey.MKBOOTIMG);
            mOtaLcProvider.skipDownload(BuildAttributeKey.RAMDISK);
            mOtaLcProvider.setUseBigStore(shouldUseBigStore());

            if (mOtaBuildId == null && !mAllowDowngrade) {
                try {
                    int minBuildId = Integer.parseInt(deviceBuild.getBuildId());
                    CLog.i("Forcing OTA build to be greater than baseline build id %s",
                            deviceBuild.getBuildId());
                    mOtaLcProvider.setMinBuildId(minBuildId + 1);
                } catch (NumberFormatException e) {
                    // ignore - build id must not be an integer
                }
            }
            mOtaLcProvider.setQueryType(mOtaQueryType);
            mOtaLcProvider.setBuildFlavor(getBuildFlavor());

            mOtaRemoteBuild = queryOtaBuild(deviceBuild.getBuildId(), mOtaLcProvider);
            if (mOtaRemoteBuild != null) {
                return deviceBuild;
            }
        }
        return null;
    }

    protected RemoteBuildInfo getTargetRemoteBuild() {
        return mOtaRemoteBuild;
    }

    protected DeviceLaunchControlProvider getOtaLcProvider() {
        return mOtaLcProvider;
    }

    protected boolean getIncludeTools() {
        return mIncludeTools;
    }

    protected boolean shouldReportTargetBuild() {
        return mReportTargetBuild;
    }

    /**
     * Factory method for creating a {@link DeviceLaunchControlProvider} to query/download build to
     * OTA.
     * <p/>
     * Exposed for unit testing.
     */
    DeviceLaunchControlProvider createOtaLcProvider() {
        return new DeviceLaunchControlProvider();
    }

    /**
     * Query launch control for a OTA build to test.
     *
     * @param deviceLcProvider
     * @return
     * @throws BuildRetrievalError
     */
    private RemoteBuildInfo queryOtaBuild(String deviceBuildId,
            DeviceLaunchControlProvider otaLcProvider) throws BuildRetrievalError {
        RemoteBuildInfo otaBuild = null;
        try {
            otaBuild = otaLcProvider.getRemoteBuild();
        } finally {
            if (otaBuild == null) {
                CLog.i("Failed to retrieve OTA build %s on branch %s",
                        mOtaBuildId, mOtaBranch);
                // reset the device build
                resetTestBuild(deviceBuildId);
            }
        }
        return otaBuild;
    }

    @Override
    public IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {

        IDeviceBuildInfo baselineBuild = (IDeviceBuildInfo)super.downloadBuildFiles(remoteBuild,
                testTargetName, buildName, downloader);
        IDeviceBuildInfo otaBuild = null;
        try {
            otaBuild = (IDeviceBuildInfo)mOtaLcProvider.fetchRemoteBuild(mOtaRemoteBuild);
            OtaDeviceBuildInfo otaAndDeviceBuild = new OtaDeviceBuildInfo();
            otaAndDeviceBuild.setBaselineBuild(baselineBuild);
            otaAndDeviceBuild.setOtaBuild(otaBuild);
            if (mIncludeTools) {
                final String otatoolsName = String.format("%s-linux-%s/%s/%s",
                        getBranch(),
                        getBuildFlavor(),
                        remoteBuild.getBuildId(),
                        "otatools.zip");
                File otatoolsFile = downloader.downloadFile(otatoolsName);
                File otatoolsUnzipDir = ZipUtil2.extractZipToTemp(otatoolsFile, "otatools");
                OtaToolsDeviceBuildInfo otaToolsBuild = new OtaToolsDeviceBuildInfo(
                        otaAndDeviceBuild);
                otaToolsBuild.setOtaTools(otatoolsUnzipDir);
                otaToolsBuild.setReportTargetBuild(shouldReportTargetBuild());
                return otaToolsBuild;
            }
            otaAndDeviceBuild.setReportTargetBuild(shouldReportTargetBuild());
            return setupSourceBuild(otaAndDeviceBuild);
        } catch (IOException e) {
            throw new BuildRetrievalError("Error unzipping otatools", e);
        } finally {
            if (otaBuild == null) {
                CLog.i("Failed to download OTA build %s on branch %s",
                       mOtaBuildId, mOtaBranch);
                baselineBuild.cleanUp();
            }
        }
    }

    /**
     * Set the OTA build ID. Exposed for subclasses to access mOtaBuildId.
     * @param otaBuildId the build ID to set
     */
    void setOtaBuildId(String otaBuildId) {
        mOtaBuildId = otaBuildId;
    }

    /**
     * Set whether or not to swap the two builds. Exposed for testing.
     * @param otaBuildAsSource
     */
    void setOtaBuildAsSource(boolean otaBuildAsSource) {
        mOtaBuildAsSource = otaBuildAsSource;
    }

    /**
     * Exposed for subclasses.
     * @param allowDowngrade
     */
    void setAllowDowngrade(boolean allowDowngrade) {
        mAllowDowngrade = allowDowngrade;
    }

    /**
     * Exposed for subclasses.
     * @param skipTests
     */
    void setSkipTests(boolean skipTests) {
        mSkipTests = skipTests;
    }

    /**
     * Exposed for subclasses.
     * @param reportTargetBuild
     */
    void setReportTargetBuild(boolean reportTargetBuild) {
        mReportTargetBuild = reportTargetBuild;
    }

    /**
     * Whether or not to swap the two builds. Exposed for subclasses.
     * @return whether or not to swap
     */
    boolean useOtaBuildAsSource() {
        return mOtaBuildAsSource;
    }

    /**
     * Exposed for subclasses.
     * @return allow-downgrade
     */
    boolean allowDowngrade() {
        return mAllowDowngrade;
    }

    /**
     * Exposed for subclasses.
     * @return skip-tests
     */
    boolean skipTests() {
        return mSkipTests;
    }

    /**
     * Exposed for subclasses.
     * @return report-target-build
     */
    boolean reportTargetBuild() {
        return mReportTargetBuild;
    }

    /**
     * If required, swap the source and target builds.
     */
    OtaDeviceBuildInfo setupSourceBuild(OtaDeviceBuildInfo otaBuildInfo) {
        if (mOtaBuildAsSource) {
            CLog.i("Swapping source build (%s) and target build (%s)",
                    otaBuildInfo.getBaselineBuild().getBuildId(),
                    otaBuildInfo.getOtaBuild().getBuildId());
            IDeviceBuildInfo swap = otaBuildInfo.getOtaBuild();
            otaBuildInfo.setOtaBuild(otaBuildInfo.getBaselineBuild());
            otaBuildInfo.setBaselineBuild(swap);
        }
        return otaBuildInfo;
    }
}
