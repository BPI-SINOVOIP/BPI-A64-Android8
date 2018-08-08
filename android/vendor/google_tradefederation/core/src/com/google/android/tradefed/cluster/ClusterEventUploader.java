// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.google.android.tradefed.util.IRestApiHelper;

import com.android.tradefed.log.LogUtil.CLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * ClusterEventUploader class, which uploads {@link IClusterEvent} to TFC.
 */
public abstract class ClusterEventUploader<T extends IClusterEvent> implements IClusterEventUploader<T> {

    // Default maximum event batch size
    private static final int DEFAULT_MAX_BATCH_SIZE = 200;

    // Default event upload interval in ms.
    private static final long DEFAULT_EVENT_UPLOAD_INTERVAL = 60 * 1000;

    private int mMaxBatchSize = DEFAULT_MAX_BATCH_SIZE;
    private long mEventUploadInterval = DEFAULT_EVENT_UPLOAD_INTERVAL;
    private long mLastEventUploadTime = 0;
    private List<T> mEvents = new LinkedList<>();
    private IRestApiHelper mApiHelper = null;
    private String[] mRestMethodPath;

    /**
     * Construct a {@link ClusterEventUploader} instance.
     *
     * @param apiHelper the {@link IRestApiHelper} instance used to submit events
     * @param restMethodPath the path of the particular REST method that will be called
     */
    public ClusterEventUploader(IRestApiHelper apiHelper, String[] restMethodPath) {
        mApiHelper = apiHelper;
        mRestMethodPath = restMethodPath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMaxBatchSize(int batchSize) {
        mMaxBatchSize = batchSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxBatchSize() {
        return mMaxBatchSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEventUploadInterval(long interval) {
        mEventUploadInterval = interval;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getEventUploadInterval(){
        return mEventUploadInterval;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void postEvent(final T event) {
        mEvents.add(event);
        uploadEvents(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void flush() {
        uploadEvents(true);
    }

    /**
     * Package the list of events into a form that can be sent to the server.
     */
    protected abstract JSONObject buildPostData(List<T> events) throws JSONException;

    private synchronized void uploadEvents(final boolean uploadNow) {
        final long now = System.currentTimeMillis();
        if (!uploadNow && now - mLastEventUploadTime < getEventUploadInterval()) {
            return;
        }
        mLastEventUploadTime = now;

        // Do not post if we have nothing.
        if (mEvents.size() == 0) {
            return;
        }

        try {
            // Upload batches of events until there are no more left
            while (!mEvents.isEmpty()) {
                // Limit the number of events to upload at once
                List<T> events = mEvents;
                int batchSize = getMaxBatchSize();
                if (mEvents.size() > batchSize) {
                    events = mEvents.subList(0, batchSize);
                }

                // We don't have to check the response. execute(..) will throw an exception on HTTP
                // error codes.
                getApiHelper().execute("POST", getMethodPath(), null, buildPostData(events));

                // Clear the events once they are uploaded successfully.
                events.clear();
            }
        } catch (IOException | JSONException e) {
            CLog.w("failed to upload events: %s", e);
            CLog.w("events will be uploaded with the next event.");
        }
    }

    /**
     * Get the {@link IRestApiHelper} instance used to call the backend's REST API.
     */
    IRestApiHelper getApiHelper() {
        return mApiHelper;
    }

    /**
     * Get the REST API method path to submit posted events.
     */
    String[] getMethodPath() {
        return mRestMethodPath;
    }

}
