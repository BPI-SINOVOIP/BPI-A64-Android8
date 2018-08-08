// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.IFlashingResourcesRetriever;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link SholesDeviceFlasher}.
 */
public class SholesDeviceFlasherTest extends TestCase {

    private SholesDeviceFlasher mSholesDeviceFlasher;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mSholesDeviceFlasher = new SholesDeviceFlasher();
        mSholesDeviceFlasher.setFlashingResourcesRetriever(
                EasyMock.createNiceMock(IFlashingResourcesRetriever.class));
    }

    /**
     * Verify success case for {@link SholesDeviceFlasher#getBootloaderFilePrefix(ITestDevice)}.
     */
    public void testGetBootloaderFilePrefix_secure() throws DeviceNotAvailableException,
            TargetSetupError {
        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        setGetBootloaderPrefixExpectations(mockDevice,
                "secure: yes\n" +
                "finished. total time: 0.003s");
        assertEquals("motoboot_secure", mSholesDeviceFlasher.getBootloaderFilePrefix(mockDevice));
    }

    /**
     * Verify success case for {@link SholesDeviceFlasher#getBootloaderFilePrefix(ITestDevice)}
     * when response has no 'finished: xx' postfix.
     */
    public void testGetBootloaderFilePrefix_secure_notimestamp() throws DeviceNotAvailableException,
            TargetSetupError {
        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        setGetBootloaderPrefixExpectations(mockDevice,
                "secure: yes");
        assertEquals("motoboot_secure", mSholesDeviceFlasher.getBootloaderFilePrefix(mockDevice));
    }

    /**
     * Verify success case for {@link SholesDeviceFlasher#getBootloaderFilePrefix(ITestDevice)}.
     */
    public void testGetBootloaderFilePrefix_unsecure() throws DeviceNotAvailableException,
            TargetSetupError {
        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        setGetBootloaderPrefixExpectations(mockDevice,
                "secure: no\n" +
                "finished. total time: 0.003s");
        assertEquals("motoboot_unsecure", mSholesDeviceFlasher.getBootloaderFilePrefix(mockDevice));
    }

    /**
     * Verify {@link SholesDeviceFlasher#getBootloaderFilePrefix(ITestDevice)} throws a
     * {@link TargetSetupError} when secure value is not recognized be obtained.
     */
    public void testGetBootloaderFilePrefix_badSecureVal() throws DeviceNotAvailableException {
        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("SERIAL");
        EasyMock.expect(mockDevice.getDeviceDescriptor()).andStubReturn(null);
        setGetBootloaderPrefixExpectations(mockDevice,
                "secure: unrecognized\n" +
                "finished. total time: 0.003s");
        try {
            mSholesDeviceFlasher.getBootloaderFilePrefix(mockDevice);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Verify {@link SholesDeviceFlasher#getBootloaderFilePrefix(ITestDevice)} throws a
     * {@link TargetSetupError} when response to secure query is garbage.
     */
    public void testGetBootloaderFilePrefix_badSecureResponse() throws DeviceNotAvailableException {
        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("SERIAL");
        EasyMock.expect(mockDevice.getDeviceDescriptor()).andStubReturn(null);
        setGetBootloaderPrefixExpectations(mockDevice,
                "unrecognized unrecognized unrecognized");
        try {
            mSholesDeviceFlasher.getBootloaderFilePrefix(mockDevice);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Set EasyMock expectations to simulate a "fastboot get var secure" response
     *
     * @param mockDevice the EasyMock mock {@link ITestDevice} to configure
     * @param response the fastboot command response to inject
     */
    private void setGetBootloaderPrefixExpectations(ITestDevice mockDevice, String response)
            throws DeviceNotAvailableException {
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStderr(response);
        result.setStdout("");
        EasyMock.expect(
                mockDevice.executeFastbootCommand((String)EasyMock.anyObject(),
                        (String)EasyMock.anyObject())).andReturn(result);
        EasyMock.replay(mockDevice);
    }
}
