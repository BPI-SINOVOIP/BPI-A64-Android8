// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.IKernelBuildInfo;
import com.android.tradefed.build.KernelDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

/**
 * A {@link IBuildProvider} implementation that queries the kernel build service for a kernel build
 * and the Google launch control servers for a device build to test.
 */
@OptionClass(alias = "kernel-device-launch-control")
public class KernelDeviceLaunchControlProvider extends DeviceLaunchControlProvider {
    KernelBuildProvider mKernelBuildProvider = null;

    private static final String KERNEL_BRANCH_NAME = "kernel-branch";
    @Option(name = KERNEL_BRANCH_NAME, description = "the kernel branch to test",
            importance = Importance.IF_UNSET)
    private String mKernelBranch = null;

    @Option(name = "kernel-build-id", description = "the kernel build id to test. If unspecified "
            + "will get latest untested build")
    private String mKernelBuildId = null;

    @Option(name = "min-kernel-build-id", description = "the minimum kernel build id to test")
    private String mMinKernelBuildId = null;

    private static final String KERNEL_HOSTNAME = "kernel-hostname";
    @Option(name = KERNEL_HOSTNAME, description = "the host of the kernel build server")
    private String mKernelHostName = "http://vpbs1.mtv.corp.google.com:8080";

    /**
     * Constructor for {@link KernelDeviceLaunchControlProvider}.
     */
    public KernelDeviceLaunchControlProvider() {
        mKernelBuildProvider = createKernelBuildProvider();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        RemoteBuildInfo deviceRemoteBuild = getRemoteBuild();
        if (deviceRemoteBuild == null) {
            return null;
        }

        mKernelBuildProvider.setKernelBranch(mKernelBranch);
        mKernelBuildProvider.setKernelBuildId(mKernelBuildId);
        mKernelBuildProvider.setMinKernelBuildId(mMinKernelBuildId);
        mKernelBuildProvider.setKernelHostName(mKernelHostName);
        mKernelBuildProvider.setTestTag(getTestTag());
        mKernelBuildProvider.setBuildFlavor(getBuildFlavor());
        mKernelBuildProvider.setBuildId(deviceRemoteBuild.getBuildId());
        mKernelBuildProvider.setUseBigStore(shouldUseBigStore());

        addDownloadKey(BuildAttributeKey.MKBOOTIMG);
        addDownloadKey(BuildAttributeKey.RAMDISK);

        IKernelBuildInfo kernelBuild = (IKernelBuildInfo) mKernelBuildProvider.getBuild();
        if (kernelBuild == null) {
            resetTestBuild(deviceRemoteBuild.getBuildId());
            return null;
        }

        IDeviceBuildInfo deviceBuild = null;
        try {
            deviceBuild = (IDeviceBuildInfo) fetchRemoteBuild(deviceRemoteBuild);
        } catch (BuildRetrievalError e) {
            resetTestBuild(deviceRemoteBuild.getBuildId());
            mKernelBuildProvider.buildNotTested(kernelBuild);
            mKernelBuildProvider.cleanUp(kernelBuild);
            throw e;
        }
        if (deviceBuild == null) {
            resetTestBuild(deviceRemoteBuild.getBuildId());
            mKernelBuildProvider.buildNotTested(kernelBuild);
            mKernelBuildProvider.cleanUp(kernelBuild);
            return null;
        }

        String buildId = String.format("%s_%s", kernelBuild.getBuildId(), deviceBuild.getBuildId());
        String buildName = String.format("%s-%s", kernelBuild.getBuildBranch(),
                deviceBuild.getBuildTargetName());
        KernelDeviceBuildInfo localBuild = new KernelDeviceBuildInfo(buildId, buildName);
        localBuild.setTestTag(getTestTag());
        localBuild.setBuildFlavor(getBuildFlavor());
        localBuild.setBuildBranch(getKernelBranch());
        localBuild.setDeviceBuild(deviceBuild);
        localBuild.setKernelBuild(kernelBuild);
        return localBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void buildNotTested(IBuildInfo info) {
        super.buildNotTested(info);
        mKernelBuildProvider.buildNotTested(info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        super.cleanUp(info);
        mKernelBuildProvider.cleanUp(info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestTag(String tag) {
        super.setTestTag(tag);
        mKernelBuildProvider.setTestTag(tag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuildId(String buildId) {
        super.setBuildId(buildId);
        mKernelBuildProvider.setBuildId(buildId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuildFlavor(String flavor) {
        super.setBuildFlavor(flavor);
        mKernelBuildProvider.setBuildFlavor(flavor);
    }

    /**
     * Get the kernel branch.
     */
    public String getKernelBranch() {
        return mKernelBuildProvider.getKernelBranch();
    }

    /**
     * Set the kernel branch.
     */
    public void setKernelBranch(String kernelBranch) {
        mKernelBranch = kernelBranch;
        mKernelBuildProvider.setKernelBranch(kernelBranch);
    }

    /**
     * Get the kernel build id (git sha1) to test.
     */
    public String getKernelBuildId() {
        return mKernelBuildProvider.getKernelBuildId();
    }

    /**
     * Set the kernel build id (git sha1) to test.
     */
    public void setKernelBuildId(String buildId) {
        mKernelBuildId = buildId;
        mKernelBuildProvider.setKernelBuildId(buildId);
    }

    /**
     * Get the minimum kernel build id (git sha1) to test.
     */
    public String getMinKernelBuildId() {
        return mKernelBuildProvider.getMinKernelBuildId();
    }

    /**
     * Set the minimum kernel build id (git sha1) to test.
     */
    public void setMinKernelBuildId(String buildId) {
        mMinKernelBuildId = buildId;
        mKernelBuildProvider.setMinKernelBuildId(buildId);
    }

    /**
     * Get the kernel build service host name.
     */
    public String getKernelHostName() {
        return mKernelBuildProvider.getKernelHostName();
    }

    /**
     * Set the kernel build service host name.
     */
    public void setKernelHostName(String hostName) {
        mKernelHostName = hostName;
        mKernelBuildProvider.setKernelHostName(hostName);
    }

    /**
     * Create at {@link KernelBuildProvider}. Exposed for unit testing.
     */
    KernelBuildProvider createKernelBuildProvider() {
        return new KernelBuildProvider();
    }
}
