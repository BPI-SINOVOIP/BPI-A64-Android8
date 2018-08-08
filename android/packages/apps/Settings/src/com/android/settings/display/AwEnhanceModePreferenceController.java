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
import com.android.settingslib.core.AbstractPreferenceController;

import com.softwinner.AWDisplay;

public class AwEnhanceModePreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    private static final String KEY_ENHANCE_MODE_SETTING = "enhance_mode_setting";
    private static final String KEY_ENHANCE_MODE = "enhance_mode";
    private static final String KEY_ENHANCE_MODE_DEMO = "enhance_mode_demo";
    private SwitchPreference mEnhanceMode;
    private SwitchPreference mDemo;

    public AwEnhanceModePreferenceController(Context context) {
        super(context);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        if (isAvailable()) {
            mEnhanceMode = (SwitchPreference) screen.findPreference(KEY_ENHANCE_MODE);
            mEnhanceMode.setOnPreferenceChangeListener(this);
            mDemo = (SwitchPreference) screen.findPreference(KEY_ENHANCE_MODE_DEMO);
            mDemo.setOnPreferenceChangeListener(this);
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
        return KEY_ENHANCE_MODE_SETTING;
    }

    @Override
    public void updateState(Preference preference) {
        boolean enabled = AWDisplay.getColorEnhance();
        boolean demo = AWDisplay.getColorEnhanceDemo();
        if (mEnhanceMode != null)
            mEnhanceMode.setChecked(enabled);
        if (mDemo != null) {
            mDemo.setEnabled(enabled);
            mDemo.setChecked(demo);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean auto = (Boolean) newValue;
        if (mEnhanceMode == preference) {
            AWDisplay.setColorEnhance(auto);
            if(mDemo != null) mDemo.setEnabled(auto);
        } else if (mDemo == preference) {
            AWDisplay.setColorEnhanceDemo(auto);
        }
        return true;
    }
}
