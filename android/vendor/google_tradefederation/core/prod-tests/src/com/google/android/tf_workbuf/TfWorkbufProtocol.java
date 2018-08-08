// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tf_workbuf;

/**
 * A small class to encapsulate the details of the TF-Workbuf wire protocol
 */
public abstract class TfWorkbufProtocol {
    // Header
    /**
     * Protocol version where the message is a JSONObject, containing keys and values
     */
    public static final int HEADER_JSON_OBJECT = 0x1;

    // JSON API
    public static final String KEY_REQUEST = "request";
    public static final String KEY_RESPONSE = "response";

    // Other stuff
    public static final int DEFAULT_PORT = 55443;

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

