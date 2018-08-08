// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.compatibility;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Test installs the right version of EpicCitadel and starts it, waits for 5 min,
 * if it does not crash, reports ok.
 */
@OptionClass(alias = "GameUnrealTest")
public class GameUnrealTest implements IDeviceTest, IRemoteTest {

    private static final String DUMPSYS_CMD = "dumpsys window | grep com.epicgames.EpicCitadel";

    private static final int TIMEOUT = 1000 * 5 * 60;

    @Option(name = "apk-version", importance = Importance.IF_UNSET, mandatory = true,
            description = "The apk and obb version to push.")
    private String mApkVersion;

    private ITestDevice mDevice;

    private static final String BASE_PATH = "/google/data/ro/teams/tradefed/testdata/third_party_perf/unreal/%s/";
    private static final String APK_PATH = BASE_PATH + "epic.apk";
    private static final String OBB_PATH = BASE_PATH + "data.obb";
    private static final String PACKAGE_NAME = "com.epicgames.EpicCitadel";
    private static final String AM_COMMAND = "am start -n com.epicgames.EpicCitadel/.UE3JavaApp";
    private static final String OBB_DEST_PATH =
            "/sdcard/Android/obb/com.epicgames.EpicCitadel/main.%s.com.epicgames.EpicCitadel.obb";
    private Map<String, String> mResultMap = new HashMap<String, String>();

    /*
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        long start = System.currentTimeMillis();
        listener.testRunStarted("Unreal", 0);
        runTest(listener);
        listener.testRunEnded(System.currentTimeMillis() - start, mResultMap);
    }

    private void runTest(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier("Unreal", "epic");
        listener.testStarted(testId);
        File apk = new File(String.format(APK_PATH, mApkVersion));
        CLog.d("Installing apk.");
        String status = mDevice.installPackage(apk, true);
        if (status != null) {
            throw new RuntimeException(String.format("Failed to install apk, %s", apk.getPath()));
        }
        CLog.d("Apk installed, pushing obb.");
        File obb = new File(String.format(OBB_PATH, mApkVersion));
        mDevice.pushFile(obb, String.format(OBB_DEST_PATH, mApkVersion));
        CLog.d("Obb pushed, starting app.");
        mDevice.executeShellCommand(AM_COMMAND);
        RunUtil.getDefault().sleep(TIMEOUT);
        if(isProcessCrashed(PACKAGE_NAME)){
            CLog.d("Failed.");
            mResultMap.put("Pass", "0");
            listener.testFailed(testId, String.format(
                    "The app doesn't last %d seconds", TIMEOUT / 1000));
        } else {
            CLog.d("Passed.");
            mResultMap.put("Pass", "1");
        }
        mDevice.uninstallPackage(PACKAGE_NAME);
        listener.testEnded(testId, mResultMap);
    }

    /**
     * @param packageName
     * @return if the package crashed.
     * @throws DeviceNotAvailableException
     */
    private boolean isProcessCrashed(String packageName) throws DeviceNotAvailableException {
        if (mDevice.executeShellCommand("ps").contains(packageName)){
            if (mDevice.executeShellCommand(DUMPSYS_CMD).contains("Error")) {
                return true;
            }
            return false;
        }
        return true;
    }
}
