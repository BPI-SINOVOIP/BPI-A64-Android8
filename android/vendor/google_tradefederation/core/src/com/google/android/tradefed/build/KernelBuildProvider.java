// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.FileDownloadCacheWrapper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.IKernelBuildInfo;
import com.android.tradefed.build.KernelBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.net.HttpHelper;
import com.android.tradefed.util.net.IHttpHelper;
import com.android.tradefed.util.net.IHttpHelper.DataSizeException;
import com.google.android.tradefed.build.RemoteKernelBuildInfo.InvalidResponseException;
import com.google.android.tradefed.result.KernelTestResultReporter;
import com.google.android.tradefed.result.KernelTestResultReporter.KernelTestStatus;

import java.io.File;
import java.io.IOException;

/**
 * A {@link IBuildProvider} implementation that queries the kernel build service for a kernel build
 * to test.
 */
public class KernelBuildProvider implements IBuildProvider {
    private static final File KERNEL_CACHE = new File(System.getProperty("java.io.tmpdir"),
            "kernel_cache");

    private static final String TEST_TAG_NAME = "test-tag";
    @Option(name = TEST_TAG_NAME, description = "unique identifier to provide to launch control.",
            importance = Importance.IF_UNSET)
    // used to track which builds have been tested when querying for new builds to test
    private String mTestTag = null;

    private static final String KERNEL_BRANCH_NAME = "kernel-branch";
    @Option(name = KERNEL_BRANCH_NAME, description = "the kernel branch to test",
            importance = Importance.IF_UNSET)
    private String mKernelBranch = null;

    @Option(name = "kernel-build-id", description = "the kernel build id to test. If unspecified "
            + "will get latest untested build")
    private String mKernelBuildId = null;

    @Option(name = "min-kernel-build-id", description = "the minimum kernel build id to test")
    private String mMinKernelBuildId = null;

    private static final String KERNEL_HOSTNAME = "kernel-hostname";
    @Option(name = KERNEL_HOSTNAME, description = "the host of the kernel build server")
    private String mKernelHostName = "http://vpbs1.mtv.corp.google.com:8080";

    private static final String BUILD_ID_NAME = "build-id";
    @Option(name = BUILD_ID_NAME, description = "Build id to query. Only used for " +
            "NOTIFY_TEST_BUILD and RESET_TEST_BUILD queries.")
    private String mDeviceBuildId = null;

    private static final String BUILD_FLAVOR_NAME = "build-flavor";
    @Option(name = BUILD_FLAVOR_NAME, description = "the build flavor e.g. passion-userdebug.",
            importance = Importance.IF_UNSET)
    private String mDeviceBuildFlavor = null;

    @Option(name = "use-bigstore", description = "[deprecated] the bigstore downloader will be " +
            "used regardless of this flag.")
    private boolean mUseBigStore = true;

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        String url = buildUrl(mKernelBuildId);
        MultiMap<String, String> params = buildParams();
        RemoteKernelBuildInfo remoteBuild = queryForBuild(url, params);

        if (remoteBuild == null) {
            return null;
        }

        IKernelBuildInfo localBuild = new KernelBuildInfo(remoteBuild.getSha1(),
                remoteBuild.getShortSha1(), remoteBuild.getCommitTime(), createBuildName());
        localBuild.setBuildFlavor(getBuildFlavor());
        localBuild.setBuildBranch(getKernelBranch());
        try {
            localBuild.setKernelFile(createDownloader().downloadFile(
                    remoteBuild.getKernelFilePath()), remoteBuild.getSha1());
        } catch (BuildRetrievalError e) {
            // one or more packages failed to download - clean up any successfully downloaded files
            localBuild.cleanUp();
            e.setBuildInfo(localBuild);
            throw e;
        } catch (RuntimeException e) {
            // one or more packages failed to download - clean up any successfully downloaded files
            localBuild.cleanUp();
            throw e;
        }

        // Mark test as testing immediately, so the same test isn't picked up by multiple instances.
        markBuildAsTesting(localBuild);

