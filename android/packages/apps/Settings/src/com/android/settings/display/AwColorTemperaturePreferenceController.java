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
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.widget.SeekBarPreference;
import com.android.settingslib.core.AbstractPreferenceController;

import com.softwinner.AWDisplay;

public class AwColorTemperaturePreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    private static final String KEY_COLOR_TEMPERATURE_SETTING = "color_temperature_setting";
    private static final String KEY_READING_MODE = "reading_mode";
    private static final String KEY_COLOR_TEMPERATURE_SCALE = "color_temperature_scale";
    private static final int COLOR_TEMPERATURE_SCALE_MAX = 100; // 100 percentage
    private SwitchPreference mReadingMode;
    private SeekBarPreference mScale;

    public AwColorTemperaturePreferenceController(Context context) {
        super(context);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        if (isAvailable()) {
            mReadingMode = (SwitchPreference) screen.findPreference(KEY_READING_MODE);
            mReadingMode.setOnPreferenceChangeListener(this);
            mScale = (SeekBarPreference) screen.findPreference(KEY_COLOR_TEMPERATURE_SCALE);
            mScale.setOnPreferenceChangeListener(this);
            mScale.setMax(COLOR_TEMPERATURE_SCALE_MAX);
        } else {
            removePreference(screen, getPreferenceKey());
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_COLOR_TEMPERATURE_SETTING;
    }

    @Override
    public void updateState(Preference preference) {
        if (mReadingMode != null) {
            boolean enabled = AWDisplay.getReadingMode();
            mReadingMode.setChecked(enabled);
        }
        if (mScale != null) {
            int value = AWDisplay.getColorTemperature();
            mScale.setProgress(value);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (mReadingMode == preference) {
            boolean auto = (Boolean) newValue;
            AWDisplay.setReadingMode(auto);
        } else if (mScale == preference) {
            int value = (Integer) newValue;
            AWDisplay.setColorTemperature(value);
        }
        return true;
    }
}
