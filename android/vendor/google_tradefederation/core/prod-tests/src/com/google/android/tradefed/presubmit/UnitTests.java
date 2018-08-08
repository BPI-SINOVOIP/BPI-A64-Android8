// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.presubmit;

import com.google.android.asit.DeviceBootTestTest;
import com.google.android.asit.GceBootTestTest;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * A test suite for all Presubmit Validation for Trade Federation.
 *
 * <p>All tests listed here should be self-contained, and do not require any external dependencies.
 */
@RunWith(Suite.class)
@SuiteClasses({
    DeviceBootTestTest.class,
    DupFileTest.class,
    GceBootTestTest.class,
    UnitTestConfigValidationTest.class,
})
public class UnitTests {
    // empty on purpose
}
