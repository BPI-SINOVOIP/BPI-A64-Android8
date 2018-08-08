// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import junit.framework.TestCase;

public class CompatibilityResultReporterTest extends TestCase {

    /**
     * Tests that the parser can de-serialize {@link CompatibilityTestResult} from data inserted in
     * front of logcat output
     * @throws Exception
     */
    public void testLogcatHeaderParsing() throws Exception {
        String rawLogcat = "=@ppcomp@t={\"rank\":\"0\",\"status\":\"success\",\"app_package\":\"com.frogmind.badland\",\"app_version\":\"1.7173\"}=@ppcomp@t=\n" +
                "--------- beginning of main\n" +
                "09-12 21:03:58.203   148   148 W auditd  : type=2000 audit(0.0:1): initialized\n" +
                "09-12 21:04:00.160   154   154 I /system/bin/tzdatacheck: tzdata file /data/misc/zoneinfo/current/tzdata does not exist. No action required.\n" +
                "09-12 21:04:00.563   191   191 I lowmemorykiller: Using in-kernel low memory killer interface\n" +
                "09-12 21:04:00.612   202   202 I         : debuggerd: May 28 2015 20:58:26\n" +
                "09-12 21:04:00.625   207   207 I installd: installd firing up\n" +
                "09-12 21:04:00.682   215   215 I bdAddrLoader: option : f=/persist/bluetooth/.bdaddr\n" +
                "09-12 21:04:00.683   215   215 I bdAddrLoader: option : h\n";
        CompatibilityResultReporter reporter = new CompatibilityResultReporter();
        assertNotNull(reporter.parseResultFromLogcatHeader(rawLogcat));
    }
}
