// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed;

import com.google.android.tradefed.build.AppLaunchControlProviderFuncTest;
import com.google.android.tradefed.build.DeviceLaunchControlProviderFuncTest;
import com.google.android.tradefed.device.GTestDeviceFuncTest;
import com.google.android.tradefed.result.AndroidBuildApiLogSaverFuncTest;
import com.google.android.tradefed.result.SpongeResultReporterFuncTest;
import com.google.android.tradefed.targetprep.FlashingResourcesParserFuncTest;
import com.google.android.tradefed.targetprep.GoogleAccountPreparerFuncTest;
import com.google.android.tradefed.targetprep.TestAppInstallSetupFuncTest;
import com.google.android.tradefed.util.SsoClientTransportFuncTest;

import com.android.tradefed.testtype.DeviceTestSuite;

import junit.framework.Test;

/**
 * A test suite for all Google Trade Federation functional tests.
 * <p/>
 * This suite requires a device and access to Google NFS and MDB.
 */
public class FuncTests extends DeviceTestSuite {

    public FuncTests() {
        super();
        this.addTestSuite(GTestDeviceFuncTest.class);
        this.addTestSuite(GoogleAccountPreparerFuncTest.class);
        // TODO: run this test manually for now - since it wipes state of device
        //this.addTestSuite(GoogleDeviceFlashPreparerFuncTest.class);
        this.addTestSuite(FlashingResourcesParserFuncTest.class);
        this.addTestSuite(DeviceLaunchControlProviderFuncTest.class);
        this.addTestSuite(AppLaunchControlProviderFuncTest.class);
        this.addTestSuite(TestAppInstallSetupFuncTest.class);
        this.addTestSuite(SpongeResultReporterFuncTest.class);
        this.addTestSuite(SsoClientTransportFuncTest.class);
        this.addTestSuite(AndroidBuildApiLogSaverFuncTest.class);
    }

    public static Test suite() {
        return new FuncTests();
    }
}
