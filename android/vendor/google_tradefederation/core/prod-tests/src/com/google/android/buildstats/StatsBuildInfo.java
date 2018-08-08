// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.buildstats;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import java.io.File;

/**
 * Implementation of a {@link IStatsBuildInfo}
 */
public class StatsBuildInfo extends BuildInfo implements IStatsBuildInfo {

    private File mSystemRoot;

    /**
     * Creates a {@link StatsBuildInfo} using default attribute values.
     */
    public StatsBuildInfo() {
    }

    /**
     * Creates a {@link StatsBuildInfo}
     *
     * @param buildId
     * @param buildName
     */
    public StatsBuildInfo(String buildId, String buildName) {
        super(buildId, buildName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp() {
        // Clean up extracted files.
        if (mSystemRoot != null) {
            CLog.v("Deleting zip dir: "+ mSystemRoot.toString());
            FileUtil.recursiveDelete(mSystemRoot);
        }
    }

    @Override
    public IBuildInfo clone() {
        StatsBuildInfo cloneBuild = new StatsBuildInfo(getBuildId(), getBuildTargetName());
        cloneBuild.addAllBuildAttributes(this);
        cloneBuild.setSystemRoot(getSystemRoot());
        return cloneBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getSystemRoot() {
        return mSystemRoot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSystemRoot(File file) {
        mSystemRoot = file;
    }
}
