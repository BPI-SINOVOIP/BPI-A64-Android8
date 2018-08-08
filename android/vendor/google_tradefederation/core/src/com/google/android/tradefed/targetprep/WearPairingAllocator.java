// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.targetprep.companion.CompanionAllocator;

/**
 * A subclass of {@link CompanionAllocator} that allocates a companion device for clockwork testing
 *
 */
@OptionClass(alias = "wear-pairing-allocator")
public class WearPairingAllocator extends CompanionAllocator {

    @Option(name = "wear-product-type",
            description = "limits the selection of companion device to the specified product type")
    private String mProductFilter = null;

    @Option(name = "wear-serial",
            description = "directly specify the serial number of companion device to use")
    private String mSerial = null;

    private DeviceSelectionOptions mDeviceSelectionOptions = new DeviceSelectionOptions() {
        @Override
        public boolean matches(com.android.ddmlib.IDevice device) {
            boolean isMatch = super.matches(device);
            if (mProductFilter != null) {
                String product = getDeviceProductType(device);
                if (product != null) {
                    isMatch &= mProductFilter.equals(product);
                }
            }
            if (mSerial != null) {
                isMatch &= mSerial.equals(device.getSerialNumber());
            }
            return isMatch;
        }
    };

    @Override
    protected DeviceSelectionOptions getCompanionDeviceSelectionOptions() {
        return mDeviceSelectionOptions;
    }
}
