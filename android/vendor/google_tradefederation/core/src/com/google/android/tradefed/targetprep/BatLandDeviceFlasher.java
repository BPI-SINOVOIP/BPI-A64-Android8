// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/** A {@link DefaultDeviceFlasher} for flashing Gordon Peak devices */
public class BatLandDeviceFlasher extends DefaultDeviceFlasher {

    /** {@inheritDoc} */
    @Override
    String getMinBootloaderVersion() {
        return "V1.0";
    }
}
