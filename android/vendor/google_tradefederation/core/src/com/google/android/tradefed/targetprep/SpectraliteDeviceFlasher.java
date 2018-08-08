// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/** A {@link DefaultDeviceFlasher} for flashing Spectralite devices */
public class SpectraliteDeviceFlasher extends DefaultDeviceFlasher {
    /** {@inheritDoc} */
    @Override
    String getMinBootloaderVersion() {
        return "SPECTRALITE-03.54";

    }
}
