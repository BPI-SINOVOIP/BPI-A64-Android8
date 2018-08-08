// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.google.android.tradefed.build.RemoteBuildInfo.InvalidResponseException;

import junit.framework.TestCase;

/**
 * Unit tests for {@link RemoteBuildInfo}.
 */
public class RemoteBuildInfoTest extends TestCase {

    /**
     * Test that {@link RemoteBuildInfo#parseRemoteBuildInfo(String)} returns null for empty input.
     */
    public void testParseRemoteBuildInfo_blank() throws InvalidResponseException {
        assertNull(RemoteBuildInfo.parseRemoteBuildInfo(""));
    }

    /**
     * Test that {@link RemoteBuildInfo#parseRemoteBuildInfo(String)} throws
     * InvalidResponseException if server returns response where buildId is missing.
     */
    public void testParseRemoteBuildInfo_missingBuild()  {
        try {
            RemoteBuildInfo.parseRemoteBuildInfo("key:value\n");
            fail("InvalidResponseException not thrown");
        } catch (InvalidResponseException e) {
            // expected
        }
    }

    /**
     * Test success case for {@link RemoteBuildInfo#getBuildId()}.
     */
    public void testGetBuildId() throws InvalidResponseException {
        RemoteBuildInfo info = RemoteBuildInfo.parseRemoteBuildInfo("bid:4\n");
        assertEquals("4", info.getBuildId());
    }

    /**
     * Test that {@link RemoteBuildInfo#parseRemoteBuildInfo(String)} returns null when the
     * input is empty.
     */
    public void testGetBuildId_emptyInput() throws InvalidResponseException {
        RemoteBuildInfo info = RemoteBuildInfo.parseRemoteBuildInfo("");
        assertNull(info);
    }

    /**
     * Test that {@link RemoteBuildInfo#parseRemoteBuildInfo(String)} returns null when the
     * input is whitespace.
     */
    public void testGetBuildId_newLines() throws InvalidResponseException {
        RemoteBuildInfo info = RemoteBuildInfo.parseRemoteBuildInfo("\r\n");
        assertNull(info);
    }

    /**
     * Test success case for get build type submitted
     */
    public void testGetBuildType_submitted() throws InvalidResponseException {
        RemoteBuildInfo info = RemoteBuildInfo.parseRemoteBuildInfo("bid:4\n");
        assertEquals(RemoteBuildInfo.BuildType.SUBMITTED.toString(),
                info.getAttribute(RemoteBuildInfo.BuildAttributeKey.BUILD_TYPE));
    }

    /**
     * Test success case for get build type pending
     */
    public void testGetBuildType_pending() throws InvalidResponseException {
        RemoteBuildInfo info = RemoteBuildInfo.parseRemoteBuildInfo("bid:P4\n");
        assertEquals(RemoteBuildInfo.BuildType.PENDING.toString(),
                info.getAttribute(RemoteBuildInfo.BuildAttributeKey.BUILD_TYPE));
    }

    /**
     * Test success case for get default build attempt id
     */
    public void testGetBuildAttemptId_default() throws InvalidResponseException {
        RemoteBuildInfo info = RemoteBuildInfo.parseRemoteBuildInfo("bid:4\n");
        assertEquals(RemoteBuildInfo.DEFAULT_BUILD_ATTEMPT_ID,
                info.getAttribute(RemoteBuildInfo.BuildAttributeKey.BUILD_ATTEMPT_ID));
    }

    /**
     * Test success case for get build attempt id
     */
    public void testGetBuildAttemptId() throws InvalidResponseException {
        RemoteBuildInfo info = RemoteBuildInfo.parseRemoteBuildInfo("bid:4\nbuild_attempt_id:0");
        assertEquals("0",
                info.getAttribute(RemoteBuildInfo.BuildAttributeKey.BUILD_ATTEMPT_ID));
    }
}
