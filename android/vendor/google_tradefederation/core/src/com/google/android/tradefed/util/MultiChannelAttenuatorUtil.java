// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;

/**
 * An utility used to control WiFi/bluetooth attenuator through its ip network connection
 * Designed for Mini-circuit USB/ETHERNET programmable attenuator
 */
public class MultiChannelAttenuatorUtil extends AttenuatorUtil {

    private final int mChannel;
    private static final String SET_ATT_CMD = "CHAN:%d:SETATT:%d";
    private static final String GET_ATT_CMD = "CHAN:%d:ATT?";

    /**
     * Create a new attenuator object by providing an ip address
     * @param ip ip address for the attenuator
     * @param channel the channel number. This could be 1, 2, 3 or 4.
     */
    public MultiChannelAttenuatorUtil(String ip, int channel) {
        super(ip);
        testCommand(channel);
        mChannel = channel;
    }

    /**
     * Create a new attenuator object
     *
     * @param ip ip address for the attenuator
     * @param port port to be used for communication
     * @param channel the channel number. This could be 1, 2, 3 or 4.
     */
    public MultiChannelAttenuatorUtil(String ip, int port, int channel) {
        super(ip, port);
        testCommand(channel);
        mChannel = channel;
    }

    private void testCommand(int channel) {
        try {
            String testCommand = String.format(GET_ATT_CMD, channel);
            String response = sendCommand(testCommand);
            Float.parseFloat(response);
        } catch (NumberFormatException e) {
            CLog.e(e);
            CLog.e("Failed to connect through channel '%d'", channel);
            throw new IllegalArgumentException(
                    String.format(
                            "Incorrect response from channel number %d. Verify channel number.",
                            channel));
        }
    }

    @Override
    public String getIdentifier(){
        return String.format("%s ch:%d", mIpAddress, mChannel);
    }

    @Override
    protected String getSetCommand(long value){
        return String.format(SET_ATT_CMD, mChannel, value);
    }

    @Override
    protected String getGetCommand(){
        return String.format(GET_ATT_CMD, mChannel);
    }
}
