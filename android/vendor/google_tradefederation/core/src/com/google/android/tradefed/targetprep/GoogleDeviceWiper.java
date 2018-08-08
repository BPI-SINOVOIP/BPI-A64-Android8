// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.DeviceWiper;
import com.android.tradefed.targetprep.TargetSetupError;
import com.google.android.tradefed.device.StaticDeviceInfo;

/**
 * A {@link DeviceWiper} that dynamically sets the 'use-erase' prop based on parent
 */
@OptionClass(alias = "google-device-wiper")
public class GoogleDeviceWiper extends DeviceWiper {

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException {
        if (mDisable) {
            return;
        }
        String productType = device.getProductType();
        // check for the two legacy devices that only support erase
        if (StaticDeviceInfo.CRESPO_PRODUCT_TYPE.equals(productType) ||
                isStingray(productType)) {
            mUseErase = true;
        }
        super.setUp(device, buildInfo);
    }

    private boolean isStingray(String productType) {
        for (String stingrayProd : StingrayDeviceFlasher.STINGRAY_PRODUCT_TYPES) {
            if (stingrayProd.equals(productType)) {
                return true;
            }
        }
        return false;
    }
}
