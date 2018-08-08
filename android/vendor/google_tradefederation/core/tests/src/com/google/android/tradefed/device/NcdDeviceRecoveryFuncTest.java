// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.ddmlib.Log;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.testtype.DeviceTestCase;

import java.util.Set;

/**
 * Functional tests for {@link NcdDeviceRecovery}.
 * <p/>
 * Tests that device can be reset
 */
public class NcdDeviceRecoveryFuncTest extends DeviceTestCase {

    private static final String LOG_TAG = "NcdDeviceRecoveryFuncTest";

    private NcdDeviceRecovery mRecovery;

    public NcdDeviceRecoveryFuncTest() {
        super();
    }

    public NcdDeviceRecoveryFuncTest(String testName) {
        super(testName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRecovery = new NcdDeviceRecovery();
    }

    /**
     * Test {@link NcdDeviceRecovery#resetDevice(String)} when an invalid ncd
     * path has been provided.
     */
    public void testResetDevice_badNcd() {
        String orgPath = mRecovery.getNcdPath();
        mRecovery.setNcdPath("/noexist/ncd.py");
        try {
            assertFalse(mRecovery.resetDevice(getDevice().getSerialNumber()));
        } finally {
            mRecovery.setNcdPath(orgPath);
        }
    }

    /**
     * Test {@link NcdDeviceRecovery#resetUsb(String)} when an invalid ncd
     * path has been provided.
     */
    public void testResetUsb_badNcd() {
        String orgPath = mRecovery.getNcdPath();
        mRecovery.setNcdPath("/noexist/ncd.py");
        try {
            assertFalse(mRecovery.resetUsb(getDevice().getSerialNumber()));
        } finally {
            mRecovery.setNcdPath(orgPath);
        }
    }

    /**
     * Test {@link NcdDeviceRecovery#resetUsb(String)}
     */
    public void testResetUsb() throws InterruptedException {
        DeviceMonitorThread thread = new DeviceMonitorThread();
        thread.start();
        // wait for thread to start
        synchronized (thread) {
            thread.wait(3*1000);
        }
        assertTrue(mRecovery.resetUsb(getDevice().getSerialNumber()));
        synchronized (thread) {
            thread.wait(2*60*1000);
        }
        assertTrue(thread.mComplete);
    }

    /**
     * Test {@link NcdDeviceRecovery#resetUsb(String)}
     */
    public void testResetDevice() throws InterruptedException {
        DeviceMonitorThread thread = new DeviceMonitorThread();
        thread.start();
        // wait for thread to start
        synchronized (thread) {
            thread.wait(3*1000);
        }
        assertTrue(mRecovery.resetDevice(getDevice().getSerialNumber()));
        synchronized (thread) {
            thread.wait(5*60*1000);
        }
        assertTrue(thread.mComplete);
    }

    /**
     * Tests that all devices are visible via ncd.
     */
    public void testAllDevicesOnNcd() {
        Set<String> ncdDevices = mRecovery.getDeviceList();

        IDeviceManager deviceMgr = getDeviceManager();

        for (DeviceDescriptor deviceDesc : deviceMgr.listAllDevices()) {
            if (deviceDesc.getState() != DeviceAllocationState.Unavailable) {
                assertTrue(String.format("Device %s is not visible to ncd", deviceDesc.getSerial()),
                        ncdDevices.remove(deviceDesc.getSerial()));
            }
        }

        StringBuilder remainingDevices = new StringBuilder();
        for (String remainingDevice: ncdDevices ) {
            remainingDevices.append(remainingDevice);
            remainingDevices.append(", ");
        }
        assertTrue(String.format("Devices %s are listed in ncd.py -p but are not available",
                remainingDevices.toString()), ncdDevices.isEmpty());
    }

    private IDeviceManager getDeviceManager() {
        return GlobalConfiguration.getDeviceManagerInstance();
    }

    /**
     * Helper class that waits for device to become unavailable then available
     */
    private class DeviceMonitorThread extends Thread {

        boolean mComplete = false;

        @Override
        public void run() {
            synchronized (this) {
                notify();
            }
            if (getDevice().waitForDeviceNotAvailable(30*1000)) {
                try {
                    getDevice().waitForDeviceAvailable();
                    mComplete = true;
                    synchronized (this) {
                        notify();
                    }
                } catch (DeviceNotAvailableException e) {
                    Log.e(LOG_TAG, "device did not become available");
                }
            } else {
                Log.e(LOG_TAG, "device did not become unavailable");
            }
        }
    }
}
