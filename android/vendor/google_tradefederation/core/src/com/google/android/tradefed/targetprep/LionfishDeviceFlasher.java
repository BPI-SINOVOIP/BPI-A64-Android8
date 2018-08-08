// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/** A {@link DefaultDeviceFlasher} for flashing Lionfish devices */
public class LionfishDeviceFlasher extends DefaultDeviceFlasher {

    /** {@inheritDoc} */
    @Override
    String getMinBootloaderVersion() {
        return "LIONFISH.00032.17070";
    }
}
