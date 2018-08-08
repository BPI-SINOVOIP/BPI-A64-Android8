// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import com.google.android.tradefed.result.EmmaPackageSummaryParser.PackageNode;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Unit tests for {@link EmmaPackageSummaryParser}.
 */
public class EmmaPackageSummaryParserTest extends TestCase {

    static final String FULL_DATA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<report>" +
            "<package name=\"com.foo\" >\n" +
            "<coverage type=\"block, %\" value=\"2%   (2/3)\"/>" +
            "</package>" +
            "<package name=\"com.bar\" >\n" +
            "<coverage type=\"block, %\" value=\"2%   (3/4)\"/>" +
            "</package>" +
            "<package name=\"com.foo.bar\" >\n" +
            "<coverage type=\"block, %\" value=\"2%   (5/6)\"/>" +
            "</package>" +
            "</report>";

    /**
     * Test that normal parsing of a well-formed emma xml report works as
     * expected.
     */
    public void testNormalParsing() throws Exception {
        EmmaPackageSummaryParser p = new EmmaPackageSummaryParser();
        p.parse(getStringAsStream(FULL_DATA));
        PackageNode root = p.getRoot();
        assertEquals(10, root.getCoveredBlocks());
        assertEquals(13, root.getTotalBlocks());
        PackageNode foo = p.findPackageNode("com.foo");
        assertEquals(7, foo.getCoveredBlocks());
        assertEquals(9, foo.getTotalBlocks());
    }

    private InputStream getStringAsStream(String input) {
        return new ByteArrayInputStream(input.getBytes());
    }
}
