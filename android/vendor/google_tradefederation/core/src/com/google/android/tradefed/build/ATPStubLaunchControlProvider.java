// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.config.Option;

/**
 * A stub {@link LaunchControlProvider} used for ATP integration testing.
 */
public class ATPStubLaunchControlProvider extends LaunchControlProvider {

    @Option(
        name = "throw-build-error",
        description = "force the stub provider to throw a BuildRetrievalError. Used for testing."
    )
    private boolean mThrowError = false;

    /**
     * {@inheritDoc}
     */
    @Override
    protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        if (mThrowError) {
            throw new BuildRetrievalError("stub failed to get build.");
        }
        IBuildInfo stubBuild = new BuildInfo(remoteBuild.getBuildId(), buildName);
        stubBuild.setTestTag(testTargetName);
        return stubBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        // ignore
    }
}
