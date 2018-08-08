// Copyright 2012 Google Inc. All Rights Reserved.

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
 * Unit tests for {@link AppLaunchControlProvider}.
 */
public class AppLaunchControlProviderTest extends TestCase {

    private static final int BUILD_ID = 4;
    private static final String APP_PATH = "somepath/app.apk";
    private static final String FILES_PATH = "somepath/file.txt";
    private static final String APP_QUERY_RESPONSE = String.format("bid:%d\n%s:%s\n%s:%s", BUILD_ID,
            BuildAttributeKey.APP_APKS.getRemoteValue(), APP_PATH,
            BuildAttributeKey.FILES.getRemoteValue(), FILES_PATH);
    private static final String APP_EMPTY_PATH = "";
    private static final String FILES_EMPTY_PATH = "";
    private static final String APP_EMPTY_QUERY_RESPONSE =
            String.format(
                    "bid:%d\n%s:%s\n%s:%s",
                    BUILD_ID,
                    BuildAttributeKey.APP_APKS.getRemoteValue(),
                    APP_EMPTY_PATH,
                    BuildAttributeKey.FILES.getRemoteValue(),
                    FILES_EMPTY_PATH);
    private static final String TEST_TAG = "tag";
    private static final QueryType TEST_QUERY = QueryType.LATEST_GREEN_BUILD;
    private static final String APP_BRANCH = "unbungled_branch";
    private static final String APP_BUILD_FLAVOR = "flavorflav";

    private AppLaunchControlProvider mLaunchControlProvider;
    private IHttpHelper mMockHttpHelper;
    private IFileDownloader mMockDownloader;
    private File mLcCacheDir;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockHttpHelper = EasyMock.createMock(IHttpHelper.class);
        mMockDownloader = EasyMock.createNiceMock(IFileDownloader.class);
        mLaunchControlProvider = new AppLaunchControlProvider() {
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

        };
        mLaunchControlProvider.setBranch(APP_BRANCH);
        mLaunchControlProvider.setBuildFlavor(APP_BUILD_FLAVOR);
        mLaunchControlProvider.setQueryType(TEST_QUERY);
        mLaunchControlProvider.setTestTag(TEST_TAG);
        mLcCacheDir = FileUtil.createTempDir("dlcp-unit-test");
        OptionSetter setter = new OptionSetter(mLaunchControlProvider);
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
     * Test normal success case for {@link AppLaunchControlProvider#getBuild()}.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild() throws Exception {
        // TODO: consider verifying contents of param - but probably overkill
        EasyMock.expect(
                mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                        (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject());
        EasyMock.expectLastCall().andReturn(APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile((String)EasyMock.anyObject()))
                .andReturn(new File("tmp"));
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        IBuildInfo info = mLaunchControlProvider.getBuild();
        assertNotNull(info);
        assertEquals("4", info.getBuildId());
        assertEquals(APP_BRANCH, info.getBuildBranch());
        assertEquals(APP_BUILD_FLAVOR, info.getBuildFlavor());
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link AppLaunchControlProvider#getBuild()} when an app name filter is provided
     * that matches apk.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_apkFilter() throws Exception {
        mLaunchControlProvider.addApkFilter(".*app.*");
        EasyMock.expect(
                mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                        (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject());
        EasyMock.expectLastCall().andReturn(APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile((String)EasyMock.anyObject()))
                .andReturn(new File("tmp"));
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        IBuildInfo info = mLaunchControlProvider.getBuild();
        assertNotNull(info);
        assertEquals("4", info.getBuildId());
        assertEquals(APP_BRANCH, info.getBuildBranch());
        assertEquals(APP_BUILD_FLAVOR, info.getBuildFlavor());
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link AppLaunchControlProvider#getBuild()} when an app name filter is provided
     * that does not match apk.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_apkFilteredOut() throws Exception {
        mLaunchControlProvider.addApkFilter("blah");
        EasyMock.expect(
                mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                        (MultiMap<String, String>)EasyMock.anyObject())).andStubReturn("");
        mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject());
        EasyMock.expectLastCall().andReturn(APP_QUERY_RESPONSE);
        // expect a resetBuild call
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());

        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mLaunchControlProvider.getBuild();
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            // expected
        }
    }

    /**
     * Test {@link AppLaunchControlProvider#getBuild()} when an additional files filter is provided
     * that matches file.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_filesFilter() throws Exception {
        mLaunchControlProvider.addFileFilter(".*file\\.txt");
        EasyMock.expect(
                mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                        (MultiMap<String, String>)EasyMock.anyObject())).andReturn("");
        mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject());
        EasyMock.expectLastCall().andReturn(APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile((String)EasyMock.anyObject()))
                .andReturn(new File("tmp")).times(2);
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        IBuildInfo info = mLaunchControlProvider.getBuild();
        assertNotNull(info);
        assertEquals("4", info.getBuildId());
        assertEquals(APP_BRANCH, info.getBuildBranch());
        assertEquals(APP_BUILD_FLAVOR, info.getBuildFlavor());
        assertTrue(info.getFile("file.txt") != null);
        EasyMock.verify(mMockHttpHelper, mMockDownloader);
    }

    /**
     * Test {@link AppLaunchControlProvider#getBuild()} when an additional files filter is provided
     * that does not match file.
     */
    @SuppressWarnings("unchecked")
    public void testGetBuild_filesFilteredOut() throws Exception {
        mLaunchControlProvider.addFileFilter("nomatch");
        EasyMock.expect(
                mMockHttpHelper.buildUrl((String)EasyMock.anyObject(),
                        (MultiMap<String, String>)EasyMock.anyObject())).andStubReturn("");
        mMockHttpHelper.doGetWithRetry((String)EasyMock.anyObject());
        EasyMock.expectLastCall().andReturn(APP_QUERY_RESPONSE);
        EasyMock.expect(mMockDownloader.downloadFile((String)EasyMock.anyObject()))
                .andReturn(new File("tmp"));
        // expect a resetBuild call
        mMockHttpHelper.doGetIgnore((String)EasyMock.anyObject());
        EasyMock.replay(mMockHttpHelper, mMockDownloader);
        try {
            mLaunchControlProvider.getBuild();
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            // expected
        }
    }

    /** Test {@link AppLaunchControlProvider#getBuild()} when no files are specified. */
    @SuppressWarnings("unchecked")
    public void testGetBuild_noFiles() throws Exception {
        EasyMock.expect(
                        mMockHttpHelper.buildUrl(
                                (String) EasyMock.anyObject(),
                                (MultiMap<String, String>) EasyMock.anyObject()))
                .andStubReturn("");
        mMockHttpHelper.doGetWithRetry((String) EasyMock.anyObject());
        EasyMock.expectLastCall().andReturn(APP_EMPTY_QUERY_RESPONSE);
        // expect a resetBuild call
        mMockHttpHelper.doGetIgnore((String) EasyMock.anyObject());
        EasyMock.replay(mMockHttpHelper, mMockDownloader);

        try {
            mLaunchControlProvider.getBuild();
            fail("BuildRetrievalError not thrown");
        } catch (BuildRetrievalError e) {
            assertTrue(e.getMessage().contains("Could not find file matching pattern"));
        }
    }
}
