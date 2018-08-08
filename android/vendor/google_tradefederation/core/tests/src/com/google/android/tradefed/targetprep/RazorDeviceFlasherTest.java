// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.IFlashingResourcesParser;
import com.android.tradefed.targetprep.IFlashingResourcesRetriever;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.Arrays;
import java.util.Collection;

/**
 * Unit tests for {@link RazorDeviceFlasher}
 */
public class RazorDeviceFlasherTest extends TestCase {

    private RazorDeviceFlasher mDeviceFlasher;
    private ITestDevice mMockDevice;
    private IDeviceBuildInfo mMockBuild;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDeviceFlasher = new RazorDeviceFlasher();
        mDeviceFlasher.setFlashingResourcesRetriever(
                EasyMock.createNiceMock(IFlashingResourcesRetriever.class));
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn("serial");
        mMockBuild = EasyMock.createNiceMock(IDeviceBuildInfo.class);
    }

    /**
     * Make sure that our modified required boards check is working as expected
     */
    public void testRequiredBoards_debRequirementWithFloDevice() throws Exception {
        final IFlashingResourcesParser parser = EasyMock.createMock(IFlashingResourcesParser.class);
        final Collection<String> boards = Arrays.asList("deb");
        EasyMock.expect(parser.getRequiredBoards()).andStubReturn(boards);
        EasyMock.replay(mMockDevice, mMockBuild, parser);
        mDeviceFlasher.verifyRequiredBoards(mMockDevice, parser, "flo");
    }

    /**
     * Make sure that our modified required boards check is working as expected
     */
    public void testRequiredBoards_floRequirementWithDebDevice() throws Exception {
        final IFlashingResourcesParser parser = EasyMock.createMock(IFlashingResourcesParser.class);
        final Collection<String> boards = Arrays.asList("flo");
        EasyMock.expect(parser.getRequiredBoards()).andStubReturn(boards);
        EasyMock.replay(mMockDevice, mMockBuild, parser);
        mDeviceFlasher.verifyRequiredBoards(mMockDevice, parser, "deb");
    }

    /**
     * Make sure that our modified required boards check is working as expected
     */
    public void testRequiredBoards_debRequirementWithBothDevices() throws Exception {
        final IFlashingResourcesParser parser = EasyMock.createMock(IFlashingResourcesParser.class);
        final Collection<String> boards = Arrays.asList("flo", "deb");
        EasyMock.expect(parser.getRequiredBoards()).andStubReturn(boards);
        EasyMock.replay(mMockDevice, mMockBuild, parser);
        mDeviceFlasher.verifyRequiredBoards(mMockDevice, parser, "deb");
    }

    /**
     * Make sure that our modified required boards check is working as expected
     */
    public void testRequiredBoards_wrongDevice() throws Exception {
        final IFlashingResourcesParser parser = EasyMock.createMock(IFlashingResourcesParser.class);
        final Collection<String> boards = Arrays.asList("flo", "deb");
        EasyMock.expect(parser.getRequiredBoards()).andStubReturn(boards);
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.replay(mMockDevice, mMockBuild, parser);

        try {
            mDeviceFlasher.verifyRequiredBoards(mMockDevice, parser, "manta");
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Make sure that our modified required boards check is working as expected
     */
    public void testRequiredBoards_wrongBuild() throws Exception {
        final IFlashingResourcesParser parser = EasyMock.createMock(IFlashingResourcesParser.class);
        final Collection<String> boards = Arrays.asList("manta");
        EasyMock.expect(parser.getRequiredBoards()).andStubReturn(boards);
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.replay(mMockDevice, mMockBuild, parser);

        try {
            mDeviceFlasher.verifyRequiredBoards(mMockDevice, parser, "flo");
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Check that the checkAndFlashBootloader fails if required bootloader is too old
     */
    public void testCheckAndFlashBootloader_requiredTooOld() throws Exception {
        setCurrentBootloaderExpectations("FLO-02.04");
        setRequiredBootloaderExpectations("FLO-02.01");
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
        setCurrentBootloaderExpectations("FLO-02.01");
        setRequiredBootloaderExpectations("FLO-02.04");
        EasyMock.replay(mMockDevice, mMockBuild);
        try {
            mDeviceFlasher.checkAndFlashBootloader(mMockDevice, mMockBuild);
            fail("TargetSetupError not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
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
