// Copyright 2017 Google Inc.  All Rights Reserved.
package com.google.android.tradefed.util;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.net.HttpHelper;
import com.google.api.client.http.LowLevelHttpRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utility class to get roll-up Change list number from Rapid.
 *
 * <p>During TradeFed test, release apks may be sideloaded on device. Engineers may be interested in
 * the roll-up CL for these release candidates. This utility class will leverage Rapid API via HTTP
 * GET to get the roll-up CL for a certain release candidate.
 *
 * <p>Release name and candidate name can usually be found by parsing the apk path
 */
public final class RollupClFetcher {

    /**
     * This exception will be thrown when error happens whiling getting roll-up cl number from Rapid
     * API. This includes JSON parsing error or InputStream IO error. For specific information,
     * please refer to the "caused by" exception.
     */
    public static class RollupClException extends Exception {
        public RollupClException(String msg) {
            super(msg);
        }

        public RollupClException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    // Static immutable fields
    private static final String RAPID_ACTION_BASE_URL = "https://rapid.corp.google.com/action/";
    private static final String GET_CANDIDATE_API_URL = RAPID_ACTION_BASE_URL + "GetCandidate";
    private static final String GET_EFFECTIVE_ROLLUP_PROPOSAL_API_URL =
            RAPID_ACTION_BASE_URL + "GetEffectiveRollupProposal";
    private static final String PROJECT_NAME_KEY = "projectName";
    private static final String RELEASE_NAME_KEY = "releaseName";
    private static final String CANDIDATE_NAME_KEY = "candidateName";
    private static final String RELEASE_ID_KEY = "releaseId";
    private static final String CANDIDATE_ID_KEY = "candidateId";
    private static final String CHANGE_LIST_KEY = "changelist";
    private static final String CANDIDATE_KEY = "key";

    // For Cross-site script inclusion Defense, the response from Rapid API start with the following
    // sequence. For validation and parsing purpose, the sequence needs to be stripped.
    private static final String JSON_START_SEQUENCE = ")]}',\n";

    // Private fields
    private SsoClientTransport transport;

    public RollupClFetcher() {
        this(new SsoClientTransport());
    }

    @VisibleForTesting
    public RollupClFetcher(SsoClientTransport transport) {
        this.transport = checkNotNull(transport);
    }

    /**
     * Fetch rollup CL number from Rapid based on project name, release name and candidate name
     *
     * @param projectName String, project name
     * @param releaseName String, release name
     * @param candidateName String, candidate name
     * @return String of rollup CL number
     */
    public String fetch(String projectName, String releaseName, String candidateName)
            throws RollupClException {
        // Get release id and candidate id based on project name and release name.
        String releaseId = null;
        String candidateId = null;
        try {
            LowLevelHttpRequest candidateRequest =
                    transport.buildRequest(
                            "GET", buildGetCandidateUrl(projectName, releaseName, candidateName));
            SsoClientHttpResponse candidateResponse =
                    (SsoClientHttpResponse) candidateRequest.execute();
            try {
                JSONObject candidateObj = parseJsonResponse(candidateResponse);
                JSONObject keyObj = candidateObj.getJSONObject(CANDIDATE_KEY);
                releaseId = keyObj.getString(RELEASE_ID_KEY);
                candidateId = keyObj.getString(CANDIDATE_ID_KEY);
            } catch (JSONException je) {
                CLog.e(je);
                throw new RollupClException(
                        "JSONException happened when parsing GetCandidate API response.", je);
            }
            // Get roll-up cl
            LowLevelHttpRequest rollupRequest =
                    transport.buildRequest(
                            "GET", buildGetEffectiveRollupProposalUrl(releaseId, candidateId));
            SsoClientHttpResponse rollupResponse = (SsoClientHttpResponse) rollupRequest.execute();
            try {
                JSONObject rollupObj = parseJsonResponse(rollupResponse);
                return rollupObj.getString(CHANGE_LIST_KEY);
            } catch (JSONException je) {
                CLog.e(je);
                throw new RollupClException(
                        "JSONException happened when parsing GetEffectiveRollupProposal "
                                + "API response.",
                        je);
            }

        } catch (IOException ioe) {
            CLog.e(ioe);
            throw new RollupClException("IOException happens. Please check log.", ioe);
        }
    }

    private static String buildGetCandidateUrl(
            String projectName, String releaseName, String candidateName) {
        HttpHelper helper = new HttpHelper();
        MultiMap<String, String> map = new MultiMap<>();
        map.put(PROJECT_NAME_KEY, projectName);
        map.put(RELEASE_NAME_KEY, releaseName);
        map.put(CANDIDATE_NAME_KEY, candidateName);
        return helper.buildUrl(GET_CANDIDATE_API_URL, map);
    }

    private static String buildGetEffectiveRollupProposalUrl(String releaseId, String candidateId) {
        HttpHelper helper = new HttpHelper();
        MultiMap<String, String> map = new MultiMap<>();
        map.put(RELEASE_ID_KEY, releaseId);
        map.put(CANDIDATE_ID_KEY, candidateId);
        return helper.buildUrl(GET_EFFECTIVE_ROLLUP_PROPOSAL_API_URL, map);
    }

    private static JSONObject parseJsonResponse(SsoClientHttpResponse response)
            throws IOException, JSONException {
        String stringResponse = StreamUtil.getStringFromStream(response.getContent());
        // The first line of the response contains the start sequence to avoid
        // the JSON object to be interpreted directly as a script.
        if (!stringResponse.startsWith(JSON_START_SEQUENCE)) {
            CLog.e("String response is %s", stringResponse);
            throw new IOException(String.format("Invalid response: %s", stringResponse));
        }
        String jsonStringResponse = stringResponse.substring(JSON_START_SEQUENCE.length());
        CLog.d("API Response String: %s", jsonStringResponse);
        return new JSONObject(jsonStringResponse);
    }
}
