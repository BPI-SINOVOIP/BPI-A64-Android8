/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.DeviceWiper;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.companion.CompanionAwarePreparer;

/**
 * A {@link DeviceWiper} that wipes userdata on companion device
 */
@OptionClass(alias = "paired-device-wiper")
public class WearPairingDeviceWiper extends CompanionAwarePreparer {

    @Option(name = "disable", description = "disables the device wiper")
    protected boolean mDisable = false;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        if (mDisable) {
            return;
        }

        DeviceWiper deviceWiper = new DeviceWiper();
        deviceWiper.setUp(getCompanion(device), buildInfo);
    }

}
