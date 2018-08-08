// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.command.CommandScheduler;
import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NoDeviceException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.sandbox.ISandbox;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.VersionParser;

import com.google.android.tradefed.sandbox.GoogleTradefedSandbox;
import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.Longs;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link ICommandScheduler} to support TFC (Tradefed Cluster).
 * This scheduler runs commands from TFC command-queue and uploads invocation events to
 * TFC command-event-queue.
 */
public class ClusterCommandScheduler extends CommandScheduler {

    static final String DEFAULT_TF_VERSION = "(unknown)";

    private static String mHostName = null;

    /** The {@link ScheduledThreadPoolExecutor} used to manage heartbeats. */
    private static ScheduledThreadPoolExecutor sHeartbeatThreadPool = null;

    /** The {@link IClusterOptions} instance used to store cluster-related settings. */
    private IClusterOptions mClusterOptions;

    /** The {@link IClusterClient} instance used to interact with the TFC backend. */
    private IClusterClient mClusterClient;

    /**
     * A {@link ThreadFactory} which returns threads in a dedicated heartbeat group.
     *
     * <p>This class is used as a factory by {@code mHeartbeatThreadPool} in order to segregate
     * heartbeat threads from other "stray" threads to avoid tripping loose thread detection in
     * {@link CommandScheduler}.
     */
    private static class HeartbeatThreadFactory implements ThreadFactory {
        private static final ThreadGroup HB_GROUP =
                new ThreadGroup("ClusterCommandScheduler.heartbeat");

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(HB_GROUP, r);
        }
    }

    /**
     * Gets the hostname.
     *
     * @return the hostname or null if we were unable to fetch it.
     */
    public static String getHostName() {
        if (mHostName == null) {
            try {
                mHostName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                CLog.w("failed to get hostname: %s", e);
            }
        }
        return mHostName;
    }

    /**
     * Returns the current system time.
     *
     * @return time in millis.
     */
    long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Gets the TF version running on this host.
     *
     * @return this host's TF version.
     */
    public static String getTfVersion() {
        final String version = VersionParser.fetchVersion();
        return toValidTfVersion(version);
    }

    /**
     * Validates a TF version and returns it if it is OK.
     *
     * @param version The string for a TF version provided by {@link VersionParser}
     * @return the version if valid or a default if not.
     */
    protected static String toValidTfVersion(String version) {
        if(Strings.isNullOrEmpty(version) || Longs.tryParse(version) == null) {
            // Making sure the version is valid. It should be a build number.
            return DEFAULT_TF_VERSION;
        }

        return version;
    }

    /**
     * Returns the run target for a given device descriptor.
     *
     * @param device {@link DeviceDescriptor} to get run target for.
     * @return run target.
     */
    public static String getRunTarget(DeviceDescriptor device, String runTargetFormat,
            Map<String, String> deviceTags) {
        if (runTargetFormat != null) {
            // Make sure the pattern is non-greedy.
            Pattern p = Pattern.compile("\\{([^:\\}]+)(:.*)?\\}");
            Matcher m = p.matcher(runTargetFormat);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String pattern = m.group(1);
                String key = null;
                String txt = null;
                switch (pattern) {
                    case "PRODUCT":
                        txt = device.getProduct();
                        break;
                    case "PRODUCT_VARIANT":
                        txt = device.getProductVariant();
                        break;
                    case "API_LEVEL":
                        txt = device.getSdkVersion();
                        break;
                    case "DEVICE_CLASS":
                        txt = device.getDeviceClass();
                        break;
                    case "SERIAL":
                        txt = device.getSerial();
                        break;
                    case "TAG":
                        if (deviceTags == null || deviceTags.isEmpty()) {
                            // simply delete the placeholder if there's nothing to match
                            txt = "";
                        } else {
                            txt = deviceTags.get(device.getSerial());
                            if (txt == null) {
                                txt = ""; // simply delete it if a tag does not exist
                            }
                        }
                        break;
                    case "DEVICE_PROP":
                        key = m.group(2).substring(1);
                        txt = device.getProperty(key);
                        break;
                    default:
                        throw new InvalidParameterException(
                                String.format("Unsupported pattern '%s' found for run target '%s'",
                                        pattern, runTargetFormat));
                }
                if (txt == null || DeviceManager.UNKNOWN_DISPLAY_STRING.equals(txt)) {
                    CLog.i("No value found for pattern %s while formatting run target %s.",
                            pattern, runTargetFormat);
                    return DeviceManager.UNKNOWN_DISPLAY_STRING;
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(txt));
            }
            m.appendTail(sb);
            return sb.toString();
        }
        // Default behavior.
        // TODO: Remove this when we cluster default run target is changed.
        String runTarget = device.getProduct();
        if (!runTarget.equals(device.getProductVariant())) {
            runTarget += ":" + device.getProductVariant();
        }
        return runTarget;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        super.start();
    }

    /** {@inheritDoc} */
    @Override
    public void shutdown() {
        getHeartbeatThreadPool().shutdown();
        super.shutdown();
    }

    /**
     * A {@link com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener} to
     * upload events to TFC.
     */
    class InvocationEventHandler extends CollectingTestListener
            implements IScheduledInvocationListener, ITestSummaryListener {

        private ScheduledFuture<?> mHeartbeat;
        private final ClusterCommand mCommandTask;
        private String mDeviceSerial;
        private String mSummary;
        private String mError;

        /**
         * Creates a {@link InvocationEventHandler} to track the given {@link ClusterCommand}.
         *
         * @param commandTask the {@link ClusterCommand} to track.
         */
        public InvocationEventHandler(ClusterCommand commandTask) {
            mCommandTask = commandTask;
        }

        private ClusterCommandEvent.Builder createEventBuilder() {
            final ClusterCommandEvent.Builder builder = ClusterCommandEvent.createEventBuilder(
                    mCommandTask).setHostName(getHostName());
            if (mDeviceSerial != null) {
                builder.setDeviceSerial(mDeviceSerial);
            }
            return builder;
        }


        /** {@inheritDoc} */
        @Override
        public void invocationStarted(IInvocationContext context) {
            super.invocationStarted(context);
            mDeviceSerial = getPrimaryBuildInfo().getDeviceSerial();
            final ClusterCommandEvent event = createEventBuilder()
                    .setType(ClusterCommandEvent.Type.InvocationStarted)
                    .build();
            getClusterClient().getCommandEventUploader().postEvent(event);
            getClusterClient().getCommandEventUploader().flush();
            mHeartbeat = startHeartbeat();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void invocationFailed(Throwable cause) {
            super.invocationFailed(cause);

            mError = cause.toString();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void invocationEnded(long elapsedTime) {
            super.invocationEnded(elapsedTime);

            ClusterCommandEvent event = createEventBuilder()
                    .setType(ClusterCommandEvent.Type.InvocationEnded)
                    .setData(ClusterCommandEvent.DATA_KEY_ERROR, mError)
                    .build();
            getClusterClient().getCommandEventUploader().postEvent(event);
            getClusterClient().getCommandEventUploader().flush();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void invocationComplete(IInvocationContext metadata,
                Map<ITestDevice, FreeDeviceState> devicesStates) {
            // TODO: handle multi-device where only one of the build could be missing.
            if (getPrimaryBuildInfo() == null && mError == null) {
                mError = "build not found";
            }
            final ClusterCommandEvent event =
                    createEventBuilder()
                            .setType(ClusterCommandEvent.Type.InvocationCompleted)
                            .setData(ClusterCommandEvent.DATA_KEY_ERROR, mError)
                            .setData(ClusterCommandEvent.DATA_KEY_SUMMARY, mSummary)
                            .setData(
                                    ClusterCommandEvent.DATA_KEY_TOTAL_TEST_COUNT,
                                    Integer.toString(getNumTotalTests()))
                            .setData(
                                    ClusterCommandEvent.DATA_KEY_FAILED_TEST_COUNT,
                                    Integer.toString(getNumAllFailedTests()))
                            .build();
            getClusterClient().getCommandEventUploader().postEvent(event);
            getClusterClient().getCommandEventUploader().flush();
            if (mHeartbeat != null) {
                mHeartbeat.cancel(false);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void putSummary(List<TestSummary> summaries) {
            final StringBuilder sb = new StringBuilder();
            for (final TestSummary summary : summaries) {
                sb.append(summary.getSummary());
                sb.append("\n");
            }
            mSummary = sb.toString();
        }

        private ScheduledFuture<?> startHeartbeat() {
            return getHeartbeatThreadPool()
                    .scheduleAtFixedRate(
                            new Runnable() {
                                @Override
                                public void run() {
                                    final ClusterCommandEvent event =
                                            createEventBuilder()
                                                    .setType(
                                                            ClusterCommandEvent.Type
                                                                    .TestRunInProgress)
                                                    .build();
                                    getClusterClient().getCommandEventUploader().postEvent(event);
                                }
                            },
                            0,
                            5,
                            TimeUnit.MINUTES);
        }
    }

    synchronized ScheduledThreadPoolExecutor getHeartbeatThreadPool() {
        if (sHeartbeatThreadPool == null) {
            sHeartbeatThreadPool = new ScheduledThreadPoolExecutor(1, new HeartbeatThreadFactory());
            // instead of throwing some exception on shutdown we simply log it.
            sHeartbeatThreadPool.setRejectedExecutionHandler(
                    new RejectedExecutionHandler() {
                        @Override
                        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                            CLog.w(
                                    "Rejecting Task %s rejected from executor %s",
                                    r.toString(), e.toString());
                        }
                    });
        }
        return sHeartbeatThreadPool;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void processReadyCommands(IDeviceManager manager) {
        super.processReadyCommands(manager);

        if (isShuttingDown()) {
            return;
        }

        List<ClusterCommand> commands = null;
        MultiMap<String, DeviceDescriptor> devices = getAvailableDevices(manager);
        if (devices.isEmpty()) {
            CLog.d("No devices are available for testing.");
            return;
        }
        // TODO(xingdai): remove the old API call once b/34387441 finished.
        if (!getClusterOptions().shouldLeaseHostCommands()) {
            // Lease command tasks through the lease API.
            commands = fetchCommands(devices);
        } else {
            // Lease command tasks through the leasehosttasks API.
            // Here we get all devices (available or not), so TFC will analyze the device tree to
            // decide which group is allocated and which group is available.
            devices = getDevices(manager, false);
            commands = fetchHostCommands(devices);
        }
        if (commands.isEmpty()) {
            CLog.d("No commands available for testing.");
            return;
        }
        execCommands(commands);
    }

    /**
     * Checks if a given input is a valid IP:PORT string.
     * @param input a string to check
     * @return true if the given input is an IP:PORT string
     */
    static boolean isIpPort(String input){
        try {
            HostAndPort hostAndPort = HostAndPort.fromString(input);
            return InetAddresses.isInetAddress(hostAndPort.getHostText());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Returns a map containing available devices grouped by their types.
     *
     * @param manager a {@link IDeviceManager}.
     * @return a {@link MultiMap} of String to DeviceDescriptor containing available devices.
     */
    MultiMap<String, DeviceDescriptor> getAvailableDevices(IDeviceManager manager) {
        return getDevices(manager, true);
    }

    /**
     * Returns a map containing devices grouped by their types.
     *
     * @param manager a {@link IDeviceManager}.
     * @param availableOnly only return available devices or all devices.
     * @return a {@link MultiMap} of String to DeviceDescriptor containing available devices.
     */
    MultiMap<String, DeviceDescriptor> getDevices(IDeviceManager manager, boolean availableOnly) {
        // Getting available device types
        final MultiMap<String, DeviceDescriptor> devices = new MultiMap<>();
        for (final DeviceDescriptor device : manager.listAllDevices()) {
            if (availableOnly && device.getState() != DeviceAllocationState.Available) {
                continue;
            }
            if (isIpPort(device.getSerial())) {
                // Note(b/28802876): Skipping IP:PORT serials from cluster scheduling because they
                // behave differently from physical devices and are not fully supported by TF.
                continue;
            }
            String runTargetFormat = getClusterOptions().getRunTargetFormat();
            String runTarget = getRunTarget(device, runTargetFormat,
                    getClusterOptions().getDeviceTag());
            CLog.d("%s is available", runTarget);
            devices.put(runTarget, device);
        }
        return devices;
    }

    /**
     * Fetches commands for available devices from the cluster command queue.
     *
     * @param devices a {@link MultiMap} of String to DeviceDescriptor containing available devices.
     * @return a list of {@link ClusterCommand}s.
     */
    List<ClusterCommand> fetchCommands(final MultiMap<String, DeviceDescriptor> devices) {
        CLog.d("fetching cluster commands...");
        final IClusterOptions options = getClusterOptions();
        final List<ClusterCommand> commands = new LinkedList<>();
        for (String deviceType : devices.keySet()) {
            List<DeviceDescriptor> d = devices.get(deviceType);
            // We only allocate half of current free device capacity, or at least 1. This done so
            // that a host with lots of free capacity don't get saturated at once, and the load can
            // be distributed somewhat to other hosts in the same cluster. However, if there are no
            // capacity in other hosts, this scheme will still guarantee that the host is fully
            // utilized after a couple iterations of command polling
            int count = d.size() / 2;
            if (count == 0) {
                count = 1;
            }

            List<ClusterCommand> cmds = getClusterClient().leaseCommands(options.getClusterId(),
                    deviceType, count, options.getNextClusterIds());

            // Try all devices per command if possible, in case of allocation failures
            List<String> deviceSerials = new LinkedList<>();
            for (DeviceDescriptor dDescriptor : d) {
                deviceSerials.add(dDescriptor.getSerial());
            }

            for (ClusterCommand cmd : cmds) {
                cmd.setTargetDeviceSerials(deviceSerials);
            }
            commands.addAll(cmds);
        }
        CLog.d("fetched %d cluster commands.", commands.size());
        return commands;
    }

    /**
     * Fetches commands for devices from the Tradefed Cluster's leasehosttasks API.
     *
     * @param devices a {@link MultiMap} of String to DeviceDescriptor containing devices.
     * @return a list of {@link ClusterCommand}s.
     */
    List<ClusterCommand> fetchHostCommands(final MultiMap<String, DeviceDescriptor> devices) {
        CLog.d("fetching cluster host commands from leasehosttasks...");
        final IClusterOptions options = getClusterOptions();
        final MultiMap<String, String> deviceGroups = options.getDeviceGroup();
        final Map<String, String> deviceToGroup = new HashMap<>();
        for (String group : deviceGroups.keySet()) {
            for (String deviceSerial : deviceGroups.get(group)) {
                deviceToGroup.put(deviceSerial, group);
            }
        }
        List<ClusterDeviceInfo> deviceInfos = new LinkedList<>();
        for (String runTarget: devices.keySet()) {
            for (DeviceDescriptor d: devices.get(runTarget)) {
                String groupName = deviceToGroup.getOrDefault(d.getSerial(), null);
                ClusterDeviceInfo deviceInfo = new ClusterDeviceInfo.Builder()
                        .setState(d.getState())
                        .setRunTarget(runTarget)
                        .setSerialId(d.getSerial())
                        .setGroupName(groupName)
                        .build();
                deviceInfos.add(deviceInfo);
            }
        }
        try {
            List<ClusterCommand> commands = getClusterClient().leaseHostCommands(
                    options.getClusterId(), getHostName(), deviceInfos,
                    options.getNextClusterIds());
            return commands;
        } catch (JSONException e) {
            CLog.e(e);
            return Collections.<ClusterCommand> emptyList();
        }
    }

    /**
     * Executes commands fetched from the cluster command queue.
     *
     * @param commands a list of {@link ClusterCommand}s fetched from the cluster command queue.
     */
    void execCommands(final List<ClusterCommand> commands) {
        for (final ClusterCommand commandTask : commands) {
            try {
                final InvocationEventHandler handler = new InvocationEventHandler(commandTask);
                switch (commandTask.getRequestType()) {
                    case UNMANAGED:
                        execClusterCommand(commandTask, handler);
                        break;
                    case MANAGED:
                        execManagedClusterCommand(commandTask, handler);
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }
            } catch (NoDeviceException e) {
                CLog.w("no device meets requirements for cluster command [%s]; returning...",
                        commandTask.getTaskId());
                IClusterEventUploader<ClusterCommandEvent> eventUploader =
                        getClusterClient().getCommandEventUploader();
                eventUploader.postEvent(
                        ClusterCommandEvent.createEventBuilder(commandTask)
                        .setHostName(getHostName())
                        .setType(ClusterCommandEvent.Type.AllocationFailed)
                        .build());
                eventUploader.flush();
            } catch (ConfigurationException | IOException | JSONException e) {
                CLog.w("failed to execute cluster command [%s]: %s", commandTask.getTaskId(), e);
                IClusterEventUploader<ClusterCommandEvent> eventUploader =
                        getClusterClient().getCommandEventUploader();
                eventUploader.postEvent(
                        ClusterCommandEvent.createEventBuilder(commandTask)
                        .setHostName(getHostName())
                        .setType(ClusterCommandEvent.Type.ConfigurationError)
                        .setData(ClusterCommandEvent.DATA_KEY_ERROR, e.toString())
                        .build());
                eventUploader.flush();
            }
        }
    }

    private void execClusterCommand(ClusterCommand commandTask, InvocationEventHandler handler)
            throws ConfigurationException, IllegalArgumentException, NoDeviceException {
        String cmdLine = commandTask.getCommandLine();
        String[] args = QuotationAwareTokenizer.tokenizeLine(cmdLine);
        // If it is a dry run command skip execution.
        if (dryRunCommand(handler, args)) {
            return;
        }
        // Append device serials to command.
        // By assigning all applicable serials, TF will try one by one until allocation
        // succeeds (or fails for all). This mitigates the issue where a single bad
        // device can starve tests.
        if (commandTask.getTargetDeviceSerials() != null) {
            for (String serial : commandTask.getTargetDeviceSerials()) {
                cmdLine += " --serial ";
                cmdLine += serial;
            }
        }
        CLog.i("executing cluster command: [%s] %s", commandTask.getTaskId(), cmdLine);
        execCommand(handler, QuotationAwareTokenizer.tokenizeLine(cmdLine));
    }

    private void execManagedClusterCommand(
            ClusterCommand commandTask, InvocationEventHandler handler)
            throws IOException, JSONException, ConfigurationException, NoDeviceException {
        final IClusterClient client = getClusterClient();

        // FIXME: Refactor this to be a separate class.

        // FIXME: Support multi-device tests.

        // FIXME: Find a way to create and run {@link IConfiguration} object here instead of
        // creating a dependency on a XML config.
        final List<String> args = new ArrayList<>();
        args.add("google/cluster/command-launcher");
        args.addAll(ArrayUtil.list("--cluster:attempt-id", commandTask.getAttemptId()));
        args.addAll(ArrayUtil.list("--cluster:command-line", commandTask.getCommandLine()));

        final File rootDir =
                new File(System.getProperty("java.io.tmpdir"), commandTask.getAttemptId());
        rootDir.mkdirs();
        args.addAll(ArrayUtil.list("--cluster:root-dir", rootDir.getAbsolutePath()));
        final TestEnvironment env = client.getTestEnvironment(commandTask.getRequestId());
        for (final Map.Entry<String, String> entry : env.getEnvVars().entrySet()) {
            final String arg = String.format("%s=%s", entry.getKey(), entry.getValue());
            args.addAll(ArrayUtil.list("--cluster:env-var", arg));
        }
        for (final String script : env.getSetupScripts()) {
            args.addAll(ArrayUtil.list("--cluster:setup-script", script));
        }
        if (env.getOutputFileUploadUrl() != null) {
            args.addAll(
                    ArrayUtil.list(
                            "--cluster:output-file-upload-url", env.getOutputFileUploadUrl()));
        }
        for (final String pattern : env.getOutputFilePatterns()) {
            args.addAll(ArrayUtil.list("--cluster:output-file-pattern", pattern));
        }

        final List<TestResource> resources = client.getTestResources(commandTask.getRequestId());
        for (final TestResource resource : resources) {
            final String arg =
                    String.format(
                            "%s=%s",
                            resource.getName(), resource.getUrl().replaceAll("=", "\\\\="));
            CLog.i(arg);
            args.addAll(ArrayUtil.list("--cluster:test-resource", arg));
        }

        // Append device serials to command.
        // By assigning all applicable serials, TF will try one by one until allocation
        // succeeds (or fails for all). This mitigates the issue where a single bad
        // device can starve tests.
        if (commandTask.getTargetDeviceSerials() != null) {
            for (String serial : commandTask.getTargetDeviceSerials()) {
                args.addAll(ArrayUtil.list("--serial", serial));
            }
        }
        CLog.i("executing cluster command: [%s] %s", commandTask.getTaskId(), args);
        execCommand(handler, args.toArray(new String[args.size()]));
    }

    /**
     * Determines if a given command is a dry-run. If the command is a dry-run, validate it.
     * If there are any configs issue, it will throw a ConfigurationException.
     *
     * @param handler {@link InvocationEventHandler} to report events for dry-run validation.
     * @param args the command to validate.
     * @return true if the command are a dry run, false otherwise.
     * @throws ConfigurationException
     */
    protected boolean dryRunCommand(final InvocationEventHandler handler, String[] args)
            throws ConfigurationException {
        IConfiguration config = getConfigFactory().createConfigurationFromArgs(
                args, null, getKeyStoreClient());
        if (config.getCommandOptions().isDryRunMode()) {
            IInvocationContext context = new InvocationContext();
            context.addDeviceBuildInfo("stub", new BuildInfo());
            handler.invocationStarted(context);
            config.validateOptions();
            handler.invocationEnded(0);
            IInvocationContext nullMeta = null;
            handler.invocationComplete(nullMeta, null);
            return true;
        }
        return false;
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

    /**
     * Get the {@link IClusterClient} instance used to interact with the TFC backend.
     */
    IClusterClient getClusterClient() {
        if (mClusterClient == null) {
            mClusterClient = (IClusterClient)GlobalConfiguration.getInstance()
                    .getConfigurationObject(ClusterClient.TYPE_NAME);
            if (mClusterClient == null) {
                throw new IllegalStateException("cluster_client not defined. You must add this " +
                        "object to your global config. See google/atp/cluster.xml.");
            }
        }
        return mClusterClient;
    }

    @Override
    public ISandbox createSandbox() {
        return new GoogleTradefedSandbox();
    }
}
