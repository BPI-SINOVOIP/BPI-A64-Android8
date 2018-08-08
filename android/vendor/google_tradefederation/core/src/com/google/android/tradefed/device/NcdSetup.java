// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;

import junit.framework.TestCase;

/**
 * A test case that will perform the one-time setup to configure a device to auto-boot when usb and
 * battery are both attached.
 * <p/>
 * This is needed to support the {@link NcdDeviceRecovery#resetDevice(String)} functionality.
 * <p/>
 * Implemented as a {@link TestCase} to make it easy to run.
 */
public class NcdSetup extends DeviceTestCase {

    public void testEnableAutoBoot() throws DeviceNotAvailableException, InterruptedException {
        String productType = getDevice().getProductType();
        if (productType.equals("stingray")) {
            // stingray is special
            getDevice().enableAdbRoot();
            getDevice().executeShellCommand("bypassfactory");
        } else {
            getDevice().rebootIntoBootloader();
            getDevice().executeFastbootCommand("oem", "writeconfig", "8", "100000");
            Thread.sleep(3*1000);
            getDevice().reboot();
        }
    }
}
