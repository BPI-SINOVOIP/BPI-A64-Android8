// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A helper for AtenuatorUtil unit test
 */
public class FakeAttenuatorUtil extends AttenuatorUtil {

    private static final String ERROR_MSG =
            "-99 Unrecognized Command. " + "Model: RCDAT-6000-90 SN=11501150004";
    private static final String CORRECT_MSG = "1";
    private static final String FAKE_IP = "127.0.0.1";
    private List<Integer> mValuesSet = new ArrayList<>();

    public List<Integer> getValuesSet(){
        return mValuesSet;
    }

    public void clearValuesSet(){
        mValuesSet = new ArrayList<>();
    }

    private int mInternalValue = 90;

    public FakeAttenuatorUtil() {
        super(FAKE_IP);
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
     * Simulate attenuator behavior when sending it a command
     * Example output from the command
     *   ATT?
     *    0.0
     *   SETATT=10
     *   1
     *   ATT?
     *   10.0
     * ATT? command will return current setting for the attenuator
     * SETATT command will set the attenuator to a new value
     *
     * @param command The command set to attenuator
     * @return The output buffer from the command return
     */
    @Override
    protected String sendCommand(String command) {
        if ("ATT?".equals(command)) {
            return Integer.toString(mInternalValue);
        } else if (command.substring(0, 7).equals("SETATT=")) {
            try {
                int newValue = Integer.parseInt(command.substring(7));
                if (newValue >= 0 && newValue <= 95) {
                    mInternalValue = newValue;
                    return CORRECT_MSG;
                }

                return ERROR_MSG;
            } catch (NumberFormatException e) {
                return ERROR_MSG;
            }
        }

        return ERROR_MSG;
    }

    @Override
    public boolean setValue(int value){
        mValuesSet.add(value);
        return super.setValue(value);
    }
}
