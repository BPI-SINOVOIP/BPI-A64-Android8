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
 * A class to represent a Gerrit Change
 */
public class Change {
    public Integer bid = null;
    public String project = null;
    public Boolean firstParent = null;
    public String sha1 = null;
    public String authorName = null;
    public String authorEmail = null;
    public String subject = null;
    public Integer originalCL = null;

    /**
     * Instantiate a {@code Change} object from its {@code JSON} representation
     * @return {@code null} in case of a parse error, a new {@code Change} otherwise.
     */
    public static Change fromJson(JSONObject json) {
        final Change c = new Change();
        try {
            c.bid = json.getInt("bid");
            c.project = json.getString("project");
            c.firstParent = json.getBoolean("is_first_parent");
            c.sha1 = json.getString("sha1");
            c.authorName = json.getString("author_name");
            c.authorEmail = json.getString("author_email");
            c.subject = json.getString("subject");
            c.originalCL = json.getInt("original_cl");
        } catch (JSONException e) {
            Util.debug("Failed to parse Change from JSON: %s", json.toString());
            Util.debug("Exception was: %s", e.toString());
            return null;
        }

        return c;
    }

    /**
     * Injects the contents of this {@link Change} into the specified xml document, inside
     * of a {@code &lt;change&gt;} tag.
     * @throws IOException if a catastrophic serialization error occurred.  Note that the
     *         KXmlSerializer should be in a usable state, even if an IOException is thrown.
     * @return {@code true} if serialization succeeded; {@code false} if it failed
     */
    public boolean serializeToXml(KXmlSerializer xml, String ns) throws IOException {
        boolean retVal = true;
        xml.startTag(ns, "change");
        try {
            xml.attribute(ns, "bid", bid.toString());
            xml.attribute(ns, "project", project);
            xml.attribute(ns, "firstParent", firstParent.toString());
            xml.attribute(ns, "sha1", sha1);
            xml.attribute(ns, "originalCL", originalCL.toString());

            // Use specific tags for these elements, to avoid potential OOM when KXmlSerializer
            // encounters certain kinds of attributes
            xml.startTag(ns, "authorName");
            xml.text(authorName);
            xml.endTag(ns, "authorName");

            xml.startTag(ns, "authorEmail");
            xml.text(authorEmail);
            xml.endTag(ns, "authorEmail");

            xml.startTag(ns, "subject");
            xml.text(subject);
            xml.endTag(ns, "subject");

        } catch (IOException e) {
            // Try to catch this, so that we can give the xml back intact
            Util.debug("Failed to serialize Change contents as XML: %s", e.toString());
            retVal = false;
        }

        xml.endTag(ns, "change");

        return retVal;
    }
}
