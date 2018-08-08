// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.iot;

import com.android.tradefed.log.LogUtil.CLog;

import junit.framework.TestCase;

import org.junit.Assert;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;

public class AccessPointParserTest extends TestCase {
    private String testValidXml = new String(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<!-- record access point information -->\n"
            + "<resources>\n"
            + "<accesspoint>\n"
            + "<brand>D-Link</brand>\n"
            + "<model>8483</model>\n"
            + "<firmware>2.1</firmware>\n"
            + "<frequencyband>2.4</frequencyband>\n"
            + "<frequencyband>5.0</frequencyband>\n"
            + "<security>open</security>\n"
            + "<security>WEP64</security>\n"
            + "<security>WEP128</security>\n"
            + "<security>WPA-TRIP</security>\n"
            + "<security>WPA2-AES</security>\n"
            + "<ip>192.168.1.2</ip>\n"
            + "<npsid>NPS_ONE</npsid>"
            + "<npsplugid>A1</npsplugid>"
            + "</accesspoint>\n"
            + "</resources>");
    private String testInvalidIP = new String(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<!-- record access point information -->\n"
            + "<resources>\n"
            + "<accesspoint>\n"
            + "<brand>D-Link</brand>\n"
            + "<model>8483</model>\n"
            + "<firmware>2.1</firmware>\n"
            + "<frequencyband>2.4</frequencyband>\n"
            + "<security>WPA2-AES</security>\n"
            + "<ip>192.168.1.</ip>\n"
            + "</accesspoint>\n"
            + "</resources>");
    private String testInvalidFrequencyBand = new String(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<!-- record access point information -->\n"
            + "<resources>\n"
            + "<accesspoint>\n"
            + "<brand>D-Link</brand>\n"
            + "<model>8483</model>\n"
            + "<firmware>2.1</firmware>\n"
            + "<frequencyband>2.6</frequencyband>\n"
            + "<security>WPA2-AES</security>\n"
            + "<ip>192.168.1.2</ip>"
            + "</accesspoint>\n"
            + "</resources>");
    private String testMultipleNPS = new String(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<!-- record access point information -->\n"
            + "<resources>\n"
            + "<accesspoint>\n"
            + "<brand>D-Link</brand>\n"
            + "<model>8483</model>\n"
            + "<firmware>2.1</firmware>\n"
            + "<frequencyband>2.4</frequencyband>\n"
            + "<frequencyband>5.0</frequencyband>\n"
            + "<security>open</security>\n"
            + "<ip>192.168.1.2</ip>\n"
            + "<npsid>NPS_ONE</npsid>\n"
            + "<npsplugid>A1</npsplugid>\n"
            + "</accesspoint>\n"
            + "<accesspoint>\n"
            + "<brand>D-Link</brand>\n"
            + "<model>815</model>\n"
            + "<firmware>2.1</firmware>\n"
            + "<frequencyband>2.4</frequencyband>\n"
            + "<frequencyband>5.0</frequencyband>\n"
            + "<security>open</security>\n"
            + "<ip>192.168.1.3</ip>\n"
            + "<npsid>NPS_TWO</npsid>\n"
            + "<npsplugid>A1</npsplugid>\n"
            + "</accesspoint>\n"
            + "</resources>");

