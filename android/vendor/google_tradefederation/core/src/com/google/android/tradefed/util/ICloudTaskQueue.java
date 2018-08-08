// Copyright 2014 Google Inc.  All Rights Reserved.
package com.google.android.tradefed.util;

import java.util.List;

/**
 * A helper interface for Cloud Task Queue APIs.
 */
public interface ICloudTaskQueue {

    /**
     * Inserts a task to the given queue.
     *
     * @param queueName name of a queue.
     * @param task a task to be inserted.
     * @throws CloudTaskQueueException
     */
    public void insertTask(String queueName, CloudTask task) throws CloudTaskQueueException;

    /**
     * Leases tasks from a given queue.
     *
     * @param queueName name of a queue.
     * @param numTasks max number of tasks to lease.
     * @param leaseSecs duration of lease.
     * @param tag tag of tasks to lease. Pass null to lease any tasks.
     * @throws CloudTaskQueueException
     */
    public List<CloudTask> leaseTasks(String queueName, int numTasks, int leaseSecs, String tag)
            throws CloudTaskQueueException;

    /**
     * Update the lease duration for a leased task.
     *
     * @param queueName name of a queue.
     * @param task a {@link CloudTask} instance.
     * @param newLeaseSecs new duration of lease.
     * @throws CloudTaskQueueException
     */
    public void updateTask(String queueName, CloudTask task, int newLeaseSecs)
            throws CloudTaskQueueException;

    /**
     * Delete a task from the given queue.
     *
     * @param queueName The name of the queue.
     * @param task The {@link CloudTask} instance to delete.
     * @throws CloudTaskQueueException
     */
    public void deleteTask(String queueName, CloudTask task) throws CloudTaskQueueException;
}
