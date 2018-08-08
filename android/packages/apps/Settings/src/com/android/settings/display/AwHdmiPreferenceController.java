/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.settings.display;

import android.content.Context;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.widget.SeekBarPreference;
import com.android.settingslib.core.AbstractPreferenceController;

import java.io.File;

import com.softwinner.AWDisplay;

public class AwHdmiPreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    private static final String HDMI_STATE = "/sys/class/extcon/hdmi/state";
    private static final String KEY_HDMI_SETTING = "hdmi_setting";
    private static final String KEY_HDMI_OUTPUT_MODE = "hdmi_output_mode";
    private static final String KEY_HDMI_FULLSCREEN = "hdmi_fullscreen";
    private static final String KEY_HDMI_WIDTH_SCALE = "hdmi_width_scale";
    private static final String KEY_HDMI_HEIGHT_SCALE = "hdmi_height_scale";
    private static final int HDMI_SCALE_MIN = 90;
    private static final int HDMI_SCALE_MAX = 100;
    private ListPreference mOutputMode;
    private SwitchPreference mFullscreen;
    private SeekBarPreference mWidthScale;
    private SeekBarPreference mHeightScale;

    public AwHdmiPreferenceController(Context context) {
        super(context);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        if (isAvailable()) {
            mOutputMode = (ListPreference) screen.findPreference(KEY_HDMI_OUTPUT_MODE);
            mOutputMode.setOnPreferenceChangeListener(this);
            mFullscreen = (SwitchPreference) screen.findPreference(KEY_HDMI_FULLSCREEN);
            mFullscreen.setOnPreferenceChangeListener(this);
            mWidthScale = (SeekBarPreference) screen.findPreference(KEY_HDMI_WIDTH_SCALE);
            mWidthScale.setOnPreferenceChangeListener(this);
            mHeightScale = (SeekBarPreference) screen.findPreference(KEY_HDMI_HEIGHT_SCALE);
            mHeightScale.setOnPreferenceChangeListener(this);
            mWidthScale.setMax(HDMI_SCALE_MAX - HDMI_SCALE_MIN);
            mHeightScale.setMax(HDMI_SCALE_MAX - HDMI_SCALE_MIN);
        } else {
            removePreference(screen, getPreferenceKey());
        }
    }

    @Override
    public boolean isAvailable() {
        return new File(HDMI_STATE).exists();
    }

    @Override
    public String getPreferenceKey() {
        return KEY_HDMI_SETTING;
    }

    @Override
    public void updateState(Preference preference) {
        if (mOutputMode != null) {
            int mode = AWDisplay.getHdmiMode();
            mOutputMode.setValue("" + mode);
        }
        if (mFullscreen != null) {
            boolean full = AWDisplay.getHdmiFullscreen();
            mFullscreen.setChecked(full);
        }
        if (mWidthScale != null) {
            int value = AWDisplay.getMarginWidth();
            mWidthScale.setProgress(value - HDMI_SCALE_MIN);
        }
        if (mHeightScale != null) {
            int value = AWDisplay.getMarginHeight();
            mHeightScale.setProgress(value - HDMI_SCALE_MIN);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (mOutputMode == preference) {
            int mode = Integer.parseInt((String) newValue);
            AWDisplay.setHdmiMode(mode);
        } else if (mFullscreen == preference) {
            boolean full = (Boolean) newValue;
            AWDisplay.setHdmiFullscreen(full);
        } else if (mWidthScale == preference) {
            int value = (Integer) newValue;
            AWDisplay.setMarginWidth(value + HDMI_SCALE_MIN);
        } else if (mHeightScale == preference) {
            int value = (Integer) newValue;
            AWDisplay.setMarginHeight(value + HDMI_SCALE_MIN);
        }
        return true;
    }
}
