// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.build.AppDeviceBuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IAppBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * A {@link LaunchControlProvider} for {@link AppDeviceBuildInfo}.
 * <p/>
 * Retrieves both a unbundled app build and a device build from the Android build server. The
 * device build is considered the 'primary' build. The config this is used in will typically
 * report results against the device build, and launch control itself will track untested/tested
 * status for the device build.
 * <p/>
 * Use the app-branch and app-build-flavor options to specify the unbundled app build. In most cases
 * query types of NOTIFY_TEST_BUILD and QUERY_LATEST_BUILD would be used, with queries like
 * LATEST_GREEN_BUILD used for the device build.
 */
@OptionClass(alias = "device-app-launch-control")
public class DeviceWithAppLaunchControlProvider extends DeviceLaunchControlProvider {

    @Option(name = "app-build-flavor", description = "the app build flavor to download.",
            importance = Importance.IF_UNSET, mandatory = true)
    private String mAppBuildFlavor;

    @Option(name = "app-branch", description = "the branch of unbundled app build to test.",
            importance = Importance.IF_UNSET, mandatory = true)
    private String mAppBranch = null;

    @Option(name = "app-build-id", description =
        "the id of unbundled app build to test. If unspecified will get latest build.")
    private String mAppBuildId = null;

    @Option(name = "app-query-type", description =
        "the launch control query type to perform for unbundled app.")
    private QueryType mAppQueryType = QueryType.QUERY_LATEST_BUILD;

    @Option(name = "use-device-build-id", description =
            "flag to specify that device build id and branch should be used for the app build " +
            "query. If set, app-branch, app-build-id, and app-query-type will be ignored.")
    private boolean mUseDeviceBuildForApp = false;

    @Option(name = "app-name-filter", description = "Optional name regex pattern filter(s) for " +
            "apks to retrieve. If set, only apks matching one or more of these filters will be " +
            "retrieved.")
    private Collection<String> mAppFilters = new ArrayList<String>();

    /**
     * Set the app build flavor to download.
     * <p/>
     * Exposed for unit testing.
     */
    void setAppBuildFlavor(String appBuildFlavor) {
        mAppBuildFlavor = appBuildFlavor;
    }

    /**
     * Set the app branch to download.
     * <p/>
     * Exposed for unit testing.
     */
    void setAppBranch(String appBranch) {
        mAppBranch = appBranch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        AppDeviceBuildInfo appDeviceBuild = null;
        final RemoteBuildInfo deviceRemoteBuild = getRemoteBuild();
        if (deviceRemoteBuild == null) {
            return null;
        }
        try {
            // query launch control to determine app build to test
            AppLaunchControlProvider appLcProvider = createAppLcProvider();
            copyOptions(appLcProvider);
            appLcProvider.setBuildFlavor(mAppBuildFlavor);
            if (mUseDeviceBuildForApp) {
                mAppBranch = getBranch();
                mAppQueryType = QueryType.NOTIFY_TEST_BUILD;
                mAppBuildId = deviceRemoteBuild.getBuildId();
            }
            appLcProvider.setBranch(mAppBranch);
            appLcProvider.setQueryType(mAppQueryType);
            appLcProvider.setBuildId(mAppBuildId);
            appLcProvider.setMinBuildId(0);
            appLcProvider.setApkFilters(mAppFilters);
            appLcProvider.setTestTag(getTestTag());

            final RemoteBuildInfo appRemoteBuild = appLcProvider.getRemoteBuild();
            if (appRemoteBuild == null) {
                // this isn't always an error - might be the case where app isn't built yet
                CLog.w("Successfully retrieved device build %s, but could not retrieve app build",
                        deviceRemoteBuild.getBuildId());
                resetTestBuild(deviceRemoteBuild.getBuildId());
                return null;
            }

            IDeviceBuildInfo deviceBuild = (IDeviceBuildInfo)fetchRemoteBuild(deviceRemoteBuild);
            appDeviceBuild = new AppDeviceBuildInfo(deviceBuild.getBuildId(),
                    deviceBuild.getBuildTargetName());
            appDeviceBuild.setDeviceBuild(deviceBuild);
            appDeviceBuild.setBuildBranch(deviceBuild.getBuildBranch());
            appDeviceBuild.setBuildFlavor(deviceBuild.getBuildFlavor());
            for (Map.Entry<String, String> mapEntry : deviceBuild.getBuildAttributes().entrySet()) {
                appDeviceBuild.addBuildAttribute(mapEntry.getKey(), mapEntry.getValue());
            }

            IAppBuildInfo appBuild = (IAppBuildInfo)appLcProvider.fetchRemoteBuild(appRemoteBuild);
            appDeviceBuild.setAppBuild(appBuild);
            return appDeviceBuild;
        } catch (BuildRetrievalError e) {
            resetTestBuild(deviceRemoteBuild.getBuildId());
            if (appDeviceBuild != null) {
                e.setBuildInfo(appDeviceBuild);
                appDeviceBuild.cleanUp();
            }
            throw e;
        }
    }

    /**
     * Create a {@link AppLaunchControlProvider} to use.
     * <p/>
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
