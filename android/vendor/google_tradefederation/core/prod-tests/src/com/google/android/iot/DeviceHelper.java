// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.iot;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Helper class to control other devices connected to the host
 */
public class DeviceHelper {
    /** Default script file directory in TF */
    private static final String SCRIPT_FILE_DIR = "iot";
    /** Script file name to disable wifi on devices */
    private static final String WIFI_SCRIPT_PREFIX = "disable_wifi";
    @SuppressWarnings("unused")
    private static final String GET_DEVICES_PREFIX = "get_available_devices";
    /** Suffix for script files */
    private static final String BASH_SCRIPT_SUFFIX = ".sh";
    private String mScriptFileDir;
    private String mScriptFilePath;

    /**
     * Default constructor. Load shell script
     *
     * @param scriptPath the directory that holds the script files.
     * @throws IOException if loading script file failed.
     */
    public DeviceHelper(String scriptPath) throws IOException{
        if (scriptPath != null) {
            mScriptFileDir = scriptPath;
        } else {
            mScriptFileDir = SCRIPT_FILE_DIR;
        }
        CLog.v("mScriptFileDir: %s", mScriptFileDir);
        mScriptFilePath = getNpsScriptFilePath(WIFI_SCRIPT_PREFIX);
    }

    public void disableWiFiSettings() {
        getRunUtil().runTimedCmd(60 * 1000, "bash", mScriptFilePath);
    }

    public int getAvailableDevices() {
        int number;
        CommandResult result = new CommandResult();
        for (int i = 0; i < 3; i++) {
            result = getRunUtil().runTimedCmd(5 * 1000, "/bin/bash", "-c",
                    "adb devices | grep \'device$\' | wc -l");
            if (!result.getStatus().equals(CommandStatus.SUCCESS)) {
                getRunUtil().sleep(2*1000);
            } else {
                break;
            }
        }
        if (result.getStdout() == null) {
            return 0;
        }
        try {
            number = Integer.parseInt(result.getStdout().trim());
        } catch (NumberFormatException e) {
            CLog.e("get number of devices exception: %s", e.toString());
            number = 0;
        }
        return number;
    }

    private String getNpsScriptFilePath(String scriptFileNamePrefix) throws IOException {
        // load the script from resource folder
        File scriptTempFile = null;
        scriptTempFile = FileUtil.createTempFile(scriptFileNamePrefix, BASH_SCRIPT_SUFFIX);
        InputStream input = this.getClass().getResourceAsStream(String.format("/%s/%s%s",
                mScriptFileDir, scriptFileNamePrefix, BASH_SCRIPT_SUFFIX));
        FileUtil.writeToFile(input, scriptTempFile);
        return scriptTempFile.getAbsolutePath();
    }

    protected static IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
