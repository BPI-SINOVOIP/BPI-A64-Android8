// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;

/**
 * Function test for {@link ArtifactFetcher}
 */
public class ArtifactFetcherFuncTest extends TestCase {
    private static final String TARGET = "sdk_tools_linux";
    private static final String BUILD_ID = "1840977";
    private static final String RESOURCE_ID = "sdk-repo-linux-tools-1840977.zip";
    private static final String BUILD_TYPE = "submitted";
    private static final String ATTEMPTS_ID = "latest";
    private static final String KEY_FILE_PATH =
            "/google/data/ro/teams/tradefed/configs/"
            + "e006c97067dbad4ecff47a965821946d2d04be57-privatekey.p12";
    private static final String SERVICE_ACCT =
            "717267004052-o19a1ouu8025j2iumfkniem897nu9gj8@developer.gserviceaccount.com";

    public void testFetchArtifact() throws Exception {
        String md5 = "ef692c35be9f4abd530786334281de25";
        File tmpDir = FileUtil.createTempDir("ArtifactFetcherFuncTest");
        File file = new File(tmpDir, RESOURCE_ID);
        File keyFile = new File(KEY_FILE_PATH);
        try {
            // Create a new ArtifactFetcher.
            ArtifactFetcher artifactFetcher = new ArtifactFetcher(keyFile, SERVICE_ACCT);
            artifactFetcher.fetchArtifact(TARGET, BUILD_ID, RESOURCE_ID, BUILD_TYPE, ATTEMPTS_ID,
                    file);
            assertTrue(file.exists());
            // Verify the md5 is correct.
            String actualMd5 = null;
            actualMd5 = FileUtil.calculateMd5(file);
            assertEquals(md5, actualMd5);
        } finally {
            FileUtil.recursiveDelete(tmpDir);
        }
    }
}
