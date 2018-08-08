// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/**
 * Unit tests for {@link InstallCompanionAppApkSetup}
 */
@RunWith(JUnit4.class)
public class InstallCompanionAppApkSetupTest {

    private InstallCompanionAppApkSetup mInstallCompanionAppApkSetup;
    private IDeviceBuildInfo mMockBuildInfo;
    private ITestDevice mMockTestDevice;

    private File mTestParentDir = null;

    // File descriptor for latest apk
    private File mTestApkDir_latest = null;
    private File mTestFile_latest = null;

    // File descriptor for 02 RC number
    private File mTestApkDir_02 = null;
    private File mTestFile_02 = null;

    // File descriptor for 00 RC number
    private File mTestApkDir_00 = null;
    private File mTestFile_00 = null;

    // Global option setter
    private OptionSetter mSetter = null;

    @Before
    public void setUp() throws Exception {
        mInstallCompanionAppApkSetup = new InstallCompanionAppApkSetup();
        mSetter = new OptionSetter(mInstallCompanionAppApkSetup);
        mMockBuildInfo = EasyMock.createMock(IDeviceBuildInfo.class);
        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockTestDevice.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andStubReturn("SERIAL");

        mTestParentDir = FileUtil
                .createNamedTempDir(InstallCompanionAppApkSetupTest.class.getSimpleName());
        mSetter.setOptionValue("root-folder-path", mTestParentDir.getAbsolutePath());

        // Folder setup for latest apk.
        mTestApkDir_latest = FileUtil.createNamedTempDir("InstallCompanionAppApkSetupTest/latest");
        mTestFile_latest = FileUtil.createTempFile("ClockworkCompanionGoogleRelease", "apk",
                mTestApkDir_latest);

        // Folder setup for 00 RC number.
        mTestApkDir_00 = FileUtil.createNamedTempDir(
                "InstallCompanionAppApkSetupTest/clockwork.companion_20161214_220009_RC00");
        mTestFile_00 = FileUtil.createTempFile("ClockworkCompanionGoogleRelease", "apk",
                mTestApkDir_00);

        // Folder setup for 02 RC number.
        mTestApkDir_02 = FileUtil.createNamedTempDir(
                "InstallCompanionAppApkSetupTest/clockwork.companion_20161214_220029_RC02");
        mTestFile_02 = FileUtil.createTempFile("ClockworkCompanionGoogleRelease", "apk",
                mTestApkDir_02);
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mTestParentDir);
    }

    /**
     * Test {@link InstallCompanionAppApkSetup#setUp(ITestDevice, IBuildInfo)} by successfully
     * install the latest apk.
     */
    @Test
    public void testSetup_latestApk()
            throws DeviceNotAvailableException, BuildError,
            TargetSetupError, ConfigurationException {
        mSetter.setOptionValue("companion-apk-name", mTestFile_latest.getName());
        EasyMock.expect(mMockTestDevice.installPackage(EasyMock.anyObject(),
                EasyMock.eq(true))).andReturn(null).times(1);
        EasyMock.replay(mMockTestDevice);
        mInstallCompanionAppApkSetup.setUp(mMockTestDevice, mMockBuildInfo);
        EasyMock.verify(mMockTestDevice);
        Assert.assertEquals(mTestFile_latest.getAbsolutePath(),
                mInstallCompanionAppApkSetup.getApkFoundPath());
    }

    /**
     * Test {@link InstallCompanionAppApkSetup#setUp(ITestDevice, IBuildInfo)} ()} by successfully
     * install apk with version specified.
     */
    @Test
    public void testSetup_versionNumberSpecified()
            throws DeviceNotAvailableException, BuildError,
            TargetSetupError, ConfigurationException {
        mSetter.setOptionValue("version-number", "20161214");
        mSetter.setOptionValue("companion-apk-name", mTestFile_02.getName());
        EasyMock.expect(mMockTestDevice.installPackage(EasyMock.anyObject(),
                EasyMock.eq(true))).andReturn(null).times(1);
        EasyMock.replay(mMockTestDevice);
        mInstallCompanionAppApkSetup.setUp(mMockTestDevice, mMockBuildInfo);
        EasyMock.verify(mMockTestDevice);
        Assert.assertEquals(mTestFile_02.getAbsolutePath(),
                mInstallCompanionAppApkSetup.getApkFoundPath());
    }

    /**
     * Test {@link InstallCompanionAppApkSetup#setUp(ITestDevice, IBuildInfo)} ()} by successfully
     * install apk with rc specified.
     */
    @Test
    public void testSetup_rcNumberSpecified()
            throws DeviceNotAvailableException, BuildError,
            TargetSetupError, ConfigurationException {
        mSetter.setOptionValue("version-number", "20161214");
        mSetter.setOptionValue("rc-number", "0");
        mSetter.setOptionValue("companion-apk-name", mTestFile_00.getName());
        EasyMock.expect(mMockTestDevice.installPackage(EasyMock.anyObject(),
                EasyMock.eq(true))).andReturn(null).times(1);
        EasyMock.replay(mMockTestDevice);
        mInstallCompanionAppApkSetup.setUp(mMockTestDevice, mMockBuildInfo);
        EasyMock.verify(mMockTestDevice);
        Assert.assertEquals(mTestFile_00.getAbsolutePath(),
                mInstallCompanionAppApkSetup.getApkFoundPath());
    }

    /**
     * Test {@link InstallCompanionAppApkSetup#setUp(ITestDevice, IBuildInfo)} ()} by throwing
     * exception due to no version found.
     */
    @Test(expected = TargetSetupError.class)
    public void testSetup_noVersionFound()
            throws DeviceNotAvailableException, BuildError,
            TargetSetupError, ConfigurationException {
        mSetter.setOptionValue("version-number", "20161213");
        EasyMock.replay(mMockTestDevice);
        mInstallCompanionAppApkSetup.setUp(mMockTestDevice, mMockBuildInfo);
        EasyMock.verify(mMockTestDevice);
    }

    /**
     * Test {@link InstallCompanionAppApkSetup#setUp(ITestDevice, IBuildInfo)} ()} by throwing
     * exception due to no rc found.
     */
    @Test(expected = TargetSetupError.class)
    public void testSetup_noRCFound()
            throws DeviceNotAvailableException, BuildError,
            TargetSetupError, ConfigurationException {
        mSetter.setOptionValue("version-number", "20161214");
        mSetter.setOptionValue("rc-number", "8");
        EasyMock.replay(mMockTestDevice);
        mInstallCompanionAppApkSetup.setUp(mMockTestDevice, mMockBuildInfo);
        EasyMock.verify(mMockTestDevice);
    }
}
