// Copyright 2015 Google Inc.  All Rights Reserved.

package com.google.android.ota;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.OtaDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

/**
 * Stores information about a current or upgrade-targeted version in the device's data fs.
 */
@OptionClass(alias = "version-cache")
public class VersionCachePreparer implements ITargetPreparer {

    @Option(name = "record-slots", description = "whether or not to record slot info")
    private boolean mRecordSlots = false;

    @Option(name = "use-first-version", description = "whether to use the build's starting " +
            "version to record expected values. default is false")
    private boolean mUseFirstVersion = false;

    public static final String OTATEST_BASE_DIR = "/sdcard/otatest/";
    public static final String OTATEST_VERSION_OLD = OTATEST_BASE_DIR + "version.old";
    public static final String OTATEST_VERSION_NEW = OTATEST_BASE_DIR + "version.new";

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (!(buildInfo instanceof IDeviceBuildInfo) && !mUseFirstVersion) {
            throw new TargetSetupError(
                    "VersionCachePreparer must receive an IDeviceBuildInfo when "
                            + "--use-first-version is disabled",
                    device.getDeviceDescriptor());
        }
        IDeviceBuildInfo otaBuildInfo = mUseFirstVersion ?
                (IDeviceBuildInfo) buildInfo :
                ((OtaDeviceBuildInfo) buildInfo).getOtaBuild();
        if (otaBuildInfo == null) {
            throw new TargetSetupError(
                    "Received an OtaDeviceBuildInfo with at least one null build",
                    device.getDeviceDescriptor());
        }
        String oldVersionInfo = String.format("%s\n%s\n%s\n", device.getBuildId(),
                device.getBootloaderVersion(), device.getBasebandVersion());
        // not every update will contain bootloader or baseband info, so preserve
        // the old versions in this case
        String newBootloader = otaBuildInfo.getBootloaderVersion() != null ?
                otaBuildInfo.getBootloaderVersion() : device.getBootloaderVersion();
        String newBaseband = otaBuildInfo.getBasebandVersion() != null ?
                otaBuildInfo.getBasebandVersion() : device.getBasebandVersion();
        String newVersionInfo = String.format("%s\n%s\n%s\n", otaBuildInfo.getBuildId(),
                newBootloader, newBaseband);
        device.executeShellCommand("mkdir " + OTATEST_BASE_DIR);
        device.executeShellCommand(
                String.format("echo \"%s\" > %s", oldVersionInfo, OTATEST_VERSION_OLD));
        device.executeShellCommand(
                String.format("echo \"%s\" > %s", newVersionInfo, OTATEST_VERSION_NEW));
        if (mRecordSlots) {
            device.executeShellCommand(
                    "bootctl get-current-slot > /data/slots.old");
        }
        // ensure that /data will be readable by any InstrumentationTests that try to access it
        device.executeShellCommand("chmod 775 /data");
    }
}

