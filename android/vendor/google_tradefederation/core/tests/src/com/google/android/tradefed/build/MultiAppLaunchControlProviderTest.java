// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.net.IHttpHelper;
import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;

/**
 * Unit tests for {@link MultiAppLaunchControlProvider}.
 */
public class MultiAppLaunchControlProviderTest extends TestCase {

    private static final int BUILD_ID = 4;
    private static final String APP_PATH = "somepath/app.apk";
    private static final String FILES_PATH = "somepath/file.txt";
    private static final int SECONDARY_BUILD_ID = 3;
    private static final String SECONDARY_APP_PATH = "otherpath/app_two.apk";
    private static final String SECONDARY_FILES_PATH = "otherpath/two.txt";
    private static final String APP_QUERY_RESPONSE = String.format("bid:%d\n%s:%s\n%s:%s",
            BUILD_ID,
            BuildAttributeKey.APP_APKS.getRemoteValue(), APP_PATH,
            BuildAttributeKey.FILES.getRemoteValue(), FILES_PATH);
    private static final String SECONDARY_APP_QUERY_RESPONSE = String.format(
            "bid:%d\n%s:%s\n%s:%s",
            SECONDARY_BUILD_ID,
            BuildAttributeKey.APP_APKS.getRemoteValue(), SECONDARY_APP_PATH,
            BuildAttributeKey.FILES.getRemoteValue(), SECONDARY_FILES_PATH);
    private static final String TEST_TAG = "tag";
    private static final QueryType TEST_QUERY = QueryType.LATEST_GREEN_BUILD;
    private static final String APP_BRANCH = "primary_ub_branch";
    private static final String APP_BUILD_FLAVOR = "primary_flav";
    private static final String SECONDARY_APP_BRANCH = "secondary_ub_branch";
    private static final String SECONDARY_APP_BUILD_FLAVOR = "secondary_flav";

    private static final String NO_BUILD_QUERY_RESPONSE = "";
    private MultiAppLaunchControlProvider mMultiAppLaunchControlProvider;
    private IHttpHelper mMockHttpHelper;
    private IFileDownloader mMockDownloader;
    private File mMockFile;
    private File mLcCacheDir;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockHttpHelper = EasyMock.createMock(IHttpHelper.class);
        mMockDownloader = EasyMock.createNiceMock(IFileDownloader.class);
        mMultiAppLaunchControlProvider = new MultiAppLaunchControlProvider() {
            @Override
            protected IHttpHelper createHttpHelper() {
                return mMockHttpHelper;
            }

            @Override
            protected IFileDownloader createLaunchControlDownloader() {
                return mMockDownloader;
            }

            @Override
            String getUrl() {
                return "fakeUrl";
            }

            @Override
            AppLaunchControlProvider createAppLcProvider() {
                return new AppLaunchControlProvider() {
                    @Override
                    protected IHttpHelper createHttpHelper() {
                        return mMockHttpHelper;
                    }

                    @Override
                    protected IFileDownloader createLaunchControlDownloader() {
                        return mMockDownloader;
                    }

                    @Override
                    String getUrl() {
                        return "secondaryUrl";
                    }
                };
            }

        };
        mMultiAppLaunchControlProvider.setBranch(APP_BRANCH);
        mMultiAppLaunchControlProvider.setBuildFlavor(APP_BUILD_FLAVOR);
        mMultiAppLaunchControlProvider.setQueryType(TEST_QUERY);
        mMultiAppLaunchControlProvider.setTestTag(TEST_TAG);
        mMultiAppLaunchControlProvider.setSecondaryAppBranch(SECONDARY_APP_BRANCH);
        mMultiAppLaunchControlProvider.setSecondaryAppBuildFlavor(SECONDARY_APP_BUILD_FLAVOR);
        mMultiAppLaunchControlProvider.setSecondaryAppQueryType(TEST_QUERY);

