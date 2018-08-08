// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep.clockwork;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link ClockworkDeviceSetup}.
 */
public class ClockworkDeviceSetupTest extends TestCase {

    private ClockworkDeviceSetup mDeviceSetup;
    private ITestDevice mMockDevice;
    private IDeviceBuildInfo mMockBuildInfo;
    private OptionSetter mSetter;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andReturn("foo").anyTimes();
        mMockBuildInfo = new DeviceBuildInfo("0", "");
        mDeviceSetup = new ClockworkDeviceSetup();
        mSetter = new OptionSetter(mDeviceSetup);
    }

    public void testSetupSkipTutorialEmerald()
            throws DeviceNotAvailableException, TargetSetupError, ConfigurationException {
        doSetupExpectations();
        doCommandsExpectations(
                "am startservice -a com.google.android.clockwork.action.TUTORIAL_SKIP");
        EasyMock.expect(mMockDevice.getApiLevel()).andReturn(23);
        EasyMock.replay(mMockDevice);

        mSetter.setOptionValue("skip-tutorial", "true");
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetupSkipTutorial()
            throws DeviceNotAvailableException, TargetSetupError, ConfigurationException {
        doSetupExpectations();
        doCommandsExpectations(
                "am broadcast -a com.google.android.clockwork.action.TUTORIAL_SKIP");
        EasyMock.expect(mMockDevice.getApiLevel()).andReturn(24);
        EasyMock.replay(mMockDevice);

        mSetter.setOptionValue("skip-tutorial", "true");
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetupTestMode()
            throws DeviceNotAvailableException, TargetSetupError, ConfigurationException {
        doSetupExpectations();
        doCommandsExpectations("am broadcast -a com.google.android.clockwork.action.TEST_MODE");
        EasyMock.replay(mMockDevice);

        mSetter.setOptionValue("test-mode", "true");
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetupDisableAmbient()
            throws DeviceNotAvailableException, TargetSetupError, ConfigurationException {
        doSetupExpectations();
        doCommandsExpectations(
                "am broadcast -a com.google.android.clockwork.settings.SYNC_AMBIENT_DISABLED"
                        + " --ez ambient_disabled true");
        EasyMock.replay(mMockDevice);

        mSetter.setOptionValue("disable-ambient", "true");
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetupDisableGaze()
            throws DeviceNotAvailableException, TargetSetupError, ConfigurationException {
        doSetupExpectations();
        doCommandsExpectations(
                "am broadcast -a com.google.gservices.intent.action.GSERVICES_OVERRIDE "
                        + "-e cw:smart_illuminate_enabled false");
        EasyMock.replay(mMockDevice);

        mSetter.setOptionValue("disable-gaze", "true");
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetupDisableUngaze()
            throws DeviceNotAvailableException, TargetSetupError, ConfigurationException {
        doSetupExpectations();
        doCommandsExpectations(
                "am broadcast -a com.google.gservices.intent.action.GSERVICES_OVERRIDE "
                        + "-e cw:ungaze_default_setting false");
        EasyMock.replay(mMockDevice);

        mSetter.setOptionValue("disable-ungaze", "true");
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetupEnterRetailEmerald()
            throws DeviceNotAvailableException, TargetSetupError, ConfigurationException {
        doSetupExpectations();
        doCommandsExpectations(
                "am startservice -a com.google.android.clockwork.settings.ENTER_RETAIL_FOR_TEST");
        EasyMock.expect(mMockDevice.getApiLevel()).andReturn(23);
        EasyMock.replay(mMockDevice);

        mSetter.setOptionValue("enter-retail", "true");
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetupEnterRetail()
            throws DeviceNotAvailableException, TargetSetupError, ConfigurationException {
        doSetupExpectations();
        doCommandsExpectations(
                "am broadcast -a com.google.android.clockwork.action.START_RETAIL_MODE");
        EasyMock.expect(mMockDevice.getApiLevel()).andReturn(24);
        EasyMock.replay(mMockDevice);

        mSetter.setOptionValue("enter-retail", "true");
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetupWifiEmerald()
            throws DeviceNotAvailableException, TargetSetupError, ConfigurationException {
        String wifiSsid = "test";
        String wifiServiceStr = "com.google.android.apps.wearable.settings/" +
                "com.google.android.clockwork.settings.wifi.WifiSettingsService";
        doSetupExpectations();
        doCommandsExpectations(
                "content update --uri content://com.google.android.wearable.settings/"
                        + "auto_wifi --bind auto_wifi:i:0",
                "svc wifi enable",
                String.format("am startservice %s", wifiServiceStr),
                String.format("dumpsys activity service %s %s 0 ''", wifiServiceStr, wifiSsid),
                String.format("am stopservice %s", wifiServiceStr));
        EasyMock.expect(mMockDevice.getApiLevel()).andReturn(23);
        EasyMock.replay(mMockDevice);

        mSetter.setOptionValue("cw-wifi-network", wifiSsid);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetupWifiPskEmerald()
            throws DeviceNotAvailableException, TargetSetupError, ConfigurationException {
        String wifiSsid = "test";
        String wifiPsk = "pw";
        String wifiServiceStr = "com.google.android.apps.wearable.settings/" +
                "com.google.android.clockwork.settings.wifi.WifiSettingsService";
        doSetupExpectations();
        doCommandsExpectations(
                "content update --uri content://com.google.android.wearable.settings/"
                        + "auto_wifi --bind auto_wifi:i:0",
                "svc wifi enable",
                String.format("am startservice %s", wifiServiceStr),
                String.format("dumpsys activity service %s %s 2 %s", wifiServiceStr, wifiSsid,
                        wifiPsk),
                String.format("am stopservice %s", wifiServiceStr));
        EasyMock.expect(mMockDevice.getApiLevel()).andReturn(23);
        EasyMock.replay(mMockDevice);

        mSetter.setOptionValue("cw-wifi-network", wifiSsid);
        mSetter.setOptionValue("cw-wifi-psk", wifiPsk);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetupWifi()
            throws DeviceNotAvailableException, TargetSetupError, ConfigurationException {
        String wifiSsid = "test";
        String wifiServiceStr = "com.google.android.apps.wearable.settings/" +
                "com.google.android.clockwork.settings.wifi.WifiSettingsService";
        doSetupExpectations();
        doCommandsExpectations(
                "svc wifi enable",
                String.format("am startservice %s", wifiServiceStr),
                String.format("dumpsys activity service %s %s 0 ''", wifiServiceStr, wifiSsid),
                String.format("am stopservice %s", wifiServiceStr));
        EasyMock.expect(mMockDevice.getApiLevel()).andReturn(24);
        EasyMock.replay(mMockDevice);

        mSetter.setOptionValue("cw-wifi-network", wifiSsid);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    public void testSetupWifiPsk()
            throws DeviceNotAvailableException, TargetSetupError, ConfigurationException {
        String wifiSsid = "test";
        String wifiPsk = "pw";
        String wifiServiceStr = "com.google.android.apps.wearable.settings/" +
                "com.google.android.clockwork.settings.wifi.WifiSettingsService";
        doSetupExpectations();
        doCommandsExpectations(
                "svc wifi enable",
                String.format("am startservice %s", wifiServiceStr),
                String.format("dumpsys activity service %s %s 2 %s", wifiServiceStr, wifiSsid,
                        wifiPsk),
                String.format("am stopservice %s", wifiServiceStr));
        EasyMock.expect(mMockDevice.getApiLevel()).andReturn(24);
        EasyMock.replay(mMockDevice);

        mSetter.setOptionValue("cw-wifi-network", wifiSsid);
        mSetter.setOptionValue("cw-wifi-psk", wifiPsk);
        mDeviceSetup.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice);
    }

    /**
     * Set EasyMock expectations for a normal setup call
     */
    private void doSetupExpectations()
            throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.enableAdbRoot()).andReturn(Boolean.TRUE);
    }

    private void doCommandsExpectations(String... commands)
            throws DeviceNotAvailableException {
        for (String command : commands) {
            EasyMock.expect(mMockDevice.executeShellCommand(command)).andReturn("");
        }
    }
}
