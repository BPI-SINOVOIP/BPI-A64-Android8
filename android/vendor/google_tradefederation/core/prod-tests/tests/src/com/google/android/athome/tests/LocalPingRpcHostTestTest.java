// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.athome.tests;

import com.google.android.athome.tests.LocalPingRpcHostTest.PortParser;

import junit.framework.TestCase;

/**
 * Unit tests for {@link LocalPingRpcHostTest}
 */
public class LocalPingRpcHostTestTest extends TestCase {

    /**
     * Test that port parsing logic works
     */
    public void testParsePort() {
        PortParser parser = new PortParser();
        parser.processNewLines(new String[] {"no port", "no port here either"});
        assertNull(parser.getPort());
        parser.processNewLines(new String[] {"no port", "PING_PORT: 1234"});
        assertEquals(Integer.valueOf(1234), parser.getPort());
    }
}
