// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.notifilter;

import com.android.notifilter.TemplateRenderer;
import com.android.notifilter.common.IDatastore;
import com.android.notifilter.common.IDatastore.Notif;
import com.android.notifilter.common.IEmail;
import com.android.notifilter.common.IEmail.Message;
import com.android.notifilter.common.MultiMap;
import com.android.notifilter.common.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kxml2.io.KXmlSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A class to represent an Android Build
 */
public class Build {
    public Integer bid = null;
    public String branch = null;
    public String branchDisplay = null;
    public String repoInitBranch = null;
    public List<Change> changes = null;

    /**
     * Instantiate a sequence of {@link Build} instances from their {@code JSON} representation
     * @return {@code null} if the n case of any parse error, or a {@link List} of {@link Build}s
     *         otherwise.
     */
    public static List<Build> fromJson(JSONObject json) {
        // Note that we shadow a bunch of class members in this method.  Because the method is
        // static, we cannot access the class members anyway.

        try {
            // Start off by parsing out all the changes
            JSONArray jsonData = json.getJSONArray("data");
            final MultiMap<Integer, Change> changes = new MultiMap<Integer, Change>();

            for (int i = 0; i < jsonData.length(); ++i) {
                JSONObject jsonChange = jsonData.getJSONObject(i);
                final Change change = Change.fromJson(jsonChange);
                if (change == null) {
                    // parse error; fail fast, to avoid showing incomplete data
                    return null;
                }

                changes.put(change.bid, change);
            }

            // package the changes up into Builds
            final List<Build> builds = new ArrayList<Build>(changes.size());
            final String branch = json.getString("branch");
            final String branchDisplay = json.getString("branch_display");
            final String repoInitBranch = json.getString("repo_init_branch");

            final List<Integer> bids = new ArrayList<Integer>(changes.keySet());
            Collections.sort(bids);  // sorts in-place
            for (Integer bid : bids) {
                final Build b = new Build();
                b.bid = bid;
                b.branch = branch;
                b.branchDisplay = branchDisplay;
                b.repoInitBranch = repoInitBranch;
                b.changes = changes.get(bid);

                builds.add(b);
            }

            return builds;
        } catch (JSONException e) {
            Util.debug("Failed to parse Build from JSON: %s", json.toString());
            Util.debug("Exception was: %s", e.toString());
            return null;
        }
    }

    /**
     * Injects the contents of this {@link Build} into the specified xml document, inside
     * of a {@code &lt;build&gt;} tag.
     * @throws IOException if a catastrophic serialization error occurred.  Note that the
     *         KXmlSerializer should be in a usable state, even if an IOException is thrown.
     * @return {@code true} if serialization succeeded; {@code false} if it failed
     */
    public boolean serializeToXml(KXmlSerializer xml, String ns) throws IOException {
        if (bid == null || branch == null || changes == null) return false;

        boolean retVal = true;
        xml.startTag(ns, "build");
        try {
            xml.attribute(ns, "bid", bid.toString());
            xml.attribute(ns, "branch", branch);
            xml.attribute(ns, "branchDisplay", branchDisplay);
            xml.attribute(ns, "repoInitBranch", repoInitBranch);

            for (Change change : changes) {
                change.serializeToXml(xml, ns);
            }
        } catch (IOException e) {
            // Try to catch this, so that we can give the xml back intact
            Util.debug("Failed to serialize Build contents as XML: %s", e.toString());
            retVal = false;
        }

        xml.endTag(ns, "build");

        return retVal;
    }
}
