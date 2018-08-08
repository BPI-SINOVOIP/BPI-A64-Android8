// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.google.api.client.http.LowLevelHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * A {@link LowLevelHttpResponse} implementation used to encapsulate an HTTP response from
 * sso_client.
 */
class SsoClientHttpResponse extends LowLevelHttpResponse {

    // Response Status
    private String mStatusLine;
    private int mStatusCode = -1;
    private String mReasonPhrase;

    // Headers
    private ArrayList<String> mHeaderNames = new ArrayList<>();
    private ArrayList<String> mHeaderValues = new ArrayList<>();

    // Message content
    private String mContent = null;
    private String mContentEncoding = null;
    private String mContentType = null;

    /**
     * Construct an SsoClientHttpResponse with the given status.
     * @param statusLine the HTTP Response Status-Line
     * @param statusCode the HTTP Response Status-Code
     * @param reasonPhrase the HTTP Response Reason-Phrase
     */
    SsoClientHttpResponse(String statusLine, int statusCode, String reasonPhrase) {
        mStatusLine = statusLine;
        mStatusCode = statusCode;
        mReasonPhrase = reasonPhrase;
    }

    /**
     * Add a header to the response.
     * @param name the header name
     * @param value the header value
     */
    void addHeader(String name, String value) {
        mHeaderNames.add(name);
        mHeaderValues.add(value);

        // Keep track of certain header values for easy access
        if (mContentEncoding == null && "Content-Encoding".equals(name)) {
            mContentEncoding = value;
        } else if (mContentType == null && "Content-Type".equals(name)) {
            mContentType = value;
        }
    }

    /**
     * Set the response content to the given String.
     * @param content the response content
     */
    public void setContent(String content) {
        mContent = content;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream getContent() throws IOException {
        return mContent == null ? null : new ByteArrayInputStream(mContent.getBytes());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentEncoding() throws IOException {
        return mContentEncoding;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getContentLength() throws IOException {
        return mContent == null ? 0 : mContent.getBytes().length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentType() throws IOException {
        return mContentType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStatusLine() throws IOException {
        return mStatusLine;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getStatusCode() throws IOException {
        return mStatusCode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getReasonPhrase() throws IOException {
        return mReasonPhrase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getHeaderCount() throws IOException {
        return mHeaderNames.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHeaderName(int index) throws IOException {
        return mHeaderNames.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHeaderValue(int index) throws IOException {
        return mHeaderValues.get(index);
    }
}
