// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.FolderBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.build.ISdkBuildInfo;
import com.android.tradefed.build.SdkBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.util.FileUtil;

import junit.framework.Assert;
import junit.framework.TestCase;

import java.io.File;

/**
 * Unit Tests for {@link SdkLaunchControlProvider}
 */
public class SdkLaunchControlProviderTest extends TestCase {
    private class StubSdkToolsLaunchControlProvider extends SdkToolsLaunchControlProvider {
        public boolean called;

        @Override
        public IBuildInfo getBuild() throws BuildRetrievalError {
            called = true;
            return new FolderBuildInfo(null, null);
        }
    }

    private SdkLaunchControlProvider mSdkLcp;
    private StubSdkToolsLaunchControlProvider mSdkTLCP;
    private IFileDownloader mDownloader;

    @Override
    protected void setUp() {
        mSdkTLCP = new StubSdkToolsLaunchControlProvider();

        mSdkLcp =
                new SdkLaunchControlProvider() {
                    @Override
                    public IBuildInfo fetchRemoteBuild(RemoteBuildInfo remoteBuild)
                            throws BuildRetrievalError {
                        return new SdkBuildInfo();
                    }

                    @Override
                    public RemoteBuildInfo getRemoteBuild() throws BuildRetrievalError {
                        return new RemoteBuildInfo();
                    }

                    @Override
                    protected void copyToolsIntoSdkAndMakeExecutable(
                            ISdkBuildInfo sdkBuildInfo, IFolderBuildInfo toolsFolderBuildInfo)
                            throws BuildRetrievalError {
                        // do nothing
                    }

                    @Override
                    protected SdkToolsLaunchControlProvider createToolsLcProvider() {
                        return mSdkTLCP;
                    }
                };

        mDownloader =
                new IFileDownloader() {
                    @Override
                    public File downloadFile(String remoteFilePath) throws BuildRetrievalError {
                        return new File("");
                    }

                    @Override
                    public void downloadFile(String relativeRemotePath, File destFile)
                            throws BuildRetrievalError {}
                };
    }

    /** Download tools if a tools branch is set */
    public void testShouldDownloadToolsIfToolsBranchSet() throws Exception {
        File lcCacheDir = FileUtil.createTempDir("dlcp-unit-test");
        try {
            OptionSetter setter = new OptionSetter(mSdkLcp);
            setter.setOptionValue("download-cache-dir", lcCacheDir.getAbsolutePath());
            final String buildId = "120981";
            final String branch = "branch";
            final String flavor = "flavor";

            mSdkLcp.setToolsBuildId(buildId);
            mSdkLcp.setToolsBranch(branch);
            mSdkLcp.setToolsFlavor(flavor);
            mSdkLcp.setToolsQueryType(QueryType.RESET_TEST_BUILD);
            mSdkLcp.getBuild();

            assertTrue(mSdkTLCP.called);
            assertEquals(buildId, mSdkTLCP.getBuildId());
            assertEquals(branch, mSdkTLCP.getBranch());
            assertEquals(flavor, mSdkTLCP.getBuildFlavor());
        } finally {
            FileUtil.recursiveDelete(lcCacheDir);
        }
    }

    /**
     * Download tools if a tools branch is set
     */
    public void testShouldNotDownloadToolsIfToolsBranchNull() throws Exception {
        File lcCacheDir = FileUtil.createTempDir("dlcp-unit-test");
        try {
            OptionSetter setter = new OptionSetter(mSdkLcp);
            setter.setOptionValue("download-cache-dir", lcCacheDir.getAbsolutePath());
            mSdkLcp.getBuild();
            assertFalse(mSdkTLCP.called);
        } finally {
            FileUtil.recursiveDelete(lcCacheDir);
        }
    }

    /** No download if RemoteBuildInfo[FILES] is empty. */
    public void testdownloadTestZip_empty() throws Exception {
        RemoteBuildInfo buildInfo = new RemoteBuildInfo();
        OptionSetter setter = new OptionSetter(mSdkLcp);
        Assert.assertNull(mSdkLcp.downloadTestZip(buildInfo, mDownloader));
    }

    /** Download if RemoteBuildInfo[FILES] matches. */
    public void testdownloadTestZip_files() throws Exception {
        RemoteBuildInfo buildInfo = new RemoteBuildInfo();
        buildInfo.addAttribute(RemoteBuildInfo.BuildAttributeKey.FILES, "");
        OptionSetter setter = new OptionSetter(mSdkLcp);
        setter.setOptionValue("tests-zip-filter", "foo");
        buildInfo.addAttribute(RemoteBuildInfo.BuildAttributeKey.FILES, "foo");
        Assert.assertNotNull(mSdkLcp.downloadTestZip(buildInfo, mDownloader));
    }
}
