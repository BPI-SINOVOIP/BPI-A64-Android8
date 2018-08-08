// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed;

import com.google.android.tradefed.build.AppLaunchControlProviderTest;
import com.google.android.tradefed.build.AppWithDeviceLaunchControlProviderTest;
import com.google.android.tradefed.build.BigStoreFileDownloaderTest;
import com.google.android.tradefed.build.BuildBlacklistTest;
import com.google.android.tradefed.build.CtsDeviceLaunchControlProviderTest;
import com.google.android.tradefed.build.CtsLaunchControlProviderTest;
import com.google.android.tradefed.build.DeviceLaunchControlProviderTest;
import com.google.android.tradefed.build.DeviceWithAppLaunchControlProviderTest;
import com.google.android.tradefed.build.KernelBuildProviderTest;
import com.google.android.tradefed.build.KernelDeviceLaunchControlProviderTest;
import com.google.android.tradefed.build.LCUtilTest;
import com.google.android.tradefed.build.LaunchControlProviderTest;
import com.google.android.tradefed.build.MultiAppLaunchControlProviderTest;
import com.google.android.tradefed.build.RemoteBuildInfoTest;
import com.google.android.tradefed.build.RemoteKernelBuildInfoTest;
import com.google.android.tradefed.build.SdkLaunchControlProviderTest;
import com.google.android.tradefed.build.SdkToolsLaunchControlProviderTest;
import com.google.android.tradefed.build.SsoClientHttpHelperTest;
import com.google.android.tradefed.build.TestlogProviderTest;
import com.google.android.tradefed.build.TfLaunchControlProviderTest;
import com.google.android.tradefed.cluster.ClusterCommandSchedulerTest;
import com.google.android.tradefed.cluster.ClusterDeviceMonitorTest;
import com.google.android.tradefed.cluster.ClusterEventUploaderTest;
import com.google.android.tradefed.device.FastbootMultiDeviceRecoveryTest;
import com.google.android.tradefed.device.GceManagerTest;
import com.google.android.tradefed.device.GceSshTunnelMonitorTest;
import com.google.android.tradefed.device.GoogleDeviceSelectionOptionsTest;
import com.google.android.tradefed.device.NcdDeviceRecoveryTest;
import com.google.android.tradefed.device.RemoteAndroidVirtualDeviceTest;
import com.google.android.tradefed.device.StaticDeviceInfoTest;
import com.google.android.tradefed.device.UsbResetMultiDeviceRecoveryTest;
import com.google.android.tradefed.result.AndroidBuildApiLogSaverTest;
import com.google.android.tradefed.result.AndroidBuildResultReporterTest;
import com.google.android.tradefed.result.BaseBlackboxResultReporterTest;
import com.google.android.tradefed.result.BlackboxCodeCoverageReporterTest;
import com.google.android.tradefed.result.BlackboxPostUtilTest;
import com.google.android.tradefed.result.BlackboxResultReporterTest;
import com.google.android.tradefed.result.ColossusLogSaverTest;
import com.google.android.tradefed.result.CompatibilityResultReporterTest;
import com.google.android.tradefed.result.CoverageMetadataCollectorTest;
import com.google.android.tradefed.result.EmmaPackageSummaryParserTest;
import com.google.android.tradefed.result.InspectBugResultReporterTest;
import com.google.android.tradefed.result.JacocoXmlHandlerTest;
import com.google.android.tradefed.result.KernelTestResultReporterTest;
import com.google.android.tradefed.result.LaunchControlResultReporterTest;
import com.google.android.tradefed.result.NotifilterResultReporterTest;
import com.google.android.tradefed.result.SpongeResultReporterTest;
import com.google.android.tradefed.result.StatusTrackingEmailResultReporterTest;
import com.google.android.tradefed.result.TestSuiteBlackboxResultReporterTest;
import com.google.android.tradefed.sandbox.GoogleTradefedSandboxTest;
import com.google.android.tradefed.targetprep.AndroidThingsDeviceFlasherTest;
import com.google.android.tradefed.targetprep.CreateUserPreparerTest;
import com.google.android.tradefed.targetprep.CrespoDeviceFlasherTest;
import com.google.android.tradefed.targetprep.GceAvdPreparerTest;
import com.google.android.tradefed.targetprep.GoogleAccountPreparerTest;
import com.google.android.tradefed.targetprep.GoogleDeviceFlashPreparerTest;
import com.google.android.tradefed.targetprep.GoogleDeviceSetupTest;
import com.google.android.tradefed.targetprep.InstallCompanionAppApkSetupTest;
import com.google.android.tradefed.targetprep.InstallGmsCoreApkSetupTest;
import com.google.android.tradefed.targetprep.InstallGsaApkSetupTest;
import com.google.android.tradefed.targetprep.NakasiDeviceFlasherTest;
import com.google.android.tradefed.targetprep.OtaFaultInjectionPreparerTest;
import com.google.android.tradefed.targetprep.PrimeGsmDeviceFlasherTest;
import com.google.android.tradefed.targetprep.RazorDeviceFlasherTest;
import com.google.android.tradefed.targetprep.SholesDeviceFlasherTest;
import com.google.android.tradefed.targetprep.StingrayDeviceFlasherTest;
import com.google.android.tradefed.targetprep.TungstenDeviceFlasherTest;
import com.google.android.tradefed.targetprep.UnbundledAppSetupTest;
import com.google.android.tradefed.targetprep.clockwork.ClockworkCompanionPreparerTest;
import com.google.android.tradefed.targetprep.clockwork.ClockworkDeviceSetupTest;
import com.google.android.tradefed.testtype.ClockworkTestTest;
import com.google.android.tradefed.testtype.CtsTestLauncherTest;
import com.google.android.tradefed.testtype.host.ArtTestlogForwarderTest;
import com.google.android.tradefed.testtype.host.GTestTestlogForwarderTest;
import com.google.android.tradefed.testtype.host.GradleTestlogForwarderTest;
import com.google.android.tradefed.testtype.host.JacocoLogForwarderTest;
import com.google.android.tradefed.testtype.host.PythonUnitTestlogForwarderTest;
import com.google.android.tradefed.testtype.host.RobolectricTestlogForwarderTest;
import com.google.android.tradefed.util.AttenuatorControllerTest;
import com.google.android.tradefed.util.AttenuatorUtilTest;
import com.google.android.tradefed.util.GceAvdInfoTest;
import com.google.android.tradefed.util.GceRemoteCmdFormatterTest;
import com.google.android.tradefed.util.GoogleAccountUtilTest;
import com.google.android.tradefed.util.MultiChannelAttenuatorUtilTest;
import com.google.android.tradefed.util.PublicApkUtilTest;
import com.google.android.tradefed.util.RestApiHelperTest;
import com.google.android.tradefed.util.RollupClFetcherTest;
import com.google.android.tradefed.util.SsoClientOutputParserTest;
import com.google.android.tradefed.util.SsoClientTransportTest;
import com.google.android.tradefed.util.hostmetric.HostMetricAgentTest;
import com.google.android.tradefed.util.hostmetric.HostMetricMonitorTest;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * A test suite for all Google Trade Federation unit tests.
 *
 * <p>All tests listed here should be self-contained, and do not require any external dependencies.
 */
