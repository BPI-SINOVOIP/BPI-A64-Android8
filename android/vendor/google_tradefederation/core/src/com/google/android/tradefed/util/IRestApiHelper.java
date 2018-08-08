// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.google.api.client.http.HttpResponse;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;

/**
 * A helper interface for performing REST API calls.
 */
public interface IRestApiHelper {

    /**
     * Executes an API request.
     *
     * @param method a HTTP method of the request
     * @param uriParts URL encoded URI parts to be used to construct the request URI.
     * @param options unencoded parameter names and values used to construct the query string
     * @param data data to be sent with the request
     * @return a HttpResponse object
     * @throws IOException
     */
    public HttpResponse execute(String method, String[] uriParts, Map<String, Object> options,
            JSONObject data) throws IOException;

}