        return localBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void buildNotTested(IBuildInfo info) {
        if (info instanceof IKernelBuildInfo) {
            IHttpHelper helper = createHttpHelper();
            IKernelBuildInfo buildInfo = (IKernelBuildInfo) info;

            String sha1 = buildInfo.getSha1();

            MultiMap<String, String> params = new MultiMap<String, String>();
            params.put("test-tag", buildInfo.getTestTag());
            params.put("build-flavor", buildInfo.getBuildFlavor());
            params.put("build-id", mDeviceBuildId);
            params.put("reset-test", "1");

            try {
                if (helper.doPostWithRetry(buildUrl(sha1),
                        helper.buildParameters(params)) == null) {
                    CLog.e("Could not reset test with url %s",
                            helper.buildUrl(buildUrl(sha1), params));
                }
            } catch (IOException e) {
                CLog.e("Could not reset test with url %s", helper.buildUrl(buildUrl(sha1), params));
            } catch (DataSizeException e) {
                CLog.e("Could not reset test with url %s", helper.buildUrl(buildUrl(sha1), params));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        info.cleanUp();
    }

    /**
     * Queries the kernel build service for a kernel build.
     *
     * @param url a {@link String} with the URL used to query for the build
     * @param params a {@link MultiMap} with the parameters used to query for the build
     * @return a {@link RemoteKernelBuildInfo} if there is a kernel build to test or {@code null}
     * @throws BuildRetrievalError if there was an error retrieving the build.
     */
    private RemoteKernelBuildInfo queryForBuild(String url, MultiMap<String, String> params)
            throws BuildRetrievalError{
        IHttpHelper helper = createHttpHelper();
        url = helper.buildUrl(url, params);
        CLog.v("Starting kernel build query %s", url);
        String response;
        try {
            response = helper.doGetWithRetry(url);
            CLog.v("Got response %s", response);
        } catch (IOException e) {
            throw new BuildRetrievalError("Failed to get response", e);
        } catch (DataSizeException e) {
            throw new BuildRetrievalError("Failed to get response", e);
        }

        try {
            return RemoteKernelBuildInfo.parseRemoteBuildInfo(response);
        } catch (InvalidResponseException e) {
            throw new BuildRetrievalError("Failed to parse response", e);
        }
    }

    /**
     * Get the test tag.
     */
    public String getTestTag() {
        return mTestTag;
    }

    /**
     * Set the test tag.
     */
    public void setTestTag(String tag) {
        mTestTag = tag;
    }

    /**
     * Get the kernel branch.
     */
    public String getKernelBranch() {
        return mKernelBranch;
    }

    /**
     * Set the kernel branch.
     */
    public void setKernelBranch(String kernelBranch) {
        mKernelBranch = kernelBranch;
    }

    /**
     * Get the kernel build id (git sha1) to test.
     */
    public String getKernelBuildId() {
        return mKernelBuildId;
    }

    /**
     * Set the kernel build id (git sha1) to test.
     */
    public void setKernelBuildId(String buildId) {
        mKernelBuildId = buildId;
    }

    /**
     * Get the minimum kernel build id (git sha1) to test.
     */
    public String getMinKernelBuildId() {
        return mMinKernelBuildId;
    }

    /**
     * Set the minimum kernel build id (git sha1) to test.
     */
    public void setMinKernelBuildId(String buildId) {
        mMinKernelBuildId = buildId;
    }

    /**
     * Get the kernel build service host name.
     */
    public String getKernelHostName() {
        return mKernelHostName;
    }

    /**
     * Set the kernel build service host name.
     */
    public void setKernelHostName(String hostName) {
        mKernelHostName = hostName;
    }

    /**
     * Get the build flavor.
     */
    public String getBuildFlavor() {
        return mDeviceBuildFlavor;
    }

    /**
     * Set the build flavor.
     */
    public void setBuildFlavor(String flavor) {
        mDeviceBuildFlavor = flavor;
    }

    /**
     * Get the device build id that the kernel is to be tested against.
     */
    public String getBuildId() {
        return mDeviceBuildId;
    }

    /**
     * Set the device build id that the kernel is to be tested against.
     */
    public void setBuildId(String buildId) {
        mDeviceBuildId = buildId;
    }

    public boolean shouldUseBigStore() {
        return mUseBigStore;
    }

    public void setUseBigStore(boolean useBigStore) {
        mUseBigStore = useBigStore;
    }

    /**
     * Factory method for creating a downloader for downloading kernel files
     */
    IFileDownloader createDownloader() {
        return new FileDownloadCacheWrapper(KERNEL_CACHE, new BigStoreFileDownloader());
    }

    /**
     * Constructs the URL to be used for querying kernel builds.
     *
     * @return the URL as a {@link String}
     * @throws IllegalArgumentException if kernel host name or kernel branch is {@code null}
     */
    String buildUrl(String sha1) {
        if (mKernelHostName == null) {
            throw createMissingOptionException(KERNEL_HOSTNAME);
        }
        if (mKernelBranch == null) {
            throw createMissingOptionException(KERNEL_BRANCH_NAME);
        }

        if (sha1 != null) {
            return String.format("%s/%s/%s/", mKernelHostName, mKernelBranch, sha1);
        }

        return String.format("%s/%s/", mKernelHostName, mKernelBranch);
    }

    /**
     * Constructs the parameters to be used for querying kernel builds.
     *
     * @return the parameters as a {@link MultiMap}
     * @throws IllegalArgumentException if the test tag, build flavor, or device build id is
     *     {@code null}
     */
    private MultiMap<String, String> buildParams() {
        if (mTestTag == null) {
            throw createMissingOptionException(TEST_TAG_NAME);
        }
        if (mDeviceBuildFlavor == null) {
            throw createMissingOptionException(BUILD_FLAVOR_NAME);
        }
        if (mDeviceBuildId == null) {
            throw createMissingOptionException(BUILD_ID_NAME);
        }
        MultiMap<String, String> params = new MultiMap<String, String>();
        params.put("test-tag", mTestTag);
        params.put("build-flavor", mDeviceBuildFlavor);
        params.put("build-id", mDeviceBuildId);
        // If we provide a kernel id, ignore the min kernel id.
        if (mKernelBuildId == null && mMinKernelBuildId != null) {
            params.put("min-kernel-build-id", mMinKernelBuildId);
        }
        return params;
    }

    /**
     * Creates an {@link IllegalArgumentException} with the name of the missing option.
     *
     * @param optionName the name of the required option.
     * @return an {@link IllegalArgumentException}
     */
    private IllegalArgumentException createMissingOptionException(String optionName) {
        return new IllegalArgumentException(String.format("Missing required option %s",
                optionName));
    }

    /**
     * Construct the name of the build.
     *
     * @return {@link String} name of build
     */
    private String createBuildName() {
        return String.format("%s-%s", mKernelBranch, mDeviceBuildFlavor);
    }

    /**
     * Get {@link IHttpHelper} to use. Exposed so unit tests can mock.
     */
    IHttpHelper createHttpHelper() {
        return new HttpHelper();
    }

    /**
     * Mark a build as testing. Exposed so unit tests can mock.
     */
    void markBuildAsTesting(IKernelBuildInfo info) {
        new KernelTestResultReporter().setStatus(buildUrl(info.getSha1()), info, mDeviceBuildId,
                KernelTestStatus.TESTING, null);
    }
}
