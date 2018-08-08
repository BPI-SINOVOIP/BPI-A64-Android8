// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.oat;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.testtype.GTest;

/**
 * A Test that runs a native test package on given device with Valgrind support.
 */
@OptionClass(alias = "valgrind-gtest")
public class ValgrindGTest extends GTest {

    static final String VALGRIND_CMD = "valgrind";

    @Option(name = "valgrind",
            description = "Whether to run this test with Valgrind enabled or not.")
    private boolean mValgrind = false;

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getGTestCmdLine(String fullPath, String flags) {
        if (mValgrind) {
            return String.format("%s %s %s", VALGRIND_CMD, fullPath, flags);
        } else {
            return String.format("%s %s", fullPath, flags);
        }
    }
}
