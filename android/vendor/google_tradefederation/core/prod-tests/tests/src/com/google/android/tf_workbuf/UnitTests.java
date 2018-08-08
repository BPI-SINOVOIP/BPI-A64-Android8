// Copyright 2014 Google Inc.  All Rights Reserved.
package com.google.android.tf_workbuf;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * All unit tests for the presubmit components.
 */
public class UnitTests extends TestSuite {
    public UnitTests() {
        super();

        addTestSuite(BuildAPIHelperTest.class);
        addTestSuite(BuildAPIResultReporterTest.class);
        addTestSuite(TfWorkbufBuildProviderTest.class);
    }

    public static Test suite() {
        return new UnitTests();
    }
}
