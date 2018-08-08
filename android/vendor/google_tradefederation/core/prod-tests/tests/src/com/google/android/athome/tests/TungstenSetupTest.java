// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.athome.tests;

import com.google.android.athome.tests.TungstenSetup;

import junit.framework.TestCase;

/**
 * Unit tests for {@link TungstenSetupTest}
 */
public class TungstenSetupTest extends TestCase {

    /**
     * Test that bluetooth address parsing logic returns valid address
     */
    public void testParseValidAddresses() {
        String address = TungstenSetup.extractBTAddress("00:1A:11:30:20:A3");
        assertEquals("00:1A:11:30:20:A3", address);

        address = TungstenSetup.extractBTAddress("00:1a:11:30:25:ed");
        assertEquals("00:1a:11:30:25:ed", address);

        address = TungstenSetup.extractBTAddress("0A:1b:2C:3d:4E:5f");
        assertEquals("0A:1b:2C:3d:4E:5f", address);

        address = TungstenSetup.extractBTAddress("ab:cd:ef:ab:cd:ef");
        assertEquals("ab:cd:ef:ab:cd:ef", address);

        address = TungstenSetup.extractBTAddress("01:23:45:67:89:01");
        assertEquals("01:23:45:67:89:01", address);
    }

    /**
     * Test that bluetooth address parsing logic rejects invalid address
     */
    public void testParseInvalidAddresses() {
        String address = TungstenSetup.extractBTAddress(":1A:11:30:20:A3");
        assertNull("Invalid BT address (:1A:11:30:20:A3) returned a match", address);

        address = TungstenSetup.extractBTAddress("12:32:1A:11:30:20:A3");
        assertNull("Invalid BT address (12:32:1A:11:30:20:A3) returned a match", address);

        address = TungstenSetup.extractBTAddress("ab:cd:ef:ga:bc:de");
        assertNull("Invalid BT address (ab:cd:ef:ga:bc:de) returned a match", address);

        address = TungstenSetup.extractBTAddress("00:1A:11:30:20:A3:f2");
        assertNull("Invalid BT address (00:1A:11:30:20:A3:f2) returned a match", address);

        address = TungstenSetup.extractBTAddress("1a:3b:2d");
        assertNull("Invalid BT address (1a:3b:2d) returned a match", address);

        address = TungstenSetup.extractBTAddress("1a:3:2d:93:53:bc");
        assertNull("Invalid BT address (1a:3:2d:93:53:bc) returned a match", address);

        address = TungstenSetup.extractBTAddress("00aa77bb44dd");
        assertNull("Invalid BT address (00aa77bb44dd) returned a match", address);

        address = TungstenSetup.extractBTAddress(null);
        assertNull("Invalid BT address (null) returned a match", address);
    }
}
