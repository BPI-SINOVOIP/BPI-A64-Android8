// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.MultiMap;

import com.google.android.tradefed.result.BlackboxPostUtil.TestResultsBuilder;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Unit tests for {@link TestSuiteBlackboxResultReporter}
 */
@RunWith(JUnit4.class)
public class TestSuiteBlackboxResultReporterTest extends CommonBlackboxResultReporterTest {

    private static final String BUILD_BRANCH = "branch";
    private static final String BUILD_ID = "123456";
    private static final String BUILD_FLAVOR = "build_flavor";

    private TestSuiteBlackboxResultReporter mReporter;
    private IBuildInfo mBuildInfo;
    private IInvocationContext mContext;

    @Before
    public void prepareTestSuiteBlackboxResultReporterTest() {
        mBuildInfo = new BuildInfo(BUILD_ID, null);
        mBuildInfo.setBuildBranch(BUILD_BRANCH);
        mBuildInfo.setBuildFlavor(BUILD_FLAVOR);
        mContext = new InvocationContext();
        mContext.setTestTag(TEST_TAG);
        mContext.addDeviceBuildInfo("device", mBuildInfo);
        // set a default blank module invocation context
        mContext.setModuleInvocationContext(new InvocationContext());

        mReporter = new TestSuiteBlackboxResultReporter() {
            @Override
            void postRequest(TestResultsBuilder postRequest) {
                addTestResult(postRequest);
            }
        };
        setInvocationContext(mContext);
        setResultReporter(mReporter);
    }

