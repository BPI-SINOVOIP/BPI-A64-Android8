// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;

/**
 * This is a workaround to override the target branch and flavor
 * for 2 different flavor devices.
 */
@OptionClass(alias = "rdb-side-by-side")
public class RdbSideBySideResultReporter extends AbstractRdbResultReporter {
    @Option(name = "branch",
            description = "Test branch which the rdb post to", mandatory = true)
    private String mBranch = null;

    @Option(name = "flavor",
            description = "Build flavor which the rdb post to", mandatory = true)
    private String mFlavor = null;

    @Option(name = "build-id",
            description = "Build id which the rdb post to")
    private String mBuildId = null;

    /**
     * Post the metrics for each test run in this invocation to the release dB.
     *
     * @param buildInfo the meta data for the build under test
     */
    @Override
    protected void postData(IBuildInfo buildInfo) {
        if (mBranch != null){
           buildInfo.setBuildBranch(mBranch);
        }
        if  (mFlavor !=null){
            buildInfo.setBuildFlavor(mFlavor);
        }
        for (TestRunResult runResults : getRunResults()) {
            if (runResults.getRunMetrics().size() > 0) {
                if (mBuildId == null) {
                    postResults(buildInfo, runResults.getName(), runResults.getRunMetrics());
                } else {
                    // Special case to handle posting the results to side by side dashboard
                    // Build id, branch and flavor are faked so that both the baseline
                    // and new build results can be viewed side by side
                    BuildInfo baselineBuildInfo = new BuildInfo(mBuildId,
                            buildInfo.getBuildTargetName());
                    baselineBuildInfo.setTestTag(buildInfo.getTestTag());
                    baselineBuildInfo.setBuildBranch(mBranch);
                    baselineBuildInfo.setBuildFlavor(mFlavor);
                    postResults(
                            baselineBuildInfo, runResults.getName(), runResults.getRunMetrics());
                }
            }
        }
    }
}
