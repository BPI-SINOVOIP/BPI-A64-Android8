/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 */

package com.google.android.widevine.targetprep;

import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.LinkedList;

/**
 * Unit tests for {@link WVLibraryFlasher}
 */

public class WVLibraryFlasherTest extends TestCase {

    private static final String LOG_TAG = "WVLibraryFlasherTest";

    private WVLibraryFlasher mFlasher = null;
    private ITestDevice mMockDevice = null;
    private IBuildInfo mMockBuildInfo = null;
    private String mDeviceLibLocation = "/system/vendor/lib/";

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDevice = EasyMock.createStrictMock(ITestDevice.class);
        mMockBuildInfo = EasyMock.createStrictMock(IBuildInfo.class);
        mFlasher = new WVLibraryFlasher();
    }

    /**
     * Helper method to create a temp file for WVLibraryFlasher.getVersion to read.
     */
    protected File createTempFile(String filename, String contents) throws Exception {
        File tempdir = new File(System.getProperty("java.io.tmpdir"));
        File tempfile = new File(tempdir, filename);
        if (!tempfile.exists()) {
            tempfile.createNewFile();
        }
        tempfile.setWritable(true);
        tempfile.deleteOnExit();
        Log.d(LOG_TAG, "Created temp file at: " + tempfile.getCanonicalPath());
        if (contents != null) {
            FileWriter fw = new FileWriter(tempfile.getCanonicalPath());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(contents);
            bw.newLine();
            bw.flush();
            bw.close();
        }
        return tempfile;
    }

    public void testSetUp_localFileNoExist() throws Exception {
        LinkedList<String> updatefilelist = new LinkedList<String>();
        String wvstreamcontrol = System.getProperty("java.io.tmpdir") +
                "/libWVStreamControlAPI_L1.so->" + mDeviceLibLocation +
                "libWVStreamControlAPI_L1.so";
        updatefilelist.add(wvstreamcontrol);
        mFlasher.setUpdateList(updatefilelist);
        EasyMock.expect(
                mMockDevice.enableAdbRoot()).andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.executeAdbCommand(EasyMock.eq("remount"))).andReturn("");
        mMockDevice.waitForDeviceAvailable();
        EasyMock.expect(
                mMockDevice.doesFileExist(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.doesFileExist(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(Boolean.TRUE);
        EasyMock.replay(mMockDevice);
        try {
            // Should throw TargetSetupError and _not_ run any post-push command
            mFlasher.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
            assertEquals(String.format("Files do not meet requirements for update in" +
                    " update-file string: '%s'", wvstreamcontrol), e.getMessage());
        }
    }

    public void testSetUp_deviceFileNoExist() throws Exception {
        LinkedList<String> updatefilelist = new LinkedList<String>();
        String wvstreamcontrol = System.getProperty("java.io.tmpdir") +
                "/libWVStreamControlAPI_L1.so->" + mDeviceLibLocation +
                "libWVStreamControlAPI_L1.so";
        updatefilelist.add(wvstreamcontrol);
        mFlasher.setUpdateList(updatefilelist);
        EasyMock.expect(
                mMockDevice.enableAdbRoot()).andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.executeAdbCommand(EasyMock.eq("remount"))).andReturn("");
        mMockDevice.waitForDeviceAvailable();
        EasyMock.expect(
                mMockDevice.doesFileExist(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(Boolean.FALSE);
        EasyMock.expect(
                mMockDevice.doesFileExist(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(Boolean.FALSE);
        EasyMock.replay(mMockDevice);
        try {
            // Should throw TargetSetupError and _not_ run any post-push command
            mFlasher.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
            assertEquals(String.format("Files do not meet requirements for update in" +
                    " update-file string: '%s'", wvstreamcontrol), e.getMessage());
        }
    }

    public void testSetUp_deviceFileNoExistEnforceUpdateFalse() throws Exception {
      LinkedList<String> updatefilelist = new LinkedList<String>();
      String wvstreamcontrol = System.getProperty("java.io.tmpdir") +
              "/libWVStreamControlAPI_L1.so->" + mDeviceLibLocation +
              "libWVStreamControlAPI_L1.so";
      updatefilelist.add(wvstreamcontrol);
      mFlasher.setUpdateList(updatefilelist);
      mFlasher.setEnforceUpdateOnly(Boolean.FALSE);
      File streamcontrolfile = createTempFile("libWVStreamControlAPI_L1.so", "4.5.0.12345 ");
      EasyMock.expect(
              mMockDevice.enableAdbRoot()).andReturn(Boolean.TRUE);
      EasyMock.expect(
              mMockDevice.executeAdbCommand(EasyMock.eq("remount"))).andReturn("");
      mMockDevice.waitForDeviceAvailable();
      EasyMock.expect(
              mMockDevice.doesFileExist(
              EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
              .andReturn(Boolean.FALSE);
      EasyMock.expect(
              mMockDevice.pullFile(
              EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
              .andReturn(null);
      EasyMock.expect(
              mMockDevice.pushFile((File)EasyMock.anyObject(),
              EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
              .andReturn(Boolean.TRUE);
      EasyMock.expect(
          mMockDevice.pullFile(
          EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
          .andReturn(streamcontrolfile);
      mMockDevice.reboot();
      EasyMock.replay(mMockDevice);
      try {
        mFlasher.setUp(mMockDevice, mMockBuildInfo);
    } finally {
        streamcontrolfile.delete();
    }
  }

    public void testSetUp_secondLocalFileNoExist() throws Exception {
        LinkedList<String> updatefilelist = new LinkedList<String>();
        String wvstreamcontrol = System.getProperty("java.io.tmpdir") +
                "/libWVStreamControlAPI_L1.so->" + mDeviceLibLocation +
                "libWVStreamControlAPI_L1.so";
        String libwvdrm = System.getProperty("java.io.tmpdir") + "/libwvdrm_L1.so->" +
                mDeviceLibLocation + "libwvdrm_L1.so";
        updatefilelist.add(wvstreamcontrol);
        updatefilelist.add(libwvdrm);
        mFlasher.setUpdateList(updatefilelist);
        File streamcontrolfile = createTempFile("libWVStreamControlAPI_L1.so", "4.5.0.12345 ");
        File devicestreamcontrolfile = createTempFile("devlibWVStreamControlAPI_L1.so",
                "4.5.0.12344 ");
        EasyMock.expect(
                mMockDevice.enableAdbRoot()).andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.executeAdbCommand(EasyMock.eq("remount"))).andReturn("");
        mMockDevice.waitForDeviceAvailable();
        EasyMock.expect(
                mMockDevice.doesFileExist(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.pullFile(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(devicestreamcontrolfile);
        EasyMock.expect(
                mMockDevice.pushFile((File)EasyMock.anyObject(),
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.pullFile(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(streamcontrolfile);
        EasyMock.expect(
                mMockDevice.doesFileExist(EasyMock.eq(mDeviceLibLocation + "libwvdrm_L1.so")))
                .andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.doesFileExist(EasyMock.eq(mDeviceLibLocation + "libwvdrm_L1.so")))
                .andReturn(Boolean.TRUE);
        EasyMock.replay(mMockDevice);
        try {
            // Should throw TargetSetupError and _not_ run any post-push command
            mFlasher.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
            assertEquals(String.format("Files do not meet requirements for update in" +
                    " update-file string: '%s'", libwvdrm), e.getMessage());
        } finally {
            streamcontrolfile.delete();
            devicestreamcontrolfile.delete();
        }
    }

    public void testSetUp_pushFail() throws Exception {
        LinkedList<String> updatefilelist = new LinkedList<String>();
        String wvstreamcontrol = System.getProperty("java.io.tmpdir") +
                "/libWVStreamControlAPI_L1.so->" + mDeviceLibLocation + "libWVStreamControlAPI_L1.so";
        updatefilelist.add(wvstreamcontrol);
        mFlasher.setUpdateList(updatefilelist);
        File streamcontrolfile = createTempFile("libWVStreamControlAPI_L1.so", "4.5.0.12345 ");
        File devicestreamcontrolfile = createTempFile("devlibWVStreamControlAPI_L1.so",
                "4.5.0.12344 ");
        EasyMock.expect(
                mMockDevice.enableAdbRoot()).andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.executeAdbCommand(EasyMock.eq("remount"))).andReturn("");
        mMockDevice.waitForDeviceAvailable();
        EasyMock.expect(
                mMockDevice.doesFileExist(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.pullFile(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(devicestreamcontrolfile);
        EasyMock.expect(
                mMockDevice.pushFile((File)EasyMock.anyObject(),
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(Boolean.FALSE);
        EasyMock.replay(mMockDevice);
        try {
            // Should throw TargetSetupError and _not_ run any post-push command
            mFlasher.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
            assertEquals(String.format("Failed to push local %s to remote %s",
                    streamcontrolfile.getAbsolutePath(), mDeviceLibLocation +
                    "libWVStreamControlAPI_L1.so"), e.getMessage());
        } finally {
            streamcontrolfile.delete();
            devicestreamcontrolfile.delete();
        }
    }

    public void testSetUp_hashMismatchAfterUpdate() throws Exception {
        LinkedList<String> updatefilelist = new LinkedList<String>();
        String wvstreamcontrol = System.getProperty("java.io.tmpdir") +
                "/libWVStreamControlAPI_L1.so->" + mDeviceLibLocation + "libWVStreamControlAPI_L1.so";
        updatefilelist.add(wvstreamcontrol);
        mFlasher.setUpdateList(updatefilelist);
        File streamcontrolfile = createTempFile("libWVStreamControlAPI_L1.so", "4.5.0.12345 ");
        File devicestreamcontrolfile = createTempFile("devlibWVStreamControlAPI_L1.so",
                "4.5.0.12344 ");
        EasyMock.expect(
                mMockDevice.enableAdbRoot()).andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.executeAdbCommand(EasyMock.eq("remount"))).andReturn("");
        mMockDevice.waitForDeviceAvailable();
        EasyMock.expect(
                mMockDevice.doesFileExist(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.pullFile(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(devicestreamcontrolfile);
        EasyMock.expect(
                mMockDevice.pushFile((File)EasyMock.anyObject(),
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.pullFile(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(devicestreamcontrolfile);
        EasyMock.replay(mMockDevice);
        try {
            // Should throw TargetSetupError and _not_ run any post-push command
            mFlasher.setUp(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
            assertEquals("Hashes do not match after update.", e.getMessage());
        } finally {
            streamcontrolfile.delete();
            devicestreamcontrolfile.delete();
        }
    }

    public void testSetup_hashMatchBeforeUpdate() throws Exception {
        LinkedList<String> updatefilelist = new LinkedList<String>();
        String wvstreamcontrol = System.getProperty("java.io.tmpdir") +
                "/libWVStreamControlAPI_L1.so->" + mDeviceLibLocation +
                "libWVStreamControlAPI_L1.so";
        updatefilelist.add(wvstreamcontrol);
        mFlasher.setUpdateList(updatefilelist);
        File streamcontrolfile = createTempFile("libWVStreamControlAPI_L1.so", "");
        EasyMock.expect(
                mMockDevice.enableAdbRoot()).andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.executeAdbCommand(EasyMock.eq("remount"))).andReturn("");
        mMockDevice.waitForDeviceAvailable();
        EasyMock.expect(
                mMockDevice.doesFileExist(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.pullFile(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(streamcontrolfile);
        mMockDevice.reboot();
        EasyMock.replay(mMockDevice);
        try {
          mFlasher.setUp(mMockDevice, mMockBuildInfo);
      } finally {
          streamcontrolfile.delete();
      }
    }

    public void testSetUp_Success() throws Exception {
        LinkedList<String> updatefilelist = new LinkedList<String>();
        String wvstreamcontrol = System.getProperty("java.io.tmpdir") +
                "/libWVStreamControlAPI_L1.so->" + mDeviceLibLocation +
                "libWVStreamControlAPI_L1.so";
        String libwvdrm = System.getProperty("java.io.tmpdir") + "/libwvdrm_L1.so->" +
                mDeviceLibLocation + "libwvdrm_L1.so";
        updatefilelist.add(wvstreamcontrol);
        updatefilelist.add(libwvdrm);
        mFlasher.setUpdateList(updatefilelist);
        File streamcontrolfile = createTempFile("libWVStreamControlAPI_L1.so", "4.5.0.12345 ");
        File libwvdrmfile = createTempFile("libwvdrm_L1.so", "");
        File devicestreamcontrolfile = createTempFile("devlibWVStreamControlAPI_L1.so",
                "4.5.0.12344 ");
        File devicelibwvdrmfile = createTempFile("devlibwvdrm_L1.so", "Needs MD5 diff");
        EasyMock.expect(
                mMockDevice.enableAdbRoot()).andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.executeAdbCommand(EasyMock.eq("remount"))).andReturn("");
        mMockDevice.waitForDeviceAvailable();
        EasyMock.expect(
                mMockDevice.doesFileExist(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(Boolean.TRUE);
        EasyMock.expect(
            mMockDevice.pullFile(
            EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
            .andReturn(devicestreamcontrolfile);
        EasyMock.expect(
            mMockDevice.pushFile((File)EasyMock.anyObject(),
            EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
            .andReturn(Boolean.TRUE);
        EasyMock.expect(
            mMockDevice.pullFile(
            EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
            .andReturn(streamcontrolfile);
        EasyMock.expect(
                mMockDevice.doesFileExist(EasyMock.eq(mDeviceLibLocation + "libwvdrm_L1.so")))
                .andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.pullFile(EasyMock.eq(mDeviceLibLocation + "libwvdrm_L1.so")))
                .andReturn(devicelibwvdrmfile);
        EasyMock.expect(
                mMockDevice.pushFile((File)EasyMock.anyObject(),
                EasyMock.eq(mDeviceLibLocation + "libwvdrm_L1.so"))).andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.pullFile(EasyMock.eq(mDeviceLibLocation + "libwvdrm_L1.so")))
                .andReturn(libwvdrmfile);
        mMockDevice.reboot();
        EasyMock.replay(mMockDevice);
        try {
            mFlasher.setUp(mMockDevice, mMockBuildInfo);
        } finally {
            streamcontrolfile.delete();
            libwvdrmfile.delete();
            devicestreamcontrolfile.delete();
        }
    }

    public void testAbortOnFailure_false() throws Exception {
        LinkedList<String> updatefilelist = new LinkedList<String>();
        String wvstreamcontrol = System.getProperty("java.io.tmpdir") +
                "/libWVStreamControlAPI_L1.so->" + mDeviceLibLocation +
                "libWVStreamControlAPI_L1.so";
        updatefilelist.add(wvstreamcontrol);
        mFlasher.setUpdateList(updatefilelist);
        File streamcontrolfile = createTempFile("libWVStreamControlAPI_L1.so", "");
        File devicestreamcontrolfile = createTempFile("devlibWVStreamControlAPI_L1.so",
                "4.5.0.12344 ");
        mFlasher.setAbortOnFailure(false);
        EasyMock.expect(
                mMockDevice.enableAdbRoot()).andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.executeAdbCommand(EasyMock.eq("remount"))).andReturn("");
        mMockDevice.waitForDeviceAvailable();
        EasyMock.expect(
                mMockDevice.doesFileExist(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(Boolean.TRUE);
        EasyMock.expect(
                mMockDevice.pullFile(
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(devicestreamcontrolfile);
        // Expect a pushFile() call and return false (failed)
        EasyMock.expect(
                mMockDevice.pushFile((File)EasyMock.anyObject(),
                EasyMock.eq(mDeviceLibLocation + "libWVStreamControlAPI_L1.so")))
                .andReturn(Boolean.FALSE);
        // Since only warning, post-push commands should run despite the push failure
        mMockDevice.reboot();
        EasyMock.replay(mMockDevice);
        try {
            mFlasher.setUp(mMockDevice, mMockBuildInfo);
        } finally {
            streamcontrolfile.delete();
            devicestreamcontrolfile.delete();
        }
    }
}

