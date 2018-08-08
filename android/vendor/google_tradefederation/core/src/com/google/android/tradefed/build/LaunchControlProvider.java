// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.ddmlib.Log;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.FileDownloadCache;
import com.android.tradefed.build.FileDownloadCacheFactory;
import com.android.tradefed.build.FileDownloadCacheWrapper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.config.OptionUpdateRule;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.TimeUtil;
import com.android.tradefed.util.net.HttpHelper;
import com.android.tradefed.util.net.IHttpHelper;
import com.android.tradefed.util.net.IHttpHelper.DataSizeException;

import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;
import com.google.android.tradefed.build.RemoteBuildInfo.InvalidResponseException;
import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * A {@link IBuildProvider} implementation that queries the Google launch control servers for a
 * build to test.
 *
 * TODO: refactor out the launch control client and download logic, and merge it with the future
 * "One Common Device Flasher Library To Rule Them All" (tm)
 */
public abstract class LaunchControlProvider implements IBuildProvider, IInvocationContextReceiver {

    private static final String LOG_TAG = "LaunchControlProvider";

    protected static final String LC_BASE_URL = "%s://%s/buildbot-update";
    // the names of the Url parameters to provide to launch control
    protected static final String BUILD_ID_PARAM = "bid";
    protected static final String BUILD_NAME_PARAM = "id";
    protected static final String TEST_TAG_PARAM = "test_tag";
    protected static final String QUERY_TYPE_PARAM = "op";
    protected static final String MIN_BUILD_ID_PARAM = "min_build_id";
    protected static final String WHITELIST_PARAM = "whitelist";
    protected static final String QUERY_WINDOW_PARAM = "query_window";

    private static final String PROTOCOL = "protocol";
    @Option(name=PROTOCOL, description = "protocol for launch control requests. (http/https)")
    private String mLcProtocol = "https";

    private static final String HOSTNAME = "hostname";
    @Option(name = HOSTNAME, description = "host name for launch control requests")
    private String mLcHostname = "lcproxy.googleplex.com";

    private static final String TEST_TAG_NAME = "test-tag";
    private String mTestTag = null;

    private static final String QUERY_TYPE_NAME = "query-type";
    @Option(name = QUERY_TYPE_NAME, description = "type of launch control query to perform when " +
            "requesting new builds to test.")
    private QueryType mQueryType = QueryType.LATEST_GREEN_BUILD;

    @Option(name="whitelist", description = "whitelist to use. Only valid with Launch Control " +
            "Proxy and only when requesting whitelisted builds.")
    private String mWhitelist = null;

    @Option(name="query-window",
            description = "How far back in time to go when querying builds. "+
            "This looks at the build timestamp as specified by the Android Build API. " +
            "The param can be specified as a signed sequence of decimal number and a unit suffix," +
            " such as 300ms or 2h45m. Valid time units are ns, us (or µs), ms, s, m, h. " +
            "For example: if we specify 24h, we are only interested in builds that were built in " +
            "the last 24 hours. " +
            "This param is only valid when querying Launch Control Proxy and only for " +
            "LATEST-WHITELISTED-CL and LATEST-GREEN-CL query types")
    private String mQueryWindow = null;

    private static final String BUILD_ID_NAME = "build-id";
    @Option(name = BUILD_ID_NAME, description = "Build id to query. Only used for " +
            "NOTIFY_TEST_BUILD and RESET_TEST_BUILD queries.")
    private String mBuildId = null;

    private static final String BUILD_ARCHIVE_PATH = "build-archive-path";
    @Option(name = BUILD_ARCHIVE_PATH, description = "MDB path where the build archive resides")
    private String mBuildArchivePath = "/namespace/android/builds";

    @Option(name = "min-build-id", description = "Minimum build id to test. " +
            "Builds less than this value will be skipped.", updateRule = OptionUpdateRule.GREATEST)
    private Integer mMinBuildId = -1;

    private static final String BRANCH_NAME = "branch";
    @Option(name = BRANCH_NAME, description = "the build branch to test.",
            importance = Importance.IF_UNSET)
    private String mBranch = null;

