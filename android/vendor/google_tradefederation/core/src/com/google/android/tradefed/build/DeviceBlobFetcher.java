// Copyright 2015 Google Inc.  All Rights Reserved.

package com.google.android.tradefed.build;

import com.google.api.client.http.GenericUrl;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Fetch bootloader.img or radio.img for device
 */
public class DeviceBlobFetcher extends AndroidBuildFetcher {
    private static final String BASE_URI =
            "https://www.googleapis.com/android/internal/build/v1/deviceBlobs/";

    /**
     * Constructor
     * @param keyFile key file to access android build api.
     * @param serviceAccount service account to access android build api.
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public DeviceBlobFetcher(File keyFile, String serviceAccount)
            throws IOException, GeneralSecurityException {
        super(keyFile, serviceAccount);
    }

    /**
     * Download device blob to local file.
     * @param deviceName If this is null, the download will fail.
     * @param binaryType radio.img or bootloader.img. If this is null, the download will fail.
     * @param version device version. If this is null, the download will fail.
     * @param outputFile local file. Download the blob to this local file.
     * @throws IOException
     * @throws JSONException
     */
    public void fetchDeviceBlob(String deviceName, String binaryType, String version,
            File outputFile) throws IOException, JSONException {
        // BASE_URI/{deviceName}/{binaryType}/{version}
        String[] urlParts = new String[] { deviceName, binaryType, version };
        GenericUrl url = AndroidBuildFetcher.createUrl(BASE_URI, urlParts, null);
        fetchMedia(url, outputFile);
    }

    /**
     * Help function to create device blob name.
     *
     * @param binaryType
     * @param version
     * @param extension
     * @return blob name
     */
    public static String createBlobName(String binaryType, String version, String extension) {
        String name;
        if (version == null) {
            name = String.format("%s%s", binaryType, extension);
        } else {
            name = String.format("%s.%s%s", binaryType, version, extension);
        }
        return name;
    }

}
