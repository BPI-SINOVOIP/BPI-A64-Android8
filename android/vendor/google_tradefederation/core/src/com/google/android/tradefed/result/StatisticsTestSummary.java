// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import com.android.tradefed.result.TestSummary;

/**
 * A more specific version of {@link TestSummary} which has spaces for a "statistics" link as well
 * as a "generic" link.  It is expected that "statistics" will show some sort of detailed machine-
 * parseable metrics for the result, and "generic" will be a top-level link to the results hosting
 * site.
 */
public class StatisticsTestSummary extends TestSummary {
    private TypedString mStatistics = null;
    private TypedString mGeneric = null;

    /**
     * Constructor relying on {@link TestSummary#TestSummary(String)}.
     */
    public StatisticsTestSummary(String summaryUri) {
        super(summaryUri);
    }

    /**
     * Constructor relying on {@link TestSummary#TestSummary(TypedString)}.
     */
    public StatisticsTestSummary(TypedString summary) {
        super(summary);
    }

    public StatisticsTestSummary(String summaryUri, String statisticsUri, String genericUri) {
        this(new TypedString(summaryUri), new TypedString(statisticsUri),
                new TypedString(genericUri));
    }

    public StatisticsTestSummary(TypedString summary, TypedString statistics, TypedString generic) {
        this(summary);
        setStatistics(statistics);
        setGeneric(generic);
    }

    public TypedString getStatistics() {
        return mStatistics;
    }
    public TypedString getGeneric() {
        return mGeneric;
    }

    public void setStatistics(TypedString statistics) {
        mStatistics = statistics;
    }
    public void setGeneric(TypedString generic) {
        mGeneric = generic;
    }

    public void setStatistics(String statisticsUri) {
        setStatistics(new TypedString(statisticsUri));
    }
    public void setGeneric(String genericUri) {
        setGeneric(new TypedString(genericUri));
    }

}

