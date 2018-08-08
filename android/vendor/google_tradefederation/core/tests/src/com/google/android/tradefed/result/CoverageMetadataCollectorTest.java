// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import static com.google.android.tradefed.result.CoverageMetadataCollector.METADATA_COMPONENT;
import static com.google.android.tradefed.result.CoverageMetadataCollector.METADATA_COVERAGE_FILTER;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.util.MultiMap;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.testtools.coverage.aggregator.CoverageMetadataProto.CodeFilter;
import com.google.wireless.android.testtools.coverage.aggregator.CoverageMetadataProto.Component;
import com.google.wireless.android.testtools.coverage.aggregator.CoverageMetadataProto.CoverageMeasurement;
import com.google.wireless.android.testtools.coverage.aggregator.CoverageMetadataProto.CoverageMetadata;
import com.google.wireless.android.testtools.coverage.aggregator.CoverageMetadataProto.TestArtifact;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoRule;
import org.mockito.junit.MockitoJUnit;

import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;

/** Unit tests for {@link CoverageMetadataCollector}. */
@RunWith(JUnit4.class)
public class CoverageMetadataCollectorTest {

    // Fake BuildInfo
    private static final String BUILD_TYPE = "submitted";
    private static final String BUILD_ID = "1234";
    private static final String BUILD_FLAVOR = "bullhead-userdebug_coverage";
    private static final String BUILD_ATTEMPT = "1";
    private static final long TEST_RESULT_ID = 9999;
    private static final BuildInfo BUILD_INFO = buildInfo(BUILD_ID, BUILD_FLAVOR, BUILD_ATTEMPT);

    // Fake Component info
    private static final String WIFI_FILTER_STRING = "glob:**/wifi_intermediates/coverage.em";
    private static final CodeFilter WIFI_CODE_FILTER =
            CodeFilter.newBuilder().setJackFilter(WIFI_FILTER_STRING).build();
    private static final Component WIFI_COMPONENT =
            Component.newBuilder().setName("wifi").addFilters(WIFI_CODE_FILTER).build();
    private static final String VCARD_FILTER_STRING =
            "glob:**/AndroidVCard_intermediates/coverage.em";
    private static final CodeFilter VCARD_CODE_FILTER =
            CodeFilter.newBuilder().setJackFilter(VCARD_FILTER_STRING).build();
    private static final Component VCARD_COMPONENT =
            Component.newBuilder().setName("vcard").addFilters(VCARD_CODE_FILTER).build();

    // Fake invocation info
    private static final String RUN_NAME = "TestRun";
    private static final int TEST_COUNT = 5;
    private static final String MEASUREMENT_LOG_NAME = "TestRun_runtime_coverage";
    private static final ByteArrayInputStreamSource MEASUREMENT_CONTENTS =
            new ByteArrayInputStreamSource("Mi estas kovrado mezurado".getBytes(UTF_8));
    private static final long RUN_TIME = 10000;
    private static final ImmutableMap<String, String> RUN_METRICS = ImmutableMap.of();
    private static final long INVOCATION_TIME = 15000;

    // Fake test logs
    private static final String MEASUREMENT1_PATH =
            "git_oc-release/1234/apct/wifi/coverage/inv_1111/TestRun_runtime_coverage_1234.ec";
    private static final LogFile MEASUREMENT1_LOGFILE =
            new LogFile(MEASUREMENT1_PATH, "unused", false, false);
    private static final String MEASUREMENT2_PATH =
            "git_oc-release/1234/apct/wifi/coverage/inv_1111/TestRun_runtime_coverage_5678.ec";
    private static final LogFile MEASUREMENT2_LOGFILE =
            new LogFile(MEASUREMENT2_PATH, "unused", false, false);

