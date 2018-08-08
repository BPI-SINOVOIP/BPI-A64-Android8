/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.log.LogUtil.CLog;

import com.google.android.tradefed.build.DeviceWithAppLaunchControlProvider;
import com.google.android.tradefed.build.QueryType;
import com.google.android.tradefed.build.RemoteBuildInfo;
/**
 * This is a workaround to synchronize the application version of
 * the companion device and the wear device.
 * Query the LCprovider to get the next available test build and prepare
 * the command file with the specific build for both companion and wear device
 * invocation.
 */
public class BuildUtility extends DeviceWithAppLaunchControlProvider {

    public String getTestBuild(String branch, String buildFlavor, String testTag,
            String lcpHost) throws BuildRetrievalError {
        DeviceWithAppLaunchControlProvider lcProvider = new DeviceWithAppLaunchControlProvider();
        RemoteBuildInfo remoteBuildInfo;
        lcProvider.setBranch(branch);
        lcProvider.setBuildFlavor(buildFlavor);
        lcProvider.setTestTag(testTag);
        if (lcpHost == null) {
            remoteBuildInfo = lcProvider.getRemoteBuild();
        } else {
            lcProvider.setQueryType(QueryType.LATEST_WHITELISTED_CL);
            lcProvider.setLcHostname(lcpHost);
            remoteBuildInfo = lcProvider.getRemoteBuild();
        }

        if (remoteBuildInfo == null){
            CLog.i("Remote build ino is null, no new build");
            // Return null as no build to test.
            return null;
        }
        return remoteBuildInfo.getBuildId();
    }
}
