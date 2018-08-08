// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.MockDeviceManager;
import com.android.tradefed.device.NoDeviceException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.keystore.IKeyStoreClient;
import com.android.tradefed.util.keystore.StubKeyStoreClient;

import com.google.android.tradefed.util.IRestApiHelper;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableMap;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link ClusterCommandScheduler}.
 */
public class ClusterCommandSchedulerTest extends TestCase {

    private static final String CLUSTER_ID = "free_pool";
    private static final String REQUEST_ID = "request_id";
    private static final String COMMAND_ID = "command_id";
    private static final String TASK_ID = "task_id";
    private static final String CMD_LINE = "test";
    private static final String DEVICE_SERIAL = "serial";

    private IDeviceManager mMockDeviceManager;
    private IRestApiHelper mMockApiHelper;
    private IClusterClient mMockClusterClient;
    private ClusterOptions mMockClusterOptions;
    private IClusterEventUploader<ClusterCommandEvent> mMockEventUploader;
    private long mMockCurrentTimeMillis;
    private ClusterCommandScheduler mScheduler;
    // Test variable to store the args of last execCommand called by CommandScheduler.
    Stack<ArrayList<String>> mExecCmdArgs = new Stack<>();

    String[] getExecCommandArgs() {
        ArrayList<String> execCmdArgs = mExecCmdArgs.pop();
        String[] args = new String[execCmdArgs.size()];
        return execCmdArgs.toArray(args);
    }

    // Explicitly define this, so we can mock it
    private static interface ICommandEventUploader
            extends IClusterEventUploader<ClusterCommandEvent> {}

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mMockDeviceManager = EasyMock.createMock(IDeviceManager.class);
        mMockApiHelper = EasyMock.createMock(IRestApiHelper.class);
        mMockEventUploader = EasyMock.createMock(ICommandEventUploader.class);
        mMockClusterOptions = new ClusterOptions();
        mMockClusterOptions.setClusterId(CLUSTER_ID);
        mMockClusterClient = new ClusterClient() {
            @Override
            public IClusterEventUploader<ClusterCommandEvent> getCommandEventUploader() {
                return mMockEventUploader;
            }

            @Override
            public IClusterOptions getClusterOptions() {
                return mMockClusterOptions;
            }

            @Override
            IRestApiHelper getApiHelper() {
                return mMockApiHelper;
            }
        };

