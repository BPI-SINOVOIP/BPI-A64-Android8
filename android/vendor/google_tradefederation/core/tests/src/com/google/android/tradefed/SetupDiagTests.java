// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed;

import com.android.tradefed.device.DeviceDiagTest;
import com.android.tradefed.testtype.DeviceTestSuite;
import com.google.android.tradefed.device.NcdDeviceRecoveryFuncTest;

import junit.framework.Test;

/**
 * A test suite that verifies that a host machine has been properly set up for Google
 * TradeFederation.
 * <p/>
 * This suite requires a device and access to Google NFS and MDB.
 */
public class SetupDiagTests extends DeviceTestSuite {

    public SetupDiagTests() {
        super();
        this.addTestSuite(DeviceDiagTest.class);
        this.addTest(new NcdDeviceRecoveryFuncTest("testResetUsb"));
        this.addTest(new NcdDeviceRecoveryFuncTest("testResetDevice"));
        this.addTest(new NcdDeviceRecoveryFuncTest("testAllDevicesOnNcd"));
    }

    public static Test suite() {
        return new SetupDiagTests();
    }
}
