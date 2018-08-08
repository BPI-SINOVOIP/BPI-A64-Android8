// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import static org.junit.Assert.*;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.google.common.io.Files;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.ArrayList;

/** Unit tests for {@link GoogleDeviceSetup}. */
@RunWith(JUnit4.class)
public class AndroidThingsDeviceFlasherTest {

    private AndroidThingsDeviceFlasher mDeviceFlasher;
    private ITestDevice mMockDevice;
    private IDeviceBuildInfo mMockBuildInfo;
    private IRunUtil mMockRunUtil;

    private File mFlashfilesDir;

    @Before
    public void setUp() throws Exception {
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockBuildInfo = EasyMock.createMock(IDeviceBuildInfo.class);
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mDeviceFlasher = new AndroidThingsDeviceFlasher();

        mFlashfilesDir = FileUtil.createTempDir("ATDFtest");
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mFlashfilesDir);
    }

    /** Make a File object and set expectations base set of expectations for doFlash method. */
    void baseFlashExpectations() throws Exception {
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn("1234");
        mMockDevice.rebootIntoBootloader();
        EasyMock.expect(mMockBuildInfo.getDeviceBuildId()).andStubReturn("5678");
        mMockRunUtil.setEnvVariable(
                "ANDROID_PROVISION_OS_PARTITIONS", mFlashfilesDir.getAbsolutePath());
        EasyMock.expectLastCall();
        mMockRunUtil.setEnvVariable(
                "ANDROID_PROVISION_VENDOR_PARTITIONS", mFlashfilesDir.getAbsolutePath());
        EasyMock.expectLastCall();
        mMockRunUtil.setEnvVariable("FLASHB", "false");
        EasyMock.expectLastCall();
        mMockRunUtil.allowInterrupt(false);
        EasyMock.expectLastCall();
    }

    /**
     * Test successful execution of flashing. Verifies environment variables are set and script
     * called.
     */
    @Test
    public void testFlash_success() throws Exception {
        baseFlashExpectations();
        Files.touch(new File(mFlashfilesDir, "flash-all.sh"));
        CommandResult cmdR = new CommandResult(CommandStatus.SUCCESS);
        EasyMock.expect(
                        mMockRunUtil.runTimedCmd(
                                600000,
                                mFlashfilesDir.getAbsolutePath() + "/flash-all.sh",
                                "-s",
                                "1234"))
                .andReturn(cmdR);
        mMockRunUtil.allowInterrupt(true);
        EasyMock.expectLastCall();
        EasyMock.replay(mMockDevice, mMockRunUtil);
        mDeviceFlasher.doFlash(mMockDevice, mMockRunUtil, mFlashfilesDir);

        // Verify env variables set and script called.
        EasyMock.verify(mMockRunUtil, mMockDevice);
    }

    /** Test timeout of flashing. Verifies exception thrown with time out message. */
    @Test
    public void testFlash_timeout() throws Exception {
        baseFlashExpectations();
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andReturn(null);
        Files.touch(new File(mFlashfilesDir, "flash-all.sh"));
        CommandResult cmdR = new CommandResult(CommandStatus.TIMED_OUT);
        EasyMock.expect(
                        mMockRunUtil.runTimedCmd(
                                600000,
                                mFlashfilesDir.getAbsolutePath() + "/flash-all.sh",
                                "-s",
                                "1234"))
                .andReturn(cmdR);
        mMockRunUtil.allowInterrupt(true);
        EasyMock.expectLastCall();
        EasyMock.replay(mMockBuildInfo, mMockDevice, mMockRunUtil);

        try {
            mDeviceFlasher.doFlash(mMockDevice, mMockRunUtil, mFlashfilesDir);
            fail("Did not throw TargetSetupError");
        } catch (TargetSetupError exception) {
            assertTrue(exception.getMessage().contains("flash-all.sh timed out."));
        }
        EasyMock.verify(mMockRunUtil, mMockDevice);
    }

    /** Test failure of flashing script. Verifies exception thrown with failure message. */
    @Test
    public void testFlash_failure() throws Exception {
        baseFlashExpectations();
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andReturn(null);
        Files.touch(new File(mFlashfilesDir, "flash-all.sh"));
        CommandResult cmdR = new CommandResult(CommandStatus.FAILED);
        EasyMock.expect(
                        mMockRunUtil.runTimedCmd(
                                600000,
                                mFlashfilesDir.getAbsolutePath() + "/flash-all.sh",
                                "-s",
                                "1234"))
                .andReturn(cmdR);
        mMockRunUtil.allowInterrupt(true);
        EasyMock.expectLastCall();
        EasyMock.replay(mMockBuildInfo, mMockDevice, mMockRunUtil);

        try {
            mDeviceFlasher.doFlash(mMockDevice, mMockRunUtil, mFlashfilesDir);
            fail("Did not throw TargetSetupError");
        } catch (TargetSetupError exception) {
            assertTrue(exception.getMessage().contains("flash-all.sh failed."));
        }
        EasyMock.verify(mMockRunUtil, mMockDevice);
    }

    /** Verify retrieval of flashfiles zip from DeviceBuildInfo */
    @Test
    public void testFindFlashfiles_success() throws TargetSetupError {
        ArrayList<VersionedFile> files = new ArrayList<VersionedFile>();
        String fakeFilename = "iot_something-eng-otherfiles-1234.zip";
        String realFilename = "iot_something-eng-flashfiles-1234.zip";
        files.add(new VersionedFile(new File(fakeFilename), "foo"));
        files.add(new VersionedFile(new File(realFilename), "foo"));
        EasyMock.expect(mMockBuildInfo.getFiles()).andReturn(files);
        EasyMock.replay(mMockBuildInfo);

        File flashfiles = mDeviceFlasher.findFlashfiles(mMockDevice, mMockBuildInfo);
        assertTrue(realFilename.equals(flashfiles.getName()));
        EasyMock.verify(mMockBuildInfo);
    }

    /** Verify TargetSetupError thrown when flashfiles zip is missing. */
    @Test
    public void testFindFlashfiles_failure() {
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andReturn(null);
        ArrayList<VersionedFile> files = new ArrayList<VersionedFile>();
        String fakeFilename = "iot_something-eng-otherfiles-1234.zip";
        files.add(new VersionedFile(new File(fakeFilename), "foo"));
        EasyMock.expect(mMockBuildInfo.getFiles()).andReturn(files);
        EasyMock.replay(mMockBuildInfo, mMockDevice);

        try {
            mDeviceFlasher.findFlashfiles(mMockDevice, mMockBuildInfo);
            fail("Did not throw TargetSetupError");
        } catch (TargetSetupError exception) {
            assertTrue(exception.getMessage().contains("No flashfiles archive"));
        }
        EasyMock.verify(mMockBuildInfo, mMockDevice);
    }
}
