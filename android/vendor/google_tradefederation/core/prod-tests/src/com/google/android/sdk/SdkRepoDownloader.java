// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.sdk;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.build.SdkBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.config.OptionUpdateRule;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.RetentionFileSaver;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.google.android.tradefed.build.LaunchControlProvider;
import com.google.android.tradefed.build.QueryType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A specialized {@link LaunchControlProvider} that creates a SDK repository on local disk.
 * <p/>
 * Queries launch control for a SDK platform to test, and/or a SDK tools to test. Then uses the
 * 'mk_sdk_release.py' script (which must exist on filesystem) to create the SDK repository.
 */
public class SdkRepoDownloader implements IBuildProvider {

    @Option(name = "dest-path", description = "root file path to store sdk repo files. " +
            "Default is $user.home/www/sdk_repo")
    private File mRootDir = null;

    @Option(name = "dest-url", description = "Optional root http url where sdk repo files " +
            "placed in dest-path will be accessible. Default is http://www/~$user/sdk_repo")
    private String mRootUrl = null;

    @Option(name = "mk-sdk-path", description = "path to the mk_sdk_release.py script.",
            mandatory = true)
    private String mMkReleaseSdkPath = "mk_sdk_release.py";

    @Option(name = "platform-branch", description = "the branch of platform build to retrieve. " +
            "If unspecified, a platform build will not be added to repo",
            importance = Importance.IF_UNSET)
    private String mPlatformBranch = null;

    @Option(name = "platform-build-id", description =
        "the id of platform build. If unspecified will get latest build on platform-branch.")
    private String mPlatformBuildId = null;

    @Option(name = "platform-query-type", description =
        "the launch control query type to perform for platform build.")
    private QueryType mPlatformQueryType = QueryType.QUERY_LATEST_BUILD;

    @Option(name = "platform-min-build-id", description =
            "the minimum build id to accept for platform build queries.",
            updateRule = OptionUpdateRule.GREATEST)
    private Integer mPlatformMinBuildId = -1;

    @Option(name = "tools-branch", description = "the branch of tools build to retrieve. " +
            "If unspecified, a tools build will not be added to repo",
            importance = Importance.IF_UNSET)
    private String mToolsBranch = null;

    @Option(name = "tools-preview", description = "whether the build is a preview build." +
            "default to a release build")
    private Boolean mToolsPreview=false;

    @Option(name = "tools-build-id", description =
        "the id of tools build. If unspecified will get latest build on tools-branch.")
    private String mToolsBuildId = null;

    @Option(name = "tools-query-type", description =
        "the launch control query type to perform for tools build.")
    private QueryType mToolsQueryType = QueryType.QUERY_LATEST_BUILD;

    @Option(name = "tools-min-build-id", description =
            "the minimum build id to accept for tools build queries.",
            updateRule = OptionUpdateRule.GREATEST)
    private Integer mToolsMinBuildId = -1;

    @Option(name = "use-platform-build-id", description =
            "flag to specify that platform build id and branch should be used for the tools " +
            "build query. If set, tools-branch, tools-build-id, and tools-query-type will be " +
            "ignored.")
    private boolean mUsePlatformBuildForTools = false;

    @Option(name = "mk-sdk-timeout", description =
            "the maximum time to wait for mk_sdk_release.py to complete in seconds.")
    private long mMkSdkTimeout = 30 * 60;

    @Option(name = "sdk-os", description =
            "optional parameter to limit SDK repo files for a single OS.")
    private Collection<SdkOs> mSdkOsList = new ArrayList<SdkOs>();

    @Option(name = "old-ns", description =
            "NS rev# (3..) to generate old repository.xml for upgrades.")
    private Integer mOldNs = null;

    @Option (name = "xsd-dir", description = "filesystem path to the repository xsd files." +
            "Default /auto/android-build/google3/googledata/download/android/repository")
    private String mXsdDirPath = null;

    @Option (name = "repo", description = "optional filesystem path to the repository xml files.")
    private String mRepoPath = null;

    @Option (name = "platform-components", description = "flag to control if additional platform" +
            "components (addon, doc, support, sample) should be added to repo. Will have no " +
            "effect if --platform-branch is unspecified.")
    private boolean mPlatformComponents = false;

    @Option (name = "retention-days", description = "number of days to keep repo folder before " +
            "deleting.")
    private int mRetentionDays = 3;

    private static final String TEST_TAG_NAME = "test-tag";
    @Option(name = TEST_TAG_NAME, description = "unique identifier to provide to launch control.",
            importance = Importance.IF_UNSET, mandatory = true)
    // used to track which builds have been tested when querying for new builds to test
    private String mTestTag = null;

    private static final String TOOLS_TEMPLATE =
            "tool:\n" +
            "   build:  $tool_branch @ $tool_build\n" +
            "build-tool:\n" +
            "   build:  $tool_branch @ $tool_build\n" +
            "adt:\n" +
            "   build:  $tool_branch @ $tool_build\n" +
            "   $preview";

