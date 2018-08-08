// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.notifilter;

import org.json.JSONArray;
import org.json.JSONObject;

import junit.framework.TestCase;

import java.util.List;

/**
 * Unit tests for {@link Change}
 */
public class ChangeTest extends TestCase {
    public void testParseChange() throws Exception {
        // sample data excerpted from
        // http://android-build.corp.google.com/repo.html?last_bid=986350&bid=990887&branch=jb-ub-now-kermit-release&output=json

        final String json =
                "{\"is_first_parent\":false,\"bid\":\"990887\",\"subject\":\"Merge \\\"Import t" +
                "ranslations. DO NOT MERGE\\\" into jb-ub-now-kermit\",\"project\":\"platform\\/" +
                "vendor\\/unbundled_google\\/packages\\/GoogleSearch\",\"author_name\":\"Baligh " +
                "Uddin\",\"sha1\":\"9483c58ef15db806de5d569958fc49825d3bb789\",\"author_email\":" +
                "\"baligh@google.com\",\"original_cl\":0}";

        final JSONObject jsonObject = new JSONObject(json);
        final Change c = Change.fromJson(jsonObject);
        assertNotNull(c);
        assertEquals(990887, c.bid.intValue());
        assertEquals("platform/vendor/unbundled_google/packages/GoogleSearch", c.project);
        assertEquals(false, c.firstParent.booleanValue());
        assertEquals("9483c58ef15db806de5d569958fc49825d3bb789", c.sha1);
        assertEquals("Baligh Uddin", c.authorName);
        assertEquals("baligh@google.com", c.authorEmail);
        assertEquals("Merge \"Import translations. DO NOT MERGE\" into jb-ub-now-kermit",
                c.subject);
        assertEquals(0, c.originalCL.intValue());
    }
}
