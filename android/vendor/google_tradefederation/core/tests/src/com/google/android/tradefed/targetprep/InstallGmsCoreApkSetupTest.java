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
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link InstallGmsCoreApkSetup} */
@RunWith(JUnit4.class)
public class InstallGmsCoreApkSetupTest {

    private InstallGmsCoreApkSetup mInstallGmsCoreApkSetup;
    private IDeviceBuildInfo mMockBuildInfo;
    private ITestDevice mMockTestDevice;
    private ITestDevice mMockTestDevice64;
    private ITestDevice mMockTestDeviceLmp;

    private File mTestParentDir = null;

    private File mTestSubFolder_20161213_rc02_suffix = null;
    private File mTestSubFolder_20161213_rc01 = null;
    private File mTestSubFolder_20161214_rc02 = null;
    private File mTestSubFolder_20161214_rc01 = null;
    private File mTestSubFolder_20161214_rc03 = null;
    private File mTestSubFolder_v9_rc21 = null;

    private File mTestApk32 = null;
    private File mTestApk64 = null;
    private File mTestApkLmp = null;
    private File mTestModule = null;

    // Global option setter
    private OptionSetter mSetter = null;

    @Before
    public void setUp() throws Exception {
        mInstallGmsCoreApkSetup = new InstallGmsCoreApkSetup();
        mSetter = new OptionSetter(mInstallGmsCoreApkSetup);
        mMockBuildInfo = EasyMock.createMock(IDeviceBuildInfo.class);

        // 32 bit device
        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockTestDevice.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andStubReturn("SERIAL");
        EasyMock.expect(mMockTestDevice.getProperty("ro.product.cpu.abilist64")).andReturn("");
        EasyMock.expect(mMockTestDevice.getProperty("ro.product.cpu.abi")).andReturn("armeabi-v7a");
        EasyMock.expect(mMockTestDevice.getApiLevel()).andReturn(24);

        // 64 bit device
        mMockTestDevice64 = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockTestDevice64.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.expect(mMockTestDevice64.getSerialNumber()).andStubReturn("SERIAL");
        EasyMock.expect(mMockTestDevice64.getProperty("ro.product.cpu.abilist64"))
                .andReturn("arm64-v8a");
        EasyMock.expect(mMockTestDevice64.getApiLevel()).andReturn(24);

        // Lollipop device
        mMockTestDeviceLmp = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockTestDeviceLmp.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.expect(mMockTestDeviceLmp.getSerialNumber()).andStubReturn("SERIAL");
        EasyMock.expect(mMockTestDeviceLmp.getProperty("ro.product.cpu.abilist64"))
                .andReturn("arm64-v8a");
        EasyMock.expect(mMockTestDeviceLmp.getApiLevel()).andReturn(21);

        mTestParentDir =
                FileUtil.createNamedTempDir(InstallGmsCoreApkSetupTest.class.getSimpleName());

        // Folder setup for latest apk.
        mTestSubFolder_20161213_rc02_suffix =
                FileUtil.createNamedTempDir(
                        String.format(
                                "%s/gmscore_apks_20161213.04_RC02_random_string",
                                InstallGmsCoreApkSetupTest.class.getSimpleName()));
        mTestSubFolder_20161213_rc01 =
                FileUtil.createNamedTempDir(
                        String.format(
                                "%s/gmscore_apks_20161213.03_RC01",
                                InstallGmsCoreApkSetupTest.class.getSimpleName()));
        mTestSubFolder_20161214_rc02 =
                FileUtil.createNamedTempDir(
                        String.format(
                                "%s/gmscore_apks_20161214.03_RC02",
                                InstallGmsCoreApkSetupTest.class.getSimpleName()));
        mTestSubFolder_20161214_rc01 =
                FileUtil.createNamedTempDir(
                        String.format(
                                "%s/gmscore_apks_20161214.03_RC01",
                                InstallGmsCoreApkSetupTest.class.getSimpleName()));
        mTestSubFolder_20161214_rc03 =
                FileUtil.createNamedTempDir(
                        String.format(
                                "%s/gmscore_apks_20161214.03_RC03",
                                InstallGmsCoreApkSetupTest.class.getSimpleName()));
        mTestSubFolder_v9_rc21 =
                FileUtil.createNamedTempDir(
                        String.format(
                                "%s/v9/gmscore_v9_RC21",
                                InstallGmsCoreApkSetupTest.class.getSimpleName()));

        mTestApk32 = FileUtil.createTempFile("test32", ".apk", mTestSubFolder_v9_rc21);
        mTestApk64 = FileUtil.createTempFile("test64", ".apk", mTestSubFolder_20161213_rc01);
        mTestApkLmp = FileUtil.createTempFile("testLmp", ".apk", mTestSubFolder_v9_rc21);
        mTestModule = FileUtil.createTempFile("auth_module", ".apk", mTestSubFolder_20161214_rc03);

        mSetter.setOptionValue("release-folder-path", mTestParentDir.getAbsolutePath());
        mSetter.setOptionValue("daily-build-folder-path", mTestParentDir.getAbsolutePath());
        mSetter.setOptionValue("auth-module-folder-path", mTestParentDir.getAbsolutePath());
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mTestParentDir);
    }

    /**
     * Test {@link InstallGmsCoreApkSetup#setUp(ITestDevice, IBuildInfo)} by successfully install
     * the release apk without auth module.
     */
    @Test
    public void testSetup_releaseApkWithoutModule()
            throws DeviceNotAvailableException, BuildError, TargetSetupError,
                    ConfigurationException {
        mSetter.setOptionValue("version-number", "v9");
        mSetter.setOptionValue("file-name-prefix-32", "test32");
        EasyMock.expect(mMockTestDevice.installPackage(EasyMock.anyObject(), EasyMock.eq(true)))
                .andReturn(null)
                .times(1);
        EasyMock.replay(mMockTestDevice);
        mInstallGmsCoreApkSetup.setUp(mMockTestDevice, mMockBuildInfo);
        EasyMock.verify(mMockTestDevice);
        Assert.assertEquals(
                mTestApk32.getAbsolutePath(), mInstallGmsCoreApkSetup.getApkFoundPathList().get(0));
    }

    /**
     * Test {@link InstallGmsCoreApkSetup#setUp(ITestDevice, IBuildInfo)} by successfully install
     * the release apk without auth module on lmp device.
     */
    @Test
    public void testSetup_releaseApkWithoutModuleLmp()
            throws DeviceNotAvailableException, BuildError, TargetSetupError,
                    ConfigurationException {
        mSetter.setOptionValue("version-number", "v9");
        mSetter.setOptionValue("file-name-prefix-lmp-32", "testLmp");
        EasyMock.expect(mMockTestDeviceLmp.installPackage(EasyMock.anyObject(), EasyMock.eq(true)))
                .andReturn(null)
                .times(1);
        EasyMock.replay(mMockTestDeviceLmp);
        mInstallGmsCoreApkSetup.setUp(mMockTestDeviceLmp, mMockBuildInfo);
        EasyMock.verify(mMockTestDeviceLmp);
        Assert.assertEquals(
                mTestApkLmp.getAbsolutePath(),
                mInstallGmsCoreApkSetup.getApkFoundPathList().get(0));
    }

    /**
     * Test {@link InstallGmsCoreApkSetup#setUp(ITestDevice, IBuildInfo)} by successfully install
     * the daily built apk without auth module.
     */
    @Test
    public void testSetup_dailyApkWithoutModule()
            throws DeviceNotAvailableException, BuildError, TargetSetupError,
                    ConfigurationException {
        mSetter.setOptionValue("daily-build", "true");
        mSetter.setOptionValue("daily-build-date", "20161213");
        mSetter.setOptionValue("file-name-prefix-64", "test64");
        EasyMock.expect(mMockTestDevice64.installPackage(EasyMock.anyObject(), EasyMock.eq(true)))
                .andReturn(null)
                .times(1);
        EasyMock.replay(mMockTestDevice64);
        mInstallGmsCoreApkSetup.setUp(mMockTestDevice64, mMockBuildInfo);
        EasyMock.verify(mMockTestDevice64);
        Assert.assertEquals(
                mTestApk64.getAbsolutePath(), mInstallGmsCoreApkSetup.getApkFoundPathList().get(0));
    }

    /**
     * Test {@link InstallGmsCoreApkSetup#setUp(ITestDevice, IBuildInfo)} by successfully install
     * the daily built apk with auth module.
     */
    @Test
    public void testSetup_dailyApkWithModule()
            throws DeviceNotAvailableException, BuildError, TargetSetupError,
                    ConfigurationException {
        mSetter.setOptionValue("daily-build", "true");
        mSetter.setOptionValue("daily-build-date", "20161213");
        mSetter.setOptionValue("file-name-prefix-64", "test64");
        mSetter.setOptionValue("auth-module", "true");
        mSetter.setOptionValue("auth-module-file-name-prefix", "auth_module");
        mSetter.setOptionValue("auth-module-date", "20161214");
        EasyMock.expect(mMockTestDevice64.installPackage(EasyMock.anyObject(), EasyMock.eq(true)))
                .andReturn(null)
                .times(2);
        EasyMock.replay(mMockTestDevice64);
        mInstallGmsCoreApkSetup.setUp(mMockTestDevice64, mMockBuildInfo);
        EasyMock.verify(mMockTestDevice64);
        Assert.assertEquals(
                mTestApk64.getAbsolutePath(), mInstallGmsCoreApkSetup.getApkFoundPathList().get(0));
        Assert.assertEquals(
                mTestModule.getAbsolutePath(),
                mInstallGmsCoreApkSetup.getApkFoundPathList().get(1));
    }

    /**
     * Test {@link InstallGmsCoreApkSetup#traverseDirAndFilter(Path, String, Integer, ITestDevice)}
     * by specifying a filter string and rc number.
     */
    @Test
    public void testTraverseDirAndFilter_specifiedRcNumber() throws TargetSetupError {
        Path rootFolder = Paths.get(mTestParentDir.getAbsolutePath());
        Path result =
                mInstallGmsCoreApkSetup.traverseDirAndFilter(
                        rootFolder, "2016", 2, mMockTestDevice);
        Assert.assertEquals(mTestSubFolder_20161214_rc02.getAbsolutePath(), result.toString());
    }

    /**
     * Test {@link InstallGmsCoreApkSetup#traverseDirAndFilter(Path, String, Integer, ITestDevice)}
     * by specifying a filter string only.
     */
    @Test
    public void testTraverseDirAndFilter_noRcNumber() throws TargetSetupError {
        Path rootFolder = Paths.get(mTestParentDir.getAbsolutePath());
        Path result =
                mInstallGmsCoreApkSetup.traverseDirAndFilter(
                        rootFolder, "2016", null, mMockTestDevice);
        Assert.assertEquals(mTestSubFolder_20161214_rc03.getAbsolutePath(), result.toString());
    }

    /**
     * Test {@link InstallGmsCoreApkSetup#traverseDirAndFilter(Path, String, Integer, ITestDevice)}
     * by passing in an invalid folder.
     */
    @Test(expected = TargetSetupError.class)
    public void testTraverseDirAndFilter_invalidFolder() throws TargetSetupError {
        Path rootFolder = Paths.get("/tmp/a_not_exising_folder");
        mInstallGmsCoreApkSetup.traverseDirAndFilter(rootFolder, "2016", null, mMockTestDevice);
    }

    /**
     * Test {@link InstallGmsCoreApkSetup#traverseDirAndFilter(Path, String, Integer, ITestDevice)}
     * by passing in a filter string which produce no results after filtering.
     */
    @Test(expected = TargetSetupError.class)
    public void testTraverseDirAndFilter_noEntryAfterFilter() throws TargetSetupError {
        Path rootFolder = Paths.get(mTestParentDir.getAbsolutePath());
        mInstallGmsCoreApkSetup.traverseDirAndFilter(rootFolder, "2015", null, mMockTestDevice);
    }

    /**
     * Test {@link InstallGmsCoreApkSetup#traverseDirAndFindApk(Path, String, ITestDevice)} by
     * specifying a valid apk prefix string.
     */
    @Test
    public void testTraverseDirAndFindApk_validApk() throws TargetSetupError {
        Path folderWithApk = Paths.get(mTestSubFolder_v9_rc21.getAbsolutePath());
        Path result =
                mInstallGmsCoreApkSetup.traverseDirAndFindApk(
                        folderWithApk, "test32", mMockTestDevice);
        Assert.assertEquals(mTestApk32.getAbsolutePath(), result.toString());
    }

    /**
     * Test {@link InstallGmsCoreApkSetup#traverseDirAndFindApk(Path, String, ITestDevice)} by
     * specifying an invalid apk prefix string.
     */
    @Test(expected = TargetSetupError.class)
    public void testTraverseDirAndFindApk_apkNotFound() throws TargetSetupError {
        Path folderWithNoApk = Paths.get(mTestSubFolder_20161214_rc02.getAbsolutePath());
        mInstallGmsCoreApkSetup.traverseDirAndFindApk(folderWithNoApk, "test64", mMockTestDevice);
    }

    /**
     * Test {@link InstallGmsCoreApkSetup#traverseDirAndFindLargestVersion(Path, ITestDevice)} by
     * specifying a valid folder.
     */
    @Test
    public void testTraverseDirAndFindLargestVersion_validFolder() throws TargetSetupError {
        Path folderWithSubfolder = Paths.get(mTestParentDir.getAbsolutePath());
        String result =
                mInstallGmsCoreApkSetup.traverseDirAndFindLargestVersion(
                        folderWithSubfolder, mMockTestDevice);
        Assert.assertEquals("20161214.03", result);
    }

    /**
     * Test {@link InstallGmsCoreApkSetup#traverseDirAndFindLargestVersion(Path, ITestDevice)} by
     * specifying an empty folder.
     */
    @Test(expected = TargetSetupError.class)
    public void testTraverseDirAndFindLargestVersion_validEmptyFolder() throws TargetSetupError {
        Path folderWithNoSubfolder = Paths.get(mTestSubFolder_20161214_rc01.getAbsolutePath());
        mInstallGmsCoreApkSetup.traverseDirAndFindLargestVersion(
                folderWithNoSubfolder, mMockTestDevice);
    }

    /**
     * Test {@link InstallGmsCoreApkSetup#traverseDirAndFindLargestVersion(Path, ITestDevice)} by
     * specifying an invalid folder.
     */
    @Test(expected = TargetSetupError.class)
    public void testTraverseDirAndFindLargestVersion_invalidFolder() throws TargetSetupError {
        Path folderNotExist = Paths.get("/tmp/a_not_exising_folder");
        mInstallGmsCoreApkSetup.traverseDirAndFindLargestVersion(folderNotExist, mMockTestDevice);
    }
}
