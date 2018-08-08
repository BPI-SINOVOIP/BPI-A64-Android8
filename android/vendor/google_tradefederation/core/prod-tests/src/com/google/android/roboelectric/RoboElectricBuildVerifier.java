// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.roboelectric;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

/**
 * A {@link ITargetPreparer} to validate a build for roboelectric tests.
 * <p/>
 * This class checks whether a build contains essential files to run roboelectric tests.
 */
public class RoboElectricBuildVerifier implements ITargetPreparer {

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        RoboElectricBuildInfo roboBuildInfo = (RoboElectricBuildInfo)buildInfo;
        if (roboBuildInfo.getTestTargetFile() == null) {
            throw new BuildError("No test target file.", device.getDeviceDescriptor());
        }
    }

}
