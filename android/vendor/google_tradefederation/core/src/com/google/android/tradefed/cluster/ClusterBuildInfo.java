// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.android.tradefed.build.FolderBuildInfo;
import com.android.tradefed.build.IBuildInfo;

import java.io.File;

/** A {@link IBuildInfo} class for builds piped from TFC. */
public class ClusterBuildInfo extends FolderBuildInfo {

    public ClusterBuildInfo(final File rootDir) {
        super(null, null);
        setRootDir(rootDir);
    }
}
