// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.google.android.tradefed.build.LaunchControlProvider;
import com.google.android.tradefed.build.RemoteBuildInfo;

/**
 * A {@link ITargetPreparer} that resets an existing test run status on launch control. It sends a
 * reset test cl request to launch control on the specified test tag, branch, build id combination.
 */
@OptionClass(alias = "lc-test-cl-reset")
public class LaunchControlTestClResetter implements ITargetPreparer {

    @Option(name = "disable", description = "disables the preparer regardless of other options"
            + " when set to true")
    private boolean mDisable = true;

    @Option(name = "reset-test-tag", description = "unique identifier of test to provide to launch "
            + "control for reset. If unspecified, current test tag will be used.")
    private String mResetTestTag = null;

    @Option(name = "reset-branch", description = "the branch of the existing test run to reset")
    private String mResetBranch = null;

    @Option(name = "reset-build-id", description = "the build id of the existing test run to reset")
    private String mResetBuildId = null;

    @Option(name = "reset-build-flavor", description = "the build flavor of the existing test run "
            + "to reset. when left unset, build flavor will be the same as what's currently being "
            + "tested")
    private String mResetBuildFlavor = null;
    /*
     * options below are intentionally named to be the same as LaunchControlProvider
     * */
    @Option(name = "test-tag-suffix", description = "suffix for test-tag.")
    // appended to test-tag to query some variants of one test.
    private String mTestTagSuffix = null;

    @Option(name = "protocol", description = "protocol for launch control requests. (http/https)")
    private String mLcProtocol = "http";

    @Option(name = "hostname", description = "host name for launch control requests")
    private String mLcHostname = "android-build";

    @Option(name = "use-sso-client", description = "whether or not we should query LC with " +
            "sso_client.")
    private boolean mUseSsoClient = false;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        if (mDisable) {
            CLog.d("preparer disabled");
            return;
        }
        if (mResetTestTag == null) {
            mResetTestTag = buildInfo.getTestTag();
        }
        if (mTestTagSuffix != null) {
            mResetTestTag = String.format("%s-%s", mResetTestTag, mTestTagSuffix);
        }
        LaunchControlProvider lcp = new LaunchControlProvider() {
            @Override
            public void cleanUp(IBuildInfo info) {
                throw new RuntimeException("should not get called");
            }
            @Override
            protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild,
                    String testTargetName, String buildName, IFileDownloader downloader)
                            throws BuildRetrievalError {
                throw new RuntimeException("should not get called");
            }
        };
        lcp.setTestTag(mResetTestTag);

        if (mResetBranch == null) {
            throw new TargetSetupError("must specify reset-branch", device.getDeviceDescriptor());
        }
        lcp.setBranch(mResetBranch);

        if (mResetBuildId == null) {
            throw new TargetSetupError("must specify reset-build-id", device.getDeviceDescriptor());
        }
        lcp.setBuildId(mResetBuildId);

        if (mResetBuildFlavor == null) {
            mResetBuildFlavor = buildInfo.getBuildFlavor();
        }
        lcp.setBuildFlavor(mResetBuildFlavor);

        lcp.setLcHostname(mLcHostname);
        lcp.setLcProtocol(mLcProtocol);
        lcp.setUseSsoClient(mUseSsoClient);
        lcp.resetTestBuild();
    }

}
