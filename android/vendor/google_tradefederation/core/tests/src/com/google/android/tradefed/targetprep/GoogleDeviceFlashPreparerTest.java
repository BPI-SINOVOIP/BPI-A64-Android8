// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.CdmaDeviceFlasher;
import com.android.tradefed.targetprep.IDeviceFlasher;
import com.android.tradefed.util.FileUtil;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/** Unit tests to make sure that {@link GoogleDeviceFlashPreparer} is behaving reasonably */
@RunWith(JUnit4.class)
public class GoogleDeviceFlashPreparerTest {
    private ITestDevice mMockDevice = null;
    private IDeviceBuildInfo mMockBuildInfo;
    private GoogleDeviceFlashPreparer mPreparer = null;
    private File mLcCache = null;

    @Before
    public void setUp() throws Exception {
        mMockDevice = EasyMock.createStrictMock(ITestDevice.class);
        mPreparer = new GoogleDeviceFlashPreparer();
        OptionSetter setter = new OptionSetter(mPreparer);
        mLcCache = FileUtil.createTempDir("lc_cache");
        setter.setOptionValue("download-cache-dir", mLcCache.getAbsolutePath());
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mLcCache);
    }

    /**
     * Verify that we get the Cdma flasher for a device that reports having the Crespo-S bootloader
     */
    @Test
    public void testCrespoS() throws Exception {
        EasyMock.expect(mMockDevice.getProductType()).andReturn("herring");
        EasyMock.expect(mMockDevice.getBootloaderVersion()).andReturn("D720SPRKC4");
        EasyMock.replay(mMockDevice);

        IDeviceFlasher flasher = mPreparer.createFlasher(mMockDevice);
        assertTrue(flasher instanceof CdmaDeviceFlasher);
        EasyMock.verify(mMockDevice);
    }

    /**
     * Verify that we get the (GSM) Crespo flasher for a device that reports having the GSM Crespo
     * bootloader
     */
    @Test
    public void testCrespoGsm() throws Exception {
        EasyMock.expect(mMockDevice.getProductType()).andReturn("herring");
        EasyMock.expect(mMockDevice.getBootloaderVersion()).andReturn("I9020XXKA3");
        EasyMock.replay(mMockDevice);

        IDeviceFlasher flasher = mPreparer.createFlasher(mMockDevice);
        assertTrue(flasher instanceof CrespoDeviceFlasher);
        EasyMock.verify(mMockDevice);
    }


    /** Verify that we get the NexusOne flasher when expected. */
    @Test
    public void testNexusOne() throws Exception {
        EasyMock.expect(mMockDevice.getProductType()).andReturn("mahimahi");
        EasyMock.expect(mMockDevice.getProductVariant()).andReturn("");
        EasyMock.replay(mMockDevice);

        IDeviceFlasher flasher = mPreparer.createFlasher(mMockDevice);
        assertTrue(flasher instanceof NexusOneDeviceFlasher);
        EasyMock.verify(mMockDevice);
    }

    /** Verify that we get the NexusOne flasher when expected. */
    @Test
    public void testStingray() throws Exception {
        mMockDevice = EasyMock.createNiceMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getProductType()).andReturn("xoom-cdma");
        EasyMock.expect(mMockDevice.getProductVariant()).andReturn("");
        EasyMock.replay(mMockDevice);

        IDeviceFlasher flasher = mPreparer.createFlasher(mMockDevice);
        assertTrue(flasher instanceof StingrayDeviceFlasher);
        EasyMock.verify(mMockDevice);
    }

    /** Verify that we get the PrimeGsm flasher when expected. */
    @Test
    public void testPrimeGsm() throws Exception {
        EasyMock.expect(mMockDevice.getProductType()).andReturn("tuna");
        EasyMock.expect(mMockDevice.getProductVariant()).andReturn("maguro");
        EasyMock.replay(mMockDevice);

        IDeviceFlasher flasher = mPreparer.createFlasher(mMockDevice);
        assertTrue(flasher instanceof PrimeGsmDeviceFlasher);
        EasyMock.verify(mMockDevice);
    }

    /** Verify that we get the PrimeGsm flasher when expected. */
    @Test
    public void testPrimeGsm_16Gb() throws Exception {
        EasyMock.expect(mMockDevice.getProductType()).andReturn("tuna");
        EasyMock.expect(mMockDevice.getProductVariant()).andReturn("maguro 16GB");
        EasyMock.replay(mMockDevice);

        IDeviceFlasher flasher = mPreparer.createFlasher(mMockDevice);
        assertTrue(flasher instanceof PrimeGsmDeviceFlasher);
        EasyMock.verify(mMockDevice);
    }

    /** Verify that we get the PrimeGsm flasher when expected. */
    @Test
    public void testPrimeGsm_32Gb() throws Exception {
        EasyMock.expect(mMockDevice.getProductType()).andReturn("tuna");
        EasyMock.expect(mMockDevice.getProductVariant()).andReturn("maguro 32GB");
        EasyMock.replay(mMockDevice);

        IDeviceFlasher flasher = mPreparer.createFlasher(mMockDevice);
        assertTrue(flasher instanceof PrimeGsmDeviceFlasher);
        EasyMock.verify(mMockDevice);
    }

    /** Verify that we get the PrimeGsm flasher when expected. */
    @Test
    public void testPrimeCdma() throws Exception {
        EasyMock.expect(mMockDevice.getProductType()).andReturn("tuna");
        EasyMock.expect(mMockDevice.getProductVariant()).andReturn("toro");
        EasyMock.replay(mMockDevice);

        IDeviceFlasher flasher = mPreparer.createFlasher(mMockDevice);
        assertTrue(flasher instanceof PrimeCdmaDeviceFlasher);
        EasyMock.verify(mMockDevice);
    }

    /** Verify that we get the PrimeGsm flasher when expected. */
    @Test
    public void testPrimeCdma_16Gb() throws Exception {
        EasyMock.expect(mMockDevice.getProductType()).andReturn("tuna");
        EasyMock.expect(mMockDevice.getProductVariant()).andReturn("toro 16GB");
        EasyMock.replay(mMockDevice);

        IDeviceFlasher flasher = mPreparer.createFlasher(mMockDevice);
        assertTrue(flasher instanceof PrimeCdmaDeviceFlasher);
        EasyMock.verify(mMockDevice);
    }

    /** Verify that we get the PrimeGsm flasher when expected. */
    @Test
    public void testPrimeCdma_32Gb() throws Exception {
        EasyMock.expect(mMockDevice.getProductType()).andReturn("tuna");
        EasyMock.expect(mMockDevice.getProductVariant()).andReturn("toro 32GB");
        EasyMock.replay(mMockDevice);

        IDeviceFlasher flasher = mPreparer.createFlasher(mMockDevice);
        assertTrue(flasher instanceof PrimeCdmaDeviceFlasher);
        EasyMock.verify(mMockDevice);
    }

    /** Verify that we get the Sprout flasher when expected. */
    @Test
    public void testSprout() throws Exception {
        EasyMock.expect(mMockDevice.getProductType()).andReturn("sprout");
        EasyMock.expect(mMockDevice.getProductVariant()).andReturn(null);
        EasyMock.replay(mMockDevice);

        IDeviceFlasher flasher = mPreparer.createFlasher(mMockDevice);
        assertTrue(flasher instanceof SproutDeviceFlasher);
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test for {@link GoogleDeviceFlashPreparer#checkDeviceProductType(ITestDevice,
     * IDeviceBuildInfo)} where device board is matching the build-flavor.
     */
    @Test
    public void testSetup_checkDeviceBuild() throws Exception {
        mMockBuildInfo = new DeviceBuildInfo();
        mMockBuildInfo.setBuildFlavor("hammerhead-userdebug");
        EasyMock.expect(mMockDevice.getProductType()).andReturn("hammerhead");
        EasyMock.expect(mMockDevice.getProductVariant()).andReturn("hammerhead");
        EasyMock.replay(mMockDevice);
        mPreparer.checkDeviceProductType(mMockDevice, mMockBuildInfo);
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test that when the build name and product board/variant are different we are still able to
     * find a match via our look up map.
     */
    @Test
    public void testSetup_checkDeviceBuild_diffVariant() throws Exception {
        mMockBuildInfo = new DeviceBuildInfo();
        mMockBuildInfo.setBuildFlavor("ryu-userdebug");
        EasyMock.expect(mMockDevice.getProductType()).andReturn("dragon");
        EasyMock.expect(mMockDevice.getProductVariant()).andReturn(null);
        EasyMock.replay(mMockDevice);
        mPreparer.checkDeviceProductType(mMockDevice, mMockBuildInfo);
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test for {@link GoogleDeviceFlashPreparer#checkDeviceProductType(ITestDevice,
     * IDeviceBuildInfo)} where device board is not matching the build-flavor, then we throw a build
     * error.
     */
    @Test
    public void testSetup_checkDeviceBuild_fail() throws Exception {
        mMockBuildInfo = new DeviceBuildInfo();
        mMockBuildInfo.setBuildFlavor("hammerhead-userdebug");
        EasyMock.expect(mMockDevice.getProductType()).andReturn("bullhead");
        EasyMock.expect(mMockDevice.getProductVariant()).andReturn("bullhead");
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andReturn(null);
        EasyMock.replay(mMockDevice);
        try {
            mPreparer.checkDeviceProductType(mMockDevice, mMockBuildInfo);
            fail("Should have thrown an exception.");
        } catch (BuildError expected) {
            // expected
            CLog.e(expected);
        }
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test for {@link GoogleDeviceFlashPreparer#checkDeviceProductType(ITestDevice,
     * IDeviceBuildInfo)} when the build product is partially match against the build target.
     */
    @Test
    public void testSetup_checkDeviceBuild_partialmatch() throws Exception {
        mMockBuildInfo = new DeviceBuildInfo();
        mMockBuildInfo.setBuildFlavor("hammerhead_target36-userdebug");
        EasyMock.expect(mMockDevice.getProductType()).andReturn("hammerhead");
        EasyMock.expect(mMockDevice.getProductVariant()).andReturn("hammerhead");
        EasyMock.replay(mMockDevice);
        mPreparer.checkDeviceProductType(mMockDevice, mMockBuildInfo);
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test for {@link GoogleDeviceFlashPreparer#checkDeviceProductType(ITestDevice,
     * IDeviceBuildInfo)} when the build flavor does not match directly but is still compatible to
     * the device product.
     */
    @Test
    public void testSetup_checkDeviceBuild_similarFlavor() throws Exception {
        mMockBuildInfo = new DeviceBuildInfo();
        mMockBuildInfo.setBuildFlavor("sturgeon_sw-userdebug");
        EasyMock.expect(mMockDevice.getProductType()).andReturn("sturgeon");
        EasyMock.expect(mMockDevice.getProductVariant()).andReturn("sturgeon");
        EasyMock.replay(mMockDevice);
        mPreparer.checkDeviceProductType(mMockDevice, mMockBuildInfo);
        EasyMock.verify(mMockDevice);
    }
}

