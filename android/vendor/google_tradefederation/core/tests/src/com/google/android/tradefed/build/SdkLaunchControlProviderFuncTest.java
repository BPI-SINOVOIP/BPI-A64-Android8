// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.ISdkBuildInfo;
import com.android.tradefed.util.net.HttpHelper;
import com.android.tradefed.util.net.IHttpHelper;

import junit.framework.TestCase;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Functional tests for {@link SdkLaunchControlProvider}.
 * <p/>
 * Perform real launch control queries for a known branch, and verifies expected results.
 */
public class SdkLaunchControlProviderFuncTest extends TestCase {

    static final String TEST_TAG = "test";
    static final String SDK_BRANCH = "git_eclair-release";
    static final String SDK_OS = "linux";
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

        mLaunchControlProvider = new SdkLaunchControlProvider() {
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

        mLaunchControlProvider.setBranch(SDK_BRANCH);
        mLaunchControlProvider.setTestTag(TEST_TAG);
        mLaunchControlProvider.setBuildOs(SDK_OS);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        for (String buildId : mBuildsToReset) {
            mLaunchControlProvider.resetTestBuild(buildId);
        }
    }

    /**
     * Functional test for {@link SdkLaunchControlProvider#getBuild()}.
     */
    public void testGetBuild_sdk() throws BuildRetrievalError {
        mLaunchControlProvider.setQueryType(QueryType.LATEST_GREEN_BUILD);
        IBuildInfo info = mLaunchControlProvider.getBuild();
        assertNotNull(info);
        mBuildsToReset.add(info.getBuildId());
        assertTrue(info instanceof ISdkBuildInfo);
        ISdkBuildInfo sdkBuild = (ISdkBuildInfo)info;
        try {
            assertNotNull(sdkBuild.getTestsDir());
            assertTrue(sdkBuild.getTestsDir().exists());
            assertEquals(EXPECTED_BUILD_ID, sdkBuild.getBuildId());
            assertNotNull(sdkBuild.getSdkDir());
            assertTrue(sdkBuild.getSdkDir().exists());
            assertTrue(new File(sdkBuild.getSdkDir(), "tools").exists());
        } finally {
            sdkBuild.cleanUp();
        }
    }
}
