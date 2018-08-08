// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceSelectionOptions;

/**
 * Container for for device selection criteria particular to Google specific devices (like Gce).
 */
public class GoogleDeviceSelectionOptions extends DeviceSelectionOptions {
    @Option(name = "gce-device", description =
            "start a placeholder for a gce device that will be connected later.")
    private boolean mGceDeviceRequested = false;

    public boolean gceDeviceRequested() {
        return mGceDeviceRequested;
    }

    @Override
    public boolean matches(IDevice device) {
        boolean result = super.matches(device);
        if (tcpDeviceRequested() && (device instanceof RemoteAvdIDevice)) {
            // We do not allow gce-device to pass as tcp-device
            return false;
        }
        if (gceDeviceRequested()) {
            if (result && device instanceof RemoteAvdIDevice) {
                return true;
            }
        } else if (device instanceof RemoteAvdIDevice) {
            return false;
        }
        if (result && gceDeviceRequested()) {
            // We requested Gce device but the matcher matched before the gce test
            return false;
        }
        return result;
    }

    @Override
    protected boolean extraMatching(IDevice device) {
        // We skip the tcpDevice matching since we want now to allow extension of TcpDevice.
        return true;
    }
}
