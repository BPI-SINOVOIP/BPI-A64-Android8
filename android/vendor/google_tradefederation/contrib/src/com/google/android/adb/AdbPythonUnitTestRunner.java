// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.adb;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.PythonUnitTestRunner;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;

/** Test runner for adb integration tests */
public class AdbPythonUnitTestRunner extends PythonUnitTestRunner {

    private IRunUtil mDefaultRunUtil = RunUtil.getDefault();
    private File mTempDir = null;
    private File mRenamedAdbBinary = null;

    private static final long CMD_TIMEOUT = 60000L;

    private static final String PATH = "PATH";
    private static final String ANDROID_SERIAL = "ANDROID_SERIAL";

    /**
     * Rename adb binary file to "adb" (from "adb_12345678987654321"), so that the test can find the
     * binary in PATH.
     *
     * @return renamed adb binary if renaming succeeded; null if renaming failed.
     */
    private File renameAdbBinary() {
        IBuildInfo buildInfo = getBuild();
        File adbBinary = null;
        for (VersionedFile f : buildInfo.getFiles()) {
            if (f.getFile().getName().matches("^adb.*")) {
                adbBinary = f.getFile();
                break;
            }
        }
        if (adbBinary == null) {
            CLog.e("Cannot find adb binary in build info");
            return null;
        }
        try {
            mTempDir = FileUtil.createTempDir("adb");
        } catch (IOException e) {
            CLog.e("Cannot create temp directory");
            FileUtil.recursiveDelete(mTempDir);
            return null;
        }
        File renamedAdbBinary = new File(mTempDir, "adb");
        if (!adbBinary.renameTo(renamedAdbBinary)) {
            CLog.e("Cannot rename adb binary");
            return null;
        }
        if (!renamedAdbBinary.setExecutable(true)) {
            CLog.e("Cannot set adb binary executable");
            return null;
        }
        return renamedAdbBinary;
    }

    /** {@inheritDoc} */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mRenamedAdbBinary = renameAdbBinary();
        if (mRenamedAdbBinary == null) {
            throw new RuntimeException("Rename adb binary failed");
        }
        String adbPath = mRenamedAdbBinary.getAbsolutePath();
        mDefaultRunUtil.runTimedCmd(CMD_TIMEOUT, adbPath, "start-server");
        mDefaultRunUtil.sleep(1 * 1000);
        try {
            super.run(listener);
        } finally {
            FileUtil.recursiveDelete(mTempDir);
        }
    }

    /** Set PATH and ANDROID_SERIAL for the RunUtil used by PythonUnittestRunner */
    @Override
    protected IRunUtil getRunUtil() {
        IRunUtil runUtil = super.getRunUtil();
        setAdbPath(runUtil);
        setAndroidSerial(runUtil);
        return runUtil;
    }

    /** Set ANDROID_SERIAL for test_device.py */
    private void setAndroidSerial(IRunUtil runUtil) {
        // Get device serial number using adb command.
        // Do not rely on Device.getSerialNumber because adb test runs on NullDevice
        CommandResult c = mDefaultRunUtil.runTimedCmd(CMD_TIMEOUT, "adb", "devices");
        if (c.getStatus() != CommandStatus.SUCCESS) {
            throw new RuntimeException("Command \"adb serial\" failed" + "\n" + c.getStderr());
        }

        String outputs[] = c.getStdout().split("\n");
        //The first line is always "List of devices attached"
        if (outputs.length <= 1) {
            throw new RuntimeException("No device found");
        }

        // Use any device to run the test
        // Set the serial with the first word of the second line of "adb serial" outputs
        String serial = outputs[1].split("\t")[0];
        CLog.i("$ANDROID_SERIAL=" + serial);
        runUtil.setEnvVariable(ANDROID_SERIAL, serial);
    }

    /** Add adb to PATH */
    private void setAdbPath(IRunUtil runUtil) {
        String adbParentDir = mRenamedAdbBinary.getParent();
        CLog.i("$PATH=" + adbParentDir);
        runUtil.setEnvVariable(PATH, adbParentDir);
    }
}
