// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.athome.tests;

import com.android.ddmlib.IDevice;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.google.android.tradefed.device.StaticDeviceInfo;

/**
 * A {@link DeviceSelectionOptions} specialization that rejects tungstens.
 */
public class TungstenFilter extends DeviceSelectionOptions {

    @Override
    public boolean matches(IDevice device) {
        return super.matches(device) && !isTungsten(device);
    }

    private boolean isTungsten(IDevice device) {
        String productType = getDeviceProductType(device);
        if (productType == null) {
            // if we don't know what the product type is, don't match it
            return true;
        }
        return productType.equals(StaticDeviceInfo.TUNGSTEN_PRODUCT);
    }
}
