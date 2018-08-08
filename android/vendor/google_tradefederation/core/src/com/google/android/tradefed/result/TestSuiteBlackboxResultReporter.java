// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.suite.ITestSuite;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.MultiMap;

import com.google.android.tradefed.result.BlackboxPostUtil.TestResultsBuilder;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A variant of {@link BaseBlackboxResultReporter} that leverages metadata in
 * {@link ConfigurationDescriptor} of an {@link ITestSuite} for reporting purpose. The test results
 * for test suite execution will be split and posted with test suite name consists of a primary
 * suite name, and optionally test module name, metadata field from module config, module ABI and
 * so on.
 */
@OptionClass(alias = "test-suite-blackbox-reporter")
public class TestSuiteBlackboxResultReporter extends BaseBlackboxResultReporter {

    private static final String PRIMARY_SUITE_NAME = "<PRIMARY_SUITE_NAME>";
    private static final String MODULE_NAME = "<MODULE_NAME>";
    private static final String MODULE_ABI = "<MODULE_ABI>";
    private static final String MODULE_META = "<MODULE_META[metadata field name]>";
    static final Pattern MODULE_META_PATTERN = Pattern.compile(
            "<MODULE_META\\[(.+?)\\]>");

    @Option(name = "primary-suite-name", description = "The primary test suite name, will fallback"
            + " to test tag if not specified")
    private String mPrimarySuiteName = null;

    @Option(name = "suite-name-formatter", description = "A format string to decide actual test "
            + "suite name used during posting, where supported place holders will be replaced with "
            + "actual values based on invocation context; supported place holders are: "
            + PRIMARY_SUITE_NAME + " -- the primary suite name used for reporting, "
            + MODULE_NAME + " -- name of the module that the test case belongs to, "
            + MODULE_ABI + " -- ABI of the module that the test case belongs to, "
            + MODULE_META + " -- value of a named metadata field as provided by module config.")
    private String mSuiteNameFormatter = PRIMARY_SUITE_NAME;

    /** keeps track of staged test requests for various test suites */
    Map<String, TestResultsBuilder> mTestResultsBuilders = new HashMap<>();
    /** keeps track of test requests that are finalized for posting */
    List<TestResultsBuilder> mQueuedRequests = new ArrayList<>();
    /** Contains the context of the module in progress */
    private IInvocationContext mCurrentContext;

    @VisibleForTesting
    String getSuiteNameFormatter() {
        return mSuiteNameFormatter;
    }

    @VisibleForTesting
    void setSuiteNameFormatter(String suiteNameFormatter) {
        mSuiteNameFormatter = suiteNameFormatter;
    }

    @VisibleForTesting
    void setPrimarySuiteName(String primarySuiteName) {
        mPrimarySuiteName = primarySuiteName;
    }

    /**
     * Formulates a suitable test suite name based on info from current {@link IInvocationContext}
     */
    @VisibleForTesting
    String getTestSuiteName(IInvocationContext context) {
        String formatter = getSuiteNameFormatter();
        formatter = formatter.replaceAll(PRIMARY_SUITE_NAME, mPrimarySuiteName);
        if (formatter.contains(MODULE_NAME)) {
            List<String> moduleNames = context.getAttributes().get(ModuleDefinition.MODULE_NAME);
            if (moduleNames == null || moduleNames.isEmpty()) {
                throw new IllegalStateException("suite-name-formatter contains " + MODULE_NAME
                        + " but no module name specified in module invocation context");
            }
            if (moduleNames.size() > 1) {
                CLog.w("More than one module name specified (using first one): %s",
                        moduleNames.toString());
            }
            formatter = formatter.replaceAll(MODULE_NAME, moduleNames.get(0));
        }
        if (formatter.contains(MODULE_ABI)) {
            List<String> moduleAbis = context.getAttributes().get(ModuleDefinition.MODULE_ABI);
            if (moduleAbis.isEmpty()) {
                throw new IllegalStateException("suite-name-formatter contains " + MODULE_ABI
                        + " but no module ABI specified in module invocation context.");
            }
            if (moduleAbis.size() > 1) {
                CLog.w("More than one module ABI specified (using first one): %s",
                        moduleAbis.toString());
            }
            formatter = formatter.replaceAll(MODULE_ABI, moduleAbis.get(0));
        }
        // resolve the metadata placeholers
        formatter = resolveMetadataFields(formatter, context);
        return formatter;
    }

