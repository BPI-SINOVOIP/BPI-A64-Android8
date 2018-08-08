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
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import java.util.Map;

/**
 * A {@link LaunchControlProvider} for {@link AppDeviceBuildInfo}.
 * <p/>
 * Retrieves both a unbundled app build and a device build from the Android build server. The
 * app build is considered the 'primary' build. The config this is used in will typically
 * report results against the app build, and launch control itself will track untested/tested
 * status for the app build.
 * <p/>
 * Use the device-branch and device-build-flavor options to specify the device build. In most cases
 * query types of NOTIFY_TEST_BUILD and QUERY_LATEST_BUILD would be used, with queries like
 * LATEST_GREEN_BUILD used for the app build.
 */
@OptionClass(alias = "app-device-launch-control")
public class AppWithDeviceLaunchControlProvider extends AppLaunchControlProvider {

    @Option(name = "device-build-flavor", description = "the device build flavor to download.",
            importance = Importance.IF_UNSET, mandatory = true)
    private String mDeviceBuildFlavor = null;

    @Option(name = "device-branch", description = "the branch of device build to use.",
            importance = Importance.IF_UNSET, mandatory = true)
    private String mDeviceBranch = null;

    @Option(name = "device-build-id", description =
        "the id of device build to use. If unspecified will get latest build.")
    private String mDeviceBuildId = null;

    @Option(name = "device-query-type", description =
        "the launch control query type to perform for device build.")
    private QueryType mDeviceQueryType = QueryType.QUERY_LATEST_BUILD;

    /**
     * Set the device build flavor to download.
     * <p/>
     * Exposed for unit testing.
     */
    void setDeviceBuildFlavor(String deviceBuildFlavor) {
        mDeviceBuildFlavor = deviceBuildFlavor;
    }

    /**
     * @return the device build to download.
     */
    String getDeviceBuildFlavor() {
        return mDeviceBuildFlavor;
    }

    /**
     * Set the device branch to download.
     * <p/>
     * Exposed for unit testing.
     */
    void setDeviceBranch(String deviceBranch) {
        mDeviceBranch = deviceBranch;
    }

    /**
     * @return the device branch to download.
     */
    String getDeviceBranch() {
        return mDeviceBranch;
    }

    /**
     * @return the device build to download.
     */
    protected String getDeviceBuildId() {
        return mDeviceBuildId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        throw new BuildRetrievalError("an instance of ITestDevice is required " +
                "to get a build. Use getBuild(ITestDevice) instead.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild(ITestDevice device) throws BuildRetrievalError,
            DeviceNotAvailableException {
        setTestTagByDevice(device);
        AppDeviceBuildInfo appDeviceBuild = null;
        final RemoteBuildInfo appRemoteBuild = getRemoteBuild();
        if (appRemoteBuild == null) {
            return null;
        }
        try {
            // query launch control to determine device build to test
            DeviceLaunchControlProvider deviceLcProvider = createDeviceLcProvider();
            copyOptions(deviceLcProvider);
            // Remove the number of iterations if it was set.
            // The number of iterations is controlling the app build
            // We will be running different app builds on the same device build
            // so test iterations parameter is no applicable.
            deviceLcProvider.setIterations(null);
            deviceLcProvider.setBuildFlavor(mDeviceBuildFlavor);
            deviceLcProvider.setBranch(mDeviceBranch);
            deviceLcProvider.setQueryType(mDeviceQueryType);
            deviceLcProvider.setBuildId(mDeviceBuildId);
            deviceLcProvider.setMinBuildId(0);
            deviceLcProvider.setTestTag(getTestTag());

            final RemoteBuildInfo deviceRemoteBuild = deviceLcProvider.getRemoteBuild();
            if (deviceRemoteBuild == null) {
                // treat this as a type of config error - should pivot on app build
                throw new BuildRetrievalError("Failed to retrieve device build");
            }

            IAppBuildInfo appBuild = (IAppBuildInfo)fetchRemoteBuild(appRemoteBuild);
            appDeviceBuild = new AppDeviceBuildInfo(appBuild.getBuildId(),
                    appBuild.getBuildTargetName());
            appDeviceBuild.setAppBuild(appBuild);
            appDeviceBuild.setBuildBranch(appBuild.getBuildBranch());
            appDeviceBuild.setBuildFlavor(appBuild.getBuildFlavor());
            for (Map.Entry<String, String> mapEntry : appBuild.getBuildAttributes().entrySet()) {
                appDeviceBuild.addBuildAttribute(mapEntry.getKey(), mapEntry.getValue());
            }

            IDeviceBuildInfo deviceBuild = (IDeviceBuildInfo)deviceLcProvider.fetchRemoteBuild(
                    deviceRemoteBuild);
            appDeviceBuild.setDeviceBuild(deviceBuild);
            return appDeviceBuild;
        } catch (BuildRetrievalError e) {
            resetTestBuild(appRemoteBuild.getBuildId());
            if (appDeviceBuild != null) {
                e.setBuildInfo(appDeviceBuild);
                appDeviceBuild.cleanUp();
            }
            throw e;
        }
    }

    /**
     * Create a {@link DeviceLaunchControlProvider} to use.
     * <p/>
     * Exposed for unit testing
     */
    DeviceLaunchControlProvider createDeviceLcProvider() {
        return new DeviceLaunchControlProvider();
    }

    private void copyOptions(DeviceLaunchControlProvider deviceLcProvider)
            throws BuildRetrievalError {
        try {
            OptionCopier.copyOptions(this, deviceLcProvider);
        } catch (ConfigurationException e) {
            throw new BuildRetrievalError("Failed to copy options to device lc provider", e);
        }
    }

}
