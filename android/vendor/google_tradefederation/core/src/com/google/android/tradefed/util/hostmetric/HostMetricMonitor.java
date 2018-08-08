// Copyright 2014 Google Inc.  All Rights Reserved.
package com.google.android.tradefed.util.hostmetric;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.VersionParser;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A device monitor to dispatch host metrics to TF Monitor.
 */
@OptionClass(alias = "host-metric-monitor", global_namespace = false)
public class HostMetricMonitor implements IDeviceMonitor {

    @Option(name = "dispatch-interval", description = "the time interval between dispatches in ms")
    private long mDispatchInterval = 60 * 1000;

    @Option(name = "disk-path", description = "paths to disks to monitor. NFS paths are also " +
            "supported.")
    private List<String> mDiskPaths = new LinkedList<>();

    /**
     * A class for dispatcher thread. This thread emits device metrics periodically.
     */
    class MetricDispatcher extends Thread {

        private Map<String, String> mHostData = new HashMap<>();
        private boolean mIsCanceled = false;

        /**
         * Constructor.
         */
        MetricDispatcher() {
            super("HostMetricMonitor.MetricDispatcher");
            this.setDaemon(true);
        }

        @Override
        public void run() {
            try {
                mHostData.put("hdm_label", getHelicopterLabel());
                mHostData.put("tradefed_version", VersionParser.fetchVersion());

                while (!mIsCanceled) {
                    dispatch();
                    getRunUtil().sleep(mDispatchInterval);
                }
            } catch (Exception e) {
                CLog.e(e);
            }
        }

        /**
         * Emits the current device metric values (count, battery level).
         */
        void dispatch() {
            final IHostMetricAgent metricAgent = getMetricAgent();
            final List<DeviceDescriptor> devices = listDevices();
            for (DeviceDescriptor device : devices) {
                final String serial = device.getSerial();
                if (serial.startsWith("null-device-")) {
                    continue;
                }

                int batteryLevel = -1;
                try {
                    batteryLevel = Integer.parseInt(device.getBatteryLevel());
                } catch (NumberFormatException e) {
                    // use the default value
                }
                final Map<String, String> data = new HashMap<>(mHostData);
                data.put("device_serial", serial);
                data.put("device_product", String.format("%s:%s", device.getProduct(),
                        device.getProductVariant()));
                metricAgent.emitValue(IHostMetricAgent.DEVICE_COUNT_METRIC, 1, data);
                metricAgent.emitValue(IHostMetricAgent.DEVICE_ALLOCATED_COUNT_METRIC,
                        (device.getState() == DeviceAllocationState.Allocated) ? 1 : 0, data);
                metricAgent.emitValue(IHostMetricAgent.DEVICE_BATTERY_METRIC, batteryLevel, data);
            }

            for (String path : mDiskPaths) {
              final File disk = new File(path);
              if (!disk.exists()) {
                CLog.w("Cannot collect metrics for a non-existent path: %s", path);
              }
              final Map<String, String> data = new HashMap<>(mHostData);
              data.put("path", path);
              metricAgent.emitValue(
                  IHostMetricAgent.DISK_TOTAL_SPACE_METRIC, disk.getTotalSpace(), data);
              metricAgent.emitValue(
                  IHostMetricAgent.DISK_FREE_SPACE_METRIC, disk.getFreeSpace(), data);
            }
            metricAgent.flush();
        }

        void cancel() {
            mIsCanceled = true;
        }

        boolean isCanceled() {
            return mIsCanceled;
        }

    }

    private MetricDispatcher mDispatcher;
    private DeviceLister mDeviceLister;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        mDispatcher = getMetricDispatcher();
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
    }

    @VisibleForTesting
    IHostMetricAgent getMetricAgent() {
        IHostMetricAgent agent = (IHostMetricAgent) GlobalConfiguration.getInstance()
                .getConfigurationObject("host_metric_agent");
        return agent;
    }

    @VisibleForTesting
    MetricDispatcher getMetricDispatcher() {
        if (mDispatcher == null) {
            mDispatcher = new MetricDispatcher();
        }
        return mDispatcher;
    }

    @VisibleForTesting
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    @VisibleForTesting
    List<DeviceDescriptor> listDevices() {
        return mDeviceLister.listDevices();
    }

    /**
     * Returns Helicopter labels if exist.
     *
     * @return a colon-separate strings of Helicopter labels. <code>null</code> if no label is set.
     */
    private String getHelicopterLabel() {
        List<String> values = GlobalConfiguration.getInstance().getOptionValues("hdm:label");
        if (values == null) {
            return null;
        }

        return ArrayUtil.join(":", values);
    }

}
