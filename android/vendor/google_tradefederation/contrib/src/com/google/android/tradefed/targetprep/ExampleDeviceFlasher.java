// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/** A {@link DefaultDeviceFlasher} for nothing, here as an example. */
// TODO(kevcheng): Delete when real device flashers live here.
public class ExampleDeviceFlasher extends DefaultDeviceFlasher {

    /** {@inheritDoc} */
    @Override
    String getMinBootloaderVersion() {
        return "ExampleVersion";
    }
}
