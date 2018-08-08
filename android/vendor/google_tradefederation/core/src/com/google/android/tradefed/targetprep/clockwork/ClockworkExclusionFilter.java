// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep.clockwork;

import com.android.ddmlib.IDevice;
import com.android.tradefed.device.DeviceSelectionOptions;

/**
 * {@link DeviceSelectionOptions} based filter to exclude clockwork devices from being selected as
 * companion
 */
public class ClockworkExclusionFilter extends DeviceSelectionOptions {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(IDevice device) {
        return super.matches(device) && !isClockwork(device);
    }

    private boolean isClockwork(IDevice device) {
        String productType = getDeviceProductType(device);
        if (productType == null) {
            // if we don't know what the product type is, don't match it
            return true;
        }

        String deviceBuildPropertyString =
            device.getProperty("ro.build.characteristics");
        if (deviceBuildPropertyString == null) {
            // if we don't know the characteristics, assume it is not watch
            return false;
        }
        return deviceBuildPropertyString.contains("watch");
    }
}
