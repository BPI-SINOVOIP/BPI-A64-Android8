// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;

/**
 * A {@link LaunchControlProvider} that will throw exception if asked to download files
 */
public class StubLaunchControlProvider extends LaunchControlProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        // ignore
    }
}
