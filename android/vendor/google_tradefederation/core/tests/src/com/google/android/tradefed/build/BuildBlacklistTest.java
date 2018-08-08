// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.StringReader;

/**
 * Unit tests for {@link BuildBlacklist}
 */
public class BuildBlacklistTest extends TestCase {
    private BuildBlacklist mBlacklist;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mBlacklist = new BuildBlacklist(0);
    }

    /**
     * Make sure that the simple blacklisting case works as expected
     */
    public void testSimpleParse() throws Exception {
        assertFalse("Build 12345 is blacklisted unexpectedly",
                mBlacklist.isBlacklistedBuild("12345", "flavor"));
        assertFalse("Build 23456 is blacklisted unexpectedly",
                mBlacklist.isBlacklistedBuild("23456", "flavor"));
        mBlacklist.parse(read("12345\n23456\n"));
        assertTrue("Build 12345 isn't blacklisted", mBlacklist.isBlacklistedBuild("12345",
                "flavor"));
        assertTrue("Build 23456 isn't blacklisted", mBlacklist.isBlacklistedBuild("23456",
                "flavor"));
    }

    /**
     * Make sure that parsing works as expected when there are whitespace and comments
     */
    public void testComments() throws Exception {
        assertFalse("Build 12345 is blacklisted unexpectedly",
                mBlacklist.isBlacklistedBuild("12345", "flavor"));
        assertFalse("Build 23456 is blacklisted unexpectedly",
                mBlacklist.isBlacklistedBuild("23456", "flavor"));
        mBlacklist.parse(read("\t#12345\n\n  ## 23456\n\t\t34567 # blah"));
        assertFalse("Build 12345 was blacklisted while commented",
                mBlacklist.isBlacklistedBuild("12345", "flavor"));
        assertFalse("Build 23456 was blacklisted while commented",
                mBlacklist.isBlacklistedBuild("23456", "flavor"));
        assertTrue("Build 34567 isn't blacklisted", mBlacklist.isBlacklistedBuild("34567",
                "flavor"));
    }

    /**
     * Make sure that parsing works as expected for the 'blacklist all flavors' syntax
     */
    public void testParse_allFlavors() throws Exception {
        mBlacklist.parse(read("\t12345 *\n23456\t*"));
        assertTrue("Build 12345 was not blacklisted",
                mBlacklist.isBlacklistedBuild("12345", "flavor"));
        assertTrue("Build 23456 was not blacklisted",
                mBlacklist.isBlacklistedBuild("23456", "flavor2"));
        assertFalse("Build 34567 is blacklisted", mBlacklist.isBlacklistedBuild("34567",
                "flavor"));
    }

    /**
     * Make sure that parsing works as expected when blacklisting only a single flavor
     */
    public void testParse_specificFlavor() throws Exception {
        mBlacklist.parse(read("\t12345 flavor1\n12345\tflavor2"));
        assertTrue("Build 12345 flavor1 was not blacklisted",
                mBlacklist.isBlacklistedBuild("12345", "flavor1"));
        assertTrue("Build 12345 flavor2 was not blacklisted",
                mBlacklist.isBlacklistedBuild("12345", "flavor2"));
        assertFalse("Build 12345 flavor3 is blacklisted", mBlacklist.isBlacklistedBuild("12345",
                "flavor3"));
    }

    private BufferedReader read(String str) {
        return new BufferedReader(new StringReader(str));
    }
}
