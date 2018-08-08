// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/**
 * A {@link DefaultDeviceFlasher} for flashing Glacier devices
 */
public class GlacierDeviceFlasher extends DefaultDeviceFlasher {

    /**
     * {@inheritDoc}
     */
    @Override
    String getMinBootloaderVersion() {
        return "2.20";
    }

}
