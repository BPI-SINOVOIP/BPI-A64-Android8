// Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.ota;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.OtaDeviceBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Test for {@link OtaFullDowngradePreparer}.
 */
@RunWith(JUnit4.class)
public class OtaFullDowngradePreparerTest {

    private ITestDevice mMockDevice;
    private OtaFullDowngradePreparer mPreparer;
    private OtaDeviceBuildInfo mOtaBuildInfo;
    private IDeviceBuildInfo mMockBuildInfo;
    private ZipEntry mZipEntry;
    private List<File> mFilesToDelete = new ArrayList<File>();
    private File mZipInternal;

    @Before
    public void setUp() throws Exception {
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mPreparer = new OtaFullDowngradePreparer();
        mOtaBuildInfo = new OtaDeviceBuildInfo();
        mPreparer.mOtaBuildInfo = mOtaBuildInfo;
        mMockBuildInfo = EasyMock.createMock(IDeviceBuildInfo.class);
        mOtaBuildInfo.setOtaBuild(mMockBuildInfo);
    }

    @After
    public void tearDown() throws Exception {
        for (File f : mFilesToDelete) {
            FileUtil.recursiveDelete(f);
        }
        mFilesToDelete.clear();
    }

    @Test
    public void testGetTimestampFromUpdaterScript_success() throws Exception {
        String line = "(!less_than_int(1492028835, getprop(\"ro.build.date.utc\"))) || "
                + "abort(\"E3003: Can't install this package (Wed Apr 12 20:27:15 UTC 2017) over "
                + "newer build (\" + getprop(\"ro.build.date\") + \").\");";
        String res = mPreparer.getTimestampFromUpdaterScript(mMockDevice, line);
        Assert.assertEquals("1492028835", res);
    }

    @Test(expected = TargetSetupError.class)
    public void testGetTimestampFromUpdaterScript_failure() throws Exception {
        String line = "!less_than_int(3, getprop(foo))";
        mPreparer.getTimestampFromUpdaterScript(mMockDevice, line);
    }

    @Test
    public void testGetTimestampFromBuildProp() throws Exception {
        String contents = "ro.build.version.base_os=\n" +
                "ro.build.date=Wed Apr 12 20:27:15 UTC 2017\n" +
                "ro.build.date.utc=1492028835\n" +
                "ro.build.type=userdebug\n" +
                "ro.build.user=android-build";
        File buildProps = setupTempFile(contents);
        String res = mPreparer.getTimestampFromBuildProp(mMockDevice, buildProps);
        Assert.assertEquals("1492028835", res);
    }

    @Test
    public void testModifyLegacyPackage() throws Exception {
        String line = "(!less_than_int(1492028835, getprop(\"ro.build.date.utc\"))) || "
                + "abort(\"E3003: Can't install this package (Wed Apr 12 20:27:15 UTC 2017) over "
                + "newer build (\" + getprop(\"ro.build.date\") + \").\");";
        ZipFile z = setupTempZipFile(line, "a", "b");
        mPreparer.mBaselineTimestamp = 12345;
        EasyMock.expect(mMockBuildInfo.getOtaPackageFile()).andReturn(mZipInternal).times(1);
        EasyMock.replay(mMockBuildInfo);
        mPreparer.modifyLegacyPackage(mMockDevice, z, mZipEntry);

        EasyMock.verify(mMockBuildInfo);
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                z.getInputStream(mZipEntry)));
        String modified = reader.readLine();
        Assert.assertEquals(line.replace("1492028835", "12346"), modified);
        Assert.assertEquals("a", reader.readLine());
        Assert.assertEquals("b", reader.readLine());
        reader.close();
        Assert.assertTrue(ZipUtil.isZipFileValid(mZipInternal, false));
        ZipUtil.closeZip(z);
    }

    @Test
    public void testModifyAbPackage() throws Exception {
        ZipFile z = setupTempZipFile(
                "ota-type=AB",
                "post-build=google/marlin/marlin:O/OPR1.170412.002/3905405:userdebug/dev-keys",
                "post-build-incremental=3905405",
                "post-timestamp=1492028027",
                "pre-device=marlin");
        mPreparer.mBaselineTimestamp = 23456;
        EasyMock.expect(mMockBuildInfo.getOtaPackageFile()).andReturn(mZipInternal).times(1);
        EasyMock.replay(mMockBuildInfo);
        mPreparer.modifyAbPackage(mMockDevice, z, mZipEntry);

        EasyMock.verify(mMockBuildInfo);

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                z.getInputStream(mZipEntry)));
        Assert.assertEquals("ota-type=AB", reader.readLine());
        Assert.assertEquals(
                "post-build=google/marlin/marlin:O/OPR1.170412.002/3905405:userdebug/dev-keys",
                reader.readLine());
        Assert.assertEquals("post-build-incremental=3905405", reader.readLine());
        Assert.assertEquals("post-timestamp=23457", reader.readLine());
        Assert.assertEquals("pre-device=marlin", reader.readLine());
        reader.close();
        Assert.assertTrue(ZipUtil.isZipFileValid(mZipInternal, false));
        ZipUtil.closeZip(z);
    }

    private File setupTempFile(String contents) throws Exception {
        File f = FileUtil.createTempFile("ota-full-downgrade-test", ".prop");
        mFilesToDelete.add(f);
        FileWriter w = new FileWriter(f);
        w.write(contents);
        w.close();
        return f;
    }

    private ZipFile setupTempZipFile(String... contents) throws Exception {
        File f = FileUtil.createTempFile("ota-full-downgrade-test", ".txt");
        FileWriter w = new FileWriter(f);
        for (String line : contents) {
            w.write(line + "\n");
        }
        w.close();
        mFilesToDelete.add(f);
        File zipFile = ZipUtil.createZip(f);
        mZipInternal = zipFile;
        //mFilesToDelete.add(zipFile);
        ZipFile zip = new ZipFile(zipFile);
        mZipEntry = zip.getEntry(f.getName());
        return zip;
    }
}
