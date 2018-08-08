// Copyright 2014 Google Inc.  All Rights Reserved.
package com.google.android.tf_workbuf;

import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.InvocationStatus;
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
import com.google.api.client.json.jackson2.JacksonFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.security.GeneralSecurityException;

/**
 * A class that facilitates communicating with the Build API
 */
public class BuildAPIHelper implements IBuildAPIHelper {
    // Account name
    // Corresponds to this account/project:
    // https://console.developers.google.com/project/apps~android-test-infra/apiui/credential
    private static final String SERVICE_ACCT =
            "717267004052-o19a1ouu8025j2iumfkniem897nu9gj8@developer.gserviceaccount.com";

    private static final String ACCESS_SCOPE =
            "https://www.googleapis.com/auth/androidbuild.internal";

    // Base API uri: "pending" -> presubmit
    private static final String BASE_URI =
            "https://www.googleapis.com/android/internal/build/v1/builds/pending/";

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String JSON_MIME = "application/json";

    static final String STATUS_PASS = "completePass";
    static final String STATUS_FAIL = "completeFail";
    static final String STATUS_ERROR = "error";

    static final String STOCK_ERROR_MSG =
            "\n\nError during test run.  Contact android-test-service@.";

    // Account key
    @Option(name = "key-file-path", description = "path to account key location",
            mandatory = true)
    private File mKeyPath = null;

    private HttpTransport mTransport;
    private GoogleCredential mCredential;
    private HttpRequestFactory mRequestFactory = null;

    /** {@inheritDoc} */
    @Override
    public void setupTransport() throws GeneralSecurityException, IOException {
        mTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Fun auth stuff
        mCredential = new GoogleCredential.Builder()
                .setTransport(mTransport)
                .setJsonFactory(JSON_FACTORY)
                .setServiceAccountId(SERVICE_ACCT)
                .setServiceAccountScopes(Collections.singleton(ACCESS_SCOPE))
                .setServiceAccountPrivateKeyFromP12File(mKeyPath)
                .build();
    }

    /**
     * Returns a summary of the test results, suitable for passing along to the Build API.  The
     * first element in the returned array will be a Build-API-suitable status string.  The second
     * element will be the description.
     */
    String[] summarizeResults(boolean testResult, String testDesc, InvocationStatus status) {
        final String[] returnAry = new String[2];
        final StringBuilder summary = new StringBuilder(testDesc);
        switch (status) {
            case SUCCESS:
                if (testResult) {
                    returnAry[0] = STATUS_PASS;
                } else {
                    returnAry[0] = STATUS_FAIL;
                }
                break;

            case FAILED:
            case BUILD_ERROR:
                // Note that these are Invocation results.  If the Invocation failed, it means that
                // the test run did not complete properly.  If there's a build error, it likely
                // means that we don't have any test results at all.
                summary.append(STOCK_ERROR_MSG);
                if (status.getThrowable() != null) {
                    summary.append("\n");
                    summary.append(status.getThrowable().toString());
                }
                returnAry[0] = STATUS_ERROR;
                break;
        }

        returnAry[1] = summary.toString();

        // fallback
        if (returnAry[1].trim().isEmpty()) {
            returnAry[1] = "[Test status update]";
        }

        return returnAry;
    }

    /**
     * Returns the HttpRequestFactory, setting it up as a side-effect if it hasn't been set up
     * yet.  Method is thread-safe.
     */
    HttpRequestFactory getRequestFactory() {
        if (mRequestFactory != null) return mRequestFactory;

        synchronized(this) {
            // check again, in case another thread beat us here
            if (mRequestFactory != null) return mRequestFactory;
            mRequestFactory = mTransport.createRequestFactory(mCredential);
        }

        return mRequestFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fetchBuildArtifact(OutputStream destStream, String buildId, String target, String attemptId,
            String name) throws IOException, JSONException {
        final String[] uriParts = {buildId, target, "attempts", attemptId, "artifacts", name};

        // alt==json will return the artifact metadata
        // alt==media will return the artifact contents
        final GenericUrl queryUri = buildQueryUri(uriParts, map("alt", "media"));
        final HttpResponse response = executeGet(queryUri);

        response.download(destStream);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String postTestResults(String buildId, String target, String attemptId,
            boolean testResult, String testResultDetail, InvocationStatus status)
            throws IOException, JSONException {
        final String[] uriParts = {buildId, target, "attempts", attemptId, "tests"};

        final JSONObject postContent = new JSONObject();
        final String[] results = summarizeResults(testResult, testResultDetail, status);
        postContent.put("status", results[0]);
        postContent.put("summary", results[1]);
        final HttpContent httpContent =
                new ByteArrayContent(JSON_MIME, postContent.toString().getBytes());

        final GenericUrl queryUri = buildQueryUri(uriParts, null);
        final HttpResponse response = executePost(queryUri, httpContent);

        return response.parseAsString();
    }

    /**
     * Construct a URI to use to communicate with the build API
     */
    GenericUrl buildQueryUri(String[] uriParts, Map<String, String> opts) {
        final GenericUrl uri = new GenericUrl(BASE_URI);
        for (int i = 0; i < uriParts.length; ++i) {
            uri.appendRawPath(uriParts[i]);
            uri.appendRawPath("/");
        }
        if (opts != null) {
            // FIXME: make sure that key and value encoding is being done
            uri.putAll(opts);
        }

        return uri;
    }

    /**
     * Execute a given GET query and return the response
     * <p />
     * Exposed for mocking/unit testing
     */
    HttpResponse executeGet(GenericUrl requestUri) throws IOException {
        CLog.v("Running get query against %s", requestUri);
        final HttpRequest request = getRequestFactory().buildGetRequest(requestUri);

        return request.execute();
    }

    /**
     * Execute a given POST query and return the response
     * <p />
     * Exposed for mocking/unit testing
     */
    HttpResponse executePost(GenericUrl requestUri, HttpContent content) throws IOException {
        CLog.v("Running post query against %s", requestUri);
        CLog.v("Query has content: %s", content);
        final HttpRequest request = getRequestFactory().buildPostRequest(requestUri, content);

        return request.execute();
    }

    /**
     * Convenience function to build a Map
     */
    private Map<String, String> map(String... pieces) {
        if ((pieces.length & 0x1) == 0x1) {
            throw new IllegalArgumentException("Need an even number of pieces!");
        }

        final Map<String, String> map = new HashMap<String, String>(pieces.length / 2);
        for (int i = 0; i + 1 < pieces.length; i += 2) {
            final String key = pieces[i];
            final String value = pieces[i + 1];
            map.put(key, value);
        }

        return map;
    }
}
