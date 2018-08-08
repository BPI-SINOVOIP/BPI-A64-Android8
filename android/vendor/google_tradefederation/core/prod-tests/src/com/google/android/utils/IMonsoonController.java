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

package com.google.android.utils;

import java.io.File;

public interface IMonsoonController {

    /**
     * Connects the monsoon's usb.
     *
     * @return true if successful, false otherwise.
     */
    void connectUsb();

    /**
     * Disconnects the monsoon's usb.
     *
     * @return true if successful, false otherwise.
     */
    void disconnectUsb();

    /** @return The monsoon serial number. */
    String getMonsoonSerialNumber();

    /** @return The monsoon serial port. */
    String getMonsoonSerialPort();

    /**
     * Kills processes allocating the monsoon.
     *
     * @return true if successful, false otherwise.
     */
    boolean freeResources();

    /**
     * Sets the monsoon voltage.
     *
     * @param voltage
     */
    void setMonsoonVoltage(double voltage);

    /** Sets the monsoon start current. */
    void setMonsoonStartCurrent(double current);

    /** Sets the monsoon current. */
    void setMonsoonCurrent(double current);

    /** @return Returns a string describing the monsoon's current state. */
    String dumpMonsoonStatus();

    /** @return true if usbpassthrough is set to 1, false otherwise. */
    boolean isUsbConnected();

    /**
     * Starts measuring current.
     *
     * @param frequency how many samples per second to collect. Values from 1 to 1000.
     */
    void startMeasurement(int frequency);

    /** Stops measuring power. */
    void stopMeasurement();

    /** @return Returns a file with the last collected power samples. */
    File getResultFile();
}
