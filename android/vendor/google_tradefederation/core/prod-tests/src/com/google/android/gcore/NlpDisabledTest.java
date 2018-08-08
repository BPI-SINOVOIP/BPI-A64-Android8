// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.gcore;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.google.android.gcore.NlpTester.FailureListener;

import java.io.File;

/**
 * Test repro case for disabled network location provider b/8901651
 */
public class NlpDisabledTest extends DeviceTestCase {

    @Option(name = "apk-path", description = "path to filesystem location that contains apk",
            mandatory = true)
    private String mApkPath;

    public void testNlpDisabled() throws DeviceNotAvailableException {
        File gmsApk = new File(mApkPath, "signed-GmsCore.apk");
        File phoneskyApk = new File(mApkPath, "signed-Phonesky.apk");
        File nlpTesterApk = new File(mApkPath, "NlpTester.apk");
        assertFileExists(gmsApk);
        assertFileExists(phoneskyApk);
        assertFileExists(nlpTesterApk);

        assertNull(getDevice().installPackage(nlpTesterApk, true));

        // first check that network location is present
        FailureListener f = NlpTester.runLocationTester(getDevice());
        assertEquals(2, f.getNumTotalTests());
        assertNull(f.mFailureTrace, f.mFailureTrace);

        // installing kamikaze phonesky before gmscore should reproduce the problem
        // on certain devices
        assertNull(getDevice().installPackage(phoneskyApk, true));
        assertNull(getDevice().installPackage(gmsApk, true));

        getDevice().reboot();

        // now check for network location provider again. This will fail on some devices
        f = NlpTester.runLocationTester(getDevice());
        assertEquals(2, f.getNumTotalTests());
        assertNull(f.mFailureTrace, f.mFailureTrace);
    }

    private void assertFileExists(File foo) {
        assertTrue(foo.getAbsolutePath() + " does not exist", foo.exists());
    }
}
