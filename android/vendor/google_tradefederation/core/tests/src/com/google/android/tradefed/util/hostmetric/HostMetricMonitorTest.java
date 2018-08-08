// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util.hostmetric;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link HostMetricMonitor}.
 */
public class HostMetricMonitorTest extends TestCase {

    private IHostMetricAgent mMockMetricAgent;
    private IRunUtil mMockRunUtil;
    private List<DeviceDescriptor> mDevices;
    private HostMetricMonitor mMonitor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockMetricAgent = EasyMock.createMock(IHostMetricAgent.class);
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mDevices = new LinkedList<>();
        mMonitor = new HostMetricMonitor() {
            @Override
            IHostMetricAgent getMetricAgent() {
                return mMockMetricAgent;
            }

            @Override
            IRunUtil getRunUtil() {
                return mMockRunUtil;
            }

            @Override
            List<DeviceDescriptor> listDevices() {
                return mDevices;
            }
        };
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testDispatch() {
        int deviceCount = 100;
        for (int i = 0; i < deviceCount; i++) {
            mDevices.add(new DeviceDescriptor(Integer.toString(i), false,
                    DeviceAllocationState.Available, "product", "product_variant", "sdk_version",
                    "build_id", "50"));
        }
        mMockMetricAgent.emitValue(EasyMock.eq(IHostMetricAgent.DEVICE_COUNT_METRIC),
                EasyMock.eq(1L), EasyMock.<Map<String, String>> anyObject());
        EasyMock.expectLastCall().times(deviceCount);
        mMockMetricAgent.emitValue(EasyMock.eq(IHostMetricAgent.DEVICE_ALLOCATED_COUNT_METRIC),
                EasyMock.eq(0L), EasyMock.<Map<String, String>> anyObject());
        EasyMock.expectLastCall().times(deviceCount);
        mMockMetricAgent.emitValue(EasyMock.eq(IHostMetricAgent.DEVICE_BATTERY_METRIC),
                EasyMock.anyLong(), EasyMock.<Map<String, String>> anyObject());
        EasyMock.expectLastCall().times(deviceCount);
        mMockMetricAgent.flush();
        EasyMock.replay(mMockMetricAgent);

        mMonitor.getMetricDispatcher().dispatch();
        EasyMock.verify(mMockMetricAgent);
    }

    public void testDispatch_unexpectedBatteryLevel() {
        mDevices.add(new DeviceDescriptor("0", false, DeviceAllocationState.Available, "product",
                "product_variant", "sdk_version", "build_id", "unexpected_battery_level"));
        mMockMetricAgent.emitValue(EasyMock.eq(IHostMetricAgent.DEVICE_COUNT_METRIC),
                EasyMock.eq(1L), EasyMock.<Map<String, String>> anyObject());
        mMockMetricAgent.emitValue(EasyMock.eq(IHostMetricAgent.DEVICE_ALLOCATED_COUNT_METRIC),
                EasyMock.eq(0L), EasyMock.<Map<String, String>> anyObject());
        mMockMetricAgent.emitValue(EasyMock.eq(IHostMetricAgent.DEVICE_BATTERY_METRIC),
                EasyMock.eq(-1L), EasyMock.<Map<String, String>> anyObject());
        mMockMetricAgent.flush();
        EasyMock.replay(mMockMetricAgent);

        mMonitor.getMetricDispatcher().dispatch();
        EasyMock.verify(mMockMetricAgent);
    }
}
