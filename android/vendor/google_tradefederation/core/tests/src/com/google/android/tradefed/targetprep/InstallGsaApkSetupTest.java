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
 * Unit tests for {@link InstallGsaApkSetup}
 */
@RunWith(JUnit4.class)
public class InstallGsaApkSetupTest {

    private InstallGsaApkSetup mInstallGsaApkSetup;
    private IDeviceBuildInfo mMockBuildInfo;
    private ITestDevice mMockTestDevice;
    private ITestDevice mMockTestDevice64;

    private File mTestParentDir = null;

    // File descriptor for 19 RC number
    private File mTestApkDir_19 = null;
    private File mTestFile_19 = null;

    // File descriptor for 21 RC number
    private File mTestApkDir_21 = null;
    private File mTestFile_21 = null;
    private File mTestFile64_21 = null;

    // Global option setter
    private OptionSetter mSetter = null;

    @Before
    public void setUp() throws Exception {
        mInstallGsaApkSetup = new InstallGsaApkSetup();
        mSetter = new OptionSetter(mInstallGsaApkSetup);
        mMockBuildInfo = EasyMock.createMock(IDeviceBuildInfo.class);
        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        mMockTestDevice64 = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockTestDevice.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.expect(mMockTestDevice64.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andStubReturn("SERIAL");
        EasyMock.expect(mMockTestDevice64.getSerialNumber()).andStubReturn("SERIAL");
        EasyMock.expect(mMockTestDevice.getProperty("ro.product.cpu.abilist64")).andReturn("");
        EasyMock.expect(mMockTestDevice64.getProperty("ro.product.cpu.abilist64"))
                .andReturn("arm64-v8a");
        EasyMock.expect(mMockTestDevice.getProperty("ro.product.cpu.abi")).andReturn("armeabi-v7a");

        mTestParentDir = FileUtil.createNamedTempDir(InstallGsaApkSetupTest.class.getSimpleName());
        mSetter.setOptionValue("root-folder-path", mTestParentDir.getAbsolutePath());
        mSetter.setOptionValue("version-number", "6.9");

        // Folder setup for 19 RC number
        mTestApkDir_19 = FileUtil.createNamedTempDir("InstallGsaApkSetupTest/6.9.19.release/0");
        mTestFile_19 = FileUtil.createTempFile("velvet.armeabi-v7a", ".apk", mTestApkDir_19);

        // Folder setup for 21 RC number
        mTestApkDir_21 = FileUtil.createNamedTempDir("InstallGsaApkSetupTest/6.9.21.release/0");
        mTestFile_21 = FileUtil.createTempFile("velvet.armeabi-v7a", ".apk", mTestApkDir_21);
        mTestFile64_21 = FileUtil.createTempFile("velvet.arm64-v8a", ".apk", mTestApkDir_21);
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mTestParentDir);
    }

    /**
     * Test {@link InstallGsaApkSetup#setUp(ITestDevice, IBuildInfo)} ()} by successfully install 32
     * bit apk on 32 device, which no rc number specified.
     */
    @Test
    public void testSetup_latestRCNumber()
            throws DeviceNotAvailableException, BuildError, TargetSetupError,
                    ConfigurationException {
        mSetter.setOptionValue("file-name-32", mTestFile_21.getName());
        mSetter.setOptionValue("file-name-64", mTestFile64_21.getName());
        EasyMock.expect(mMockTestDevice.installPackage(EasyMock.anyObject(),
                EasyMock.eq(true))).andReturn(null).times(1);
        EasyMock.replay(mMockTestDevice);
        mInstallGsaApkSetup.setUp(mMockTestDevice, mMockBuildInfo);
        EasyMock.verify(mMockTestDevice);
        Assert.assertEquals(mTestFile_21.getAbsolutePath(),
                mInstallGsaApkSetup.getApkFoundPath().getAbsolutePath());
    }

    /**
     * Test {@link InstallGsaApkSetup#setUp(ITestDevice, IBuildInfo)} ()} by successfully install 32
     * bit apk on 32 device, which rc number specified.
     */
    @Test
    public void testSetup_rcNumberSpecified()
            throws DeviceNotAvailableException, BuildError,
            TargetSetupError, ConfigurationException {
        mSetter.setOptionValue("rc-number", "19");
        mSetter.setOptionValue("file-name-32", mTestFile_19.getName());
        EasyMock.expect(mMockTestDevice.installPackage(EasyMock.anyObject(),
                EasyMock.eq(true))).andReturn(null).times(1);
        EasyMock.replay(mMockTestDevice);
        mInstallGsaApkSetup.setUp(mMockTestDevice, mMockBuildInfo);
        EasyMock.verify(mMockTestDevice);
        Assert.assertEquals(mTestFile_19.getAbsolutePath(),
                mInstallGsaApkSetup.getApkFoundPath().getAbsolutePath());
    }

    /**
     * Test {@link InstallGsaApkSetup#setUp(ITestDevice, IBuildInfo)} ()} by successfully install 64
     * bit apk on 64 device.
     */
    @Test
    public void testSetup_install64BitApk()
            throws DeviceNotAvailableException, BuildError, TargetSetupError,
                    ConfigurationException {
        mSetter.setOptionValue("file-name-64", mTestFile64_21.getName());
        EasyMock.expect(mMockTestDevice64.installPackage(EasyMock.anyObject(),
                EasyMock.eq(true))).andReturn(null).times(1);
        EasyMock.replay(mMockTestDevice64);
        mInstallGsaApkSetup.setUp(mMockTestDevice64, mMockBuildInfo);
        EasyMock.verify(mMockTestDevice64);
        Assert.assertEquals(mTestFile64_21.getAbsolutePath(),
                mInstallGsaApkSetup.getApkFoundPath().getAbsolutePath());
    }

    /**
     * Test {@link InstallGsaApkSetup#setUp(ITestDevice, IBuildInfo)} ()} by throwing exception due
     * to no version found.
     */
    @Test(expected = TargetSetupError.class)
    public void testSetup_noVersionFound()
            throws DeviceNotAvailableException, BuildError,
            TargetSetupError, ConfigurationException {
        mSetter.setOptionValue("version-number", "6.2");
        EasyMock.replay(mMockTestDevice64);
        mInstallGsaApkSetup.setUp(mMockTestDevice64, mMockBuildInfo);
        EasyMock.verify(mMockTestDevice64);
    }

    /**
     * Test {@link InstallGsaApkSetup#setUp(ITestDevice, IBuildInfo)} ()} by throwing exception due
     * to no rc found.
     */
    @Test(expected = TargetSetupError.class)
    public void testSetup_noRCFound()
            throws DeviceNotAvailableException, BuildError,
            TargetSetupError, ConfigurationException {
        mSetter.setOptionValue("rc-number", "55");
        EasyMock.replay(mMockTestDevice64);
        mInstallGsaApkSetup.setUp(mMockTestDevice64, mMockBuildInfo);
        EasyMock.verify(mMockTestDevice64);
    }
}
