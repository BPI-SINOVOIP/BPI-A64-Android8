// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.IMultiDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.clockwork.ClockworkUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base test class for clockwork tests that paired with companion devices.
 */
public abstract class ClockworkTest implements IMultiDeviceTest, IRemoteTest {

    private ITestDevice mCompanion = null;
    private List<ITestDevice> mDeviceList = new ArrayList<>();
    private Map<ITestDevice, IBuildInfo> mInfoMap = null;
    protected static final String WATCH = "WATCH";
    protected static final String PHONE = "PHONE";

    /**
     * Get companion device instance
     *
     * @return {@link ITestDevice} instance of companion device
     */
    protected ITestDevice getCompanion() {
        return mCompanion;
    }

    /**
     * Get one of watch device instance
     *
     * @return {@link ITestDevice} instance of first watch device
     */
    protected ITestDevice getDevice() {
        return mDeviceList.get(0);
    }

    /**
     * Get a list of watch device instances
     *
     * @return a list of {@link ITestDevice} of watch devices
     */
    protected List<ITestDevice> getDeviceList() {
        return mDeviceList;
    }

    /**
     * Get an information map for all devices in this test
     *
     * @return a map of {@link ITestDevice} to {@link IBuildInfo}
     */
    protected Map<ITestDevice, IBuildInfo> getInfoMap() {
        return mInfoMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceInfos(Map<ITestDevice, IBuildInfo> deviceInfos) {
        ClockworkUtils cwUtils = new ClockworkUtils();
        mCompanion = cwUtils.setUpMultiDevice(deviceInfos, mDeviceList);
        mInfoMap = deviceInfos;
    }
}
