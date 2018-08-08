// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/**
 * A {@link DefaultDeviceFlasher} for flashing hikey devices
 */
public class HikeyDeviceFlasher extends DefaultDeviceFlasher {

    /**
     * {@inheritDoc}
     */
    @Override
    String getMinBootloaderVersion() {
        return "0.4";
    }
}
