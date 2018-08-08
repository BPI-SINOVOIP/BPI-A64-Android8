// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.util.FileUtil;
import com.google.api.client.http.GenericUrl;

import junit.framework.TestCase;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Functional tests for {@link AndroidBuildFetcher}
 */
public class AndroidBuildFetcherFuncTest extends TestCase {
    private static final String KEY_FILE_PATH =
            "/google/data/ro/teams/tradefed/configs/"
            + "e006c97067dbad4ecff47a965821946d2d04be57-privatekey.p12";
    private static final String SERVICE_ACCT =
            "717267004052-o19a1ouu8025j2iumfkniem897nu9gj8@developer.gserviceaccount.com";
    private static final File KEY_FILE = new File(KEY_FILE_PATH);

    /**
     * Test {@link AndroidBuildFetcher}
     */
    public void testCreateUrl() {
        String baseUrl = "https://www.googleapis.com/test/";
        String[] urlParts = {"part1", "part2"};
        Map<String, String> urlParams = new HashMap<>();
        urlParams.put("param1", "value1");
        GenericUrl url = AndroidBuildFetcher.createUrl(baseUrl, urlParts, urlParams);
        assertEquals(url.toString(), "https://www.googleapis.com/test/part1/part2?param1=value1");
    }

    /**
     * Test {@link AndroidBuildFetcher#fetchMetaData(GenericUrl)}.
     * @throws Exception
     */
    public void testFetchMetaData() throws Exception {
        final String BASE_URI = "https://www.googleapis.com/android/internal/build/v1/deviceBlobs/";
        String[] urlParts = {"dory", "bootloader", "DORYZ07f"};
        GenericUrl url = AndroidBuildFetcher.createUrl(BASE_URI, urlParts, null);
        // Create a new AndroidBuildFetcher.
        AndroidBuildFetcher buildUrlFetcher = new AndroidBuildFetcher(KEY_FILE, SERVICE_ACCT);
        // fetch meta data
        String metaData = buildUrlFetcher.fetchMetaData(url);

        // Verify the meta data format is json and the json is right.
        JSONObject metaJson = new JSONObject(metaData);
        long totalSize = metaJson.getLong("size");
        String metaMd5 = metaJson.getString("md5");
        String name = metaJson.getString("name");
        assertTrue(totalSize > 0);
        assertEquals("0b5a94367184747dbb5baad4fde95fcd", metaMd5);
        assertEquals("DORYZ07f", name);
    }

    /**
     * Test a non-exist url.
     * @throws Exception
     */
    public void testFetchMetaData_Fail() throws Exception {
        String BASE_URI = "https://www.googleapis.com/android/internal/build/v1/deviceBlobs/";
        String[] urlParts = {"dory", "bootloader", "notexist"};
        GenericUrl url = AndroidBuildFetcher.createUrl(BASE_URI, urlParts, null);
        try {
            AndroidBuildFetcher buildUrlFetcher = new AndroidBuildFetcher(KEY_FILE, SERVICE_ACCT);
            String metaData = buildUrlFetcher.fetchMetaData(url);
            assertNull(metaData);
            fail("Expected IOException to be thrown.");
        } catch (IOException e) {
            // expected
        }
    }

    /**
     * Test {@link AndroidBuildFetcher#fetchMedia(GenericUrl, File)};
     * @throws Exception
     */
    public void testFetchMedia() throws Exception {
        String BASE_URI = "https://www.googleapis.com/android/internal/build/v1/deviceBlobs/";
        String[] urlParts = {"dory", "bootloader", "DORYZ07f"};
        GenericUrl url = AndroidBuildFetcher.createUrl(BASE_URI, urlParts, null);
        File tempFile = FileUtil.createTempFile("bootloader", ".img");
        // Create a new AndroidBuildFetcher.
        AndroidBuildFetcher buildUrlFetcher = new AndroidBuildFetcher(KEY_FILE, SERVICE_ACCT);

        // Download the media file with verifying md5.
        try {
            buildUrlFetcher.fetchMedia(url, tempFile);
        } finally {
            FileUtil.deleteFile(tempFile);
        }
    }

    /**
     * Test {@link AndroidBuildFetcher#copyStream(java.io.InputStream, OutputStream)}.
     * @throws IOException
     */
    public void testCopyStream() throws IOException {
        final int LEN = 30000;
        byte[] bytes = new byte[LEN];
        for(int i = 0; i < LEN; ++i) {
            bytes[i] = (byte)(i % 256);
        }
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long len = AndroidBuildFetcher.copyStream(input, output);
        assertEquals(LEN, len);

    }
}
