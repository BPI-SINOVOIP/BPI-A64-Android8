// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.tradefed.device.DeviceStateMonitor;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.ManagedTestDeviceFactory;
import com.android.tradefed.device.TestDeviceState;

/**
 * Factory to create the different kind of devices that can be monitored by Tf.
 * Extends the generic {@link ManagedTestDeviceFactory} to create Google specific devices.
 */
public class GoogleManagedTestDeviceFactory extends ManagedTestDeviceFactory {

    /**
     * Constructor for google specific factory.
     * Call directly the {@link ManagedTestDeviceFactory} constructor.
     *
     * @param fastbootEnabled
     * @param deviceManager
     * @param allocationMonitor
     */
    public GoogleManagedTestDeviceFactory(boolean fastbootEnabled, IDeviceManager deviceManager,
            IDeviceMonitor allocationMonitor) {
        super(fastbootEnabled, deviceManager, allocationMonitor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IManagedTestDevice createDevice(IDevice idevice) {
        IManagedTestDevice testDevice = null;
        if (idevice instanceof RemoteAvdIDevice) {
            testDevice = new RemoteAndroidVirtualDevice(idevice,
                    new DeviceStateMonitor(mDeviceManager, idevice, mFastbootEnabled),
                    mAllocationMonitor);
            testDevice.setFastbootEnabled(mFastbootEnabled);
            testDevice.setFastbootPath(mDeviceManager.getFastbootPath());
            testDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        } else {
            testDevice = super.createDevice(idevice);
        }
        return testDevice;
    }
}
