// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;

import junit.framework.TestCase;

import java.util.Collections;

/**
 * Unit tests for {@link RestApiHelper}.
 */
public class RestApiHelperTest extends TestCase {
    private static final String BASE_URI = "https://www.googleapis.com/test/";

    private RestApiHelper mHelper = null;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
        mHelper = new RestApiHelper(requestFactory, BASE_URI);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testBuildQueryUri() {
        String[] uriParts = {"add", "new", "fox"};
        GenericUrl uri = new GenericUrl(BASE_URI + "add/new/fox");

        assertEquals(uri, mHelper.buildQueryUri(uriParts, Collections.<String, Object>emptyMap()));
    }

}
