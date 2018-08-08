// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.IMultiDeviceRecovery;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.util.List;

/**
 * A {@link IMultiDeviceRecovery} which tries to recover ADB if no devices are visible.
 */
public class AdbRecovery implements IMultiDeviceRecovery {

    private static final long ADB_RECOVERY_TOOL_TIMEOUT = 60 * 1000;

    @Option(name = "adb-tool-path",
            description = "the path to adb recovery tool binary.")
    private String mAdbRecoveryToolPath = "adb_recovery.par";

    @Option(name = "always-adb-recovery",
            description = "always run the adb recovery tool binary.")
    private boolean mAlwaysRun = false;

    /**
     * {@InheritDoc}
     */
    @Override
    public void recoverDevices(List<IManagedTestDevice> devices) {
        if (!devices.isEmpty() && !mAlwaysRun) {
            CLog.w("Devices seem to be up, skipping ADB recovery.");
            return;
        }
        // Run adb recovery tool.
        final IRunUtil runUtil = getRunUtil();
        final CommandResult result = runUtil.runTimedCmd(ADB_RECOVERY_TOOL_TIMEOUT,
                mAdbRecoveryToolPath);
        if (result.getStatus() != CommandStatus.SUCCESS) {
            CLog.w("failed to reset ADB: stdout=%s, stderr=%s", result.getStdout(),
                    result.getStderr());
        }
    }

    /**
     * Returns a {@link IRunUtil} instance.
     * Exposed for testing.
     *
     * @return a {@link IRunUtil} instance.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
