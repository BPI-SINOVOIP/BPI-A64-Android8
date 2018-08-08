// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;

/**
 * Functional tests for {@link SsoClientTransport}.
 */
public class SsoClientTransportFuncTest extends TestCase {

    private static final String TFC_URL = "https://tradefed-cluster.googleplex.com/";
    private static final String APPENGINE_APP_ID_KEY = "x-google-appengine-appid";
    private static final String TFC_APP_ID = "[s~google.com:tradefed-cluster]";

    private SsoClientTransport mTransport = null;

    @Override
    public void setUp() {
        mTransport = new SsoClientTransport();
    }

    /**
     * Test that we can make a GET request from an internal site. Ensure that the site is really
     * reached and not uberproxy instead.
     */
    public void testSimpleGetRequest() throws IOException {
        HttpResponse response = mTransport.createRequestFactory()
                .buildRequest("GET", new GenericUrl(TFC_URL), null)
                .execute();
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getStatusMessage());
        // Ensure we reached blackbox and not uberproxy
        assertNotNull(response.getHeaders().getUnknownKeys().get(APPENGINE_APP_ID_KEY));
        assertEquals(TFC_APP_ID,
                response.getHeaders().getUnknownKeys().get(APPENGINE_APP_ID_KEY).toString());
        InputStream content = response.getContent();
        assertNotNull(content);
    }
}
