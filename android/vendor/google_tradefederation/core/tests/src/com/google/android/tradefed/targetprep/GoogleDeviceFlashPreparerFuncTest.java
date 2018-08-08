// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.ddmlib.Log;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.IDeviceFlasher.UserDataFlashOption;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.DeviceTestCase;

import java.io.File;

/**
 * Functional tests for {@link GoogleDeviceFlashPreparer}.
 * <p/>
 * Assumes device under test is a passion device. Uses test files from Google NFS
 */
public class GoogleDeviceFlashPreparerFuncTest extends DeviceTestCase {

    private static final String LOG_TAG = "GoogleDeviceFlashPreparer";
    static final String TEST_DATA_ROOT = "/google/data/ro/teams/tradefed-test/";
    static final String IMG_FILE_1 = "aosp_marlin-img-2886678.zip";
    private static final String IMG_FILE_2 = "passion-img-38176.zip";
    private static final String USERDATA_FILE_1 = "passion-tests-userdata-24247.img";
    static final String TESTS_DIR_2 = "passion-tests-38176";
    private static final String BUILD_1 = "24247";
    private static final String BUILD_2 = "38176";

    public void testSetup() throws TargetSetupError, DeviceNotAvailableException, BuildError {
        Log.i(LOG_TAG, "testFlash");
        // flash from 1 build to another
        IDeviceBuildInfo info = new DeviceBuildInfo(BUILD_1, "foo") {
            @Override
            public void cleanUp() {
                // override to not delete permanent test files
            }
        };
        File dataRootFile = new File(TEST_DATA_ROOT);
        assertTrue(dataRootFile.exists());
        assertTrue(dataRootFile.isDirectory());
        GoogleDeviceFlashPreparer flasher = new GoogleDeviceFlashPreparer();
        flasher.setUserDataFlashOption(UserDataFlashOption.FLASH);
        info.setDeviceImageFile(new File(dataRootFile, IMG_FILE_1), BUILD_1);
        info.setUserDataImageFile(new File(dataRootFile, USERDATA_FILE_1), BUILD_1);
        flasher.setUp(getDevice(), info);
        String fingerprint = getDevice().getProperty("ro.build.fingerprint");
        assertTrue(fingerprint.contains(BUILD_1));

        // now flash build 2 - using a tests.zip
        IDeviceBuildInfo info2 = new DeviceBuildInfo(BUILD_2, "foo") {
            @Override
            public void cleanUp() {
                // override to not delete permanent test files
            }
        };
        flasher.setUserDataFlashOption(UserDataFlashOption.TESTS_ZIP);
        info2.setDeviceImageFile(new File(dataRootFile, IMG_FILE_2), BUILD_2);
        info2.setTestsDir(new File(dataRootFile, TESTS_DIR_2), BUILD_2);
        flasher.setUp(getDevice(), info2);
        fingerprint = getDevice().getProperty("ro.build.fingerprint");
        assertTrue(fingerprint.contains(BUILD_2));
    }
}
