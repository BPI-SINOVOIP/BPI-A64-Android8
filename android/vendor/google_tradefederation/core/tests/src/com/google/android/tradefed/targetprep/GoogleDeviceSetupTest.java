// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.BinaryState;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;

/**
 * Unit tests for {@link GoogleDeviceSetup}.
 */
public class GoogleDeviceSetupTest extends TestCase {

    private GoogleDeviceSetup mDeviceSetup;
    private ITestDevice mMockDevice;
    private IDeviceBuildInfo mMockBuildInfo;

    private static final int DEFAULT_API_LEVEL = 23;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andReturn("foo").anyTimes();
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andStubReturn(null);
        mMockBuildInfo = new DeviceBuildInfo("0", "");
        mDeviceSetup = new GoogleDeviceSetup();
        mDeviceSetup.setDisableUseLocationPrompt(false);
    }

    public void testSetup_wake_double_tap_incompatible() throws DeviceNotAvailableException {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        EasyMock.expect(mMockDevice.getProperty("ro.product.name")).andReturn("incompatible");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setWakeDoubleTap(BinaryState.ON);
        try {
            mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("Expected TargetSetupError");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    public void testSetup_wake_double_tap_on() throws DeviceNotAvailableException,
            TargetSetupError {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        EasyMock.expect(mMockDevice.getProperty("ro.product.name")).andReturn("shamu");
        doCommandsExpectations(false, "echo on > /sys/bus/i2c/devices/1-004a/tsp");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setWakeDoubleTap(BinaryState.ON);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetup_wake_double_tap_off() throws DeviceNotAvailableException,
            TargetSetupError {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        EasyMock.expect(mMockDevice.getProperty("ro.product.name")).andReturn("shamu");
        doCommandsExpectations(false, "echo off > /sys/bus/i2c/devices/1-004a/tsp");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setWakeDoubleTap(BinaryState.OFF);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetup_wake_gesture_on() throws DeviceNotAvailableException, TargetSetupError {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        EasyMock.expect(mMockDevice.getProperty("ro.product.name")).andReturn("volantis");
        doCommandsExpectations(true,
                "echo 1 > /sys/devices/platform/spi-tegra114.2/spi_master/spi2/spi2.0/" +
                "input/input0/wake_gesture");
        doSettingExpectations("secure", "wake_gesture_enabled", "1");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setWakeGesture(BinaryState.ON);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetup_wake_gesture_off() throws DeviceNotAvailableException, TargetSetupError {
        doSetupExpectations();
        EasyMock.expect(mMockDevice.getProperty("ro.product.name")).andReturn("volantis");
        doCheckExternalStoreSpaceExpectations();
        doCommandsExpectations(true,
                "echo 0 > /sys/devices/platform/spi-tegra114.2/spi_master/spi2/spi2.0/" +
                "input/input0/wake_gesture");
        doSettingExpectations("secure", "wake_gesture_enabled", "0");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setWakeGesture(BinaryState.OFF);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetup_disable_use_location_preKLP() throws DeviceNotAvailableException,
            TargetSetupError {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        EasyMock.expect(mMockDevice.getApiLevel()).andReturn(18);
        doCommandsExpectations(false,
                "am start -a com.google.android.gsf.action.SET_USE_LOCATION_FOR_SERVICES " +
                "--ez disable true");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setDisableUseLocationPrompt(true);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetup_disable_use_location_postKLP()
            throws DeviceNotAvailableException, TargetSetupError {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        EasyMock.expect(mMockDevice.getApiLevel()).andReturn(19);
        // should not be a call to set location prompted here, expected no-op
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setDisableUseLocationPrompt(true);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetup_location_collection_on() throws DeviceNotAvailableException,
            TargetSetupError {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        doCommandsExpectations(true,
                "am broadcast -a 'com.google.gservices.intent.action.GSERVICES_OVERRIDE' " +
                "-e 'location:collection_enabled' '1'");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setLocationCollection(BinaryState.ON);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetup_location_collection_off() throws DeviceNotAvailableException,
            TargetSetupError {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        doCommandsExpectations(true,
                "am broadcast -a 'com.google.gservices.intent.action.GSERVICES_OVERRIDE' " +
                        "-e 'location:collection_enabled' '0'");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setLocationCollection(BinaryState.OFF);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetup_location_opt_in_on() throws DeviceNotAvailableException,
            TargetSetupError {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        doCommandsExpectations(true,
                "content insert --uri content://com.google.settings/partner " +
                "--bind name:s:use_location_for_services --bind value:s:1",
                "content insert --uri content://com.google.settings/partner " +
                "--bind name:s:network_location_opt_in --bind value:s:1");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setLocationOptIn(BinaryState.ON);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetup_location_opt_in_off() throws DeviceNotAvailableException, TargetSetupError {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        doCommandsExpectations(true,
                "content insert --uri content://com.google.settings/partner " +
                "--bind name:s:use_location_for_services --bind value:s:0",
                "content insert --uri content://com.google.settings/partner " +
                "--bind name:s:network_location_opt_in --bind value:s:0");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setLocationOptIn(BinaryState.OFF);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetup_cast_on() throws DeviceNotAvailableException, TargetSetupError {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        doCommandsExpectations(true,
                "am broadcast -a 'com.google.gservices.intent.action.GSERVICES_OVERRIDE' -e 'gms:cast:mdns_device_scanner:is_enabled' 'true'");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setCastBroadcast(BinaryState.ON);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetup_cast_off() throws DeviceNotAvailableException, TargetSetupError {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        doCommandsExpectations(true,
                "am broadcast -a 'com.google.gservices.intent.action.GSERVICES_OVERRIDE' -e 'gms:cast:mdns_device_scanner:is_enabled' 'false'");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setCastBroadcast(BinaryState.OFF);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetup_disable_playstore() throws DeviceNotAvailableException, TargetSetupError {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        doCommandsExpectations(false, "pm disable-user com.android.vending");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setDisablePlaystore(true);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetup_disable_volta() throws DeviceNotAvailableException, TargetSetupError {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        doCommandsExpectations(false, "pm disable-user com.google.android.volta");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setDisableVolta(true);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    @SuppressWarnings("deprecation")
    public void testSetup_legacy_postKLP() throws DeviceNotAvailableException, TargetSetupError {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        // extra getApiLevel call for disable location prompt check
        EasyMock.expect(mMockDevice.getApiLevel()).andReturn(22);
        doCommandsExpectations(true,
                "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true",
                "content insert --uri content://com.google.settings/partner --bind name:s:key " +
                "--bind value:s:value",
                "am broadcast -a 'com.google.gservices.intent.action.GSERVICES_OVERRIDE' " +
                "-e 'key' 'value'");
        doSettingExpectations("global", "airplane_mode_on", "1");
        doSettingExpectations("system", "key", "value");
        doSettingExpectations("secure", "key", "value");
        doSettingExpectations("global", "key", "value");
        doSettingExpectations("global", "multi_sim_voice_call", "1");
        doSettingExpectations("global", "multi_sim_data_call", "1");
        doSettingExpectations("global", "multi_sim_sms", "1");
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setDeprecatedAirplaneMode(true);
        mDeviceSetup.setDisableUseLocationPrompt(true); // Reset the default value
        mDeviceSetup.setDeprecatedDisableUseLocation(true);
        mDeviceSetup.setDeprecatedSetting("key", "value");
        mDeviceSetup.setDeprecatedSecureSetting("key", "value");
        mDeviceSetup.setDeprecatedPartnerSetting("key", "value");
        mDeviceSetup.setDeprecatedGlobalSetting("key", "value");
        mDeviceSetup.setDeprecatedGServicesSetting("key", "value");
        mDeviceSetup.setDeprecatedDefaultDataCallSubscription(1);
        mDeviceSetup.setDeprecatedDefaultVoiceCallSubscription(1);
        mDeviceSetup.setDeprecatedDefaultSmsSubscription(1);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    @SuppressWarnings("deprecation")
    public void testSetup_legacy_airplane_mode_conflict() throws DeviceNotAvailableException {
        doSetupExpectations();
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setAirplaneMode(BinaryState.ON);
        mDeviceSetup.setDeprecatedAirplaneMode(true);
        try {
            mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    @SuppressWarnings("deprecation")
    public void testSetup_legacy_use_location_prompt_conflict() throws DeviceNotAvailableException {
        doSetupExpectations();
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setDisableUseLocationPrompt(false);
        mDeviceSetup.setDeprecatedDisableUseLocation(false);
        try {
            mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    @SuppressWarnings("deprecation")
    public void testSetup_legacy_system_settings_conflict() throws DeviceNotAvailableException {
        doSetupExpectations();
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setSystemSetting("key", "value");
        mDeviceSetup.setDeprecatedSetting("key", "value");
        try {
            mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    @SuppressWarnings("deprecation")
    public void testSetup_legacy_secure_settings_conflict() throws DeviceNotAvailableException {
        doSetupExpectations();
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setSecureSetting("key", "value");
        mDeviceSetup.setDeprecatedSecureSetting("key", "value");
        try {
            mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    @SuppressWarnings("deprecation")
    public void testSetup_legacy_global_settings_conflict() throws DeviceNotAvailableException {
        doSetupExpectations();
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setGlobalSetting("key", "value");
        mDeviceSetup.setDeprecatedGlobalSetting("key", "value");
        try {
            mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    @SuppressWarnings("deprecation")
    public void testSetup_legacy_google_partner_settings_conflict()
            throws DeviceNotAvailableException {
        doSetupExpectations();
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setGooglePartnerSetting("key", "value");
        mDeviceSetup.setDeprecatedPartnerSetting("key", "value");
        try {
            mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    @SuppressWarnings("deprecation")
    public void testSetup_legacy_gservice_settings_conflict() throws DeviceNotAvailableException {
        doSetupExpectations();
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setGserviceSetting("key", "value");
        mDeviceSetup.setDeprecatedGServicesSetting("key", "value");
        try {
            mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    @SuppressWarnings("deprecation")
    public void testSetup_legacy_sim_voice_conflict() throws DeviceNotAvailableException {
        doSetupExpectations();
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setDefaultSimVoice(1);
        mDeviceSetup.setDeprecatedDefaultVoiceCallSubscription(1);
        try {
            mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    @SuppressWarnings("deprecation")
    public void testSetup_legacy_sim_data_conflict() throws DeviceNotAvailableException {
        doSetupExpectations();
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setDefaultSimData(1);
        mDeviceSetup.setDeprecatedDefaultDataCallSubscription(1);
        try {
            mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    @SuppressWarnings("deprecation")
    public void testSetup_legacy_sim_sms_conflict() throws DeviceNotAvailableException {
        doSetupExpectations();
        EasyMock.replay(mMockDevice);

        mDeviceSetup.setDefaultSimSms(1);
        mDeviceSetup.setDeprecatedDefaultSmsSubscription(1);
        try {
            mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    private void doSetupExpectations() throws DeviceNotAvailableException {
        doSetupExpectations(true /* screen on */, true /* root enabled */, true /* root response */,
                DEFAULT_API_LEVEL, new Capture<String>());
    }

    private void doSetupExpectations(boolean screenOn, boolean adbRootEnabled,
            boolean adbRootResponse, int apiLevel,
            Capture<String> setPropCapture) throws DeviceNotAvailableException {
        TestDeviceOptions options = new TestDeviceOptions();
        options.setEnableAdbRoot(adbRootEnabled);
        EasyMock.expect(mMockDevice.getOptions()).andReturn(options).once();
        if (adbRootEnabled) {
            EasyMock.expect(mMockDevice.enableAdbRoot()).andReturn(adbRootResponse);
        }
        EasyMock.expect(mMockDevice.clearErrorDialogs()).andReturn(Boolean.TRUE);
        EasyMock.expect(mMockDevice.getApiLevel()).andReturn(apiLevel);
        // expect push of local.prop file to change system properties
        EasyMock.expect(mMockDevice.pushString(EasyMock.capture(setPropCapture),
                EasyMock.contains("local.prop"))).andReturn(Boolean.TRUE);
        EasyMock.expect(mMockDevice.executeShellCommand(
                EasyMock.matches("chmod 644 .*local.prop"))).andReturn("");
        mMockDevice.reboot();
        if (screenOn) {
            EasyMock.expect(mMockDevice.executeShellCommand("svc power stayon true")).andReturn("");
            EasyMock.expect(mMockDevice.executeShellCommand("input keyevent 82")).andReturn("");
            EasyMock.expect(mMockDevice.executeShellCommand("input keyevent 3")).andReturn("");
        }
    }

    private void doCheckExternalStoreSpaceExpectations() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.getExternalStoreFreeSpace()).andReturn(1000l);
    }

    private void doCommandsExpectations(boolean settings, String... commands)
            throws DeviceNotAvailableException {
        if (settings) {
            EasyMock.expect(mMockDevice.getApiLevel()).andReturn(22);
        }
        for (String command : commands) {
            EasyMock.expect(mMockDevice.executeShellCommand(command)).andReturn("");
        }
    }

    private void doSettingExpectations(String namespace, String key, String value)
            throws DeviceNotAvailableException {
        mMockDevice.setSetting(namespace, key, value);
        EasyMock.expectLastCall();
    }
}
