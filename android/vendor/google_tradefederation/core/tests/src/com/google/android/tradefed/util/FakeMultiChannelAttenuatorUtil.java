// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.util;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A helper for AtenuatorUtil unit test
 */
public class FakeMultiChannelAttenuatorUtil extends MultiChannelAttenuatorUtil {

    private static final String ERROR_MSG =
            "-99 Unrecognized Command. " + "Model: RCDAT-6000-90 SN=11501150004";

    /**
     * Create a new attenuator object by providing an ip address
     *
     * @param ip ip address for the attenuator
     * @param channel the channel number.
     */
    public FakeMultiChannelAttenuatorUtil(String ip, int channel) {
        super(ip, channel);
    }

    @Override
    protected void connect () throws IOException {
        return;
    }

    @Override
    protected void disconnect() {
        return;
    }

    /**
     * Simulate attenuator with four channels behavior when sending it a command
     * Example output from the command
     *   CHAN:1:ATT?
     *    0.0
     *   SETATT=10
     *   1
     *   ATT?
     *   0.0 0.0 0.0 0.0
     * ATT? command will return current setting for the attenuator
     * SETATT command will set the attenuator to a new value
     *
     * @param command The command set to attenuator
     * @return The output buffer from the command return
     */
    @Override
    protected String sendCommand(String command) {
        Pattern getPattern = Pattern.compile("CHAN:(\\d+):ATT\\?");
        Matcher getMatcher = getPattern.matcher(command);
        if (getMatcher.matches()) {
            if (getMatcher.group(1).equals("0") ){
                return " 0.0 0.0 0.0 0.0";
            }

            return "0.0";
        }

        return ERROR_MSG;
    }
}
