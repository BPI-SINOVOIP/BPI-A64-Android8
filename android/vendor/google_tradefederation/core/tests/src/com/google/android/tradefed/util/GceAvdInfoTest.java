// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.android.tradefed.targetprep.TargetSetupError;

import junit.framework.TestCase;

public class GceAvdInfoTest extends TestCase {
    public void testValidGceJsonParsing() throws Exception {
        String valid = " {\n" +
                "    \"data\": {\n" +
                "      \"devices\": [\n" +
                "        {\n" +
                "          \"ip\": \"104.154.62.236\",\n" +
                "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22ecc3cf\"\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    \"errors\": [],\n" +
                "    \"command\": \"create\",\n" +
                "    \"status\": \"SUCCESS\"\n" +
                "  }";
        GceAvdInfo avd = GceAvdInfo.parseGceInfoFromString(valid, null);
        assertNotNull(avd);
        assertEquals(avd.hostAndPort().getHostText(), "104.154.62.236");
        assertEquals(avd.instanceName(), "gce-x86-phone-userdebug-2299773-22ecc3cf");
     }

     public void testNullStringJsonParsing() throws Exception {
        GceAvdInfo avd = GceAvdInfo.parseGceInfoFromString(null, null);
        assertNull(avd);
     }

     public void testEmptyStringJsonParsing() throws Exception {
         try {
             GceAvdInfo.parseGceInfoFromString(new String(), null);
             fail("A TargetSetupError should have been thrown.");
         } catch (TargetSetupError e) {
             // expected
         }
      }

     public void testMultipleGceJsonParsing() throws Exception {
         String multipleInstances = " {\n" +
                 "    \"data\": {\n" +
                 "      \"devices\": [\n" +
                 "        {\n" +
                 "          \"ip\": \"104.154.62.236\",\n" +
                 "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22ecc3cf\"\n" +
                 "        },\n" +
                 "       {\n" +
                 "          \"ip\": \"104.154.62.236\",\n" +
                 "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22ecc3cf\"\n" +
                 "        }\n" +
                 "      ]\n" +
                 "    },\n" +
                 "    \"errors\": [],\n" +
                 "    \"command\": \"create\",\n" +
                 "    \"status\": \"SUCCESS\"\n" +
                 "  }";
         try {
             GceAvdInfo.parseGceInfoFromString(multipleInstances, null);
             fail("A TargetSetupError should have been thrown.");
         } catch (TargetSetupError e) {
             // expected
         }
     }

     public void testInvalidJsonParsing() throws Exception {
         String invalidJson = "bad_json";
         try {
             GceAvdInfo.parseGceInfoFromString(invalidJson, null);
             fail("A TargetSetupError should have been thrown.");
         } catch (TargetSetupError e) {
             // expected
         }
     }

     public void testMissingGceJsonParsing() throws Exception {
         String missingInstance = " {\n" +
                 "    \"data\": {\n" +
                 "      \"devices\": [\n" +
                 "      ]\n" +
                 "    },\n" +
                 "    \"errors\": [],\n" +
                 "    \"command\": \"create\",\n" +
                 "    \"status\": \"SUCCESS\"\n" +
                 "  }";
         try {
             GceAvdInfo.parseGceInfoFromString(missingInstance, null);
             fail("A TargetSetupError should have been thrown.");
         } catch (TargetSetupError e) {
             // expected
         }
     }

     /**
      * In case of failure to boot in expected time, we need to parse the error to get the instance
      * name and stop it.
      * @throws Exception
      */
     public void testValidGceJsonParsingFail() throws Exception {
         String validFail = " {\n" +
                 "    \"data\": {\n" +
                 "      \"devices_failing_boot\": [\n" +
                 "        {\n" +
                 "          \"ip\": \"104.154.62.236\",\n" +
                 "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22ecc3cf\"\n" +
                 "        }\n" +
                 "      ]\n" +
                 "    },\n" +
                 "    \"errors\": [],\n" +
                 "    \"command\": \"create\",\n" +
                 "    \"status\": \"FAIL\"\n" +
                 "  }";
         GceAvdInfo avd = GceAvdInfo.parseGceInfoFromString(validFail, null);
         assertNotNull(avd);
         assertEquals(avd.hostAndPort().getHostText(), "104.154.62.236");
         assertEquals(avd.instanceName(), "gce-x86-phone-userdebug-2299773-22ecc3cf");
      }

     /**
      * On a quota error No GceAvd information is created because the instance was not created.
      * @throws Exception
      */
     public void testValidGceJsonParsingFailQuota() throws Exception {
         String validError = " {\n" +
                 "    \"data\": {},\n" +
                 "    \"errors\": [\n" +
                 "\"Get operation state failed, errors: [{u'message': u\\\"Quota 'CPUS' exceeded.  "
                 + "Limit: 500.0\\\", u'code': u'QUOTA_EXCEEDED'}]\"\n"
                 + "],\n" +
                 "    \"command\": \"create\",\n" +
                 "    \"status\": \"FAIL\"\n" +
                 "  }";
         try {
             GceAvdInfo.parseGceInfoFromString(validError, null);
             fail("A TargetSetupError should have been thrown.");
         } catch (TargetSetupError e) {
             // expected
         }
     }

     /**
      * In case of failure to boot in expected time, we need to parse the error to get the instance
      * name and stop it.
      * @throws Exception
      */
     public void testParseJson_Boot_Fail() throws Exception {
         String validFail = " {\n" +
                 "    \"data\": {\n" +
                 "      \"devices_failing_boot\": [\n" +
                 "        {\n" +
                 "          \"ip\": \"104.154.62.236\",\n" +
                 "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22ecc3cf\"\n" +
                 "        }\n" +
                 "      ]\n" +
                 "    },\n" +
                 "    \"errors\": [\"device did not boot\"],\n" +
                 "    \"command\": \"create\",\n" +
                 "    \"status\": \"BOOT_FAIL\"\n" +
                 "  }";
         GceAvdInfo avd = GceAvdInfo.parseGceInfoFromString(validFail, null);
         assertNotNull(avd);
         assertEquals(avd.hostAndPort().getHostText(), "104.154.62.236");
         assertEquals(avd.instanceName(), "gce-x86-phone-userdebug-2299773-22ecc3cf");
         assertEquals(GceAvdInfo.GceStatus.BOOT_FAIL, avd.getStatus());
      }
}
