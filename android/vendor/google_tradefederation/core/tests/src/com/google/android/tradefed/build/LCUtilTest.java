// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import junit.framework.TestCase;

import java.util.Map;

/**
 * Helper class to parse a Launch Control path into its constituent parts
 */
public class LCUtilTest extends TestCase {
    public void testParseAttributeLine() throws Exception {
        final String line = "userdata:git_master-linux-yakju-tests/242537/userdata.img";
        Map<String, String> m = LCUtil.parseAttributeLine(line);
        assertEquals(6, m.size());
        assertFalse(m.containsKey("kernel"));
        assertEquals("userdata", m.get("key"));
        assertEquals("git_master", m.get("branch"));
        assertEquals("linux", m.get("os"));
        assertEquals("yakju-tests", m.get("flavor"));
        assertEquals("242537", m.get("bid"));
        assertEquals("userdata.img", m.get("filename"));
    }

    public void testParseAttributeLine_branchWithDash() throws Exception {
        final String line = "ub-gcore-parmesan-release-linux-GmsCore/1821559/GmsCore.apk";
        Map<String, String> m = LCUtil.parseAttributeLine(line);
        assertEquals(6, m.size());
        assertFalse(m.containsKey("kernel"));
        assertNull(m.get("key"));
        assertEquals("ub-gcore-parmesan-release", m.get("branch"));
        assertEquals("linux", m.get("os"));
        assertEquals("GmsCore", m.get("flavor"));
        assertEquals("1821559", m.get("bid"));
        assertEquals("GmsCore.apk", m.get("filename"));
    }

    public void testParseAttributeLine_fastbuild() throws Exception {
        final String line ="git_master-release-fastbuild_linux-hammerhead-userdebug/1834480/bootloader.img";
        Map<String, String> m = LCUtil.parseAttributeLine(line);
        assertEquals(6, m.size());
        assertFalse(m.containsKey("kernel"));
        assertNull(m.get("key"));
        assertEquals("git_master-release", m.get("branch"));
        assertEquals("fastbuild_linux", m.get("os"));
        assertEquals("hammerhead-userdebug", m.get("flavor"));
        assertEquals("1834480", m.get("bid"));
        assertEquals("bootloader.img", m.get("filename"));
    }

    public void testParseKernelAttributeLine() throws Exception {
        final String line = "android-omap-tuna-3.0-master/1e843e8996f61a22794bf54da25b008c6886c140/kernel";
        Map<String, String> m = LCUtil.parseAttributeLine(line);
        assertEquals(4, m.size());
        assertTrue(m.containsKey("kernel"));
        assertEquals("android-omap-tuna-3.0-master", m.get("branch"));
        assertEquals("1e843e8996f61a22794bf54da25b008c6886c140", m.get("bid"));
        assertEquals("kernel", m.get("filename"));
    }
}

