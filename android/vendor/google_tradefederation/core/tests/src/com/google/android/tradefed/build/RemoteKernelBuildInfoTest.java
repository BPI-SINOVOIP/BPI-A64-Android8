// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.google.android.tradefed.build.RemoteKernelBuildInfo.InvalidResponseException;

import junit.framework.TestCase;

/**
 * Unit tests for {@link RemoteKernelBuildInfo}.
 */
public class RemoteKernelBuildInfoTest extends TestCase {
    private final static String SHA1 = "0f99f2a06508af6b739381ed79eb8a1a06667c4a";
    private final static String VALID_RESPONSE = (
            "{\"files\": [\"kernel\", \"vmlinux\", \"System.map\"], "
            + "\"sha1\": \"0f99f2a06508af6b739381ed79eb8a1a06667c4a\", "
            + "\"build-flavor\": \"mysid-userdebug\", "
            + "\"commit-time\": 1327706105, "
            + "\"short-sha1\": \"0f99f2a\", "
            + "\"url\": \"http://android-build/kernelbuilds/android-omap-tuna-3.0-master/"
                    + "0f99f2a06508af6b739381ed79eb8a1a06667c4a\", "
            + "\"build-id\": \"1\", "
            + "\"branch\": \"android-omap-tuna-3.0-master\", "
            + "\"path\": \"android-omap-tuna-3.0-master/"
                    + "0f99f2a06508af6b739381ed79eb8a1a06667c4a\", "
            + "\"test-tag\": \"kernel_smoke_tests\"}");
    private final static String VALID_RESPONSE_NO_BUILD = (
            "{\"test-tag\": \"kernel_smoke_tests\", "
            + "\"build-flavor\": \"mysid-userdebug\", "
            + "\"branch\": \"android-omap-tuna-3.0-master\", "
            + "\"build-id\": \"1\"}");

    /**
     * Test that {@link RemoteKernelBuildInfo#parseRemoteBuildInfo(String)} returns null for valid
     * input with no build to test.
     */
    public void testParseRemoteBuildInfo_noBuild() throws InvalidResponseException {
        assertNull(RemoteKernelBuildInfo.parseRemoteBuildInfo(VALID_RESPONSE_NO_BUILD));
    }

    /**
     * Test that {@link RemoteKernelBuildInfo#parseRemoteBuildInfo(String)} returns a
     * {@link RemoteKernelBuildInfo} for valid input with a build to test.
     */
    public void testParseRemoteBuildInfo_build() throws InvalidResponseException {
        RemoteKernelBuildInfo remoteInfo = RemoteKernelBuildInfo.parseRemoteBuildInfo(
                VALID_RESPONSE);
        assertEquals(SHA1, remoteInfo.getSha1());
        assertEquals("0f99f2a", remoteInfo.getShortSha1());
        assertEquals("android-omap-tuna-3.0-master/0f99f2a06508af6b739381ed79eb8a1a06667c4a/kernel",
                remoteInfo.getKernelFilePath());
        assertEquals(1327706105, remoteInfo.getCommitTime());
    }

    /**
     * Test that {@link RemoteKernelBuildInfo#parseRemoteBuildInfo(String)} throws a
     * {@link InvalidResponseException} for an empty string.
     */
    public void testParseRemoteBuildInfo_empty() {
        try {
            RemoteKernelBuildInfo.parseRemoteBuildInfo("");
            fail("Expected InvalidResponseException");
        } catch (InvalidResponseException e) {
            // Expected
        }
    }

    /**
     * Test that {@link RemoteKernelBuildInfo#parseRemoteBuildInfo(String)} throws a
     * {@link InvalidResponseException} for a bad sha1.
     */
    public void testParseRemoteBuildInfo_badSha1() {
        String response = VALID_RESPONSE.replaceAll(SHA1, "sha1");
        try {
            RemoteKernelBuildInfo.parseRemoteBuildInfo(response);
            fail("Expected InvalidResponseException");
        } catch (InvalidResponseException e) {
            // Expected
        }
    }

    /**
     * Test that {@link RemoteKernelBuildInfo#parseRemoteBuildInfo(String)} throws a
     * {@link InvalidResponseException} when missing a required key.
     */
    public void testParseRemoteBuildInfo_missingKeys() {
        String response = VALID_RESPONSE.replaceAll("\"commit-time\"", "\"not-commit-time\"");
        try {
            RemoteKernelBuildInfo.parseRemoteBuildInfo(response);
            fail("Expected InvalidResponseException");
        } catch (InvalidResponseException e) {
            // Expected
        }
    }

    /**
     * Test that {@link RemoteKernelBuildInfo#parseRemoteBuildInfo(String)} throws a
     * {@link InvalidResponseException} when missing the kernel file.
     */
    public void testParseRemoteBuildInfo_missingKernel() {
        String response = VALID_RESPONSE.replaceAll("\\[\"kernel\", \"vmlinux\", \"System.map\"\\]",
                "[\"vmlinux\", \"System.map\"]");
        try {
            RemoteKernelBuildInfo.parseRemoteBuildInfo(response);
            fail("Expected InvalidResponseException");
        } catch (InvalidResponseException e) {
            // Expected
        }
    }

    /**
     * Test that {@link RemoteKernelBuildInfo#parseRemoteBuildInfo(String)} throws a
     * {@link InvalidResponseException} when a non-JSON string is encountered.
     */
    public void testParseRemoteBuildInfo_malformedJson() {
        try {
            RemoteKernelBuildInfo.parseRemoteBuildInfo("{");
            fail("Expected InvalidResponseException");
        } catch (InvalidResponseException e) {
            // Expected
        }
    }

    /**
     * Test that {@link RemoteKernelBuildInfo#parseRemoteBuildInfo(String)} throws a
     * {@link InvalidResponseException} when a wrong type is encountered.
     */
    public void testParseRemoteBuildInfo_classCastExcetpion() {
        String response = VALID_RESPONSE.replaceAll("\"commit-time\": 1327706105",
                "\"commit-time\": \"time\"");
        try {
            RemoteKernelBuildInfo.parseRemoteBuildInfo(response);
            fail("Expected InvalidResponseException");
        } catch (InvalidResponseException e) {
            // Expected
        }
    }
}
