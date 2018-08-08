// Copyright 2015 Google Inc.  All Rights Reserved.

package com.google.android.tradefed.build;

import com.google.api.client.http.GenericUrl;
import com.google.common.base.Strings;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Fetch build artifact from android build server.
 */
public class ArtifactFetcher extends AndroidBuildFetcher {
    private static final String BASE_URI =
            "https://www.googleapis.com/android/internal/build/v1/builds/";

    /**
     * Constructor
     * @param keyFile key file to access android build api.
     * @param serviceAccount service account to access android build api.
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public ArtifactFetcher(File keyFile, String serviceAccount)
            throws IOException, GeneralSecurityException {
        super(keyFile, serviceAccount);
    }

    /**
     * Download artifact to local file.
     * @param target artifact target. If it's null, the download will fail.
     * @param buildId artifact buildId. If it's null, the download will fail.
     * @param resourceId the filename to download. If it's null, the download will fail.
     * @param buildType submitted or pending, default is submitted.
     * @param attemptsId latest, current or attempts id, default is latest.
     * @param outputFile download the file to this local file.
     * @throws IOException
     * @throws JSONException
     */
    public void fetchArtifact(String target, String buildId, String resourceId, String buildType,
            String attemptsId, File outputFile) throws IOException, JSONException {
        if (Strings.isNullOrEmpty(buildType)) {
            buildType = "submitted";
        }

        if (Strings.isNullOrEmpty(attemptsId)) {
            attemptsId = "latest";
        }
        // BASE_URL/{buildType}/{buildId}/{target}/attempts/{attemptsId}/artifacts/{resourceId}
        String[] urlParts = new String[] {
                buildType, buildId, target, "attempts", attemptsId, "artifacts", resourceId };
        GenericUrl url = AndroidBuildFetcher.createUrl(BASE_URI, urlParts, null);
        fetchMedia(url, outputFile);
    }
}
