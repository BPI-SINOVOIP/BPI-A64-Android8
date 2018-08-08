// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.util.StreamingContent;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link LowLevelHttpRequest} which uses the sso_client command line utility to transmit the
 * request.
 */
class SsoClientHttpRequest extends LowLevelHttpRequest {

    // The method and URL to use for this request
    private String mMethod;
    private String mUrl;

    // Request header fields
    private List<String> mHeaders = new ArrayList<>();

    // Timeout
    private long mConnectTimeout = 0;

    private String mSsoClientPath = "sso_client";

    /**
     * Construct a {@link SsoClientHttpRequest} for the given method and URL, and the sso_client
     * path to use.
     */
    SsoClientHttpRequest(String method, String url, String ssoClientPath) {
        mMethod = method;
        mUrl = url;
        mSsoClientPath = ssoClientPath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTimeout(int connectTimeout, int readTimeout) throws IOException {
        mConnectTimeout = connectTimeout;
        // Ignore readTimeout since sso_client will write the response all at once and we'll never
        // be waiting for more data.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeader(String name, String value) throws IOException {
        // Ignore headers with null values
        if (value == null) {
            return;
        }

        // Ignore "Accept-Encoding" headers as we don't support any compression
        if ("Accept-Encoding".equals(name)) {
            return;
        }

        mHeaders.add(String.format("%s: %s", name, value));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LowLevelHttpResponse execute() throws IOException {
        CommandResult result = null;
        File tmp = null;
        try {
            // Build the base command string
            List<String> cmd = Lists.newArrayList(mSsoClientPath,
                    "--method", mMethod, "--request_timeout", String.valueOf(mConnectTimeout),
                    "--dump_header", "--location");

            // If the request has content
            StreamingContent content = getStreamingContent();
            if (content != null) {
                // Write the content to a temp file
                tmp = FileUtil.createTempFile("postData", "tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    content.writeTo(out);
                }

                // Add the data file to the command string
                cmd.addAll(Arrays.asList("--data_file", tmp.getAbsolutePath()));

                // Set content headers
                addHeader("Content-Type", getContentType());
                addHeader("Content-Length", String.valueOf(getContentLength()));
                addHeader("Content-Encoding", getContentEncoding());
            }

            // Add the headers to the command string
            cmd.addAll(Arrays.asList("--headers", Joiner.on(";").join(mHeaders)));

            // Finally, add the URL as the final parameter
            cmd.add(mUrl);

            // Actually run the command
            result = getRunUtil().runTimedCmd(mConnectTimeout, cmd.toArray(new String[] {}));
        } finally {
            // Cleanup the tmp file (if applicable)
            FileUtil.deleteFile(tmp);
        }

        // Check for errors, and return an SsoClientHttpResponse if successful
        switch (result.getStatus()) {
            case FAILED:
                throw new IOException(result.getStderr());
            case TIMED_OUT:
                throw new IOException("Request timed out");
            case EXCEPTION:
                throw new IOException("Unknown error");
            case SUCCESS:
                String response = result.getStdout();
                try {
                    return getSsoClientOutputParser().parse(response);
                } catch (ParseException e) {
                    throw new IOException("Failed to parse sso_client output", e);
                }
            default:
                // Shouldn't happen
                throw new RuntimeException(
                        String.format("Unknown CommandStatus: %s", result.getStatus()));
        }
    }

    /**
     * Get the {@link RunUtil} instance to use.
     * </p>
     * Exposed for unit testing.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Get the {@link SsoClientOutputParser} instance to use.
     * </p>
     * Exposed for unit testing.
     */
    ISsoClientOutputParser getSsoClientOutputParser() {
        return SsoClientOutputParser.getDefault();
    }
}
