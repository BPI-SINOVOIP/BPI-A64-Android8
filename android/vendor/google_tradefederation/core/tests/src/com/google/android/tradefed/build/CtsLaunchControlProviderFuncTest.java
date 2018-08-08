// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.util.net.HttpHelper;
import com.android.tradefed.util.net.IHttpHelper;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Functional tests for {@link CtsLaunchControlProvider}.
 * <p/>
 * Perform real launch control queries for a known branch, and verifies expected results.
 */
public class CtsLaunchControlProviderFuncTest extends TestCase {

    static final String TEST_TAG = "test";
    static final String CTS_BRANCH = "git_eclair-release";
    // CTS was built on mac in eclair
    static final String CTS_OS = "mac";
    static final String EXPECTED_BUILD_ID = "30198";

    private LaunchControlProvider mLaunchControlProvider;
    private Collection<String> mBuildsToReset;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mBuildsToReset = new ArrayList<String>();

        mLaunchControlProvider = new CtsLaunchControlProvider() {
            @Override
            protected IHttpHelper createHttpHelper() {
                IHttpHelper h = new HttpHelper();
                // reduce retry times to less intensive values
                h.setOpTimeout(10*1000);
                h.setInitialPollInterval(1*1000);
                h.setMaxPollInterval(5*1000);
                return h;
            }
        };

        mLaunchControlProvider.setBranch(CTS_BRANCH);
        mLaunchControlProvider.setTestTag(TEST_TAG);
        mLaunchControlProvider.setBuildOs(CTS_OS);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        for (String buildId : mBuildsToReset) {
            mLaunchControlProvider.resetTestBuild(buildId);
        }
    }

    /**
     * Functional test for {@link CtsLaunchControlProvider#getBuild()} when querying a cts branch
     * branch.
     */
    public void testGetBuild_cts() throws BuildRetrievalError {
        mLaunchControlProvider.setQueryType(QueryType.LATEST_GREEN_BUILD);
        IBuildInfo info = mLaunchControlProvider.getBuild();
        assertNotNull(info);
        mBuildsToReset.add(info.getBuildId());
        assertTrue(info instanceof IFolderBuildInfo);
        IFolderBuildInfo ctsBuild = (IFolderBuildInfo)info;
        try {
            assertNotNull(ctsBuild.getRootDir());
            assertEquals(EXPECTED_BUILD_ID, ctsBuild.getBuildId());
            // do sanity check that cts zip was extracted
            assertTrue(ctsBuild.getRootDir().exists());
        } finally {
            ctsBuild.cleanUp();
        }
    }
}
