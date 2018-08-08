package com.google.android.tradefed.util;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.targetprep.AltDirBehavior;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.BuildTestsZipUtils;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A functional test for {@link BuildTestsZipUtils}.
 * <p/>
 * This test is in google-tradefed-tests as opposed to tradefed-tests because it depends on
 * a fixed file in Google NFS.
 */
public class BuildTestsZipUtilsFuncTests extends TestCase {

    private static final String TEST_DATA_ROOT = "/auto/android-test/data/tradefed-tests";
    private static final String ALT_DIR_ROOT = "/auto/android-test/data/tradefed-tests/alt-dir-apk";
    private static final String TESTS_DIR_2 = "passion-tests-38176";
    private static final String APK_NAME0 = "SetGoogleAccount.apk";
    private static final String APK_NAME1 = "SetGoogleAccount1.apk";
    private static final String APK_NAME2 = "SetGoogleAccount2.apk";
    private static final String APK_NAME3 = "SetGoogleAccount3.apk";
    private static final String APK_NAME4 = "PermissionUtils.apk";
    private static final String APK_NAME4_ACTUAL = "PermissionUtils-test-keys.apk";
    private List<File> mAltDirs = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAltDirs.add(new File(ALT_DIR_ROOT));
    }

    /**
     * Test that apk path is resolved to the location under expanded test zip
     * @throws IOException
     */
    public void testSetup_testDir() throws IOException {
        String expected = "/auto/android-test/data/tradefed-tests/passion-tests-38176"
                + "/DATA/app/SetGoogleAccount.apk";
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub");
        stubBuild.setTestsDir(FileUtil.getFileForPath(new File(TEST_DATA_ROOT), TESTS_DIR_2), "0");
        assertEquals(expected, BuildTestsZipUtils.getApkFile(
                stubBuild, APK_NAME0, null, null, false, null).getAbsolutePath());
    }

    /**
     * Test that apk path is resolved to the location under expanded test zip and provided
     * fallback is not used
     * @throws IOException
     */
    public void testSetup_altDir_fallback_notused() throws IOException {
        String expected = "/auto/android-test/data/tradefed-tests/passion-tests-38176"
                + "/DATA/app/SetGoogleAccount.apk";
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub");
        stubBuild.setTestsDir(FileUtil.getFileForPath(new File(TEST_DATA_ROOT), TESTS_DIR_2), "0");
        assertEquals(expected, BuildTestsZipUtils.getApkFile(stubBuild, APK_NAME0,
                mAltDirs, AltDirBehavior.FALLBACK, false, null).toString());
    }

    /**
     * Test that apk path is resolved to the location under expanded test zip and provided
     * fallback is used. The fallback is expected to live under root of alt dir
     * @throws IOException
     */
    public void testSetup_altDir_fallback_used1() throws IOException {
        String expected =
                "/auto/android-test/data/tradefed-tests/alt-dir-apk/SetGoogleAccount1.apk";
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub");
        stubBuild.setTestsDir(FileUtil.getFileForPath(new File(TEST_DATA_ROOT), TESTS_DIR_2), "0");
        assertEquals(expected, BuildTestsZipUtils.getApkFile(stubBuild, APK_NAME1,
                mAltDirs, AltDirBehavior.FALLBACK, false, null).getAbsolutePath());
    }

    /**
     * Test that apk path is resolved to the location under expanded test zip and provided
     * fallback is used. The fallback is expected to live under DATA/app relative to root of alt dir
     * @throws IOException
     */
    public void testSetup_altDir_fallback_used2() throws IOException {
        String expected =
                "/auto/android-test/data/tradefed-tests/alt-dir-apk/DATA/app/SetGoogleAccount2.apk";
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub");
        stubBuild.setTestsDir(FileUtil.getFileForPath(new File(TEST_DATA_ROOT), TESTS_DIR_2), "0");
        assertEquals(expected, BuildTestsZipUtils.getApkFile(stubBuild, APK_NAME2,
                mAltDirs, AltDirBehavior.FALLBACK, false, null).getAbsolutePath());
    }

    /**
     * Test that apk path is resolved to the location under expanded test zip and provided
     * override is used. The override is expected to live under DATA/app/SetGoogleAccount relative
     * to root of alt dir
     * @throws IOException
     */
    public void testSetup_altDir_override() throws IOException {
        String expected = "/auto/android-test/data/tradefed-tests/alt-dir-apk"
                + "/DATA/app/SetGoogleAccount/SetGoogleAccount.apk";
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub");
        stubBuild.setTestsDir(FileUtil.getFileForPath(new File(TEST_DATA_ROOT), TESTS_DIR_2), "0");
        assertEquals(expected, BuildTestsZipUtils.getApkFile(stubBuild, APK_NAME0,
                mAltDirs, AltDirBehavior.OVERRIDE, false, null).getAbsolutePath());
    }

    /**
     * Test that apk path is not resolved since it's nowhere to be found
     * @throws IOException
     */
    public void testSetup_notfound() throws IOException {
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub");
        stubBuild.setTestsDir(FileUtil.getFileForPath(new File(TEST_DATA_ROOT), TESTS_DIR_2), "0");
        assertNull("not expected to find apk with name " + APK_NAME3,
                BuildTestsZipUtils.getApkFile(stubBuild, APK_NAME3,
                        mAltDirs, AltDirBehavior.FALLBACK, false, null));
    }

    /**
     * Test that apk is resolved from local resource of test harness
     */
    public void testSetup_resource() throws IOException {
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub");
        // set a bogus non-existing folder
        stubBuild.setTestsDir(FileUtil.getFileForPath(new File("/tmp"), "bogus"), "0");
        File apk = BuildTestsZipUtils.getApkFile(stubBuild, APK_NAME4,
                mAltDirs, AltDirBehavior.FALLBACK, true, null);
        // we can't assert on the path since it'll be a randomly created local file, so we do md5
        String fileMd5 = FileUtil.calculateMd5(apk);
        String streamMd5 = StreamUtil.calculateMd5(getClass().getResourceAsStream(
                String.format("/apks/%s", APK_NAME4)));
        assertEquals("resolved apk checksum does not match", streamMd5, fileMd5);
    }

    /**
     * Test that apk is resolved from local resource of test harness and with desginated signing
     * key variant
     */
    public void testSetup_resource_keys() throws IOException {
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub");
        // set a bogus non-existing folder
        stubBuild.setTestsDir(FileUtil.getFileForPath(new File("/tmp"), "bogus"), "0");
        File apk = BuildTestsZipUtils.getApkFile(stubBuild, APK_NAME4,
                mAltDirs, AltDirBehavior.FALLBACK, true, "test-keys");
        // we can't assert on the path since it'll be a randomly created local file, so we do md5
        String fileMd5 = FileUtil.calculateMd5(apk);
        String streamMd5 = StreamUtil.calculateMd5(getClass().getResourceAsStream(
                String.format("/apks/%s", APK_NAME4_ACTUAL)));
        assertEquals("resolved apk checksum does not match", streamMd5, fileMd5);
    }
}
