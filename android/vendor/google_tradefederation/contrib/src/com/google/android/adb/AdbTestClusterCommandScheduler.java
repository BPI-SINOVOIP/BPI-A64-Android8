// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.adb;

import com.google.android.tradefed.cluster.ClusterCommandScheduler;

/** A Cluster Command Scheduler for tests that require exclusive access to adb server. */
public class AdbTestClusterCommandScheduler extends ClusterCommandScheduler {

    /** block the scheduler loop until all (should only have one) invocations finish. */
    @Override
    protected void postProcessReadyCommands() {
        waitForAllInvocationThreads();
        getDeviceManager().restartAdbBridge();
    }

    /** Stop adb bridge before adb test invocation. */
    @Override
    protected void initInvocation() {
        getDeviceManager().stopAdbBridge();
    }
}
