// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.IFlashingResourcesRetriever;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link NakasiDeviceFlasher}
 */
public class NakasiDeviceFlasherTest extends TestCase {

    private NakasiDeviceFlasher mDeviceFlasher;
    private ITestDevice mMockDevice;
    private IDeviceBuildInfo mMockBuild;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDeviceFlasher = new NakasiDeviceFlasher();
        mDeviceFlasher.setFlashingResourcesRetriever(
                EasyMock.createNiceMock(IFlashingResourcesRetriever.class));
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn("serial");
        mMockBuild = EasyMock.createNiceMock(IDeviceBuildInfo.class);
    }

    /**
     * Check that the checkAndFlashBootloader fails if required bootloader is too old
     */
    public void testCheckAndFlashBootloader_requiredTooOld() throws Exception {
        setCurrentBootloaderExpectations("3.28");
        setRequiredBootloaderExpectations("3.27");
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.replay(mMockDevice, mMockBuild);
        try {
            mDeviceFlasher.checkAndFlashBootloader(mMockDevice, mMockBuild);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Check that the checkAndFlashBootloader fails if current bootloader is too old
     */
    public void testCheckAndFlashBootloader_currentTooOld() throws Exception {
        setCurrentBootloaderExpectations("3.26");
        setRequiredBootloaderExpectations("3.28");
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.replay(mMockDevice, mMockBuild);
        try {
            mDeviceFlasher.checkAndFlashBootloader(mMockDevice, mMockBuild);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Check that the checkAndFlashBootloader fails if required bootloader is too new
     */
    public void testCheckAndFlashBootloader_tooNew() throws Exception {
        setCurrentBootloaderExpectations("3.27");
        setRequiredBootloaderExpectations("3.29");
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.replay(mMockDevice, mMockBuild);
        try {
            mDeviceFlasher.checkAndFlashBootloader(mMockDevice, mMockBuild);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Check that the checkAndFlashBootloader when bootloader does not need flashing
     */
    public void testCheckAndFlashBootloader_noFlash() throws Exception {
        setCurrentBootloaderExpectations("3.28");
        setRequiredBootloaderExpectations("3.28");
        setCurrentBootloaderExpectations("3.28");
        EasyMock.replay(mMockDevice, mMockBuild);
        assertFalse(mDeviceFlasher.checkAndFlashBootloader(mMockDevice, mMockBuild));
    }

    private void setRequiredBootloaderExpectations(String version) {
        EasyMock.expect(mMockBuild.getBootloaderVersion()).andStubReturn(version);
    }

    private void setCurrentBootloaderExpectations(String version)
            throws DeviceNotAvailableException {
        String returnString = String.format("version-bootloader: %s\n", version);
        CommandResult fastbootResponse = new CommandResult(CommandStatus.SUCCESS);
        fastbootResponse.setStderr(returnString);
        EasyMock.expect(mMockDevice.executeFastbootCommand("getvar",
                "version-bootloader")).andStubReturn(fastbootResponse);
    }
}