    private static final String PLATFORM_TEMPLATE =
            "platform:\n" +
            "   build:  $platform_branch @ $platform_build\n" +
            "platform-tool:\n" +
            "   build:  $platform_branch @ $platform_build\n" +
            "si-arm:\n" +
            "   build:  $platform_branch @ $platform_build\n" +
            "si-x86:\n" +
            "   build:  $platform_branch @ $platform_build\n";


    private static final String PLAT_COMPONENTS_TEMPLATE =
            "addon:\n" +
            "   build:  $platform_branch @ $platform_build\n" +
            "doc:\n" +
            "   build:  $platform_branch @ $platform_build\n" +
            "support:\n" +
            "   build:  $platform_branch @ $platform_build\n" +
            "sample:\n" +
            "   build:  $platform_branch @ $platform_build\n";

    private static enum SdkOs {
        windows, linux, mac;
    }

    /**
     * Set the tools branch to query.
     */
    void setToolsBranch(String toolsBranch) {
        mToolsBranch = toolsBranch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        initRootDir();
        IBuildInfo platformBuild = null;
        IBuildInfo toolsBuild = null;
        File buildConfigFile = null;

        try {
            platformBuild = queryPlatformBuild();

            toolsBuild = queryToolsBuild(platformBuild);
            if (toolsBuild == null && platformBuild == null) {
                CLog.i("No builds are available.");
                return null;
            }

            buildConfigFile = createBuildConfig(platformBuild, toolsBuild);

            String repoBuildId = createRepoBuildId(toolsBuild, platformBuild);
            File repoFolder = createRepoDir(mRootDir, repoBuildId);

            createRepo(repoFolder, buildConfigFile);

            SdkBuildInfo sdkBuild = new SdkBuildInfo(repoBuildId, mTestTag);
            sdkBuild.addBuildAttribute("sdk_repo_dir", repoFolder.getAbsolutePath());
            if (mRootUrl != null) {
                sdkBuild.addBuildAttribute("sdk_repo_url", String.format("%s/%s", mRootUrl,
                        repoBuildId));
            }
            sdkBuild.setBuildBranch(getBranch(platformBuild, toolsBuild));
            return sdkBuild;
        } catch (IOException e) {
            resetBuilds(platformBuild, toolsBuild);
            throw new BuildRetrievalError("Failed to create SDK repo", e);
        } catch (BuildRetrievalError e) {
            resetBuilds(platformBuild, toolsBuild);
            throw e;
        } finally {
            FileUtil.deleteFile(buildConfigFile);
        }
    }

    /**
     * Helper method to get the first valid branch from list of builds
     */
    private String getBranch(IBuildInfo... builds) {
        for (IBuildInfo build : builds) {
            if (build != null && build.getBuildBranch() != null) {
                return build.getBuildBranch();
            }
        }
        return null;
    }

    private String createRepoBuildId(IBuildInfo toolsBuild, IBuildInfo platformBuild) {
        StringBuilder idBuilder = new StringBuilder();
        if (toolsBuild != null) {
            idBuilder.append("tools_");
            idBuilder.append(getBuildAlias(toolsBuild));
        }
        if (platformBuild != null) {
            if (idBuilder.length() > 0) {
                idBuilder.append("_");
            }
            idBuilder.append("platform_");
            idBuilder.append(getBuildAlias(platformBuild));
        }
        return idBuilder.toString();
    }

    /**
     * Initializes values for mRootDir and mRootUrl if unset.
     * @throws BuildRetrievalError
     */
    private void initRootDir() throws BuildRetrievalError {
        if (mRootDir == null) {
            String userHome = System.getProperty("user.home");
            if (userHome == null) {
                throw new BuildRetrievalError("dest-path was unspecified and cannot be set.");
            }
            mRootDir = FileUtil.getFileForPath(new File(userHome), "www", "sdk_repo");
            if (mRootUrl == null) {
                String user = System.getProperty("user.name");
                if (user != null) {
                    mRootUrl = String.format("http://www/~%s/sdk_repo/", user);
                }
            }
        }
    }

    /**
     * Query launch control for a platform SDK build.
     */
    private IBuildInfo queryPlatformBuild() throws BuildRetrievalError {
        if (mPlatformBranch == null) {
            return null;
        }
        try {
            EmptySdkLaunchControlProvider platformProvider = createLcProvider();
            OptionCopier.copyOptions(this, platformProvider);
            platformProvider.setBranch(mPlatformBranch);
            platformProvider.setBuildId(mPlatformBuildId);
            platformProvider.setQueryType(mPlatformQueryType);
            platformProvider.setMinBuildId(mPlatformMinBuildId);
            return platformProvider.getBuild();
        } catch (ConfigurationException e) {
            throw new BuildRetrievalError("Failed to copy options", e);
        }
    }

