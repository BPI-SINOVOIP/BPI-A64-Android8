// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.bandwidth.tests;

import junit.framework.TestCase;

public class DataAbuserTestTest extends TestCase {

    /*
     * Make sure that parsing the uid from a dump works as expected.
     */
    public void testparseUidFromDumpSys()  {
        String testFoo = ("Package [com.google.android.tests.dataabuser] (410655c0):\n" +
                "userId=10054 gids=[3003, 1015]\n" +
                "sharedUser=null");
        assertEquals(10054, DataAbuserTest.parseUidFromDumpSys(testFoo).longValue());
    }

}
