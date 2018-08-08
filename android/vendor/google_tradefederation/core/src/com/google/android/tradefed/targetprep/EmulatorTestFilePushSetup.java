// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.ddmlib.FileListingService;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.ISdkBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A {@link ITargetPreparer} that pushes one or more files/dirs from a
 * {@link ISdkBuildInfo#getTestsDir()} folder onto device.
 */
@OptionClass(alias = "emulator-tests-setup")
public class EmulatorTestFilePushSetup implements ITargetPreparer {

    @Option(name = "test-file-name", description =
            "the relative path of a test zip file/directory to install on device. Can be repeated.",
            importance = Importance.IF_UNSET)
    private Collection<String> mTestFileNames = new ArrayList<String>();

    @Option(name = "install-on-sdcard", description =
            "whether or not to push the files to sdcard or not. If false, will push to userdata")
    private boolean mPushToSdCard = false;

    @Option(name = "throw-if-not-found", description =
            "Throw exception if the specified file is not found.")
    private boolean mThrowIfNoFile = true;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        // TODO: refactor code to share common code with TestFilePushSetup.

        if (!(buildInfo instanceof ISdkBuildInfo)) {
            throw new IllegalArgumentException(String.format("Provided buildInfo is not a %s",
                    ISdkBuildInfo.class.getCanonicalName()));
        }
        if (mTestFileNames.size() == 0) {
            CLog.d("No test files to push, skipping");
            return;
        }

        File testDir = ((ISdkBuildInfo) buildInfo).getTestsDir();
        if (testDir == null || !testDir.exists()) {
            throw new TargetSetupError("Provided buildInfo does not contain a valid tests "
                    + "directory", device.getDeviceDescriptor());
        }
        int filePushed = 0;
        for (String fileName : mTestFileNames) {
            // assumes that the test files will be under data
            File localFile = FileUtil.getFileForPath(testDir, "DATA", fileName);
            if (!localFile.exists()) {
                if (mThrowIfNoFile) {
                    throw new TargetSetupError(String.format("Could not find test file %s in "
                            + "extracted %s", localFile.getAbsolutePath(),
                            testDir.getAbsolutePath()), device.getDeviceDescriptor());
                } else {
                    continue;
                }
            }
            String deviceFileName = getDevicePathFromUserData(fileName);
            CLog.d("Pushing file: %s -> %s", localFile.getAbsoluteFile(), deviceFileName);
            if (localFile.isDirectory()) {
                device.pushDir(localFile, deviceFileName);
            } else if (localFile.isFile()) {
                device.pushFile(localFile, deviceFileName);
            }
            // there's no recursive option for 'chown', best we can do here
            device.executeShellCommand(String.format("chown system.system %s", deviceFileName));
            filePushed++;
        }
        if (filePushed == 0) {
            throw new TargetSetupError("No file is pushed from tests.zip",
                    device.getDeviceDescriptor());
        }
    }

    String getDevicePathFromUserData(String path) {
        String partition = mPushToSdCard ?
                FileListingService.DIRECTORY_SDCARD : FileListingService.DIRECTORY_DATA;
        return ArrayUtil.join(FileListingService.FILE_SEPARATOR,
                "", partition, path);
    }
}
