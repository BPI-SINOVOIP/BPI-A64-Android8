// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/**
 * A {@link DefaultDeviceFlasher} for flashing Smelt devices
 */
public class SmeltDeviceFlasher extends DefaultDeviceFlasher {

    /**
     * {@inheritDoc}
     */
    @Override
    String getMinBootloaderVersion() {
        return "SMELT_50.17(*)";
    }
}