    @Option(name = "build-os", description = "the OS platform the build is made on.")
    private String mOsPlatform = "linux";

    private static final String BUILD_FLAVOR_NAME = "build-flavor";
    @Option(name = BUILD_FLAVOR_NAME, description = "the build flavor e.g. passion-userdebug.")
    private String mBuildFlavor = null;

    private static final String ITERATIONS_NAME = "iterations";
    @Option(name = "test-iterations", description = "the number of iterations to run for. " +
            "Only available with the Launch Control Proxy.")
    private Integer mIterations = null;

    private static final String FALLBACK_NAME = "fallback";
    @Option(name = FALLBACK_NAME, description = "whether or not we should fall back to the next " +
            "available build, or just test the latest one. Only available with the Launch " +
            "Control Proxy.")
    private Boolean mFallback = null;

    @Option(name = "download-cache-dir", description = "the directory for caching downloaded " +
            "files.  Should be on the same filesystem as java.io.tmpdir.  Consider changing the " +
            "java.io.tmpdir property if you want to move downloads to a different filesystem.")
    private File mDownloadCacheDir = new File(System.getProperty("java.io.tmpdir"), "lc_cache");

    @Option(name = "use-bigstore", description = "[deprecated] this flag is not used anymore.")
    private boolean mUseBigStore = true;

    @Option(name = "use-kerberos-with-bigstore", description = "whether to use kerberos for the " +
            "bigstore downloader.  Ignored unless bigstore downloader is enabled.")
    private boolean mUseKrbForBigstore = false;

    @Option(name = "query-timeout", description =
            "Maximum number of seconds to wait for a single LC query to return.")
    private int mQueryTimeoutSec = 180;

    @Option(name = "query-max-time", description =
            "Maximum number of minutes to try to query LC. Note an escalting backoff is used.")
    private int mQueryRetryMin = 31;

    @Option(name = "build-blacklist", description = "the file that contains the build blacklist")
    private File mBuildBlacklist = null;

    @Option(name = "use-sso-client", description = "whether or not we should query LC with " +
            "sso_client.")
    private boolean mUseSsoClient = true;

    @Option(name = "sso-client-path", description = "The location of the sso_client binary, "
            + "default use the one in $PATH")
    private String mSsoBinary = "sso_client";

    @Option(name = "sso-client-cert", description = "The location of the sso_client cert to "
            + "use if any.")
    private String mSsoCert = null;

    @Option(name = "fetch-signed-build", description = "whether or not to fetch a signed build")
    private boolean mFetchSigned = false;

    @Option(name = "fetch-artifact-arg", description = "Extra arguments to pass through to " +
            "fetch_artifact script, which is a tool used for downloading build artifacts.")
    private List<String> mFetchArtifactArgs = new LinkedList<>();

    @Option(name = "additional-files-filter", description = "Additional file(s) to retrieve. " +
            "If set, files matching one or more of these filters will be retrieved. " +
            "An exception will be thrown if no files are found matching a given pattern.")
    private Collection<String> mAdditionalFilesFilters = new ArrayList<String>();

    @Option(name = "Max timeout allowed for each artifact to be downloaded, defaults to 30 minutes."
            + "Supports abbreviated time notation such as 2h, 3m.", isTimeVal = true)
    private long mDownloadTimeout = 30 * 60 * 1000;

    /** Standard platforms */
    private static final Set<String> STANDARD_PLATFORMS = new HashSet<String>(
            Arrays.asList("linux", "mac"));

    /**
     * Semaphore to control the number of concurrent download that can occur on the host as defined
     * by {@link IHostOptions#getConcurrentDownloadLimit()}.
     */
    private static Semaphore sDownloadLock = null;

    /**
     * This serves both as an indication of whether the download lock should be used, and as an
     * indicator of whether or not the download lock has been initialized -- if this is variable is
     * true and {@code sDownloadLock} is {@code null}, then it has not yet been initialized.
     */
    private static Boolean sShouldCheckDownloadLock = true;

