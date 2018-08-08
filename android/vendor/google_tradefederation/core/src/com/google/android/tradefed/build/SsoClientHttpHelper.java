// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.IRunUtil.IRunnableResult;
import com.android.tradefed.util.net.HttpHelper;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Contains helper methods for making http requests using the sso client binary
 */
public class SsoClientHttpHelper extends HttpHelper {
    /** Each line of the header from SSO client response ends in CRLF */
    private static final String HTTP_HEADER_LINE_DELIMITER = "\r\n";

    /** The whole header from SSO client response ends with CRLF CRLF */
    private static final String HTTP_HEADER_DELIMITER = "\r\n\r\n";

    /** Time before timing out a sso_client command in ms. */
    private int mSsoClientTimeout = 2 * 60 * 1000;

    private String mSsoClient = "sso_client";
    private String mSsoCert = null;

    public SsoClientHttpHelper() {
    }

    public SsoClientHttpHelper(String ssoClient, String certificate) {
        mSsoClient = ssoClient;
        mSsoCert = certificate;
    }

    int getSsoClientTimeout() {
        return mSsoClientTimeout;
    }

    void setSsoClientTimeout(int timeout) {
        mSsoClientTimeout = timeout;
    }

    /**
     * Returns the path to the sso_client binary to be used.
     */
    String getSsoClientPath() {
        return mSsoClient;
    }

    /**
     * Helper method that calls sso_client.
     *
     * @param url
     * @param data
     * @param contentType
     * @return {@link CommandResult}
     */
    private CommandResult requestWithSsoClient(String url, String data, String contentType) {
        // NOTE: The response now contains the HTTP headers. If the header contains a 500
        // Internal Server Error status code, we will return nothing (an empty string).
        // Make sure to use --location instead --url to ensure that redirects are followed.
        List<String> cmd = Lists.newArrayList(mSsoClient, "--dump_header", "--location", url);
        if (mSsoCert != null) {
            cmd.add("--ca_cert");
            cmd.add(mSsoCert);
        }
        if (data != null) {
            cmd.add("--data");
            cmd.add(data);
        }
        if (contentType != null) {
            String extraHeader = String.format("Content-Type: %s", contentType);
            cmd.add("--headers");
            cmd.add(extraHeader);
        }
        cmd.add("--connect_timeout");
        cmd.add(Integer.toString(getOpTimeout() / 1000));
        cmd.add("--request_timeout");
        cmd.add(Integer.toString(getOpTimeout() / 1000));

        return getRunUtil().runTimedCmd(getSsoClientTimeout(), cmd.toArray(new String[] {}));
    }

    /**
     * Runnable for making sso_client GET requests with
     * {@link IRunUtil#runEscalatingTimedRetry(long, long, long, long, IRunnableResult)}
     * .
     */
    private class SsoClientGetRequestRunnable extends RequestRunnable {
        private boolean mIgnoreResult;

        public SsoClientGetRequestRunnable(String url, boolean ignoreResult) {
            super(url);
            mIgnoreResult = ignoreResult;
        }

        /**
         * Perform a single GET request, storing the response or the associated
         * exception in case of error.
         */
        @Override
        public boolean run() {
            try {
                if (mIgnoreResult) {
                    doGetIgnore(getUrl());
                } else {
                    setResponse(doGet(getUrl()));
                }
                return true;
            } catch (IOException e) {
                CLog.i("IOException %s from %s", e.getMessage(), getUrl());
                setException(e);
            } catch (DataSizeException e) {
                CLog.i("Unexpected oversized response from %s", getUrl());
                setException(e);
            } catch (RuntimeException e) {
                CLog.i("RuntimeException %s", e.getMessage());
                setException(e);
            }
            return false;
        }
    }

    /**
     * Runnable for making sso_client POST requests with
     * {@link IRunUtil#runEscalatingTimedRetry(long, long, long, long, IRunnableResult)}
     * .
     */
    private class SsoClientPostRequestRunnable extends RequestRunnable {
        String mPostData;
        String mContentType;

        public SsoClientPostRequestRunnable(String url, String postData, String contentType) {
            super(url);
            mPostData = postData;
            mContentType = contentType;
        }

        /**
         * Perform a single POST request, storing the response or the associated
         * exception in case of error.
         */
        @Override
        public boolean run() {
            try {
                CommandResult result = requestWithSsoClient(getUrl(), mPostData, mContentType);
                setResponse(result.getStdout());
                return result.getStatus() == CommandStatus.SUCCESS;
            } catch (RuntimeException e) {
                CLog.i("RuntimeException %s", e.getMessage());
                setException(e);
            }
            return false;
        }
    }

    @Override
    public String doGet(String url) throws IOException, DataSizeException {
        CommandResult result = requestWithSsoClient(url, null, null);
        if (result.getStatus() == CommandStatus.SUCCESS && result.getStdout() != null) {
            return validateAndAdjustResponse(result.getStdout());
        }
        return "";
    }

