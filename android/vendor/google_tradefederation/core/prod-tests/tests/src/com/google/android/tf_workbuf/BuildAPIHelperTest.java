// Copyright 2014 Google Inc.  All Rights Reserved.
package com.google.android.tf_workbuf;

import com.google.api.client.http.GenericUrl;
import com.android.tradefed.result.InvocationStatus;
import com.android.tradefed.targetprep.BuildError;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link BuildAPIHelper}
 */
public class BuildAPIHelperTest extends TestCase {
    private BuildAPIHelper mHelper = null;

    @Override
    public void setUp() throws Exception {
        mHelper = new BuildAPIHelper();
    }

    /**
     * Make sure that test results are summarized as expected when everything is passing.
     */
    public void testSummarizeResults_pass() {
        final String desc = "Ran 5 tests.  0 failed and 0 experienced errors.";

        final String[] result = mHelper.summarizeResults(true, desc, InvocationStatus.SUCCESS);
        assertEquals(2, result.length);
        assertEquals(BuildAPIHelper.STATUS_PASS, result[0]);
        assertEquals(desc, result[1]);
    }

    /**
     * Make sure that test results are summarized as expected when the invocation finishes
     * successfully with some test failures.
     */
    public void testSummarizeResults_fail() {
        final String desc = "Ran 5 tests.  1 failed and 0 experienced errors.";

        final String[] result = mHelper.summarizeResults(false, desc, InvocationStatus.SUCCESS);
        assertEquals(2, result.length);
        assertEquals(BuildAPIHelper.STATUS_FAIL, result[0]);
        assertEquals(desc, result[1]);
    }

    /**
     * Make sure that test results are summarized as expected when the invocation fails, but all
     * reported tests pass.
     */
    public void testSummarizeResults_invFail_testPass() {
        final Throwable cause = new RuntimeException("Oh bother...");
        final String desc = "Ran 3 tests.  0 failed and 0 experienced errors.";
        final String expSummary = desc + BuildAPIHelper.STOCK_ERROR_MSG + "\n" + cause.toString();
        final InvocationStatus status = InvocationStatus.FAILED;
        status.setThrowable(cause);

        final String[] result = mHelper.summarizeResults(true, desc, status);
        assertEquals(2, result.length);
        assertEquals(BuildAPIHelper.STATUS_ERROR, result[0]);
        assertEquals(expSummary, result[1]);
    }

    /**
     * Make sure that test results are summarized as expected when the invocation fails, but all
     * reported tests pass.  In this case, we check the case where no cause was recorded.
     */
    public void testSummarizeResults_invFail_testPass_noThrowable() {
        final String desc = "Ran 3 tests.  0 failed and 0 experienced errors.";
        final String expSummary = desc + BuildAPIHelper.STOCK_ERROR_MSG;
        final InvocationStatus status = InvocationStatus.FAILED;
        // FIXME: this shouldn't be necessary.  InvocationStatus.FAILED is modified directly when
        // FIXME: #setThrowable is called on it
        status.setThrowable(null);

        final String[] result = mHelper.summarizeResults(true, desc, status);
        assertEquals(2, result.length);
        assertEquals(BuildAPIHelper.STATUS_ERROR, result[0]);
        assertEquals(expSummary, result[1]);
    }

    /**
     * Make sure that test results are summarized as expected when the invocation fails, and some
     * of the reported tests also fail.
     */
    public void testSummarizeResults_invError_testFail() {
        final Throwable cause =
                new BuildError("I simply can't run tests without my hunny, Tigger.", null);
        final String desc = "Ran 0 tests.  0 failed and 0 experienced errors.";
        final String expSummary = desc + BuildAPIHelper.STOCK_ERROR_MSG + "\n" + cause.toString();
        final InvocationStatus status = InvocationStatus.BUILD_ERROR;
        status.setThrowable(cause);

        final String[] result = mHelper.summarizeResults(true, desc, status);
        assertEquals(2, result.length);
        assertEquals(BuildAPIHelper.STATUS_ERROR, result[0]);
        assertEquals(expSummary, result[1]);
    }

    /**
     * Make sure that query construction works as expected
     */
    public void testBuildQueryURI() {
        final String[] parts = {"a", "b", "c", "d"};
        final GenericUrl url1 = mHelper.buildQueryUri(parts, null);
        assertEquals("https://www.googleapis.com/android/internal/build/v1/builds/pending/a/b/c/d/",
                url1.build());

        final Map<String, String> opts = new HashMap<String, String>();
        opts.put("key\n1", "value&1");
        opts.put("key#2", "value=2");
        final GenericUrl url2 = mHelper.buildQueryUri(parts, opts);
        // We do this to allow either order of query params.
        // Note that we expect the query params to be URLEncoded
        final String exp2a = "https://www.googleapis.com/android/internal/build/v1/builds/" +
                "pending/a/b/c/d/?key%232=value%3D2&key%0A1=value%261";
        final String exp2b = "https://www.googleapis.com/android/internal/build/v1/builds/" +
                "pending/a/b/c/d/?key%0A1=value%261&key%232=value%3D2";
        final String obs = url2.build();
        assertTrue(String.format("Got unexpected query construction %s", obs),
                exp2a.equals(obs) || exp2b.equals(obs));
    }
}
