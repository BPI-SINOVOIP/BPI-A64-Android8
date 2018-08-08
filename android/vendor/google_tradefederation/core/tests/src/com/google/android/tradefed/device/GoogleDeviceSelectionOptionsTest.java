// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.device.TcpDevice;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test class for {@link GoogleDeviceSelectionOptions}.
 */
public class GoogleDeviceSelectionOptionsTest {

    private GoogleDeviceSelectionOptions mDeviceSelection;

    @Before
    public void setUp() {
        mDeviceSelection = new GoogleDeviceSelectionOptions();
    }

    /**
     * When a gce-device is requested, it can be matched.
     */
    @Test
    public void testAllocateGceDevice() throws ConfigurationException {
        IDevice gceDevice = new RemoteAvdIDevice("gce-device:0");
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        setter.setOptionValue("gce-device", "true");
        Assert.assertTrue(mDeviceSelection.matches(gceDevice));
    }

    /** When a gce-device is presented but no --gce-device flag, it can't be matched. */
    @Test
    public void testAllocateGceDevice_default() {
        IDevice gceDevice = new RemoteAvdIDevice("gce-device:0");
        Assert.assertFalse(mDeviceSelection.matches(gceDevice));
    }

    /** When a gce-device is requested by serial. other gce-device serial should be rejected. */
    @Test
    public void testAllocateGceDevice_bySerial() throws ConfigurationException {
        IDevice gceDevice = new RemoteAvdIDevice("gce-device:1");
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        setter.setOptionValue("gce-device", "true");
        setter.setOptionValue("serial", "gce-device:0");
        Assert.assertFalse(mDeviceSelection.matches(gceDevice));
    }

    /** When a tcp-device is requested, a gce-device cannot be allocated for it. */
    @Test
    public void testAllocateGceDevice_whenTcpRequested() throws ConfigurationException {
        IDevice gceDevice = new RemoteAvdIDevice("gce-device:0");
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        setter.setOptionValue("tcp-device", "true");
        Assert.assertFalse(mDeviceSelection.matches(gceDevice));
    }

    /** When a tcp-device is requested, a tcp-device can be allocated for it. */
    @Test
    public void testAllocateTcpDevice_whenTcpRequested() throws ConfigurationException {
        IDevice gceDevice = new TcpDevice("tcp-device:0");
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        setter.setOptionValue("tcp-device", "true");
        Assert.assertTrue(mDeviceSelection.matches(gceDevice));
    }

    /** When a gce-device is requested, a serial is provided that match a non gce-device. */
    @Test
    public void testAllocateDeviceMatch_gceRequested() throws ConfigurationException {
        IDevice gceDevice = new StubDevice("stub-device:0");
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        // mis-match of expectation: request gce-device with a serial of a device that is not one.
        setter.setOptionValue("gce-device", "true");
        setter.setOptionValue("serial", "stub-device:0");
        Assert.assertFalse(mDeviceSelection.matches(gceDevice));
    }

    /** When a gce-device is requested, a serial is provided that doens't match a non gce-device. */
    @Test
    public void testAllocateDevice_NoMatch_gceRequested() throws ConfigurationException {
        IDevice gceDevice = new StubDevice("stub-device:0");
        OptionSetter setter = new OptionSetter(mDeviceSelection);
        // mis-match of expectation: request gce-device with a serial of a device that is not one.
        setter.setOptionValue("gce-device", "true");
        setter.setOptionValue("serial", "stub-device:1");
        Assert.assertFalse(mDeviceSelection.matches(gceDevice));
    }

    /**
     * When the base {@link DeviceSelectionOptions} is used to match a tcp-device and gce-device are
     * available, it should not use them.
     */
    @Test
    public void testAllocateTcpDevice_forGceDevice() throws ConfigurationException {
        DeviceSelectionOptions deviceSelection = new DeviceSelectionOptions();
        IDevice gceDevice = new RemoteAvdIDevice("gce-device:0");
        OptionSetter setter = new OptionSetter(deviceSelection);
        setter.setOptionValue("tcp-device", "true");
        Assert.assertFalse(deviceSelection.matches(gceDevice));
    }

    /**
     * When no particular device is requested with the base {@link DeviceSelectionOptions},
     * gce-device should not be allowed.
     */
    @Test
    public void testAllocateNoDevice_forGceDevice_noOption() {
        DeviceSelectionOptions deviceSelection = new DeviceSelectionOptions();
        IDevice gceDevice = new RemoteAvdIDevice("gce-device:0");
        Assert.assertFalse(deviceSelection.matches(gceDevice));
    }
}
