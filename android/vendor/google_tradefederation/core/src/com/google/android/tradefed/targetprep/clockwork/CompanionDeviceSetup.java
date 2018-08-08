// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep.clockwork;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.companion.CompanionDeviceTracker;
import com.google.android.tradefed.targetprep.GoogleDeviceSetup;

/**
 * An extension of {@link GoogleDeviceSetup} that allows setup of companion device.
 */
@OptionClass(alias = "companion-device-setup")
public class CompanionDeviceSetup extends GoogleDeviceSetup {

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mDisable) {
            return;
        }

        ITestDevice companionDevice = CompanionDeviceTracker.getInstance()
                .getCompanionDevice(device);
        if (companionDevice == null) {
            throw new RuntimeException(
                    "no companion device allocated, use appropriate ITargetPreparer");
        }

        super.setUp(companionDevice, buildInfo);
    }
}
