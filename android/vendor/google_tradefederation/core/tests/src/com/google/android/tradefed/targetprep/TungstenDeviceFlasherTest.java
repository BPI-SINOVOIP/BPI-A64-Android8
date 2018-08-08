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
 * Unit tests for {@link TungstenDeviceFlasher}
 */
public class TungstenDeviceFlasherTest extends TestCase {

    private TungstenDeviceFlasher mDeviceFlasher;
    private ITestDevice mMockDevice;
    private IDeviceBuildInfo mMockBuild;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDeviceFlasher = new TungstenDeviceFlasher();
        mDeviceFlasher.setFlashingResourcesRetriever(
                EasyMock.createNiceMock(IFlashingResourcesRetriever.class));
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn("serial");
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andStubReturn(null);
        mMockBuild = EasyMock.createNiceMock(IDeviceBuildInfo.class);
    }

    /**
     * Check that the checkAndFlashBootloader fails if required bootloader is too old
     */
    public void testCheckAndFlashBootloader_requiredTooOld() throws Exception {
        setCurrentBootloaderExpectations("steelheadB1003");
        setRequiredBootloaderExpectations("steelheadB1002");
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
        setCurrentBootloaderExpectations("steelheadB1002");
        setRequiredBootloaderExpectations("steelheadB1003");
        EasyMock.replay(mMockDevice, mMockBuild);
        try {
            mDeviceFlasher.checkAndFlashBootloader(mMockDevice, mMockBuild);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
    }

    /**
     * Check that the checkAndFlashBootloader when bootloader does not need flashing
     */
    public void testCheckAndFlashBootloader_noFlash() throws Exception {
        setCurrentBootloaderExpectations("steelheadB1003");
        setRequiredBootloaderExpectations("steelheadB1003");
        EasyMock.replay(mMockDevice, mMockBuild);
        assertFalse(mDeviceFlasher.checkAndFlashBootloader(mMockDevice, mMockBuild));
    }

    private void setRequiredBootloaderExpectations(String version) {
        EasyMock.expect(mMockBuild.getBootloaderVersion()).andReturn(version);
    }

    private void setCurrentBootloaderExpectations(String version)
            throws DeviceNotAvailableException {
        String returnString = String.format("version-bootloader: %s\n", version);
        CommandResult fastbootResponse = new CommandResult(CommandStatus.SUCCESS);
        fastbootResponse.setStderr(returnString);
        EasyMock.expect(mMockDevice.executeFastbootCommand("getvar",
                "version-bootloader")).andReturn(fastbootResponse);
    }
}
