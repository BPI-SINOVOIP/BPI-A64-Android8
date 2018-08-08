// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.ota;

import com.android.tradefed.util.FileUtil;
import com.google.android.ota.OtaRuleGenerator.IRuleListener;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * Unit tests for (@link OtaRuleGenerator}.
 */
public class OtaRuleGeneratorTest extends TestCase {

    private IRuleListener mStubListener = new IRuleListener() {
        @Override
        public void otaRuleGenerated(File otaPackage) {
            // ignore
        }
    };

    /**
     * Test method for {@link com.google.android.ota.ConfigureSignedOta#convertSize(long)}.
     */
    public void testConvertSize() {
        OtaRuleGenerator otaConfigurer = new OtaRuleGenerator("mybuild", mStubListener,
                new StringWriter(), false /* no selfupdate */);
        assertEquals("50", otaConfigurer.convertSize(50));
        assertEquals("1K", otaConfigurer.convertSize(1025));
        assertEquals("1023K", otaConfigurer.convertSize(1024 * 1024 - 1));
        assertEquals("1M", otaConfigurer.convertSize(1024 * 1024));
    }


    public void testGenerateConfigFromOtaProp() throws IOException {
        final String expectedString =
                "Rule {" +
                "require: \"fingerprint:prefingerprint\"" +
  "Set { name: \"update_title\" value: \"mybuild\" }" +
  "Set { name: \"update_size\" value: \"0\" }" +
  "Set { name: \"update_urgency\" value: \"3\" }" +
  "Set { name: \"update_watchdog_frequency\" value: \"3600000\" }" +
  "Set {" +
  "  name: \"update_description\"" +
  "  value: \"<p>mybuild update</p>\"" +
  "}" +
  "Set {" +
  "  name: \"update_url\"" +
  "  value: \"http://android.clients.google.com/packages/internal/%s\"" +
  "}" +
  "}";
        StringWriter output = new StringWriter();
        OtaRuleGenerator otaConfigurer = new OtaRuleGenerator("mybuild", mStubListener,
                output, false /* no selfupdate */);
        Reader input = new StringReader(
                "pre-build=prefingerprint\n" +
                "post-build=postfingerprint\n"
        );
        File tmpFile = FileUtil.createTempFile("foo", ".txt");
        try {
            otaConfigurer.generateConfigFromOtaProp(output, tmpFile, input);
            assertEquals(cleanString(String.format(expectedString, tmpFile.getName())),
                    cleanString(output.toString()));
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    private String cleanString(String input) {
        return input.replaceAll("\\s", "");
    }
}
