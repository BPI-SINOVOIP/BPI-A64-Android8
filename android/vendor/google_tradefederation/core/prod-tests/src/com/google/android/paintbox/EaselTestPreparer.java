/*
 * Copyright 2017 Google Inc. All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.paintbox;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.TargetSetupError;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/** An ITargetPreparer that attempts to set up Easel state and test artifacts. */
@OptionClass(alias = "easel-test-preparer")
public class EaselTestPreparer implements ITargetCleaner {

    private static final String AP_SIDE_DIR = "/data/local/tmp/";
    private static final String AP_FIRMWARE_DIR = "/vendor/firmware/easel";
    private String mHdrPlusMode = "0";

    @Option(
        name = "firmware-dir",
        description = "Full path of easel firmware directory on host machine",
        importance = Importance.ALWAYS
    )
    private String mFirmwareDir =
            "/google/data/ro/teams/paintbox/tf_tests/easel_software/system/output/mnh-busybox/uboot";

    @Option(
        name = "google3-dir",
        description = "Full path of Google3 top directory",
        importance = Importance.ALWAYS
    )
    private String mGoogle3TopDir = "/google/data/ro/teams/paintbox/tf_tests/google3";

    @Option(
        name = "ipu-dir",
        description = "Full path of IPU top directory",
        importance = Importance.ALWAYS
    )
    private String mIpuTopDir = "/google/data/ro/teams/paintbox/tf_tests/easel_software/IPU";

    @Option(
        name = "ipu-test",
        description = "Run Paintbox IPU tests",
        importance = Importance.ALWAYS
    )
    private boolean mIpuTest = false;

    @Option(name = "test-inputs", description = "Inputs for test", importance = Importance.ALWAYS)
    private List<String> mTestInputs = new ArrayList<String>();

    @Option(
        name = "debug",
        description = "Debug mode. When it is on, input files on AP and SoC will not be removed."
    )
    private boolean mDebug = false;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {

        mTestInputs = getFullPath(mTestInputs);
        CLog.d(getTestPreparerString());

        // remount / and /vendor as rw is changed for walleye due to b/34124301
        device.enableAdbRoot();
        device.executeShellCommand("avbctl disable-verity");
        // enable HDR+
        mHdrPlusMode = device.executeShellCommand("getprop persist.camera.hdrplus.enable");
        if (!mHdrPlusMode.equals("1")) {
            device.executeShellCommand("setprop persist.camera.hdrplus.enable 1");
        }
        device.reboot();

        device.enableAdbRoot();
        device.remountSystemWritable();
        updateEaselFirmware(device);
        // deactivate Easel in case Easel is in active mode,
        // otherwise Easel will hang if activate twice
        device.executeShellCommand("pbticlient --deactivate");
        // activate Easel
        device.executeShellCommand("pbticlient --activate");
        try {
            pushFileToEasel(device, mTestInputs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable exception)
            throws DeviceNotAvailableException {

        if (!mDebug) {
            // remove everything inside AP_SIDE_DIR
            device.executeShellCommand(String.format("rm -rf %s/*", AP_SIDE_DIR));
            // put Easel to suspend mode
            device.executeShellCommand("pbticlient --deactivate");
            // revert the hdrplus property
            device.executeShellCommand("setprop persist.camera.hdrplus.enable " + mHdrPlusMode);
        }
    }

    private void executeEaselShellCommand(ITestDevice device, String easelCommand)
            throws DeviceNotAvailableException {
        CLog.d("Executing easel shell command: " + easelCommand);
        String cmd = String.format("pbticlient --command '%s'", easelCommand);
        device.executeShellCommand(cmd);
    }

    private void executeEaselShellCommand(
            ITestDevice device, String easelCommand, String logPath, long timeoutSeconds)
            throws DeviceNotAvailableException {
        CLog.d(
                "Executing easel shell command: %s, log file: %s, timeout seconds %s",
                easelCommand, logPath, timeoutSeconds);
        String cmd =
                String.format(
                        "pbticlient --command '%s' --log_path '%s' --timeout_seconds %d",
                        easelCommand, logPath, timeoutSeconds);
        device.executeShellCommand(cmd);
    }

    private void updateEaselFirmware(ITestDevice device) throws DeviceNotAvailableException {
        device.executeShellCommand("rm -rf " + AP_FIRMWARE_DIR);
        device.executeShellCommand("mkdir -p " + AP_FIRMWARE_DIR);
        File firmwareDir = new File(mFirmwareDir);
        File[] firmwareFiles = firmwareDir.listFiles();
        for (File f : firmwareFiles) {
            device.pushFile(f, AP_FIRMWARE_DIR + "/" + f.getName());
        }
        // update stage firmware
        String stageFirmware = "/sys/devices/virtual/misc/mnh_sm/stage_fw";
        device.executeShellCommand("echo 1 > " + stageFirmware);
    }

    private boolean pushFileToEasel(ITestDevice device, List<String> filePaths)
            throws DeviceNotAvailableException, FileNotFoundException {
        for (String filePath : filePaths) {
            if (!pushFileToEasel(device, filePath)) {
                return false;
            }
        }
        return true;
    }

    private boolean pushFileToEasel(ITestDevice device, String filePath)
            throws DeviceNotAvailableException, FileNotFoundException {
        if (!pushFileFromHostToDevice(device, filePath)) {
            CLog.e("ERROR: Failed to push <%s> from host to device", filePath);
            return false;
        }
        pushFileFromDeviceToEasel(device, filePath);
        return true;
    }

    private boolean pushFileFromHostToDevice(ITestDevice device, String filePath)
            throws DeviceNotAvailableException, RuntimeException {
        File file = new File(filePath);
        CLog.d("Pushing <%s> from host to device", filePath);
        if (file.isFile()) {
            return device.pushFile(file, AP_SIDE_DIR + filePath);
        } else {
            return device.pushDir(file, AP_SIDE_DIR + filePath);
        }
    }

    private void pushFileFromDeviceToEasel(ITestDevice device, String filePath)
            throws DeviceNotAvailableException, FileNotFoundException, RuntimeException {
        File file = new File(filePath);
        String easelPath = (file.isFile() ? filePath : file.getParent());
        String cmd =
                String.format(
                        "ezlsh push %s %s", AP_SIDE_DIR + getFileFullPathForEasel(file), easelPath);
        CLog.d("Executing: " + cmd);
        device.executeShellCommand(cmd);
    }

    private String getFileFullPathForEasel(File file) {
        String dirName = file.getParent();
        String fileName = file.toPath().getFileName().toString();
        // In case the filename has "$" character, we have to change it to "\$",
        // otherwise a filename like  __yuv$1.tmp could cause issues when using ezlsh.
        return new File(dirName, fileName.replace("$", "\\$")).toString();
    }

    private String getFullPath(String relativePath) {
        return (mIpuTest ? mIpuTopDir : mGoogle3TopDir) + "/" + relativePath;
    }

    private List<String> getFullPath(List<String> relativePaths) {
        String dirName = (mIpuTest ? mIpuTopDir : mGoogle3TopDir);
        List<String> fullPaths = new ArrayList<String>();
        for (String relativePath : relativePaths) {
            fullPaths.add(dirName + "/" + relativePath);
        }
        return fullPaths;
    }

    private String getTestPreparerString() {
        String configString = "\n*********************************\n";
        for (String input : mTestInputs) {
            File file = new File(input);
            String prefix = (file.isFile() ? "Input File: " : "Input Directory: ");
            configString += prefix + input + "\n";
        }
        configString += "*********************************\n";

        return configString;
    }
}
