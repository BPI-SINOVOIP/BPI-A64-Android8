// Copyright 2013 Google Inc. All Rights Reserved.
package com.google.android.notifilter;

import com.android.notifilter.TemplateRenderer;
import com.android.notifilter.TemplateRendererTest;
import com.android.notifilter.Notifilter;
import com.android.notifilter.common.IDatastore;
import com.android.notifilter.common.IDatastore.Flakiness;
import com.android.notifilter.common.IDatastore.Notif;
import com.android.notifilter.common.IDatastore.StatusKey;
import com.android.notifilter.common.IDatastore.StatusValue;
import com.android.notifilter.common.IDatastore.UpdateMask;
import com.android.notifilter.common.IEmail.Message;
import com.android.notifilter.common.Util;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GoogleTemplatesTest extends TestCase {
    private static final String ARTIFACT_PATH_PROPERTY = "TemplateOutputPath";
    private static final String TEMPLATE_LIST_PATH = "/template/google/template_list.txt";
    private List<String> mTemplateNames = null;

    private static final String[] STOCK_KEY_INPUT = new String[] {"testMethod", ".TestClass",
            "com.test.package", "TestSuite", "test-target", "git_branch", "12345", "PASS",
            "http://foo.com/bar?baz=quux"};
    private static final StatusKey STOCK_KEY = new StatusKey(STOCK_KEY_INPUT);
    private static final StatusValue STOCK_VALUE = new StatusValue();
    private TemplateRenderer mTemplateRenderer = null;

    private String mOldChangeInclusionValue = null;

    static {
        STOCK_VALUE.build = 12345;
        STOCK_VALUE.passed = true;
    }

    @Override
    public void setUp() throws IOException {
        if (mTemplateNames == null) {
            // Load the list of templates
            mTemplateNames = new ArrayList<String>();

            final InputStream templateStream = getClass().getResourceAsStream(TEMPLATE_LIST_PATH);
            final BufferedReader templateReader =
                    new BufferedReader(new InputStreamReader(templateStream));

            String line = templateReader.readLine();
            for (; line != null; line = templateReader.readLine()) {
                if (line.startsWith("#")) {
                    // skip comments
                    continue;
                }
                mTemplateNames.add(line);
            }
        }

        mTemplateRenderer = new TemplateRenderer() {
            @Override
            protected void peekGeneratedXml(String xml) {
                try {
                    Util.writeToFile(xml, new File("/tmp/google_notifilter_output.xml"));
                } catch (IOException e) {
                    Util.debug("Caught IOException while trying to dump XML: %s", e.getMessage());
                }
            }
        };
    }

    private Notif buildNotif(Object suffix, String bucket) {
        return buildNotif(suffix, null, null, bucket);
    }

    private Notif buildNotif(Object suffix, Map<String, String> extras,
            Map<String, String> ngextras, String subBucket) {
        final String suffixString = suffix.toString();
        Notif notif = new Notif();
        notif.address = "email@address.com";
        notif.key = new StatusKey(STOCK_KEY_INPUT);
        notif.key.method = notif.key.method + suffixString;
        notif.key.extras = extras;
        notif.oldBuild = 12345;
        notif.newBuild = 22222;
        notif.update = new UpdateMask(true, false);
        notif.summaryUrl = "http://foo.com/bar?baz=quux" + suffixString;
        // FIXME: trim stack trace length in a manner that matches the real implementation
        notif.cause = String.format(
                "junit.framework.AssertionFailedError: Expected 0 entries; found 1 for (%s)\n" +
                "at com.path.to.package.ClassName%s.countAndVerify(ClassName%s.java:178)\n" +
                "at com.path.to.package.ClassName%s.testMethod(ClassName%s.java:82)\n" +
                "at java.lang.reflect.Method.invokeNative(Native Method)\n",
                suffixString, suffixString, suffixString, suffixString, suffixString);
        notif.ngExtras = ngextras;
        if (subBucket != null) {
            notif.subBucket = subBucket;
        } else {
            notif.subBucket = "default";
        }
        notif.subjectFormat = IDatastore.DEFAULT_SUBJECT_FORMAT;
        notif.dispatchPeriod = 30;  // 30 minutes is the default
        return notif;
    }

    /**
     * Attempts to load all Google-specific templates
     */
    public void testLoadAllGoogleTemplates() throws Exception {
        final TemplateRendererTest dt = new TemplateRendererTest();

        for (String template : mTemplateNames) {
            final String name;
            if (template.endsWith(".xsl")) {
                // drop the extension
                name = template.substring(0, template.length() - 4);
            } else {
                name = template;
            }

            final String bucket = String.format("google/%s", name);
            Util.debug("got template %s, bucket %s", template, bucket);

            // Shell out to TemplateRenderer Test to actually generate a sample email
            final Message msg = dt.generateSampleEmail_singleInvocation(mTemplateRenderer, bucket);
            writeTestArtifactToDisk(msg.getBody(), String.format("template-%s.html", name));
            Util.debug("CC list for message is %s", msg.getCc());
        }
    }

    /**
     * Writes a test artifact to disk if an output directory has been specified
     */
    private boolean writeTestArtifactToDisk(String contents, String filename) throws IOException {
        final File outDir = getTestOutputDir();
        if (outDir == null) return false;

        final File outFile = new File(outDir, filename);
        Util.writeToFile(contents, outFile);
        Util.debug("Wrote test artifact out to %s", outFile.getAbsolutePath());
        return true;
    }

    /**
     * Returns the user-specified output directory, if one has been specified
     */
    private File getTestOutputDir() {
        final String outputPath = System.getProperty(ARTIFACT_PATH_PROPERTY);

        if (outputPath == null) {
            Util.debug("Note: To save test artifacts to disk, specify the -D%s=(path) argument " +
                    "when running the unit tests.  (path) should denote a directory in which the " +
                    "test artifacts will be saved.", ARTIFACT_PATH_PROPERTY);
            return null;
        }

        final File dir = new File(outputPath);
        if (!dir.exists()) {
            Util.debug("Warning: Test output directory \"%s\" does not exist.  " +
                    "Not saving artifacts", outputPath);
            return null;
        } else if (!dir.isDirectory()) {
            Util.debug("Warning: Test output directory \"%s\" exists, but is not a " +
                    "directory.  Not saving artifacts.", outputPath);
        }
        return dir;
    }

    /**
     * A convenience method to quickly build a map with known contents
     */
    private Map<String, String> m(String... elements) {
        final Map<String, String> map = new HashMap<String, String>(elements.length / 2);

        if (elements.length % 2 == 1) {
            throw new IllegalArgumentException(String.format(
                    "Expected an even number of arguments; got %d", elements.length));
        }

        for (int i = 0; i < elements.length; i += 2) {
            map.put(elements[i], elements[i+1]);
        }

        return map;
    }
}