@SuppressWarnings("deprecation")
@RunWith(Suite.class)
@SuiteClasses({
    // NOTE: please keep classes sorted lexicographically in each group
    // build
    AppLaunchControlProviderTest.class,
    AppWithDeviceLaunchControlProviderTest.class,
    BigStoreFileDownloaderTest.class,
    BuildBlacklistTest.class,
    CtsDeviceLaunchControlProviderTest.class,
    CtsLaunchControlProviderTest.class,
    DeviceLaunchControlProviderTest.class,
    DeviceWithAppLaunchControlProviderTest.class,
    KernelBuildProviderTest.class,
    KernelDeviceLaunchControlProviderTest.class,
    LaunchControlProviderTest.class,
    LCUtilTest.class,
    MultiAppLaunchControlProviderTest.class,
    RemoteBuildInfoTest.class,
    RemoteKernelBuildInfoTest.class,
    SdkLaunchControlProviderTest.class,
    SdkToolsLaunchControlProviderTest.class,
    SsoClientHttpHelperTest.class,
    TestlogProviderTest.class,
    TfLaunchControlProviderTest.class,

    // command
    ClusterEventUploaderTest.class,
    ClusterCommandSchedulerTest.class,

    // cluster
    ClusterDeviceMonitorTest.class,

    // device
    GceManagerTest.class,
    GceSshTunnelMonitorTest.class,
    GoogleDeviceSelectionOptionsTest.class,
    FastbootMultiDeviceRecoveryTest.class,
    NcdDeviceRecoveryTest.class,
    RemoteAndroidVirtualDeviceTest.class,
    StaticDeviceInfoTest.class,
    UsbResetMultiDeviceRecoveryTest.class,

    // result
    AndroidBuildApiLogSaverTest.class,
    AndroidBuildResultReporterTest.class,
    BaseBlackboxResultReporterTest.class,
    BlackboxCodeCoverageReporterTest.class,
    BlackboxPostUtilTest.class,
    BlackboxResultReporterTest.class,
    TestSuiteBlackboxResultReporterTest.class,
    ColossusLogSaverTest.class,
    EmmaPackageSummaryParserTest.class,
    InspectBugResultReporterTest.class,
    JacocoXmlHandlerTest.class,
    KernelTestResultReporterTest.class,
    LaunchControlResultReporterTest.class,
    NotifilterResultReporterTest.class,
    SpongeResultReporterTest.class,
    StatusTrackingEmailResultReporterTest.class,
    CompatibilityResultReporterTest.class,
    CoverageMetadataCollectorTest.class,

    // sandbox
    GoogleTradefedSandboxTest.class,

    // targetprep
    AndroidThingsDeviceFlasherTest.class,
    ClockworkCompanionPreparerTest.class,
    CreateUserPreparerTest.class,
    CrespoDeviceFlasherTest.class,
    GceAvdPreparerTest.class,
    GoogleAccountPreparerTest.class,
    GoogleDeviceFlashPreparerTest.class,
    GoogleDeviceSetupTest.class,
    InstallCompanionAppApkSetupTest.class,
    InstallGmsCoreApkSetupTest.class,
    InstallGsaApkSetupTest.class,
    NakasiDeviceFlasherTest.class,
    OtaFaultInjectionPreparerTest.class,
    PrimeGsmDeviceFlasherTest.class,
    RazorDeviceFlasherTest.class,
    SholesDeviceFlasherTest.class,
    StingrayDeviceFlasherTest.class,
    TungstenDeviceFlasherTest.class,
    UnbundledAppSetupTest.class,

    // targetprep, clockwork
    ClockworkDeviceSetupTest.class,

    // testtype
    ClockworkTestTest.class,
    CtsTestLauncherTest.class,

    // testtype.host
    ArtTestlogForwarderTest.class,
    GradleTestlogForwarderTest.class,
    GTestTestlogForwarderTest.class,
    PythonUnitTestlogForwarderTest.class,
    JacocoLogForwarderTest.class,
    RobolectricTestlogForwarderTest.class,

    // util
    AttenuatorControllerTest.class,
    AttenuatorUtilTest.class,
    GceAvdInfoTest.class,
    GceRemoteCmdFormatterTest.class,
    GoogleAccountUtilTest.class,
    MultiChannelAttenuatorUtilTest.class,
    PublicApkUtilTest.class,
    RestApiHelperTest.class,
    RollupClFetcherTest.class,
    SsoClientOutputParserTest.class,
    SsoClientTransportTest.class,

    // util hostmetric
    HostMetricAgentTest.class,
    HostMetricMonitorTest.class,
})
public class UnitTests {
    // empty on purpose
}
