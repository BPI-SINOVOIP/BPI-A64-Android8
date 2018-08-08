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

import com.google.android.utils.IMonsoonController;
import com.google.android.utils.MonsoonController;
import com.google.android.utils.MonsoonController.InteractionMode;

import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

public class MonsoonUsbSwitch implements IUsbSwitch {
    private static final double DEFAULT_START_CURRENT = 7.8d;

    private static final double DEFAULT_CURRENT = 7.8d;

    private final IMonsoonController mMonsoon;

    private long mResetSleepTime = 2 * 1000; // 2 seconds

    private double mResetVoltage = 4.2;

    /**
     * Creates an instance of MonsoonUsbSwitch.
     *
     * @param controller the Monsoon usb controller to intercat with.
     */
    public MonsoonUsbSwitch(IMonsoonController controller) {
        mMonsoon = controller;
    }

    /**
     * Creates an instance of MonsoonUsbSwitch.
     *
     * @param executablePath the path to the monsoon executable library.
     * @param serial the serial number of the monsoon power monitor to be handled.
     * @param mode specifies if the serial port or the serial number should be used to interact with
     *     the monsoon power monitor.
     */
    public MonsoonUsbSwitch(String executablePath, String serial, InteractionMode mode) {
        this(new MonsoonController(executablePath, serial, mode));
    }

    /**
     * Creates an instance of MonsoonUsbSwitch.
     *
     * @param executablePath the path to the monsoon executable library.
     * @param serial the serial number of the monsoon power monitor to be handled.
     */
    public MonsoonUsbSwitch(String executablePath, String serial) {
        this(executablePath, serial, MonsoonController.InteractionMode.USE_SERIAL_NUMBER);
    }

    public void setResetVoltage(double value) {
        mResetVoltage = value;
    }

    public void setResetSleepTime(long value) {
        mResetSleepTime = value;
    }

    /** Get {@link IRunUtil} to use. Exposed so unit tests can mock. */
    protected IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    @Override
    public void connectUsb() {
        mMonsoon.connectUsb();
    }

    @Override
    public void disconnectUsb() {
        mMonsoon.disconnectUsb();
    }

    @Override
    public void freeResources() {
        mMonsoon.freeResources();
    }

    @Override
    public void powerCycle() {
        try {
            disconnectUsb();
            getRunUtil().sleep(mResetSleepTime);
            mMonsoon.setMonsoonVoltage(0);
            getRunUtil().sleep(mResetSleepTime);
            mMonsoon.setMonsoonCurrent(DEFAULT_CURRENT);
            getRunUtil().sleep(mResetSleepTime);
            mMonsoon.setMonsoonStartCurrent(DEFAULT_START_CURRENT);
            getRunUtil().sleep(mResetSleepTime);
            mMonsoon.setMonsoonVoltage(mResetVoltage);
            getRunUtil().sleep(mResetSleepTime);
        } finally {
            connectUsb();
        }
    }
}
