// Copyright 2014 Google Inc.  All Rights Reserved.
package com.google.android.tf_workbuf;

import com.android.tradefed.result.InvocationStatus;

import org.json.JSONException;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

/**
 * An interface to make it easier to test classes that talk to the Build API
 */
public interface IBuildAPIHelper {

    /**
     * Downloads an Artifact from the build server into {@code destStream}
     */
    public void fetchBuildArtifact(OutputStream destStream, String buildId, String target,
            String attemptId, String name) throws IOException, JSONException;

    /**
     * Runs a POST query on a build server URI, and returns any response as a {@link String}
     *
     * @param buildId Build ID
     * @param target Target
     * @param attemptId Attempt ID
     * @param testResult {@code false} if the test run should be consider a failure (for instance,
     *                   if any tests failed or had errors.  {@code true} if it should be considered
     *                   successful.
     * @param testResultDetail A human-readable explanation of the results.  Will be displayed in a
     *                 web UI, so may contain hyperlinks.
     * @param status Whether the Invocation as a whole passed, failed, or experienced an error.
     *                 {@see InvocationStatus}.
     */
    public String postTestResults(String buildId, String target, String attemptId,
            boolean testResult, String testResultDetail, InvocationStatus status)
            throws IOException, JSONException;

    /**
     * Actually setup the transport and authentication
     * <p />
     * Exposed for unit testing
     */
    public void setupTransport() throws GeneralSecurityException, IOException;
}
