// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.targetprep.FastbootDeviceFlasher;
import com.android.tradefed.targetprep.IFlashingResourcesRetriever;
import com.android.tradefed.targetprep.TargetSetupError;

import junit.framework.TestCase;

import java.io.File;

/**
 * Functional tests for {@link NfsFlashingResourcesRetriever}.
 * <p/>
 * Attempts to retrieve known good files.
 */
public class NfsFlashingResourcesRetrieverFuncTest extends TestCase {

    private static final String DEVICE_TYPE = "foo";

    private IFlashingResourcesRetriever mRetriever;
    /** retrieved file reference to use for tests */
    private File mFile = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRetriever = new NfsFlashingResourcesRetriever(DEVICE_TYPE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mFile != null) {
            mFile.delete();
            mFile = null;
        }
    }

    /**
     * Test {@link NfsFlashingResourcesRetriever#retrieveFile(String, String)}
     * retrieves a valid hboot.
     */
    public void testGetBootloaderFile() throws TargetSetupError {
        mFile = mRetriever.retrieveFile("hboot", "0.35.2012");
        assertNotNull(mFile);
        assertEquals(524288L, mFile.length());
    }

    /**
     * Test {@link NfsFlashingResourcesRetriever#retrieveFile(String, String)}
     * when file cannot be found.
     */
    public void testGetHbootFile_missing() {
        try {
            mFile = mRetriever.retrieveFile("foo", "0.35.2012");
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Test {@link NfsFlashingResourcesRetriever#retrieveFile(String, String)} retrieves a valid
     * baseband, when radio file name does not include deviceName.
     */
    public void testGetBasebandFile() throws TargetSetupError {
        mFile = mRetriever.retrieveFile(FastbootDeviceFlasher.BASEBAND_IMAGE_NAME, "4.06.00.12_7");
        assertNotNull(mFile);
        assertEquals(26738688L, mFile.length());
    }

    /**
     * Test {@link NfsFlashingResourcesRetriever#retrieveFile(String, String)} retrieves a valid
     * baseband, when radio file name includes the device name.
     */
    public void testGetBasebandFile_device() throws TargetSetupError {
        IFlashingResourcesRetriever retriever = new NfsFlashingResourcesRetriever("sapphire");
        mFile = retriever.retrieveFile(FastbootDeviceFlasher.BASEBAND_IMAGE_NAME, "2.22.25.24");
        assertNotNull(mFile);
        assertEquals(22020096L, mFile.length());
    }

    /**
     * Test {@link NfsFlashingResourcesRetriever#retrieveFile(String, String)} when file cannot be
     * found.
     */
    public void testGetBasebandFile_missing() {
        try {
            // use "passion" retriever
            mFile = mRetriever.retrieveFile(
                    FastbootDeviceFlasher.BASEBAND_IMAGE_NAME, "2.22.25.24");
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Test {@link NfsFlashingResourcesRetriever#retrieveFile(String, String)} retrieves a valid
     * microp file, when file name does not include deviceName.
     */
    public void testGetMicropFile() throws TargetSetupError {
        mFile = mRetriever.retrieveFile("microp", "0b15");
        assertNotNull(mFile);
        assertEquals(7162L, mFile.length());
    }

    /**
     * Test {@link NfsFlashingResourcesRetriever#retrieveFile(String, String)} retrieves a valid
     * microp file,  when file name includes deviceName.
     */
    public void testGetMicropFile_deviceName() throws TargetSetupError {
        IFlashingResourcesRetriever retriever = new NfsFlashingResourcesRetriever("desirec");
        mFile = retriever.retrieveFile("microp", "0110");
        assertNotNull(mFile);
        assertEquals(6258L, mFile.length());
    }
}
