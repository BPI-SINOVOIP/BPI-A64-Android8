// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.google.common.annotations.VisibleForTesting;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.RunUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link IDeviceMonitor} implementation that reports results to the Tradefed Cluster service.
 */
@OptionClass(alias = "cluster-device-monitor")
public class ClusterDeviceMonitor implements IDeviceMonitor {

    @Option(name = "host-info-cmd", description = "A label and command to run periodically, to " +
            "collect and send host info to the backend. May be repeated. Commands containing " +
            "spaces should be double-quoted.")
    private Map<String, String> mHostInfoCmds = new HashMap<String, String>();
    private Map<String, String[]> mTokenizedHostInfoCmds = null;

    @Option(name = "host-info-cmd-timeout", description = "How long to wait for each " +
            "host-info-cmd to complete, in millis. If the command times out, a (null) value " +
            "will be passed to the backend for that particular command.")
    private long mHostInfoCmdTimeout = 5 * 1000;

    /** The {@link IClusterOptions} instance used to store cluster-related settings. */
    private IClusterOptions mClusterOptions;

    /** The {@link IClusterClient} instance used to interact with the TFC backend. */
    private IClusterClient mClusterClient;

    /**
     * Worker thread to dispatch a cluster host event that includes a snapshot of the devices
     */
    class EventDispatcher extends Thread {

        private boolean mIsCanceled = false;
        private IClusterEventUploader<ClusterHostEvent> mEventUploader = null;

        public EventDispatcher() {
            super("ClusterDeviceMonitor.EventDispatcher");
            this.setDaemon(true);
        }

        @Override
        public void run() {
            try {
                while (!mIsCanceled) {
                    dispatch();
                    getRunUtil().sleep(getClusterOptions().getDeviceMonitorSnapshotInterval());
                }
            } catch (Exception e) {
                CLog.e(e);
            }
        }

        IClusterEventUploader<ClusterHostEvent> getEventUploader() {
            if (mEventUploader == null) {
                mEventUploader = getClusterClient().getHostEventUploader();
            }
            return mEventUploader;
        }

        void dispatch() {
            CLog.d("Start device snapshot.");
            final IClusterEventUploader<ClusterHostEvent> eventUploader = getEventUploader();
            final List<DeviceDescriptor> devices = listDevices();
            final ClusterHostEvent.Builder builder = new ClusterHostEvent.Builder()
                .setHostEventType(ClusterHostEvent.HostEventType.DeviceSnapshot)
                .setHostName(ClusterCommandScheduler.getHostName())
                .setTfVersion(ClusterCommandScheduler.getTfVersion())
                .setData(getAdditionalHostInfo())
                .setClusterId(getClusterOptions().getClusterId());
            for (DeviceDescriptor device : devices) {
                final String serial = device.getSerial();
                final ClusterDeviceInfo.Builder deviceBuilder = new ClusterDeviceInfo.Builder();
                deviceBuilder.setSerialId(serial);
                deviceBuilder.setBuildId(device.getBuildId());
                deviceBuilder.setProduct(device.getProduct());
                deviceBuilder.setProductVariant(device.getProductVariant());
                deviceBuilder.setSdkVersion(device.getSdkVersion());
                deviceBuilder.setState(device.getState());
                deviceBuilder.setBatteryLevel(device.getBatteryLevel());
                deviceBuilder.setMacAddress(device.getMacAddress());
                deviceBuilder.setSimState(device.getSimState());
                deviceBuilder.setSimOperator(device.getSimOperator());

                String runTargetFormat = getClusterOptions().getRunTargetFormat();
                deviceBuilder.setRunTarget(ClusterCommandScheduler.getRunTarget(
                        device, runTargetFormat, getClusterOptions().getDeviceTag()));

                builder.addDeviceInfo(deviceBuilder.build());
            }
            // We want to force an upload.
            CLog.d("Dispatched devicesnapshot.");
            eventUploader.postEvent(builder.build());
            eventUploader.flush();
        }

        void cancel() {
            mIsCanceled = true;
        }

        boolean isCanceled() {
            return mIsCanceled;
        }

    }

    private EventDispatcher mDispatcher;
    private DeviceLister mDeviceLister;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        if (getClusterOptions().isDeviceMonitorDisabled()) {
            return;
        }
        mDispatcher = getEventDispatcher();
        mDispatcher.start();
    }

    /** {@inheritDoc} */
    @Override
    public void stop() {
        if (mDispatcher != null && mDispatcher.isAlive()) {
            mDispatcher.cancel();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setDeviceLister(DeviceLister lister) {
        if (lister == null) {
            throw new NullPointerException();
        }
        mDeviceLister = lister;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyDeviceStateChange(String serial, DeviceAllocationState oldState,
            DeviceAllocationState newState) {
        // Nothing happens. We only take snapshots. Maybe we can add state change in the future.
    }

    @VisibleForTesting
    EventDispatcher getEventDispatcher() {
        if (mDispatcher == null) {
            mDispatcher = new EventDispatcher();
        }
        return mDispatcher;
    }

    @VisibleForTesting
    List<DeviceDescriptor> listDevices() {
        return mDeviceLister.listDevices();
    }

    @VisibleForTesting
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * A helper method to tokenize the host info commands.
     */
    void tokenizeCommands() {
        if (mTokenizedHostInfoCmds != null && !mTokenizedHostInfoCmds.isEmpty()) {
            // Commands already tokenized and cached. No need to tokenize again.
            return;
        }

        mTokenizedHostInfoCmds = new HashMap<String, String[]>(mHostInfoCmds.size());
        // Tokenize the commands and cache the result
        for (Map.Entry<String, String> entry : mHostInfoCmds.entrySet()) {
            final String key = entry.getKey();
            final String cmd = entry.getValue();

            CLog.d("Tokenized key %s command: %s", key, cmd);
            mTokenizedHostInfoCmds.put(key, QuotationAwareTokenizer.tokenizeLine(cmd));
        }
    }

    /**
     * Queries additional host info from host-info-cmd options in TF configs.
     */
    Map<String, String> getAdditionalHostInfo() {
        final Map<String, String> info = new HashMap<>();
        this.tokenizeCommands();

        for (Map.Entry<String, String[]> entry : mTokenizedHostInfoCmds.entrySet()) {
            final String key = entry.getKey();
            final String[] cmd = entry.getValue();
            final String cmdString = mHostInfoCmds.get(key);

            final CommandResult result = getRunUtil().runTimedCmdSilently(mHostInfoCmdTimeout, cmd);

            CLog.d("Command %s result: %s", cmdString, result.getStatus().toString());

            if (result.getStatus() == CommandStatus.SUCCESS) {
                info.put(key, result.getStdout());
            } else {
                info.put(key, result.getStderr());
            }
        }

        return info;
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
}
