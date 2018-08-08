// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.StreamUtil;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.LowLevelHttpRequest;

import junit.framework.TestCase;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

/**
 * Unit tests for {@link SsoClientTransport}.
 */
public class SsoClientTransportTest extends TestCase {

    private static final String EXAMPLE_URL = "http://www.example.com";
    private SsoClientTransport mTransport = null;
    private IRunUtil mMockRunUtil;

    @Override
    public void setUp() {
        mMockRunUtil = Mockito.mock(IRunUtil.class);

        mTransport = new SsoClientTransport() {
            @Override
            protected LowLevelHttpRequest buildRequest(String method, String url)
                    throws IOException {
                return new SsoClientHttpRequest(method, url, "sso_client") {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }
                };
            }
        };
    }

    public void testSimpleGetRequest() throws IOException {
        // Create a mock response
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStdout("HTTP/1.1 200 OK\r\n\r\nHello world");

        // We expect a call to sso_client
        doReturn(result).when(mMockRunUtil).runTimedCmd(Mockito.anyLong(), Mockito.any());

        // Actually make the call
        HttpResponse response = mTransport.createRequestFactory()
                .buildRequest("GET", new GenericUrl(EXAMPLE_URL), null)
                .execute();

        ArgumentCaptor<String> capture = ArgumentCaptor.forClass(String.class);
        verify(mMockRunUtil).runTimedCmd(Mockito.anyLong(), capture.capture());

        List<String> args = capture.getAllValues();
        assertEquals("sso_client", args.get(0));
        assertEquals(EXAMPLE_URL, args.get(args.size() - 1));
        assertTrue(args.contains("--method"));
        assertEquals("GET", args.get(args.indexOf("--method") + 1));

        // Verify that the response was parsed correctly
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getStatusMessage());
        assertEquals("Hello world", StreamUtil.getStringFromStream(response.getContent()));
    }

    public void testSimpleGetRequest_noResponseBody() throws IOException {
        // Create a mock response
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStdout("HTTP/1.1 200 OK\r\n\r\n");

        // We expect a call to sso_client
        doReturn(result).when(mMockRunUtil).runTimedCmd(Mockito.anyLong(), Mockito.any());

        // Actually make the call
        HttpResponse response = mTransport.createRequestFactory()
                .buildRequest("GET", new GenericUrl(EXAMPLE_URL), null)
                .execute();

        // Verify that sso_client was called with the correct arguments
        ArgumentCaptor<String> capture = ArgumentCaptor.forClass(String.class);
        verify(mMockRunUtil).runTimedCmd(Mockito.anyLong(), capture.capture());

        List<String> args = capture.getAllValues();
        assertEquals("sso_client", args.get(0));
        assertEquals(EXAMPLE_URL, args.get(args.size() - 1));
        assertTrue(args.contains("--method"));
        assertEquals("GET", args.get(args.indexOf("--method") + 1));

        // Verify that the response was parsed correctly
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getStatusMessage());
        assertNull(response.getContent());
    }

    public void testSimpleGetRequest_forbiddenWithException() throws IOException {
        // Create a mock response
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStdout("HTTP/1.1 403 Forbidden\r\n\r\nAccess denied.");

        // We expect a call to sso_client
        doReturn(result).when(mMockRunUtil).runTimedCmd(Mockito.anyLong(), Mockito.any());

        // Actually make the call
        try {
            mTransport.createRequestFactory()
                    .buildRequest("GET", new GenericUrl(EXAMPLE_URL), null)
                    .setThrowExceptionOnExecuteError(true)
                    .execute();
            fail("HttpResponseException not thrown");
        } catch (HttpResponseException e) {
            // Verify that the response was parsed correctly
            assertEquals(403, e.getStatusCode());
            assertEquals("Forbidden", e.getStatusMessage());
            assertEquals("Access denied.", e.getContent());
        }

        // Verify that sso_client was called with the correct arguments
        ArgumentCaptor<String> capture = ArgumentCaptor.forClass(String.class);
        verify(mMockRunUtil).runTimedCmd(Mockito.anyLong(), capture.capture());

        List<String> args = capture.getAllValues();
        assertEquals("sso_client", args.get(0));
        assertEquals(EXAMPLE_URL, args.get(args.size() - 1));
        assertTrue(args.contains("--method"));
        assertEquals("GET", args.get(args.indexOf("--method") + 1));
    }

    public void testSimpleGetRequest_forbiddenWithoutException() throws IOException {
        // Create a mock response
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStdout("HTTP/1.1 403 Forbidden\r\n\r\nAccess denied.");

        // We expect a call to sso_client
        doReturn(result).when(mMockRunUtil).runTimedCmd(Mockito.anyLong(), Mockito.any());

        // Actually make the call
        HttpResponse response = mTransport.createRequestFactory()
                .buildRequest("GET", new GenericUrl(EXAMPLE_URL), null)
                .setThrowExceptionOnExecuteError(false)
                .execute();

        // Verify that sso_client was called with the correct arguments
        ArgumentCaptor<String> capture = ArgumentCaptor.forClass(String.class);
        verify(mMockRunUtil).runTimedCmd(Mockito.anyLong(), capture.capture());

        List<String> args = capture.getAllValues();
        assertEquals("sso_client", args.get(0));
        assertEquals(EXAMPLE_URL, args.get(args.size() - 1));
        assertTrue(args.contains("--method"));
        assertEquals("GET", args.get(args.indexOf("--method") + 1));

        // Verify that the response was parsed correctly
        assertEquals(403, response.getStatusCode());
        assertEquals("Forbidden", response.getStatusMessage());
        assertEquals("Access denied.", StreamUtil.getStringFromStream(response.getContent()));
    }

    public void testSimplePostRequest() throws IOException {
        // Initialize some data to post
        HttpContent content = ByteArrayContent.fromString("text/plain", "xyzzy");

        // Create a mock response
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStdout("HTTP/1.1 200 OK\r\n\r\nHello world");

        // We expect a call to sso_client
        doReturn(result).when(mMockRunUtil).runTimedCmd(Mockito.anyLong(), Mockito.any());

        // Actually make the call
        HttpResponse response = mTransport.createRequestFactory()
                .buildRequest("POST", new GenericUrl(EXAMPLE_URL), content)
                .execute();

        // Verify that sso_client was called with the correct arguments
        ArgumentCaptor<String> capture = ArgumentCaptor.forClass(String.class);
        verify(mMockRunUtil).runTimedCmd(Mockito.anyLong(), capture.capture());

        List<String> args = capture.getAllValues();
        assertEquals("sso_client", args.get(0));
        assertEquals(EXAMPLE_URL, args.get(args.size() - 1));
        assertTrue(args.contains("--method"));
        int methodIndex = args.indexOf("--method") + 1;
        assertTrue(args.size() > methodIndex);
        assertEquals("POST", args.get(methodIndex));
        assertTrue(args.contains("--data_file"));
        int dataFileIndex = args.indexOf("--data_file") + 1;
        assertTrue(args.size() > dataFileIndex);
        // Note: We can't actually validate the file contents at this point since it gets cleaned up

        // Verify that the response was parsed correctly
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getStatusMessage());
        assertEquals("Hello world", StreamUtil.getStringFromStream(response.getContent()));
    }

    public void testSimpleGetRequest_customSsoClient() throws IOException {
        final String fakeSsoClient = "fake_sso_client";
        mTransport = new SsoClientTransport() {
            @Override
            protected LowLevelHttpRequest buildRequest(String method, String url)
                    throws IOException {
                return new SsoClientHttpRequest(method, url, fakeSsoClient) {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }
                };
            }
        };
        // Create a mock response
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStdout("HTTP/1.1 200 OK\r\n\r\nHello world");

        // We expect a call to sso_client
        doReturn(result).when(mMockRunUtil).runTimedCmd(Mockito.anyLong(), Mockito.any());

        // Actually make the call
        HttpResponse response = mTransport.createRequestFactory()
                .buildRequest("GET", new GenericUrl(EXAMPLE_URL), null)
                .execute();

        // Verify that sso_client was called with the correct arguments
        ArgumentCaptor<String> capture = ArgumentCaptor.forClass(String.class);
        verify(mMockRunUtil).runTimedCmd(Mockito.anyLong(), capture.capture());

        List<String> args = capture.getAllValues();
        assertEquals(fakeSsoClient, args.get(0));
        assertEquals(EXAMPLE_URL, args.get(args.size() - 1));
        assertTrue(args.contains("--method"));
        assertEquals("GET", args.get(args.indexOf("--method") + 1));

        // Verify that the response was parsed correctly
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getStatusMessage());
        assertEquals("Hello world", StreamUtil.getStringFromStream(response.getContent()));
    }
}
