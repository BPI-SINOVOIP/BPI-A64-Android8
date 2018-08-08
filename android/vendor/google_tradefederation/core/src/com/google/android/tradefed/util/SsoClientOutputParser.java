// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * A helper class for parsing output from the sso_client command line utility.
 */
class SsoClientOutputParser implements ISsoClientOutputParser {

    private static ISsoClientOutputParser sDefaultInstance = null;

    /**
     * Get a reference to the default {@link SsoClientOutputParser}.
     */
    public static ISsoClientOutputParser getDefault() {
        if (sDefaultInstance == null) {
            sDefaultInstance = new SsoClientOutputParser();
        }
        return sDefaultInstance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SsoClientHttpResponse parse(String ssoClientOutput) throws ParseException {
        return parse(new ByteArrayInputStream(ssoClientOutput.getBytes()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SsoClientHttpResponse parse(InputStream ssoClientOutput) throws ParseException {
        // The HTTP Response is delimited by CR LF characters
        Scanner scanner = new Scanner(ssoClientOutput);
        scanner.useDelimiter("\r\n");
        try {
            // The first line is the status field
            SsoClientHttpResponse response = parseStatusLine(scanner.next());

            // The following lines are headers, until we reach an empty line
            for (String line = scanner.next(); !line.isEmpty(); line = scanner.next()) {
                parseHeaderLine(line, response);
            }

            // The rest of the content is the body
            scanner.skip("\r\n").useDelimiter("\\z");
            StringBuilder content = new StringBuilder();
            while (scanner.hasNext()) {
                content.append(scanner.next());
            }
            if (content.length() > 0) {
                response.setContent(content.toString());
            }

            // We're done! Return the response.
            return response;
        } catch (NoSuchElementException e) {
            ParseException parseException = new ParseException(
                    String.format("Failed to parse sso_client output: %s", ssoClientOutput), 0);
            parseException.initCause(e);
            throw parseException;
        } finally {
            scanner.close();
        }
    }

    /**
     * Parse an HTTP response status line and returns an initialized {@link SsoClientHttpResponse}.
     * @param line the status line
     * @return an {@link SsoClientHttpResponse} object
     * @throws ParseException
     */
    SsoClientHttpResponse parseStatusLine(String line) throws ParseException {
        // Line must start with 'HTTP/1.' and contain at least one space
        String[] parts = line.split(" ", 3);
        if (parts.length < 2) {
            throw new ParseException(String.format("Invalid status line: '%s'", line), 0);
        }
        if (!parts[0].startsWith("HTTP/1.")) {
            throw new ParseException(String.format("Invalid protocol version: '%s'", parts[0]), 0);
        }

        // Extract the status code
        int statusCode;
        try {
            statusCode = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new ParseException(String.format("Invalid status code: '%s'", parts[1]), 0);
        }

        // Reason phrase is optional. Set it to null if not present.
        String reasonPhrase = parts.length > 2 ? parts[2] : null;

        // Initialize an SsoClientHttpResponse object with the parsed data and return
        return new SsoClientHttpResponse(line, statusCode, reasonPhrase);
    }

    /**
     * Reads a single header line and adds it to the {@code response}.
     * @param line the header line
     * @param response the {@link SsoClientHttpResponse} object to add the header to
     * @throws ParseException
     */
    void parseHeaderLine(String line, SsoClientHttpResponse response) throws ParseException {
        // Line must contain a ': '
        String[] parts = line.split(": ", 2);
        if (parts.length < 2) {
            throw new ParseException(String.format("Invalid header: '%s'", line), 0);
        }

        // Add the header name and value to the response
        response.addHeader(parts[0], parts[1]);
    }
}
