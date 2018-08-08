// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.cluster;

import java.util.List;
import java.util.UUID;

/**
 * A class that represents a task fetched from TF Cluster.
 */
public class ClusterCommand {

    public static enum RequestType {
        /** An unmanaged request: the command line will run as is by the current TF process. */
        UNMANAGED,
        /** A managed request: the command line will run by a new TF process. */
        MANAGED;
    }

    private final String mTaskId;
    private final String mRequestId;
    private final String mCommandId;
    private final String mCommandLine;
    private final RequestType mRequestType;
    private final String mAttemptId;
    private String mDeviceSerial = null;
    private List<String> mTargetDeviceSerials = null;

    public ClusterCommand(String commandId, String taskId, String cmdLine) {
        this(null, commandId, taskId, cmdLine, RequestType.UNMANAGED);
    }

    /**
     * Constructor.
     *
     * @param requestId A request ID
     * @param commandId The ID of the command that issued this task
     * @param taskId The ID of this task
     * @param cmdLine The command line to run
     * @param requestType A request type
     */
    public ClusterCommand(
            String requestId,
            String commandId,
            String taskId,
            String cmdLine,
            RequestType requestType) {
        mTaskId = taskId;
        mRequestId = requestId;
        mCommandId = commandId;
        mCommandLine = cmdLine;
        mRequestType = requestType;
        mAttemptId = UUID.randomUUID().toString();
    }

    /**
     * Returns the task ID.
     *
     * @return task ID.
     */
    public String getTaskId() {
        return mTaskId;
    }

    /**
     * Returns the request ID.
     *
     * @return the request ID
     */
    public String getRequestId() {
        return mRequestId;
    }

    /**
     * Returns the command ID.
     *
     * @return the command ID
     */
    public String getCommandId() {
        return mCommandId;
    }

    /**
     * Returns the attempt ID. The attempt is randomly generated GUID used to distinguish multiple
     * command runs.
     *
     * @return the attempt ID
     */
    public String getAttemptId() {
        return mAttemptId;
    }

    /**
     * Returns the command line string.
     *
     * @return the command line string.
     */
    public String getCommandLine() {
        return mCommandLine;
    }

    /**
     * Returns a request type
     *
     * @return a request type
     */
    public RequestType getRequestType() {
        return mRequestType;
    }

    /**
     * Returns the device serial on which the command has run. This is set after a device is
     * allocated.
     *
     * @return the device serial.
     */
    public String getDeviceSerial() {
        return mDeviceSerial;
    }

    /**
     * Sets the device serial on which the command has run.
     *
     * @param deviceSerial the device serial.
     */
    public void setDeviceSerial(String deviceSerial) {
        mDeviceSerial = deviceSerial;
    }

    /**
     * Returns the list of target device serials on which this command will attempt to run.
     *
     * @return the list of target device serials
     */
    public List<String> getTargetDeviceSerials() {
        return mTargetDeviceSerials;
    }

    /**
     * Sets the list of target device serials on which the command will try to run.
     *
     * @param targetDeviceSerials the list of device serials to set
     */
    public void setTargetDeviceSerials(List<String> targetDeviceSerials) {
        this.mTargetDeviceSerials = targetDeviceSerials;
    }
}
