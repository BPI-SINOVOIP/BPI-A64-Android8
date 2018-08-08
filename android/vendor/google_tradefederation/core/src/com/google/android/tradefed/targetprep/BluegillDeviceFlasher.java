// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/** A {@link DefaultDeviceFlasher} for flashing Fossil Bluegill devices */
public class BluegillDeviceFlasher extends DefaultDeviceFlasher {
    /** {@inheritDoc} */
    @Override
    String getMinBootloaderVersion() {
        return "BLUEGILL.00036.17211";
    }
}
