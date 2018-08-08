//Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.iot;

import com.android.tradefed.log.LogUtil.CLog;

import junit.framework.TestCase;

/**
 * Test class for DeviceHelper
 */
public class DeviceHelperTest extends TestCase {

    public void testGetAvailableDevices() throws Exception {
        DeviceHelper dh = new DeviceHelper(null);
        int numberDevices = dh.getAvailableDevices();
        CLog.i("the number of available devices is: %d", numberDevices);
    }
}