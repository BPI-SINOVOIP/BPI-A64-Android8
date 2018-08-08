// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.iot;

import junit.framework.TestCase;

public class ApControllerTest extends TestCase {
    ApController mApController;

    @Override
    public void setUp() throws Exception {
        mApController = new ApController(null);
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testApController() throws Exception {
        AccessPointInfo testAp = new AccessPointInfo("D-Link", "8483");
        testAp.addFrequencyBand("2.4");
        testAp.setFirmware("2.1");
        testAp.addSecurityType("open");
        testAp.setIpAddress("192.168.1.2");
        testAp.setNpsId("NPS_ONE");
        testAp.setNpsPlugId("A6");
        System.out.println("create a test ap");
        mApController.enableAp(testAp);
    }
}
