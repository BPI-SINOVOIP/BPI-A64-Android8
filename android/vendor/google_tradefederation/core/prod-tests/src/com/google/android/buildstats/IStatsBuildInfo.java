// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.buildstats;

import com.android.tradefed.build.IBuildInfo;

import java.io.File;

/**
 *  A {@link IBuildInfo} that contains build statistics.
 */
public interface IStatsBuildInfo extends IBuildInfo {

    /**
     * Helper method to retrieve the root directory of the file system contents.
     * @return the root directory or <code>null</code> if not found
     */
    public File getSystemRoot();

    /**
     * Stores the root directory of the file system in this build info
     *
     * @param file the local image {@link File}
     */
    public void setSystemRoot(File file);


}