    public void setAdditionalFilesFilters(Collection<String> additionalFilesFilters) {
        mAdditionalFilesFilters = additionalFilesFilters;
    }

    public void addFileFilter(String filter) {
        mAdditionalFilesFilters.add(filter);
    }

    /**
     * Set the Launch Control hostname to use in query
     */
    public void setLcHostname(String lcHostname) {
      this.mLcHostname = lcHostname;
    }

    /**
     * Get the Launch Control hostname to use in query
     */
    public String getLcHostname() {
      return mLcHostname;
    }

    /**
     * Set the Launch Control protocol to use in query
     * @param lcProtocol
     */
    public void setLcProtocol(String lcProtocol) {
        mLcProtocol = lcProtocol;
    }

    /**
     * Get the Launch Control protocol to use in query
     */
    public String getLcProtocol() {
        return mLcProtocol;
    }

    public boolean shouldFetchSigned() {
        return mFetchSigned;
    }

    public void setFetchSigned(boolean fetchSigned) {
        mFetchSigned = fetchSigned;
    }

    /**
     * Set if sso client should be used
     */
    public void setUseSsoClient(boolean useSsoClient) {
        mUseSsoClient = useSsoClient;
    }

    /**
     * Get if sso client should be used
     */
    public boolean getUseSsoClient() {
        return mUseSsoClient;
    }

    /**
     * Set the path to the build artifacts archive
     * @param buildArchivePath
     */
    public void setBuildArchivePath(String buildArchivePath) {
      this.mBuildArchivePath = buildArchivePath;
    }

