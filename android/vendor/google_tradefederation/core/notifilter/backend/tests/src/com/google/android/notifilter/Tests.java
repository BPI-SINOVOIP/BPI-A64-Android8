// Copyright 2013 Google Inc. All Rights Reserved.
package com.google.android.notifilter;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * A test suite for all Google-Notifilter backend unit and functional tests
 */
public class Tests extends TestSuite {
    public Tests() {
        super();
        addTestSuite(BuildTest.class);
        addTestSuite(ChangeTest.class);
        addTestSuite(GoogleTemplatesTest.class);
    }

    public static Test suite() {
        return new Tests();
    }
}
