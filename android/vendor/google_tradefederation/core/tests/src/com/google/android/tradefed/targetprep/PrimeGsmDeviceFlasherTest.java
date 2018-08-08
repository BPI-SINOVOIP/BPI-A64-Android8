// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.FlashingResourcesParser;
import com.android.tradefed.targetprep.IFlashingResourcesParser;
import com.android.tradefed.targetprep.IFlashingResourcesRetriever;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;

/**
 * Unit tests for {@link PrimeGsmDeviceFlasherTest}.
 */
public class PrimeGsmDeviceFlasherTest extends TestCase {

    private static final String FLASH_MESSAGE = "flashBootloader reached";

    @SuppressWarnings("unused")
    private boolean mAssumeSblFlashSuccess = false;
    private boolean mFlashedSbl = false;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mAssumeSblFlashSuccess = false;
        mFlashedSbl = false;
    }

    /**
     * A template for the testFlashBootloader failing test set.  These expect TargetSetupError to be
     * thrown during
     * {@link PrimeGsmDeviceFlasher#checkAndFlashBootloader(ITestDevice, IDeviceBuildInfo)}.
     *
     * @param deviceVersion Number of versions beyond the min_supported that the device
     *        currently is.  May be negative.
     * @param buildVersion Number of versions beyond the min_supported that the new build would
     *        require.  May be negative.
     */
    private void flashBootloaderFailingTestTemplate(String deviceVersion, String buildVersion,
            Class<?> exceptionClass, String expectedMessageContent)
            throws DeviceNotAvailableException, TargetSetupError {
        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mockDevice.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("0A10F0153F0A6521");
        // device has deviceVersion
        setFastbootResponseExpectations(mockDevice, String.format("version-bootloader: %s\n",
                deviceVersion));
        EasyMock.replay(mockDevice);
        // and build requires buildVersion
        PrimeGsmDeviceFlasher flasher = getFlasherWithParserData(
                String.format("require version-bootloader=%s", buildVersion));

        IDeviceBuildInfo build = new DeviceBuildInfo("1234", "build-name");
        build.setBootloaderImageFile(new File("/"), String.format("%s", buildVersion));

        try {
            flasher.checkAndFlashBootloader(mockDevice, build);
            fail("DeviceNotAvailableException or TargetSetupError not thrown");
        } catch (DeviceNotAvailableException e) {
            if (!exceptionClass.isAssignableFrom(e.getClass())) {
                // not the exception we're expecting, so rethrow
                throw e;
            }
            // expected
            if (!e.getMessage().contains(expectedMessageContent)) {
                fail(String.format("Exception didn't contain expected content \"%s\": %s",
                        expectedMessageContent, e));
            }
        } catch (TargetSetupError e) {
            if (!exceptionClass.isAssignableFrom(e.getClass())) {
                // not the exception we're expecting, so rethrow
                throw e;
            }
            // expected
            if (!e.getMessage().contains(expectedMessageContent)) {
                fail(String.format("Exception didn't contain expected content \"%s\": %s",
                        expectedMessageContent, e));
            }
        }
        EasyMock.verify(mockDevice);
    }

    /**
     * A template for the testFlashBootloader passing test set.  These expect
     * DeviceNotAvailableException to be thrown with special message FLASH_MESSAGE to show that
     * that method was reached from
     * {@link PrimeGsmDeviceFlasher#checkAndFlashBootloader(ITestDevice, IDeviceBuildInfo)}.
     *
     * @param deviceVersion Number of versions beyond the min_supported that the device
     *        currently is.  May be negative.
     * @param buildVersion Number of versions beyond the min_supported that the new build would
     *        require.  May be negative.
     */
    private void flashBootloaderPassingTestTemplate(String deviceVersion, String buildVersion)
            throws DeviceNotAvailableException, TargetSetupError {
        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("0A10F0153F0A6521");
        // device has deviceVersion.
        // version-bootloader is called once from PrimeGsmDeviceFlasher and once from DeviceFlasher
        setFastbootResponseExpectations(mockDevice, String.format("version-bootloader: %s\n",
                deviceVersion));
        setFastbootResponseExpectations(mockDevice, String.format("version-bootloader: %s\n",
                deviceVersion));
        EasyMock.replay(mockDevice);
        // and build requires buildVersion
        PrimeGsmDeviceFlasher flasher = getFlasherWithParserData(
                String.format("require version-bootloader=%s", buildVersion));

        IDeviceBuildInfo build = new DeviceBuildInfo("1234", "build-name");
        build.setBootloaderImageFile(new File("/"), String.format("%s", buildVersion));
        try {
            flasher.checkAndFlashBootloader(mockDevice, build);
            fail("flashBootloader method not called");
        } catch (DeviceNotAvailableException e) {
            // expect DeviceNotAvailableException with message FLASH_MESSAGE
            if (!FLASH_MESSAGE.equals(e.getMessage())) {
                throw e;
            }
        }
        EasyMock.verify(mockDevice);
    }

    /**
     * Check that old versions send the person to the flashstation
     */
    public void testOldTargetBootloader() throws Exception {
        flashBootloaderFailingTestTemplate("PRIMEKF06", "PRIMEKF02",
                TargetSetupError.class, "bootloader version to be flashed, PRIMEKF02, is lower");
    }

    /**
     * Check that we fail when the flashing versions are unknown
     */
    public void testFlashBootloader_noParse() throws Exception {
        flashBootloaderFailingTestTemplate("PRIMEKFC01", "PRIMEKFC05",
                TargetSetupError.class, "TF flasher only supports devices with");
    }

    /**
     * Check that we fail when the flashing versions are unknown
     */
    public void testFlashBootloader_noParse2() throws Exception {
        flashBootloaderFailingTestTemplate("PRIMEJA01", "PRIMEJA05",
                TargetSetupError.class, "TF flasher only supports devices with");
    }

    /**
     * Check that old current bootloader version sends the person to the flashstation
     */
    public void testOldCurrentBootloader() throws Exception {
        flashBootloaderFailingTestTemplate("PRIMEKF02", "PRIMEKF06",
                DeviceNotAvailableException.class,
                "bootloader version currently on the device, PRIMEKF02, is lower");
    }

    /**
     * Check that a normal flash from KF05 to KH06 succeeds (and skips flashing SBL)
     */
    public void testUpgradeBootloader_skipSbl() throws Exception {
        mAssumeSblFlashSuccess = true;
        flashBootloaderPassingTestTemplate("PRIMEKF05", "PRIMEKH06");
        assertFalse("Flashed SBL unexpectedly for KF05->KH06 upgrade!", mFlashedSbl);
    }

    /**
     * Check that a normal flash from KF05 to KH06 succeeds (and skips flashing SBL)
     */
    public void testDowngradeBootloader_skipSbl() throws Exception {
        mAssumeSblFlashSuccess = true;
        flashBootloaderPassingTestTemplate("PRIMEKH06", "PRIMEKF05");
        assertFalse("Flashed SBL unexpectedly for KH06->KF05 downgrade!", mFlashedSbl);
    }

    /**
     * Set EasyMock expectations to simulate the response to some fastboot command
     *
     * @param mockDevice the EasyMock mock {@link ITestDevice} to configure
     * @param response the fastboot command response to inject
     */
    private static void setFastbootResponseExpectations(ITestDevice mockDevice, String response)
            throws DeviceNotAvailableException {
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStderr(response);
        result.setStdout("");
        EasyMock.expect(
                mockDevice.executeFastbootCommand((String)EasyMock.anyObject(),
                        (String)EasyMock.anyObject())).andReturn(result);
    }

    private PrimeGsmDeviceFlasher getFlasherWithParserData(final String androidInfoData) {
        PrimeGsmDeviceFlasher flasher = new PrimeGsmDeviceFlasher() {
            @Override
            protected IFlashingResourcesParser createFlashingResourcesParser(
                    IDeviceBuildInfo localBuild, DeviceDescriptor desc) throws TargetSetupError {
                BufferedReader reader = new BufferedReader(new StringReader(androidInfoData));
                try {
                    return new FlashingResourcesParser(reader);
                } catch (IOException e) {
                    return null;
                }
            }

            // This is here in case we need to test SBL flashing again at some point
            /*@Override
            void flashSblAndReboot(ITestDevice device, IDeviceBuildInfo deviceBuild)
                    throws DeviceNotAvailableException, TargetSetupError {
                if (mAssumeSblFlashSuccess) {
                    mFlashedSbl = true;
                } else {
                    super.flashSblAndReboot(device, deviceBuild);
                }
            }*/

            @Override
            protected void flashBootloader(ITestDevice device, File bootloaderImageFile)
                    throws DeviceNotAvailableException, TargetSetupError {
                throw new DeviceNotAvailableException(FLASH_MESSAGE, "fakeserial");
            }
        };
        flasher.setFlashingResourcesRetriever(EasyMock.createMock(
                IFlashingResourcesRetriever.class));
        return flasher;
    }
}
