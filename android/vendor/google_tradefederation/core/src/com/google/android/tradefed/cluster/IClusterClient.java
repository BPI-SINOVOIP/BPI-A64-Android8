// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;

/**
 * An interface for interacting with the TFC backend.
 */
interface IClusterClient {

    /**
     * Leases {@link ClusterCommand} for the given device type from TFC.
     *
     * @param clusterId a cluster ID to lease commands from.
     * @param runTarget the type of devices available.
     * @param count the number of devices available.
     * @param nextClusterIds a list of next cluster IDs to lease commands from.
     * @return a list of {@link ClusterCommand}.
     */
    public List<ClusterCommand> leaseCommands(final String clusterId, final String runTarget,
            final int count, final List<String> nextClusterIds);

    /**
     * Get a {@link IClusterEventUploader} that can be used to upload {@link ClusterCommandEvent}s.
     */
    public IClusterEventUploader<ClusterCommandEvent> getCommandEventUploader();

    /**
     * Get a {@link IClusterEventUploader} that can be used to upload {@link ClusterHostEvent}s.
     */
    public IClusterEventUploader<ClusterHostEvent> getHostEventUploader();

    /**
     * Lease {@link ClusterCommand} for the give host.
     *
     * @param clusterId cluster id for the host
     * @param hostname hostname
     * @param devices deviceInfos the host has
     * @param nextClusterIds a list of next cluster IDs to lease commands from.
     * @return a list of {@link ClusterCommand}
     * @throws JSONException
     */
    public List<ClusterCommand> leaseHostCommands(
            final String clusterId,
            final String hostname,
            final List<ClusterDeviceInfo> devices,
            final List<String> nextClusterIds) throws JSONException;

    /**
     * Get {@link TestEnvironment} for a request.
     *
     * @param requestId
     * @return a {@link TestEnvironment} object.
     * @throws IOException
     * @throws JSONException
     */
    public TestEnvironment getTestEnvironment(final String requestId)
            throws IOException, JSONException;

    /**
     * Get {@link TestResource}s for a request.
     *
     * @param requestId
     * @return a list of {@link TestResource}.
     * @throws IOException
     * @throws JSONException
     */
    public List<TestResource> getTestResources(final String requestId)
            throws IOException, JSONException;
}
