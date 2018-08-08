// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/**
 * A {@link DefaultDeviceFlasher} for flashing Huawei Sawshark devices
 */
public class SawsharkDeviceFlasher extends DefaultDeviceFlasher {
    /**
     * {@inheritDoc}
     */
    @Override
    String getMinBootloaderVersion() {
        return "SAWSHARKV3.9";
    }
}
