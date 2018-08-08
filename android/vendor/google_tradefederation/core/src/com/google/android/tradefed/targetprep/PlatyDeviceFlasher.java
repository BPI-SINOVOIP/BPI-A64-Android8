// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/**
 * A {@link DefaultDeviceFlasher} for flashing ZTE Platy devices
 */
public class PlatyDeviceFlasher extends DefaultDeviceFlasher {
    /**
     * {@inheritDoc}
     */
    @Override
    String getMinBootloaderVersion() {
        return "MSM8909-PLATY-BOOTLOADER-V10";
    }
}
