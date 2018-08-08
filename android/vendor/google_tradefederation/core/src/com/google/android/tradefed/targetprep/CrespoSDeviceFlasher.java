// Copyright 2012 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.CdmaDeviceFlasher;

/**
 * A {@link CdmaDeviceFlasher} for flashing Crespo-S devices.
 */
public class CrespoSDeviceFlasher extends CdmaDeviceFlasher {
    /**
     * {@inheritDoc}
     */
    @Override
    public void overrideDeviceOptions(ITestDevice device) {
        if (!device.getUseFastbootErase()) {
            CLog.w("Overriding use-fastboot-erase to true. Fastboot format is not supported on " +
                    "Crespo-S");
            device.setUseFastbootErase(true);
        }
    }
}