    String resolveMetadataFields(String formatter, IInvocationContext context) {
        Matcher matcher = MODULE_META_PATTERN.matcher(formatter);
        MultiMap<String, String> metadata = null;
        while (matcher.find()) {
            if (metadata == null) {
                // lazy initialization: don't reach into context if we don't need to
                ConfigurationDescriptor descriptor = context.getConfigurationDescriptor();
                if (descriptor == null) {
                    throw new IllegalStateException(
                            "module invocation context does not have a configuration descriptor");
                }
                metadata = descriptor.getAllMetaData();
                if (metadata == null) {
                    throw new IllegalStateException(
                            "module invocation context does not have metadata associated");
                }
            }
            // first extract the metadata name
            String metadataName = matcher.group(1);
            List<String> metadataValues = metadata.get(metadataName);
            if (metadataValues == null || metadataValues.isEmpty()) {
                throw new IllegalStateException("suite name formatter contains " + matcher.group(0)
                + " but no metadata name with name \"" + metadataName + "\" is found in module "
                        + "invocation context");
            }
            if (metadataValues.size() > 1) {
                CLog.w("More than one module metadata \"%s\" specified (using first one): %s",
                        metadataName, metadataValues.toString());
            }
            formatter = formatter.substring(0, matcher.start()) + metadataValues.get(0)
                + formatter.substring(matcher.end());
            // look for next occurrence
            matcher = MODULE_META_PATTERN.matcher(formatter);
        }
        return formatter;
    }

    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
        mCurrentContext = moduleContext;
    }

    @Override
    public void testModuleEnded() {
        mCurrentContext = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String name, int numTests) {
        if (mDisable) {
            return;
        }
        super.testRunStarted(name, numTests);
        initPostRequestIfNecessary();
    }

    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        if (mDisable) {
            return;
        }
        // try to do an init here if necessary, helps with handling of malformed posting calls
        // (to keep behavior inline with BlackboxResultReporter)
        initPostRequestIfNecessary();
        super.testEnded(test, testMetrics);
    }

    void initPostRequestIfNecessary() {
        // TODO: For compatibility we keep the getModuleInvocationContext for now but should be
        // removed.
        // when test run is started, set the current result set based on (module) invocation context
        IInvocationContext context = getInvocationContext().getModuleInvocationContext();
        if (context == null) {
            context = mCurrentContext;
            if (context == null) {
                throw new IllegalStateException("module invocation context is null");
            }
        }
        setCurrentRequestBuilder(getOrCreatePostRequestForTestSuite(getTestSuiteName(context)));
    }

    @Override
    public void invocationStarted(IInvocationContext context) {
        super.invocationStarted(context);
        if (mPrimarySuiteName == null) {
            mPrimarySuiteName = getInvocationContext().getTestTag();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        finalizePostRequest();
        super.invocationEnded(elapsedTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    TestResultsBuilder getOrCreatePostRequestForTestSuite(String testSuite) {
        TestResultsBuilder request = mTestResultsBuilders.get(testSuite);
        if (request == null) {
            CLog.v("Creating new TestResultsBuilder for test suite %s", testSuite);
            request = createPostRequestForTestSuite(testSuite);
            mTestResultsBuilders.put(testSuite, request);
        }
        return request;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    List<TestResultsBuilder> getRequestBuilders() {
        return mQueuedRequests;
    }

    @Override
    void finalizePostRequest() {
        if (!mQueuedRequests.isEmpty()) {
            throw new IllegalStateException("Queued test requests already exist, duplicate calls of"
                    + "finalizePostRequest()?");
        }
        for (TestResultsBuilder request : mTestResultsBuilders.values()) {
            request.setBuildInfo(getInvocationContext().getBuildInfos().get(0));
            mQueuedRequests.add(request);
        }
        setCurrentRequestBuilder(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void enqueuCurrentRequestBuilder(TestResultsBuilder testResultsBuilder) {
        throw new UnsupportedOperationException("All requests should be enqueued at the end of "
                + "invocation via #finalizePostRequest()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void clearTestResultsBuilds() {
        mTestResultsBuilders.clear();
        mQueuedRequests.clear();
    }
}