    @Test
    public void testModuleMetaReplacement_Single() throws Exception {
        ConfigurationDescriptor descriptor = new ConfigurationDescriptor();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "framework");
        descriptor.setMetaData(metadata);
        IInvocationContext context = new InvocationContext();
        context.setConfigurationDescriptor(descriptor);
        String formatter = "foo-<MODULE_META[component]>-bar";
        Assert.assertEquals("foo-framework-bar",
                mReporter.resolveMetadataFields(formatter, context));
    }

    @Test
    public void testModuleMetaReplacement_Multiple() throws Exception {
        TestSuiteBlackboxResultReporter reporter = new TestSuiteBlackboxResultReporter();
        ConfigurationDescriptor descriptor = new ConfigurationDescriptor();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "framework");
        metadata.put("category", "perf");
        descriptor.setMetaData(metadata);
        IInvocationContext context = new InvocationContext();
        context.setConfigurationDescriptor(descriptor);
        String formatter = "foo-<MODULE_META[component]>-<MODULE_META[category]>-bar";
        Assert.assertEquals("foo-framework-perf-bar",
                reporter.resolveMetadataFields(formatter, context));
    }

    @Test
    public void testModuleMetaReplacement_Repetition() throws Exception {
        TestSuiteBlackboxResultReporter reporter = new TestSuiteBlackboxResultReporter();
        ConfigurationDescriptor descriptor = new ConfigurationDescriptor();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "framework");
        metadata.put("component", "camera");
        descriptor.setMetaData(metadata);
        IInvocationContext context = new InvocationContext();
        context.setConfigurationDescriptor(descriptor);
        String formatter = "foo-<MODULE_META[component]>-bar";
        Assert.assertEquals("foo-framework-bar",
            reporter.resolveMetadataFields(formatter, context));
    }

    @Test
    public void testGetTesuiteName_default() throws Exception {
        mReporter.setPrimarySuiteName("foo");
        Assert.assertEquals("foo", mReporter.getTestSuiteName(new InvocationContext()));
    }

    @Test
    public void testGetTesuiteName_everything() throws Exception {
        mReporter.setPrimarySuiteName("foo");
        mReporter.setSuiteNameFormatter(
                "<PRIMARY_SUITE_NAME>-<MODULE_NAME>-<MODULE_ABI>-<MODULE_META[component]>");
        IInvocationContext context = new InvocationContext();
        context.addInvocationAttribute(ModuleDefinition.MODULE_NAME, "CtsCameraTestCases");
        context.addInvocationAttribute(ModuleDefinition.MODULE_ABI, "arm64-v8a");
        ConfigurationDescriptor descriptor = new ConfigurationDescriptor();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "camera");
        descriptor.setMetaData(metadata);
        context.setConfigurationDescriptor(descriptor);
        Assert.assertEquals("foo-CtsCameraTestCases-arm64-v8a-camera",
                mReporter.getTestSuiteName(context));
    }

    /** test that reports against multiple test suites work as intended */
    @Test
    public void testRunMultipleModules() throws Exception {

        // prepare contexts for modules
        IInvocationContext moduleContext1 = new InvocationContext();
        ConfigurationDescriptor configDesc1 = new ConfigurationDescriptor();
        MultiMap<String, String> meta1 = new MultiMap<>();
        meta1.put("component", "framework");
        configDesc1.setMetaData(meta1);
        moduleContext1.setConfigurationDescriptor(configDesc1);

        IInvocationContext moduleContext2 = new InvocationContext();
        ConfigurationDescriptor configDesc2 = new ConfigurationDescriptor();
        MultiMap<String, String> meta2 = new MultiMap<>();
        meta2.put("component", "camera");
        configDesc2.setMetaData(meta2);
        moduleContext2.setConfigurationDescriptor(configDesc2);

        IInvocationContext moduleContext3 = new InvocationContext();
        ConfigurationDescriptor configDesc3 = new ConfigurationDescriptor();
        MultiMap<String, String> meta3 = new MultiMap<>();
        meta3.put("component", "framework");
        configDesc3.setMetaData(meta3);
        moduleContext3.setConfigurationDescriptor(configDesc3);

        IInvocationContext moduleContext4 = new InvocationContext();
        ConfigurationDescriptor configDesc4 = new ConfigurationDescriptor();
        MultiMap<String, String> meta4 = new MultiMap<>();
        meta4.put("component", "camera");
        configDesc4.setMetaData(meta4);
        moduleContext4.setConfigurationDescriptor(configDesc4);

        mReporter.setPrimarySuiteName("foo");
        mReporter.setSuiteNameFormatter("<PRIMARY_SUITE_NAME>-<MODULE_META[component]>");

        mReporter.invocationStarted(mContext);

        // module 1
        mContext.setModuleInvocationContext(moduleContext1);
        mReporter.testRunStarted("run1", 2);
        mReporter.testStarted(new TestIdentifier("package1.class", "pass"));
        mReporter.testEnded(new TestIdentifier("package1.class", "pass"), Collections.emptyMap());
        mReporter.testStarted(new TestIdentifier("package1.class", "fail"));
        mReporter.testFailed(new TestIdentifier("package1.class", "fail"), "");
        mReporter.testEnded(new TestIdentifier("package1.class", "fail"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        mContext.setModuleInvocationContext(null);

        // module 2
        mContext.setModuleInvocationContext(moduleContext2);
        mReporter.testRunStarted("run2", 1);
        mReporter.testStarted(new TestIdentifier("package2.class", "pass"));
        mReporter.testEnded(new TestIdentifier("package2.class", "pass"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        mContext.setModuleInvocationContext(null);

        // module 3
        mContext.setModuleInvocationContext(moduleContext3);
        mReporter.testRunStarted("run3", 1);
        mReporter.testStarted(new TestIdentifier("package3.class", "fail"));
        mReporter.testFailed(new TestIdentifier("package3.class", "fail"), "");
        mReporter.testEnded(new TestIdentifier("package3.class", "fail"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        mContext.setModuleInvocationContext(null);

        // module 4
        mContext.setModuleInvocationContext(moduleContext4);
        mReporter.testRunStarted("run4", 3);
        mReporter.testStarted(new TestIdentifier("package4.class1", "pass"));
        mReporter.testEnded(new TestIdentifier("package4.class1", "pass"), Collections.emptyMap());
        mReporter.testStarted(new TestIdentifier("package4.class", "fail"));
        mReporter.testFailed(new TestIdentifier("package4.class", "fail"), "");
        mReporter.testEnded(new TestIdentifier("package4.class", "fail"), Collections.emptyMap());
        mReporter.testStarted(new TestIdentifier("package4.class2", "pass"));
        mReporter.testEnded(new TestIdentifier("package4.class2", "pass"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        mContext.setModuleInvocationContext(null);

        mReporter.invocationEnded(0);

        // now verify that the reporter has queued up the right number of test suites with right
        // number of test cases
        Assert.assertEquals("wrong number of queued test suites", 2, getCapturedResults().size());
        // now verify the contents
        // put all TestResultsBuilder into a map with key being their respective test suite names
        Map<String, TestResultsBuilder> results = getCapturedResults().stream().collect(
                Collectors.toMap(TestResultsBuilder::getTestSuite, Function.identity()));
        TestResultsBuilder request = results.get("foo-framework");
        Assert.assertNotNull("No test suite \"foo-framework\" in queued results", request);
        Assert.assertEquals(3, request.getResultCount());

        request = results.get("foo-camera");
        Assert.assertNotNull("No test suite \"foo-camera\" in queued results", request);
        Assert.assertEquals(4, request.getResultCount());
    }

    /**
     * test that reports against multiple test suites work as intended using the testModuleStarted
     * and testModuleEnded callbacks.
     */
    @Test
    public void testRunMultipleModules_withTestModuleStart() throws Exception {
        // Initially the module context is null.
        mContext.setModuleInvocationContext(null);
        // prepare contexts for modules
        IInvocationContext moduleContext1 = new InvocationContext();
        ConfigurationDescriptor configDesc1 = new ConfigurationDescriptor();
        MultiMap<String, String> meta1 = new MultiMap<>();
        meta1.put("component", "framework");
        configDesc1.setMetaData(meta1);
        moduleContext1.setConfigurationDescriptor(configDesc1);

        IInvocationContext moduleContext2 = new InvocationContext();
        ConfigurationDescriptor configDesc2 = new ConfigurationDescriptor();
        MultiMap<String, String> meta2 = new MultiMap<>();
        meta2.put("component", "camera");
        configDesc2.setMetaData(meta2);
        moduleContext2.setConfigurationDescriptor(configDesc2);

        IInvocationContext moduleContext3 = new InvocationContext();
        ConfigurationDescriptor configDesc3 = new ConfigurationDescriptor();
        MultiMap<String, String> meta3 = new MultiMap<>();
        meta3.put("component", "framework");
        configDesc3.setMetaData(meta3);
        moduleContext3.setConfigurationDescriptor(configDesc3);

        IInvocationContext moduleContext4 = new InvocationContext();
        ConfigurationDescriptor configDesc4 = new ConfigurationDescriptor();
        MultiMap<String, String> meta4 = new MultiMap<>();
        meta4.put("component", "camera");
        configDesc4.setMetaData(meta4);
        moduleContext4.setConfigurationDescriptor(configDesc4);

        mReporter.setPrimarySuiteName("foo");
        mReporter.setSuiteNameFormatter("<PRIMARY_SUITE_NAME>-<MODULE_META[component]>");

        mReporter.invocationStarted(mContext);

        // module 1
        mReporter.testModuleStarted(moduleContext1);
        mReporter.testRunStarted("run1", 2);
        mReporter.testStarted(new TestIdentifier("package1.class", "pass"));
        mReporter.testEnded(new TestIdentifier("package1.class", "pass"), Collections.emptyMap());
        mReporter.testStarted(new TestIdentifier("package1.class", "fail"));
        mReporter.testFailed(new TestIdentifier("package1.class", "fail"), "");
        mReporter.testEnded(new TestIdentifier("package1.class", "fail"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        mReporter.testModuleEnded();

        // module 2
        mReporter.testModuleStarted(moduleContext2);
        mReporter.testRunStarted("run2", 1);
        mReporter.testStarted(new TestIdentifier("package2.class", "pass"));
        mReporter.testEnded(new TestIdentifier("package2.class", "pass"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        mReporter.testModuleEnded();

        // module 3
        mReporter.testModuleStarted(moduleContext3);
        mReporter.testRunStarted("run3", 1);
        mReporter.testStarted(new TestIdentifier("package3.class", "fail"));
        mReporter.testFailed(new TestIdentifier("package3.class", "fail"), "");
        mReporter.testEnded(new TestIdentifier("package3.class", "fail"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        mReporter.testModuleEnded();

        // module 4
        mReporter.testModuleStarted(moduleContext4);
        mReporter.testRunStarted("run4", 3);
        mReporter.testStarted(new TestIdentifier("package4.class1", "pass"));
        mReporter.testEnded(new TestIdentifier("package4.class1", "pass"), Collections.emptyMap());
        mReporter.testStarted(new TestIdentifier("package4.class", "fail"));
        mReporter.testFailed(new TestIdentifier("package4.class", "fail"), "");
        mReporter.testEnded(new TestIdentifier("package4.class", "fail"), Collections.emptyMap());
        mReporter.testStarted(new TestIdentifier("package4.class2", "pass"));
        mReporter.testEnded(new TestIdentifier("package4.class2", "pass"), Collections.emptyMap());
        mReporter.testRunEnded(0, Collections.emptyMap());
        mReporter.testModuleEnded();

        mReporter.invocationEnded(0);

        // now verify that the reporter has queued up the right number of test suites with right
        // number of test cases
        Assert.assertEquals("wrong number of queued test suites", 2, getCapturedResults().size());
        // now verify the contents
        // put all TestResultsBuilder into a map with key being their respective test suite names
        Map<String, TestResultsBuilder> results =
                getCapturedResults()
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        TestResultsBuilder::getTestSuite, Function.identity()));
        TestResultsBuilder request = results.get("foo-framework");
        Assert.assertNotNull("No test suite \"foo-framework\" in queued results", request);
        Assert.assertEquals(3, request.getResultCount());

        request = results.get("foo-camera");
        Assert.assertNotNull("No test suite \"foo-camera\" in queued results", request);
        Assert.assertEquals(4, request.getResultCount());
    }

    /**
     * Ensure that the reporter is fully disabled and no callbacks cause exception from active
     * methods.
     */
    @Test
    public void testDisabled() throws Exception {
        OptionSetter setter = new OptionSetter(mReporter);
        setter.setOptionValue("disable", "true");
        IInvocationContext context = new InvocationContext();
        mReporter.invocationStarted(context);
        mReporter.testRunStarted("test", 1);
        TestIdentifier tid = new TestIdentifier("class", "method");
        mReporter.testStarted(tid);
        mReporter.testFailed(tid, "Oh I failed");
        mReporter.testEnded(tid, Collections.emptyMap());
        mReporter.testRunFailed("run failed");
        mReporter.testRunEnded(0l, Collections.emptyMap());
        mReporter.invocationEnded(0l);
    }
}
