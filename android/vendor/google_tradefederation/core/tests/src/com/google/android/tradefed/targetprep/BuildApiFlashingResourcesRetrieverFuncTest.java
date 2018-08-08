// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.targetprep.FastbootDeviceFlasher;
import com.android.tradefed.targetprep.IFlashingResourcesRetriever;
import com.android.tradefed.targetprep.TargetSetupError;

import junit.framework.TestCase;

import java.io.File;

import static com.google.android.tradefed.targetprep.GoogleDeviceFlashPreparer.KEY_FILE_PATH;
import static com.google.android.tradefed.targetprep.GoogleDeviceFlashPreparer.SERVICE_ACCOUNT;

/**
 * Functional tests for {@link BuildApiFlashingResourcesRetriever}.
 */
public class BuildApiFlashingResourcesRetrieverFuncTest extends TestCase {

    /** retrieved file reference to use for tests */
    private File mFile = null;

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mFile != null) {
            mFile.delete();
            mFile = null;
        }
    }

    // Test a valid device image file.
    public void testExistDeviceImage() throws Exception {
        IFlashingResourcesRetriever retriever =
                new BuildApiFlashingResourcesRetriever("dory", KEY_FILE_PATH, SERVICE_ACCOUNT);
        mFile = retriever.retrieveFile("bootloader", "DORYZ07f");
        assertNotNull(mFile);
        assertEquals(2685052L, mFile.length());
    }

    // Test a invalid device image file.
    public void testNonExistDeviceImage() throws Exception {
        IFlashingResourcesRetriever retriever =
                new BuildApiFlashingResourcesRetriever("foo", KEY_FILE_PATH, SERVICE_ACCOUNT);
        try {
            mFile = retriever.retrieveFile("bootloader", "DORYZ07f");
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    // Fetch old device image files, will fallback to nfs retriever.
    public void testOldDeviceImage() throws Exception {
        IFlashingResourcesRetriever retriever =
                new BuildApiFlashingResourcesRetriever("sapphire", KEY_FILE_PATH, SERVICE_ACCOUNT);
        mFile = retriever.retrieveFile(FastbootDeviceFlasher.BASEBAND_IMAGE_NAME, "2.22.25.24");
        assertNotNull(mFile);
        assertEquals(22020096L, mFile.length());
    }
}
