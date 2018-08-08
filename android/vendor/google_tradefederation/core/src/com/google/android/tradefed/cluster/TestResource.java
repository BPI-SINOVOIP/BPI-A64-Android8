// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

/** A class to model a TestResource message returned by TFC API. */
public class TestResource {

    private final String mName;
    private final String mUrl;

    public TestResource(final String name, final String url) {
        mName = name;
        mUrl = url;
    }

    public String getName() {
        return mName;
    }

    public String getUrl() {
        return mUrl;
    }
}
