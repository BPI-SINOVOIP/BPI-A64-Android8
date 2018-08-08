package com.google.android.aupt;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;

import java.util.Date;

/**
 * Extend the AuptInspectBugReporter to collect companion logs
 */
public class AuptInspectBugCompanionReporter extends AuptInspectBugReporter {

    @Override
    public void testLogSaved(String dataName, LogDataType dataType, InputStreamSource source,
            LogFile file) {
        CLog.i(dataName);
        if (mDisable || mOnlyReportInvFailures) {
            CLog.i("Posting disabled");
            return;
        }

        // Collect compact meminfos from companion
        if (dataType.equals(LogDataType.COMPACT_MEMINFO)
                && dataName.contains("companion")) {
            if (!mRunCreated) {
                // Create the run
                mRunCreated = true;
                // Companion build info
                IBuildInfo companionBuildInfo = new BuildInfo();
                // Want companion to show on same page as Clockwork device
                // so build ID must match
                companionBuildInfo.setBuildId(getBuildInfo().getBuildId());
                companionBuildInfo.setBuildFlavor("companion");
                // and build branch must match
                companionBuildInfo.setBuildBranch(getBuildInfo().getBuildBranch());
                // Make it clear which companion is paired with which watch
                companionBuildInfo.setDeviceSerial(
                        "companion-for-" + getBuildInfo().getDeviceSerial());
                setRunId(createRun(getCurrentTimestamp(), companionBuildInfo));
            }
            Date timestamp = parseTimestamp(file);
            postLog(LogDataType.COMPACT_MEMINFO, source.createInputStream(), file, timestamp);
        }
    }

    /**
     * Create a run on inspect bug
     *
     * @param timestamp The timestamp of the run.
     * @return The id of the run, or null if run was not created.
     */
    private Integer createRun(Date timestamp, IBuildInfo buildInfo) {
        setBuildInfo(buildInfo);
        return createRun(timestamp);
    }

    /**
     * For the companion reporter, I use a special build info that is set in the override of
     * createRun. This is for the purpose of showing the companion memory leaks in the same report
     * as the watch device. I do not want to use the companion build info even though it is provided
     * in multi-device support as the companion app is what I'm interested in and not the build on
     * the companion phone.
     */
    @Override
    public IBuildInfo getPrimaryBuildInfo() {
        return getBuildInfo();
    }
}