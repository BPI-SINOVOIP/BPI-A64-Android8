// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/** A {@link DefaultDeviceFlasher} for flashing Fossil Shiner devices */
public class ShinerDeviceFlasher extends DefaultDeviceFlasher {
    /** {@inheritDoc} */
    @Override
    String getMinBootloaderVersion() {
        return "SHINER.00034.17180";
    }
}
