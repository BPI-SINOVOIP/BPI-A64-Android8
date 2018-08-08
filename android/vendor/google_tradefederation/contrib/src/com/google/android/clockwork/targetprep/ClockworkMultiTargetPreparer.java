package com.google.android.clockwork.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.targetprep.multi.IMultiTargetPreparer;

import java.util.Map;

public abstract class ClockworkMultiTargetPreparer implements IMultiTargetPreparer {
    private static final String WATCH_CONFIG_NAME = "watch";
    private static final String COMPANION_CONFIG_NAME = "companion";

    ITestDevice mWatchDevice;
    ITestDevice mCompanionDevice;
    IBuildInfo mWatchBuildInfo;
    IBuildInfo mCompanionBuildInfo;
    IInvocationContext mContext;

    @Override
    public void setUp(IInvocationContext context) throws DeviceNotAvailableException {
        mContext = context;
        Map<ITestDevice, IBuildInfo> deviceInfos = mContext.getDeviceBuildMap();

        if (deviceInfos.size() < 2) {
            throw new RuntimeException(
                    "there should be at least two devices for clockwork " + "multiple device test");
        }
        for (Map.Entry<ITestDevice, IBuildInfo> entry : deviceInfos.entrySet()) {
            try {
                String deviceBuildPropertyString =
                        entry.getKey().getProperty("ro.build.characteristics");
                if (deviceBuildPropertyString.contains(WATCH_CONFIG_NAME)) {
                    mWatchDevice = entry.getKey();
                    mWatchBuildInfo = entry.getValue();
                } else {
                    if (mCompanionDevice != null) {
                        throw new RuntimeException(
                                "there should be only one " + "companion in the test");
                    }
                    mCompanionDevice = entry.getKey();
                    mCompanionBuildInfo = entry.getValue();
                }
            } catch (DeviceNotAvailableException e) {
                throw new RuntimeException(
                        "device not available, "
                                + "cannot get device build property to determine companion/watch device");
            }
        }
        if (mCompanionDevice == null) {
            throw new RuntimeException("no companion device found in the test");
        }
    }

    @Override
    public void tearDown(IInvocationContext context, Throwable e)
            throws DeviceNotAvailableException {
        // TODO: Clean up unless specified.
    }
}