    /**
     * Get the path to the build artifacts archive
     */
    public String getBuildArchivePath() {
      return mBuildArchivePath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInvocationContext(IInvocationContext invocationContext) {
        mTestTag = invocationContext.getTestTag();
    }

    /**
     * Set the test-tag to be used.
     */
    public void setTestTag(String tag) {
        mTestTag = tag;
    }

    /**
     * Get the test tag to use in query
     */
    public String getTestTag() {
        return mTestTag;
    }

    /**
     * Set the build id to query
     * @param buildId
     */
    public void setBuildId(String buildId) {
        mBuildId = buildId;
    }

    /**
     * Get the build id to query
     */
    public String getBuildId() {
        return mBuildId;
    }

    /**
     * Set the branch to query
     * @param branch
     */
    public void setBranch(String branch) {
        mBranch = branch;
    }

    /**
     * Get the currently specified branch to query
     * @return the branch
     */
    public String getBranch() {
        return mBranch;
    }

    /**
     * Set the build-flavor to query
     * @param flavor
     */
    public void setBuildFlavor(String flavor) {
        mBuildFlavor = flavor;
    }

    /**
     * Get the build-flavor to query
     */
    public String getBuildFlavor() {
        return mBuildFlavor;
    }

    /**
     * Set the query type to be used
     * @param queryType
     */
    public void setQueryType(QueryType queryType) {
        mQueryType = queryType;
    }

    /**
     * Get the query type to be used
     */
    public QueryType getQueryType() {
        return mQueryType;
    }

    public String getBuildOs() {
        return mOsPlatform;
    }

    public void setBuildOs(String os) {
        mOsPlatform = os;
    }

    /**
     * Set the min build id to use.
     */
    public void setMinBuildId(int minBuildId) {
        mMinBuildId = minBuildId;
    }

    public boolean shouldUseBigStore() {
        return mUseBigStore;
    }

    public void setUseBigStore(boolean useBigStore) {
        mUseBigStore = useBigStore;
    }

    public File getBuildBlacklist() {
        return mBuildBlacklist;
    }

    public void setBuildBlacklist(File blacklistFile) {
        mBuildBlacklist = blacklistFile;
    }

    /**
     * Get number of iterations to run.
     * @return number of iterations
     */
    public Integer getIterations() {
        return mIterations;
    }

    /**
     * Sets the number of iterations to run
     */
    public void setIterations(Integer mIterations) {
        this.mIterations = mIterations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        if (mBuildFlavor == null) {
            throw new BuildRetrievalError("Failed to specify build-flavor");
        }
        try {
            takeDownloadPermit();
            final RemoteBuildInfo remoteBuild = getRemoteBuild();

            if (remoteBuild != null) {
                return fetchRemoteBuild(remoteBuild);
            }
            return null;
        } catch (BuildRetrievalError e) {
            // add additional attributes describing the type of build
            IBuildInfo buildInfo = e.getBuildInfo();
            if (buildInfo != null) {
                buildInfo.setBuildFlavor(mBuildFlavor);
                buildInfo.setBuildBranch(mBranch);
                insertTargetAttribute(buildInfo);
            }
            throw e;
        } finally {
            returnDownloadPermit();
        }
    }

    /**
     * Request permission to download. If the number of concurrent downloads is limited, this will
     * wait in line in order to remain under the download limit count.
     */
    private void takeDownloadPermit() {
        synchronized (getCheckDownloadLock()) {
            if (!getCheckDownloadLock()) {
                return;
            }

            if (getDownloadLock() == null) {
                IHostOptions hostOptions = getHostOptions();
                Integer concurrentDownloadLimit = null;
                if (hostOptions.getConcurrentDownloadLimit() != null) {
                    CLog.i(
                            "using host-wide download limit %d",
                            hostOptions.getConcurrentDownloadLimit());
                    concurrentDownloadLimit = hostOptions.getConcurrentDownloadLimit();
                }

                if (concurrentDownloadLimit == null) {
                    setCheckDownloadLock();
                    CLog.d("No download concurrency limit set on this host.");
                    return;
                }

                if (getDownloadLock() == null) {
                    createDownloadLock(concurrentDownloadLimit);
                }
            }
        }

        try {
            long startTime = System.currentTimeMillis();
            getDownloadLock().acquire();
            CLog.d(
                    "Got download permit after %s",
                    TimeUtil.formatElapsedTime(System.currentTimeMillis() - startTime));
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while acquiring the download lock.", e);
        }
    }

    /** Returns the current download lock. */
    @VisibleForTesting
    Semaphore getDownloadLock() {
        return sDownloadLock;
    }

    /** Create the download lock. */
    @VisibleForTesting
    void createDownloadLock(int concurrentDownloadLimit) {
        sDownloadLock = new Semaphore(concurrentDownloadLimit, true /* fair */);
    }

    /** Returns the boolean object to check or not the download lock. */
    @VisibleForTesting
    Boolean getCheckDownloadLock() {
        return sShouldCheckDownloadLock;
    }

    /** Sets the boolean object to check the download lock to false. */
    @VisibleForTesting
    void setCheckDownloadLock() {
        synchronized (sShouldCheckDownloadLock) {
            sShouldCheckDownloadLock = false;
        }
    }

    /** Restore a download permit that we acquired previously */
    private void returnDownloadPermit() {
        if (getDownloadLock() != null) {
            getDownloadLock().release();
        }
    }

    /**
     * Gets the {@link IHostOptions} instance to use.
     *
     * <p>Exposed for unit testing
     */
    @VisibleForTesting
    IHostOptions getHostOptions() {
        return GlobalConfiguration.getInstance().getHostOptions();
    }

    /**
     * Get the launch control response without downloading all the files.
     *
     * <p>Made public so specializations outside of this package can use it directly. Normally
     * {@link #getBuild()} should be used instead.
     *
     * @return the {@link RemoteBuildInfo} response from launch control
     * @throws BuildRetrievalError if build info failed to be retrieved due to an unexpected error
     */
    public RemoteBuildInfo getRemoteBuild() throws BuildRetrievalError {
        final RemoteBuildInfo remoteBuild = queryForBuild();
        if (remoteBuild != null) {
            try {
                if (mBuildId == null && Integer.valueOf(remoteBuild.getBuildId()) < mMinBuildId) {
                    // Assume that minBuildId is a valid build, and reject any build lower than that
                    // except that when an explicit build id is specified
                    return null;
                }
            } catch (NumberFormatException e) {
                Log.w(LOG_TAG, String.format(
                        "Remote build %s was not an integer, so not bounding it with min build %d",
                        remoteBuild.getBuildId(), mMinBuildId));
            }
            if (isBlacklistedBuild(remoteBuild.getBuildId(), getBuildFlavor())) {
                CLog.i("Ignoring blacklisted build %s %s", remoteBuild.getBuildId(),
                        getBuildFlavor());
                return null;
            }
            if (shouldFetchSigned()) {
                convertBuildToSigned(remoteBuild);
            }
            return remoteBuild;
        }
        return null;
    }

    /**
     * Checks whether the specified build should be returned or should be silently dropped as DoA
     */
    boolean isBlacklistedBuild(String buildId, String flavor) {
        if (mBuildBlacklist != null) {
            try {
                return BuildBlacklistFactory.getInstance()
                        .getBlacklist(mBuildBlacklist)
                        .isBlacklistedBuild(buildId, flavor);
            } catch (IOException e) {
                CLog.e("Error parsing build blacklist %s", mBuildBlacklist.getAbsolutePath());
                CLog.e(e);
            }
        }
        return false;
    }

    /**
     * Modify parameters in this {@link RemoteBuildInfo} to describe a signed build.
     * @param remoteBuild the {@link RemoteBuildInfo} to modify
     * @throws BuildRetrievalError
     */
    protected void convertBuildToSigned(RemoteBuildInfo remoteBuild)
            throws BuildRetrievalError {
        throw new UnsupportedOperationException(
                "LaunchControlProvider does not implement convertBuildToSigned");
    }

    protected void replaceParamWithSigned(RemoteBuildInfo buildInfo, BuildAttributeKey key)
            throws BuildRetrievalError {
        buildInfo.addAttribute(key, createSignedParameter(buildInfo.getAttribute(key)));
    }

    /**
     * Modify a single parameter to represent a signed parameter
     * @param unsignedParam the initial parameter
     * @return the unsigned parameter
     * @throws BuildRetrievalError if the parameter is malformed
     */
    protected String createSignedParameter(String unsignedParam) throws BuildRetrievalError{
        // Expected format: <build name>/<build id>/<filename>
        // Failure to conform to this format on a basic level will cause a BuildRetrievalError
        // to be thrown
        String delim = "/";
        String[] paramSegments = unsignedParam.split(delim);
        if (paramSegments.length != 3) {
            CLog.e("Unexpected signed build parameter: %s", unsignedParam);
            throw new BuildRetrievalError("Malformatted build parameter for signed build");
        }
        return ArrayUtil.join(delim,
                paramSegments[0],
                paramSegments[1],
                "signed",
                "signed-" + paramSegments[2]);
    }

    /**
     * Convert the launch control response into a local {@link IBuildInfo} containing all the
     * necessary files.
     * <p/>
     * Made public so specializations outside of this package can use it directly. Normally
     * {@link #getBuild()} should be used instead.
     *
     * @param remoteBuild the launch control server response
     * @return the {@link IBuildInfo} populated with local versions of build files
     * @throws BuildRetrievalError if build files could not be downloaded
     */
    public IBuildInfo fetchRemoteBuild(RemoteBuildInfo remoteBuild) throws BuildRetrievalError {
        try {
            IFileDownloader downloader = createLaunchControlDownloader();
            IBuildInfo localBuild = downloadBuildFiles(
                    remoteBuild, mTestTag, createBuildName(), downloader);
            downloadAdditionalFiles(remoteBuild, downloader, localBuild);
            localBuild.setBuildFlavor(mBuildFlavor);
            localBuild.setBuildBranch(mBranch);
            String buildAlias = remoteBuild.getAttribute(BuildAttributeKey.BUILD_ALIAS);
            if (buildAlias != null && !buildAlias.trim().isEmpty()) {
                // convert to upper case since that's the typical representation
                localBuild.addBuildAttribute("build_alias", buildAlias.toUpperCase());
            }
            String buildType = remoteBuild.getAttribute(BuildAttributeKey.BUILD_TYPE);
            if (buildType != null && !buildType.trim().isEmpty()) {
                localBuild.addBuildAttribute("build_type", buildType);
            }
            String buildAttemptId = remoteBuild.getAttribute(BuildAttributeKey.BUILD_ATTEMPT_ID);
            if (buildAttemptId != null && !buildAttemptId.trim().isEmpty()) {
                localBuild.addBuildAttribute("build_attempt_id", buildAttemptId.toUpperCase());
            }
            insertTargetAttribute(localBuild);
            return localBuild;
        } catch (BuildRetrievalError e) {
            // downloadBuildFiles failed, build cannot be tested
            resetTestBuild(remoteBuild.getBuildId());
            throw e;
        } catch (RuntimeException e) {
            // downloadBuildFiles failed, build cannot be tested
            resetTestBuild(remoteBuild.getBuildId());
            throw e;
        }
    }

    private void insertTargetAttribute(IBuildInfo localBuild) {
        // Insert build target into build attribute. Build Target is used by the new
        // Android Build API. (this can be different from the build flavor).
        // This is to handle special cases like fastbuild targets.
        String buildTarget = mBuildFlavor;
        if (!STANDARD_PLATFORMS.contains(mOsPlatform)) {
            buildTarget += "_" + mOsPlatform;
        }
        localBuild.addBuildAttribute("build_target", buildTarget);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void buildNotTested(IBuildInfo info) {
        // If we are running multi-iterations count, decrement build instead.
        if (mIterations != null && mIterations > 1) {
            decrementTestBuild(info.getBuildId());
        } else {
            resetTestBuild(info.getBuildId());
        }
    }

    /**
     * Performs a {@link QueryType#RESET_TEST_BUILD} query to requests launch control to remove
     * configured build from 'tested builds' set.
     * <p/>
     * Has limited error handling and retry mechanisms
     */
    public void resetTestBuild() {
        if (mBuildId == null) {
            throw new IllegalArgumentException(String.format("Missing %s option", BUILD_ID_NAME));
        }
        resetTestBuild(mBuildId);
    }

    /**
     * Performs a {@link QueryType#RESET_TEST_BUILD} query to requests launch control to remove
     * given build from 'tested builds' set.
     * <p/>
     * Has limited error handling and retry mechanisms
     *
     * @param buildId the build id to reset
     */
    public void resetTestBuild(String buildId) {
        Log.i(LOG_TAG, String.format("Resetting build %s", buildId));
        final MultiMap<String, String> params = buildParamMap(QueryType.RESET_TEST_BUILD, buildId);
        try {
            IHttpHelper helper = createHttpHelper();
            helper.doGetIgnore(helper.buildUrl(getUrl(), params));
        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Reset test build failed for %s", buildId));
            Log.e(LOG_TAG, e);
        }
    }

    public void decrementTestBuild(String buildId) {
        Log.i(LOG_TAG, String.format("Decrementing build %s", buildId));
        MultiMap<String, String> params = buildParamMap(QueryType.DECREMENT_TEST_COUNT, buildId);
        try {
            IHttpHelper helper = createHttpHelper();
            helper.doGetIgnore(helper.buildUrl(getUrl(), params));
        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Decrement test build failed for %s", buildId));
            Log.e(LOG_TAG, e);
        }
    }

    /**
     * Factory method for creating a {@link HttpHelper}.
     * <p/>
     * Exposed so it can be mocked for unit testing.
     */
    protected IHttpHelper createHttpHelper() {
        IHttpHelper helper = null;
        if (mUseSsoClient) {
            helper = new SsoClientHttpHelper(mSsoBinary, mSsoCert);

        } else {
            helper = new HttpHelper();
        }
        helper.setOpTimeout(mQueryTimeoutSec * 1000);
        helper.setMaxTime(mQueryRetryMin * 60 * 1000);
        return helper;
    }

    /**
     * Creates the map of request parameters to provide to launch control.
     *
     * @return a {@link Map} of key-value parameter pairs
     * @throws IllegalArgumentException if any of the required parameter values have not been
     * provided, or the type of query provided is not recognized.
     */
    private MultiMap<String, String> buildParamMap() {
        // if build id is specified, do a NOTIFY_TEST_BUILD query
        QueryType query = mBuildId == null ? mQueryType : QueryType.NOTIFY_TEST_BUILD;
        return buildParamMap(query, mBuildId);
    }

    /**
     * Creates the map of request parameters to provide to launch control.
     *
     * @param queryEnum the {@link QueryType} to include
     * @param buildId the optional build id. If <code>null</code> will be excluded.
     * @return a {@link Map} of key-value parameter pairs
     * @throws IllegalArgumentException if any of the required parameter values have not been
     * provided,
     */
    private MultiMap<String, String> buildParamMap(QueryType queryEnum, String buildId) {
        if (mTestTag == null) {
            throw createMissingOptionException(TEST_TAG_NAME);
        }
        if (mBranch == null) {
            throw createMissingOptionException(BRANCH_NAME);
        }
        if (mBuildFlavor == null) {
            throw createMissingOptionException(BUILD_FLAVOR_NAME);
        }
        MultiMap<String, String> paramMap = new MultiMap<String, String>();
        paramMap.put(QUERY_TYPE_PARAM, queryEnum.getRemoteValue());
        paramMap.put(TEST_TAG_PARAM, mTestTag);
        paramMap.put(BUILD_NAME_PARAM, createBuildName());
        paramMap.put(MIN_BUILD_ID_PARAM, mMinBuildId.toString());
        if (buildId != null) {
            paramMap.put(BUILD_ID_PARAM, buildId);
        }
        if (mIterations != null && mIterations > 1) {
            String iterations = mIterations.toString();
            paramMap.put(ITERATIONS_NAME, iterations);
        }
        if (mFallback != null) {
            paramMap.put(FALLBACK_NAME, String.valueOf(mFallback));
        }
        if (mWhitelist != null) {
            paramMap.put(WHITELIST_PARAM, mWhitelist);
        }
        if (mQueryWindow != null) {
            paramMap.put(QUERY_WINDOW_PARAM, mQueryWindow);
        }
        return paramMap;
    }

    /**
     * Construct the name of the build as defined by launch control.
     *
     * @return {@link String} name of build
     */
    private String createBuildName() {
        return String.format("%s-%s-%s", mBranch, mOsPlatform, mBuildFlavor);
    }

    private IllegalArgumentException createMissingOptionException(String optionName) {
        return new IllegalArgumentException(String.format("Missing required option %s",
                optionName));
    }

    /**
     * Perform the launch control query, retrying multiple times in case of error.
     *
     * @return the {@link RemoteBuildInfo} retrieved from launch control
     */
    protected RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
        IHttpHelper helper = createHttpHelper();
        final MultiMap<String, String> params = buildParamMap();
        return queryForBuild(helper, getUrl(), params);
    }

    /**
     * Perform the launch control query, retrying multiple times in case of error.
     *
     * @param params parameters for launch control
     * @return the {@link RemoteBuildInfo} retrieved from launch control
     */
    protected RemoteBuildInfo queryForBuild(MultiMap<String, String> params)
            throws BuildRetrievalError {
        IHttpHelper helper = createHttpHelper();
        return queryForBuild(helper, getUrl(), params);
    }

    /**
     * Perform the launch control query, retrying multiple times in case of error.
     *
     * @param helper the {@link HttpHelper} to use to make query.
     * @param lcRequestUrl the complete url with parameter string used to retrieve build.
     * @return the {@link RemoteBuildInfo}
     */
    private RemoteBuildInfo queryForBuild(IHttpHelper helper, String lcRequestUrl,
            MultiMap<String, String> params) throws BuildRetrievalError {
        Log.d(LOG_TAG, String.format("Starting launch control query %s", lcRequestUrl));
        try {
            String response = helper.doGetWithRetry(helper.buildUrl(lcRequestUrl, params));
            Log.i(LOG_TAG, "launch control query response: " + response);
            return RemoteBuildInfo.parseRemoteBuildInfo(response);
        } catch (IOException e) {
            Log.i(LOG_TAG, String.format("IOException %s when contacting launch control",
                    e.getMessage()));
            throw new BuildRetrievalError("Failed to query build", e);
        } catch (DataSizeException e) {
            Log.i(LOG_TAG, "Unexpected oversized response when contacting launch control");
            throw new BuildRetrievalError("Failed to query build", e);
        } catch (InvalidResponseException e) {
            Log.i(LOG_TAG, "Invalid response when contacting launch control");
            throw new BuildRetrievalError("Failed to query build", e);
        } catch (RuntimeException e) {
            throw new BuildRetrievalError("Failed to query build", e);
        }
    }

    /**
     * Get {@link IRunUtil} to use.
     * <p/>
     * Exposed so unit tests can mock.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Download the necessary files specified in the <var>remoteBuild</var>, and store them
     * in a {@link IBuildInfo}.
     *
     * @param remoteBuild the launch control server response
     * @param testTargetName the test target name
     * @param buildName the name of the build, as defined by launch control
     * @param downloader the {@link IFileDownloader} to use to download build files
     * @return the {@link IBuildInfo} populated with local versions of build files
     * @throws BuildRetrievalError if build files could not be downloaded
     */
    protected abstract IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild,
            String testTargetName, String buildName, IFileDownloader downloader)
            throws BuildRetrievalError;

