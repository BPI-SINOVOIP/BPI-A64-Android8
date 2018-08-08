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

package com.google.android.power.tests;

import com.google.android.utils.MonsoonController.InteractionMode;
import com.google.android.utils.usb.switches.DatamationUsbSwitch;
import com.google.android.utils.usb.switches.IUsbSwitch;
import com.google.android.utils.usb.switches.MonsoonUsbSwitch;
import com.google.android.utils.usb.switches.MultiUsbSwitch;
import com.google.android.utils.usb.switches.NcdUsbSwitch;
import com.google.android.utils.usb.switches.TigertailUsbSwitch;

import com.android.tradefed.log.LogUtil.CLog;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

/* Given a power test command, finds the respective usb switch to be reset */
public class PowerTestUsbSwitchProvider {

    private final String mTigertoolPath;
    private final float mMonsoonVoltage;
    private final String mMonsoonLibPath;
    private final InteractionMode mInteractionMode;
    private final UseTigertailOption mUseTigertail;

    private final String mDatamationPort;
    private final String mNcdPort;
    private final String mMonsoonSerial;
    private final String mTigertailSerial;

    private IUsbSwitch mUsbSwitch;

    public PowerTestUsbSwitchProvider(
            String monsoonLibPath,
            String monsoonSerial,
            float monsoonVoltage,
            String tigertoolLibPath,
            String tigertailSerial,
            String datamationPort,
            String ncdPort,
            InteractionMode interactionMode,
            UseTigertailOption useTigertail) {

        // General options
        mInteractionMode = interactionMode;

        // Datamation options
        // TODO(htellez): parametrize datamation lib path in a similar fashion to monsoon's.
        mDatamationPort = datamationPort;

        // Monsoon options
        mMonsoonSerial = monsoonSerial;
        mMonsoonLibPath = monsoonLibPath;
        mMonsoonVoltage = monsoonVoltage;

        // Ncd options
        // TODO(htellez): parametrize ncd lib path in a similar fashion to monsoon's.
        mNcdPort = ncdPort;

        // Tigertail options
        mUseTigertail = useTigertail;
        mTigertoolPath = tigertoolLibPath;
        mTigertailSerial = tigertailSerial;
    }

    public enum UseTigertailOption {
        USE_TIGERTAIL,
        DO_NOT_USE_TIGERTAIL,
        USE_IF_AVAILABLE
    }

    private static final long RESET_SLEEP_TIME = 20 * 1000; // 20 seconds

    public static class Builder {
        private String mDeviceSerial;
        private String mDatamationPort;
        private String mMonsoonLibPath;
        private String mMonsoonSerial;
        private float mMonsoonVoltage = 4.2f;
        private String mTigertoolLibPath;
        private String mTigertailSerial;
        private UseTigertailOption mUseTigertail = UseTigertailOption.DO_NOT_USE_TIGERTAIL;
        private String mNcdPort;
        private InputStreamReader mLabSetupInputStreamReader;
        private InteractionMode mMonsoonInteractionMode = InteractionMode.USE_SERIAL_NUMBER;
        private LabSetupInfoExtractor mInfoExtractor;
        private boolean mBuilt;

        private LabSetupInfoExtractor getInfoExtractor() {
            if (mInfoExtractor != null) {
                return mInfoExtractor;
            }

            if (mLabSetupInputStreamReader == null) {
                return null;
            }

            mInfoExtractor = new LabSetupInfoExtractor(mLabSetupInputStreamReader);
            return mInfoExtractor;
        }

        private Builder() {}

        public Builder deviceSerial(String serial) {
            mDeviceSerial = serial;
            return this;
        }

        public Builder monsoonSerial(String serial) {
            mMonsoonSerial = serial;
            return this;
        }

        public Builder monsoonVoltage(float voltage) {
            mMonsoonVoltage = voltage;
            return this;
        }

        public Builder monsoonLibPath(String path) {
            mMonsoonLibPath = path;
            return this;
        }

        public Builder useTigertail(UseTigertailOption option) {
            mUseTigertail = option;
            return this;
        }

        public Builder tigertailSerial(String serial) {
            mTigertailSerial = serial;
            return this;
        }

        public Builder tigertoolPath(String path) {
            mTigertoolLibPath = path;
            return this;
        }

        public Builder datamationPort(String port) {
            mDatamationPort = port;
            return this;
        }

        public Builder ncdPort(String port) {
            mNcdPort = port;
            return this;
        }

        public Builder monsoonInteractionMode(InteractionMode mode) {
            mMonsoonInteractionMode = mode;
            return this;
        }

