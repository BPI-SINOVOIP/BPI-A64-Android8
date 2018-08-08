// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.FastbootDeviceFlasher;
import com.android.tradefed.targetprep.FlashingResourcesParser;
import com.android.tradefed.targetprep.FlashingResourcesParser.Constraint;
import com.android.tradefed.targetprep.IFlashingResourcesParser;
import com.android.tradefed.targetprep.TargetSetupError;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link FastbootDeviceFlasher} for flashing Crespo devices
 */
public class CrespoDeviceFlasher extends FastbootDeviceFlasher {
    @SuppressWarnings("unused")
    private static final String LOG_TAG = "CrespoDeviceFlasher";

    protected static final String[] SUPPORTED_CRESPO_TYPES = new String[] {"XX"};

    /**
     * A regex to parse a Crespo BL/radio version string
     * Segments are: (model) (type) (year) (month) (build)
     */
    private static final Pattern VERSION_PAT =
            Pattern.compile("(?x) (I9020A?) (.{2}) (.) ([A-L]) ([0-9A-F])");

    // the minimum supported bootloader. Devices with bootloaders < this version will not be
    // supported
    private static final String MIN_BOOTLOADER_VER = "I9020XXJJ2";

    /** Returns a parsed version of the GSM Crespo bootloader or radio version passed */
    public static Map<String, String> parseVersion(String version) {
        Map<String, String> map = new HashMap<String, String>(5);
        Matcher match = VERSION_PAT.matcher(version);
        if (match.matches()) {
            map.put("model", match.group(1));
            map.put("type", match.group(2));
            map.put("year", match.group(3));
            map.put("month", match.group(4));
            map.put("build", match.group(5));
            return map;
        }
        return null;
    }

    static class SupportedBasebandConstraint implements Constraint {
        private final String[] mSupportedTypes;

        public SupportedBasebandConstraint(String... supportedTypes) {
            mSupportedTypes = supportedTypes;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean shouldAccept(String version) {
            Map<String, String> parsed = parseVersion(version);
            if (parsed == null) {
                CLog.w("Failed to parse baseband version %s; blindly rejecting", version);
                return false;
            }
            String type = parsed.get("type");
            for (String checkType : mSupportedTypes) {
                if (checkType.equals(type)) {
                    return true;
                }
            }

            CLog.d("Rejecting baseband version %s; not supported", version);
            return false;
        }
    }

    /**
     * Create and return a {@link IFlashingResourcesParser} that only contains baseband entries
     * that are compatible with this particular device.
     * <p />
     * {@inheritDoc}
     */
    @Override
    protected IFlashingResourcesParser createFlashingResourcesParser(IDeviceBuildInfo localBuild,
            DeviceDescriptor descriptor) throws TargetSetupError {
        Map<String, Constraint> constraintMap = new HashMap<String, Constraint>(1);
        constraintMap.put(FlashingResourcesParser.BASEBAND_VERSION_KEY,
                new SupportedBasebandConstraint(SUPPORTED_CRESPO_TYPES));
        try {
            return new FlashingResourcesParser(localBuild.getDeviceImageFile(), constraintMap);
        } catch (TargetSetupError e) {
            // Rethrow the exception with serial since FlashingResourceParser doesn't contains it.
            throw new TargetSetupError(e.getMessage(), e, descriptor);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getBootPartitionName() {
        return "bootloader";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUserDataFlashOption(UserDataFlashOption flashOption) {
        super.setUserDataFlashOption(flashOption);
        if (getUserDataFlashOption().equals(UserDataFlashOption.FLASH)) {
            CLog.w("Overriding userdata-flash to %s: current setting of %s is not supported",
                    UserDataFlashOption.TESTS_ZIP, UserDataFlashOption.FLASH);
            super.setUserDataFlashOption(UserDataFlashOption.TESTS_ZIP);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean checkAndFlashBootloader(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String currentBootVersion = getImageVersion(device, "bootloader");
        String reqBootVersion = deviceBuild.getBootloaderVersion();

        if (reqBootVersion.length() != currentBootVersion.length()) {
            // A lexicographical compare isn't guaranteed to do the right thing; bail
            throw new TargetSetupError(String.format("Can't compare Crespo bootloader versions " +
                    "with different string lengths. Device %s has bootloader %s (%d chars), but " +
                    "build %s/%s requires bootloader version %s (%d chars).",
                    device.getSerialNumber(), currentBootVersion, currentBootVersion.length(),
                    deviceBuild.getDeviceBuildId(), deviceBuild.getBuildTargetName(),
                    reqBootVersion, reqBootVersion.length()), device.getDeviceDescriptor());
        }
        if (currentBootVersion.compareTo(MIN_BOOTLOADER_VER) < 0) {
            // take device out of service, must be flashed by hand
            throw new DeviceNotAvailableException(String.format("Bootloader not supported on " +
                    "Crespo.  Device %s has bootloader (%s), but minimum supported version is (%s)",
                    device.getSerialNumber(), currentBootVersion, MIN_BOOTLOADER_VER),
                    device.getSerialNumber());
        }
        return super.checkAndFlashBootloader(device, deviceBuild);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void overrideDeviceOptions(ITestDevice device) {
        if (!device.getUseFastbootErase()) {
            CLog.w("Overriding use-fastboot-erase to true. Fastboot format is not supported on " +
                    "Crespo");
            device.setUseFastbootErase(true);
        }
    }
}
