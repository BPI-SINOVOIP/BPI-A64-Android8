// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.tradefed.device.TcpDevice;

/**
 * A placeholder {@link IDevice} used by {@link GoogleDeviceManager} to allocate when
 * {@link GoogleDeviceSelectionOptions#gceDeviceRequested()} is <code>true</code>
 */
public class RemoteAvdIDevice extends TcpDevice {

    /**
     * @param serial placeholder for the real serial
     */
    public RemoteAvdIDevice(String serial) {
        super(serial);
    }
}
