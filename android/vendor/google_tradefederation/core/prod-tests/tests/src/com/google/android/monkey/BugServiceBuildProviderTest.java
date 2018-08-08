// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.monkey;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.command.FatalHostError;
import com.google.android.tradefed.build.RemoteBuildInfo;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

public class BugServiceBuildProviderTest extends TestCase {
    private BugServiceBuildProvider mProvider = null;
    private String mResponse = null;
    private static final String RESPONSE_TEMPLATE =
            "<?xml version='1.0'?>\r\n" +
            "<methodResponse>\r\n" +
            "<params>\r\n" +
            "<param>\r\n" +
            "<value><boolean>%d</boolean></value>\r\n" +
            "</param>\r\n" +
            "</params>\r\n" +
            "</methodResponse>\r\n" +
            "\r\n";
    private static final String TRUE_RESPONSE = String.format(RESPONSE_TEMPLATE, 1);
    private static final String FALSE_RESPONSE = String.format(RESPONSE_TEMPLATE, 0);

    private static final String BUILD_ID = "1234";
    private static final String BUILD_FLAVOR = "soju-userdebug";
    private static final String PROD_TYPE = "crespo";
    private static final String LC_BUILD_INFO = "bid:1234";

    private RemoteBuildInfo mRemoteBuild = null;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mRemoteBuild = RemoteBuildInfo.parseRemoteBuildInfo(LC_BUILD_INFO);

        mResponse = "";
        mProvider = new BugServiceBuildProvider() {
            @Override
            public String getBuildFlavor() {
                return BUILD_FLAVOR;
            }

            @Override
            public RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
                return mRemoteBuild;
            }

            @Override
            OutputStream getOutputStream(HttpURLConnection httpConnection) throws IOException {
                // FIXME do something with this
                return new ByteArrayOutputStream();
            }

            @Override
            InputStream getInputStream(HttpURLConnection httpConnection) throws IOException {
                return new ByteArrayInputStream(mResponse.getBytes());
            }

            @Override
            HttpURLConnection getBugServiceConnection() throws IOException {
                return null;
            }
        };
    }

    public void testFlavor() {
        assertEquals("soju", mProvider.getFlavorName());
    }

    /**
     * Check that the BSBP returns {@code true} (do need more monkey runs) when it receives a
     * {@code true} response (yes, need more runs) from BugService
     */
    public void testMonkeyCheck_trueResponse() throws Exception {
        mResponse = TRUE_RESPONSE;
        assertTrue(mProvider.needMoreMonkeyRuns(BUILD_ID, BUILD_FLAVOR));
    }

     /**
     * Check that the BSBP returns {@code false} (do not need more monkey runs) when it receives a
     * {@code false} response (no, don't need more runs) from BugService
     */
   public void testMonkeyCheck_falseResponse() throws Exception {
        mResponse = FALSE_RESPONSE;
        assertFalse(mProvider.needMoreMonkeyRuns(BUILD_ID, BUILD_FLAVOR));
    }

    /**
     * Check that the BSBP returns {@code true} (do need more monkey runs) when it receives a
     * {@code true} response (yes, need more runs) from BugService
     */
    public void testMonkeyCheck_killTF() throws Exception {
        mResponse = FALSE_RESPONSE;
        mProvider.setProdType(PROD_TYPE);
        mProvider.setThrowFatalErrorWhenDone(true);
        try {
            mProvider.getBuild();
            fail("FatalHostError not thrown");
        } catch (FatalHostError e) {
            // expected
        }
    }
}
