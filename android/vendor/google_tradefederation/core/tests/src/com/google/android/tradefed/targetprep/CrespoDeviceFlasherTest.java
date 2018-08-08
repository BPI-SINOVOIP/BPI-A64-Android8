// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.google.android.tradefed.targetprep.CrespoDeviceFlasher.SupportedBasebandConstraint;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.FlashingResourcesParser;
import com.android.tradefed.targetprep.FlashingResourcesParser.Constraint;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link CrespoDeviceFlasherTest}.
 */
public class CrespoDeviceFlasherTest extends TestCase {

    private static final String FLASH_MESSAGE = "flashBootloader reached";

    private static final String OLD_BOOTLOADER = "I9020XXJI3";
    private static final String MIN_BOOTLOADER = "I9020XXJJ2";
    private static final String NEW_BOOTLOADER = "I9020XXJH3";


    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }


    /**
     * A template for the testFlashBootloader failing test set.  These expect TargetSetupError to be
     * thrown during
     * {@link CrespoDeviceFlasher#checkAndFlashBootloader(ITestDevice, IDeviceBuildInfo)}.
     *
     * @param deviceVersion Number of versions beyond the min_supported that the device
     *        currently is.  May be negative.
     * @param buildVersion Number of versions beyond the min_supported that the new build would
     *        require.  May be negative.
     */
    private void flashBootloaderFailingTestTemplate(String deviceVersion, String buildVersion)
            throws DeviceNotAvailableException {
        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mockDevice.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.expect(mockDevice.getSerialNumber()).andReturn("3032D99AA57700EC").anyTimes();
        // device has deviceVersion
        setFastbootResponseExpectations(mockDevice, String.format("version-bootloader: %s\n",
                deviceVersion));
        EasyMock.replay(mockDevice);
        // and build requires buildVersion
        CrespoDeviceFlasher flasher = getFlasherWithParserData(
                String.format("require version-bootloader=%s", buildVersion));

        IDeviceBuildInfo build = new DeviceBuildInfo("1234", "build-name");
        build.setBootloaderImageFile(new File("/"), String.format("%s", buildVersion));

        try {
            flasher.checkAndFlashBootloader(mockDevice, build);
            fail("DeviceNotAvailableException or TargetSetupError not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * A template for the testFlashBootloader passing test set.  These expect
     * DeviceNotAvailableException to be thrown with special message FLASH_MESSAGE to show that
     * that method was reached from
     * {@link CrespoDeviceFlasher#checkAndFlashBootloader(ITestDevice, IDeviceBuildInfo)}.
     *
     * @param deviceVersion Number of versions beyond the min_supported that the device
     *        currently is.  May be negative.
     * @param buildVersion Number of versions beyond the min_supported that the new build would
     *        require.  May be negative.
     */
    private void flashBootloaderPassingTestTemplate(String deviceVersion, String buildVersion)
            throws DeviceNotAvailableException, TargetSetupError {
        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mockDevice.getSerialNumber()).andReturn("3032D99AA57700EC").anyTimes();
        // device has deviceVersion.
        // version-bootloader is called once from CrespoDeviceFlasher and once from DeviceFlasher
        setFastbootResponseExpectations(mockDevice, String.format("version-bootloader: %s\n",
                deviceVersion));
        setFastbootResponseExpectations(mockDevice, String.format("version-bootloader: %s\n",
                deviceVersion));
        EasyMock.replay(mockDevice);
        // and build requires buildVersion
        CrespoDeviceFlasher flasher = getFlasherWithParserData(
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
    }

    /**
     * Verify that bootloader upgrades would be flashed.
     */
    public void testFlashBootloader_fromMinToNew() throws DeviceNotAvailableException,
            TargetSetupError {
        flashBootloaderPassingTestTemplate(MIN_BOOTLOADER, NEW_BOOTLOADER);
    }

    /**
     * Verify that device with old bootloader
     */
    public void testFlashBootloader_tooOld() throws DeviceNotAvailableException {
        flashBootloaderFailingTestTemplate(OLD_BOOTLOADER, NEW_BOOTLOADER);
    }

    /**
     * Verify that bootloader is not flashed if the bootloader version strings differ in length.
     */
    public void testFlashBootloader_tooShort() throws DeviceNotAvailableException {
        flashBootloaderFailingTestTemplate("", NEW_BOOTLOADER);
    }

    /**
     * Verify that bootloader is not flashed if the bootloader version strings differ in length.
     * A second case
     */
    public void testFlashBootloader_tooShort2() throws DeviceNotAvailableException {
        String prefix = NEW_BOOTLOADER.substring(0, NEW_BOOTLOADER.length() - 2);
        flashBootloaderFailingTestTemplate(prefix, NEW_BOOTLOADER);
    }

    public void testParseVersion_normal() throws Exception {
        Map<String, String> m = CrespoDeviceFlasher.parseVersion("I9020XXJH3");
        assertEquals(5, m.size());
        assertEquals("I9020", m.get("model"));
        assertEquals("XX", m.get("type"));
        assertEquals("J", m.get("year"));
        assertEquals("H", m.get("month"));
        assertEquals("3", m.get("build"));
    }

    public void testParseVersion_badVersion() throws Exception {
        Map<String, String> m = CrespoDeviceFlasher.parseVersion("D7200XXJH3");
        assertNull(m);
    }

    public void testBasebandConstraint() throws Exception {
        Constraint c = new SupportedBasebandConstraint("XX");
        assertTrue(c.shouldAccept("I9020XXKI1"));
        assertFalse(c.shouldAccept("M200KRKC1"));  // different model
        assertFalse(c.shouldAccept("I9020UCKB2"));  // same model, different type
    }

    /**
     * Verify a bugfix.  This is the corrected case, in which we should _not_ attempt to flash an
     * incompatible baseband.
     */
    public void testBasebandConstraint_endToEndGood() throws Exception {
        final String validInfoData = "require board=herring\n" +
                "require version-bootloader=I9020XXJK1|I9020XXKA3|I9020XXKI1\n" +
                "require version-baseband=I9020XXJK8|I9020XXKB1|I9020XXKD1|I9020XXKF1|I9020XXKI1" +
                    "|I9020UCKB2|I9020UCKD1|I9020UCKF1|I9020KRKB3|M200KRKC1";

        BufferedReader reader = new BufferedReader(new StringReader(validInfoData));
        Map<String, Constraint> constraintMap = new HashMap<String, Constraint>(1);
        constraintMap.put(FlashingResourcesParser.BASEBAND_VERSION_KEY,
                new SupportedBasebandConstraint(CrespoDeviceFlasher.SUPPORTED_CRESPO_TYPES));

        IFlashingResourcesParser parser = new FlashingResourcesParser(reader, constraintMap);
        assertEquals("I9020XXKI1", parser.getRequiredBasebandVersion());
    }

    /**
     * Verify a bugfix.  This is the uncorrected case, in which (given current ordering prefs) we
     * _should_ attempt to flash the incompatible baseband.
     */
    public void testBasebandConstraint_endToEndBad() throws Exception {
        final String validInfoData = "require board=herring\n" +
                "require version-bootloader=I9020XXJK1|I9020XXKA3|I9020XXKI1\n" +
                "require version-baseband=I9020XXJK8|I9020XXKB1|I9020XXKD1|I9020XXKF1|I9020XXKI1" +
                    "|I9020UCKB2|I9020UCKD1|I9020UCKF1|I9020KRKB3|M200KRKC1";

        BufferedReader reader = new BufferedReader(new StringReader(validInfoData));
        Map<String, Constraint> constraintMap = new HashMap<String, Constraint>(1);
        // We skip the correction here
        //constraintMap.put(FlashingResourcesParser.BASEBAND_VERSION_KEY,
        //        new SupportedBasebandConstraint(CrespoDeviceFlasher.SUPPORTED_CRESPO_TYPES));

        IFlashingResourcesParser parser = new FlashingResourcesParser(reader, constraintMap);
        assertEquals("M200KRKC1", parser.getRequiredBasebandVersion());
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

    private CrespoDeviceFlasher getFlasherWithParserData(final String androidInfoData) {
        CrespoDeviceFlasher flasher = new CrespoDeviceFlasher() {
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

            @Override
            protected void flashBootloader(ITestDevice device, File bootloaderImageFile)
                    throws DeviceNotAvailableException, TargetSetupError {
                throw new DeviceNotAvailableException(FLASH_MESSAGE, "fakeserial");
            }
        };
        flasher.setFlashingResourcesRetriever(EasyMock.createNiceMock(
                IFlashingResourcesRetriever.class));
        return flasher;
    }
}