    // Fake CoverageMeasurement
    private static final TestArtifact ARTIFACT_TEMPLATE =
            TestArtifact.newBuilder()
                    .setBuildType(BUILD_TYPE)
                    .setBuildId(BUILD_ID)
                    .setTarget(BUILD_FLAVOR)
                    .setAttemptId(BUILD_ATTEMPT)
                    .setTestResultId(TEST_RESULT_ID)
                    .build();
    private static final CoverageMeasurement MEASUREMENT1 =
            CoverageMeasurement.newBuilder()
                    .setTestArtifact(ARTIFACT_TEMPLATE.toBuilder().setResourceId(MEASUREMENT1_PATH))
                    .build();
    private static final CoverageMeasurement MEASUREMENT2 =
            CoverageMeasurement.newBuilder()
                    .setTestArtifact(ARTIFACT_TEMPLATE.toBuilder().setResourceId(MEASUREMENT2_PATH))
                    .build();

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    CoverageMetadataCollector mCollector;

    @Mock AndroidBuildApiLogSaver mLogSaver;

    @Before
    public void setup() {
        // Configure mocks.
        when(mLogSaver.getTestResultId()).thenReturn(TEST_RESULT_ID);
        mCollector = new CoverageMetadataCollector();
        mCollector.setLogSaver(mLogSaver);
    }

    @Test
    public void testGetBuildType_noPrefix() {
        // Set up test data.
        IBuildInfo buildInfo = new BuildInfo("1234", "bullhead-userdebug");

        // Verify that a non-prefixed build is "submitted".
        assertThat(CoverageMetadataCollector.getBuildType(buildInfo)).isEqualTo("submitted");
    }

    @Test
    public void testGetBuildType_externalPrefix() {
        // Set up test data.
        IBuildInfo buildInfo = new BuildInfo("E5678", "bullhead-userdebug");

        // Verify that a build prefixed with "E" is "external".
        assertThat(CoverageMetadataCollector.getBuildType(buildInfo)).isEqualTo("external");
    }

    @Test
    public void testGetBuildType_pendingPrefix() {
        // Set up test data.
        IBuildInfo buildInfo = new BuildInfo("P9876", "bullhead-userdebug");

        // Verify that a build prefixed with "P" is "pending".
        assertThat(CoverageMetadataCollector.getBuildType(buildInfo)).isEqualTo("pending");
    }

    @Test
    public void testGetBuildType_unknownPrefix() {
        // Set up test data.
        IBuildInfo buildInfo = new BuildInfo("X5432", "bullhead-userdebug");

        // Verify that unknown prefixes default to "submitted".
        assertThat(CoverageMetadataCollector.getBuildType(buildInfo)).isEqualTo("submitted");
    }

    /** Returns a {@link BuildInfo} with the given values. */
    private static BuildInfo buildInfo(String buildId, String buildFlavor) {
        BuildInfo ret = new BuildInfo(BUILD_ID, "unused");
        ret.setBuildFlavor(buildFlavor);
        return ret;
    }

    /** Returns a {@link BuildInfo} with the given values. */
    private static BuildInfo buildInfo(String buildId, String buildFlavor, String buildAttempt) {
        BuildInfo ret = buildInfo(buildId, buildFlavor);
        ret.addBuildAttribute(AndroidBuildResultReporter.BUILD_ATTEMPT_ID, buildAttempt);
        return ret;
    }

    /** Returns an {@link InvocationContext} with the given values. */
    private InvocationContext getInvocationContext(
            IBuildInfo build, MultiMap<String, String> metadata) {

        ConfigurationDescriptor configuration = new ConfigurationDescriptor();
        configuration.setMetaData(metadata);

        InvocationContext context = new InvocationContext();
        context.setConfigurationDescriptor(configuration);
        context.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, build);

