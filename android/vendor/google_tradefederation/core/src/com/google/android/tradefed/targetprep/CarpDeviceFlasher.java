// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.google.android.tradefed.device.StaticDeviceInfo;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.IFlashingResourcesParser;
import com.android.tradefed.targetprep.TargetSetupError;

/**
 * A {@link DefaultDeviceFlasher} for flashing Carp & Bowfin devices
 * Carp & Bowfin uses the product type "smelt" in android-info.txt.
 */
public class CarpDeviceFlasher extends DefaultDeviceFlasher {

    /**
     * {@inheritDoc}
     */
    @Override
    String getMinBootloaderVersion() {
        return "SMELT_50.17(*)";
    }

    @Override
    protected void verifyRequiredBoards(ITestDevice device, IFlashingResourcesParser resourceParser,
            String deviceProductType) throws TargetSetupError {
        // Handle shared product type with "smelt"
        if (deviceProductType.contains("carp")
                || deviceProductType.contains(StaticDeviceInfo.BOWFIN_PRODUCT)) {
            deviceProductType = "smelt";
        }
        if (!resourceParser.getRequiredBoards().contains(deviceProductType)) {
            throw new TargetSetupError(String.format("Device %s is %s. Expected %s",
                    device.getSerialNumber(), deviceProductType,
                    resourceParser.getRequiredBoards()), device.getDeviceDescriptor());
        }
    }
}
