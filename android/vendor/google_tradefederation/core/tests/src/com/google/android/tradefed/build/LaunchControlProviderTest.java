// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import static org.junit.Assert.*;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.host.HostOptions;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.util.net.IHttpHelper;

import org.junit.After;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.concurrent.Semaphore;

/** Unit tests for {@link LaunchControlProvider} */
public class LaunchControlProviderTest {

    private Semaphore mTestDownloadLock;
    private Boolean mCheckDownloadLock = true;

    @After
    public void tearDown() {
        mTestDownloadLock = null;
        mCheckDownloadLock = true;
    }

    /** Make sure that a blacklisted build isn't offered for testing by the provider */
    @Test
    public void testBlacklist_badBuild() throws Exception {
        final BuildBlacklist blacklist = new BuildBlacklist(0);
        blacklist.parse(new BufferedReader(new StringReader(("12345\n"))));

        final String buildResponse = String.format("%s:12345",
                RemoteBuildInfo.BuildAttributeKey.BUILD_ID.getRemoteValue());
        final RemoteBuildInfo build = RemoteBuildInfo.parseRemoteBuildInfo(buildResponse);

        LaunchControlProvider mLC = new LaunchControlProvider() {
            @Override
            protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild,
                    String testTargetName, String buildName, IFileDownloader downloader)
                    throws BuildRetrievalError {
                throw new BuildRetrievalError("stub; not implemented");
            }

            @Override
            boolean isBlacklistedBuild(String buildId, String flavor) {
                return blacklist.isBlacklistedBuild(buildId, flavor);
            }

            @Override
            protected RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
                return build;
            }