        return context;
    }

    @Test
    public void testMainContext() throws IOException {
        // Set up component metadata.
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put(METADATA_COMPONENT, WIFI_COMPONENT.getName());
        String filterKey = String.format(METADATA_COVERAGE_FILTER, WIFI_COMPONENT.getName());
        WIFI_COMPONENT
                .getFiltersList()
                .stream()
                .map(CodeFilter::getJackFilter)
                .forEach(filter -> metadata.put(filterKey, filter));

        // Set up the context.
        InvocationContext mainContext = getInvocationContext(BUILD_INFO, metadata);

        // Simulate a test run.
        mCollector.invocationStarted(mainContext);
        mCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCollector.testLogSaved(
                MEASUREMENT_LOG_NAME,
                LogDataType.COVERAGE,
                MEASUREMENT_CONTENTS,
                MEASUREMENT1_LOGFILE);
        mCollector.testRunEnded(RUN_TIME, RUN_METRICS);
        mCollector.invocationEnded(INVOCATION_TIME);

        // Verify the proto.
        ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(mLogSaver).saveLogDataRaw(eq("coverage_metadata"), eq("proto"), captor.capture());
        CoverageMetadata metadataProto = CoverageMetadata.parseFrom(captor.getValue());
        assertThat(metadataProto.getComponentsList())
                .containsExactlyElementsIn(Arrays.asList(WIFI_COMPONENT));
        assertThat(metadataProto.getMeasurementsList())
                .containsExactlyElementsIn(Arrays.asList(MEASUREMENT1));
    }

    @Test
    public void testModuleContext() throws IOException {
        // Set up component metadata.
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put(METADATA_COMPONENT, WIFI_COMPONENT.getName());
        String filterKey = String.format(METADATA_COVERAGE_FILTER, WIFI_COMPONENT.getName());
        WIFI_COMPONENT
                .getFiltersList()
                .stream()
                .map(CodeFilter::getJackFilter)
                .forEach(filter -> metadata.put(filterKey, filter));

        // Set up the module context.
        InvocationContext mainContext = new InvocationContext();
        InvocationContext moduleContext = getInvocationContext(BUILD_INFO, metadata);

        // Simulate a test run.
        mCollector.invocationStarted(mainContext);
        mainContext.setModuleInvocationContext(moduleContext);
        mCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCollector.testLogSaved(
                MEASUREMENT_LOG_NAME,
                LogDataType.COVERAGE,
                MEASUREMENT_CONTENTS,
                MEASUREMENT1_LOGFILE);
        mCollector.testRunEnded(RUN_TIME, RUN_METRICS);
        mCollector.invocationEnded(INVOCATION_TIME);

        // Verify the proto.
        ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(mLogSaver).saveLogDataRaw(eq("coverage_metadata"), eq("proto"), captor.capture());
        CoverageMetadata metadataProto = CoverageMetadata.parseFrom(captor.getValue());
        assertThat(metadataProto.getComponentsList())
                .containsExactlyElementsIn(Arrays.asList(WIFI_COMPONENT));
        assertThat(metadataProto.getMeasurementsList())
                .containsExactlyElementsIn(Arrays.asList(MEASUREMENT1));
    }

    @Test
    public void testDefaultBuildAttempt() throws IOException {
        // Expected measurement.
        TestArtifact artifactWithDefaultBuildAttempt =
                MEASUREMENT1
                        .getTestArtifact()
                        .toBuilder()
                        .setAttemptId(AndroidBuildResultReporter.DEFAULT_BUILD_ATTEMPT_ID)
                        .build();
        CoverageMeasurement measurementWithDefaultAttemptId =
                CoverageMeasurement.newBuilder()
                        .setTestArtifact(artifactWithDefaultBuildAttempt)
                        .build();

        // Set up component metadata.
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put(METADATA_COMPONENT, WIFI_COMPONENT.getName());
        String filterKey = String.format(METADATA_COVERAGE_FILTER, WIFI_COMPONENT.getName());
        WIFI_COMPONENT
                .getFiltersList()
                .stream()
                .map(CodeFilter::getJackFilter)
                .forEach(filter -> metadata.put(filterKey, filter));

        // Set up the context.
        BuildInfo unknownAttemptId = buildInfo(BUILD_ID, BUILD_FLAVOR);
        InvocationContext mainContext = getInvocationContext(unknownAttemptId, metadata);

        // Simulate a test run.
        mCollector.invocationStarted(mainContext);
        mCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCollector.testLogSaved(
                MEASUREMENT_LOG_NAME,
                LogDataType.COVERAGE,
                MEASUREMENT_CONTENTS,
                MEASUREMENT1_LOGFILE);
        mCollector.testRunEnded(RUN_TIME, RUN_METRICS);
        mCollector.invocationEnded(INVOCATION_TIME);

        // Verify the proto.
        ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(mLogSaver).saveLogDataRaw(eq("coverage_metadata"), eq("proto"), captor.capture());
        CoverageMetadata metadataProto = CoverageMetadata.parseFrom(captor.getValue());
        assertThat(metadataProto.getComponentsList())
                .containsExactlyElementsIn(Arrays.asList(WIFI_COMPONENT));
        assertThat(metadataProto.getMeasurementsList())
                .containsExactlyElementsIn(Arrays.asList(measurementWithDefaultAttemptId));
    }

    @Test
    public void testMultipleCoverageMeasurements() throws IOException {
        // Set up component metadata.
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put(METADATA_COMPONENT, WIFI_COMPONENT.getName());
        String filterKey = String.format(METADATA_COVERAGE_FILTER, WIFI_COMPONENT.getName());
        WIFI_COMPONENT
                .getFiltersList()
                .stream()
                .map(CodeFilter::getJackFilter)
                .forEach(filter -> metadata.put(filterKey, filter));

        // Set up context.
        InvocationContext mainContext = getInvocationContext(BUILD_INFO, metadata);

        // Simulate a test run.
        mCollector.invocationStarted(mainContext);
        mCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCollector.testLogSaved(
                MEASUREMENT_LOG_NAME,
                LogDataType.COVERAGE,
                MEASUREMENT_CONTENTS,
                MEASUREMENT1_LOGFILE);
        mCollector.testLogSaved(
                MEASUREMENT_LOG_NAME,
                LogDataType.COVERAGE,
                MEASUREMENT_CONTENTS,
                MEASUREMENT2_LOGFILE);
        mCollector.testRunEnded(RUN_TIME, RUN_METRICS);
        mCollector.invocationEnded(INVOCATION_TIME);

        // Verify the proto.
        ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(mLogSaver).saveLogDataRaw(eq("coverage_metadata"), eq("proto"), captor.capture());
        CoverageMetadata metadataProto = CoverageMetadata.parseFrom(captor.getValue());
        assertThat(metadataProto.getComponentsList())
                .containsExactlyElementsIn(Arrays.asList(WIFI_COMPONENT));
        assertThat(metadataProto.getMeasurementsList())
                .containsExactlyElementsIn(Arrays.asList(MEASUREMENT1, MEASUREMENT2));
    }

    @Test
    public void testMultipleComponents() throws IOException {
        // Set up component metadata.
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put(METADATA_COMPONENT, WIFI_COMPONENT.getName());
        String wifiFilterKey = String.format(METADATA_COVERAGE_FILTER, WIFI_COMPONENT.getName());
        WIFI_COMPONENT
                .getFiltersList()
                .stream()
                .map(CodeFilter::getJackFilter)
                .forEach(filter -> metadata.put(wifiFilterKey, filter));
        metadata.put(METADATA_COMPONENT, VCARD_COMPONENT.getName());
        String vcardFilterKey = String.format(METADATA_COVERAGE_FILTER, VCARD_COMPONENT.getName());
        VCARD_COMPONENT
                .getFiltersList()
                .stream()
                .map(CodeFilter::getJackFilter)
                .forEach(filter -> metadata.put(vcardFilterKey, filter));

        // Set up the context.
        InvocationContext mainContext = getInvocationContext(BUILD_INFO, metadata);

        // Simulate a test run.
        mCollector.invocationStarted(mainContext);
        mCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCollector.testLogSaved(
                MEASUREMENT_LOG_NAME,
                LogDataType.COVERAGE,
                MEASUREMENT_CONTENTS,
                MEASUREMENT1_LOGFILE);
        mCollector.testRunEnded(RUN_TIME, RUN_METRICS);
        mCollector.invocationEnded(INVOCATION_TIME);

        // Verify the proto.
        ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(mLogSaver).saveLogDataRaw(eq("coverage_metadata"), eq("proto"), captor.capture());
        CoverageMetadata metadataProto = CoverageMetadata.parseFrom(captor.getValue());
        assertThat(metadataProto.getComponentsList())
                .containsExactlyElementsIn(Arrays.asList(VCARD_COMPONENT, WIFI_COMPONENT));
        assertThat(metadataProto.getMeasurementsList())
                .containsExactlyElementsIn(Arrays.asList(MEASUREMENT1));
    }

    @Test
    public void testNoComponentsConfigured() throws IOException {
        // Set up the context.
        MultiMap<String, String> metadata = new MultiMap<>();
        InvocationContext mainContext = getInvocationContext(BUILD_INFO, metadata);

        // Simulate a test run, but expect a failure during testRunStarted(..).
        mCollector.invocationStarted(mainContext);
        try {
            mCollector.testRunStarted(RUN_NAME, TEST_COUNT);
            fail("Exception not thrown");
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    @Test
    public void testComponentWithoutFilter() {
        // Set up the context.
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put(METADATA_COMPONENT, WIFI_COMPONENT.getName());
        InvocationContext mainContext = getInvocationContext(BUILD_INFO, metadata);

        // Simulate a test run, but expect a failure during testRunStarted(..).
        mCollector.invocationStarted(mainContext);
        try {
            mCollector.testRunStarted(RUN_NAME, TEST_COUNT);
            fail("Exception not thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testComponentFromCommandLine() throws IOException {
        // Set command line options.
        MultiMap<String, String> options = new MultiMap<>();
        WIFI_COMPONENT
                .getFiltersList()
                .stream()
                .forEach(filter -> options.put(WIFI_COMPONENT.getName(), filter.getJackFilter()));
        mCollector.setCoverageFilters(options);

        // Set up the context.
        MultiMap<String, String> metadata = new MultiMap<>();
        InvocationContext mainContext = getInvocationContext(BUILD_INFO, metadata);

        // Simulate a test run.
        mCollector.invocationStarted(mainContext);
        mCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCollector.testLogSaved(
                MEASUREMENT_LOG_NAME,
                LogDataType.COVERAGE,
                MEASUREMENT_CONTENTS,
                MEASUREMENT1_LOGFILE);
        mCollector.testLogSaved(
                MEASUREMENT_LOG_NAME,
                LogDataType.COVERAGE,
                MEASUREMENT_CONTENTS,
                MEASUREMENT2_LOGFILE);
        mCollector.testRunEnded(RUN_TIME, RUN_METRICS);
        mCollector.invocationEnded(INVOCATION_TIME);

        // Verify the proto.
        ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(mLogSaver).saveLogDataRaw(eq("coverage_metadata"), eq("proto"), captor.capture());
        CoverageMetadata metadataProto = CoverageMetadata.parseFrom(captor.getValue());
        assertThat(metadataProto.getComponentsList())
                .containsExactlyElementsIn(Arrays.asList(WIFI_COMPONENT));
        assertThat(metadataProto.getMeasurementsList())
                .containsExactlyElementsIn(Arrays.asList(MEASUREMENT1, MEASUREMENT2));
    }

    @Test
    public void testCommandLineComponentAlreadySet() throws IOException {
        // Set up component metadata.
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put(METADATA_COMPONENT, WIFI_COMPONENT.getName());
        String wifiFilterKey = String.format(METADATA_COVERAGE_FILTER, WIFI_COMPONENT.getName());
        WIFI_COMPONENT
                .getFiltersList()
                .stream()
                .map(CodeFilter::getJackFilter)
                .forEach(filter -> metadata.put(wifiFilterKey, filter));

        // Set command line options.
        MultiMap<String, String> options = new MultiMap<>();
        WIFI_COMPONENT
                .getFiltersList()
                .stream()
                .forEach(filter -> options.put(WIFI_COMPONENT.getName(), filter.getJackFilter()));
        mCollector.setCoverageFilters(options);

        // Set up the context.
        InvocationContext mainContext = getInvocationContext(BUILD_INFO, metadata);

        // Simulate a test run.
        mCollector.invocationStarted(mainContext);
        try {
            mCollector.testRunStarted(RUN_NAME, TEST_COUNT);
            fail("Exception not thrown");
        } catch (IllegalStateException e) {
            // Expected
        }
    }
}
