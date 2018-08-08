// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.FastbootDeviceFlasher;
import com.android.tradefed.targetprep.TargetSetupError;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link FastbootDeviceFlasher} for flashing Motorola Sholes devices.
 */
public class SholesDeviceFlasher extends FastbootDeviceFlasher {
    /**
     * {@inheritDoc}
     */
    @Override
    protected String getBootPartitionName() {
        return "motoboot";
    }

    /**
     * Get the bootloader file prefix based on device secure setting.
     * @throws DeviceNotAvailableException
     */
    @Override
    protected String getBootloaderFilePrefix(ITestDevice device) throws TargetSetupError,
            DeviceNotAvailableException {
        String secureOutput = executeFastbootCmd(device, "getvar", "secure");
        Pattern secureOutputPattern = Pattern.compile("secure:\\s*(.*)\\s*");
        Matcher matcher = secureOutputPattern.matcher(secureOutput);
        String secureVal = null;
        if (matcher.find()) {
            secureVal = matcher.group(1);
        }
        if ("yes".equals(secureVal)) {
            return "motoboot_secure";
        } else if ("no".equals(secureVal)) {
            return "motoboot_unsecure";
        } else {
            // device can be bricked if wrong bootloader is flashed, so be overly cautious
            throw new TargetSetupError("Could not determine 'secure' value for moto bootloader",
                    device.getDeviceDescriptor());
        }
    }

    /**
     * Flashes the sholes baseband, which requires special hand-holding
     */
    @Override
    protected void flashBaseband(ITestDevice device, File basebandImageFile)
            throws DeviceNotAvailableException, TargetSetupError {
        executeLongFastbootCmd(device, "flash", "radio", basebandImageFile.getAbsolutePath());
        // flashing the radio on a sholes is a different beast...
        // reboot and wait until the device boots completely after flash
        // TODO: flash recovery images first.
        device.reboot();
        device.rebootIntoBootloader();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void overrideDeviceOptions(ITestDevice device) {
        if (!device.getUseFastbootErase()) {
            CLog.w("Overriding use-fastboot-erase to true. Fastboot format is not supported on " +
                    "Sholes");
            device.setUseFastbootErase(true);
        }
    }
}
