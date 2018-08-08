// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/**
 * A {@link DefaultDeviceFlasher} for flashing Nemo devices
 */
public class NemoDeviceFlasher extends DefaultDeviceFlasher {

    /**
     * {@inheritDoc}
     */
    @Override
    String getMinBootloaderVersion() {
        return "NEMOZ10b";
    }

}
