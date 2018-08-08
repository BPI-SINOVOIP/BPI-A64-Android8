// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.presubmit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.log.LogUtil.CLog;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test that the presubmit setup for TF won't break badly after a change by validating some base
 * components.
 */
@RunWith(JUnit4.class)
public class UnitTestConfigValidationTest {

    private static final String PRESUBMIT_LAUNCHER = "google/tf/unit-launcher";
    private static final String PRESUBMIT_REPORTER =
            "reporters=google/template/reporters/unit-reporter";
    private static final String PRESUBMIT_RUNNER = "google/tf/unit-runner";

    private static final String CTS_PRESUBMIT_LAUNCHER = "google/cts/cts-unit-launcher";
    private static final String CTS_REPORTER = "google/template/reporters/cts_v2-google-reporters";

    /**
     * Validate that the runner configuration is not broken with usual arguments.
     */
    @Test
    public void testTFPresubmitRunnerForUnitTests() {
        String[] runnerCommand = {PRESUBMIT_RUNNER, "-n", "--test-tag", "STUB", "--branch",
                "tradefed", "--build-flavor", "tradefed", "--subprocess-report-port", "9999"};
        createAndTestConfigHelper(runnerCommand, "runner configuration for TradeFed");
    }

    /**
     * Validate that the launcher configuration command to run the tests is not broken with
     * usual arguments.
     */
    @Test
    public void testTFPresubmitLauncherForUnitTests() {
        String[] presubmitCommand = {PRESUBMIT_LAUNCHER, "--template:map", PRESUBMIT_REPORTER,
                "--use-sso-client", "--protocol", "https", "--branch", "tradefed", "--build-flavor",
                "tradefed", "--build-os", "linux", "--build-id", "3498943"};
        createAndTestConfigHelper(presubmitCommand, "launcher configuration for TradeFed");
    }

    /**
     * Validate that the launcher configuration for CTS unit tests presubmit is not broken with
     * usual arguments.
     */
    @Test
    public void testCtsPresubmitLauncherForUnitTests() {
        String[] runnerCommand = {CTS_PRESUBMIT_LAUNCHER, "--template:map", PRESUBMIT_REPORTER,
                "--config-name", "cts-unit-tests", "--cts-package-name", "android-cts.zip",
                "--cts-version", "2", "--no-need-device", "--reporter-template", CTS_REPORTER,
                "--run-jar", "cts-tradefed.jar", "--test-tag", "CtsUnitTests"};
        createAndTestConfigHelper(runnerCommand, "launcher configuration for CTS");
    }

    /**
     * Validate that our usual CTS reporting configuration in the infrastructure is not broken.
     */
    @Test
    public void testCtsReporterConfiguration() {
        String[] runnerCommand = {CTS_REPORTER};
        createAndTestConfigHelper(runnerCommand, "reporting configuration for CTS");
    }

    /**
     * Helper to test the creation of a {@link IConfiguration} and test it given a command.
     */
    private void createAndTestConfigHelper(String[] runnerCommand, String message) {
        IConfigurationFactory factory = ConfigurationFactory.getInstance();
        try {
            IConfiguration config = factory.createConfigurationFromArgs(runnerCommand);
            assertEquals(1, config.getTests().size());
        } catch (ConfigurationException e) {
            CLog.e(e);
            fail(String.format("ConfigException '%s': One of your change is breaking TF presubmit "
                    + "%s", e.getMessage(), message));
        }
    }
}
