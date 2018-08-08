// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.google.android.tradefed.build.SsoClientHttpHelper;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link SsoClientHttpHelper}
 */
public class SsoClientHttpHelperTest extends TestCase {

    private SsoClientHttpHelper mSsoClientHttpHelper = null;
    private IRunUtil mRunUtil = null;

    @Override
    public void setUp() throws Exception {
        mRunUtil = EasyMock.createMock(IRunUtil.class);
        mSsoClientHttpHelper = new SsoClientHttpHelper() {
           @Override
           public IRunUtil getRunUtil() {
               return mRunUtil;
           }
        };
    }

    public void  testDoGet() throws Exception {
        CommandResult mockResult = new CommandResult();
        mockResult.setStdout("HTTP/1.1 200 Internal Server Error\r\n\r\nCanned Successful Result");
        mockResult.setStatus(CommandStatus.SUCCESS);
        EasyMock.expect(mRunUtil.runTimedCmd(
                    EasyMock.anyInt(),
                    EasyMock.eq("sso_client"),
                    EasyMock.eq("--dump_header"),
                    EasyMock.eq("--location"),
                    EasyMock.eq("test url"),
                    EasyMock.eq("--connect_timeout"),
                    EasyMock.eq(Integer.toString(mSsoClientHttpHelper.getOpTimeout() / 1000)),
                    EasyMock.eq("--request_timeout"),
                    EasyMock.eq(Integer.toString(mSsoClientHttpHelper.getOpTimeout() / 1000))))
                        .andReturn(mockResult).times(1);
        EasyMock.replay(mRunUtil);
        assertEquals("Canned Successful Result", mSsoClientHttpHelper.doGet("test url"));
        EasyMock.verify(mRunUtil);
    }

    public void  testDoGet_successHttpHeader() throws Exception {
        CommandResult mockResult = new CommandResult();
        String response = "HTTP/1.1 200 OK\n" +
                "Cache-Control: no-cache, must-revalidate\r\n\r\n" +
                "Canned Successful Result";
        mockResult.setStdout(response);
        mockResult.setStatus(CommandStatus.SUCCESS);
        EasyMock.expect(mRunUtil.runTimedCmd(
                    EasyMock.anyInt(),
                    EasyMock.eq("sso_client"),
                    EasyMock.eq("--dump_header"),
                    EasyMock.eq("--location"),
                    EasyMock.eq("test url"),
                    EasyMock.eq("--connect_timeout"),
                    EasyMock.eq(Integer.toString(mSsoClientHttpHelper.getOpTimeout() / 1000)),
                    EasyMock.eq("--request_timeout"),
                    EasyMock.eq(Integer.toString(mSsoClientHttpHelper.getOpTimeout() / 1000))))
                        .andReturn(mockResult).times(1);
        EasyMock.replay(mRunUtil);
        assertEquals("Canned Successful Result", mSsoClientHttpHelper.doGet("test url"));
        EasyMock.verify(mRunUtil);
    }

    public void  testDoGet_serverErrorHttpHeader() throws Exception {
        CommandResult mockResult = new CommandResult();
        String response = "HTTP/1.1 500 Internal Server Error\r\n" +
                "Cache-Control: no-cache, must-revalidate\r\n\r\n" +
                "Canned Successful Result";
        mockResult.setStdout(response);
        mockResult.setStatus(CommandStatus.SUCCESS);
        EasyMock.expect(mRunUtil.runTimedCmd(
                    EasyMock.anyInt(),
                    EasyMock.eq("sso_client"),
                    EasyMock.eq("--dump_header"),
                    EasyMock.eq("--location"),
                    EasyMock.eq("test url"),
                    EasyMock.eq("--connect_timeout"),
                    EasyMock.eq(Integer.toString(mSsoClientHttpHelper.getOpTimeout() / 1000)),
                    EasyMock.eq("--request_timeout"),
                    EasyMock.eq(Integer.toString(mSsoClientHttpHelper.getOpTimeout() / 1000))))
                        .andReturn(mockResult).times(1);
        EasyMock.replay(mRunUtil);
        assertEquals("", mSsoClientHttpHelper.doGet("test url"));
        EasyMock.verify(mRunUtil);
    }

