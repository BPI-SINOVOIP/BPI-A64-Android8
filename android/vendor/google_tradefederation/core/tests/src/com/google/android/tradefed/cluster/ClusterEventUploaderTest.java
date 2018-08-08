// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.android.tradefed.util.IRestApiHelper;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link ClusterEventUploader}. */
@RunWith(JUnit4.class)
public class ClusterEventUploaderTest {

    private static final String[] API_PATH = new String[] {"example", "endpoints", "path"};
    private static final String DATA_KEY = "example_key";

    private ClusterEventUploader<ClusterCommandEvent> mUploader;
    private IRestApiHelper mMockApiHelper;

    @Before
    public void setUp() throws Exception {
        mMockApiHelper = EasyMock.createMock(IRestApiHelper.class);
        mUploader = new ClusterEventUploader<ClusterCommandEvent>(mMockApiHelper, API_PATH) {
            @Override
            protected JSONObject buildPostData(List<ClusterCommandEvent> events) throws JSONException {
                JSONArray array = new JSONArray();
                for (ClusterCommandEvent event : events) {
                    array.put(event.toJSON());
                }
                JSONObject postData = new JSONObject();
                postData.put(DATA_KEY, array);
                return postData;
            }
        };
    }

    @Test
    public void testPostCommandEvent_simpleEvent() throws IOException, JSONException {
        // Create an event to post
        final ClusterCommandEvent commandEvent = new ClusterCommandEvent.Builder()
                .setType(ClusterCommandEvent.Type.InvocationStarted)
                .build();

        // Initialize the data we expect to see
        Capture<JSONObject> postData = new Capture<>();
        EasyMock.expect(mMockApiHelper.execute(
                EasyMock.eq("POST"),
                EasyMock.aryEq(API_PATH),
                EasyMock.<Map<String, Object>>isNull(),
                EasyMock.capture(postData)))
                .andReturn(null);

        // Actually post the event
        EasyMock.replay(mMockApiHelper);
        mUploader.postEvent(commandEvent);
        EasyMock.verify(mMockApiHelper);

        // Validate the post data
        JSONArray events = postData.getValue().optJSONArray(DATA_KEY);
        assertNotNull(events);
        assertEquals(1, events.length());

        // Validate the event data
        JSONObject event = events.getJSONObject(0);
        assertEquals(ClusterCommandEvent.Type.InvocationStarted.toString(), event.opt("type"));
    }

    /*
     * If there are no events in the queue, do not post.
     */
    @Test
    public void testPostCommandEvent_NoEvent() {
        EasyMock.replay(mMockApiHelper);
        // Flushing an empty queue should not post anything
        mUploader.flush();
        EasyMock.verify(mMockApiHelper);
    }

    @Test
    public void testPostCommandEvent_eventWithData() throws IOException, JSONException {
        // Create an event to post
        final ClusterCommandEvent commandEvent = new ClusterCommandEvent.Builder()
                .setType(ClusterCommandEvent.Type.InvocationStarted)
                .setData("foo", "bar")
                .setData("bar", "foo")
                .build();

        // Initialize the data we expect to see
        Capture<JSONObject> postData = new Capture<>();
        EasyMock.expect(mMockApiHelper.execute(
                EasyMock.eq("POST"),
                EasyMock.aryEq(API_PATH),
                EasyMock.<Map<String, Object>>isNull(),
                EasyMock.capture(postData)))
                .andReturn(null);

        // Actually post the event
        EasyMock.replay(mMockApiHelper);
        mUploader.postEvent(commandEvent);
        EasyMock.verify(mMockApiHelper);

        // Validate the post data
        JSONArray events = postData.getValue().optJSONArray(DATA_KEY);
        assertNotNull(events);
        assertEquals(1, events.length());

        // Validate the event data
        JSONObject event = events.getJSONObject(0);
        assertEquals(ClusterCommandEvent.Type.InvocationStarted.toString(), event.opt("type"));
        JSONObject data = event.optJSONObject("data");
        assertNotNull(data);
        assertEquals("bar", data.opt("foo"));
        assertEquals("foo", data.opt("bar"));
    }

    @Test
    public void testPostCommandEvent_multipleEvents() throws IOException, JSONException {
        // Create an event to post
        final ClusterCommandEvent commandEvent = new ClusterCommandEvent.Builder()
                .setType(ClusterCommandEvent.Type.InvocationStarted)
                .build();

        // Initialize the data we expect to see
        Capture<JSONObject> postData = new Capture<>(CaptureType.ALL);
        EasyMock.expect(mMockApiHelper.execute(
                EasyMock.eq("POST"),
                EasyMock.aryEq(API_PATH),
                EasyMock.<Map<String, Object>>isNull(),
                EasyMock.capture(postData)))
                .andReturn(null);

        // The events may get posted in separate batches
        EasyMock.expectLastCall().atLeastOnce();

        // Actually post the events
        EasyMock.replay(mMockApiHelper);
        mUploader.postEvent(commandEvent);
        mUploader.postEvent(commandEvent);
        mUploader.postEvent(commandEvent);
        mUploader.postEvent(commandEvent);
        mUploader.flush();
        EasyMock.verify(mMockApiHelper);

        // Validate the post data
        int numCapturedEvents = 0;
        for (JSONObject batch : postData.getValues()) {
            JSONArray events = batch.optJSONArray(DATA_KEY);
            assertNotNull(events);
            numCapturedEvents += events.length();

            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.getJSONObject(i);
                assertEquals(ClusterCommandEvent.Type.InvocationStarted.toString(),
                        event.opt("type"));
            }
        }
        assertEquals(4, numCapturedEvents);
    }

    @Test
    public void testPostCommandEvent_multipleBatches() throws IOException, JSONException {
        // Create an event to post
        final ClusterCommandEvent commandEvent = new ClusterCommandEvent.Builder()
                .setType(ClusterCommandEvent.Type.InvocationStarted)
                .build();

        // Set the maximum batch size
        mUploader.setMaxBatchSize(2);

        // Initialize the data we expect to see
        Capture<JSONObject> postData = new Capture<>(CaptureType.ALL);
        EasyMock.expect(mMockApiHelper.execute(
                EasyMock.eq("POST"),
                EasyMock.aryEq(API_PATH),
                EasyMock.<Map<String, Object>>isNull(),
                EasyMock.capture(postData)))
                .andReturn(null);

        // The first event will get uploaded right away. The rest of the events (4) should be split
        // into 2 additional batches.
        EasyMock.expectLastCall().times(3);

        // Post 5 events
        EasyMock.replay(mMockApiHelper);
        mUploader.postEvent(commandEvent);
        mUploader.postEvent(commandEvent);
        mUploader.postEvent(commandEvent);
        mUploader.postEvent(commandEvent);
        mUploader.postEvent(commandEvent);
        mUploader.flush();
        EasyMock.verify(mMockApiHelper);

        // Validate the post data
        int numCapturedEvents = 0;
        for (JSONObject batch : postData.getValues()) {
            JSONArray events = batch.optJSONArray(DATA_KEY);
            assertNotNull(events);
            numCapturedEvents += events.length();

            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.getJSONObject(i);
                assertEquals(ClusterCommandEvent.Type.InvocationStarted.toString(),
                        event.opt("type"));
            }
        }
        assertEquals(5, numCapturedEvents);
    }
}
