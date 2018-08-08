// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * An utility used to control WiFi/bluetooth attenuator through its ip network connection
 * Designed for Mini-circuit USB/ETHERNET programmable attenuator
 */
public class AttenuatorUtil {

    private Socket mAtt = null;
    protected final String mIpAddress;
    private final int mPort;
    private static final int DEFAULT_PORT = 23;
    private static final String SET_ATT_CMD = "SETATT=";
    private static final String GET_ATT_CMD = "ATT?";

    /**
     * Create a new attenuator object by providing an ip address
     * @param ip ip address for the attenuator
     */
    public AttenuatorUtil(String ip) {
        mIpAddress = ip;
        mPort = DEFAULT_PORT;
    }

    public AttenuatorUtil(String ip, int port) {
        mIpAddress = ip;
        mPort = port;
    }

    protected void connect() throws IOException {
        mAtt = null;
        try {
            mAtt = new Socket(mIpAddress, mPort);
        } catch (UnknownHostException e) {
            CLog.e("Unknown host");
            CLog.e(e);
        } catch (ConnectException e) {
            CLog.e("Not able to connect to IP");
            CLog.e(e);
        }
    }

    protected void disconnect() {
        StreamUtil.close(mAtt);
        mAtt = null;
    }

    /**
     * Sending a raw command like this temp = a1.sendCommand("SETATT=70");
     *
     * This method is synchronized because concurrent calls cause output format errors.
     *
     * @param command The command set to attenuator
     * @return The output buffer from the command return
     */
    protected synchronized String sendCommand(String command) {
        PrintWriter out = null;
        BufferedReader in = null;
        String buffer = null;
        try {
            connect();
            if (mAtt == null) {
                CLog.e("Please connect to the attenuator first");
                return buffer;
            }
            try {
                out = new PrintWriter(mAtt.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(mAtt.getInputStream()));
                out.println(command);
                /* Example output from the command
                 *   ATT?
                 *    0.0
                 *   SETATT=10
                 *   1
                 *   ATT?
                 *   10.0
                 */
                buffer = in.readLine();
                buffer = in.readLine();
            } finally {
                StreamUtil.close(out);
                StreamUtil.close(in);
            }
            disconnect();
        } catch (IOException e) {
            CLog.e("Error while executing command '%s'", command);
            CLog.e(e);
        }
        return buffer;
    }

    /**
     * Set attenuator to a value between 0-95,
     * @param value The value you want to set the attenuator
     * @return true if it set correctly, otherwise false
     */
    public boolean setValue(int value) {
        if (value < 0 || value > 95) {
            CLog.e("Use value between 0-95");
            return false;
        }

        String response = sendCommand(getSetCommand(value));
        if (response == null) {
            CLog.e("Failed to set attenuator to %d", value);
            return false;
        }

        try {
            if (Integer.parseInt(response) != 1) {
                CLog.e("Failed to set value set to %d, return %s", value, response);
                return false;
            }

            if (value != getValue()) {
                CLog.e("Failed to set value set to %d", value);
                return false;
            }

            CLog.d("Attenuator %s set to %d", getIdentifier(), value);
            return true;
        } catch (NumberFormatException e) {
            CLog.e("Error format, set value to %d, '%s'", value, response);
            return false;
        }
    }

    /**
     * Get current attenuator value
     * @return the integer value from attenuator for current reading
     */
    public int getValue() {
        String response = sendCommand(getGetCommand());
        if (response == null) {
            CLog.e("Failed to get value, return %s", response);
            return -1;
        }
        float x = 0;
        try {
            x = Float.parseFloat(response);
        } catch (NumberFormatException e) {
            CLog.e("Failed to get value '%s'", response);
            return -1;
        }
        return (int)x;
    }


    /**
     * Builds the command to retrieve the attenuation level.
     * @return The command used to retrieve the attenuation level.
     */
    protected String getGetCommand() {
        return GET_ATT_CMD;
    }

    /**
     * Builds the command to set the attenuation level to an specific value.
     * @param value Any number between 0 and 95 that represents the attenuation level.
     * @return The command to set the attenuation level to an specific value.
     */
    protected String getSetCommand(long value) {
        return String.format("%s%d", SET_ATT_CMD, value);
    }

    /**
     * Returns an string representation of the attenuator that is being controlled by this Utility
     * class.
     * @return An string representation of the attenuator that is being controlled by this Utility.
     */
    public String getIdentifier() {
        return mIpAddress;
    }

    /**
     * Set attenuator to a new value progressively with multiple setting steps from start value
     *
     * @param startValue The starting value for attenuator.
     * @param endValue The final value for attenuator.
     * @param steps The steps it takes to reach the final value, must be a positive number.
     * @param waitTime The wait time in MS between each iteration.
     * @return true if endvalue is set, false otherwise.
     */
    public boolean progressivelySetAttValue(int startValue, int endValue, int steps,
            long waitTime) {
        if (steps <= 0) {
            CLog.e("Use positive number for steps");
            return false;
        }

        if (!setValue(startValue)) {
            return false;
        }

        if (startValue != endValue && waitTime > 0) {
            RunUtil.getDefault().sleep(waitTime);
        }

        int stepSize = Math.abs(endValue - startValue) / steps;
        return progressivelySetAttValue(endValue, stepSize, waitTime);
    }

    /**
     * Set attenuator to a new value progressively with multiple setting steps from current value
     *
     * @param endValue The final value for attenuator.
     * @param stepSize How many levels skip in each iteration. Must be a positive number.
     * @param waitTime The wait time in MS between each iteration.
     * @return true if endvalue is set, false otherwise.
     */
    public boolean progressivelySetAttValue(int endValue, int stepSize, long waitTime) {
        if (stepSize <= 0) {
            System.out.println("Step size is not possitive");
            CLog.e("Use positive step size");
            return false;
        }

        int startLevel = getValue();
        int currentLevel = startLevel;
        while (currentLevel != endValue) {
            if (startLevel < endValue) {
                // Steps are incremental
                currentLevel = Math.min(currentLevel + stepSize, endValue);
            } else {
                // Steps are decremental
                currentLevel = Math.max(currentLevel - stepSize, endValue);
            }

            if(!setValue(currentLevel)){
                System.out.println(String.format("could set currentValue %d", currentLevel));
                return false;
            }

            if (currentLevel != endValue && waitTime > 0) {
                RunUtil.getDefault().sleep(waitTime);
            }
        }

        return true;
    }
}
