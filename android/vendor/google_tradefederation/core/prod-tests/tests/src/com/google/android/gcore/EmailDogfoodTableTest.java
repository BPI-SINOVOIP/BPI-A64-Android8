// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.gcore;

import junit.framework.TestCase;

/**
 * Unit tests for EmailDogfoodTable.
 */
public class EmailDogfoodTableTest extends TestCase {

    public void testParseVersionCode() {
        EmailDogfoodTable t = new EmailDogfoodTable();
        int[] versions = t.parseVersionCode("51060300");
        assertEquals(5, versions[0]);
        assertEquals(1, versions[1]);
        assertEquals(6, versions[2]);
    }
}
