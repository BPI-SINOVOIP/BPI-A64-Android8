// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;

/**
 * Function test for {@link DeviceBlobFetcher}
 */
public class DeviceBlobFetcherFuncTest extends TestCase {
    private static final String BINARY_TYPE = "radio";
    private static final String VESION = "D4.01-9625-05.14.01+FSG-9625-02.93";
    private static final String EXTENSION = ".img";
    private static final String DEVICE_NAME = "shamu";
    private static final String MD5 = "8993f101f5e07a55dbef01afbeae0997";
    private static final String KEY_FILE_PATH =
            "/google/data/ro/teams/tradefed/configs/"
            + "e006c97067dbad4ecff47a965821946d2d04be57-privatekey.p12";
    private static final String SERVICE_ACCT =
            "717267004052-o19a1ouu8025j2iumfkniem897nu9gj8@developer.gserviceaccount.com";

    /**
     * Test fetch device blob successfully with customed key file and service account.
     * @throws Exception
     */
    public void testFetchDeviceBlobWithKeyFilePathAndServiceAccount() throws Exception {
        File keyFile = new File(KEY_FILE_PATH);
        File tmpDir = FileUtil.createTempDir("DeviceBlobFetcherFuncTest");
        String filename = DeviceBlobFetcher.createBlobName(BINARY_TYPE, VESION, EXTENSION);
        File file = new File(tmpDir, filename);
        try {
            // Create a DeviceBlobFetcher.
            DeviceBlobFetcher deviceBlobFetcher = new DeviceBlobFetcher(keyFile, SERVICE_ACCT);
            // Download the device blob file to local.
            deviceBlobFetcher.fetchDeviceBlob(DEVICE_NAME, BINARY_TYPE, VESION, file);
            assertTrue(file.exists());

            // Check the downloaded file is complete by md5.
            String actualMd5 = null;
            actualMd5 = FileUtil.calculateMd5(file);
            assertEquals(MD5, actualMd5);
        } finally {
            FileUtil.recursiveDelete(tmpDir);
        }
    }
}
