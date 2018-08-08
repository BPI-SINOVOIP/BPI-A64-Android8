// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.google.android.tradefed.cluster.ClusterCommand.RequestType;
import com.google.android.tradefed.util.IRestApiHelper;
import com.google.android.tradefed.util.RestApiHelper;
import com.google.android.tradefed.util.SsoClientTransport;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.StreamUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link IClusterClient} implementation for interacting with the TFC backend.
 */
public class ClusterClient implements IClusterClient {

    /**
     * The unique configuration object type name.
     * Used to retrieve the singleton instance from the {@link GlobalConfiguration}.
     *
     * @see IConfiguration#getConfigurationObject(String)
     */
    public static final String TYPE_NAME = "cluster_client";

    /** The {@link IClusterOptions} instance used to store cluster-related settings. */
    private IClusterOptions mClusterOptions;

    private IRestApiHelper mApiHelper = null;

    private IClusterEventUploader<ClusterCommandEvent> mCommandEventUploader = null;
    private IClusterEventUploader<ClusterHostEvent> mHostEventUploader = null;

    /**
     * A {@link IClusterEventUploader} implementation for uploading {@link ClusterCommandEvent}s.
     */
    private static class ClusterCommandEventUploader
            extends ClusterEventUploader<ClusterCommandEvent> {

        public static final String[] REST_API_METHOD = new String[] { "command_events" };
        public static final String DATA_KEY = "command_events";

        /**
         * Construct a {@link ClusterCommandEventUploader}
         * @param apiHelper the REST API used to submit events
         */
        public ClusterCommandEventUploader(IRestApiHelper apiHelper) {
            super(apiHelper, REST_API_METHOD);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected JSONObject buildPostData(List<ClusterCommandEvent> events) throws JSONException {
            return ClusterClient.buildPostData(events, DATA_KEY);
        }
    }

    /**
     * A {@link IClusterEventUploader} implementation for uploading {@link ClusterHostEvent}s.
     */
    private static class ClusterHostEventUploader extends ClusterEventUploader<ClusterHostEvent> {
        public static final String[] REST_API_METHOD = new String[] { "host_events" };
        public static final String DATA_KEY = "host_events";

        /**
         * Construct a {@link ClusterHostEventUploader}
         * @param apiHelper the REST API used to submit events
         */
        public ClusterHostEventUploader(IRestApiHelper apiHelper) {
            super(apiHelper, REST_API_METHOD);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected JSONObject buildPostData(List<ClusterHostEvent> events) throws JSONException {
            return ClusterClient.buildPostData(events, DATA_KEY);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ClusterCommand> leaseCommands(final String clusterId, final String runTarget,
            final int count, final List<String> nextClusterIds) {
        // Make an API call to lease some work
        final Map<String, Object> options = new HashMap<>();
        options.put("num_tasks", Integer.toString(count));
        if (nextClusterIds != null && !nextClusterIds.isEmpty()) {
            // {@link IRestApiHelper} will translate nextClusterIds into repeated query parameters.
            options.put("next_cluster_ids", nextClusterIds);
        }
        try {
            // By default, execute(..) will throw an exception on HTTP error codes.
            HttpResponse httpResponse = getApiHelper().execute("GET",
                    new String[] {"tasks", "lease", clusterId, runTarget},
                    options, null);
            return parseCommandTasks(httpResponse);
        } catch (IOException e) {
            // May be transient. Log a warning and we'll try again later.
            CLog.w("Failed to lease commands: %s", e);
            return Collections.<ClusterCommand>emptyList();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ClusterCommand> leaseHostCommands(
            final String clusterId, final String hostname,
            final List<ClusterDeviceInfo> deviceInfos,
            final List<String> nextClusterIds) throws JSONException {
        // Make an API call to lease some work
        final Map<String, Object> options = new HashMap<>();
        options.put("cluster", clusterId);
        options.put("hostname", hostname);

        JSONObject data = new JSONObject();
        if (nextClusterIds != null && !nextClusterIds.isEmpty()) {
            JSONArray ids = new JSONArray();
            for (String id : nextClusterIds) {
                ids.put(id);
            }
            data.put("next_cluster_ids", ids);
        }
        JSONArray deviceInfoJsons = new JSONArray();
        for (ClusterDeviceInfo d : deviceInfos) {
            deviceInfoJsons.put(d.toJSON());
        }
        // Add device infos in the request body. TFC will match devices based on those infos.
        data.put("device_infos", deviceInfoJsons);
        try {
            // By default, execute(..) will throw an exception on HTTP error codes.
            HttpResponse httpResponse = getApiHelper().execute("POST",
                    new String[] { "tasks", "leasehosttasks" },
                    options, data);
            return parseCommandTasks(httpResponse);
        } catch (IOException e) {
            // May be transient. Log a warning and we'll try again later.
            CLog.w("Failed to lease commands: %s", e);
            return Collections.<ClusterCommand> emptyList();
        }
    }

    /** {@inheritDoc} */
    @Override
    public TestEnvironment getTestEnvironment(final String requestId)
            throws IOException, JSONException {
        final TestEnvironment result = new TestEnvironment();
        final Map<String, Object> options = new HashMap<>();
        final HttpResponse response =
                getApiHelper()
                        .execute(
                                "GET",
                                new String[] {"requests", requestId, "test_environment"},
                                options,
                                null);
        final String content = StreamUtil.getStringFromStream(response.getContent());
        CLog.d(content);
        final JSONObject json = new JSONObject(content);
        final JSONArray envVars = json.optJSONArray("env_vars");
        if (envVars != null) {
            for (int i = 0; i < envVars.length(); i++) {
                final JSONObject envVar = envVars.getJSONObject(i);
                result.addEnvVar(envVar.getString("key"), envVar.getString("value"));
            }
        } else {
            CLog.w("env_vars is null");
        }
        final JSONArray scripts = json.optJSONArray("setup_scripts");
        if (scripts != null) {
            for (int i = 0; i < scripts.length(); i++) {
                result.addSetupScripts(scripts.getString(i));
            }
        } else {
            CLog.w("setup_scripts is null");
        }
        final JSONArray patterns = json.optJSONArray("output_file_patterns");
        if (patterns != null) {
            for (int i = 0; i < patterns.length(); i++) {
                result.addOutputFilePattern(patterns.getString(i));
            }
        } else {
            CLog.w("output_file_patterns is null");
        }
        final String url = json.optString("output_file_upload_url");
        if (url != null) {
            result.setOutputFileUploadUrl(url);
        } else {
            CLog.w("output_file_upload_url is null");
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public List<TestResource> getTestResources(final String requestId)
            throws IOException, JSONException {
        final List<TestResource> result = new ArrayList<>();
        final Map<String, Object> options = new HashMap<>();
        final HttpResponse response =
                getApiHelper()
                        .execute(
                                "GET",
                                new String[] {"requests", requestId, "test_resources"},
                                options,
                                null);
        final String content = StreamUtil.getStringFromStream(response.getContent());
        CLog.d(content);
        JSONObject json = new JSONObject(content);
        JSONArray resources = json.getJSONArray("test_resources");
        if (resources != null) {
            for (int i = 0; i < resources.length(); i++) {
                JSONObject resource = resources.getJSONObject(i);
                result.add(new TestResource(resource.getString("name"), resource.getString("url")));
            }
        } else {
            CLog.w("test_resources is null");
        }
        return result;
    }

    /**
     * Parse command tasks from the http response
     *
     * @param httpResponse the http response
     * @return a list of command tasks
     * @throws IOException
     */
    private static List<ClusterCommand> parseCommandTasks(HttpResponse httpResponse)
            throws IOException {
        String response = "";
        InputStream content = httpResponse.getContent();
        if (content == null) {
            throw new IOException("null response");
        }
        response = StreamUtil.getStringFromStream(content);
        // Parse the response
        try {
            JSONObject jsonResponse = new JSONObject(response);
            if (jsonResponse.has("tasks")) {
                // Convert the JSON commands to ClusterCommand objects
                JSONArray jsonCommands = jsonResponse.getJSONArray("tasks");
                List<ClusterCommand> commandTasks = new ArrayList<>(jsonCommands.length());
                for (int i = 0; i < jsonCommands.length(); i++) {
                    JSONObject jsonCommand = jsonCommands.getJSONObject(i);
                    ClusterCommand command =
                            new ClusterCommand(
                                    jsonCommand.getString("request_id"),
                                    jsonCommand.getString("command_id"),
                                    jsonCommand.getString("task_id"),
                                    jsonCommand.getString("command_line"),
                                    RequestType.valueOf(
                                            jsonCommand.optString(
                                                    "request_type", RequestType.UNMANAGED.name())));
                    JSONArray jsonDeviceSerials = jsonCommand.optJSONArray("device_serials");
                    if (jsonDeviceSerials != null) {
                        final List<String> deviceSerials = new ArrayList<>();
                        for (int j = 0; j < jsonDeviceSerials.length(); j++) {
                            deviceSerials.add(jsonDeviceSerials.getString(j));
                        }
                        command.setTargetDeviceSerials(deviceSerials);
                    }
                    commandTasks.add(command);
                }
                return commandTasks;
            } else {
                // No work to be done
                return Collections.<ClusterCommand> emptyList();
            }
        } catch (JSONException e) {
            // May be a server-side issue. Log a warning and we'll try again later.
            CLog.w("Failed to parse response from server: %s", response);
            return Collections.<ClusterCommand> emptyList();
        }
    }

    /**
     * Helper method to convert a list of {@link IClusterEvent}s into a json format that TFC can
     * understand.
     *
     * @param events The list of events to convert
     * @param key The key string to use to identify the list of events
     */
    private static JSONObject buildPostData(List<? extends IClusterEvent> events, String key)
            throws JSONException {

        JSONArray array = new JSONArray();
        for (IClusterEvent event : events) {
            array.put(event.toJSON());
        }
        JSONObject postData = new JSONObject();
        postData.put(key, array);
        return postData;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IClusterEventUploader<ClusterCommandEvent> getCommandEventUploader() {
        if (mCommandEventUploader == null) {
            mCommandEventUploader = new ClusterCommandEventUploader(getApiHelper());
        }
        return mCommandEventUploader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IClusterEventUploader<ClusterHostEvent> getHostEventUploader() {
        if (mHostEventUploader == null) {
            mHostEventUploader = new ClusterHostEventUploader(getApiHelper());
        }
        return mHostEventUploader;
    }

    /**
     * Get the shared {@link IRestApiHelper} instance.
     * <p>
     * Exposed for testing.
     *
     * @return the shared {@link IRestApiHelper} instance.
     */
    IRestApiHelper getApiHelper() {
        if (mApiHelper == null) {
            HttpTransport transport = null;
            if (getClusterOptions().shouldUseSsoClient()) {
                transport = new SsoClientTransport();
            } else {
                transport = new NetHttpTransport();
            }
            HttpRequestFactory requestFactory = transport.createRequestFactory();
            mApiHelper = new RestApiHelper(requestFactory, getClusterOptions().getServiceUrl());
        }
        return mApiHelper;
    }

    /**
     * Get the {@link IClusterOptions} instance used to store cluster-related settings.
     */
    IClusterOptions getClusterOptions() {
        if (mClusterOptions == null) {
            mClusterOptions = (IClusterOptions)GlobalConfiguration.getInstance()
                    .getConfigurationObject(ClusterOptions.TYPE_NAME);
            if (mClusterOptions == null) {
                throw new IllegalStateException("cluster_options not defined. You must add this " +
                        "object to your global config. See google/atp/cluster.xml.");
            }
        }
        return mClusterOptions;
    }
}
