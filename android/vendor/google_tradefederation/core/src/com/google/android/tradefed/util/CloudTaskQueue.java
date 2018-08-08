// Copyright 2014 Google Inc.  All Rights Reserved.
package com.google.android.tradefed.util;

import com.google.api.client.http.HttpResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * A helper class for Google Task Queue APIs.
 */
public class CloudTaskQueue implements ICloudTaskQueue {

    private static final String QUEUE_NAME = "queueName";
    private static final String TASKQUEUES = "taskqueues";
    private static final String TASKS = "tasks";

    private static final Collection<String> SCOPE =
            Collections.singleton("https://www.googleapis.com/auth/taskqueue");
    private static final String BASE_URI_FORMAT =
            "https://www.googleapis.com/taskqueue/v1beta2/projects/{0}/";

    private String mProject;
    private IRestApiHelper mApiHelper = null;

    public CloudTaskQueue(final String project) {
        mProject = project;
    }

    public void setup(String account, File keyFile)
            throws GeneralSecurityException, IOException {

        // Initialize the helper class we'll use for making requests
        mApiHelper = RestApiHelper.newInstanceWithGoogleCredential(buildBaseUri(mProject), account,
                keyFile, SCOPE);
    }

    private static String buildBaseUri(String project) {
        try {
            return MessageFormat.format(BASE_URI_FORMAT, URLEncoder.encode(project, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // Shouldn't happen
            throw new AssertionError("UTF-8 is unsupported");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void insertTask(String queueName, CloudTask task) throws CloudTaskQueueException {
        try {
            final JSONObject data = task.toJson();
            data.put(QUEUE_NAME, queueName);
            HttpResponse response = getApiHelper().execute("POST", new String[] {
                    TASKQUEUES, queueName, TASKS }, null, data);
            if (!response.isSuccessStatusCode()) {
                throw new CloudTaskQueueException(
                        String.format("Failed to insert task: %s", response.getStatusMessage()));
            }
        } catch (final IOException | JSONException e) {
            throw new CloudTaskQueueException("Failed to insert task", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CloudTask> leaseTasks(String queueName, int numTasks, int leaseSecs, String tag)
            throws CloudTaskQueueException {
        try {
            final Map<String, Object> options = new HashMap<>();
            options.put("numTasks", Integer.toString(numTasks));
            options.put("leaseSecs", Integer.toString(leaseSecs));
            if (tag != null) {
                options.put("groupByTag", Boolean.toString(true));
                options.put("tag", tag);
            }

            final HttpResponse response = getApiHelper().execute("POST", new String[] {
                    TASKQUEUES, queueName, TASKS, "lease" }, options, new JSONObject());
            if (!response.isSuccessStatusCode()) {
                throw new CloudTaskQueueException(
                        String.format("Failed to lease tasks: %s", response.getStatusMessage()));
            }
            final JSONArray items = parseJSON(response.getContent()).optJSONArray("items");
            final List<CloudTask> tasks = new LinkedList<>();
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    tasks.add(CloudTask.fromJson(items.getJSONObject(i)));
                }
            }
            return tasks;
        } catch (final IOException | JSONException e) {
            throw new CloudTaskQueueException("Failed to lease tasks: " + e.toString());
        }
    }

    private static JSONObject parseJSON(InputStream input) throws JSONException {
        final Scanner scanner = new Scanner(input, "UTF-8");
        final String content = scanner.useDelimiter("\\A").next();
        scanner.close();
        return new JSONObject(content);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateTask(String queueName, CloudTask task, int newLeaseSecs)
            throws CloudTaskQueueException {
        try {
            final Map<String, Object> options = new HashMap<>();
            // We need to get the original resource first.
            HttpResponse response = getApiHelper().execute("GET",
                    new String[] {TASKQUEUES, queueName, TASKS, task.getId()}, options, null);
            final JSONObject resource = parseJSON(response.getContent());
            resource.put(QUEUE_NAME, queueName);
            options.put("newLeaseSeconds", Integer.toString(newLeaseSecs));
            response = getApiHelper().execute("POST", new String[] {
                    TASKQUEUES, queueName, TASKS, task.getId() }, options, resource);
            if (!response.isSuccessStatusCode()) {
                throw new CloudTaskQueueException(
                        String.format("Failed to update task: %s", response.getStatusMessage()));
            }
        } catch (final IOException | JSONException e) {
            throw new CloudTaskQueueException("Failed to update task: " + e.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteTask(String queueName, CloudTask task) throws CloudTaskQueueException {
        try {
            final JSONObject data = task.toJson();
            data.put(QUEUE_NAME, queueName);
            HttpResponse response = getApiHelper().execute("DELETE", new String[] {
                    TASKQUEUES, queueName, TASKS, task.getId() }, null, null);
            if (!response.isSuccessStatusCode()) {
                throw new CloudTaskQueueException(
                        String.format("Failed to delete task: %s", response.getStatusMessage()));
            }
        } catch (IOException | JSONException e) {
            throw new CloudTaskQueueException("Failed to delete task: " + e.toString());
        }
    }

    IRestApiHelper getApiHelper() {
        if (mApiHelper == null) {
            throw new IllegalStateException("setup(..) must be called first");
        }
        return mApiHelper;
    }
}