    public void  testDoGetFails() throws Exception {
        CommandResult mockResult = new CommandResult();
        mockResult.setStatus(CommandStatus.FAILED);
        EasyMock.expect(mRunUtil.runTimedCmd(
                    EasyMock.anyInt(),
                    EasyMock.eq("sso_client"),
                    EasyMock.eq("--dump_header"),
                    EasyMock.eq("--location"),
                    EasyMock.eq("test url"),
                    EasyMock.eq("--connect_timeout"),
                    EasyMock.eq(Integer.toString(mSsoClientHttpHelper.getOpTimeout() / 1000)),
                    EasyMock.eq("--request_timeout"),
                    EasyMock.eq(Integer.toString(mSsoClientHttpHelper.getOpTimeout() / 1000))))
                        .andReturn(mockResult).times(1);
        EasyMock.replay(mRunUtil);
        assertEquals("", mSsoClientHttpHelper.doGet("test url"));
        EasyMock.verify(mRunUtil);
    }

    public void testValidateAndAdjustResponse() {
        String response = "HTTP/1.1 200 Internal Server Error\r\n" +
                "Cache-Control: no-cache, must-revalidate\r\n\r\n" +
                "Canned Successful Result";
        String validatedResponse = mSsoClientHttpHelper.validateAndAdjustResponse(response);
        assertEquals("Canned Successful Result", validatedResponse);
    }

    public void testValidateAndAdjustResponse_noBody() {
        String response = "HTTP/1.1 200 Internal Server Error\r\n" +
                "Cache-Control: no-cache, must-revalidate\r\n\r\n";
        String validatedResponse = mSsoClientHttpHelper.validateAndAdjustResponse(response);
        assertEquals("", validatedResponse);
    }

    public void testValidateAndAdjustResponse_serverError() {
        String response = "HTTP/1.1 500 Internal Server Error\r\n" +
                "Cache-Control: no-cache, must-revalidate\r\n\r\n" +
                "Canned Successful Result";
        String validatedResponse = mSsoClientHttpHelper.validateAndAdjustResponse(response);
        assertEquals("", validatedResponse);
    }

    public void testValidateAndAdjustResponse_emptyResponse() {
        String response = "";
        String validatedResponse = mSsoClientHttpHelper.validateAndAdjustResponse(response);
        assertEquals("", validatedResponse);
    }

    public void testParseHttpStatusCode() {
        String statusLine = "HTTP/1.1 500 Internal Server Error\r\n";
        int code = mSsoClientHttpHelper.parseHttpStatusCode(statusLine);
        assertEquals(500, code);
    }

    public void testParseHttpStatusCode_emptyStatusLine() {
        String statusLine = "";
        int code = mSsoClientHttpHelper.parseHttpStatusCode(statusLine);
        assertEquals(200, code);
    }

    public void testParseHttpStatusCode_invalidStatus() {
        String statusLine = "Something";
        int code = mSsoClientHttpHelper.parseHttpStatusCode(statusLine);
        assertEquals(200, code);
    }

    public void testParseHttpStatusCode_invalidCode() {
        String statusLine = "Something BadCode SomeReason";
        int code = mSsoClientHttpHelper.parseHttpStatusCode(statusLine);
        assertEquals(200, code);
    }

    public void testStripResponseHeader() {
        String response = "HTTP/1.1 500 Internal Server Error\r\n" +
                "Cache-Control: no-cache, must-revalidate\r\n\r\n" +
                "Canned Successful Result";
        String validatedResponse = mSsoClientHttpHelper.stripResponseHeader(response);
        assertEquals("Canned Successful Result", validatedResponse);
    }

    public void testStripResponseHeader_badHeader() {
        String response = "HTTP/1.1 500 Internal Server Error\n" +
                "Cache-Control: no-cache, must-revalidate";
        String validatedResponse = mSsoClientHttpHelper.stripResponseHeader(response);
        assertEquals(response, validatedResponse);
    }

    public void testStripResponseHeader_emptyInput() {
        String response = "";
        String validatedResponse = mSsoClientHttpHelper.stripResponseHeader(response);
        assertEquals("", validatedResponse);
    }
}
