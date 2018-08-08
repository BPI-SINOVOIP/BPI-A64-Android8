// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import com.android.tradefed.build.DeviceBuildDescriptor;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;


/** Reports the test metrics for each run to the Release Dashboard for unbundled apps */
@OptionClass(alias = "rdb-unbundled-metrics")
public class RdbUnbundledRunMetricsResultReporter extends RdbRunMetricsResultReporter {

    /**
     * Post the metrics for each test in this invocation to the release DB.
     *
     * @param buildInfo {@link IBuildInfo}
     */
    @Override
    protected void postData(IBuildInfo buildInfo) {
        if (DeviceBuildDescriptor.describesDeviceBuild(buildInfo)) {
            super.postData(buildInfo);
        } else {
            CLog.e("missing device build info in build %s", buildInfo.getBuildId());
        }
    }
}