            @Override
            public void cleanUp(IBuildInfo info) {
                // ignore
            }
        };

        RemoteBuildInfo lcBuild = mLC.getRemoteBuild();
        assertNull(String.format("Expected null build, but got %s", lcBuild), lcBuild);
    }

    /** Make sure that a good build is offered as expected even when we're using the blacklist. */
    @Test
    public void testBlacklist_goodBuild() throws Exception {
        final BuildBlacklist blacklist = new BuildBlacklist(0);
        blacklist.parse(new BufferedReader(new StringReader(("12345\n"))));

        final String buildResponse = String.format("%s:54321",
                RemoteBuildInfo.BuildAttributeKey.BUILD_ID.getRemoteValue());
        final RemoteBuildInfo build = RemoteBuildInfo.parseRemoteBuildInfo(buildResponse);

        LaunchControlProvider mLC = new LaunchControlProvider() {
            @Override
            protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild,
                    String testTargetName, String buildName, IFileDownloader downloader)
                    throws BuildRetrievalError {
                throw new BuildRetrievalError("stub; not implemented");
            }

            @Override
            boolean isBlacklistedBuild(String buildId, String flavor) {
                return blacklist.isBlacklistedBuild(buildId, flavor);
            }

            @Override
            protected RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
                return build;
            }

            @Override
            public void cleanUp(IBuildInfo info) {
                // ignore
            }
        };

        RemoteBuildInfo lcBuild = mLC.getRemoteBuild();
        assertNotNull("Didn't get a build from LC provider", lcBuild);
    }

    /**
     * Ensure that when the sso option is on, we create the proper helper and use right location.
     */
    @Test
    public void testSsoClientlocation() throws ConfigurationException {
        final String ssoFakePath = "/tmp/sso_client_test";
        LaunchControlProvider mLC = new LaunchControlProvider() {
            @Override
            public void cleanUp(IBuildInfo info) {
                // empty on purpose
            }
            @Override
            protected IBuildInfo downloadBuildFiles(
                    RemoteBuildInfo remoteBuild, String testTargetName, String buildName,
                    IFileDownloader downloader) throws BuildRetrievalError {
                // empty on purpose
                return null;
            }
        };
        OptionSetter setter = new OptionSetter(mLC);
        setter.setOptionValue("sso-client-path", ssoFakePath);
        setter.setOptionValue("use-sso-client", "true");
        IHttpHelper helper = mLC.createHttpHelper();
        assertTrue(helper instanceof SsoClientHttpHelper);
        assertEquals(((SsoClientHttpHelper)helper).getSsoClientPath(), ssoFakePath);
    }

    /** Test that querying builds with a download limit can still work when done serially. */
    @Test
    public void testTakePermit() throws Exception {
        final String buildResponse =
                String.format(
                        "%s:54321", RemoteBuildInfo.BuildAttributeKey.BUILD_ID.getRemoteValue());
        final RemoteBuildInfo build = RemoteBuildInfo.parseRemoteBuildInfo(buildResponse);

        LaunchControlProvider mLC =
                new LaunchControlProvider() {
                    @Override
                    public void cleanUp(IBuildInfo info) {
                        // empty on purpose
                    }

                    @Override
                    protected IBuildInfo downloadBuildFiles(
                            RemoteBuildInfo remoteBuild,
                            String testTargetName,
                            String buildName,
                            IFileDownloader downloader)
                            throws BuildRetrievalError {
                        return null;
                    }

                    @Override
                    public IBuildInfo fetchRemoteBuild(RemoteBuildInfo remoteBuild)
                            throws BuildRetrievalError {
                        return new BuildInfo();
                    }

                    @Override
                    protected RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
                        return build;
                    }

                    @Override
                    IHostOptions getHostOptions() {
                        IHostOptions hostOption = new HostOptions();
                        try {
                            OptionSetter setter = new OptionSetter(hostOption);
                            setter.setOptionValue("host_options:concurrent-download-limit", "1");
                        } catch (ConfigurationException e) {
                            // Should not happen.
                            throw new RuntimeException(e);
                        }
                        return hostOption;
                    }

                    @Override
                    Semaphore getDownloadLock() {
                        return mTestDownloadLock;
                    }

                    @Override
                    void createDownloadLock(int concurrentDownloadLimit) {
                        mTestDownloadLock = new Semaphore(concurrentDownloadLimit, true);
                    }

                    @Override
                    Boolean getCheckDownloadLock() {
                        return mCheckDownloadLock;
                    }

                    @Override
                    void setCheckDownloadLock() {
                        mCheckDownloadLock = false;
                    }
                };
        mLC.setBuildFlavor("TEST_FLAVOR");
        mLC.setTestTag("TEST_TAG");
        mLC.setBranch("BRANCH");
        // Token is properly taken and released, otherwise next query would block.
        assertNull(mTestDownloadLock);
        assertNotNull(mLC.getBuild());
        // lock is initialized
        assertNotNull(mTestDownloadLock);
        assertNotNull(mLC.getBuild());
        assertNotNull(mTestDownloadLock);
        assertNotNull(mLC.getBuild());
        assertNotNull(mTestDownloadLock);
    }

    /**
     * Test that querying builds with no download limit results results in not checking to take the
     * permit.
     */
    @Test
    public void testTakePermit_noHostOptions() throws Exception {
        final String buildResponse =
                String.format(
                        "%s:54321", RemoteBuildInfo.BuildAttributeKey.BUILD_ID.getRemoteValue());
        final RemoteBuildInfo build = RemoteBuildInfo.parseRemoteBuildInfo(buildResponse);

        LaunchControlProvider mLC =
                new LaunchControlProvider() {
                    @Override
                    public void cleanUp(IBuildInfo info) {
                        // empty on purpose
                    }

                    @Override
                    protected IBuildInfo downloadBuildFiles(
                            RemoteBuildInfo remoteBuild,
                            String testTargetName,
                            String buildName,
                            IFileDownloader downloader)
                            throws BuildRetrievalError {
                        return null;
                    }

                    @Override
                    public IBuildInfo fetchRemoteBuild(RemoteBuildInfo remoteBuild)
                            throws BuildRetrievalError {
                        return new BuildInfo();
                    }

                    @Override
                    protected RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
                        return build;
                    }

                    @Override
                    IHostOptions getHostOptions() {
                        // Use default hostOption with null value.
                        IHostOptions hostOption = new HostOptions();
                        return hostOption;
                    }

                    @Override
                    Semaphore getDownloadLock() {
                        return mTestDownloadLock;
                    }

                    @Override
                    void createDownloadLock(int concurrentDownloadLimit) {
                        mTestDownloadLock = new Semaphore(concurrentDownloadLimit, true);
                    }

                    @Override
                    Boolean getCheckDownloadLock() {
                        return mCheckDownloadLock;
                    }

                    @Override
                    void setCheckDownloadLock() {
                        mCheckDownloadLock = false;
                    }
                };
        mLC.setBuildFlavor("TEST_FLAVOR");
        mLC.setTestTag("TEST_TAG");
        mLC.setBranch("BRANCH");
        // Assert the download lock is never initialized.
        assertNull(mTestDownloadLock);
        assertNotNull(mLC.getBuild());
        assertNull(mTestDownloadLock);
        assertNotNull(mLC.getBuild());
        assertNull(mTestDownloadLock);
    }

    /**
     * Test that the build info associated with the {@link BuildRetrievalError} contains some
     * information necessary for reporting.
     */
    @Test
    public void testBuildRetrievalError_buildInfo() throws Exception {
        final String buildResponse =
                String.format(
                        "%s:54321", RemoteBuildInfo.BuildAttributeKey.BUILD_ID.getRemoteValue());
        final RemoteBuildInfo build = RemoteBuildInfo.parseRemoteBuildInfo(buildResponse);

        LaunchControlProvider mLC =
                new LaunchControlProvider() {
                    @Override
                    public void cleanUp(IBuildInfo info) {
                        // empty on purpose
                    }

                    @Override
                    protected IBuildInfo downloadBuildFiles(
                            RemoteBuildInfo remoteBuild,
                            String testTargetName,
                            String buildName,
                            IFileDownloader downloader)
                            throws BuildRetrievalError {
                        return null;
                    }

                    @Override
                    public IBuildInfo fetchRemoteBuild(RemoteBuildInfo remoteBuild)
                            throws BuildRetrievalError {
                        throw new BuildRetrievalError(
                                "test", new Exception("failed"), new BuildInfo());
                    }

                    @Override
                    protected RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
                        return build;
                    }

                    @Override
                    IHostOptions getHostOptions() {
                        // Use default hostOption with null value.
                        IHostOptions hostOption = new HostOptions();
                        return hostOption;
                    }
                };
        mLC.setBuildFlavor("TEST_FLAVOR");
        mLC.setTestTag("TEST_TAG");
        mLC.setBranch("BRANCH");
        mLC.setBuildOs("fastlinux");
        try {
            mLC.getBuild();
            fail("Should have thrown an exception");
        } catch (BuildRetrievalError expected) {
            assertNotNull(expected.getBuildInfo());
            assertEquals("BRANCH", expected.getBuildInfo().getBuildBranch());
            assertEquals("TEST_FLAVOR", expected.getBuildInfo().getBuildFlavor());
            assertEquals(
                    "TEST_FLAVOR_fastlinux",
                    expected.getBuildInfo().getBuildAttributes().get("build_target"));
        }
    }

    /** Test that "" doesn't match RemoteBuildInfo with no files. */
    @Test(expected = BuildRetrievalError.class)
    public void testDownloadAdditionalFiles() throws Exception {
        LaunchControlProvider lcp =
                new LaunchControlProvider() {
                    @Override
                    protected IBuildInfo downloadBuildFiles(
                            RemoteBuildInfo remoteBuild,
                            String testTargetName,
                            String buildName,
                            IFileDownloader downloader)
                            throws BuildRetrievalError {
                        return null;
                    }

                    @Override
                    public void cleanUp(IBuildInfo info) {
                        // empty on purpose
                    }
                };
        OptionSetter setter = new OptionSetter(lcp);
        setter.setOptionValue("additional-files-filter", "");
        RemoteBuildInfo remoteBuildInfo = new RemoteBuildInfo();
        remoteBuildInfo.addAttribute(RemoteBuildInfo.BuildAttributeKey.FILES, "");
        lcp.downloadAdditionalFiles(remoteBuildInfo, null, null);
    }
}

