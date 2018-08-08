// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collection;
import java.util.StringTokenizer;

/**
 * A simple blacklist to determine builds to skip
 * FIXME: move this to the open-source half; there's nothing proprietary here
 */
public class BuildBlacklist {

    private static final String ALL_FLAVORS = "*";
    /** Map of blacklisted build and the flavors */
    private Multimap<String, String> mBuildFlavorMap = LinkedListMultimap.create();
    private final long mDataTime;


    BuildBlacklist(long dataTime) {
        mDataTime = dataTime;
    }

    /**
     * Parses data containing line-separated black-listed builds.
     * <p/>
     * Valid line formats include
     * <ul>
     * <li><i>buildId</i> - blacklist build for all flavors
     * <li><i>buildId *</i> - blacklist build for all flavors
     * <li><i>buildId flavor</i> - black list build for this one specific build flavor
     * <li><i># blah</i> - comment line - ignored
     * <li><i>buildId # blah</i> - comment ignore -blacklist build for all flavors
     * </ul>
     * @param reader the {@link BufferedReader} containing black listed build data
     * @throws IOException
     */
    void parse(BufferedReader reader) throws IOException {
        String buildLine = null;
        while ((buildLine = reader.readLine()) != null) {
            StringTokenizer tokenizer = new StringTokenizer(buildLine, " \t");
            String buildId = getNextToken(tokenizer);
            if (buildId == null) {
                continue;
            }
            String flavor = getNextToken(tokenizer);
            if (flavor == null) {
                flavor = ALL_FLAVORS;
            }
            mBuildFlavorMap.put(buildId, flavor);
        }
    }

    private String getNextToken(StringTokenizer tokenizer) {
        if (!tokenizer.hasMoreTokens()) {
            return null;
        }
        String token = tokenizer.nextToken();
        if (token.startsWith("#")) {
            return null;
        }
        return token;
    }

    public boolean isBlacklistedBuild(String buildId, String flavor) {
        Collection<String> blacklistedFlavors = mBuildFlavorMap.get(buildId);
        if (blacklistedFlavors == null) {
            return false;
        } else if (blacklistedFlavors.contains(ALL_FLAVORS)) {
            return true;
        } else if (blacklistedFlavors.contains(flavor)) {
            return true;
        }
        return false;
    }

    public long getDataTime() {
        return mDataTime;
    }
}
