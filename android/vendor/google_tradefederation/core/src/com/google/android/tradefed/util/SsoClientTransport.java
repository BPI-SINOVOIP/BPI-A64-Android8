// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;

import java.io.IOException;

/**
 * A {@link HttpTransport} implementation which transmits request by invoking the sso_client command
 * line utility.
 *
 * The Google Client HTTP Library is designed to let you swap out the transport layer by providing
 * your own HttpTransport implementation. Doing this requires concrete implementations for
 * {@link LowLevelHttpRequest} and {@link LowLevelHttpResponse} which we provide in
 * {@link SsoClientHttpResponse} and {@link SsoClientHttpResponse}. We can't override
 * {@link HttpResponse} or {@link HttpRequest} directly as they are final classes.
 */
public class SsoClientTransport extends HttpTransport {

    private String mSsoClientPath = "sso_client";

    public SsoClientTransport() {
    }

    public SsoClientTransport(String ssoClientPath) {
        mSsoClientPath = ssoClientPath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        return new SsoClientHttpRequest(method, url, mSsoClientPath);
    }
}