    private AccessPointParser app = null;
    AccessPointInfo[][] aps = null;
    private BufferedInputStream input = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        app = new AccessPointParser(null);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testValidXmlFile() throws Exception {
        input = createXmlInputStream(testValidXml);
        app.setXmlInputSource(input);
        try {
            app.parse();
        } catch (Exception e) {
            Assert.fail("Parsing should succeed, shouldn't reach here: " + e.toString());
        }
        aps = app.getAccessPointInfo();
        Assert.assertTrue(app.getTotalNumberAps() == 1);
        AccessPointInfo apInfo = aps[0][0];

        String brand = new String("D-Link");
        String model = new String("8483");
        String firmWare = new String("2.1");
        String ipAddress = new String("192.168.1.2");
        String npsId = new String("NPS_ONE");
        String npsPlugId = new String("A1");
        Assert.assertEquals(brand, apInfo.getBrand());
        Assert.assertEquals(model, apInfo.getModel());
        Assert.assertEquals(firmWare, apInfo.getFirmware());
        Assert.assertEquals(ipAddress, apInfo.getIpAddress());
        Assert.assertEquals(npsId, apInfo.getNpsId());
        Assert.assertEquals(npsPlugId, apInfo.getNpsPlugId());
        Assert.assertTrue(apInfo.isDualBand());
        Assert.assertEquals("number of security types is not equal to 5",
                5, apInfo.getNumberSecurityTypes());

        // test NPS enum type
        npsId = new String("NPS_ONE");
        IotUtil.NPS nps = IotUtil.NPS.valueOf(npsId);
        int index = nps.ordinal();
        Assert.assertEquals(0, index);
        ipAddress = IotUtil.NPS.valueOf(npsId).getIpAddress();
        Assert.assertEquals("192.168.2.2", ipAddress);

        npsId = new String("NPS_TWO");
        index = IotUtil.NPS.valueOf(npsId).ordinal();
        Assert.assertEquals(1, index);
        ipAddress = IotUtil.NPS.valueOf(npsId).getIpAddress();
        Assert.assertEquals("192.168.2.3", ipAddress);
    }

    public void testNullFile() throws Exception {
        app.setXmlInputSource(null);
        try {
            app.parse();
        } catch (Exception e) {
            CLog.e("Expected: input file shouldn't be null " + e.toString());
            return;
        }
        Assert.fail("Parsing on null input shouldn't succeed!");
    }

    public void testInValidIP() throws Exception {
        input = createXmlInputStream(testInvalidIP);
        app.setXmlInputSource(input);
        try {
            app.parse();
        } catch (Exception e) {
            CLog.e("Expected: parsing IP address failed: " + e.toString());
            return;
        }
        Assert.fail("Parsing invalid IP address shouldn't succeed");
    }

    public void testInValidFrequency() throws Exception {
        input = createXmlInputStream(testInvalidFrequencyBand);
        app.setXmlInputSource(input);
        try {
            app.parse();
        } catch (Exception e) {
            CLog.d("Expected: parsing frequency band failed: " + e.toString());
            return;
        }
        Assert.fail("Parsing invalid frequency shouldn't succeed");
    }

    public void testMultipleNPS() throws Exception {
        input = createXmlInputStream(testMultipleNPS);
        app.setXmlInputSource(input);
        try {
            app.parse();
        } catch (Exception e) {
            Assert.fail("Parsing should succeed, shouldn't reach here: " + e.toString());
        }
        aps = app.getAccessPointInfo();
        int nps_number = app.getTotalNumberAps();
        Assert.assertEquals(2, nps_number);

        AccessPointInfo ap1 = aps[0][0];
        AccessPointInfo ap2 = aps[1][0];
        AccessPointInfo ap3 = aps[0][1];
        Assert.assertNotNull(ap1);
        Assert.assertNotNull(ap2);
        Assert.assertNull(ap3);

        String brand = new String("D-Link");
        String model = new String("815");
        String npsId = new String("NPS_TWO");
        String npsPlugId = new String("A1");
        Assert.assertEquals(brand, ap2.getBrand());
        Assert.assertEquals(model, ap2.getModel());
        Assert.assertEquals(npsId, ap2.getNpsId());
        Assert.assertEquals(npsPlugId, ap2.getNpsPlugId());
    }

    private BufferedInputStream createXmlInputStream(String input) {
        BufferedInputStream tempInput;
        tempInput = new BufferedInputStream(new ByteArrayInputStream(input.getBytes()));
        return tempInput;
    }
}
