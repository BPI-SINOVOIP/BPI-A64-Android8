// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.cluster;

/**
 * Interface for ClusterEventUploader
 */
public interface IClusterEventUploader<T extends IClusterEvent> {
    /**
     * Get the maximum number of events to upload at once.
     *
     * @param batchSize the maximum number of events to upload at once.
     */
    public void setMaxBatchSize(int batchSize);

    /**
     * Get the maximum batch size used when uploading events.
     *
     * @return the maximum batch size.
     */
    public int getMaxBatchSize();

    /**
     * Set how often we upload events to TFC.
     *
     * @param interval in ms for events to be uploaded to TFC.
     */
    public void setEventUploadInterval(long interval);

    /**
     * Get the upload interval.
     *
     * @return the upload interval in ms.
     */
    public long getEventUploadInterval();

    /**
     * Posts an event to TFC. This queues the event to be uploaded.
     * Events will be batched and uploaded.
     *
     * @param event the event to upload
     */
   void postEvent(final T event);


   /**
    * Force an upload of all events queued.
    */
   void flush();

}
