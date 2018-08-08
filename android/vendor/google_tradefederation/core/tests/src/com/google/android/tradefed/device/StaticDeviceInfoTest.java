// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.google.android.tradefed.targetprep.HammerheadDeviceFlasher;
import com.google.android.tradefed.targetprep.ShamuDeviceFlasher;

import junit.framework.TestCase;

/**
 * Test class for {@link StaticDeviceInfo}.
 */
public class StaticDeviceInfoTest extends TestCase {

    /**
     * Check that {@link StaticDeviceInfo#getFlasherClass(String, String)} can handle a null
     * variant with unknown product
     */
    public void testGetFlasherClass_unknownProductNullVariant() {
        assertNull(StaticDeviceInfo.getFlasherClass("foo", null));
    }

    /**
     * Check that {@link StaticDeviceInfo#getFlasherClass(String, String)} can handle a null
     * product.
     */
    public void testGetFlasherClass_nullProduct() {
        assertNull(StaticDeviceInfo.getFlasherClass(null, "hammerhead"));
    }

    /**
     * Check that {@link StaticDeviceInfo#getFlasherClass(String, String)} can handle a null
     * variant with known product
     */
    public void testGetFlasherClass_knownProductNullVariant() {
        assertNull(StaticDeviceInfo.getFlasherClass("hammerhead", null));
    }

    /**
     * Check that {@link StaticDeviceInfo#getFlasherClass(String, String)} can fetch a flasher when
     * both variant and product match.
     */
    public void testGetFlasherClass() {
        Class<?> test = StaticDeviceInfo.getFlasherClass("hammerhead", "hammerhead");
        assertNotNull(test);
        assertTrue((HammerheadDeviceFlasher.class.equals(test)));
    }

    /**
     * Check that {@link StaticDeviceInfo#getFlasherClass(String, String)} can fetch a flasher when
     * product match and variant is left open.
     */
    public void testGetFlasherClass_onlyProduct() {
        Class<?> test = StaticDeviceInfo.getFlasherClass("shamu", null);
        assertNotNull(test);
        assertTrue((ShamuDeviceFlasher.class.equals(test)));
    }

    /**
     * Check that {@link StaticDeviceInfo#getDefaultLcFlavor(String, String)} can handle a null
     * variant with known product.
     */
    public void testGetDefaultLcFlavor_nullVariant() {
        assertNull(StaticDeviceInfo.getDefaultLcFlavor("hammerhead", null));
    }

    /**
     * Check that {@link StaticDeviceInfo#getDefaultLcFlavor(String, String)} with non null variant
     */
    public void testGetDefaultLcFlavor() {
        assertEquals("hammerhead", StaticDeviceInfo.getDefaultLcFlavor("hammerhead", "hammerhead"));
    }

    /**
     * Check that {@link StaticDeviceInfo#getDefaultLcFlavor(String, String)} can handle a null
     * variant with known product for pair where the variant is not required.
     */
    public void testGetDefaultLcFlavor_withWildMatching() {
        assertEquals("shamu", StaticDeviceInfo.getDefaultLcFlavor("shamu", null));
    }
}
