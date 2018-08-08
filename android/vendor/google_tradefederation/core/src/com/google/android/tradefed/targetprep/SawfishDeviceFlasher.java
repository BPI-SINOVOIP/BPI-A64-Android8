// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/**
 * A {@link DefaultDeviceFlasher} for flashing Huawei Sawfish devices
 */
public class SawfishDeviceFlasher extends DefaultDeviceFlasher {
    /**
     * {@inheritDoc}
     */
    @Override
    String getMinBootloaderVersion() {
        return "SAWFISHV4.0";
    }
}
