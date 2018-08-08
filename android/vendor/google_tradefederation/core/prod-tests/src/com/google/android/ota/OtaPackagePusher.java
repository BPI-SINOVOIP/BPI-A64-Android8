// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.ota;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.google.android.ota.OtaRuleGenerator.IRuleListener;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Class that listens for processed OTA files, and spawns off a separate threads to push the
 * files to OTA download server.config rules for them, and then
 * notifies its own listeners.
 * <p/>
 * Implemented as a separate thread to control access to the single OTA rule writer
 */
class OtaPackagePusher implements IRuleListener {

    private boolean mOverwrite = false;

    private static final int PUSH_CMD_TIMEOUT = 10 * 60 * 1000;
    private static final String OTA_PUSH_PKG_PATH =
            "/google/data/ro/teams/android-build/otapackagetool/otapackagetool.sh";
    private static final String OTA_PKG_DEST = "internal/%s";

    private Queue<Thread> mThreads = new LinkedList<Thread>();

    public void setOverwrite(boolean overwrite) {
        mOverwrite = overwrite;
    }

    public boolean getOverwrite() {
        return mOverwrite;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void otaRuleGenerated(final File otaPackage) {
        Thread t = new Thread(String.format("%s pusher", otaPackage.getName())) {
            @Override
            public void run() {
                // -b is for bandaid - makes download server less sad under load
                List<String> publishCmd = ArrayUtil.list(OTA_PUSH_PKG_PATH, "publish",
                        "-b");
                if (mOverwrite) {
                    // force publish in case an instance of the ota package exists.
                    publishCmd.add("-f");
                }
                publishCmd.add(String.format(OTA_PKG_DEST, otaPackage.getName()));
                publishCmd.add(otaPackage.getAbsolutePath());
                CLog.logAndDisplay(LogLevel.INFO, "Pushing %s", otaPackage.getName());
                CommandResult result = getRunUtil().runTimedCmd(
                        PUSH_CMD_TIMEOUT, publishCmd.toArray(new String[]{}));
                if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                    CLog.e("Push of %s failed: result: %s, stderr: \n%s\n, stdout: \n%s",
                            otaPackage.getName(), result.getStatus(), result.getStderr(),
                            result.getStdout());
                }
            }
        };
        mThreads.add(t);
        t.start();
    }

    public void join() {
        while (!mThreads.isEmpty()) {
            try {
                mThreads.poll().join();
            } catch (InterruptedException e) {
            }
        }
    }

    /**
     * Get {@link IRunUtil} to use.
     * <p/>
     * Exposed so unit tests can mock.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
