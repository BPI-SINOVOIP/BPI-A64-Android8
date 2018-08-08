// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.iot;

import com.android.tradefed.log.LogUtil.CLog;

import org.junit.Assert;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;

/**
 * A utility class for iot test.
 *
 * It contains test constants and implements utility functions.
 */
public class IotUtil {
    // Set maximum time to wait for an AP to power up to 10 minutes
    public static final long MAX_AP_POWERUP_TIMER = 10 * 60 * 1000;
    // Set minimum time to wait for an AP to power up to 60 seconds
    public static final long MIN_AP_POWERUP_TIMER = 60 * 1000;
    // Wait for another 5 minutes for AP to stable after it is powered up
    public static final long AP_STABLE_TIMER = 5 * 60 * 1000;
    public static final String PING_SERVER = "192.168.1.1";
    public static final String IPERF_HOST_SERVER = "192.168.1.1";
    /** Maximum number of APs that an NPS can connect to */
    public static final int MAX_NPS_AP_NUMBER = 16;

    public enum NPS {
        NPS_ONE("192.168.2.2"),
        NPS_TWO("192.168.2.3"),
        NPS_THREE("192.168.2.4");

        private String npsIpAddress;

        private NPS(String ipAddress) {
            npsIpAddress = ipAddress;
        }
        public String getIpAddress() {
            return npsIpAddress;
        }
    }

    /* Define the size of NPS as a constant */
    public static final int NPS_SIZE = NPS.values().length;

    public static BufferedInputStream getSourceFile(String fileName) {
        URL resUrl = IotUtil.class.getResource(String.format("/%s", fileName));
        CLog.v("The resource location is: " + resUrl.toString());

        InputStream input = IotUtil.class.getResourceAsStream(String.format("/%s", fileName));
        if (input == null) {
            CLog.v("loading resouce file failed");
            try {
                input = new FileInputStream(fileName);
            } catch (FileNotFoundException e) {
                Assert.fail(String.format("No AP XML file %s", fileName));
            }
        }
        return new BufferedInputStream(input);
    }
}
