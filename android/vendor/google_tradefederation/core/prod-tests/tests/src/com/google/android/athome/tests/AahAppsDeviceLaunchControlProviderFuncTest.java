// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.athome.tests;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IAppBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.google.android.tradefed.build.QueryType;
import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import junit.framework.TestCase;

/**
 * Functional tests for {@link DeviceWithAahAppsLaunchControlProvider}.
 */
public class AahAppsDeviceLaunchControlProviderFuncTest extends TestCase {

    /**
     * Sanity test that {@link DeviceWithAahAppsLaunchControlProvider#getBuild()}
     * returns a valid build.
     */
    public void testGetBuild() throws BuildRetrievalError {
        DeviceWithAahAppsLaunchControlProvider provider =
                new DeviceWithAahAppsLaunchControlProvider();
        provider.setBranch("git_ics-aah-release");
        provider.setQueryType(QueryType.QUERY_LATEST_BUILD);
        provider.setBuildFlavor("tungsten-userdebug");
        provider.setTestTag("AahAppsDeviceLaunchControlProviderFuncTest");
        provider.skipDownload(BuildAttributeKey.USER_DATA);
        provider.skipDownload(BuildAttributeKey.OTA_PACKAGE);

        IBuildInfo build = provider.getBuild();
        assertTrue(build instanceof IDeviceBuildInfo);
        assertTrue(build instanceof IAppBuildInfo);
    }
}
