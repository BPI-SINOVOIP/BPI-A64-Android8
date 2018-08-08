// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.util.androidbuildapi;

import com.google.android.tradefed.util.androidbuildapi.AndroidBuildAPIHelper;
import com.google.android.tradefed.util.androidbuildapi.AndroidBuildTestResult;

import junit.framework.TestCase;

import org.json.JSONObject;

import java.io.File;

/**
 * Function test for {@link AndroidBuildAPIHelper}
 */
public class AndroidBuildAPIHelperFuncTest extends TestCase {
    private static final String TARGET = "tradefed";
    private static final String BUILD_ID = "2214937";
    private static final String BUILD_TYPE = "submitted";
    private static final String ATTEMPTS_ID = "latest";
    private static final String TEST_TAG = "android_build_api_helper_func_test";
    private static final String KEY_FILE_PATH = "/google/data/ro/teams/tradefed/configs/"
            + "e006c97067dbad4ecff47a965821946d2d04be57-privatekey.p12";
    private static final String SERVICE_ACCT = "717267004052-o19a1ouu8025j2iumfkniem897nu9gj8@developer.gserviceaccount.com";

    public void testInsertTestResult() throws Exception {
        // Create a new ArtifactFetcher.
        AndroidBuildAPIHelper helper = new AndroidBuildAPIHelper();
        helper.setup(SERVICE_ACCT, new File(KEY_FILE_PATH));
        AndroidBuildTestResult data = new AndroidBuildTestResult(
                TEST_TAG, BUILD_TYPE, BUILD_ID, TARGET, ATTEMPTS_ID);
        assertNull(data.id());
        assertNotNull(helper.postTestResult(data));
        assertNotNull(data.id());
    }

    public void testUpdateTestResult() throws Exception {
        AndroidBuildAPIHelper helper = new AndroidBuildAPIHelper();
        helper.setup(SERVICE_ACCT, new File(KEY_FILE_PATH));
        AndroidBuildTestResult data = new AndroidBuildTestResult(
                TEST_TAG, BUILD_TYPE, BUILD_ID, TARGET, ATTEMPTS_ID);
        data.setStatus(AndroidBuildTestResult.STATUS_IN_PROGRESS);
        assertNull(data.id());
        JSONObject foo = helper.postTestResult(data);
        assertNotNull(data.id());
        assertTrue(data.id().equals(foo.getLong("id")));
        assertEquals(AndroidBuildTestResult.STATUS_IN_PROGRESS, foo.getString("status"));
        data.setStatus(AndroidBuildTestResult.STATUS_PASS);
        foo = helper.postTestResult(data);
        assertNotNull(data.id());
        assertEquals(AndroidBuildTestResult.STATUS_PASS, foo.getString("status"));
    }

    public void testUpdateTestSummary() throws Exception {
        AndroidBuildAPIHelper helper = new AndroidBuildAPIHelper();
        helper.setup(SERVICE_ACCT, new File(KEY_FILE_PATH));
        AndroidBuildTestResult data = new AndroidBuildTestResult(
                TEST_TAG, BUILD_TYPE, BUILD_ID, TARGET, ATTEMPTS_ID);
        data.setStatus(AndroidBuildTestResult.STATUS_IN_PROGRESS);
        assertNull(data.id());
        JSONObject foo = helper.postTestResult(data);
        assertNotNull(data.id());
        assertTrue(data.id().equals(foo.getLong("id")));
        assertEquals(AndroidBuildTestResult.STATUS_IN_PROGRESS, foo.getString("status"));
        data.setStatus(AndroidBuildTestResult.STATUS_PASS);
        data.setSummary("Foo");
        foo = helper.postTestResult(data);
        assertNotNull(data.id());
        assertEquals(AndroidBuildTestResult.STATUS_PASS, foo.getString("status"));
    }
}
