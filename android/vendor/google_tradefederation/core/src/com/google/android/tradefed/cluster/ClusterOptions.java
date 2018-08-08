// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.util.MultiMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * A {@link IClusterOptions} implementation which contains cluster-related options.
 */
@OptionClass(alias = "cluster", global_namespace = false)
public class ClusterOptions implements IClusterOptions {

    /**
     * The unique configuration object type name.
     * Used to retrieve the singleton instance from the {@link GlobalConfiguration}.
     *
     * @see IConfiguration#getConfigurationObject(String)
     */
    public static final String TYPE_NAME = "cluster_options";

    @Option(name = "service-url",
            description = "the base url of the tradefed cluster REST API")
    public String mServiceUrl =
            "https://tradefed-cluster.googleplex.com/_ah/api/tradefed_cluster/v1/";

    @Option(name = "cluster",
            description = "the cluster id for this TF instance",
            mandatory = true)
    public String mClusterId = null;

    @Option(name = "next-cluster",
            description = "seconadary clusters for this TF instance to run commands from. If " +
                    "this option is set, TF will try to lease commands from these clusters in " +
                    "the order they are specified if it still has available devices after " +
                    "leasing commands from the primary cluster.")
    public List<String> mNextClusterIds = new ArrayList<>();

    @Option(name = "run-target-format",
            description = "the format for labelling run targets.")
    private String mRunTargetFormat = null;

    @Option(name = "disable-device-monitor",
            description = "disable Cluster device reporting")
    private boolean mIsDeviceMonitorDisabled = false;

    @Option(name = "device-monitor-interval",
            description = "the time interval between each device snapshot in ms")
    private long mDeviceMonitorSnapshotInterval = 60 * 1000;

    @Option(name = "device-group", description = "A multi-map from device group to device serials."
            + " The key is a device group name and value is device serial.")
    private MultiMap<String, String> mDeviceGroup = new MultiMap<String, String>();

    @Option(name = "device-tag", description = "A map for tagging device serials; each device may "
            + "have one tag. This can be used for reporting in run-target")
    private Map<String, String> mDeviceTag = new HashMap<>();

    // TODO(b/34387441): remove this once we migrate to new API, and use leasehosttasks API only.
    @Option(name = "lease-host-commands", description = "Lease commands from the"
            + " leasehosttasks API or not.")
    private Boolean mLeaseHostCommands = false;

    @Option(name = "use-sso-client", description = "Use sso_client for HTTP requests.")
    private Boolean mUseSsoClient = true;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getServiceUrl() {
        return mServiceUrl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getClusterId() {
        return mClusterId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getNextClusterIds() {
        return mNextClusterIds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MultiMap<String, String> getDeviceGroup() {
        return mDeviceGroup;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getDeviceTag() {
        return mDeviceTag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean shouldLeaseHostCommands() {
        return mLeaseHostCommands;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRunTargetFormat() {
        return mRunTargetFormat;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDeviceMonitorDisabled() {
        return mIsDeviceMonitorDisabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getDeviceMonitorSnapshotInterval() {
        return mDeviceMonitorSnapshotInterval;
    }

    /** {@inheritDoc} */
    @Override
    public Boolean shouldUseSsoClient() {
        return mUseSsoClient;
    }

    /**
     * Set the base url of the tradefed cluster REST API.
     *
     * <p>Exposed for testing.
     */
    void setServiceUrl(String url) {
        mServiceUrl = url;
    }

    /**
     * Set the cluster id for this TF instance.
     * <p>
     * Exposed for testing.
     */
    void setClusterId(String id) {
        mClusterId = id;
    }

    /**
     * Set the format for labelling run targets.
     * <p>
     * Exposed for testing.
     */
    void setRunTargetFormat(String format) {
        mRunTargetFormat = format;
    }

    /**
     * Set whether Cluster device reporting is disabled.
     * <p>
     * Exposed for testing.
     */
    void setDeviceMonitorDisabled(boolean disabled) {
        mIsDeviceMonitorDisabled = disabled;
    }

    /**
     * Set the time interval between each device snapshot in ms.
     * <p>
     * Exposed for testing.
     */
    void setDeviceMonitorSnapshotInterval(long interval) {
        mDeviceMonitorSnapshotInterval = interval;
    }

}
