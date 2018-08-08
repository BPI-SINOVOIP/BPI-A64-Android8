// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.power.tests;

import com.android.tradefed.build.IAppBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.command.CommandFileParser;
import com.android.tradefed.command.CommandFileParser.CommandLine;
import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;

import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A specialized utility for running platform power tests on a system with an additional app
 * installed.
 *
 * In this scenario, its desired to use the existing platform power configuration as much as
 * possible. thus this test acts as a wrapper, which tells an existing set of power test to execute
 * but
 * a. install an additional apk(s) after flashing
 * b. report results to the app's branch etc rather than the platform
 */
public class PowerAppScheduler implements IRemoteTest, IBuildReceiver {

    @Option(name = "command-file",
            description = "Command file which contains the commands to run", mandatory = true)
    private File mCommandFile = null;

    private IAppBuildInfo mAppBuild;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mAppBuild);

        try {
            ICommandScheduler scheduler = GlobalConfiguration.getInstance().getCommandScheduler();
            CommandFileParser parser = new CommandFileParser();

            List<CommandLine> commandLines = parser.parseFile(mCommandFile);
            for (CommandLine line : commandLines) {
                List<String> appOptions = buildAppOptions(mAppBuild);
                line.addAll(appOptions);
                scheduler.addCommand(line.asArray());
            }
        } catch (IOException | ConfigurationException e) {
            throw new RuntimeException("failed to parse command file and add commands", e);
        }
    }

    /**
     * Build the list of options to append to each command, that passes in details about the app.
     * @throws IOException
     */
    private List<String> buildAppOptions(IAppBuildInfo appBuild) throws IOException {
        List<String> options = new ArrayList<String>();
        options.add("--rdb-metrics:rdb-branch");
        options.add(appBuild.getBuildBranch());
        options.add("--rdb-metrics:rdb-build-id");
        options.add(appBuild.getBuildId());
        for (VersionedFile f : appBuild.getAppPackageFiles()) {
            String origFileName = f.getFile().getName();
            // create a file copy, so all files can be cleaned up when their invocations end
            File fileCopy = FileUtil.createTempFile(FileUtil.getBaseName(origFileName),
                    FileUtil.getExtension(origFileName));
            fileCopy.delete();
            CLog.d("Copying apk to %s", fileCopy.getAbsolutePath());
            FileUtil.hardlinkFile(f.getFile(), fileCopy);
            options.add("--install-apk:apk-path");
            options.add(fileCopy.getAbsolutePath());
            options.add("--file-cleaner:apk-path");
            options.add(fileCopy.getAbsolutePath());
        }
        return options;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mAppBuild = (IAppBuildInfo)buildInfo;
    }
}