        public Builder labSetupStreamReader(InputStreamReader reader) {
            mLabSetupInputStreamReader = reader;
            return this;
        }

        public Builder labSetupMapFilePath(String labSetupMapFilePath)
                throws FileNotFoundException {

            if (labSetupMapFilePath == null) {
                return this;
            }

            mLabSetupInputStreamReader =
                    new InputStreamReader(new FileInputStream(labSetupMapFilePath));
            return this;
        }

        public PowerTestUsbSwitchProvider build() {
            if (mBuilt) {
                throw new IllegalStateException("Build can be used only once.");
            }
            mBuilt = true;

            // If serials are not passed, extract them from the lab setup.
            if (mDeviceSerial != null && mLabSetupInputStreamReader != null) {
                if (mDatamationPort == null) {
                    mDatamationPort = getInfoExtractor().extractDatamationPort(mDeviceSerial);
                }

                if (mMonsoonSerial == null) {
                    mMonsoonSerial = getInfoExtractor().extractMonsoonSerialNo(mDeviceSerial);
                }

                if (mNcdPort == null) {
                    mNcdPort = getInfoExtractor().extractNcdPort(mDeviceSerial);
                }

                if (mTigertailSerial == null) {
                    mTigertailSerial = getInfoExtractor().extractTigertailSerialNo(mDeviceSerial);
                }
            }

            return new PowerTestUsbSwitchProvider(
                    mMonsoonLibPath,
                    mMonsoonSerial,
                    mMonsoonVoltage,
                    mTigertoolLibPath,
                    mTigertailSerial,
                    mDatamationPort,
                    mNcdPort,
                    mMonsoonInteractionMode,
                    mUseTigertail);
        }
    }

    public static Builder Builder() {
        return new Builder();
    }

    public IUsbSwitch getUsbSwitch() {
        if (mUsbSwitch != null) {
            return mUsbSwitch;
        }

        MultiUsbSwitch multiSwitch = new MultiUsbSwitch();

        // Tigertail
        TigertailUsbSwitch tigertailSwitch = createTigertailSwitch();
        if (tigertailSwitch != null) {
            multiSwitch.addSwitch(tigertailSwitch);
        }

        // Monsoon
        MonsoonUsbSwitch monsoonSwitch = createMonsoonSwitch();
        if (monsoonSwitch != null) {
            multiSwitch.addSwitch(monsoonSwitch);
        }

        // NCD
        if (mNcdPort != null) {
            CLog.i("Found ncd port: %s", mNcdPort);
            multiSwitch.addSwitch(new NcdUsbSwitch(mNcdPort));
        }

        // Datamation
        if (mDatamationPort != null) {
            CLog.i("Found datamation port: %s", mDatamationPort);
            multiSwitch.addSwitch(new DatamationUsbSwitch(mDatamationPort));
        }

        if (multiSwitch.getSwitchesList().size() == 0) {
            CLog.i("No usb switches found.");
        }

        mUsbSwitch = multiSwitch;
        return multiSwitch;
    }

    private MonsoonUsbSwitch createMonsoonSwitch() {
        if (mMonsoonSerial != null) {
            CLog.i("Found monsoon: %s", mMonsoonSerial);
            MonsoonUsbSwitch monsoonSwitch =
                    new MonsoonUsbSwitch(mMonsoonLibPath, mMonsoonSerial, mInteractionMode);
            monsoonSwitch.setResetVoltage(mMonsoonVoltage);
            monsoonSwitch.setResetSleepTime(RESET_SLEEP_TIME);
            return monsoonSwitch;
        }

        return null;
    }

    private TigertailUsbSwitch createTigertailSwitch() {
        switch (mUseTigertail) {
            case USE_TIGERTAIL:
                if (mTigertailSerial == null) {
                    String message =
                            "--use-tigertail was true but couldn't find the corresponding "
                                    + "tigertail serial.";
                    CLog.e(message);
                    throw new IllegalArgumentException(message);
                }

                CLog.i("Found tigertail: %s", mTigertailSerial);
                return new TigertailUsbSwitch(mTigertoolPath, mTigertailSerial);

            case USE_IF_AVAILABLE:
                if (mTigertailSerial != null) {
                    CLog.i("Found tigertail: %s", mTigertailSerial);
                    return new TigertailUsbSwitch(mTigertoolPath, mTigertailSerial);
                }

                CLog.i("tigertail serial not available, skipping tigertail usb switch");
                return null;

            default:
                return null;
        }
    }
}
