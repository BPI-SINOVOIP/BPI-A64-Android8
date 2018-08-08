// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.iot;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

/**
 * This class controls Access Point power on/off by switching the corresponding
 * power plug the AP is attached to.
 *
 * It provides two scripts: one script is to switch off all plugs for a given NPS
 * and the other script is used to switch on a specific plug for a given NPS.
 */
public class ApController {
    /** Default script file directory in TF */
    private static final String SCRIPT_FILE_DIR = "iot";
    /** Script file name to turn plug off */
    private static final String NPS_OFF_SCRIPT_PREFIX = "nps_switch_off";
    /** Script file name for switching power plug on */
    private static final String NPS_ON_SCRIPT_PREFIX = "nps_switch_on";
    /** Suffix for script files */
    private static final String NPS_SCRIPT_SUFFIX = ".exp";
    static InputStream in = null;
    static OutputStream out = null;
    static BufferedReader mBr = null;
    static BufferedWriter mBw = null;
    private String mScriptFileDir;
    private String mOffScriptFilePath;
    private String mOnScriptFilePath;
    /**
     * Record the previous NPS IP address that the controller has been operated on.
     * when AP connected to a second NPS needs to be powered on, the controller will
     * switch off all power plugs on previous NPS first.
     */
    private String mPreviousNPS = null;
    private String mPreviousPlugId = null;

    /**
     * Default constructor. Load the script files.
     *
     * @param scriptPath the directory that holds the script files.
     * @throws Exception if loading script file failed.
     */
    public ApController(String scriptPath) throws Exception{
        if (scriptPath != null) {
            CLog.v("input script path is not null");
            mScriptFileDir = scriptPath;
        } else {
            CLog.v("use the default script");
            mScriptFileDir = SCRIPT_FILE_DIR;
        }
        mOffScriptFilePath = getNpsScriptFilePath(NPS_OFF_SCRIPT_PREFIX);
        CLog.v("mOffScriptFilePath: %s", mOffScriptFilePath);
        mOnScriptFilePath = getNpsScriptFilePath(NPS_ON_SCRIPT_PREFIX);
        CLog.v("mOnScriptFilePath: %s", mOnScriptFilePath);
        mPreviousNPS = null;
        mPreviousPlugId = null;
    }

    private String getNpsScriptFilePath(String scriptFileNamePrefix) throws Exception {
        // load the script from resource folder
        File npsTempFile = null;
        try {
            npsTempFile = File.createTempFile(scriptFileNamePrefix, NPS_SCRIPT_SUFFIX);
            CLog.v("npsTempFile: %s", npsTempFile);
            CLog.v("script file: %s", String.format("%s/%s.%s",
                    mScriptFileDir, scriptFileNamePrefix, NPS_SCRIPT_SUFFIX));
            BufferedInputStream bufferedInput = IotUtil.getSourceFile(String.format("%s/%s%s",
                    mScriptFileDir, scriptFileNamePrefix, NPS_SCRIPT_SUFFIX));
            BufferedOutputStream bufferedOutput = new BufferedOutputStream(
                    new FileOutputStream(npsTempFile));
            CLog.v("create output stream");
            byte[] buffer = new byte[1024];
            int bytesRead = 0;
            while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                bufferedOutput.write(buffer, 0, bytesRead);
                CLog.v(new String(buffer, 0, bytesRead));
            }
            bufferedInput.close();
            bufferedOutput.close();
            CLog.v("npsTempFile path %s", npsTempFile.getAbsolutePath());
            return npsTempFile.getAbsolutePath();
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Switch off all power plugs for all available NPSs in the pool.
     */
    public void switchAllNpsOff() {
        for (IotUtil.NPS nps: IotUtil.NPS.values()) {
            CLog.v("nps ip is: %s", nps.getIpAddress());
            switchNpsOffByIp(nps.getIpAddress());
        }
    }

    /**
     * Switch off all power plugs for a given NPS with IP address.
     * @param ipAddress IP address of the NPS
     */
    public void switchNpsOffByIp(String ipAddress) {
        String command = String.format("expect %s %s", mOffScriptFilePath, ipAddress);
        CLog.v("Command: %s", command);
        executeScript(command);
    }

    /**
     * Switch the power plug on for a given AP.
     *
     * Before switching on the given AP, the previous test AP will be switched off.
     * @param ap the current test AP that needs to be turned on.
     */
    public void enableAp(AccessPointInfo ap) {
        String command;
        String npsIpAddress = IotUtil.NPS.valueOf(ap.getNpsId()).getIpAddress();
        String npsPlugId = ap.getNpsPlugId();

        if (mPreviousNPS == null) {
            // Turn on the first AP on the first NPS
            command = String.format("expect %s %s %s", mOnScriptFilePath, npsIpAddress, npsPlugId);
            // Set previous NPS IP address and NPS plug id
            mPreviousNPS = npsIpAddress;
            mPreviousPlugId = npsPlugId;
        } else {
            if (mPreviousNPS.equals(npsIpAddress)) {
                // If the current NPS id is the same as the previous NPS id,
                // the operation will be switching off the previous plug and switching on
                // the current plug.
                command = String.format("expect %s %s %s %s", mOnScriptFilePath, npsIpAddress,
                        npsPlugId, mPreviousPlugId);
                mPreviousPlugId = npsPlugId;
            } else {
                // If the current NPS id is not equal to the previous NPS, the previous NPS
                // needs to be switched off before enabling the current AP
                switchNpsOffByIp(mPreviousNPS);
                command = String.format("expect %s %s %s", mOnScriptFilePath, npsIpAddress,
                        npsPlugId);
                // reset previous NPS ip address
                mPreviousNPS = npsIpAddress;
                mPreviousPlugId = npsPlugId;
            }
        }
        CLog.v("command to power on AP: %s", command);
        CLog.v("Run expect script to power on AP %s", ap.getBrandModel() + "\n");
        executeScript(command);
    }

    /**
     * Verify whether the given AP has been powered on by the ApController
     *
     * @param ap the given AP
     * @return true if the given AP has been powered on in the last round
     *         false if this is a new AP.
     */
    public boolean isApEnabled(AccessPointInfo ap) {
        String currentIpAddress = IotUtil.NPS.valueOf(ap.getNpsId()).getIpAddress();
        if (mPreviousNPS == null || mPreviousPlugId == null) {
            return false;
        } else {
            return (mPreviousNPS.equals(currentIpAddress)
                    && mPreviousPlugId.equals(ap.getNpsPlugId()));
        }
    }

    private void executeScript(String command) {
        try {
            Process proc = Runtime.getRuntime().exec(command);

            in = proc.getInputStream();
            mBr = new BufferedReader(new InputStreamReader(in));

            String line = null;
            while ((line = mBr.readLine()) != null) {
                CLog.d("line: " + line + "\n");
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            while ((line = br.readLine()) != null) {
                CLog.d("Command return errors: " + line + "\n");
            }
            br.close();

            int exitValue = proc.waitFor();
            if (exitValue != 0) {
                CLog.e("the process didn't exit correctly");
            }
            mBr.close();
            // Wait for 1 minutes for the AP to power on
            getRunUtil().sleep(IotUtil.MIN_AP_POWERUP_TIMER);
            // TODO: Verify the AP is actually on and all other APs are powered off
        } catch (Exception e) {
            CLog.e("read from telnet client throws exception: " + e.toString());
        }
    }

    protected static IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
