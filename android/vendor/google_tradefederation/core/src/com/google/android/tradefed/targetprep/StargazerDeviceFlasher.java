// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/** A {@link DefaultDeviceFlasher} for flashing Stargazer devices */
public class StargazerDeviceFlasher extends DefaultDeviceFlasher {

    /** {@inheritDoc} */
    @Override
    String getMinBootloaderVersion() {
        return "STARGAZER.00033.17102";
    }
}