    /**
     * Query launch control for a tools SDK build.
     */
    private IBuildInfo queryToolsBuild(IBuildInfo platformBuild) throws BuildRetrievalError {
        if (mUsePlatformBuildForTools) {
            return platformBuild;
        } else if (mToolsBranch == null) {
            return null;
        } else {
            try {
                EmptySdkLaunchControlProvider toolsProvider = createLcProvider();
                OptionCopier.copyOptions(this, toolsProvider);
                toolsProvider.setBranch(mToolsBranch);
                toolsProvider.setBuildId(mToolsBuildId);
                toolsProvider.setQueryType(mToolsQueryType);
                toolsProvider.setMinBuildId(mToolsMinBuildId);
                return toolsProvider.getBuild();
            } catch (ConfigurationException e) {
                throw new BuildRetrievalError("Failed to copy options", e);
            }
        }
    }

    EmptySdkLaunchControlProvider createLcProvider() {
        return new EmptySdkLaunchControlProvider();
    }

    /**
     * Creates a temporary build.config file specifying the builds to use for the
     * various SDK components.
     *
     * @param platformBuild
     * @param toolsBuild
     * @return a build.config {@link File}
     * @throws IOException
     */
    private File createBuildConfig(IBuildInfo platformBuild, IBuildInfo toolsBuild)
            throws IOException {
        StringBuilder configBuilder = new StringBuilder();

        if (toolsBuild != null) {
            String toolsString = TOOLS_TEMPLATE;
            toolsString = toolsString.replace("$tool_branch", toolsBuild.getBuildBranch());
            toolsString = toolsString.replace("$tool_build", toolsBuild.getBuildId());
            toolsString = toolsString.replace("$preview", mToolsPreview ? "preview: 1\n" : "");
            configBuilder.append(toolsString);
        }
        if (platformBuild != null) {
            String platString = PLATFORM_TEMPLATE;
            platString = platString.replace("$platform_branch",
                    platformBuild.getBuildBranch());
            platString = platString.replace("$platform_build",
                    platformBuild.getBuildId());
            configBuilder.append(platString);
            if (mPlatformComponents) {
                String compString = PLAT_COMPONENTS_TEMPLATE;
                compString = compString.replace("$platform_branch",
                        platformBuild.getBuildBranch());
                compString = compString.replace("$platform_build",
                        platformBuild.getBuildId());
                configBuilder.append(compString);
            }
        }
        String buildConfigString = configBuilder.toString();
        CLog.i("Build config: \n%s", buildConfigString);
        File buildConfig = FileUtil.createTempFile("build_", ".config");
        FileUtil.writeToFile(buildConfigString, buildConfig);
        return buildConfig;
    }

    /**
     * Creates a SDK repository using the <var>mMkReleaseSdkPath</var> script.
     * <p/>
     * Exposed for unit testing.
     */
    void createRepo(File repoFolder, File buildConfigFile) throws BuildRetrievalError {
        IRunUtil runUtil = RunUtil.getDefault();
        List<String> params = new ArrayList<String>();
        params.add(mMkReleaseSdkPath);
        params.add("--timeout");
        params.add(Long.toString(mMkSdkTimeout));
        params.add("--keep");
        params.add("--skip-license-check");
        if (mSdkOsList != null && !mSdkOsList.isEmpty()) {
            params.add("--os");
            params.add(ArrayUtil.join(",", mSdkOsList));
        }
        if (mOldNs != null) {
            params.add("--old-ns");
            params.add(mOldNs.toString());
        }
        if (mXsdDirPath != null) {
            params.add("--xsd-dir");
            params.add(mXsdDirPath);
        }
        if (mRepoPath != null) {
            params.add("--repo");
            params.add(mRepoPath);
        }
        params.add(buildConfigFile.getAbsolutePath());
        params.add(repoFolder.getAbsolutePath());

        CommandResult r = runUtil.runTimedCmd(mMkSdkTimeout * 1000, params.toArray(
                new String[] {}));
        if (!r.getStatus().equals(CommandStatus.SUCCESS)) {
            CLog.e("%s failed. stdout: %s, stderr: %s", mMkReleaseSdkPath, r.getStdout(),
                    r.getStderr());
            throw new BuildRetrievalError(mMkReleaseSdkPath + " failed");
        }
        rxFiles(repoFolder);
    }

    private String getBuildAlias(IBuildInfo build) {
        String buildAlias = build.getBuildAttributes().get("build_alias");
        if (buildAlias != null && !buildAlias.isEmpty()) {
            return buildAlias;
        } else {
            return build.getBuildId();
        }
    }

    private void rxFiles(File storeDir) {
        for (File f : storeDir.listFiles()) {
            f.setReadable(true, false);
        }
    }

    private File createRepoDir(File rootDir, String dir) {
        File f = new File(rootDir, dir);
        f.mkdirs();
        f.setExecutable(true, false);
        f.setReadable(true, false);
        new RetentionFileSaver().writeRetentionFile(f, mRetentionDays);
        return f;
    }

    private void resetBuilds(IBuildInfo... builds) {
        EmptySdkLaunchControlProvider lc = createLcProvider();
        lc.setTestTag(mTestTag);
        for (IBuildInfo build : builds) {
            if (build != null) {
                lc.setBranch(build.getBuildBranch());
                lc.resetTestBuild(build.getBuildId());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void buildNotTested(IBuildInfo info) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        // ignore
    }
}
