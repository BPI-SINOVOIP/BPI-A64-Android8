// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.ddmlib.Log;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.testtype.DeviceTestCase;

/**
 * Google-specific functional tests for {@link TestDevice}.
 */
public class GTestDeviceFuncTest extends DeviceTestCase {

    private static final String LOG_TAG = "GTestDeviceFuncTest";
    private TestDeviceOptions mOptions;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mOptions = getDevice().getOptions();
    }

    @Override
    protected void tearDown() throws Exception {
        getDevice().setOptions(mOptions);
        super.tearDown();
    }

    /** Test that device can connect to GoogleGuest wifi */
    public void testConnectToWifiNetwork() throws Exception {
        try {
            Log.i(LOG_TAG, "testConnectToWifiNetwork()");
            GceAvdTestDeviceOptions tmpOptions = new GceAvdTestDeviceOptions();
            tmpOptions.setWifiAttempts(3);
            OptionSetter setter = new OptionSetter(tmpOptions);
            setter.setOptionValue("wifi-exponential-retry", "false");
            setter.setOptionValue("wifi-retry-wait-time", "30000");
            getDevice().setOptions(tmpOptions);
            assertTrue(getDevice().connectToWifiNetwork("AndroidWifi", null));
            String pingOutput = getDevice().executeShellCommand("ping -c 1 -w 5 www.google.com");
            assertTrue("ping unsuccessful: " + pingOutput, pingOutput
                    .contains("1 packets transmitted, 1 received"));
        } finally {
            getDevice().disconnectFromWifi();
        }
    }
}
