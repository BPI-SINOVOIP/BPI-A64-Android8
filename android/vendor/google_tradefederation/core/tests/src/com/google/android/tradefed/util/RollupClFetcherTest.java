// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.google.api.client.http.LowLevelHttpRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit test for {@link RollupClFetcher} class. */
@RunWith(JUnit4.class)
public class RollupClFetcherTest {

    private static final String ROLLUP_RESPONSE_STRING =
            ")]}',\n{\n\"changelist\": \"158411527\"\n}\n";
    private static final String CANDIDATE_RESPONSE_STRING =
            ")]}',\n"
                    + "{\n"
                    + "  \"key\": {\n"
                    + "    \"candidateId\": \"001\",\n"
                    + "    \"releaseId\": \"002\"\n"
                    + "  }\n"
                    + "}\n";

    private SsoClientHttpRequest mockCandidateRequest;
    private SsoClientHttpRequest mockRollupRequest;
    private FakeTransport fakeTransport;
    private RollupClFetcher rollupClFetcher;

    private final class FakeTransport extends SsoClientTransport {

        final List<String> requestedUrls = new ArrayList<>();

        @Override
        protected LowLevelHttpRequest buildRequest(String method, String url) {
            requestedUrls.add(url);
            if (url.contains("/GetCandidate")) {
                return mockCandidateRequest;
            } else if (url.contains("/GetEffectiveRollupProposal")) {
                return mockRollupRequest;
            } else {
                fail("Unexpected URL to fake transport: " + url);
            }
            // Never happens
            return null;
        }
    }

    @Before
    public void setup() throws IOException {
        mockCandidateRequest = mock(SsoClientHttpRequest.class);
        mockRollupRequest = mock(SsoClientHttpRequest.class);
        fakeTransport = new FakeTransport();
        rollupClFetcher = new RollupClFetcher(fakeTransport);
    }

    @Test
    public void testValidResponse() throws RollupClFetcher.RollupClException, IOException {
        configHttpResponse(mockCandidateRequest, "OK", 200, CANDIDATE_RESPONSE_STRING);
        configHttpResponse(mockRollupRequest, "OK", 200, ROLLUP_RESPONSE_STRING);
        String actualClNumber = rollupClFetcher.fetch("project", "release", "candidate");
        assertEquals("CL number doesn't match", "158411527", actualClNumber);
    }

    @Test(expected = RollupClFetcher.RollupClException.class)
    public void testInvalidResponse_InvalidContent()
            throws IOException, RollupClFetcher.RollupClException {
        configHttpResponse(mockCandidateRequest, "OK", 200, ")]}',\nNot a valid JSON string");
        rollupClFetcher.fetch("project", "release", "candidate");
    }

    @Test(expected = RollupClFetcher.RollupClException.class)
    public void testInvalidResponse_MissingPrefixString()
            throws IOException, RollupClFetcher.RollupClException {
        configHttpResponse(mockCandidateRequest, "OK", 200, CANDIDATE_RESPONSE_STRING);
        // No prefix string on response.
        configHttpResponse(mockRollupRequest, "OK", 200, "{\n\"changelist\": \"158411527\"\n}\n");
        rollupClFetcher.fetch("project", "release", "candidate");
    }

    @Test(expected = RollupClFetcher.RollupClException.class)
    public void testFailedHttpRequest_GetCandidate()
            throws IOException, RollupClFetcher.RollupClException {
        configHttpResponse(mockCandidateRequest, "Forbidden", 403, "");
        rollupClFetcher.fetch("project", "release", "candidate");
    }

    @Test(expected = RollupClFetcher.RollupClException.class)
    public void testFailedHttpRequest_GetEffectiveRollupProposal()
            throws IOException, RollupClFetcher.RollupClException {
        configHttpResponse(mockCandidateRequest, "OK", 200, CANDIDATE_RESPONSE_STRING);
        configHttpResponse(mockRollupRequest, "Bad Request", 400, "");
        rollupClFetcher.fetch("project", "release", "candidate");
    }

    @Test
    public void testRequestedUrl() throws IOException, RollupClFetcher.RollupClException {
        configHttpResponse(mockCandidateRequest, "OK", 200, CANDIDATE_RESPONSE_STRING);
        configHttpResponse(mockRollupRequest, "OK", 200, ROLLUP_RESPONSE_STRING);
        String expectedGetCandidateUrl =
                "https://rapid.corp.google.com/action/GetCandidate?candidateName=candidate"
                        + "&releaseName=release&projectName=project";
        String expectedGetEffectiveRollupProposalUrl =
                "https://rapid.corp.google.com/action/GetEffectiveRollupProposal?"
                        + "releaseId=002&candidateId=001";
        rollupClFetcher.fetch("project", "release", "candidate");
        List<String> requestedUrls = fakeTransport.requestedUrls;
        assertEquals("There should be two requested URLs.", 2, requestedUrls.size());
        String actualGetCandidateUrl = requestedUrls.get(0);
        assertEquals(
                "Candidate URL doesn't match.", expectedGetCandidateUrl, actualGetCandidateUrl);
        String actualGetEffectiveRollupProposalUrl = requestedUrls.get(1);
        assertEquals(
                "Rollup URL doesn't match.",
                expectedGetEffectiveRollupProposalUrl,
                actualGetEffectiveRollupProposalUrl);
    }

    private void configHttpResponse(
            SsoClientHttpRequest request, String statusLine, int statusCode, String content)
            throws IOException {
        SsoClientHttpResponse response = new SsoClientHttpResponse(statusLine, statusCode, null);
        response.setContent(content);
        when(request.execute()).thenReturn(response);
    }
}
