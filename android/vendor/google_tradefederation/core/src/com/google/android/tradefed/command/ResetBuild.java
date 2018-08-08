// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.command;

import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.ArgsOptionParser;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.log.StdoutLogger;
import com.google.android.tradefed.build.LaunchControlProvider;
import com.google.android.tradefed.build.QueryType;
import com.google.android.tradefed.build.StubLaunchControlProvider;

/**
 * A command line utility for informing launch control to reset a build.
 * <p/>
 * Required command line options: --test-tag --branch --build-flavor --build-id
 */
public class ResetBuild {

    void run(String[] args)  {
        try {
            DdmPreferences.setLogLevel(LogLevel.DEBUG.getStringValue());
            Log.addLogger(new StdoutLogger());
            LaunchControlProvider provider = new StubLaunchControlProvider();
            ArgsOptionParser parser = new ArgsOptionParser(provider);
            parser.parse(args);
            provider.setQueryType(QueryType.RESET_TEST_BUILD);
            provider.resetTestBuild();
        } catch (ConfigurationException e) {
            System.err.println("Invalid arguments provided");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ResetBuild resetter = new ResetBuild();
        resetter.run(args);
    }
}
