/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.power.tests;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipFile;

import org.junit.Assert;

/** Extracts voice model data file from hotword APK and pushes it to /sdcard */
@OptionClass(alias = "hotword-preparer")
public class HotwordPreparer implements ITargetPreparer {

    @Option(
        name = "hotword-apk-file",
        description = "Name of the hotword APK file",
        mandatory = true
    )
    private String mAPKName = null;

    private static final String MODEL_NAME = "en_us.mmap";

    private Path mTmpDir;
    private Path mModelDataFile;
    private File mAPKFile;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, DeviceNotAvailableException, BuildError {
        try {
            pullAPKFromDevice(device);
            extractModelDataFile();
            pushModelDataFile(device);
        } catch (IOException e) {
            CLog.e(e);
            throw new TargetSetupError(e.getMessage(), device.getDeviceDescriptor());
        }
    }

    private void pullAPKFromDevice(ITestDevice device)
            throws DeviceNotAvailableException, IOException {
        String apkPath = String.join("/", "system", "priv-app", mAPKName, mAPKName + ".apk");
        mAPKFile = FileUtil.createTempFile(mAPKName, ".apk");
        Assert.assertTrue(
                "Unable to pull hotword APK from device", device.pullFile(apkPath, mAPKFile));
    }

    private void extractModelDataFile() throws IOException {
        mTmpDir = Paths.get(System.getProperty("java.io.tmpdir"), "lc_cache", mAPKName);
        mModelDataFile = Paths.get(mTmpDir.toAbsolutePath().toString(), "res", "raw", MODEL_NAME);
        ZipUtil.extractZip(new ZipFile(mAPKFile), mTmpDir.toFile());
        Assert.assertTrue(
                "Unable to extract model data file", Files.exists(mModelDataFile.toAbsolutePath()));
    }

    private void pushModelDataFile(ITestDevice device) throws DeviceNotAvailableException {
        String modelFileDestination = String.join("/", "sdcard", MODEL_NAME);

        Assert.assertTrue(
                "Unable to push the model data file",
                device.pushFile(mModelDataFile.toFile(), modelFileDestination));
    }
}
