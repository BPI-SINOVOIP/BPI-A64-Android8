// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.IDeviceSelection;

import java.util.List;

/**
 * A Google specific extension of DeviceManager in order to support
 * internal specific devices (Gce in particular)
 */
@OptionClass(alias = "gdmgr", global_namespace = false)
public class GoogleDeviceManager extends DeviceManager {

    @Option(name = "max-gce-devices",
            description = "the maximum number of gce devices that can be allocated at one time")
    private int mNumGceDevicesSupported = 1;

    private static final String GCE_DEVICE_SERIAL_PREFIX = "gce-device";

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(IDeviceSelection globalDeviceFilter,
            List<IDeviceMonitor> globalDeviceMonitors) {
        init(globalDeviceFilter, globalDeviceMonitors,
                new GoogleManagedTestDeviceFactory(mFastbootEnabled,
                        GoogleDeviceManager.this, mDvcMon));
        addGceDevices();
    }

    /**
     * Add placeholder objects for the max number of tcp devices that can be connected
     */
    private void addGceDevices() {
        for (int i = 0; i < mNumGceDevicesSupported; i++) {
            addAvailableDevice(new RemoteAvdIDevice(String.format("%s:%d",
                    GCE_DEVICE_SERIAL_PREFIX, i)));
        }
    }
}