    /** Validate and adjust a HTTP response if necessary. */
    protected String validateAndAdjustResponse(String response) {
        CLog.i("Validating SSO Client response: %s", response);

        // The first line is always a HTTP status (e.g.: "HTTP/1.1 400 Bad Request")
        String[] responseLines = response.split(HTTP_HEADER_LINE_DELIMITER, 2);
        int statusCode = parseHttpStatusCode(responseLines[0]);
        if (statusCode == 500) {
            // An empty response will result in a null RemoteBuildInfo and no builds to test.
            CLog.i("An internal server error (HTTP 500) was found in the response. " +
            "Returning an empty response");
            return "";
        }

        String body = stripResponseHeader(response);
        CLog.d("Returning response body: %s", body);
        return body;
    }

    /** Remove the header from an HTTP response from SSO client and return its body. */
    protected String stripResponseHeader(String response) {
        int headerEndIndex = response.indexOf(HTTP_HEADER_DELIMITER);
        if (headerEndIndex == -1) {
            // If the separation isn't present it means that the body was empty
            CLog.i("No HTTP header found. Returning original response.");
            return response;
        }

        headerEndIndex += HTTP_HEADER_DELIMITER.length();

        return response.substring(headerEndIndex);
    }

    /**
     * Parse the HTTP status code (eg. 200) from a HTTP status line (eg. HTTP/1.1 200 OK)
     *
     * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html#sec6.1">Status-Line</a>
     */
    protected int parseHttpStatusCode(String httpStatusLine) {
        // The code is always the second word of the status line
        String[] statusSections = httpStatusLine.split(" ", 3);

        // A HTTP status line has 3 elements separated by spaces:
        // HTTP-Version (eg. HTTP/1.1) SP Status-Code (eg. 200) SP Reason-Phrase (eg. OK) CRLF
        if (statusSections.length >= 3) {
            try {
                return Integer.parseInt(statusSections[1]);
            } catch (NumberFormatException e) {
                CLog.i("NumberFormatException %s while parsing %s from %s ", e.getMessage(),
                        statusSections[1], httpStatusLine);
            }
        }

        CLog.w("Unable to parse status code from HTTP status line [%s]. Assuming OK.",
                httpStatusLine);

        // If we can't parse the status code, assume it's OK
        return 200;
    }

    @Override
    public String doGetWithRetry(String url) throws IOException, DataSizeException {
        SsoClientGetRequestRunnable runnable = new SsoClientGetRequestRunnable(url, false);
        if (getRunUtil().runEscalatingTimedRetry(getOpTimeout(), getInitialPollInterval(),
                getMaxPollInterval(), getMaxTime(), runnable)) {
            return runnable.getResponse();
        } else if (runnable.getException() instanceof IOException) {
            throw (IOException) runnable.getException();
        } else if (runnable.getException() instanceof DataSizeException) {
            throw (DataSizeException) runnable.getException();
        } else if (runnable.getException() instanceof RuntimeException) {
            throw (RuntimeException) runnable.getException();
        } else {
            throw new IOException("GET request could not be completed");
        }
    }

    @Override
    public void doGetIgnore(String url) throws IOException {
        requestWithSsoClient(url, null, null);
    }

    @Override
    public void doGetIgnoreWithRetry(String url) throws IOException {
        SsoClientGetRequestRunnable runnable = new SsoClientGetRequestRunnable(url, true);
        if (getRunUtil().runEscalatingTimedRetry(getOpTimeout(), getInitialPollInterval(),
                getMaxPollInterval(), getMaxTime(), runnable)) {
            return;
        } else if (runnable.getException() instanceof IOException) {
            throw (IOException) runnable.getException();
        } else if (runnable.getException() instanceof RuntimeException) {
            throw (RuntimeException) runnable.getException();
        } else {
            throw new IOException("GET request could not be completed");
        }
    }

    @Override
    public String doPostWithRetry(String url, String postData, String contentType)
            throws IOException, DataSizeException {
        SsoClientPostRequestRunnable runnable = new SsoClientPostRequestRunnable(
                url, postData, contentType);
        if (getRunUtil().runEscalatingTimedRetry(getOpTimeout(), getInitialPollInterval(),
                getMaxPollInterval(), getMaxTime(), runnable)) {
            return runnable.getResponse();
        } else if (runnable.getException() instanceof IOException) {
            throw (IOException) runnable.getException();
        } else if (runnable.getException() instanceof DataSizeException) {
            throw (DataSizeException) runnable.getException();
        } else if (runnable.getException() instanceof RuntimeException) {
            throw (RuntimeException) runnable.getException();
        } else {
            throw new IOException("POST request could not be completed");
        }
    }

    @Override
    public HttpURLConnection createConnection(URL url, String method, String contentType)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpURLConnection createXmlConnection(URL url, String method) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpURLConnection createJsonConnection(URL url, String method) throws IOException {
        throw new UnsupportedOperationException();
    }
}
