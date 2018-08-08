// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;

/**
 * Provides access to a cached set of {@link BuildBlacklist}s such that the caches are invalidated
 * if the backing file for the specified blacklist is modified.
 */
public class BuildBlacklistFactory {

    private Map<String, BuildBlacklist> mBlacklistCache = new Hashtable<String, BuildBlacklist>();

    /**
    * Use on demand holder idiom
    * @see <a href="http://en.wikipedia.org/wiki/Singleton_pattern#The_solution_of_Bill_Pugh">
    * http://en.wikipedia.org/wiki/Singleton_pattern#The_solution_of_Bill_Pugh</a>
    */
    private static class SingletonHolder {
            public static final BuildBlacklistFactory cInstance = new BuildBlacklistFactory();
    }

    public static BuildBlacklistFactory getInstance() {
            return SingletonHolder.cInstance;
    }

    private BuildBlacklistFactory() {
    }

    public synchronized BuildBlacklist getBlacklist(File blackListFile) throws IOException {
        BuildBlacklist b = mBlacklistCache.get(blackListFile);
        if (b == null) {
            b = createBuildBlacklist(blackListFile);
        } else if (b.getDataTime() < blackListFile.lastModified()) {
            b = createBuildBlacklist(blackListFile);
        }
        mBlacklistCache.put(blackListFile.getAbsolutePath(), b);
        return b;
    }

    private BuildBlacklist createBuildBlacklist(File blackListFile) throws IOException {
        BuildBlacklist b = new BuildBlacklist(blackListFile.lastModified());
        b.parse(new BufferedReader(new FileReader(blackListFile)));
        return b;
    }
}
