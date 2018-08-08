// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.util;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Constructs a {@link com.android.tradefed.build.BuildInfo} with temp files.
 */
public class BuildInfoBuilder {

    private IBuildInfo mBuildInfo = new BuildInfo();
    private Collection<File> mFilesToClean = new ArrayList<>();

    public BuildInfoBuilder withLogContents(String logContents) {
        return withLogContents("test", "log", logContents);
    }

    public BuildInfoBuilder withLogContents(String prefix, String suffix, String logContents) {
        try {
            File tempFile = FileUtil.createTempFile(prefix, suffix);
            mFilesToClean.add(tempFile);
            FileUtil.writeToFile(logContents, tempFile);
            mBuildInfo.setFile(tempFile.getName(), tempFile, "1");
        } catch (IOException e) {
            CLog.e(e);
            throw new RuntimeException("Failed to create test data: ", e);
        }
        return this;
    }

    public BuildInfoBuilder withLogFile(String name, File file, String version) {
        mBuildInfo.setFile(name, file, version);
        return this;
    }

    public IBuildInfo build() {
        return mBuildInfo;
    }

    public void cleanUp() {
        mFilesToClean.forEach(FileUtil::deleteFile);
        mBuildInfo.cleanUp();
    }
}
