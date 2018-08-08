// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.AppBuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A {@link LaunchControlProvider} for {@link AppBuildInfo} that downloads two unbundled apps.
 * The primary app will trigger the downloads and test runs. The primary and secondary app apks and
 * additional files will be store under the same AppBuildInfo.
 */
@OptionClass(alias = "multi-app-launch-control")
public class MultiAppLaunchControlProvider extends AppLaunchControlProvider {
    @Option(name = "secondary-app-build-flavor",
            description = "the secondary app build flavor to download.",
            importance = Importance.IF_UNSET, mandatory = true)
    private String mSecondaryAppBuildFlavor;

    @Option(name = "secondary-app-branch",
            description = "the branch of the secondary unbundled app.",
            importance = Importance.IF_UNSET, mandatory = true)
    private String mSecondaryAppBranch = null;

    @Option(name = "secondary-app-build-id", description =
            "the id of secondary unbundled app. If unspecified will get latest build.")
    private String mSecondaryAppBuildId = null;

    @Option(name = "secondary-app-query-type", description =
            "the launch control query type to perform for the secondary unbundled app.")
    private QueryType mSecondaryAppQueryType = QueryType.QUERY_LATEST_BUILD;

    @Option(name = "secondary-app-name-filter",
            description = "Optional name regex pattern filter(s) for " +
                    "apks to retrieve in the secondary app. If set, only apks matching one or " +
                    "more of these filters will be retrieved. An exception will be thrown if no " +
                    "files are found matching a given pattern.")
    private Collection<String> mSecondaryAppFilters = new ArrayList<String>();

    @Option(name = "secondary-additional-files-filter",
            description = "Additional file(s) to retrieve. " +
                    "If set, files matching one or more of these filters will be retrieved from " +
                    "the secondary unbundled app. An exception will be thrown if no files are " +
                    "found matching a given pattern.")
    private Collection<String> mSecondaryAdditionalFilesFilters = new ArrayList<String>();

    @Option(name = "secondary-optional-app-name-filter", description =
            "Similiar to secondary-app-name-filter, but no exception will be thrown " +
            "if no files are found matching a given pattern.")
    private Collection<String> mSecondaryOptionalAppFilters = new ArrayList<String>();

    public void setSecondaryAppBranch(String appBranch) {
        mSecondaryAppBranch = appBranch;
    }

    public void setSecondaryAppBuildFlavor(String buildFlavor) {
        mSecondaryAppBuildFlavor = buildFlavor;
    }

    public void setSecondaryAppQueryType(QueryType query) {
        mSecondaryAppQueryType = query;
    }

    public void setSecondaryAdditionalFilesFilters(Collection<String> additionalFilesFilters) {
        mSecondaryAdditionalFilesFilters = additionalFilesFilters;
    }

    public void setSecondaryApkFilters(Collection<String> appFilters) {
        mSecondaryAppFilters = appFilters;
    }

    public void setSecondaryOptionalApkFilters(Collection<String> optionalAppFilters) {
        mSecondaryOptionalAppFilters = optionalAppFilters;
    }

    public void addSecondaryApkFilter(String appFilter) {
        mSecondaryAppFilters.add(appFilter);
    }

    public void addSecondaryOptionalApkFilter(String optionalAppFilter) {
        mSecondaryOptionalAppFilters.add(optionalAppFilter);
    }

    public void addSecondaryFileFilter(String filter) {
        mSecondaryAdditionalFilesFilters.add(filter);
    }

    private AppLaunchControlProvider mSecondaryAppLcProvider;


    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild(ITestDevice device) throws BuildRetrievalError,
            DeviceNotAvailableException {
        setTestTagByDevice(device);
        return getBuild();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        AppBuildInfo b = (AppBuildInfo) super.getBuild();
        // If there are no primary build to test don't bother dealing with secondary app.
        if (b == null) {
            return null;
        }

        mSecondaryAppLcProvider = createAppLcProvider();
        copyOptions(mSecondaryAppLcProvider);
        // Need to set the appropriate options for secondary appLCProvider.
        mSecondaryAppLcProvider.setBranch(mSecondaryAppBranch);
        mSecondaryAppLcProvider.setBuildFlavor(mSecondaryAppBuildFlavor);
        mSecondaryAppLcProvider.setQueryType(mSecondaryAppQueryType);
        mSecondaryAppLcProvider.setMinBuildId(0);
        mSecondaryAppLcProvider.setTestTag(getTestTag());
        mSecondaryAppLcProvider.setBuildId(mSecondaryAppBuildId);
        mSecondaryAppLcProvider.setApkFilters(mSecondaryAppFilters);
        mSecondaryAppLcProvider.setOptionalApkFilters(mSecondaryOptionalAppFilters);
        mSecondaryAppLcProvider.setAdditionalFilesFilters(mSecondaryAdditionalFilesFilters);

        AppBuildInfo secondaryAppBuildInfo = (AppBuildInfo) mSecondaryAppLcProvider.getBuild();
        if (secondaryAppBuildInfo == null) {
            CLog.w("Successfully retrieved primary app build %s %s %s, but could not retrieve" +
                   " secondary app build %s %s", b.getBuildBranch(), b.getBuildFlavor(),
                   b.getBuildId(), mSecondaryAppBranch, mSecondaryAppBuildFlavor);
            resetTestBuild(b.getBuildId());
            b.cleanUp();
            return null;
        }
        CLog.d("Fetching build for Secondary App Build Info!");

        // Copy over the apks and additional files.
        for (VersionedFile f : secondaryAppBuildInfo.getAppPackageFiles()) {
            b.addAppPackageFile(f.getFile(), f.getVersion());
        }
        for (VersionedFile f : secondaryAppBuildInfo.getFiles()) {
            String label = f.getFile().getName();
            b.setFile(label, f.getFile(), f.getVersion());
        }
        return b;
    }

    /**
     * Create a {@link AppLaunchControlProvider} to use.
     * </p>
     * Exposed for unit testing
     */
    AppLaunchControlProvider createAppLcProvider() {
        return new AppLaunchControlProvider();
    }

    private void copyOptions(AppLaunchControlProvider appLcProvider) throws BuildRetrievalError {
        try {
            OptionCopier.copyOptions(this, appLcProvider);
        } catch (ConfigurationException e) {
            throw new BuildRetrievalError("Failed to copy options to app lc provider", e);
        }
    }
}
