// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import static com.google.android.tradefed.result.AndroidBuildResultReporter.BUILD_ATTEMPT_ID;
import static com.google.android.tradefed.result.AndroidBuildResultReporter.DEFAULT_BUILD_ATTEMPT_ID;
import static com.google.android.tradefed.result.AndroidBuildResultReporter.TEST_RESULT_ID;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.util.MultiMap;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.android.testtools.coverage.aggregator.CoverageMetadataProto.CodeFilter;
import com.google.wireless.android.testtools.coverage.aggregator.CoverageMetadataProto.Component;
import com.google.wireless.android.testtools.coverage.aggregator.CoverageMetadataProto.CoverageMeasurement;
import com.google.wireless.android.testtools.coverage.aggregator.CoverageMetadataProto.CoverageMetadata;
import com.google.wireless.android.testtools.coverage.aggregator.CoverageMetadataProto.TestArtifact;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A {@link ILogSaverListener} which collects logged coverage measurements and generates a
 * CoverageMetadataProto for processing by the Android Code Coverage service.
 */
public class CoverageMetadataCollector implements ILogSaverListener {

    @Option(
        name = "coverage-filter",
        description = "Sets the coverage filter for tests that use a shared xml configuration."
    )
    private MultiMap<String, String> mComponentFilters = new MultiMap<>();

    // Mapping of build ID to build type strings
    private static final ImmutableBiMap<String, String> PREFIX_TO_BUILD_TYPE =
            ImmutableBiMap.of("P", "pending", "E", "external");
    private static final String DEFAULT_BUILD_TYPE = "submitted";

    // Configuration Metadata Tags
    static final String METADATA_COMPONENT = "component";
    static final String METADATA_COVERAGE_FILTER = "coverage-filter:%s";

    private AndroidBuildApiLogSaver mLogSaver = null;

    // Protos to hold accumulated measurements
    private TestArtifact mTestArtifactTemplate;
    private CoverageMetadata.Builder mCoverageMetadata;

    private IInvocationContext mMainContext;

    /** {@inheritDoc} */
    @Override
    public void setLogSaver(ILogSaver logSaver) {
        if (logSaver instanceof AndroidBuildApiLogSaver) {
            mLogSaver = (AndroidBuildApiLogSaver) logSaver;
        }
        checkState(mLogSaver != null, "Only AndroidBuildApiLogSaver is supported");
    }

    /** Sets the coverage-filter option for testing. */
    @VisibleForTesting
    void setCoverageFilters(MultiMap<String, String> coverageFilters) {
        mComponentFilters = coverageFilters;
    }

    /** {@inheritDoc} */
    @Override
    public void invocationStarted(IInvocationContext context) {
        mMainContext = context;
    }

    /** {@inheritDoc} */
    @Override
    public void testRunStarted(String runName, int testCount) {
        // Use the module context if we're in one.
        IInvocationContext context = mMainContext.getModuleInvocationContext();
        if (context == null) {
            context = mMainContext;
        }

        // Initialize a TestArtifact template since most of the fields will be the same.
        IBuildInfo buildInfo = context.getBuildInfos().get(0);
        mTestArtifactTemplate =
                TestArtifact.newBuilder()
                        .setBuildType(getBuildType(buildInfo))
                        .setBuildId(checkNotNull(buildInfo.getBuildId()))
                        .setTarget(checkNotNull(buildInfo.getBuildFlavor()))
                        .setAttemptId(getBuildAttempt(buildInfo))
                        .setTestResultId(mLogSaver.getTestResultId())
                        .build();

        // Initialize a CoverageMetadata to hold the report configuration and measurements.
        mCoverageMetadata = CoverageMetadata.newBuilder();

        // Add any components from the configuration.
        ConfigurationDescriptor configuration = context.getConfigurationDescriptor();
        List<String> components = configuration.getMetaData(METADATA_COMPONENT);
        if (components != null) {
            // Only configuration metadata, or command line options can be used, not both
            checkState(
                    mComponentFilters.isEmpty(),
                    "Component defined by test configuration. --component cannot be used.");

            for (String component : components) {
                String filterKey = String.format(METADATA_COVERAGE_FILTER, component);
                List<CodeFilter> filters =
                        checkNotNull(configuration.getMetaData(filterKey), "%s not set", filterKey)
                                .stream()
                                .map(s -> CodeFilter.newBuilder().setJackFilter(s).build())
                                .collect(Collectors.toList());
                mCoverageMetadata.addComponents(
                        Component.newBuilder().setName(component).addAllFilters(filters));
            }
        }

        // Add a component based on command line parameters if set.
        for (String name : mComponentFilters.keySet()) {
            List<CodeFilter> filters =
                    ImmutableSet.copyOf(mComponentFilters.get(name))
                            .stream()
                            .map(s -> CodeFilter.newBuilder().setJackFilter(s).build())
                            .collect(Collectors.toList());
            mCoverageMetadata.addComponents(
                    Component.newBuilder().setName(name).addAllFilters(filters));
        }

        // Make sure we found at least one component.
        checkState(!mCoverageMetadata.getComponentsList().isEmpty(), "Component not set");
    }

    /** Returns the build type for the given {@link IBuildInfo}. */
    @VisibleForTesting
    static String getBuildType(IBuildInfo buildInfo) {
        String buildType = buildInfo.getBuildAttributes().get("build_type");
        if (buildType == null) {
            buildType =
                    PREFIX_TO_BUILD_TYPE.getOrDefault(
                            buildInfo.getBuildId().substring(0, 1), DEFAULT_BUILD_TYPE);
        }
        return buildType;
    }

    /** Returns the test result id for the given {@link IInvocationContext}. */
    private static String getBuildAttempt(IBuildInfo buildInfo) {
        return buildInfo
                .getBuildAttributes()
                .getOrDefault(BUILD_ATTEMPT_ID, DEFAULT_BUILD_ATTEMPT_ID);
    }

    /** Returns the test result id for the given {@link IInvocationContext}. */
    private static long getTestResultId(IInvocationContext context) {
        return Long.parseLong(context.getAttributes().get(TEST_RESULT_ID).get(0));
    }

    /** {@inheritDoc} */
    @Override
    public void testLogSaved(
            String dataName, LogDataType dataType, InputStreamSource dataStream, LogFile logFile) {
        // Accumulate coverage measurements.
        if (LogDataType.COVERAGE.equals(dataType)) {
            TestArtifact.Builder artifact =
                    mTestArtifactTemplate.toBuilder().setResourceId(logFile.getPath());
            mCoverageMetadata.addMeasurements(
                    CoverageMeasurement.newBuilder().setTestArtifact(artifact));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        // Make sure we got at least one measurement.
        checkState(
                !mCoverageMetadata.getMeasurementsList().isEmpty(),
                "Did not recieve any coverage measurements");

        // Save the completed CoverageMetadata proto.
        InputStreamSource source =
                new ByteArrayInputStreamSource(mCoverageMetadata.build().toByteArray());
        try {
            mLogSaver.saveLogDataRaw("coverage_metadata", "proto", source.createInputStream());
        } catch (IOException e) {
            CLog.e(e);
            CLog.e("Failed to save coverage metadata.");
        } finally {
            source.cancel();
        }
    }
}
