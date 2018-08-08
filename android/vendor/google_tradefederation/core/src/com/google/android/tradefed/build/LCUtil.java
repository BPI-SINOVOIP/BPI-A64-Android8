// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class to parse a Launch Control path into its constituent parts
 */
class LCUtil {
    private LCUtil() {
        // hidden
    }

    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
    //        key(opt)      branch    os                  flavor    bid       filename
            "(?:([^:]+):)? (.+?   ) -([^-]*linux|mac)   -([^/]+ )/ (P?\\d+)/ (.+      )",
            Pattern.COMMENTS);

    private static final Pattern KERNEL_ATTRIBUTE_PATTERN = Pattern.compile(
    //        kernel-branch   sha1              filename
            "([^/]+        )/([a-fA-F0-9]{40})/(.+      )", Pattern.COMMENTS);

    /**
     * Parse a line like "userdata:git_master-linux-yakju-tests/242537/userdata.img" into a Map
     * like "{key:userdata, branch:git_master, flavor:yakju-tests, bid:242537, file:userdata.img}"
     */
    public static Map<String, String> parseAttributeLine(String line) {
        Map<String, String> out = new HashMap<String, String>();
        Matcher m = ATTRIBUTE_PATTERN.matcher(line);
        if (m.matches()) {
            // key may be missing
            out.put("key", m.group(1));
            out.put("branch", m.group(2));
            out.put("os", m.group(3));
            out.put("flavor", m.group(4));
            out.put("bid", m.group(5));
            out.put("filename", m.group(6));
            return out;
        }
        m = KERNEL_ATTRIBUTE_PATTERN.matcher(line);
        if (m.matches()) {
            // key may be missing
            out.put("kernel", "");
            out.put("branch", m.group(1));
            out.put("bid", m.group(2));
            out.put("filename", m.group(3));
            return out;
        }
        return null;
    }
}

