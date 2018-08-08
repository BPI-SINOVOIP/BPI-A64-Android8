// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.notifilter;

import com.android.notifilter.common.Util;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import junit.framework.TestCase;

import java.util.List;

/**
 * Unit tests for {@link Build}
 */
public class BuildTest extends TestCase {
    public void testParseFullPage() throws Exception {
        // Sample data from:
        // http://android-build.corp.google.com/repo.html?last_bid=986350&bid=990887&branch=jb-ub-now-kermit-release&output=json
        final String json = Util.getStringFromStream(
                getClass().getResourceAsStream("/sample_data/BuildTest.json"));

        final JSONObject jsonObject = new JSONObject(json);
        List<Build> builds = Build.fromJson(jsonObject);
        assertNotNull(builds);
        assertEquals(3, builds.size());

        Build b = builds.get(0);
        assertEquals(986938, b.bid.intValue());
        assertEquals("jb-ub-now-kermit-release", b.branch);
        assertEquals("UB: Google Now (kermit-release)", b.branchDisplay);
        assertEquals("jb-ub-now-kermit-release", b.repoInitBranch);
        assertEquals(10, b.changes.size());

        b = builds.get(1);
        assertEquals(989596, b.bid.intValue());
        assertEquals("jb-ub-now-kermit-release", b.branch);
        assertEquals("UB: Google Now (kermit-release)", b.branchDisplay);
        assertEquals("jb-ub-now-kermit-release", b.repoInitBranch);
        assertEquals(4, b.changes.size());

        b = builds.get(2);
        assertEquals(990887, b.bid.intValue());
        assertEquals("jb-ub-now-kermit-release", b.branch);
        assertEquals("UB: Google Now (kermit-release)", b.branchDisplay);
        assertEquals("jb-ub-now-kermit-release", b.repoInitBranch);
        assertEquals(13, b.changes.size());
    }

    /**
     * This basically demonstrates that {@link Build} doesn't need to care about invalid
     * input
     */
    public void testParseEmptyData() throws Exception {
        final String json = "";

        try {
            final JSONObject jsonObject = new JSONObject(json);
            fail("JSONException not thrown for empty input document!");
        } catch (JSONException e) {
            // expected
        }
    }
}
