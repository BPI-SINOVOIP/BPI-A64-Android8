/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ITargetPreparer} that dismisses dialogs for the supplied apps
 */
@OptionClass(alias = "dismiss-dialogs")
public class DismissDialogsPreparer extends TestAppInstallSetup
        implements ITestLoggerReceiver {
    private static final String DISMISS_DIALOGS_COMMAND = "am instrument -w "
            + "-e apps %s -e quitOnError %b -e screenshots %b "
            + "com.android.test.util.dismissdialogs/.DismissDialogsInstrumentation";
    private static final String INSTR_SUCCESS = "INSTRUMENTATION_CODE: -1";
    private static final long AVAILABLE_TIMEOUT = 5 * 60 * 1000;
    private static final long MAX_RESPONSE_TIMEOUT = 5 * 60 * 1000;

    private ITestLogger mTestLogger;

    @Option(name = "disable", description = "Disable the dialog dismissal preparer")
    private boolean mDisable = false;

    @Option(name = "dismiss-apps", description = "Dismiss dialogs for the supplied apps, in the "
            + "format of a comma-separated list.")
    private String mDismissApps = "";

    @Option(name = "quit-on-error", description = "Quit the current test suite by throwing an "
            + "exception upon failure to dismiss")
    private boolean mQuitOnError = false;

    @Option(name = "clear-logcat",
            description = "If enabled, clears the logcat before starting to dismiss dialogs")
    private boolean mClearLogcat = false;

    @Option(name = "upload-logcat", description = "Upload the logs from this preparer.")
    private boolean mUploadLogcat = false;

    @Option(name = "upload-screenshots", description = "Upload the screenshots from this preparer.")
    private boolean mUploadScreenshots = false;

    @Option(name = "screenshot-dir", description = "The device folder where screenshots are saved.")
    private String mScreenshotDir = "${EXTERNAL_STORAGE}/dialog-dismissal/";

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException {
        if (mDisable) {
            return;
        } else if(mDismissApps.isEmpty()) {
            CLog.i("Skipping dialog dismissal because the apps list is empty.");
            return;
        }

        if (mClearLogcat) {
            device.clearLogcat();
        }

        super.setUp(device, buildInfo);

        CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        String dismissCommand = String.format(DISMISS_DIALOGS_COMMAND,
                mDismissApps, mQuitOnError, mUploadScreenshots);
        device.executeShellCommand(
                dismissCommand, outputReceiver, MAX_RESPONSE_TIMEOUT, TimeUnit.MILLISECONDS, 1);

        if (mUploadLogcat) {
            try (InputStreamSource source = device.getLogcat()) {
                mTestLogger.testLog("dismiss-dialogs-logcat", LogDataType.TEXT, source);
            }
        }

        if (mUploadScreenshots) {
            uploadScreenshots(device);
        }

        String output = outputReceiver.getOutput();
        if (output == null) {
            String error = "Unknown error receiving command output.";
            if (mQuitOnError) {
                throw new TargetSetupError(error, device.getDeviceDescriptor());
            } else {
                CLog.w(error);
            }
        } else if (!output.contains(INSTR_SUCCESS)) {
            String error = String.format("Failed dismissal.\nCommand output: %s", output);
            if (mQuitOnError) {
                throw new TargetSetupError(error, device.getDeviceDescriptor());
            } else {
                CLog.w(error);
            }
        }
    }

    private void uploadScreenshots (ITestDevice device) throws DeviceNotAvailableException {
        device.waitForDeviceAvailable(AVAILABLE_TIMEOUT);
        IFileEntry screenshotDir = device.getFileEntry(mScreenshotDir);
        if (screenshotDir != null) {
            for (IFileEntry fileEntry : screenshotDir.getChildren(false)) {
                // Exclude non-PNG/UIX files
                LogDataType dataType = null;
                if (fileEntry.getName().endsWith(".png")) {
                    dataType = LogDataType.PNG;
                } else if (fileEntry.getName().endsWith(".uix")) {
                    dataType = LogDataType.XML;
                } else {
                    continue;
                }
                // Pull the file off the device
                File pulledFileEntry = device.pullFile(fileEntry.getFullPath());
                if (pulledFileEntry == null) {
                    CLog.w("Could not pull file entry %s", fileEntry.getFullPath());
                    continue;
                }
                // Upload the files
                try (FileInputStreamSource fileStream =
                        new FileInputStreamSource(pulledFileEntry)) {
                    mTestLogger.testLog(
                            FileUtil.getBaseName(pulledFileEntry.getName()), dataType, fileStream);
                }
                // Clean up resources
                FileUtil.deleteFile(pulledFileEntry);
            }
        } else {
            CLog.e("Could not find screenshot directory %s", mScreenshotDir);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mTestLogger = testLogger;
    }
}
