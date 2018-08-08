// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.sdk;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.SdkBuildInfo;
import com.google.android.tradefed.build.RemoteBuildInfo;
import com.google.android.tradefed.build.SdkLaunchControlProvider;

/**
 * A {@link SdkLaunchControlProvider} that skips the download of files.
 */
public class EmptySdkLaunchControlProvider extends SdkLaunchControlProvider {

    @Override
    protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        SdkBuildInfo build = new SdkBuildInfo(remoteBuild.getBuildId(), buildName);
        build.setTestTag(testTargetName);
        return build;
    }
}
