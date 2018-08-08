// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/** A {@link DefaultDeviceFlasher} for flashing Glowlight devices */
public class GlowlightDeviceFlasher extends DefaultDeviceFlasher {
    /** {@inheritDoc} */
    @Override
    String getMinBootloaderVersion() {
        return "GLOWLIGHT.00036.17220";
    }
}
