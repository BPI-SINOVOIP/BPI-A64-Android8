// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.adb;

import static com.android.tradefed.device.TestDeviceState.ONLINE;

import static org.junit.Assert.assertEquals;

import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.MockDeviceManager;
import com.android.tradefed.device.NullDevice;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AdbTestClusterCommandScheduler}. */
@RunWith(JUnit4.class)
public class AdbTestClusterCommandSchedulerTest {
    private MockDeviceManager mMockManager;
    private AdbTestClusterCommandScheduler mAdbScheduler;

    @Before
    public void setUp() throws Exception {
        mMockManager = new MockDeviceManager(0);

        mAdbScheduler =
                new AdbTestClusterCommandScheduler() {
                    @Override
                    protected IDeviceManager getDeviceManager() {
                        return mMockManager;
                    }

                    @Override
                    protected void initLogging() {
                        // ignore
                    }

                    @Override
                    protected void cleanUp() {
                        // ignore
                    }
                };
    }

    @After
    public void tearDown() throws Exception {
        if (mAdbScheduler != null) {
            mAdbScheduler.shutdown();
        }
    }

    /** Switch all mock objects to replay mode */
    private void replayMocks(Object... additionalMocks) {
        for (Object mock : additionalMocks) {
            EasyMock.replay(mock);
        }
    }

    /** Verify all mock objects */
    private void verifyMocks(Object... additionalMocks) {
        for (Object mock : additionalMocks) {
            EasyMock.verify(mock);
        }
        mMockManager.assertDevicesFreed();
    }

    /** Test if adb bridge is stopped then restarted */
    @Test
    public void testStopAndRestartAdb() throws Throwable {
        String[] args = new String[] {"empty", "-n"};
        mMockManager.setNumDevicesStub(1, ONLINE, new NullDevice("foo"));

        replayMocks();
        mAdbScheduler.start();
        mAdbScheduler.addCommand(args);
        mAdbScheduler.shutdownOnEmpty();
        mAdbScheduler.join(2 * 1000);
        verifyMocks();

        assertEquals(1, mMockManager.getStopAdbBridgeCallCount());
        assertEquals(1, mMockManager.getRestartAdbBridgeCallCount());
    }
}
