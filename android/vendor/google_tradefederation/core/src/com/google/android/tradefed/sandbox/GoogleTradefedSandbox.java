// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.sandbox;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.sandbox.TradefedSandbox;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil2;

import com.google.android.tradefed.build.LaunchControlProvider;
import com.google.android.tradefed.build.TfLaunchControlProvider;
import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Internal specialization of {@link TradefedSandbox} to be used in the lab infrastructure. This
 * address the download need of the proper version associated with the build. We might be able to
 * move this to AOSP if one day we can rely on AOSP API to download builds.
 *
 * <p>TODO: Need a mechanism to still report something to ATP invocation
 */
public class GoogleTradefedSandbox extends TradefedSandbox {

    private static final String BUILD_ID_ARG = "--build-id";
    private static final String BUILD_BRANCH_ARG = "--branch";
    private static final String TEST_TAG_ARG = "--test-tag";
    private static final Map<String, Entry<String, String>> BRANCH_FLAVOR_LOOKUP = new HashMap<>();
    private static final Map<String, String> EXTRA_TARGETS = new HashMap<>();
    // TODO: Replace by a configuration file or something similar if possible
    static {
        // TODO: specify the branch where we want versioning to be supported.
        BRANCH_FLAVOR_LOOKUP.put(
                "git_master", createEntry("test_suites_arm64", "fastbuild3d_linux"));

        EXTRA_TARGETS.put("lab", "general-tests.zip");
    }

    /** Convenience helper to create the entries. */
    private static SimpleImmutableEntry<String, String> createEntry(String target, String os) {
        return new AbstractMap.SimpleImmutableEntry<>(target, os);
    }

    private File mWorkingDir = null;
    private IFolderBuildInfo mDownloadedBuild = null;

    @Override
    public File getTradefedEnvironment(String[] args) throws ConfigurationException {
        if (mWorkingDir != null) {
            return mWorkingDir;
        }

        try {
            mWorkingDir = FileUtil.createTempDir("tmp-working-dir");
            String buildId = extractArgValue(BUILD_ID_ARG, args);
            String branch = extractArgValue(BUILD_BRANCH_ARG, args);
            String testTag = extractArgValue(TEST_TAG_ARG, args);
            // TODO: check values, and ensure host-wide args (download-limit)
            TfLaunchControlProvider provider = createBuildProvider();
            provider.setBuildId(buildId);
            provider.setBranch(branch);
            provider.setBuildFlavor(BRANCH_FLAVOR_LOOKUP.get(branch).getKey());
            provider.setBuildOs(BRANCH_FLAVOR_LOOKUP.get(branch).getValue());
            provider.setTestTag(testTag);
            // Add possible additional targets
            addAdditionalDownloadTargets(args, provider);

            mDownloadedBuild = (IFolderBuildInfo) provider.getBuild();
            if (mDownloadedBuild == null) {
                throw new ConfigurationException("Failed to download the versioned Tradefed build");
            }
            FileUtil.recursiveCopy(mDownloadedBuild.getRootDir(), mWorkingDir);
            unpackExtraTargets(mWorkingDir, mDownloadedBuild);
        } catch (RuntimeException | IOException | BuildRetrievalError e) {
            FileUtil.recursiveDelete(mWorkingDir);
            CLog.e(e);
            throw new ConfigurationException("failed to prepare environment", e);
        }
        CLog.logAndDisplay(
                LogLevel.DEBUG, "Content of working dir: %s", Arrays.asList(mWorkingDir.list()));
        return mWorkingDir;
    }

    @Override
    protected File prepareContext(IInvocationContext context) throws IOException {
        // We copy the artifact from the extra build targets to the test dir to be picked up.
        IBuildInfo primaryBuild = context.getBuildInfos().get(0);
        if (primaryBuild instanceof IDeviceBuildInfo) {
            unpackExtraTargets(((IDeviceBuildInfo) primaryBuild).getTestsDir(), mDownloadedBuild);
        }

        return super.prepareContext(context);
    }

    @Override
    public void tearDown() {
        try {
            super.tearDown();
        } finally {
            FileUtil.recursiveDelete(mWorkingDir);
            if (mDownloadedBuild != null) {
                mDownloadedBuild.cleanUp();
            }
        }
    }

    @VisibleForTesting
    TfLaunchControlProvider createBuildProvider() {
        return new TfLaunchControlProvider();
    }

    /** Extract the value of a given arg key. --build-id 888 will extract 888. */
    private String extractArgValue(String key, String[] args) throws ConfigurationException {
        for (int i = 0; i < args.length; i++) {
            if (key.equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new ConfigurationException(
                            String.format("Could not find proper value for %s", key));
                }
                return args[i + 1];
            }
        }
        throw new ConfigurationException(String.format("Could not find key %s in args.", key));
    }

    /** Attempt to make an educated guess on extra needed artifacts. */
    private void addAdditionalDownloadTargets(String[] args, LaunchControlProvider provider) {
        CLog.logAndDisplay(LogLevel.DEBUG, "args:%s", Arrays.asList(args));
        for (Entry<String, String> possibleTarget : EXTRA_TARGETS.entrySet()) {
            if (args[0].contains(possibleTarget.getKey())) {
                CLog.logAndDisplay(
                        LogLevel.DEBUG, "Downloading extra: %s", possibleTarget.getKey());
                provider.addFileFilter(".*" + possibleTarget.getValue());
            }
        }
    }

    /** If the extra targets are zip file, unpack them in the working dir. */
    private void unpackExtraTargets(File workingDir, IFolderBuildInfo info) throws IOException {
        for (String extraTargets : EXTRA_TARGETS.values()) {
            CLog.logAndDisplay(
                    LogLevel.DEBUG, "extra:%s - %s", extraTargets, info.getFile(extraTargets));
            if (info.getFile(extraTargets) != null && extraTargets.endsWith(".zip")) {
                CLog.logAndDisplay(LogLevel.DEBUG, "extracting %s", extraTargets);
                ZipUtil2.extractZip(info.getFile(extraTargets), workingDir);
            }
        }
    }
}
