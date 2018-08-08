// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.android.tradefed.util.StreamUtil;

import junit.framework.TestCase;

import java.io.IOException;
import java.text.ParseException;

/**
 * Unit tests for {@link SsoClientOutputParser}.
 */
public class SsoClientOutputParserTest extends TestCase {

    private SsoClientOutputParser mParser = null;

    @Override
    public void setUp() {
        mParser = new SsoClientOutputParser();
    }

    public void testParse_simpleResponse() throws IOException, ParseException {
        // Parse a simple HTTP Response
        String output = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\nHello World!";
        SsoClientHttpResponse response = mParser.parse(output);

        // Verify that the response object has the correct values
        assertEquals("HTTP/1.1 200 OK", response.getStatusLine());
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getReasonPhrase());
        assertEquals(1, response.getHeaderCount());
        assertEquals("Content-Type", response.getHeaderName(0));
        assertEquals("text/plain", response.getHeaderValue(0));
        assertEquals("Hello World!".getBytes().length, response.getContentLength());
        assertEquals("Hello World!", StreamUtil.getStringFromStream(response.getContent()));
    }

    public void testParse_invalidResponse() {
        // Try to parse an invalid HTTP Response
        try {
            mParser.parse("FOOBAR");
            fail("No exception thrown on invalid response");
        } catch (ParseException e) {
            // Expected
        }
    }

    public void testParse_emptyResponse() {
        // Try to parse an invalid HTTP Response
        try {
            mParser.parse("");
            fail("No exception thrown on empty response");
        } catch (ParseException e) {
            // Expected
        }
    }

    public void testParseStatusLine_statusOK() throws IOException, ParseException {
        // Parse a simple HTTP Response Status-Line
        String statusLine = "HTTP/1.1 200 OK";
        SsoClientHttpResponse response = mParser.parseStatusLine(statusLine);

        // Verify that the response object has the correct values
        assertEquals(statusLine, response.getStatusLine());
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getReasonPhrase());

        // Verify that the response has no headers or content at this point
        assertEquals(0, response.getHeaderCount());
        assertNull(response.getContent());
        assertEquals(0, response.getContentLength());
    }

    public void testParseStatusLine_status200_noReasonPhrase() throws IOException, ParseException {
        // Parse a simple HTTP Response Status-Line
        String statusLine = "HTTP/1.1 200";
        SsoClientHttpResponse response = mParser.parseStatusLine(statusLine);

        // Verify that the response object has the correct values
        assertEquals(statusLine, response.getStatusLine());
        assertEquals(200, response.getStatusCode());
        assertNull(response.getReasonPhrase());

        // Verify that the response has no headers or content at this point
        assertEquals(0, response.getHeaderCount());
        assertNull(response.getContent());
        assertEquals(0, response.getContentLength());
    }

    public void testParseStatusLine_statusForbidden() throws IOException, ParseException {
        // Parse a simple HTTP Response Status-Line
        String statusLine = "HTTP/1.1 403 Forbidden";
        SsoClientHttpResponse response = mParser.parseStatusLine(statusLine);

        // Verify that the response object has the correct values
        assertEquals(statusLine, response.getStatusLine());
        assertEquals(403, response.getStatusCode());
        assertEquals("Forbidden", response.getReasonPhrase());

        // Verify that the response has no headers or content at this point
        assertEquals(0, response.getHeaderCount());
        assertNull(response.getContent());
        assertEquals(0, response.getContentLength());
    }

    public void testParseStatusLine_statusInternalServerError() throws IOException, ParseException {
        // Parse a simple HTTP Response Status-Line
        String statusLine = "HTTP/1.1 500 Internal Server Error";
        SsoClientHttpResponse response = mParser.parseStatusLine(statusLine);

        // Verify that the response object has the correct values
        assertEquals(statusLine, response.getStatusLine());
        assertEquals(500, response.getStatusCode());
        assertEquals("Internal Server Error", response.getReasonPhrase());

        // Verify that the response has no headers or content at this point
        assertEquals(0, response.getHeaderCount());
        assertNull(response.getContent());
        assertEquals(0, response.getContentLength());
    }

    public void testParseStatusLine_http1_0() throws IOException, ParseException {
        // Parse a simple HTTP Response Status-Line
        String statusLine = "HTTP/1.0 200 OK";
        SsoClientHttpResponse response = mParser.parseStatusLine(statusLine);

        // Verify that the response object has the correct values
        assertEquals(statusLine, response.getStatusLine());
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getReasonPhrase());

        // Verify that the response has no headers or content at this point
        assertEquals(0, response.getHeaderCount());
        assertNull(response.getContent());
        assertEquals(0, response.getContentLength());
    }

    public void testParseStatusLine_invalidStatusLine() {
        // Attempt to parse an invalid status line
        String statusLine = "FOOBAR";
        try {
            mParser.parseStatusLine(statusLine);
            fail("No exception thrown on invalid status line");
        } catch (ParseException e) {
            // Expected
        }
    }

    public void testParseStatusLine_emptyStatusLine() {
        // Attempt to parse an invalid status line
        String statusLine = "";
        try {
            mParser.parseStatusLine(statusLine);
            fail("No exception thrown on empty status line");
        } catch (ParseException e) {
            // Expected
        }
    }

    public void testParseStatusLine_invalidProtocol() {
        // Attempt to parse an invalid status line
        String statusLine = "HTTP/5.0 200 OK";
        try {
            mParser.parseStatusLine(statusLine);
            fail("No exception thrown on invalid protocol");
        } catch (ParseException e) {
            // Expected
        }
    }

    public void testParseStatusLine_invalidStatusCode() {
        // Attempt to parse an invalid status line
        String statusLine = "HTTP/1.1 TWO_HUNDRED OK";
        try {
            mParser.parseStatusLine(statusLine);
            fail("No exception thrown on invalid status code");
        } catch (ParseException e) {
            // Expected
        }
    }

    public void testParseHeaderLine_server() throws IOException, ParseException {
        // Initialize a response object
        SsoClientHttpResponse response = new SsoClientHttpResponse("HTTP/1.1 200 OK", 200, "OK");

        // Parse a simple header line
        mParser.parseHeaderLine("Server: gws", response);

        // Verify that the response object has the correct values
        assertEquals(1, response.getHeaderCount());
        assertEquals("Server", response.getHeaderName(0));
        assertEquals("gws", response.getHeaderValue(0));
    }

    public void testParseHeaderLine_date() throws IOException, ParseException {
        // Initialize a response object
        SsoClientHttpResponse response = new SsoClientHttpResponse("HTTP/1.1 200 OK", 200, "OK");

        // Parse a simple header line
        mParser.parseHeaderLine("Date: Mon, 6 Aug 2012 05:17:00 UTC", response);

        // Verify that the response object has the correct values
        assertEquals(1, response.getHeaderCount());
        assertEquals("Date", response.getHeaderName(0));
        assertEquals("Mon, 6 Aug 2012 05:17:00 UTC", response.getHeaderValue(0));
    }

    public void testParseHeaderLine_contentEncoding() throws IOException, ParseException {
        // Initialize a response object
        SsoClientHttpResponse response = new SsoClientHttpResponse("HTTP/1.1 200 OK", 200, "OK");

        // Parse a simple header line
        mParser.parseHeaderLine("Content-Encoding: gzip", response);

        // Verify that the response object has the correct values
        assertEquals(1, response.getHeaderCount());
        assertEquals("Content-Encoding", response.getHeaderName(0));
        assertEquals("gzip", response.getHeaderValue(0));
        assertEquals("gzip", response.getContentEncoding());
    }

    public void testParseHeaderLine_contentType() throws IOException, ParseException {
        // Initialize a response object
        SsoClientHttpResponse response = new SsoClientHttpResponse("HTTP/1.1 200 OK", 200, "OK");

        // Parse a simple header line
        mParser.parseHeaderLine("Content-Type: text/html", response);

        // Verify that the response object has the correct values
        assertEquals(1, response.getHeaderCount());
        assertEquals("Content-Type", response.getHeaderName(0));
        assertEquals("text/html", response.getHeaderValue(0));
        assertEquals("text/html", response.getContentType());
    }

    public void testParseHeaderLine_contentType_withParameter() throws IOException, ParseException {
        // Initialize a response object
        SsoClientHttpResponse response = new SsoClientHttpResponse("HTTP/1.1 200 OK", 200, "OK");

        // Parse a simple header line
        mParser.parseHeaderLine("Content-Type: text/html; charset=UTF-8", response);

        // Verify that the response object has the correct values
        assertEquals(1, response.getHeaderCount());
        assertEquals("Content-Type", response.getHeaderName(0));
        assertEquals("text/html; charset=UTF-8", response.getHeaderValue(0));
        assertEquals("text/html; charset=UTF-8", response.getContentType());
    }

    public void testParseHeaderLine_multipleHeaders() throws IOException, ParseException {
        // Initialize a response object
        SsoClientHttpResponse response = new SsoClientHttpResponse("HTTP/1.1 200 OK", 200, "OK");

        // Parse some headers
        mParser.parseHeaderLine("Server: gws", response);
        mParser.parseHeaderLine("Content-Encoding: gzip", response);
        mParser.parseHeaderLine("Content-Type: text/html", response);

        // Verify that the response object has the correct values
        assertEquals(3, response.getHeaderCount());
        assertEquals("Server", response.getHeaderName(0));
        assertEquals("gws", response.getHeaderValue(0));
        assertEquals("Content-Encoding", response.getHeaderName(1));
        assertEquals("gzip", response.getHeaderValue(1));
        assertEquals("gzip", response.getContentEncoding());
        assertEquals("Content-Type", response.getHeaderName(2));
        assertEquals("text/html", response.getHeaderValue(2));
        assertEquals("text/html", response.getContentType());
    }

    public void testParseHeaderLine_repeatedHeader() throws IOException, ParseException {
        // Initialize a response object
        SsoClientHttpResponse response = new SsoClientHttpResponse("HTTP/1.1 200 OK", 200, "OK");

        // Parse a simple header line
        mParser.parseHeaderLine("Set-Cookie: FOO=SBB", response);
        mParser.parseHeaderLine("Set-Cookie: BAR=ONE", response);

        // Verify that the response object has the correct values
        assertEquals(2, response.getHeaderCount());
        assertEquals("Set-Cookie", response.getHeaderName(0));
        assertEquals("FOO=SBB", response.getHeaderValue(0));
        assertEquals("Set-Cookie", response.getHeaderName(1));
        assertEquals("BAR=ONE", response.getHeaderValue(1));
    }

    public void testParseHeaderLine_invalidHeader() {
        // Initialize a response object
        SsoClientHttpResponse response = new SsoClientHttpResponse("HTTP/1.1 200 OK", 200, "OK");

        // Attempt to parse an invalid header line
        try {
            mParser.parseHeaderLine("INVALID", response);
            fail("No exception thrown on invalid header");
        } catch (ParseException e) {
            // Expected
        }
    }

    public void testParseHeaderLine_emptyHeader() {
        // Initialize a response object
        SsoClientHttpResponse response = new SsoClientHttpResponse("HTTP/1.1 200 OK", 200, "OK");

        // Attempt to parse an invalid header line
        try {
            mParser.parseHeaderLine("", response);
            fail("No exception thrown on empty header");
        } catch (ParseException e) {
            // Expected
        }
    }

    public void testSetContent() throws IOException {
        // Initialize a response object
        SsoClientHttpResponse response = new SsoClientHttpResponse("HTTP/1.1 200 OK", 200, "OK");

        // Set some content
        String content = "Hello world!";
        response.setContent(content);

        // Verify that the response object has the correct values
        assertEquals(content, StreamUtil.getStringFromStream(response.getContent()));
        assertEquals(content.getBytes().length, response.getContentLength());
    }

    public void testSetContent_containingCRLF() throws IOException {
        // Initialize a response object
        SsoClientHttpResponse response = new SsoClientHttpResponse("HTTP/1.1 200 OK", 200, "OK");

        // Set some content
        String content = "Hello world!\r\nGreetings to earth\r\n\r\n";
        response.setContent(content);

        // Verify that the response object has the correct values
        assertEquals(content, StreamUtil.getStringFromStream(response.getContent()));
        assertEquals(content.getBytes().length, response.getContentLength());
    }

    public void testSetContent_emptyContent() throws IOException {
        // Initialize a response object
        SsoClientHttpResponse response = new SsoClientHttpResponse("HTTP/1.1 200 OK", 200, "OK");

        // Set some content
        response.setContent("");

        // Verify that the response object has the correct values
        assertEquals("", StreamUtil.getStringFromStream(response.getContent()));
        assertEquals(0, response.getContentLength());
    }
}
