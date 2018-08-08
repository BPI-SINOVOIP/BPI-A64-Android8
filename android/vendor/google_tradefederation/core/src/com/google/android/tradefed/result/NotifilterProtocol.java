// Copyright 2012 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

/**
 * A small class to encapsulate the details of the Notifilter wire protocols
 */
public abstract class NotifilterProtocol {
    // Header values
    public static final int HEADER_JSON = 1<<0;

    // JSON API
    public static final String TESTTAG_KEY = "testTag";
    public static final String PKGNAME_KEY = "pkgName";
    public static final String CLASSNAME_KEY = "className";
    public static final String METHODNAME_KEY = "methodName";
    public static final String TARGET_KEY = "target";
    public static final String BRANCH_KEY = "branch";
    public static final String BUILDID_KEY = "buildId";
    public static final String FLAVOR_KEY = "flavor";
    public static final String STATUS_KEY = "status";
    public static final String STACK_KEY = "stackTrace";
    public static final String SUMMARY_KEY = "summaryUrl";
    public static final String RUNS_KEY = "runs";
    public static final String DISP_BUILDID_KEY = "display_build_id";
    public static final String EXTRAS_KEY = "extras";
    public static final String NGEXTRAS_KEY = "ngextras";

    /**
     * Pack up to four bytes into an int in MSB order (that is, network byte order).  Assumes that
     * the bytes are already in MSB order.
     */
    public static int fourBytesToInt(byte[] bytes) {
        if (bytes.length > 4) throw new IllegalArgumentException("Expected at most four bytes.");
        if (bytes.length == 0) return 0;

        int val = bytes[0];
        for (int i = 1; i < bytes.length; ++i) {
            // shift by 8 bits
            val <<= 8;
            // pack the next 8 bits
            val |= bytes[i] & 0xff;
        }
        return val;
    }

    /**
     * Unpack an int into four bytes.  Unpacks the int in MSB order, and emits the bytes in
     * that order.
     */
    public static byte[] intToFourBytes(int val) {
        if (val < 0) throw new IllegalArgumentException("Value may not be signed.");
        byte[] bytes = new byte[4];

        for (int i = 3; i >= 0; --i) {
            // unpack a byte
            byte b = (byte)(val & 0xff);
            // shift by 8 bits
            val >>= 8;
            // store the unpacked byte, moving from LSB to MSB (so the MSB ends up in bytes[0])
            bytes[i] = b;
        }

        return bytes;
    }
}