    protected void downloadAdditionalFiles(RemoteBuildInfo remoteBuild, IFileDownloader downloader,
            IBuildInfo localBuild) throws BuildRetrievalError {
        // download any additional files; expect comma-separated file paths.
        final String filesPathCSVString = remoteBuild.getAttribute(BuildAttributeKey.FILES);
        if (filesPathCSVString == null) {
            return;
        }
        String[] filePaths;
        if (filesPathCSVString.isEmpty()) {
            filePaths = new String[] {};
        } else {
            filePaths = filesPathCSVString.split(",");
        }
        for (String pattern : mAdditionalFilesFilters) {
            boolean foundMatch = false;
            for (String filePath : filePaths) {
                if (filePath.matches(pattern)) {
                    String label = new File(filePath).getName();
                    CLog.d("label for %s is %s",filePath, label);
                    localBuild.setFile(label, downloader.downloadFile(filePath),
                            remoteBuild.getBuildId());
                    foundMatch = true;
                }
            }
            if (!foundMatch) {
                throw new BuildRetrievalError(String.format(
                        "Could not find file matching pattern '%s'", pattern));
            }
        }
    }

    /**
     * Factory method for creating a downloader to use for launch control files.
     */
    protected IFileDownloader createLaunchControlDownloader() {
        if (mBuildArchivePath == null || mBuildArchivePath.isEmpty()) {
            throw createMissingOptionException(BUILD_ARCHIVE_PATH);
        }
        BigStoreFileDownloader bigstore = new BigStoreFileDownloader();
        bigstore.setUseKrb(mUseKrbForBigstore);
        bigstore.setFetchArtifactArgs(mFetchArtifactArgs);
        bigstore.setDownloadTimeout(mDownloadTimeout);
        return new FileDownloadCacheWrapper(mDownloadCacheDir, bigstore);
    }

    /**
     * Remove the file from the cache in case it is corrupted.
     *
     * @param remoteFilePath Same remote file as specified in
     *        {@link IFileDownloader#downloadFile(String)}.
     */
    protected void deleteCacheEntry(String remoteFilePath) {
        FileDownloadCache cache =
                FileDownloadCacheFactory.getInstance().getCache(mDownloadCacheDir);
        cache.deleteCacheEntry(remoteFilePath);
    }

    /**
     * The URL of the build server to talk to.
     * Exposed for mocking from unit tests.
     */
    String getUrl() {
        if (mLcHostname == null || mLcHostname.isEmpty()) {
            throw createMissingOptionException(HOSTNAME);
        }
        return String.format(LC_BASE_URL, mLcProtocol, mLcHostname);
    }
}
