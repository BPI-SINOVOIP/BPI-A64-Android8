// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

/**
 * A {@link DefaultDeviceFlasher} for flashing sailfish/marlin devices
 */
public class MarFishDeviceFlasher extends DefaultDeviceFlasher {

    /**
     * {@inheritDoc}
     */
    @Override
    String getMinBootloaderVersion() {
        return "8996-P11002-1605042355";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int compareBootloaderVersion(String x, String y) {
        String[] xs = x.split("-");
        String[] ys = y.split("-");
        if (xs.length != 3 || ys.length != 3) {
            throw new IllegalArgumentException("Invalid bootloader string format! "
                    + "Expected: <CHIPSET>-<QCT-VERSION>-<BOOTLOADER-VERSION>");
        }
        return xs[2].compareTo(ys[2]);
    }
}
