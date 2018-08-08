// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.roboelectric;

import java.util.List;

/**
 * Test for the V2 roboelectric framework
 */
public class RoboElectricV2Test extends RoboElectricTest {

    /**
     * Build the debug flags to pass to JVM
     * @param args
     */
    @Override
    protected void buildDebugFlags(List<String> args) {
        args.add("-Dandroid.manifest=./AndroidManifest.xml");
        args.add("-Dandroid.resources=./res");
        args.add("-Dandroid.assets=./assets");
        args.add("-XX:MaxPermSize=256m");
    }
}
