// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;

import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Functional tests for {@link TfLaunchControlProvider}.
 * <p/>
 * Perform real launch control queries for a known branch, and verifies expected results.
 */
public class TfLaunchControlProviderFuncTest extends TestCase {

    private static final String TEST_TAG = "test";
    private static final String BUILD_FLAVOR = "test_suites_x86_64_fastbuild3d_linux";
    private static final String BUILD_ID = "3592382";
    private static final String BRANCH = "git_master";

    private TfLaunchControlProvider mTfLaunchControlProvider;
    private Collection<String> mBuildsToReset;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mBuildsToReset = new ArrayList<String>();

        mTfLaunchControlProvider = new TfLaunchControlProvider();

        mTfLaunchControlProvider.setBranch(BRANCH);
        mTfLaunchControlProvider.setBuildFlavor(BUILD_FLAVOR);
        mTfLaunchControlProvider.setBuildId(BUILD_ID);
        mTfLaunchControlProvider.setTestTag(TEST_TAG);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        for (String buildId : mBuildsToReset) {
            mTfLaunchControlProvider.resetTestBuild(buildId);
        }
    }

    /**
     * Functional test for {@link TfLaunchControlProvider#queryForBuild()}.
     */
    public void testQueryBuild_tf() throws BuildRetrievalError {
        RemoteBuildInfo info = mTfLaunchControlProvider.queryForBuild();
        assertNotNull(info);
        assertNotNull(info.getAttribute(BuildAttributeKey.TF));
    }

    /**
     * Functional test for {@link TfLaunchControlProvider#getBuild()}.
     */
    public void testGetBuild_tf() throws BuildRetrievalError {
        IBuildInfo info = mTfLaunchControlProvider.getBuild();
        assertNotNull(info);
        mBuildsToReset.add(info.getBuildId());
        assertTrue(info instanceof IFolderBuildInfo);
        IFolderBuildInfo tfBuild = (IFolderBuildInfo)info;
        try {
            assertNotNull(tfBuild.getRootDir());
            assertEquals(BUILD_ID, tfBuild.getBuildId());
        } finally {
            tfBuild.cleanUp();
        }
    }
}
