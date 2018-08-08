// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.AppBuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IAppBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.util.net.IHttpHelper;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Functional tests for {@link AppLaunchControlProvider}.
 * <p/>
 * Perform real launch control queries for a known branch, and verifies expected results.
 */
public class AppLaunchControlProviderFuncTest extends TestCase {

    private static final String TEST_TAG = "test";
    private static final String UNBUNDLED_BRANCH = "ub-supportlib-master";
    private static final String UNBUNDLED_FLAVOR = "support_library";

    private LaunchControlProvider mLaunchControlProvider;
    private Collection<String> mBuildsToReset;
    private IFileDownloader mMockDownloader;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mBuildsToReset = new ArrayList<String>();
        mMockDownloader = EasyMock.createNiceMock(IFileDownloader.class);
        EasyMock.expect(mMockDownloader.downloadFile((String)EasyMock.anyObject())).
                andReturn(new File("tmp")).anyTimes();
        EasyMock.replay(mMockDownloader);
        mLaunchControlProvider = new AppLaunchControlProvider() {
            @Override
            protected IHttpHelper createHttpHelper() {
                IHttpHelper h = super.createHttpHelper();
                // reduce retry times to less intensive values
                h.setOpTimeout(10 * 1000);
                h.setInitialPollInterval(1 * 1000);
                h.setMaxPollInterval(5 * 1000);
                return h;
            }

            @Override
            protected IFileDownloader createLaunchControlDownloader() {
                return mMockDownloader;
            }
        };

        mLaunchControlProvider.setBranch(UNBUNDLED_BRANCH);
        mLaunchControlProvider.setBuildFlavor(UNBUNDLED_FLAVOR);
        mLaunchControlProvider.setTestTag(TEST_TAG);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        for (String buildId : mBuildsToReset) {
            mLaunchControlProvider.resetTestBuild(buildId);
        }
    }

    /**
     * Functional test for {@link LaunchControlProvider#getBuild()} when querying an unbundled
     * branch
     */
    public void testGetBuild_unbundled() throws BuildRetrievalError {
        mLaunchControlProvider.setQueryType(QueryType.QUERY_LATEST_BUILD);
        IBuildInfo info = mLaunchControlProvider.getBuild();
        assertNotNull(info);
        mBuildsToReset.add(info.getBuildId());
        assertTrue(info instanceof AppBuildInfo);
        IAppBuildInfo appBuild = (IAppBuildInfo)info;
        assertTrue(appBuild.getAppPackageFiles().size() > 0);
    }
}