        mMockFile = new File("tmp");
        mLcCacheDir = FileUtil.createTempDir("dlcp-unit-test");
        OptionSetter setter = new OptionSetter(mMultiAppLaunchControlProvider);
        setter.setOptionValue("download-cache-dir", mLcCacheDir.getAbsolutePath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() throws Exception {
        FileUtil.recursiveDelete(mLcCacheDir);
        super.tearDown();
    }

    /**
     * Test normal success case for
     * {@link MultiAppLaunchControlProvider#getBuild()}.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild() throws Exception {
        EasyMock.expect(mMockHttpHelper.buildUrl((String) EasyMock.anyObject(),
                (MultiMap<String, String>) EasyMock.anyObject())).andReturn("").times(2);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(APP_PATH)).andReturn(mMockFile);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                SECONDARY_APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(SECONDARY_APP_PATH)).andReturn(mMockFile);
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        IBuildInfo info = mMultiAppLaunchControlProvider.getBuild();
        assertNotNull(info);
        assertEquals("4", info.getBuildId());
        assertEquals(APP_BRANCH, info.getBuildBranch());
        assertEquals(APP_BUILD_FLAVOR, info.getBuildFlavor());
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link MultiAppLaunchControlProvider#getBuild()} when the primary
     * App build is not available for testing.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_noPrimaryApp() throws Exception {
        EasyMock.expect(mMockHttpHelper.buildUrl((String) EasyMock.anyObject(),
                (MultiMap<String, String>) EasyMock.anyObject())).andReturn("");
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                NO_BUILD_QUERY_RESPONSE);
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        IBuildInfo info = mMultiAppLaunchControlProvider.getBuild();
        assertNull(info);
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link MultiAppLaunchControlProvider#getBuild()} when the secondary App build is not
     * available for testing.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_noSecondaryApp() throws Exception {
        // Three queries, one for the primary app, one for the secondary app, and one last one for
        // resetting the primary app.
        EasyMock.expect(mMockHttpHelper.buildUrl((String) EasyMock.anyObject(),
                (MultiMap<String, String>) EasyMock.anyObject())).andReturn("").times(3);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(APP_PATH)).andReturn(mMockFile);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                NO_BUILD_QUERY_RESPONSE);
        // expect a 'reset build' post to mark the device build as not tested
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        IBuildInfo info = mMultiAppLaunchControlProvider.getBuild();
        assertNull(info);
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link MultiAppLaunchControlProvider#getBuild()} when the download for the primary App
     * fails.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_downloadFailedPrimaryApp() throws Exception {
        EasyMock.expect(mMockHttpHelper.buildUrl((String) EasyMock.anyObject(),
                (MultiMap<String, String>) EasyMock.anyObject())).andReturn("").times(2);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(APP_PATH)).andThrow(new BuildRetrievalError("error"));
        // expect a 'reset build' post to mark the device build as not tested
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mMultiAppLaunchControlProvider.getBuild();
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            // expected
        }
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link MultiAppLaunchControlProvider#getBuild()} when the download for the secondary App
     * fails.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_downloadFailedSecondaryApp() throws Exception {
        EasyMock.expect(mMockHttpHelper.buildUrl((String) EasyMock.anyObject(),
                (MultiMap<String, String>) EasyMock.anyObject())).andReturn("").times(3);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(APP_PATH)).andReturn(mMockFile);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                SECONDARY_APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(SECONDARY_APP_PATH)).andThrow(
                new BuildRetrievalError("error"));
        // expect a 'reset build' post to mark the device build as not tested
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mMultiAppLaunchControlProvider.getBuild();
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            // expected
        }
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link MultiAppLaunchControlProvider#getBuild()} when an app name filter is provided
     * that matches apk.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_apkFilter() throws Exception {
        mMultiAppLaunchControlProvider.addApkFilter(".*app.*");
        mMultiAppLaunchControlProvider.addSecondaryApkFilter(".*app.*");
        EasyMock.expect(
                mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                        (MultiMap<String, String>)EasyMock.anyObject())).andReturn("").times(2);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(APP_PATH)).andReturn(mMockFile);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                SECONDARY_APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(SECONDARY_APP_PATH)).andReturn(mMockFile);

        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        IBuildInfo info = mMultiAppLaunchControlProvider.getBuild();
        assertNotNull(info);
        assertEquals("4", info.getBuildId());
        assertEquals(APP_BRANCH, info.getBuildBranch());
        assertEquals(APP_BUILD_FLAVOR, info.getBuildFlavor());
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link MultiAppLaunchControlProvider#getBuild()} when a primary app name filter is
     * provided and does not match any apk.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_primaryApkFilterNotFound() throws Exception {
        mMultiAppLaunchControlProvider.addApkFilter("nomatchfilter");
        EasyMock.expect(
                mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                        (MultiMap<String, String>)EasyMock.anyObject())).andReturn("").times(2);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
     // expect a resetBuild call
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());

        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mMultiAppLaunchControlProvider.getBuild();
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            // expected
        }
    }

    /**
     * Test {@link MultiAppLaunchControlProvider#getBuild()} when a secondary app name filter is
     * provided and does not match any apk.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_secondaryApkFilterNotFound() throws Exception {
        mMultiAppLaunchControlProvider.addSecondaryApkFilter("nomatchfilter");
        EasyMock.expect(
                mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                        (MultiMap<String, String>)EasyMock.anyObject())).andReturn("").times(3);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(APP_PATH)).andReturn(mMockFile);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                SECONDARY_APP_QUERY_RESPONSE);
        // expect a resetBuild call
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());

        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mMultiAppLaunchControlProvider.getBuild();
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            // expected
        }
    }

    /**
     * Test {@link MultiAppLaunchControlProvider#getBuild()} when an additional files filter is
     * provided that matches file.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_filesFilter() throws Exception {
        mMultiAppLaunchControlProvider.addFileFilter(".*file\\.txt");
        mMultiAppLaunchControlProvider.addSecondaryFileFilter(".*two\\.txt");
        EasyMock.expect(
                mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                        (MultiMap<String, String>)EasyMock.anyObject())).andReturn("").times(2);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(APP_PATH)).andReturn(mMockFile);
        EasyMock.expect(mMockDownloader.downloadFile(FILES_PATH)).andReturn(mMockFile);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                SECONDARY_APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(SECONDARY_APP_PATH)).andReturn(mMockFile);
        EasyMock.expect(mMockDownloader.downloadFile(SECONDARY_FILES_PATH)).andReturn(
                new File("two.txt"));

        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        IBuildInfo info = mMultiAppLaunchControlProvider.getBuild();
        assertNotNull(info);
        assertEquals("4", info.getBuildId());
        assertEquals(APP_BRANCH, info.getBuildBranch());
        assertEquals(APP_BUILD_FLAVOR, info.getBuildFlavor());
        assertTrue(info.getFile("file.txt") != null);
        assertTrue(info.getFile("two.txt") != null);
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link MultiAppLaunchControlProvider#getBuild()} when the primary additional files
     * filter provided does not that matches file.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_primaryfilesFilterDoesNotMatch() throws Exception {
        mMultiAppLaunchControlProvider.addFileFilter("no match filter");
        EasyMock.expect(
                mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                        (MultiMap<String, String>)EasyMock.anyObject())).andReturn("").times(2);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(APP_PATH)).andReturn(mMockFile);
        // expect a resetBuild call
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mMultiAppLaunchControlProvider.getBuild();
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            // expected
        }
    }

    /**
     * Test {@link MultiAppLaunchControlProvider#getBuild()} when the secondary additional files
     * filter provided does not that matches file.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_secondaryfilesFilterDoesNotMatch() throws Exception {
        mMultiAppLaunchControlProvider.addFileFilter(".*file\\.txt");
        mMultiAppLaunchControlProvider.addSecondaryFileFilter("no match filter");
        EasyMock.expect(
                mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                        (MultiMap<String, String>)EasyMock.anyObject())).andReturn("").times(3);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(APP_PATH)).andReturn(mMockFile);
        EasyMock.expect(mMockDownloader.downloadFile(FILES_PATH)).andReturn(mMockFile);
        EasyMock.expect(mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject())).andReturn(
                SECONDARY_APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile(SECONDARY_APP_PATH)).andReturn(mMockFile);
        // expect a resetBuild call
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mMultiAppLaunchControlProvider.getBuild();
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            // expected
        }
    }
}
