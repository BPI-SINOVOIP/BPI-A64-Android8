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

public class AwSmartBacklightPreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    private static final String KEY_SMART_BACKLIGHT_SETTING = "smart_backlight_setting";
    private static final String KEY_SMART_BACKLIGHT = "smart_backlight";
    private static final String KEY_SMART_BACKLIGHT_DEMO = "smart_backlight_demo";
    private SwitchPreference mSmartBacklight;
    private SwitchPreference mDemo;

    public AwSmartBacklightPreferenceController(Context context) {
        super(context);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        if (isAvailable()) {
            mSmartBacklight = (SwitchPreference) screen.findPreference(KEY_SMART_BACKLIGHT);
            mSmartBacklight.setOnPreferenceChangeListener(this);
            mDemo = (SwitchPreference) screen.findPreference(KEY_SMART_BACKLIGHT_DEMO);
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
        return KEY_SMART_BACKLIGHT_SETTING;
    }

    @Override
    public void updateState(Preference preference) {
        boolean enabled = AWDisplay.getSmartBacklight();
        boolean demo = AWDisplay.getSmartBacklightDemo();
        if (mSmartBacklight != null)
            mSmartBacklight.setChecked(enabled);
        if (mDemo != null) {
            mDemo.setEnabled(enabled);
            mDemo.setChecked(demo);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean auto = (Boolean) newValue;
        if (mSmartBacklight == preference) {
            AWDisplay.setSmartBacklight(auto);
            if(mDemo != null) mDemo.setEnabled(auto);
        } else if (mDemo == preference) {
            AWDisplay.setSmartBacklightDemo(auto);
        }
        return true;
    }
}
