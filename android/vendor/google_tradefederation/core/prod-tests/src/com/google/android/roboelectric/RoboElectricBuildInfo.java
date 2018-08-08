// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.roboelectric;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.FolderBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;

/**
 * A {@link IBuildInfo} for roboelectric tests.
 * <p/>
 * In addition to the usual build metadata, stores the following types of host JVM jars all in the
 * same folder:
 * <ul>
 * <li>a test target jar, containing the code under test
 * <li>a test suite jar, containing the tests themselves
 * <li>set of jars used to emulate the android platform (eg android.jar, etc)
 */
public class RoboElectricBuildInfo extends FolderBuildInfo {

    private File mTestTargetFile;
    private Collection<File> mTestSuiteFiles = new LinkedList<File>();

    /**
     * Creates a {@link RoboElectricBuildInfo}
     *
     * @param buildId
     * @param buildName
     */
    public RoboElectricBuildInfo(String buildId, String buildName) {
        super(buildId, buildName);
    }

    /**
     * Sets the file name of the test target jar.
     *
     * @throws BuildRetrievalError if file with that name does not exist in root dir
     */
    public void setTestTargetFileName(String fileName) throws BuildRetrievalError {
        if (getRootDir() == null) {
            throw new IllegalStateException("root dir is not set");
        }
        setTestTargetFile(new File(getRootDir(), fileName));
    }

    void setTestTargetFile(File testTarget) throws BuildRetrievalError {
        mTestTargetFile = testTarget;
        if (!mTestTargetFile.exists()) {
            throw new BuildRetrievalError(String.format("Could not find %s in build folder",
                    testTarget.getAbsolutePath()));
        }
    }

    /**
     * Adds a test suite jar to build by file name
     *
     * @throws BuildRetrievalError if file with that name does not exist in root dir
     */
    public void addTestSuiteFileName(String fileName) throws BuildRetrievalError {
        if (getRootDir() == null) {
            throw new IllegalStateException("root dir is not set");
        }
        addTestSuiteFile(new File(getRootDir(), fileName));
    }

    public File getTestTargetFile() {
        return mTestTargetFile;
    }

    public Collection<File> getTestSuiteFiles() {
        return mTestSuiteFiles;
    }

    /**
     * Adds a test suite jar to the build.
     *
     * @throws BuildRetrievalError if file does not exist
     */
    void addTestSuiteFile(File destFile) throws BuildRetrievalError {
        if (!destFile.exists()) {
            throw new BuildRetrievalError(String.format("Could not find %s",
                    destFile.getAbsolutePath()));
        }
        mTestSuiteFiles.add(destFile);
    }

    @Override
    public void cleanUp() {
        super.cleanUp();
        FileUtil.deleteFile(mTestTargetFile);
        for (File f : mTestSuiteFiles) {
            FileUtil.deleteFile(f);
        }
    }
}
