//Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.ITestInvocationListener;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link ClockworkTest}.
 */
@RunWith(JUnit4.class)
public class ClockworkTestTest {

    private ITestDevice mMockWatchDevice1;
    private ITestDevice mMockWatchDevice2;
    private ITestDevice mMockCompanionDevice1;
    private ITestDevice mMockCompanionDevice2;
    private ClockworkTest mTestInstance;

    @Before
    public void setUp() throws DeviceNotAvailableException {
        mMockWatchDevice1 = EasyMock.createMock(ITestDevice.class);
        mMockWatchDevice2 = EasyMock.createMock(ITestDevice.class);
        mMockCompanionDevice1 = EasyMock.createMock(ITestDevice.class);
        mMockCompanionDevice2 = EasyMock.createMock(ITestDevice.class);

        EasyMock.expect(mMockWatchDevice1.getProperty("ro.build.characteristics"))
                .andReturn("nosdcard,watch");
        EasyMock.expect(mMockWatchDevice2.getProperty("ro.build.characteristics"))
                .andReturn("nosdcard,watch");
        EasyMock.expect(mMockCompanionDevice1.getProperty("ro.build.characteristics"))
                .andReturn("nosdcard");
        EasyMock.expect(mMockCompanionDevice2.getProperty("ro.build.characteristics"))
                .andReturn("nosdcard");

        mTestInstance = new ClockworkTestConcrete();
    }

    @Test(expected = RuntimeException.class)
    public void testSetDeviceInfos_notEnoughDevices() {
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("watch1", mMockWatchDevice1);
        context.addDeviceBuildInfo("watch1", null);
        EasyMock.replay(mMockWatchDevice1);
        mTestInstance.setDeviceInfos(context.getDeviceBuildMap());
        EasyMock.verify(mMockWatchDevice1);
    }

    @Test(expected = RuntimeException.class)
    public void testSetDeviceInfos_moreThanOneCompanions() {
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("watch1", mMockWatchDevice1);
        context.addDeviceBuildInfo("watch1", null);
        context.addAllocatedDevice("companion1", mMockCompanionDevice1);
        context.addDeviceBuildInfo("companion1", null);
        context.addAllocatedDevice("companion2", mMockCompanionDevice2);
        context.addDeviceBuildInfo("companion2", null);
        EasyMock.replay(mMockWatchDevice1, mMockCompanionDevice1, mMockCompanionDevice2);
        mTestInstance.setDeviceInfos(context.getDeviceBuildMap());
        EasyMock.verify(mMockWatchDevice1, mMockCompanionDevice1, mMockCompanionDevice2);
    }

    @Test(expected = RuntimeException.class)
    public void testSetDeviceInfos_noCompanion() {
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("watch1", mMockWatchDevice1);
        context.addDeviceBuildInfo("watch1", null);
        context.addAllocatedDevice("watch2", mMockWatchDevice2);
        context.addDeviceBuildInfo("watch2", null);
        EasyMock.replay(mMockWatchDevice1, mMockWatchDevice2);
        mTestInstance.setDeviceInfos(context.getDeviceBuildMap());
        EasyMock.verify(mMockWatchDevice1, mMockWatchDevice2);
    }

    @Test(expected = RuntimeException.class)
    public void testSetDeviceInfos_noWatches() {
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("companion1", mMockCompanionDevice1);
        context.addDeviceBuildInfo("companion1", null);
        context.addAllocatedDevice("companion2", mMockCompanionDevice2);
        context.addDeviceBuildInfo("companion2", null);
        EasyMock.replay(mMockCompanionDevice1, mMockCompanionDevice2);
        mTestInstance.setDeviceInfos(context.getDeviceBuildMap());
        EasyMock.verify(mMockCompanionDevice1, mMockCompanionDevice2);
    }

    @Test
    public void testSetDeviceInfos_correctDeviceInfos() {
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("watch1", mMockWatchDevice1);
        context.addDeviceBuildInfo("watch1", null);
        context.addAllocatedDevice("watch2", mMockWatchDevice2);
        context.addDeviceBuildInfo("watch2", null);
        context.addAllocatedDevice("companion1", mMockCompanionDevice1);
        context.addDeviceBuildInfo("companion1", null);
        EasyMock.replay(mMockWatchDevice1, mMockWatchDevice2, mMockCompanionDevice1);
        mTestInstance.setDeviceInfos(context.getDeviceBuildMap());
        EasyMock.verify(mMockWatchDevice1, mMockWatchDevice2, mMockCompanionDevice1);
        Assert.assertEquals(2, mTestInstance.getDeviceList().size());
        Assert.assertEquals(3, mTestInstance.getInfoMap().size());
        Assert.assertNotNull(mTestInstance.getCompanion());
    }

    private class ClockworkTestConcrete extends ClockworkTest {

        @Override
        public void run(ITestInvocationListener iTestInvocationListener)
                throws DeviceNotAvailableException {
            // Do nothing
        }
    }
}
