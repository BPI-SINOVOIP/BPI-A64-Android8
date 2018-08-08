// Copyright 2015 Google Inc.  All Rights Reserved.

package com.google.android.tradefed.util.androidbuildapi;

import com.google.android.tradefed.util.IRestApiHelper;
import com.google.android.tradefed.util.RestApiHelper;
import com.google.api.client.http.HttpResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;

/**
 * Helper method used to talk to the Android Build API.
 */
// FIXME: re-factor AndroidBuildFetcher and BuildAPIHelper to use this client
// instead.
public class AndroidBuildAPIHelper {

    private static final Collection<String> ACCESS_SCOPE =
            Collections.singleton("https://www.googleapis.com/auth/androidbuild.internal");
    private static final String BASE_URI = "https://www.googleapis.com/android/internal/build/v1/";

    private IRestApiHelper mApiHelper;

    /**
     * Prepares the instance. This should be called before any other calls.
     *
     * @param serviceAccount service account to access Android Build API.
     * @param keyFile account key
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public void setup(String serviceAccount, File keyFile)
            throws GeneralSecurityException, IOException {

        // Initialize the helper class we'll use for making requests
        mApiHelper = RestApiHelper.newInstanceWithGoogleCredential(BASE_URI, serviceAccount,
                keyFile, ACCESS_SCOPE);
    }

    /**
     * Post a given test result to Android Build API
     *
     * @param data {@link AndroidBuildTestResult} which contains the test
     *            result.
     * @return {@link JSONObject} of the test result posted.
     * @throws AndroidBuildAPIException
     */
    public JSONObject postTestResult(AndroidBuildTestResult data) throws AndroidBuildAPIException {
        final String[] uriParts = {
                "builds", data.buildType(), data.buildId(), data.target(), "attempts",
                data.attemptId(), "tests"
        };
        HttpResponse res = null;
        try {
            if (data.id() == null) {
                res = getApiHelper().execute("POST", uriParts, null, data.toJson());
            } else {
                res = poorManPatch(uriParts, null, data.toJson());
            }
            if (!res.isSuccessStatusCode()) {
                throw new AndroidBuildAPIException(String.format("Failed to post to build API: %s",
                        res.parseAsString()));
            }
            final JSONObject testResult = parseJSON(res.getContent());
            if (testResult == null) {
                throw new AndroidBuildAPIException(String.format(
                        "Did not get test result data in POST response: %s", res.parseAsString()));
            }
            if (data.id() == null) {
                data.setId(testResult.getLong("id"));
            }
            return testResult;
        } catch (JSONException e) {
            throw new AndroidBuildAPIException("Failed to parse JSON in POST", e);
        } catch (IOException e) {
            throw new AndroidBuildAPIException("IO exception in POST", e);
        }
    }

    /**
     * Patch a given test result.
     *
     * @param uriParts to form the URL for the Android Build API
     * @param options any additional options needed
     * @param payload the {@link JSONObject} to post
     * @return a {@link HttpResponse} from posting the data.
     * @throws IOException
     * @throws JSONException
     * @throws AndroidBuildAPIException
     */
    public HttpResponse poorManPatch(String[] uriParts, Map<String, Object> options,
            JSONObject payload) throws IOException, JSONException, AndroidBuildAPIException {

        // Append the id to the request.
        ArrayList<String> u = new ArrayList<String>(Arrays.asList(uriParts));

        long id = payload.optLong("id");
        u.add(String.valueOf(id));

        IRestApiHelper apiHelper = getApiHelper();
        HttpResponse res = apiHelper.execute(
                "GET", u.toArray(new String[u.size()]), options, null);
        if (!res.isSuccessStatusCode()) {
            return res;
        }
        final JSONObject testResult = parseJSON(res.getContent());
        if (testResult == null) {
            throw new AndroidBuildAPIException("Did not get test result data in response.");
        }
        String revision = testResult.getString("revision");
        payload.putOpt("revision", revision);
        return apiHelper.execute("POST", uriParts, null, payload);
    }

    private static JSONObject parseJSON(InputStream input) throws JSONException {
        try (final Scanner scanner = new Scanner(input, "UTF-8")) {
            final String content = scanner.useDelimiter("\\A").next();
            return new JSONObject(content);
        }
    }

    IRestApiHelper getApiHelper() {
        if (mApiHelper == null) {
            throw new IllegalStateException("setup(..) must be called first");
        }
        return mApiHelper;
    }
}
