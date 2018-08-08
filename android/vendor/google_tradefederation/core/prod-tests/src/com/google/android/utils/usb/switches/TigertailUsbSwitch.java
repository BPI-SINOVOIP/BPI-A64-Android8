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

package com.google.android.utils.usb.switches;

import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

public class TigertailUsbSwitch implements IUsbSwitch {

    private static final int COMMAND_TIMEOUT = 3000; // 3 seconds
    private final String mTigertoolPath;
    private final String mSerialNumber;

    public TigertailUsbSwitch(String tigertoolPath, String serialNumber) {
        mTigertoolPath = tigertoolPath;
        mSerialNumber = serialNumber;
    }

    private IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    private void exec(String... cmd) {
        String[] commandPrefix = new String[] {mTigertoolPath, "-s", mSerialNumber};
        String[] fullCmd = concatCommands(commandPrefix, cmd);
        getRunUtil().runTimedCmd(COMMAND_TIMEOUT, fullCmd);
    }

    private String[] concatCommands(String[] prefix, String[] suffix) {
        String[] concatenated = new String[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, concatenated, 0, prefix.length);
        System.arraycopy(suffix, 0, concatenated, prefix.length, suffix.length);
        return concatenated;
    }

    @Override
    public void connectUsb() {
        exec("-m", "A");
    }

    @Override
    public void disconnectUsb() {
        exec("-m", "B");
    }

    @Override
    public void powerCycle() {
        exec("--reboot");
    }

    @Override
    public void freeResources() {
        exec("--reboot");
    }
}
