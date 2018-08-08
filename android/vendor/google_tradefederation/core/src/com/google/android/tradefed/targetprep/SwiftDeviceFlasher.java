// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/**
 * A {@link DefaultDeviceFlasher} for flashing Swift devices
 */
public class SwiftDeviceFlasher extends DefaultDeviceFlasher {

    /**
     * {@inheritDoc}
     */
    @Override
    String getMinBootloaderVersion() {
        return "swift-7.2.331.7";
    }

}
