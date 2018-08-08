// Copyright 2010 Google Inc. All Rights Reserved.
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
import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

/**
 * Unit tests for {@link StingrayDeviceFlasherTest}.
 */
public class StingrayDeviceFlasherTest extends TestCase {

    private StingrayDeviceFlasher mFlasher;
    private static final String FLASH_MESSAGE = "flashBootloader reached";
    private ITestDevice mMockDevice;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mFlasher = new StingrayDeviceFlasher(new String[] {});
        mFlasher.setFlashingResourcesRetriever(EasyMock.createNiceMock(
                IFlashingResourcesRetriever.class));
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn("SERIAL");
    }

    /**
     * Verify that {@link StingrayDeviceFlasher#getBootloaderFilePrefix(ITestDevice)} throws an
     * exception for secure devices (which are currently unsupported).
     */
    public void testGetBootloaderFilePrefix_secure() throws DeviceNotAvailableException {
        setFastbootResponseExpectations(mMockDevice,
                "secure: yes\n" +
                "finished. total time: 0.003s");
        EasyMock.replay(mMockDevice);
        try {
            mFlasher.getBootloaderFilePrefix(mMockDevice);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Verify that {@link StingrayDeviceFlasher#getBootloaderFilePrefix(ITestDevice)} throws an
     * exception for secure devices (which are currently unsupported), even when the response has
     * no 'finished: xx' postfix.
     */
    public void testGetBootloaderFilePrefix_secure_notimestamp()
            throws DeviceNotAvailableException {
        setFastbootResponseExpectations(mMockDevice,
                "secure: yes");
        EasyMock.replay(mMockDevice);
        try {
            mFlasher.getBootloaderFilePrefix(mMockDevice);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Verify success case for {@link StingrayDeviceFlasher#getBootloaderFilePrefix(ITestDevice)}.
     */
    public void testGetBootloaderFilePrefix_unsecure() throws DeviceNotAvailableException,
            TargetSetupError {
        setFastbootResponseExpectations(mMockDevice,
                "secure: no\n" +
                "finished. total time: 0.003s");
        EasyMock.replay(mMockDevice);
        assertEquals("motoboot", mFlasher.getBootloaderFilePrefix(mMockDevice));
    }

    /**
     * Verify {@link StingrayDeviceFlasher#getBootloaderFilePrefix(ITestDevice)} throws a
     * {@link TargetSetupError} when secure value is not recognized be obtained.
     */
    public void testGetBootloaderFilePrefix_badSecureVal() throws DeviceNotAvailableException {
        setFastbootResponseExpectations(mMockDevice,
                "secure: unrecognized\n" +
                "finished. total time: 0.003s");
        EasyMock.replay(mMockDevice);
        try {
            mFlasher.getBootloaderFilePrefix(mMockDevice);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Verify {@link StingrayDeviceFlasher#getBootloaderFilePrefix(ITestDevice)} throws a
     * {@link TargetSetupError} when response to secure query is garbage.
     */
    public void testGetBootloaderFilePrefix_badSecureResponse() throws DeviceNotAvailableException {
        setFastbootResponseExpectations(mMockDevice,
                "unrecognized unrecognized unrecognized");
        EasyMock.replay(mMockDevice);
        try {
            mFlasher.getBootloaderFilePrefix(mMockDevice);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }


    /**
     * A template for the testFlashBootloader failing test set.  These expect TargetSetupError to be
     * thrown during
     * {@link StingrayDeviceFlasher#checkAndFlashBootloader(ITestDevice, IDeviceBuildInfo)}.
     *
     * @param deviceVersionDelta Number of versions beyond the min_supported that the device
     *        currently is.  May be negative.
     * @param buildVersionDelta Number of versions beyond the min_supported that the new build would
     *        require.  May be negative.
     */
    private void flashBootloaderFailingTestTemplate(int deviceVersionDelta, int buildVersionDelta)
            throws DeviceNotAvailableException {
        // Device currently has bootloader $min_version + $deviceVersionDelta
        setFastbootResponseExpectations(mMockDevice, String.format("version-bootloader: %d\n",
                StingrayDeviceFlasher.MIN_SUPPORTED_BOOTLOADER + deviceVersionDelta));
        EasyMock.replay(mMockDevice);

        // And the new build requires $min_version + $buildVersionDelta
        StingrayDeviceFlasher flasher = getFlasherWithParserData(
                String.format("require version-bootloader=%d",
                StingrayDeviceFlasher.MIN_SUPPORTED_BOOTLOADER + buildVersionDelta));

        try {
            IDeviceBuildInfo build = new DeviceBuildInfo("1234", "build-name");
            build.setBootloaderImageFile(new File("/"), String.format("%d",
                    StingrayDeviceFlasher.MIN_SUPPORTED_BOOTLOADER + buildVersionDelta));
            flasher.checkAndFlashBootloader(mMockDevice, build);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * A template for the testFlashBootloader passing test set.  These expect
     * DeviceNotAvailableException to be thrown with special message FLASH_MESSAGE to show that
     * that method was reached from
     * {@link StingrayDeviceFlasher#checkAndFlashBootloader(ITestDevice, IDeviceBuildInfo)}.
     *
     * @param deviceVersionDelta Number of versions beyond the min_supported that the device
     *        currently is.  May be negative.
     * @param buildVersionDelta Number of versions beyond the min_supported that the new build would
     *        require.  May be negative.
     */
    private void flashBootloaderPassingTestTemplate(int deviceVersionDelta, int buildVersionDelta)
            throws DeviceNotAvailableException, TargetSetupError {
        // Device currently has bootloader $min_version + $deviceVersionDelta
        // version-bootloader is called once from StingrayDeviceFlasher and once from DeviceFlasher
        setFastbootResponseExpectations(mMockDevice, String.format("version-bootloader: %d\n",
                StingrayDeviceFlasher.MIN_SUPPORTED_BOOTLOADER + deviceVersionDelta));
        setFastbootResponseExpectations(mMockDevice,
            "secure: no\n" +
            "finished. total time: 0.003s");
        setFastbootResponseExpectations(mMockDevice, String.format("version-bootloader: %d\n",
            StingrayDeviceFlasher.MIN_SUPPORTED_BOOTLOADER + deviceVersionDelta));
        setFastbootResponseExpectations(mMockDevice,
            "secure: no\n" +
            "finished. total time: 0.003s");
        EasyMock.replay(mMockDevice);

        // And the new build requires $min_version + $buildVersionDelta
        StingrayDeviceFlasher flasher = getFlasherWithParserData(
                String.format("require version-bootloader=%d",
                StingrayDeviceFlasher.MIN_SUPPORTED_BOOTLOADER + buildVersionDelta));

        IDeviceBuildInfo build = new DeviceBuildInfo("1234", "build-name");
        build.setBootloaderImageFile(new File("/"), String.format("%d",
                StingrayDeviceFlasher.MIN_SUPPORTED_BOOTLOADER + buildVersionDelta));
        try {
            flasher.checkAndFlashBootloader(mMockDevice, build);
            fail("flashBootloader method not called");
        } catch (DeviceNotAvailableException e) {
            // expect DeviceNotAvailableException with message FLASH_MESSAGE
            if (!FLASH_MESSAGE.equals(e.getMessage())) {
                throw e;
            }
        }
    }

    /**
     * Return an absolute build number to pass to flashBootloader(Passing/Failing)Template
     */
    private int translateRelToAbsoluteBuild(int absBuildNumber) {
        // Theory: the template uses MIN_SUPPORTED_BOOTLOADER + xxxVersionDelta
        // if xxxVersionDelta = absBuildNumber - MIN_SUPPORTED_BOOTLOADER, then the template
        // will use MIN_SUPPORTED + absBuildNumber - MIN_SUPPORTED == absBuildNumber
        return absBuildNumber - StingrayDeviceFlasher.MIN_SUPPORTED_BOOTLOADER;
    }

    /**
     * Bootloaders 1025 and 1026 require special handling.  Make sure we don't flash these versions
     * for now
     */
    public void testSpecialHandling_1023To1025() throws Exception {
        int deviceVersion = translateRelToAbsoluteBuild(1023);
        int buildVersion = translateRelToAbsoluteBuild(1025);
        flashBootloaderFailingTestTemplate(deviceVersion, buildVersion);
    }

    /**
     * Bootloaders 1025 and 1026 require special handling.  Make sure we don't flash these versions
     * for now
     */
    public void testSpecialHandling_1023To1026() throws Exception {
        int deviceVersion = translateRelToAbsoluteBuild(1023);
        int buildVersion = translateRelToAbsoluteBuild(1026);
        flashBootloaderFailingTestTemplate(deviceVersion, buildVersion);
    }

    /**
     * Bootloaders 1025 and 1026 require special handling.  Make sure we don't flash these versions
     * for now
     */
    public void testSpecialHandling_1025To1023() throws Exception {
        int deviceVersion = translateRelToAbsoluteBuild(1025);
        int buildVersion = translateRelToAbsoluteBuild(1023);
        flashBootloaderFailingTestTemplate(deviceVersion, buildVersion);
    }

    /**
     * Bootloaders 1025 and 1026 require special handling.  Make sure we don't flash these versions
     * for now
     */
    public void testSpecialHandling_1026To1023() throws Exception {
        int deviceVersion = translateRelToAbsoluteBuild(1026);
        int buildVersion = translateRelToAbsoluteBuild(1023);
        flashBootloaderFailingTestTemplate(deviceVersion, buildVersion);
    }

    /**
     * Bootloaders 1025 and 1026 require special handling.  Make sure that we can handle devices
     * already at the target version.
     */
    public void testSpecialHandling_1025To1025() throws Exception {
        int constantVersion = 1025;
        // Device currently has bootloader $min_version + $deviceVersionDelta
        // version-bootloader is called once from StingrayDeviceFlasher and once from DeviceFlasher
        setFastbootResponseExpectations(mMockDevice,
                String.format("version-bootloader: %d\n", constantVersion));
        setFastbootResponseExpectations(mMockDevice,
                String.format("version-bootloader: %d\n", constantVersion));
        EasyMock.replay(mMockDevice);

        // And the new build requires $min_version + $buildVersionDelta
        StingrayDeviceFlasher flasher = getFlasherWithParserData(
                String.format("require version-bootloader=%d", constantVersion));

        IDeviceBuildInfo build = new DeviceBuildInfo("1234", "build-name");
        build.setBootloaderImageFile(new File("/"), String.format("%d", constantVersion));

        assertFalse(flasher.checkAndFlashBootloader(mMockDevice, build));
    }

    /**
     * Bootloaders 1025 and 1026 require special handling.  Make sure that we can handle devices
     * already at the target version.
     */
    public void testSpecialHandling_1026To1026() throws Exception {
        int constantVersion = 1026;
        ITestDevice mockDevice = EasyMock.createMock(ITestDevice.class);
        // Device currently has bootloader $min_version + $deviceVersionDelta
        // version-bootloader is called once from StingrayDeviceFlasher and once from DeviceFlasher
        setFastbootResponseExpectations(mockDevice,
                String.format("version-bootloader: %d\n", constantVersion));
        setFastbootResponseExpectations(mockDevice,
                String.format("version-bootloader: %d\n", constantVersion));
        EasyMock.replay(mockDevice);

        // And the new build requires $min_version + $buildVersionDelta
        StingrayDeviceFlasher flasher = getFlasherWithParserData(
                String.format("require version-bootloader=%d", constantVersion));

        IDeviceBuildInfo build = new DeviceBuildInfo("1234", "build-name");
        build.setBootloaderImageFile(new File("/"), String.format("%d", constantVersion));

        assertFalse(flasher.checkAndFlashBootloader(mockDevice, build));
    }

    /**
     * Verify that bootloader version lockout logic is functioning properly: flashing should fail if
     * we would need to flash either from or to a bootloader version < MIN_SUPPORTED_BOOTLOADER.
     */
    public void testFlashBootloader_fromOldToNew() throws DeviceNotAvailableException {
        flashBootloaderFailingTestTemplate(-1, +0);
    }

    /**
     * Verify that bootloader version lockout logic is functioning properly: flashing should fail if
     * we would need to flash either from or to a bootloader version < MIN_SUPPORTED_BOOTLOADER.
     */
    public void testFlashBootloader_fromNewToOld() throws DeviceNotAvailableException {
        flashBootloaderFailingTestTemplate(+0, -1);
    }

    /**
     * Verify that bootloader version lockout logic is functioning properly: flashing should fail if
     * we would need to flash either from or to a bootloader version < MIN_SUPPORTED_BOOTLOADER.
     */
    public void testFlashBootloader_fromOldToOld() throws DeviceNotAvailableException {
        flashBootloaderFailingTestTemplate(-1, -1);
    }

    /**
     * Verify that bootloader version lockout logic is functioning properly: flashing should fail if
     * we would need to flash either from or to a bootloader version < MIN_SUPPORTED_BOOTLOADER.
     */
    public void testFlashBootloader_fromOldToOlder() throws DeviceNotAvailableException {
        flashBootloaderFailingTestTemplate(-1, -2);
    }

    /**
     * Verify that bootloader version lockout logic is functioning properly: flashing should fail if
     * we would need to flash either from or to a bootloader version < MIN_SUPPORTED_BOOTLOADER.
     */
    public void testFlashBootloader_fromOlderToOld() throws DeviceNotAvailableException {
        flashBootloaderFailingTestTemplate(-2, -1);
    }

    /**
     * Verify that bootloader version lockout logic is functioning properly: flashing should proceed
     * if both the current device version and the new required build version
     * are >= MIN_SUPPORTED_BOOTLOADER.
     */
    public void testFlashBootloader_fromNewToNewer() throws DeviceNotAvailableException,
            TargetSetupError {
        flashBootloaderPassingTestTemplate(+0, +1);
    }

    /**
     * Verify that bootloader version lockout logic is functioning properly: flashing should proceed
     * if both the current device version and the new required build version
     * are >= MIN_SUPPORTED_BOOTLOADER.
     */
    public void testFlashBootloader_fromNewerToNew() throws DeviceNotAvailableException,
            TargetSetupError {
        flashBootloaderPassingTestTemplate(+1, +0);
    }

    public void testFullFlash_oldBootloader_withLte() throws Exception {
        ITestDevice mockDevice = EasyMock.createStrictMock(ITestDevice.class);
        EasyMock.expect(mockDevice.getDeviceDescriptor()).andStubReturn(null);
        // Device currently has bootloader 1039, product-type "xoom-cdma", radio "N_02.0F.00R"
        // LTE radio ""
        EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("deviceserial");
        // Return a current build ID that needs to be upgraded
        EasyMock.expect(mockDevice.getBuildId()).andReturn("1111");
        EasyMock.expect(mockDevice.getBuildFlavor()).andReturn("test-userdebug");
        mockDevice.rebootIntoBootloader();

        // The old bootloader will always return "xoom-cdma" (or "stingray"), LTE or no
        EasyMock.expect(mockDevice.getProductType()).andReturn("xoom-cdma");

        // check and flash bootloader
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "version-bootloader"))
                .andReturn(getResponseResult("version-bootloader: 1039\n"));
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "secure"))
                .andReturn(getResponseResult("secure: no"));
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "version-bootloader"))
                .andReturn(getResponseResult("version-bootloader: 1039\n"));
        EasyMock.expect(mockDevice.executeFastbootCommand("flash", "motoboot", "/bootloader"))
                .andReturn(getResponseResult(""));
        mockDevice.rebootIntoBootloader();

        // check should flash baseband
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "version-baseband"))
                .andReturn(getResponseResult("version-baseband: N_02.0F.00R\n"))
                .times(1);

        // At this point we drop into the special CDMA flashing codepath.  First thing, we flash
        // userdata (which we don't test) and then we erase the cache
        // (hack to work around corruption bug)
        // EasyMock.expect(mockDevice.executeFastbootCommand("erase", "cache"))
        //        .andReturn(getResponseResult(""));
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "secure"))
                .andReturn(getResponseResult("secure: no"));
        EasyMock.expect(mockDevice.executeLongFastbootCommand("flash", "cache",
                StingrayDeviceFlasher.STINGRAY_EMPTY_CACHE_IMAGE.getAbsolutePath()))
                .andReturn(getResponseResult(""));

        // check and flash baseband
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "version-baseband"))
                .andReturn(getResponseResult("version-baseband: N_02.0F.00R\n"))
                .times(2);
        EasyMock.expect(mockDevice.executeLongFastbootCommand("flash", "radio", "/baseband"))
                .andReturn(getResponseResult(""));

        // flash System
        EasyMock.expect(mockDevice.executeLongFastbootCommand("flash", "boot",
                "/updaterPath/boot.img")).andReturn(getResponseResult(""));
        EasyMock.expect(mockDevice.executeLongFastbootCommand("flash", "recovery",
                "/updaterPath/recovery.img")).andReturn(getResponseResult(""));
        EasyMock.expect(mockDevice.executeLongFastbootCommand("flash", "system",
                "/updaterPath/system.img")).andReturn(getResponseResult(""));

        // double reboot
        EasyMock.expect(mockDevice.executeFastbootCommand("reboot"))
                .andReturn(getResponseResult(""));
        mockDevice.waitForDeviceOnline(EasyMock.anyLong());
        mockDevice.waitForDeviceAvailable();
        mockDevice.reboot();
        mockDevice.rebootIntoBootloader();

        // check and flash lte baseband
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "version-baseband-2"))
                .andReturn(getResponseResult(
                    "getvar:version-baseband-2 FAILED (remote: Invalid Argument)\n"));
        // The new bootloader will recognize that this device has an LTE modem
        EasyMock.expect(mockDevice.getFastbootProductType()).andReturn("xoom-cdma-lte");
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "version-baseband-2"))
                .andReturn(getResponseResult(
                    "getvar:version-baseband-2 FAILED (remote: Invalid Argument)\n"));
        EasyMock.expect(mockDevice.executeLongFastbootCommand("flash", "radio", "/lte"))
                .andReturn(getResponseResult(""));

        // one last double reboot
        EasyMock.expect(mockDevice.executeFastbootCommand("reboot"))
                .andReturn(getResponseResult(""));
        mockDevice.waitForDeviceOnline(EasyMock.anyLong());
        mockDevice.waitForDeviceAvailable();
        EasyMock.expect(mockDevice.getProductVariant()).andReturn("stingray");
        mockDevice.reboot();

        EasyMock.replay(mockDevice);

        // And the new build requires the following
        final String androidInfo =
                "require product=stingray|xoom-cdma|xoom-cdma-lte\n" +
                "require version-bootloader=1045\n" +
                "require version-baseband=CDMA_N_03.16.00RU|N_03.16.00RU\n" +
                "require-for-product:xoom-cdma-lte " +
                    "version-baseband-2=ltedc_u_03.25.00|ltedc_u_03.19.00\n";

        StingrayDeviceFlasher flasher = new StingrayDeviceFlasher(new String[] {}) {
            @Override
            protected IFlashingResourcesParser createFlashingResourcesParser(
                    IDeviceBuildInfo localBuild, DeviceDescriptor desc) throws TargetSetupError {
                BufferedReader reader = new BufferedReader(new StringReader(androidInfo));
                try {
                    return new FlashingResourcesParser(reader);
                } catch (IOException e) {
                    return null;
                }
            }

            @Override
            protected void flashUserData(ITestDevice device, IDeviceBuildInfo deviceBuild) {
                // skip verifying userdata flashing stuff
            }


            @Override
            protected File extractSystemZip(IDeviceBuildInfo deviceBuild) {
                return new File("/updaterPath");
            }

            @Override
            protected IRunUtil getRunUtil() {
                return EasyMock.createNiceMock(IRunUtil.class);
            }

        };
        flasher.setFlashingResourcesRetriever(EasyMock.createNiceMock(
                IFlashingResourcesRetriever.class));

        IDeviceBuildInfo build = new DeviceBuildInfo("1234", "build-name");
        build.setBootloaderImageFile(new File("/bootloader"), "1045");
        build.setBasebandImage(new File("/baseband"), "CDMA_N_03.16.00RU");
        build.setFile(StingrayDeviceFlasher.LTE_BUILD_IMAGE_NAME, new File("/lte"),
                "ltedc_u_03.19.00");

        // Actually "flash" the mock device
        flasher.flash(mockDevice, build);

        EasyMock.verify(mockDevice);
    }

    public void testFullFlash_oldBootloader_withoutLte() throws Exception {
        ITestDevice mockDevice = EasyMock.createStrictMock(ITestDevice.class);
        EasyMock.expect(mockDevice.getDeviceDescriptor()).andStubReturn(null);
        // Device currently has bootloader 1039, product-type "xoom-cdma", radio "N_02.0F.00R"
        // LTE radio ""
        EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("deviceserial");
        // Return a current build ID that needs to be upgraded
        EasyMock.expect(mockDevice.getBuildId()).andReturn("1111");
        EasyMock.expect(mockDevice.getBuildFlavor()).andReturn("test-userdebug");
        mockDevice.rebootIntoBootloader();

        // The old bootloader will always return "xoom-cdma" (or "stingray"), LTE or no
        EasyMock.expect(mockDevice.getProductType()).andReturn("xoom-cdma");

        // check and flash bootloader
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "version-bootloader"))
                .andReturn(getResponseResult("version-bootloader: 1039\n"));
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "secure"))
                .andReturn(getResponseResult("secure: no"));
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "version-bootloader"))
                .andReturn(getResponseResult("version-bootloader: 1039\n"));
        EasyMock.expect(mockDevice.executeFastbootCommand("flash", "motoboot", "/bootloader"))
                .andReturn(getResponseResult(""));
        mockDevice.rebootIntoBootloader();

        // check should flash baseband
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "version-baseband"))
                .andReturn(getResponseResult("version-baseband: N_02.0F.00R\n"))
                .times(1);

        // At this point we drop into the special CDMA flashing codepath.  First thing, we flash
        // userdata (which we don't test) and then we erase the cache
        // (hack to work around corruption bug)
        // EasyMock.expect(mockDevice.executeFastbootCommand("erase", "cache"))
        //        .andReturn(getResponseResult(""));
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "secure"))
                .andReturn(getResponseResult("secure: no"));
        EasyMock.expect(mockDevice.executeLongFastbootCommand("flash", "cache",
                StingrayDeviceFlasher.STINGRAY_EMPTY_CACHE_IMAGE.getAbsolutePath()))
                .andReturn(getResponseResult(""));

        // check and flash baseband
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "version-baseband"))
                .andReturn(getResponseResult("version-baseband: N_02.0F.00R\n"))
                .times(2);
        EasyMock.expect(mockDevice.executeLongFastbootCommand("flash", "radio", "/baseband"))
                .andReturn(getResponseResult(""));

        // flash System
        EasyMock.expect(mockDevice.executeLongFastbootCommand("flash", "boot",
                "/updaterPath/boot.img")).andReturn(getResponseResult(""));
        EasyMock.expect(mockDevice.executeLongFastbootCommand("flash", "recovery",
                "/updaterPath/recovery.img")).andReturn(getResponseResult(""));
        EasyMock.expect(mockDevice.executeLongFastbootCommand("flash", "system",
                "/updaterPath/system.img")).andReturn(getResponseResult(""));

        // double reboot
        EasyMock.expect(mockDevice.executeFastbootCommand("reboot"))
                .andReturn(getResponseResult(""));
        mockDevice.waitForDeviceOnline(EasyMock.anyLong());
        mockDevice.waitForDeviceAvailable();
        mockDevice.reboot();
        mockDevice.rebootIntoBootloader();

        // check and flash lte baseband
        EasyMock.expect(mockDevice.executeFastbootCommand("getvar", "version-baseband-2"))
                .andReturn(getResponseResult(
                    "getvar:version-baseband-2 FAILED (remote: Invalid Argument)\n"));
        // The new bootloader will recognize that this device doesn't have an LTE modem
        EasyMock.expect(mockDevice.getFastbootProductType()).andReturn("xoom-cdma");
        EasyMock.expect(mockDevice.getProductVariant()).andReturn("wingray");
        EasyMock.expect(mockDevice.executeShellCommand("setprop persist.sys.usb.config adb"))
            .andReturn("");

        // The flasher should skip trying to flash the LTE baseband now.  In doing so, it should
        // also skip the second double-reboot that would happen after flashing the LTE baseband.

        // The single reboot (back into userspace) happens at the end of the #flash method
        mockDevice.reboot();

        EasyMock.replay(mockDevice);

        // And the new build requires the following
        final String androidInfo =
                "require product=stingray|xoom-cdma|xoom-cdma-lte\n" +
                "require version-bootloader=1045\n" +
                "require version-baseband=CDMA_N_03.16.00RU|N_03.16.00RU\n" +
                "require-for-product:xoom-cdma-lte " +
                    "version-baseband-2=ltedc_u_03.25.00|ltedc_u_03.19.00\n";

        StingrayDeviceFlasher flasher = new StingrayDeviceFlasher(new String[] {}) {
            @Override
            protected IFlashingResourcesParser createFlashingResourcesParser(
                    IDeviceBuildInfo localBuild, DeviceDescriptor desc) throws TargetSetupError {
                BufferedReader reader = new BufferedReader(new StringReader(androidInfo));
                try {
                    return new FlashingResourcesParser(reader);
                } catch (IOException e) {
                    return null;
                }
            }

            @Override
            protected void flashUserData(ITestDevice device, IDeviceBuildInfo deviceBuild) {
                // skip verifying userdata flashing stuff
            }


            @Override
            protected File extractSystemZip(IDeviceBuildInfo deviceBuild) {
                return new File("/updaterPath");
            }

            @Override
            protected IRunUtil getRunUtil() {
                return EasyMock.createNiceMock(IRunUtil.class);
            }

        };
        flasher.setFlashingResourcesRetriever(EasyMock.createNiceMock(
                IFlashingResourcesRetriever.class));

        IDeviceBuildInfo build = new DeviceBuildInfo("1234", "build-name");
        build.setBootloaderImageFile(new File("/bootloader"), "1045");
        build.setBasebandImage(new File("/baseband"), "CDMA_N_03.16.00RU");
        build.setFile(StingrayDeviceFlasher.LTE_BUILD_IMAGE_NAME, new File("/lte"),
                "ltedc_u_03.19.00");

        // Actually "flash" the mock device
        flasher.flash(mockDevice, build);

        EasyMock.verify(mockDevice);
    }

    @SuppressWarnings("unused")
    private String getFlashedImage(DeviceNotAvailableException e)
            throws DeviceNotAvailableException {
        String msg = e.getMessage();
        if (msg == null || !msg.startsWith(FLASH_MESSAGE)) {
            throw e;
        } else {
            // flashing message; just extract the image name and return it
            return msg.replaceFirst("^" + FLASH_MESSAGE, "");
        }
    }

    /**
     * Regression test that verifyRequiredBoards() passes if device product type exactly matches
     * the build's required boards
     */
    public void testVerifyRequiredBoards_stingray() throws Exception {
        String[] requiredBoards = new String[] {"stingray"};
        doVerifyRequiredBoardsTest("stingray", requiredBoards);
    }

    /**
     * Test that verifyRequiredBoards() passes if device is flashed from a 'stingray'
     * product to a 'xoom-cdma' build
     */
    public void testVerifyRequiredBoards_stingrayToXoom() throws Exception {
        String[] requiredBoards = new String[] {"xoom-cdma"};
        doVerifyRequiredBoardsTest("stingray", requiredBoards);
    }

    /**
     * Test that verifyRequiredBoards() passes if device is flashed from a 'wingray'
     * product to a 'xoom-cdma' build
     */
    public void testVerifyRequiredBoards_wingrayToXoom() throws Exception {
        String[] requiredBoards = new String[] {"xoom-cdma"};
        doVerifyRequiredBoardsTest("wingray", requiredBoards);
    }

    /**
     * Test that verifyRequiredBoards() fails if try to flash a passion build to a stingray device
     */
    public void testVerifyRequiredBoards_wrongBuild() throws Exception {
        String[] requiredBoards = new String[] {"passion"};
        try {
            doVerifyRequiredBoardsTest("stingray", requiredBoards);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Test that verifyRequiredBoards() fails if try to flash a stingray build to a passion device
     */
    public void testVerifyRequiredBoards_wrongDevice() throws Exception {
        String[] requiredBoards = new String[] {"xoom-cdma"};
        try {
            doVerifyRequiredBoardsTest("passion", requiredBoards);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Helper method to perform a test call on verifyRequiredBoards().
     *
     * @param deviceProductType the device product type to simulate
     * @param requiredBoards the mock response to return from a
     *            {@link IFlashingResourcesParser#getRequiredBoards()} call
     * @throws TargetSetupError
     */
    private void doVerifyRequiredBoardsTest(String deviceProductType, String[] requiredBoards)
            throws TargetSetupError {
        IFlashingResourcesRetriever mockRetriever = EasyMock.createMock(
                IFlashingResourcesRetriever.class);
        IFlashingResourcesParser mockParser = EasyMock.createMock(
                IFlashingResourcesParser.class);
        EasyMock.expect(mockParser.getRequiredBoards()).andStubReturn(Arrays.asList(requiredBoards));
        ITestDevice mockDevice = EasyMock.createNiceMock(ITestDevice.class);
        StingrayDeviceFlasher flasher = new StingrayDeviceFlasher(
                StingrayDeviceFlasher.STINGRAY_PRODUCT_TYPES);
        flasher.setFlashingResourcesRetriever(mockRetriever);
        EasyMock.replay(mockDevice, mockParser, mockRetriever);
        flasher.verifyRequiredBoards(mockDevice, mockParser, deviceProductType);
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

    /**
     * Set EasyMock expectations to simulate the response to some fastboot command
     *
     * @param response the fastboot command response to inject
     */
    private static CommandResult getResponseResult(String response) {
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStderr(response);
        result.setStdout("");
        return result;
    }

    private StingrayDeviceFlasher getFlasherWithParserData(final String androidInfoData) {
        StingrayDeviceFlasher flasher = new StingrayDeviceFlasher(new String[] {}) {
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
                    throws DeviceNotAvailableException {
                throw new DeviceNotAvailableException(FLASH_MESSAGE, "fakeserial");
            }
        };
        flasher.setFlashingResourcesRetriever(EasyMock.createNiceMock(
                IFlashingResourcesRetriever.class));
        return flasher;
    }
}
