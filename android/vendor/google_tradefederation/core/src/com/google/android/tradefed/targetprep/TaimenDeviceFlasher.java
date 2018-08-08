// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/** A {@link DefaultDeviceFlasher} for flashing Taimen devices */
public class TaimenDeviceFlasher extends DefaultDeviceFlasher {
    /** {@inheritDoc} */
    @Override
    String getMinBootloaderVersion() {
        return "TMZ09b";
    }
}
