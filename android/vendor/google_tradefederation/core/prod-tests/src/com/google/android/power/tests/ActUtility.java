/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.ZipUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.zip.ZipFile;

import org.junit.Assert;

public class ActUtility {

    private static final String ACTS_ZIP_FILE = "acts.zip";
    private static final Path TEST_SCRIPT_DIR_SUFFIX =
            Paths.get("tools", "test", "connectivity", "acts", "tests", "google");

    private static final Path ACT_SCRIPT_DIR_SUFFIX =
            Paths.get(
                    "tools", "test", "connectivity", "acts", "framework", "acts", "bin", "act.py");
    private static final long ACTS_DOWNLOAD_TIMEOUT_MSECS = 3 * 60 * 1000; // 3 mins
    private static final long SCRIPT_START_TIMEOUT_MSECS = 30 * 1000;
    private static final long SMALL_DELAY_MSECS = 3 * 1000;
    private static final String SCRIPT_RUN_CHECK_CMD = "ps -aux";

    public static void fetchActsScripts(String target, String buildId, String tempDir)
            throws IOException {

        final Path actsDestination = Paths.get(tempDir, ACTS_ZIP_FILE);
        String fetch_artifact_command[] = {
            "/google/data/ro/projects/android/fetch_artifact",
            "--bid",
            buildId,
            "--target",
            target,
            ACTS_ZIP_FILE,
            actsDestination.toAbsolutePath().toString()
        };

        CommandResult response =
                RunUtil.getDefault()
                        .runTimedCmd(ACTS_DOWNLOAD_TIMEOUT_MSECS, fetch_artifact_command);
        CLog.d(response.getStdout());
        CLog.d(response.getStderr());
        Assert.assertTrue("Unable to download acts.zip", Files.exists(actsDestination));
        ZipUtil.extractZip(
                new ZipFile(actsDestination.toAbsolutePath().toString()), new File(tempDir));
        Assert.assertTrue(
                "Unable to extract acts zip package",
                Files.exists(getScriptDir(tempDir).toAbsolutePath()));
    }

    public static Path getScriptDir(String rootDir) {
        return Paths.get(rootDir, TEST_SCRIPT_DIR_SUFFIX.toString());
    }

    public static File createConfigFile(String filename, String contents) throws IOException {
        File configFile = FileUtil.createTempFile(filename, ".config");
        FileUtil.writeToFile(contents, configFile);
        return configFile;
    }

    public static String getHostName() {
        String hostName = "localhost";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            CLog.e(e);
        }
        return hostName;
    }

    public static void startSetupScript(String configFilePath, String testcase, String rootDir)
            throws IOException {

        final Path actsScript = Paths.get(rootDir, ACT_SCRIPT_DIR_SUFFIX.toString());

        String change_permission_cmd[] = {"chmod", "777", actsScript.toAbsolutePath().toString()};
        RunUtil.getDefault().runTimedCmd(SMALL_DELAY_MSECS, change_permission_cmd);

        String setup_script_cmd[] = {
            actsScript.toAbsolutePath().toString(), "-c", configFilePath, "-tc", testcase
        };
        CLog.d(Arrays.toString(setup_script_cmd));
        Process scriptProcess = RunUtil.getDefault().runCmdInBackground(setup_script_cmd);
        BufferedReader outputReader =
                new BufferedReader(new InputStreamReader(scriptProcess.getInputStream()));
        long startTime = System.currentTimeMillis();
        boolean timedOut = true;
        String line;
        while ((System.currentTimeMillis() - startTime) < SCRIPT_START_TIMEOUT_MSECS) {
            line = outputReader.readLine();
            CLog.d(line);
            if (line.contains("Waiting for client socket connection")) {
                timedOut = false;
                break;
            }
        }
        Assert.assertFalse("Setup script failed to start", timedOut);
        // Small sleep to ensure the script has started
        RunUtil.getDefault().sleep(SMALL_DELAY_MSECS);
        Assert.assertTrue("Script is not running", isSetupScriptRunning(configFilePath));
    }

    public static boolean isSetupScriptRunning(String configFilePath) {
        CommandResult response =
                RunUtil.getDefault()
                        .runTimedCmd(SMALL_DELAY_MSECS, SCRIPT_RUN_CHECK_CMD.split(" "));
        if (response.getStdout().contains(configFilePath)) {
            return true;
        }
        return false;
    }
}
