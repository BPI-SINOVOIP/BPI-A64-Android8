// Copyright 2017 Google Inc.  All Rights Reserved.

package com.google.android.ota;

import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/**
 * Test for {@link IncrementalOtaLaunchControlProvider}.
 */
@RunWith(JUnit4.class)
public class IncrementalOtaLaunchControlProviderTest {

    private File mPrePackage;
    private File mPostPackage;
    private File mPreDir;
    private File mPostDir;
    private IncrementalOtaLaunchControlProvider mProvider;

    @Before
    public void setUp() throws Exception {
        mPreDir = FileUtil.createTempDir("incr-test-pre");
        File preChildDir = new File(mPreDir, "SYSTEM");
        Assert.assertTrue(preChildDir.mkdir());
        File preBuildProp = new File(preChildDir, "build.prop");
        FileUtil.writeToFile("ro.build.version.base_os=\nro.build.date.utc=12345678\n"
                + "ro.build.user=android-build\n",
                preBuildProp);
        mPrePackage = ZipUtil.createZip(new File(mPreDir, "SYSTEM"));

        mPostDir = FileUtil.createTempDir("incr-test-post");
        File postChildDir = new File(mPostDir, "SYSTEM");
        Assert.assertTrue(postChildDir.mkdir());
        File postBuildProp = new File(postChildDir, "build.prop");
        FileUtil.writeToFile("ro.build.version.base_os=\nro.build.date.utc=23456789\n"
                + "ro.build.user=android-build\n",
                postBuildProp);
        mPostPackage = ZipUtil.createZip(new File(mPostDir, "SYSTEM"));

        mProvider = new IncrementalOtaLaunchControlProvider();
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.deleteFile(mPrePackage);
        FileUtil.deleteFile(mPostPackage);
        FileUtil.recursiveDelete(mPreDir);
        FileUtil.recursiveDelete(mPostDir);
    }

    @Test
    public void testDetectDowngrade_noDowngrade() throws Exception {
        mProvider.mFromTargetFilesPath = mPrePackage;
        mProvider.mToTargetFilesPath = mPostPackage;
        mProvider.detectDowngrade();
        Assert.assertFalse(mProvider.mDowngrade);
    }

    @Test
    public void testDetectDowngrade_downgrade() throws Exception {
        mProvider.mFromTargetFilesPath = mPostPackage;
        mProvider.mToTargetFilesPath = mPrePackage;
        mProvider.detectDowngrade();
        Assert.assertTrue(mProvider.mDowngrade);
    }
}
