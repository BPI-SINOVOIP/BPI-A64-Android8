// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Map;

/**
 * A helper class for performing REST API calls.
 */
public class RestApiHelper implements IRestApiHelper {

    protected static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    protected static final String JSON_MIME = "application/json";

    private HttpRequestFactory mRequestFactory;
    private String mBaseUri;

    /**
     * Creates an API helper instance with the given information.
     *
     * @param baseUri the base URI of API
     * @param requestFactory the factory to use when creating {@link HttpRequest}s.
     */
    public RestApiHelper(HttpRequestFactory requestFactory, String baseUri) {
        mRequestFactory = requestFactory;
        // Make sure the uri ends with a slash to avoid GenericUrl weirdness later
        mBaseUri = baseUri.endsWith("/") ? baseUri : baseUri + "/";
    }

    /**
     * Creates an API helper instance which uses a {@link GoogleCredential} for authentication.
     *
     * @param baseUri the base URI of the API
     * @param serviceAccount the name of the service account to use
     * @param keyFile the service account key file
     * @param scopes the collection of OAuth scopes to use with the service account
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public static RestApiHelper newInstanceWithGoogleCredential(String baseUri, String serviceAccount,
            File keyFile, Collection<String> scopes) throws GeneralSecurityException, IOException {

        HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(transport)
                .setJsonFactory(JSON_FACTORY)
                .setServiceAccountId(serviceAccount)
                .setServiceAccountScopes(scopes)
                .setServiceAccountPrivateKeyFromP12File(keyFile)
                .build();
        HttpRequestFactory requestFactory = transport.createRequestFactory(credential);

        return new RestApiHelper(requestFactory, baseUri);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpResponse execute(String method, String[] uriParts, Map<String, Object> options,
            JSONObject data) throws IOException {
        HttpContent content = null;
        if (data != null) {
            content = new ByteArrayContent(JSON_MIME, data.toString().getBytes());
        }
        final GenericUrl uri = buildQueryUri(uriParts, options);
        final HttpRequest request = getRequestFactory().buildRequest(method, uri, content);
        request.setParser(new JsonObjectParser(JSON_FACTORY));
        return request.execute();
    }

    /**
     * Returns the HttpRequestFactory.
     */
    HttpRequestFactory getRequestFactory() {
        return mRequestFactory;
    }

    /**
     * Construct a URI for a API call with given URI parts and options. uriParts should be
     * URL-encoded already, while options should be unencoded Strings.
     */
    GenericUrl buildQueryUri(String[] uriParts, Map<String, Object> options) {
        final GenericUrl uri = new GenericUrl(mBaseUri);
        for (int i = 0; i < uriParts.length; ++i) {
            uri.appendRawPath(uriParts[i]);
            // Don't add a trailing slash
            if (i + 1 < uriParts.length) {
                uri.appendRawPath("/");
            }
        }

        if (options != null) {
            uri.putAll(options);
        }

        return uri;
    }
}
