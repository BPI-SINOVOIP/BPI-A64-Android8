// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Contains mappings of GCore package -> team name, as well as a collection of the mandatory teams.
 */
@OptionClass(alias = "gcore-teams")
public class GCoreTeams {

    public static final String ALIAS = "gcore-teams";

    /**
     * A mapping of java package within GmsCore to the team name. Used in cases where one team
     * owns code in several java packages, and we want to report coverage for all the packages
     * as one category.
     * <p/>
     * If a java package name is not in this map, it will be reported to rdB using the java package
     * name.
     */
    @Option(name = "pkg-team-map", description = "translate java package to a team name",
            importance = Importance.ALWAYS, mandatory = true)
    protected Map<String, String> mPackageTeamMap = new HashMap<String, String>();

    /**
     * hack to get around misaligned display in dashboard when packages are added or removed
     * these are packages that will always get reported
     */
    @Option(name = "mandatory-team", description = "packages to report even if missing",
            importance = Importance.ALWAYS)
    protected Collection<String> mMandatoryPackages = new ArrayList<String>();

    /**
     * Gets the team name mapped to the specified package, or null if not mapped.
     *
     * @return the name of the team for the given package
     */
    public String getTeamName(String packageName) {
        return mPackageTeamMap.get(packageName);
    }

    /**
     * Gets the set of mandatory team names that should always be posting test results.
     * @return set of mandatory team names
     */
    public Set<String> getMandatoryTeams() {
        Set<String> mandatoryPackages = new HashSet<String>(mMandatoryPackages.size());
        mandatoryPackages.addAll(mMandatoryPackages);
        return mandatoryPackages;
    }
}