        mMockCurrentTimeMillis = System.currentTimeMillis();
        mScheduler =
                new ClusterCommandScheduler() {
                    @Override
                    long getCurrentTimeMillis() {
                        return mMockCurrentTimeMillis;
                    }

                    @Override
                    public IClusterOptions getClusterOptions() {
                        return mMockClusterOptions;
                    }

                    @Override
                    IClusterClient getClusterClient() {
                        return mMockClusterClient;
                    }

                    @Override
                    public void execCommand(IScheduledInvocationListener listener, String[] args)
                            throws ConfigurationException, NoDeviceException {
                        ArrayList<String> execCmdArgs = new ArrayList<>();
                        for (String arg : args) {
                            execCmdArgs.add(arg);
                        }
                        mExecCmdArgs.push(execCmdArgs);
                    }

                    @Override
                    protected boolean dryRunCommand(
                            final InvocationEventHandler handler, String[] args) {
                        return false;
                    }
                };
    }

    private static class FakeHttpTransport extends MockHttpTransport {

        private byte[] mResponseBytes;

        public FakeHttpTransport(byte[] responseBytes) {
            mResponseBytes = responseBytes;
        }

        @Override
        public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
            return new MockLowLevelHttpRequest() {
                @Override
                public LowLevelHttpResponse execute() throws IOException {
                    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                    response.setContent(new ByteArrayInputStream(mResponseBytes));
                    return response;
                }
            };
        }
    }

    private HttpResponse buildHttpResponse(String response) throws IOException {
        HttpRequestFactory factory =
                new FakeHttpTransport(response.getBytes()).createRequestFactory();
        // The method and url aren't used by our fake transport, but they can't be null
        return factory.buildRequest("GET", new GenericUrl("http://example.com"), null).execute();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mExecCmdArgs.clear();
    }

    private DeviceDescriptor createDevice(
            String product, String variant, DeviceAllocationState state) {
        return createDevice(DEVICE_SERIAL, product, variant, state);
    }

    private DeviceDescriptor createDevice(
            String serial, String product, String variant, DeviceAllocationState state) {
        return new DeviceDescriptor(
                serial, false, state, product, variant, "sdkVersion", "buildId", "batteryLevel");
    }

    public void testIsIpPort() {
        assertTrue(ClusterCommandScheduler.isIpPort("127.0.0.1:101"));
        assertTrue(ClusterCommandScheduler.isIpPort("127.0.0.1"));
        assertFalse(ClusterCommandScheduler.isIpPort(DEVICE_SERIAL));
        assertFalse(ClusterCommandScheduler.isIpPort("127.0.0.1:notaport"));
    }

    public void testGetAvailableDevices() {
        final List<DeviceDescriptor> deviceList = new ArrayList<>();
        deviceList.add(createDevice("product1", "variant1", DeviceAllocationState.Available));
        deviceList.add(createDevice("product2", "variant2", DeviceAllocationState.Available));
        deviceList.add(createDevice("product2", "variant2", DeviceAllocationState.Allocated));
        deviceList.add(createDevice("product3", "variant3", DeviceAllocationState.Available));
        deviceList.add(createDevice("product3", "variant3", DeviceAllocationState.Allocated));
        deviceList.add(createDevice("product3", "variant3", DeviceAllocationState.Unavailable));
        EasyMock.expect(mMockDeviceManager.listAllDevices()).andReturn(deviceList);

        EasyMock.replay(mMockDeviceManager);
        final MultiMap<String, DeviceDescriptor> deviceMap =
                mScheduler.getDevices(mMockDeviceManager, false);
        EasyMock.verify(mMockDeviceManager);

        Assert.assertTrue(deviceMap.containsKey("product1:variant1"));
        Assert.assertEquals(1, deviceMap.get("product1:variant1").size());
        Assert.assertTrue(deviceMap.containsKey("product2:variant2"));
        Assert.assertEquals(2, deviceMap.get("product2:variant2").size());
        Assert.assertTrue(deviceMap.containsKey("product3:variant3"));
        Assert.assertEquals(3, deviceMap.get("product3:variant3").size());
    }

    public void testGetDevices_available() {
        final List<DeviceDescriptor> deviceList = new ArrayList<>();
        deviceList.add(createDevice("product1", "variant1", DeviceAllocationState.Available));
        deviceList.add(createDevice("product2", "variant2", DeviceAllocationState.Available));
        deviceList.add(createDevice("product2", "variant2", DeviceAllocationState.Allocated));
        deviceList.add(createDevice("product3", "variant3", DeviceAllocationState.Available));
        deviceList.add(createDevice("product3", "variant3", DeviceAllocationState.Allocated));
        deviceList.add(createDevice("product3", "variant3", DeviceAllocationState.Unavailable));
        EasyMock.expect(mMockDeviceManager.listAllDevices()).andReturn(deviceList);

        EasyMock.replay(mMockDeviceManager);
        final MultiMap<String, DeviceDescriptor> deviceMap =
                mScheduler.getDevices(mMockDeviceManager, true);
        EasyMock.verify(mMockDeviceManager);

        Assert.assertTrue(deviceMap.containsKey("product1:variant1"));
        Assert.assertEquals(1, deviceMap.get("product1:variant1").size());
        Assert.assertTrue(deviceMap.containsKey("product2:variant2"));
        Assert.assertEquals(1, deviceMap.get("product2:variant2").size());
        Assert.assertTrue(deviceMap.containsKey("product3:variant3"));
        Assert.assertEquals(1, deviceMap.get("product3:variant3").size());
    }

    public void testGetDevices_LocalhostIpDevices() {
        final List<DeviceDescriptor> deviceList = new ArrayList<>();
        deviceList.add(
                createDevice(
                        "127.0.0.1:101", "product1", "variant1", DeviceAllocationState.Available));
        deviceList.add(
                createDevice(
                        "127.0.0.1:102", "product1", "variant1", DeviceAllocationState.Available));
        deviceList.add(createDevice("product2", "variant2", DeviceAllocationState.Allocated));
        deviceList.add(createDevice("product3", "variant3", DeviceAllocationState.Available));
        deviceList.add(createDevice("product3", "variant3", DeviceAllocationState.Unavailable));
        EasyMock.expect(mMockDeviceManager.listAllDevices()).andReturn(deviceList);

        EasyMock.replay(mMockDeviceManager);
        final MultiMap<String, DeviceDescriptor> deviceMap =
                mScheduler.getDevices(mMockDeviceManager, true);
        EasyMock.verify(mMockDeviceManager);

        Assert.assertFalse(deviceMap.containsKey("product1:variant1"));
        Assert.assertFalse(deviceMap.containsKey("product2:variant2"));
        Assert.assertTrue(deviceMap.containsKey("product3:variant3"));
        Assert.assertEquals(1, deviceMap.get("product3:variant3").size());
    }

    public void testGetDevices_NoAvailableDevices() {
        final List<DeviceDescriptor> deviceList = new ArrayList<>();
        deviceList.add(createDevice("product1", "variant1", DeviceAllocationState.Allocated));
        deviceList.add(createDevice("product2", "variant2", DeviceAllocationState.Unavailable));
        deviceList.add(createDevice("product3", "variant3", DeviceAllocationState.Ignored));
        EasyMock.expect(mMockDeviceManager.listAllDevices()).andReturn(deviceList);

        EasyMock.replay(mMockDeviceManager);
        final MultiMap<String, DeviceDescriptor> deviceMap =
                mScheduler.getDevices(mMockDeviceManager, true);
        EasyMock.verify(mMockDeviceManager);

        Assert.assertTrue(deviceMap.isEmpty());
    }

    private JSONObject createCommandTask(
            String requestId, String commandId, String taskId, String commandLine)
            throws JSONException {

        JSONObject ret = new JSONObject();
        ret.put(REQUEST_ID, requestId);
        ret.put(COMMAND_ID, commandId);
        ret.put(TASK_ID, taskId);
        ret.put("command_line", commandLine);
        return ret;
    }

    private JSONObject createLeaseResponse(JSONObject... tasks) throws JSONException {
        JSONObject response = new JSONObject();
        JSONArray array = new JSONArray();
        for (JSONObject task : tasks) {
            array.put(task);
        }
        response.put("tasks", array);
        return response;
    }

    public void testFetchCommands() throws IOException, JSONException {
        // Create some devices to fetch tasks for
        final MultiMap<String, DeviceDescriptor> deviceMap = new MultiMap<>();
        deviceMap.put(
                "product1:variant1",
                createDevice("product1", "variant1", DeviceAllocationState.Available));
        deviceMap.put(
                "product2:variant2",
                createDevice("product2", "variant2", DeviceAllocationState.Available));
        deviceMap.put(
                "product2:variant2",
                createDevice("product2", "variant2", DeviceAllocationState.Available));

        // Create some mock responses for the expected REST API calls
        JSONObject product1Response =
                createLeaseResponse(createCommandTask("1", "1", "0", "command line 1"));
        EasyMock.expect(
                        mMockApiHelper.execute(
                                EasyMock.eq("GET"),
                                EasyMock.aryEq(
                                        new String[] {
                                            "tasks", "lease", CLUSTER_ID, "product1:variant1"
                                        }),
                                EasyMock.eq(ImmutableMap.of("num_tasks", "1")),
                                (JSONObject) EasyMock.isNull()))
                .andReturn(buildHttpResponse(product1Response.toString()));

        JSONObject product2Response =
                createLeaseResponse(createCommandTask("1", "2", "0", "command line 2"));
        EasyMock.expect(
                        mMockApiHelper.execute(
                                EasyMock.eq("GET"),
                                EasyMock.aryEq(
                                        new String[] {
                                            "tasks", "lease", CLUSTER_ID, "product2:variant2"
                                        }),
                                EasyMock.eq(ImmutableMap.of("num_tasks", "1")),
                                (JSONObject) EasyMock.isNull()))
                .andReturn(buildHttpResponse(product2Response.toString()));

        // Actually fetch commands
        EasyMock.replay(mMockApiHelper, mMockEventUploader);
        final List<ClusterCommand> commands = mScheduler.fetchCommands(deviceMap);

        // Verify that the commands were fetched
        EasyMock.verify(mMockApiHelper, mMockEventUploader);
        // expect 1 command allocated per device type based on availability and fetching algorithm
        Assert.assertEquals("commad size mismatch", 2, commands.size());
    }

    public void testFetchCommands_multipleDevices() throws IOException, JSONException {
        // Create some devices to fetch tasks for
        final MultiMap<String, DeviceDescriptor> deviceMap = new MultiMap<>();
        deviceMap.put(
                "product1:variant1",
                createDevice("product1", "variant1", DeviceAllocationState.Available));
        deviceMap.put(
                "product1:variant1",
                createDevice("product1", "variant1", DeviceAllocationState.Available));
        deviceMap.put(
                "product1:variant1",
                createDevice("product1", "variant1", DeviceAllocationState.Available));
        deviceMap.put(
                "product1:variant1",
                createDevice("product1", "variant1", DeviceAllocationState.Available));

        // Create some mock responses for the expected REST API calls
        JSONObject product1Response =
                createLeaseResponse(
                        createCommandTask("1", "1", "0", "command line 1"),
                        createCommandTask("2", "2", "0", "command line 2"));
        EasyMock.expect(
                        mMockApiHelper.execute(
                                EasyMock.eq("GET"),
                                EasyMock.aryEq(
                                        new String[] {
                                            "tasks", "lease", CLUSTER_ID, "product1:variant1"
                                        }),
                                EasyMock.eq(ImmutableMap.of("num_tasks", "2")),
                                (JSONObject) EasyMock.isNull()))
                .andReturn(buildHttpResponse(product1Response.toString()));

        // Actually fetch commands
        EasyMock.replay(mMockApiHelper, mMockEventUploader);
        final List<ClusterCommand> commands = mScheduler.fetchCommands(deviceMap);

        // Verify that the commands were fetched
        EasyMock.verify(mMockApiHelper, mMockEventUploader);
        Assert.assertEquals(2, commands.size());

        // Verify that all devices would be attempted per command
        for (ClusterCommand cmd : commands) {
            Assert.assertEquals(4, cmd.getTargetDeviceSerials().size());
        }
    }

    public void testFetchCommands_withNextClusters() throws IOException, JSONException {
        // Create some devices to fetch tasks for
        final MultiMap<String, DeviceDescriptor> deviceMap = new MultiMap<>();
        deviceMap.put(
                "product1:variant1",
                createDevice("product1", "variant1", DeviceAllocationState.Available));
        deviceMap.put(
                "product1:variant1",
                createDevice("product1", "variant1", DeviceAllocationState.Available));
        deviceMap.put(
                "product1:variant1",
                createDevice("product1", "variant1", DeviceAllocationState.Available));
        deviceMap.put(
                "product1:variant1",
                createDevice("product1", "variant1", DeviceAllocationState.Available));

        mMockClusterOptions.getNextClusterIds().add("cluster2");
        mMockClusterOptions.getNextClusterIds().add("cluster3");

        // Create some mock responses for the expected REST API calls
        Map<String, Object> options = new HashMap<>();
        options.put("num_tasks", "2");
        options.put("next_cluster_ids", mMockClusterOptions.getNextClusterIds());
        JSONObject product1Response =
                createLeaseResponse(createCommandTask("1", "1", "0", "command line 1"));
        EasyMock.expect(
                        mMockApiHelper.execute(
                                EasyMock.eq("GET"),
                                EasyMock.aryEq(
                                        new String[] {
                                            "tasks", "lease", CLUSTER_ID, "product1:variant1"
                                        }),
                                EasyMock.eq(options),
                                (JSONObject) EasyMock.isNull()))
                .andReturn(buildHttpResponse(product1Response.toString()));

        // Actually fetch commands
        EasyMock.replay(mMockApiHelper, mMockEventUploader);
        final List<ClusterCommand> commands = mScheduler.fetchCommands(deviceMap);

        // Verify that the commands were fetched
        EasyMock.verify(mMockApiHelper, mMockEventUploader);
        Assert.assertEquals(1, commands.size());

        // Verify that all devices would be attempted per command
        for (ClusterCommand cmd : commands) {
            Assert.assertEquals(4, cmd.getTargetDeviceSerials().size());
        }
    }

    /**
     * Test that we can allocate one last device available
     */
    public void testFetchCommands_LastDevice() throws Exception {
        // Create some devices to fetch tasks for
        final MultiMap<String, DeviceDescriptor> deviceMap = new MultiMap<>();
        deviceMap.put(
                "product1:variant1",
                createDevice("product1", "variant1", DeviceAllocationState.Available));

        // Create some mock responses for the expected REST API calls
        JSONObject product1Response =
                createLeaseResponse(createCommandTask("1", "1", "0", "command line 1"));
        EasyMock.expect(
                        mMockApiHelper.execute(
                                EasyMock.eq("GET"),
                                EasyMock.aryEq(
                                        new String[] {
                                            "tasks", "lease", CLUSTER_ID, "product1:variant1"
                                        }),
                                EasyMock.eq(ImmutableMap.of("num_tasks", "1")),
                                (JSONObject) EasyMock.isNull()))
                .andReturn(buildHttpResponse(product1Response.toString()));

        // Actually fetch commands
        EasyMock.replay(mMockApiHelper, mMockEventUploader);
        final List<ClusterCommand> commands = mScheduler.fetchCommands(deviceMap);

        // Verify that the commands were fetched
        EasyMock.verify(mMockApiHelper, mMockEventUploader);
        Assert.assertFalse("no commands fetched", commands.isEmpty());
    }

    public void testFetchCommands_NoCommands() throws IOException {
        // Create some devices to fetch tasks for
        final MultiMap<String, DeviceDescriptor> deviceMap = new MultiMap<>();
        deviceMap.put(
                "product1:variant1",
                createDevice("product1", "variant1", DeviceAllocationState.Available));
        deviceMap.put(
                "product2:variant2",
                createDevice("product2", "variant2", DeviceAllocationState.Available));
        deviceMap.put(
                "product2:variant2",
                createDevice("product2", "variant2", DeviceAllocationState.Available));

        // Create some mock responses for the expected REST API calls. Return no work.
        EasyMock.expect(
                        mMockApiHelper.execute(
                                EasyMock.eq("GET"),
                                EasyMock.aryEq(
                                        new String[] {
                                            "tasks", "lease", CLUSTER_ID, "product1:variant1"
                                        }),
                                EasyMock.eq(ImmutableMap.of("num_tasks", "1")),
                                (JSONObject) EasyMock.isNull()))
                .andReturn(buildHttpResponse(""));

        EasyMock.expect(
                        mMockApiHelper.execute(
                                EasyMock.eq("GET"),
                                EasyMock.aryEq(
                                        new String[] {
                                            "tasks", "lease", CLUSTER_ID, "product2:variant2"
                                        }),
                                EasyMock.eq(ImmutableMap.of("num_tasks", "1")),
                                (JSONObject) EasyMock.isNull()))
                .andReturn(buildHttpResponse(""));

        // Actually fetch commands
        EasyMock.replay(mMockApiHelper, mMockEventUploader);
        final List<ClusterCommand> commands = mScheduler.fetchCommands(deviceMap);

        // Verify that we tried to fetch some commands, but didn't get any
        EasyMock.verify(mMockApiHelper, mMockEventUploader);
        Assert.assertEquals(0, commands.size());
    }

    public void testFetchHostCommands() throws Exception {
        // Create some devices to fetch tasks for
        mMockClusterOptions.getDeviceGroup().put("group1", "s1");
        mMockClusterOptions.getDeviceGroup().put("group1", "s2");
        DeviceDescriptor d1 =
                createDevice("s1", "product1", "variant1", DeviceAllocationState.Available);
        DeviceDescriptor d2 =
                createDevice("s2", "product2", "variant2", DeviceAllocationState.Available);
        DeviceDescriptor d3 =
                createDevice("s3", "product2", "variant2", DeviceAllocationState.Available);
        String runTarget1 = "product1:variant1";
        String runTarget2 = "product2:variant2";
        final MultiMap<String, DeviceDescriptor> deviceMap = new MultiMap<>();
        deviceMap.put(runTarget1, d1);
        deviceMap.put(runTarget2, d2);
        deviceMap.put(runTarget2, d3);

        mMockClusterOptions.getNextClusterIds().add("cluster2");
        mMockClusterOptions.getNextClusterIds().add("cluster3");

        Capture<JSONObject> capture = new Capture<>();
        // Create some mock responses for the expected REST API calls
        JSONObject product1Response =
                createLeaseResponse(createCommandTask("1", "1", "0", "command line 1"));
        EasyMock.expect(
                        mMockApiHelper.execute(
                                EasyMock.eq("POST"),
                                EasyMock.aryEq(new String[] {"tasks", "leasehosttasks"}),
                                EasyMock.eq(
                                        ImmutableMap.of(
                                                "cluster",
                                                CLUSTER_ID,
                                                "hostname",
                                                ClusterCommandScheduler.getHostName())),
                                EasyMock.capture(capture)))
                .andReturn(buildHttpResponse(product1Response.toString()));
        // Actually fetch commands
        EasyMock.replay(mMockApiHelper, mMockEventUploader);
        final List<ClusterCommand> commands = mScheduler.fetchHostCommands(deviceMap);

        // Verity the http request body is correct.
        JSONArray deviceInfos = capture.getValue().getJSONArray("device_infos");
        JSONArray clusterIds = capture.getValue().getJSONArray("next_cluster_ids");
        Assert.assertEquals("group1", deviceInfos.getJSONObject(0).get("group_name"));
        Assert.assertEquals("s1", deviceInfos.getJSONObject(0).get("device_serial"));
        Assert.assertEquals("group1", deviceInfos.getJSONObject(1).get("group_name"));
        Assert.assertEquals("s2", deviceInfos.getJSONObject(1).get("device_serial"));
        Assert.assertFalse(deviceInfos.getJSONObject(2).has("group_name"));
        Assert.assertEquals("s3", deviceInfos.getJSONObject(2).get("device_serial"));
        Assert.assertEquals("cluster2", clusterIds.getString(0));
        Assert.assertEquals("cluster3", clusterIds.getString(1));
        // Verify that the commands were fetched
        EasyMock.verify(mMockApiHelper, mMockEventUploader);
        // expect 1 command allocated per device type based on availability and fetching algorithm
        Assert.assertEquals("commad size mismatch", 1, commands.size());
    }

    // Test default run target if nothing is specified.
    public void testGetDefaultRunTarget() {
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Assert.assertEquals(
                "product:productVariant", ClusterCommandScheduler.getRunTarget(device, null, null));
    }

    // Test default run target if nothing is specified, and product == product variant.
    public void testGetDefaultRunTargetWithSameProductAndProductVariant() {
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "product",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Assert.assertEquals("product", ClusterCommandScheduler.getRunTarget(device, null, null));
    }

    // If a constant string run target pattern is set, always return said pattern.
    public void testSimpleConstantRunTargetMatchPattern() {
        String format = "foo";
        mMockClusterOptions.setRunTargetFormat(format);
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Assert.assertEquals("foo", ClusterCommandScheduler.getRunTarget(device, format, null));
    }

    // Test run target pattern with a device tag map
    public void testDeviceTagRunTargetMatchPattern_simple() {
        String format = "{TAG}";
        mMockClusterOptions.setRunTargetFormat(format);
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Map<String, String> deviceTag = new HashMap<>();
        deviceTag.put(DEVICE_SERIAL, "foo");
        Assert.assertEquals("foo",
                ClusterCommandScheduler.getRunTarget(device, format, deviceTag));
    }

    // Test run target pattern with a device tag map, but the device serial is not in map
    public void testDeviceTagRunTargetMatchPattern_missingSerial() {
        String format = "foo{TAG}bar";
        mMockClusterOptions.setRunTargetFormat(format);
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Map<String, String> deviceTag = Collections.emptyMap();
        Assert.assertEquals("foobar",
                ClusterCommandScheduler.getRunTarget(device, format, deviceTag));
    }

    // Ensure that invalid run target pattern throws an exception.
    public void testInvalidRunTargetMetachPattern() {
        String format = "foo-{INVALID PATTERN}";
        mMockClusterOptions.setRunTargetFormat(format);
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        try {
            ClusterCommandScheduler.getRunTarget(device, format, null);
            fail("Should have thrown an InvalidParameter exception.");
        } catch (InvalidParameterException e) {
            // expected.
        }
    }

    // Test all supported run target match patterns.
    public void testSupportedRunTargetMatchPattern() {
        String format = "foo-{PRODUCT}-{PRODUCT_VARIANT}-{API_LEVEL}-{DEVICE_PROP:bar}";
        mMockClusterOptions.setRunTargetFormat(format);
        IDevice mockIDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mockIDevice.getProperty("bar")).andReturn("zzz");
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceState.ONLINE,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel",
                        "",
                        "",
                        "",
                        "",
                        mockIDevice);
        EasyMock.replay(mockIDevice);
        Assert.assertEquals(
                "foo-product-productVariant-sdkVersion-zzz",
                ClusterCommandScheduler.getRunTarget(device, format, null));
        EasyMock.verify(mockIDevice);
        // The pattern should remain unchanged.
        Assert.assertEquals(format, mMockClusterOptions.getRunTargetFormat());
    }

    // Test all supported run target match patterns with unknown property.
    public void testSupportedRunTargetMatchPattern_unknownProperty() {
        String format = "foo-{PRODUCT}-{PRODUCT_VARIANT}-{API_LEVEL}";
        mMockClusterOptions.setRunTargetFormat(format);
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        DeviceManager.UNKNOWN_DISPLAY_STRING,
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Assert.assertEquals(
                DeviceManager.UNKNOWN_DISPLAY_STRING,
                ClusterCommandScheduler.getRunTarget(device, format, null));
        // The pattern should remain unchanged.
        Assert.assertEquals(
                "foo-{PRODUCT}-{PRODUCT_VARIANT}-{API_LEVEL}",
                mMockClusterOptions.getRunTargetFormat());
    }

    // Test when the run target pattern contains repeated patterns.
    public void testRepeatedPattern() {
        String format = "foo-{PRODUCT}-{PRODUCT}:{PRODUCT_VARIANT}";
        mMockClusterOptions.setRunTargetFormat(format);
        DeviceDescriptor device =
                new DeviceDescriptor(
                        DEVICE_SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "product",
                        "productVariant",
                        "sdkVersion",
                        "buildId",
                        "batteryLevel");
        Assert.assertEquals(
                "foo-product-product:productVariant",
                ClusterCommandScheduler.getRunTarget(device, format, null));
    }

    // Test default behavior when device serial is not set for command task.
    public void testExecCommandsWithNoSerials() {
        List<ClusterCommand> cmds = new ArrayList<>();
        ClusterCommand cmd = new ClusterCommand(COMMAND_ID, TASK_ID, CMD_LINE);
        cmds.add(cmd);
        mScheduler.execCommands(cmds);
        Assert.assertEquals(CMD_LINE, cmds.get(0).getCommandLine());
        Assert.assertArrayEquals(new String[] {CMD_LINE}, getExecCommandArgs());
    }

    // If device serial is specified for a command task append serial to it.
    public void testExecCommandWithSerial() {
        List<ClusterCommand> cmds = new ArrayList<>();
        ClusterCommand cmd = new ClusterCommand(COMMAND_ID, TASK_ID, CMD_LINE);
        cmd.setTargetDeviceSerials(ArrayUtil.list("deviceSerial"));
        cmds.add(cmd);
        mScheduler.execCommands(cmds);
        Assert.assertEquals(CMD_LINE, cmds.get(0).getCommandLine());
        Assert.assertArrayEquals(
                new String[] {CMD_LINE, "--serial", "deviceSerial"}, getExecCommandArgs());
    }

    // Multiple serials specified for a command task.
    public void testExecCommandWithMultipleSerials() {
        List<ClusterCommand> cmds = new ArrayList<>();
        ClusterCommand cmd = new ClusterCommand(COMMAND_ID, TASK_ID, CMD_LINE);
        cmd.setTargetDeviceSerials(
                ArrayUtil.list("deviceSerial0", "deviceSerial1", "deviceSerial2"));
        cmds.add(cmd);
        mScheduler.execCommands(cmds);
        Assert.assertEquals(CMD_LINE, cmds.get(0).getCommandLine());
        Assert.assertArrayEquals(
                new String[] {
                    CMD_LINE,
                    "--serial",
                    "deviceSerial0",
                    "--serial",
                    "deviceSerial1",
                    "--serial",
                    "deviceSerial2"
                },
                getExecCommandArgs());
    }

    // Multiple serials specified for multiple commands.
    public void testExecCommandWithMultipleCommandsAndSerials() {
        List<String> serials = ArrayUtil.list("deviceSerial0", "deviceSerial1", "deviceSerial2");
        List<ClusterCommand> cmds = new ArrayList<>();
        ClusterCommand cmd0 = new ClusterCommand("command_id0", "task_id0", CMD_LINE);
        cmd0.setTargetDeviceSerials(serials);
        cmds.add(cmd0);
        ClusterCommand cmd1 = new ClusterCommand("command_id1", "task_id1", "test1");
        cmd1.setTargetDeviceSerials(serials);
        cmds.add(cmd1);
        mScheduler.execCommands(cmds);
        Assert.assertEquals(CMD_LINE, cmds.get(0).getCommandLine());
        Assert.assertEquals("test1", cmds.get(1).getCommandLine());
        Assert.assertArrayEquals(
                new String[] {
                    "test1",
                    "--serial",
                    "deviceSerial0",
                    "--serial",
                    "deviceSerial1",
                    "--serial",
                    "deviceSerial2"
                },
                getExecCommandArgs());
        Assert.assertArrayEquals(
                new String[] {
                    CMD_LINE,
                    "--serial",
                    "deviceSerial0",
                    "--serial",
                    "deviceSerial1",
                    "--serial",
                    "deviceSerial2"
                },
                getExecCommandArgs());
    }

    // Test a valid TF version
    public void testToValidTfVersion() {
        String version = "12345";
        String actual = ClusterCommandScheduler.toValidTfVersion(version);
        Assert.assertEquals(version, actual);
    }

    // Test an empty TF version
    public void testToValidTfVersionWithEmptyVersion() {
        String version = "";
        String actual = ClusterCommandScheduler.toValidTfVersion(version);
        Assert.assertEquals(ClusterCommandScheduler.DEFAULT_TF_VERSION, actual);
    }

    // Test a null TF version
    public void testToValidTfVersionWithNullVersion() {
        String version = null;
        String actual = ClusterCommandScheduler.toValidTfVersion(version);
        Assert.assertEquals(ClusterCommandScheduler.DEFAULT_TF_VERSION, actual);
    }

    // Test an invalid TF version
    public void testToValidTfVersionWithInvalidVersion() {
        String version = "1abcd2efg";
        String actual = ClusterCommandScheduler.toValidTfVersion(version);
        Assert.assertEquals(ClusterCommandScheduler.DEFAULT_TF_VERSION, actual);
    }

    static ClusterCommandEvent checkClusterCommandEvent(ClusterCommandEvent.Type type) {
        EasyMock.reportMatcher(
                new IArgumentMatcher() {
                    @Override
                    public boolean matches(Object object) {
                        if (!(object instanceof ClusterCommandEvent)) {
                            return false;
                        }

                        ClusterCommandEvent actual = (ClusterCommandEvent) object;
                        return (TASK_ID.equals(actual.getCommandTaskId())
                                && DEVICE_SERIAL.equals(actual.getDeviceSerial())
                                && actual.getType() == type);
                    }

                    @Override
                    public void appendTo(StringBuffer buffer) {
                        buffer.append("checkEvent(");
                        buffer.append(type);
                        buffer.append(")");
                    }
                });
        return null;
    }

    public void testInvocationEventHandler() {
        ClusterCommand mockCommand = new ClusterCommand(COMMAND_ID, TASK_ID, CMD_LINE);
        IBuildInfo mockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo(DEVICE_SERIAL, mockBuildInfo);
        ITestDevice mockTestDevice = EasyMock.createMock(ITestDevice.class);
        ClusterCommandScheduler.InvocationEventHandler handler =
                mScheduler.new InvocationEventHandler(mockCommand);
        EasyMock.expect(mockBuildInfo.getDeviceSerial()).andReturn(DEVICE_SERIAL);

        mMockEventUploader.postEvent(
                checkClusterCommandEvent(ClusterCommandEvent.Type.InvocationStarted));
        mMockEventUploader.flush();
        mMockEventUploader.postEvent(
                checkClusterCommandEvent(ClusterCommandEvent.Type.TestRunInProgress));
        EasyMock.expectLastCall().anyTimes();
        mMockEventUploader.postEvent(
                checkClusterCommandEvent(ClusterCommandEvent.Type.InvocationEnded));
        mMockEventUploader.flush();
        Capture<ClusterCommandEvent> capture = new Capture<>();
        mMockEventUploader.postEvent(EasyMock.capture(capture));
        mMockEventUploader.flush();

        EasyMock.replay(mMockEventUploader, mockBuildInfo, mockTestDevice);
        handler.invocationStarted(context);
        handler.testRunStarted("test run", 1);
        handler.testStarted(new TestIdentifier("class", CMD_LINE));
        handler.testEnded(new TestIdentifier("class", CMD_LINE), Collections.emptyMap());
        handler.testRunEnded(10L, Collections.emptyMap());
        handler.invocationEnded(100L);
        context.addAllocatedDevice(DEVICE_SERIAL, mockTestDevice);
        Map<ITestDevice, FreeDeviceState> releaseMap = new HashMap<>();
        releaseMap.put(mockTestDevice, FreeDeviceState.AVAILABLE);
        handler.invocationComplete(context, releaseMap);
        EasyMock.verify(mMockEventUploader, mockBuildInfo, mockTestDevice);
        ClusterCommandEvent capturedEvent = capture.getValue();
        assertTrue(capturedEvent.getType().equals(ClusterCommandEvent.Type.InvocationCompleted));
        // Ensure we have not raised an unexpected error
        assertNull(capturedEvent.getData().get(ClusterCommandEvent.DATA_KEY_ERROR));
        assertEquals(
                "0", capturedEvent.getData().get(ClusterCommandEvent.DATA_KEY_FAILED_TEST_COUNT));
    }

    /** Test that the error count is the proper one. */
    public void testInvocationEventHandler_counting() {
        ClusterCommand mockCommand = new ClusterCommand(COMMAND_ID, TASK_ID, CMD_LINE);
        IBuildInfo mockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo(DEVICE_SERIAL, mockBuildInfo);
        ITestDevice mockTestDevice = EasyMock.createMock(ITestDevice.class);
        ClusterCommandScheduler.InvocationEventHandler handler =
                mScheduler.new InvocationEventHandler(mockCommand);
        EasyMock.expect(mockBuildInfo.getDeviceSerial()).andReturn(DEVICE_SERIAL);

        mMockEventUploader.postEvent(
                checkClusterCommandEvent(ClusterCommandEvent.Type.InvocationStarted));
        mMockEventUploader.flush();
        mMockEventUploader.postEvent(
                checkClusterCommandEvent(ClusterCommandEvent.Type.TestRunInProgress));
        EasyMock.expectLastCall().anyTimes();
        mMockEventUploader.postEvent(
                checkClusterCommandEvent(ClusterCommandEvent.Type.InvocationEnded));
        mMockEventUploader.flush();
        Capture<ClusterCommandEvent> capture = new Capture<>();
        mMockEventUploader.postEvent(EasyMock.capture(capture));
        mMockEventUploader.flush();

        EasyMock.replay(mMockEventUploader, mockBuildInfo, mockTestDevice);
        handler.invocationStarted(context);
        handler.testRunStarted("test run", 1);
        TestIdentifier tid = new TestIdentifier("class", CMD_LINE);
        handler.testStarted(tid);
        handler.testFailed(tid, "failed");
        handler.testEnded(tid, Collections.emptyMap());
        TestIdentifier tid2 = new TestIdentifier("class", "test2");
        handler.testStarted(tid2);
        handler.testAssumptionFailure(tid2, "I assume I failed");
        handler.testEnded(tid2, Collections.emptyMap());
        handler.testRunEnded(10L, Collections.emptyMap());
        handler.invocationEnded(100L);
        context.addAllocatedDevice(DEVICE_SERIAL, mockTestDevice);
        Map<ITestDevice, FreeDeviceState> releaseMap = new HashMap<>();
        releaseMap.put(mockTestDevice, FreeDeviceState.AVAILABLE);
        handler.invocationComplete(context, releaseMap);
        EasyMock.verify(mMockEventUploader, mockBuildInfo, mockTestDevice);
        ClusterCommandEvent capturedEvent = capture.getValue();
        assertTrue(capturedEvent.getType().equals(ClusterCommandEvent.Type.InvocationCompleted));
        // Ensure we have not raised an unexpected error
        assertNull(capturedEvent.getData().get(ClusterCommandEvent.DATA_KEY_ERROR));
        // We only count test failure and not assumption failures.
        assertEquals(
                "1", capturedEvent.getData().get(ClusterCommandEvent.DATA_KEY_FAILED_TEST_COUNT));
    }

    public void testInvocationEventHandler_longTestRun() {
        ClusterCommand mockCommand = new ClusterCommand(COMMAND_ID, TASK_ID, CMD_LINE);
        IBuildInfo mockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo(DEVICE_SERIAL, mockBuildInfo);
        ITestDevice mockTestDevice = EasyMock.createMock(ITestDevice.class);
        ClusterCommandScheduler.InvocationEventHandler handler =
                mScheduler.new InvocationEventHandler(mockCommand);
        EasyMock.expect(mockBuildInfo.getDeviceSerial()).andReturn(DEVICE_SERIAL);
        mMockEventUploader.postEvent(
                checkClusterCommandEvent(ClusterCommandEvent.Type.InvocationStarted));
        mMockEventUploader.flush();
        mMockEventUploader.postEvent(
                checkClusterCommandEvent(ClusterCommandEvent.Type.TestRunInProgress));
        EasyMock.expectLastCall().anyTimes();
        mMockEventUploader.postEvent(
                checkClusterCommandEvent(ClusterCommandEvent.Type.InvocationEnded));
        mMockEventUploader.flush();
        mMockEventUploader.postEvent(
                checkClusterCommandEvent(ClusterCommandEvent.Type.InvocationCompleted));
        mMockEventUploader.flush();

        EasyMock.replay(mMockEventUploader, mockBuildInfo, mockTestDevice);
        handler.invocationStarted(context);
        handler.testRunStarted("test run", 1);
        mMockCurrentTimeMillis += 10 * 60 * 1000;
        handler.testStarted(new TestIdentifier("class", CMD_LINE));
        handler.testEnded(new TestIdentifier("class", CMD_LINE), Collections.emptyMap());
        handler.testRunEnded(10L, Collections.emptyMap());
        handler.invocationEnded(100L);
        context.addAllocatedDevice(DEVICE_SERIAL, mockTestDevice);
        Map<ITestDevice, FreeDeviceState> releaseMap = new HashMap<>();
        releaseMap.put(mockTestDevice, FreeDeviceState.AVAILABLE);
        handler.invocationComplete(context, releaseMap);
        EasyMock.verify(mMockEventUploader, mockBuildInfo, mockTestDevice);
    }

    /**
     * Test that when dry-run is used we validate the config and no ConfigurationException gets
     * thrown.
     */
    public void testExecCommandsWithDryRun() {
        mScheduler =
                new ClusterCommandScheduler() {
                    @Override
                    long getCurrentTimeMillis() {
                        return mMockCurrentTimeMillis;
                    }

                    @Override
                    public IClusterOptions getClusterOptions() {
                        return mMockClusterOptions;
                    }

                    @Override
                    IClusterClient getClusterClient() {
                        return mMockClusterClient;
                    }

                    @Override
                    public void execCommand(IScheduledInvocationListener listener, String[] args)
                            throws ConfigurationException, NoDeviceException {
                        ArrayList<String> execCmdArgs = new ArrayList<>();
                        for (String arg : args) {
                            execCmdArgs.add(arg);
                        }
                        mExecCmdArgs.push(execCmdArgs);
                    }

                    @Override
                    protected IKeyStoreClient getKeyStoreClient() {
                        return new StubKeyStoreClient();
                    }
                };
        ClusterCommand cmd = new ClusterCommand(COMMAND_ID, TASK_ID, "empty --dry-run");
        mScheduler.execCommands(Arrays.asList(cmd));
        Assert.assertEquals("empty --dry-run", cmd.getCommandLine());
        // Nothing gets executed
        Assert.assertTrue(mExecCmdArgs.isEmpty());
    }

    // Helper class for more functional like tests.
    private class TestableClusterCommandScheduler extends ClusterCommandScheduler {

        private IDeviceManager manager = new MockDeviceManager(1);

        @Override
        long getCurrentTimeMillis() {
            return mMockCurrentTimeMillis;
        }

        @Override
        public IClusterOptions getClusterOptions() {
            return mMockClusterOptions;
        }

        @Override
        IClusterClient getClusterClient() {
            return mMockClusterClient;
        }

        @Override
        protected boolean dryRunCommand(final InvocationEventHandler handler, String[] args) {
            return false;
        }

        @Override
        protected IDeviceManager getDeviceManager() {
            return manager;
        }

        // Direct getter to avoid making getDeviceManager public.
        public IDeviceManager getTestManager() {
            return manager;
        }

        @Override
        protected void initLogging() {
            // ignore
        }

        @Override
        protected void cleanUp() {
            // ignore
        }
    }

    /** Test that when a provider returns a null build, we still handle it gracefully. */
    public void testExecCommands_nullBuild() throws Exception {
        try {
            GlobalConfiguration.createGlobalConfiguration(new String[] {});
        } catch (IllegalStateException e) {
            // in case Global config is already initialized.
        }
        TestableClusterCommandScheduler scheduler = new TestableClusterCommandScheduler();
        List<ClusterCommand> cmds = new ArrayList<>();
        // StubBuildProvider of empty.xml can return a null instead of build with --return-null
        ClusterCommand cmd = new ClusterCommand(COMMAND_ID, TASK_ID, "empty --return-null");
        cmds.add(cmd);
        IDeviceManager m = scheduler.getTestManager();
        scheduler.start();
        try {
            // execCommands is going to allocate a device to execute the command.
            scheduler.execCommands(cmds);
            assertEquals(0, ((MockDeviceManager) m).getQueueOfAvailableDeviceSize());
            Assert.assertEquals("empty --return-null", cmds.get(0).getCommandLine());
            scheduler.shutdownOnEmpty();
            scheduler.join(2000);
            // There is only one device so allocation should succeed if device was released.
            assertEquals(1, ((MockDeviceManager) m).getQueueOfAvailableDeviceSize());
            ITestDevice device = m.allocateDevice();
            assertNotNull(device);
        } finally {
            scheduler.shutdown();
        }
    }

    /** Test that when a provider throws a build error retrieval, we still handle it gracefully. */
    public void testExecCommands_buildRetrievalError() throws Exception {
        try {
            // we need to initialize the GlobalConfiguration when running directly in IDE.
            GlobalConfiguration.createGlobalConfiguration(new String[] {});
        } catch (IllegalStateException e) {
            // in case Global config is already initialized, when running in the infra.
        }
        TestableClusterCommandScheduler scheduler = new TestableClusterCommandScheduler();
        File tmpLogDir = FileUtil.createTempDir("clusterschedulertest");
        List<ClusterCommand> cmds = new ArrayList<>();
        // StubBuildProvider of empty.xml can throw a build error if requested via
        // --throw-build-error
        ClusterCommand cmd =
                new ClusterCommand(
                        COMMAND_ID,
                        TASK_ID,
                        "empty --throw-build-error --log-file-path " + tmpLogDir.getAbsolutePath());
        cmds.add(cmd);
        IDeviceManager m = scheduler.getTestManager();
        scheduler.start();
        try {
            scheduler.execCommands(cmds);
            assertEquals(0, ((MockDeviceManager) m).getQueueOfAvailableDeviceSize());
            scheduler.shutdownOnEmpty();
            scheduler.join(5000);
            // There is only one device so allocation should succeed if device was released.
            assertEquals(1, ((MockDeviceManager) m).getQueueOfAvailableDeviceSize());
            ITestDevice device = m.allocateDevice();
            assertNotNull(device);
        } finally {
            scheduler.shutdown();
            FileUtil.recursiveDelete(tmpLogDir);
        }
    }

    /**
     * Ensure that we do not thrown an exception from scheduling the heartbeat after calling
     * shutdown on the thread pool.
     */
    public void testShutdownHearbeat() throws Exception {
        TestableClusterCommandScheduler scheduler = new TestableClusterCommandScheduler();
        scheduler.getHeartbeatThreadPool().shutdown();
        scheduler
                .getHeartbeatThreadPool()
                .scheduleAtFixedRate(
                        new Runnable() {
                            @Override
                            public void run() {
                                RunUtil.getDefault().sleep(500);
                            }
                        },
                        0,
                        100,
                        TimeUnit.MILLISECONDS);
        boolean res = scheduler.getHeartbeatThreadPool().awaitTermination(5, TimeUnit.SECONDS);
        assertTrue("HeartBeat scheduler did not terminate.", res);
    }
}
