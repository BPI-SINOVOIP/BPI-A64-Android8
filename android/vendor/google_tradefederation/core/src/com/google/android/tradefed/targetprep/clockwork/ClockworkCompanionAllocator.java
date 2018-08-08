// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep.clockwork;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.targetprep.companion.CompanionAllocator;

/**
 * A subclass of {@link CompanionAllocator} that allocates a companion device for clockwork testing
 *
 */
@OptionClass(alias = "clockwork-companion-allocator")
public class ClockworkCompanionAllocator extends CompanionAllocator {

    @Option(name = "companion-product-type",
            description = "limits the selection of companion device to the specified product type")
    private String mProductFilter = null;

    @Option(name = "companion-serial",
            description = "directly specify the serial number of companion device to use")
    private String mSerial = null;

    private DeviceSelectionOptions mDeviceSelectionOptions = new ClockworkExclusionFilter() {
        @Override
        public boolean matches(com.android.ddmlib.IDevice device) {
            boolean shouldMatch = super.matches(device);
            if (mProductFilter != null) {
                String product = getDeviceProductType(device);
                if (product != null) {
                    shouldMatch &= mProductFilter.equals(product);
                }
            }
            if (mSerial != null) {
                shouldMatch &= mSerial.equals(device.getSerialNumber());
            }
            return shouldMatch;
        }
    };

    @Override
    protected DeviceSelectionOptions getCompanionDeviceSelectionOptions() {
        return mDeviceSelectionOptions;
    }
}
