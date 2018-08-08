// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.net.IHttpHelper;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Functional tests for {@link LaunchControlProvider}.
 * <p/>
 * Perform real launch control queries for a known good build, and verifies expected results.
 */
public class DeviceLaunchControlProviderFuncTest extends TestCase {

    // constants for a known good build on launch control
    private static final String EXPECTED_LATEST_BUILD_ID = "3307497";
    private static final String EXPECTED_BUILD_ALIAS = "NRD91L";
    private static final String TEST_BRANCH = "git_nyc-release";
    private static final String TEST_FLAVOR = "bullhead-userdebug";
    private static final String TEST_TAG = "test";

    private static final String TEST_DATA_ROOT = "/google/data/ro/teams/tradefed-test/";
    static final String IMG_FILE_1 = "bullhead-img-3307497.zip";

    private DeviceLaunchControlProvider mLaunchControlProvider;
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
        // Fake return an actual valid zip so that parsing succeed
        EasyMock.expect(mMockDownloader.downloadFile((String)EasyMock.anyObject())).
                andReturn(new File(TEST_DATA_ROOT + IMG_FILE_1)).anyTimes();
        EasyMock.replay(mMockDownloader);
        mLaunchControlProvider = new DeviceLaunchControlProvider() {
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

            @Override
            File extractZip(File zipFile) {
                return new File("tmp");
            }
        };

        mLaunchControlProvider.setBranch(TEST_BRANCH);
        mLaunchControlProvider.setBuildFlavor(TEST_FLAVOR);
        mLaunchControlProvider.setTestTag(TEST_TAG);
        try {
            GlobalConfiguration.createGlobalConfiguration(new String[] {"empty"});
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        for (String buildId : mBuildsToReset) {
            mLaunchControlProvider.resetTestBuild(buildId);
        }
    }

    /**
     * Functional test for {@link LaunchControlProvider#getBuild()} with a
     * {@link QueryType#NOTIFY_TEST_BUILD} query.
     */
    public void testGetBuild_NotifyTest() throws BuildRetrievalError {
        String testTag = "notify_func_test";
        mLaunchControlProvider.setTestTag(testTag);
        mLaunchControlProvider.setQueryType(QueryType.NOTIFY_TEST_BUILD);
        mLaunchControlProvider.setBuildId(EXPECTED_LATEST_BUILD_ID);
        IBuildInfo info = mLaunchControlProvider.getBuild();
        assertNotNull("Did not receive build info from notify test.", info);
        mBuildsToReset.add(info.getBuildId());
        assertEquals(EXPECTED_LATEST_BUILD_ID, info.getBuildId());
        assertEquals(EXPECTED_BUILD_ALIAS, info.getBuildAttributes().get("build_alias"));
    }

    /**
     * Functional test for {@link LaunchControlProvider#getBuild()} with a
     * {@link QueryType#QUERY_LATEST_BUILD} query.
     * <p/>
     * Assumes {@link #EXPECTED_LATEST_BUILD_ID} is the latest build on {@link #TEST_BRANCH}
     */
    public void testGetBuild_QueryLatest() throws BuildRetrievalError {
        mLaunchControlProvider.setQueryType(QueryType.QUERY_LATEST_BUILD);
        mLaunchControlProvider.setBuildId(EXPECTED_LATEST_BUILD_ID);
        IBuildInfo info = mLaunchControlProvider.getBuild();
        assertNotNull(info);
        mBuildsToReset.add(info.getBuildId());
        assertEquals(EXPECTED_LATEST_BUILD_ID, info.getBuildId());
        assertEquals(EXPECTED_BUILD_ALIAS, info.getBuildAttributes().get("build_alias"));
    }

    /**
     * Functional test for {@link LaunchControlProvider#getBuild()} with a
     * {@link QueryType#LATEST_GREEN_BUILD} query.
     * <p/>
     * Assumes {@link #EXPECTED_LATEST_BUILD_ID} is the latest build on {@link #TEST_BRANCH}
     */
    public void testGetBuild_LatestGreen() throws BuildRetrievalError {
        String testTag = "latest_green_func_test";
        mLaunchControlProvider.setTestTag(testTag);
        // First make sure the build is clean
        mLaunchControlProvider.setQueryType(QueryType.QUERY_LATEST_BUILD);
        RemoteBuildInfo rInfo = mLaunchControlProvider.getRemoteBuild();
        assertNotNull("Failed to query latest build to setup the test", rInfo);
        mLaunchControlProvider.resetTestBuild(rInfo.getBuildId());
        // Allow some time to the reset to take effect.
        RunUtil.getDefault().sleep(2000);
        // Do actual request
        mLaunchControlProvider.setQueryType(QueryType.LATEST_GREEN_BUILD);
        IBuildInfo info = mLaunchControlProvider.getBuild();
        assertNotNull("Did not get the build info the first time.", info);
        mBuildsToReset.add(info.getBuildId());
        long firstBuildId = Long.parseLong(info.getBuildId());
        assertEquals(7, info.getBuildId().length());
        assertTrue(info.getBuildAttributes().get("build_alias").startsWith("NR"));

        // now do request again, expect immediate previous build
        info = mLaunchControlProvider.getBuild();
        assertNotNull("Did not receive buildInfo from provider.", info);
        mBuildsToReset.add(info.getBuildId());
        long previousBuildId = Long.parseLong(info.getBuildId());
        // We cannot do strong checking because latest green build change constantly.
        assertTrue(previousBuildId < firstBuildId);
    }

    /**
     * Functional test for {@link DeviceLaunchControlProvider#getBuild(ITestDevice)} with option
     * set to boot strap information from device
     * <p/>
     * Assumes {@link #EXPECTED_LATEST_BUILD_ID} is the latest build on {@link #TEST_BRANCH}
     * @throws DeviceNotAvailableException
     */
    public void testGetBuild_BootstrapBuildInfo_NotifyTestCl()
            throws BuildRetrievalError, DeviceNotAvailableException {
        final String expectedBuildId = "3307497";
        final String expectedBuildAlias = "NRD91L";
        ITestDevice device = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(device.getBuildId()).andReturn(expectedBuildId);
        EasyMock.expect(device.getBuildFlavor()).andReturn(null);
        EasyMock.replay(device);
        mLaunchControlProvider.setBootstrapBuildInfo(true);
        IBuildInfo info = mLaunchControlProvider.getBuild(device);
        assertNotNull(info);
        mBuildsToReset.add(info.getBuildId());
        assertEquals(expectedBuildId, info.getBuildId());
        assertEquals(expectedBuildAlias, info.getBuildAttributes().get("build_alias"));
        EasyMock.verify(device);
    }
}
