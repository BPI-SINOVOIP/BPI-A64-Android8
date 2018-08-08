// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.gcore;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.google.android.gcore.NlpTester.FailureListener;

import java.io.File;

/**
 * Test that applying nlp fixer app correctly fixes broken network location
 *
 * TODO: change this to a play store install + launch instead of directly installing nlp fixer
 */
public class NlpFixerTest extends DeviceTestCase {

    @Option(name = "apk-path", description = "path to filesystem location that contains apk",
            mandatory = true)
    private String mApkPath;

    public void testNlpFixer() throws DeviceNotAvailableException {
        // assume device is already in desired state, and nlp tester is already installed
        File nlpFixerApk = new File(mApkPath, "signed-NlpFixer.apk");
        assertFileExists(nlpFixerApk);
        File nlpTesterApk = new File(mApkPath, "NlpTester.apk");
        assertFileExists(nlpTesterApk);

        assertNull(getDevice().installPackage(nlpTesterApk, true));

        // first check that network location is present
        FailureListener f = NlpTester.runLocationTester(getDevice());
        assertEquals(2, f.getNumTotalTests());
        if (f.mFailureTrace != null) {
            // this might be expected, just log it for now
            CLog.i("Device %s has disabled network provider", getDevice().getSerialNumber());
        }

        assertNull(getDevice().installPackage(nlpFixerApk, true));

        getDevice().reboot();

        // now check for network location provider again. This should pass on all devices
        f = NlpTester.runLocationTester(getDevice());
        assertEquals(2, f.getNumTotalTests());
        assertNull(f.mFailureTrace, f.mFailureTrace);
    }

    private void assertFileExists(File foo) {
        assertTrue(foo.getAbsolutePath() + " does not exist", foo.exists());
    }
}
