// Copyright 2013 Google Inc. All Rights Reserved.
package com.google.tradefed.latencylab.test;


import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.command.remote.ICommandResultHandler;
import com.android.tradefed.command.remote.RemoteClient;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.FreeDeviceState;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * An end to end test that connects to remote TF, waits for a device, flashes device, then exits
 */
public class RemoteImageTest {

    private static final long AVAIL_DEVICE_WAIT_TIME = 10 * 1000;
    private static final long POLL_TIME = 2 *1000;
    private static final long IMAGE_DEVICE_WAIT_TIME = 15 * 60 * 1000;

    private static class CommandResultHandler implements ICommandResultHandler {
        private boolean mIsSuccess = false;
        private boolean mIsComplete = false;

        @Override
        public void success() {
            System.out.println("command succeeded!");
            mIsSuccess = true;
            setComplete();
        }

        public boolean didSucceed() {
            return mIsSuccess;
        }

        public boolean isComplete() {
            return mIsComplete;
        }

        @Override
        public void failure(String errorDetails, FreeDeviceState deviceState) {
            System.out.println("command failed!");
            setComplete();
        }

        @Override
        public void stillRunning() {
            // do nothing
        }

        @Override
        public void notAllocated() {
            System.out.println("device not allocated??");
            setComplete();
        }

        @Override
        public void noActiveCommand() {
            System.out.println("no active command??");
            setComplete();
        }

        private void setComplete() {
            mIsComplete = true;
        }
    }

    private RemoteClient mTfClient;

    @Before
    public void setUp() throws Exception {
        mTfClient = RemoteClient.connect();
    }

    @Test
    public void testRemoteImage() throws Exception {
        String deviceSerial = findAvailableDevice(mTfClient);
        mTfClient.sendAllocateDevice(deviceSerial);
        imageDevice(mTfClient, deviceSerial);
        mTfClient.sendFreeDevice(deviceSerial);
    }

    @After
    public void tearDown() {
        if (mTfClient != null) {
            mTfClient.close();
        }
    }

    private String findAvailableDevice(RemoteClient tfClient) throws Exception {
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) < AVAIL_DEVICE_WAIT_TIME) {
            List<DeviceDescriptor> devices = tfClient.sendListDevices();
            for (DeviceDescriptor device : devices) {
                if (device.getState().equals(DeviceAllocationState.Available)) {
                    return device.getSerial();
                }
            }
            Thread.sleep(POLL_TIME);
        }
        Assert.fail(String.format("Could not find avail device after %s ms",
                       AVAIL_DEVICE_WAIT_TIME));
        // should never reach this
        return null;
    }

    private void imageDevice(RemoteClient tfClient, String deviceSerial) throws Exception {
        // TODO: don't hardcode the device image path
        tfClient.sendExecCommand(deviceSerial, new String[] {
                "google_latency_lab/flash", "--build-dir", "/usr/local/google/flash/yakju_JWR67E"
        });
        CommandResultHandler handler = new CommandResultHandler();
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) < IMAGE_DEVICE_WAIT_TIME
                && !handler.isComplete()) {
            tfClient.sendGetLastCommandResult(deviceSerial, handler);
            Thread.sleep(POLL_TIME);
        }
        Assert.assertTrue(handler.didSucceed());
    }
}
