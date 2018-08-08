/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.utils.usb.switches;

import com.android.tradefed.util.RunUtil;

public class DatamationUsbSwitch implements IUsbSwitch {

    private static final long CMD_TIME_OUT = 30 * 1000;
    private static final String U16S_PY_LIBRARY = "u16s.py";

    /**
     * Argument that precedes which port is to be manipulated.
     */
    private static final String PORT_ID_ARGUMENT_NAME = "-d";

    /**
     * Argument to turn on the USB connection.
     */
    public static final String SYNC_ARGUMENT = "-m s";

    /**
     * Argument to turn off the USB connection.
     */
    public static final String OFF_ARGUMENT = "-m o";

    private final String mUSBSwitchPortID;

    public DatamationUsbSwitch(String usbSwitchPortID) {
        mUSBSwitchPortID = usbSwitchPortID;
    }

    @Override
    public void connectUsb() {
        RunUtil.getDefault()
                .runTimedCmd(CMD_TIME_OUT, U16S_PY_LIBRARY, PORT_ID_ARGUMENT_NAME, mUSBSwitchPortID,
                        SYNC_ARGUMENT);
    }

    @Override
    public void disconnectUsb() {
        RunUtil.getDefault()
                .runTimedCmd(CMD_TIME_OUT, U16S_PY_LIBRARY, PORT_ID_ARGUMENT_NAME, mUSBSwitchPortID,
                        OFF_ARGUMENT);
    }

    @Override
    public void powerCycle() {
        // Can't perform a power cycle on a Datamation.
    }

    @Override
    public void freeResources() {
        // Can't free resources for a Datamation port without interfering with other Datamation ports.
    }
}
