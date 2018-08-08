// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/**
 * A {@link DefaultDeviceFlasher} for flashing Intel Shasta devices
 */
public class ShastaDeviceFlasher extends DefaultDeviceFlasher {
    /**
     * {@inheritDoc}
     */
    @Override
    String getMinBootloaderVersion() {
        return "SHASTA-03.16";
    }
}
