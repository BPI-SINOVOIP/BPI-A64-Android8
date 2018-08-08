// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.android.tradefed.util.MultiMap;

import java.util.List;
import java.util.Map;

/**
 * An interface for getting cluster-related options.
 */
public interface IClusterOptions {

    /**
     * Get the base url of the tradefed cluster REST API.
     */
    public String getServiceUrl();

    /**
     * Get the cluster id for this TF instance.
     */
    public String getClusterId();

    /**
     * Get the secondary cluster ids for this TF instance.
     */
    public List<String> getNextClusterIds();

    /**
     * Get the device group to device mapping.
     */
    public MultiMap<String, String> getDeviceGroup();

    /**
     * Get the device serial to tag mapping.
     */
    public Map<String, String> getDeviceTag();

    /**
     * Check if it should call leasehosttasks or not.
     */
    public Boolean shouldLeaseHostCommands();

    /**
     * Get the format for labelling run targets.
     */
    public String getRunTargetFormat();

    /**
     * Returns whether Cluster device reporting is disabled.
     */
    public boolean isDeviceMonitorDisabled();

    /**
     * Get the time interval between each device snapshot in ms.
     */
    public long getDeviceMonitorSnapshotInterval();

    /** Check if it should use sso_client. */
    public Boolean shouldUseSsoClient();

}
