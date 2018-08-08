package com.google.android.ota;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.OtaDeviceBuildInfo;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OtaDeviceLaunchControlProviderTest {

    OtaDeviceLaunchControlProvider mProvider;
    OtaDeviceBuildInfo mBuildInfo;
    IDeviceBuildInfo mDevBuildInfo1 = new DeviceBuildInfo();
    IDeviceBuildInfo mDevBuildInfo2 = new DeviceBuildInfo();

    @Before
    public void setUp() throws Exception {
        mProvider = new OtaDeviceLaunchControlProvider();
        mBuildInfo = new OtaDeviceBuildInfo();
        mBuildInfo.setBaselineBuild(mDevBuildInfo1);
        mBuildInfo.setOtaBuild(mDevBuildInfo2);
    }

    @Test
    public void testSetupSourceBuild_noSwap() {
        mProvider.setOtaBuildAsSource(false);
        mProvider.setupSourceBuild(mBuildInfo);
        Assert.assertEquals(mDevBuildInfo1, mBuildInfo.getBaselineBuild());
        Assert.assertEquals(mDevBuildInfo2, mBuildInfo.getOtaBuild());
    }

    @Test
    public void testSetupSourceBuild_swap() {
        mProvider.setOtaBuildAsSource(true);
        mProvider.setupSourceBuild(mBuildInfo);
        Assert.assertEquals(mDevBuildInfo2, mBuildInfo.getBaselineBuild());
        Assert.assertEquals(mDevBuildInfo1, mBuildInfo.getOtaBuild());
    }
}
