// Copyright 2015 Google Inc.  All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Map;

/**
 * Help class used for downloading specific url from the android build server.
 */
public class AndroidBuildFetcher {
    private static final int DEFAULT_CHUNK_SIZE = 20 * 1024 * 1024; // bytes
    private static final int BUF_SIZE = 1024; // bytes
    private static final int MAX_DUMP_RESPONSE_SIZE = 5 * 1024; // bytes
    private static final String ACCESS_SCOPE =
            "https://www.googleapis.com/auth/androidbuild.internal";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private HttpRequestFactory mRequestFactory;
    private HttpTransport mTransport;
    private GoogleCredential mCredential;

    /**
     * Constructor of AndroidBuildFetcher.
     *
     * @param keyFile key file to access android build api.
     * @param serviceAccount the service account to access android build api.
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public AndroidBuildFetcher(File keyFile, String serviceAccount)
            throws GeneralSecurityException, IOException {
        mTransport = GoogleNetHttpTransport.newTrustedTransport();
        mCredential = new GoogleCredential.Builder()
                .setTransport(mTransport)
                .setJsonFactory(JSON_FACTORY)
                .setServiceAccountId(serviceAccount)
                .setServiceAccountScopes(Collections.singleton(ACCESS_SCOPE))
                .setServiceAccountPrivateKeyFromP12File(keyFile)
                .build();
    }

    /**
     * Returns the HttpRequestFactory, setting it up if it hasn't been set up. Thread-safe.
     */
    HttpRequestFactory getRequestFactory() {
        if (mRequestFactory != null)
            return mRequestFactory;

        synchronized (this) {
            // check again, in case another thread beat us here
            if (mRequestFactory != null)
                return mRequestFactory;
            mRequestFactory = mTransport.createRequestFactory(mCredential);
        }

        return mRequestFactory;
    }

    /**
     * Fetch meta data from a specific url.
     *
     * @param url
     * @return meta data string
     * @throws IOException
     */
    public String fetchMetaData(GenericUrl url) throws IOException {
        url = url.clone();
        // add alt=json to the url to get the meta data
        url.put("alt", "json");
        HttpRequest request = getRequestFactory().buildGetRequest(url);
        CLog.v("Begin to download meta data %s.", url);
        HttpResponse response = request.execute();
        verifyResponse(response);
        return StreamUtil.getStringFromStream(response.getContent());
    }

    /**
     * Download a url media file to a local file.
     *
     * @param url
     * @param outputFile
     * @throws IOException
     * @throws JSONException
     */
    public void fetchMedia(GenericUrl url, File outputFile) throws IOException, JSONException {
        url = url.clone();
        // add alt=media to the url to get the media data
        url.put("alt", "media");
        HttpRequest request = getRequestFactory().buildGetRequest(url);

        String metaData = fetchMetaData(url);
        JSONObject metaJson = new JSONObject(metaData);
        final long totalSize = metaJson.getLong("size");
        final String md5 = metaJson.getString("md5");

        fetchMedia(request, totalSize, md5, outputFile, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Help method to download the media file.
     *
     * @param totalSize the size of the file to download
     * @param md5 the md5 of the file to download. Verify md5 if this is not null.
     * @param outputFile download file to outputFile
     * @param chunkSize download file chunk by chunk and this is the size of the chunk
     * @throws IOException
     */
    void fetchMedia(final HttpRequest request, final long totalSize, final String md5,
            final File outputFile, final long chunkSize) throws IOException {
        OutputStream output = null;
        try {
            output = new FileOutputStream(outputFile);
            CLog.v("Begin to download %s to %s.", request.getUrl(), outputFile);
            download(request, output, totalSize, chunkSize);
            CLog.v("Finish downloading %s to %s.", request.getUrl(), outputFile);

            // Check md5 of the downloaded file.
            if (md5 != null) {
                String actualMd5 = FileUtil.calculateMd5(outputFile);
                CLog.v("Checking md5, local %s, remote %s.", actualMd5, md5);
                if (!md5.toLowerCase().equals(actualMd5)) {
                    throw new IOException(String.format(
                            "Download failed, checksum(md5) error. Expected %s but observed %s",
                            md5, actualMd5));
                }
            }
        } finally {
            output.close();
        }
    }

    /**
     * One shot download a request.
     *
     * @param request
     * @param output
     * @throws IOException
     */
    void download(HttpRequest request, OutputStream output) throws IOException {
        HttpResponse response = request.execute();
        verifyResponse(response);
        response.download(output);
        output.flush();
    }

    /**
     * Download a request chunk by chunk. If the totalSize < chunkSize, will use one shot download.
     *
     * @param request
     * @param output
     * @param totalSize
     * @param chunkSize
     * @throws IOException
     */
    void download(HttpRequest request, OutputStream output,
            final long totalSize, final long chunkSize) throws IOException {
        if (totalSize < chunkSize) {
            download(request, output);
            return;
        }
        HttpHeaders headers = request.getHeaders();
        long size = 0;
        while (size < totalSize) {
            headers.setRange(String.format("bytes=%d-%d", size, size + chunkSize));
            request.setHeaders(headers);
            HttpResponse response = request.execute();
            verifyResponse(response);
            size += copyStream(response.getContent(), output);
        }
        output.flush();
    }

    /**
     * Verify response status code is HTTP_OK or HTTP_PARTIAL.
     *
     * @param response
     * @throws IOException
     */
    void verifyResponse(HttpResponse response) throws IOException {
        if (response.getStatusCode() != HttpURLConnection.HTTP_OK
                && response.getStatusCode() != HttpURLConnection.HTTP_PARTIAL) {
            String content = response.parseAsString();
            String truncatedContent = content;
            if (MAX_DUMP_RESPONSE_SIZE < content.length()) {
               truncatedContent = content.substring(0, MAX_DUMP_RESPONSE_SIZE) + "...";
            }
            throw new IOException(String.format("Request fail. Response code: %d. Content: %s",
                    response.getStatusCode(), truncatedContent));
        }
    }

    /**
     * Help method to copy input to output.
     *
     * @param input
     * @param output
     * @return the bytes copied
     * @throws IOException
     */
    static long copyStream(InputStream input, OutputStream output) throws IOException {
        int size = 0;
        byte[] buf = new byte[BUF_SIZE];
        int len = 0;
        while ((len = input.read(buf, 0, BUF_SIZE)) >= 0) {
            size += len;
            output.write(buf, 0, len);
        }
        return size;
    }

    /**
     * Create a properly encoded url.
     *
     * @param baseUrl properly encoded.
     * @param urlParts not encoded.
     * @param urlParams the params will append to the url like key=value, not encoded.
     * @return url properly encoded.
     */
    static GenericUrl createUrl(String baseUrl, String[] urlParts, Map<String, String> urlParams) {
        GenericUrl url = new GenericUrl(baseUrl);
        for (int i = 0; i < urlParts.length; ++i) {
            if (i > 0 || (i == 0 && !baseUrl.endsWith("/"))) {
                url.appendRawPath("/");
            }
            url.appendRawPath(encode(urlParts[i]));
        }
        if (urlParams != null) {
            for(Map.Entry<String, String> entry: urlParams.entrySet()) {
                url.put(encode(entry.getKey()), encode(entry.getValue()));
            }
        }
        return url;
    }

    /**
     * Encode urlPart, escape special characters.
     * @param urlPart
     * @return encoded urlPart
     */
    static String encode(String urlPart) {
        try {
            return URLEncoder.encode(urlPart, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // This will never happen.
            throw new RuntimeException(e);
        }
    }
}
