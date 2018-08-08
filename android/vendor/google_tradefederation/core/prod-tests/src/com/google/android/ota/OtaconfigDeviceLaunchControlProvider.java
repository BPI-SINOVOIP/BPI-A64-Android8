// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.ota;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.OtaDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.android.tradefed.build.DeviceLaunchControlProvider;
import com.google.android.tradefed.build.RemoteBuildInfo;

/**
 * A {@link DeviceLaunchControlProvider} that uses information from go/otaconfig to
 * provide builds for specific OTA groups (i.e. droidfood, experimental, etc) and
 * corresponding OTA packages.
 */
public class OtaconfigDeviceLaunchControlProvider extends OtaDeviceLaunchControlProvider {

    @Option(name = "ota-group", description = "The name of the OTA group to use")
    private String mOtaGroup;

    @Option(name = "otaconfig-reader-path", description = "path to otaconfigreader util")
    private String mOtaConfigPath = DEFAULT_OTACONFIG_READER_PATH;

    @Option(name = "otaconfig-read-timeout", description = "max time for otaconfigreader to run",
            isTimeVal = true)
    private long mOtaConfigTimeout = 5 * 60 * 1000;

    @Option(name = "incremental", description = "whether or not to use an incremental update")
    private boolean mIncremental = false;

    private static final String DEFAULT_OTACONFIG_READER_PATH =
            "/google/data/ro/teams/android-test/tools/otatools/otaconfigreader";

    // Exposed for testing
    IncrementalOtaLaunchControlProvider mIncrementalProvider =
            new IncrementalOtaLaunchControlProvider();

    // Exposed for testing
    IRunUtil mRunUtil = RunUtil.getDefault();

    @Override
    public RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
        String deviceType = getBuildFlavor().split("-")[0];
        String[] cmd = {
                mOtaConfigPath,
                "--device", deviceType,
                "--branch", getBranch(),
                "--group", mOtaGroup
            };
        CommandResult res = mRunUtil.runTimedCmd(mOtaConfigTimeout, cmd);
        if (res.getStatus() != CommandStatus.SUCCESS) {
            CLog.e(res.getStderr());
            throw new BuildRetrievalError("otaconfigreader failed");
        }
        String intendedSource = res.getStdout().trim();
        // FIXME: Workaround for b/35357078
        String[] lns = intendedSource.split("\n");
        intendedSource = lns[lns.length - 1].trim();
        CLog.i("Build ID from otaconfigreader: %s", intendedSource);
        setOtaBuildId(intendedSource);
        if (mIncremental) {
            mIncrementalProvider.setUseBigStore(shouldUseBigStore());
            mIncrementalProvider.setBuildId(getBuildId());
            mIncrementalProvider.setBranch(getBranch());
            mIncrementalProvider.setBuildOs(getBuildOs());
            mIncrementalProvider.setBuildFlavor(getBuildFlavor());
            mIncrementalProvider.setTestTag(getTestTag());
            mIncrementalProvider.setOtaBuildAsSource(useOtaBuildAsSource());
            mIncrementalProvider.setSwap(useOtaBuildAsSource());
            mIncrementalProvider.setOtaBuildId(intendedSource);
            mIncrementalProvider.setAllowDowngrade(allowDowngrade());
            mIncrementalProvider.setSkipTests(skipTests());
            mIncrementalProvider.setReportTargetBuild(reportTargetBuild());
            return mIncrementalProvider.queryForBuild();
        }
        return super.queryForBuild();
    }

    @Override
    public IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
    String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        OtaDeviceBuildInfo otaBuildInfo;
        if (mIncremental) {
            otaBuildInfo = (OtaDeviceBuildInfo) mIncrementalProvider.downloadBuildFiles(
                    remoteBuild, testTargetName, buildName, downloader);
        } else {
            // This BuildProvider can supply its own full package
            otaBuildInfo = (OtaDeviceBuildInfo)
                    super.downloadBuildFiles(remoteBuild, testTargetName, buildName, downloader);
        }
        return otaBuildInfo;
    }

    /**
     * Exposed for testing.
     */
    void setIncremental(boolean incremental) {
        mIncremental = incremental;
    }
}
