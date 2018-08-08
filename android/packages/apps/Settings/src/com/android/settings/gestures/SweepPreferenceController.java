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
package com.android.settings.gestures;

import android.content.Context;
import android.provider.Settings;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

public class SweepPreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    private static final String KEY_SWEEP_SCREENSHOT = "gesture_sweep_screenshot_summary";
    private static final String KEY_SWEEP_SCREENRECORD = "gesture_sweep_screenrecord_summary";
    private final String mKey;
    private SwitchPreference mScreenshot;
    private SwitchPreference mScreenrecord;

    public SweepPreferenceController(Context context, String key) {
        super(context);
        mKey = key;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        if (isAvailable()) {
            mScreenshot = (SwitchPreference) screen.findPreference(KEY_SWEEP_SCREENSHOT);
            mScreenshot.setOnPreferenceChangeListener(this);
            mScreenrecord = (SwitchPreference) screen.findPreference(KEY_SWEEP_SCREENRECORD);
            mScreenrecord.setOnPreferenceChangeListener(this);
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
        return mKey;
    }

    @Override
    public void updateState(Preference preference) {
        if (mScreenshot != null) {
            mScreenshot.setChecked(Settings.System.getInt(mContext.getContentResolver(), Settings.System.GESTURE_SCREENSHOT_ENABLE, 0) != 0);
        }
        if (mScreenrecord != null) {
            mScreenrecord.setChecked(Settings.System.getInt(mContext.getContentResolver(), Settings.System.GESTURE_SCREENRECORD_ENABLE, 0) != 0);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (mScreenshot == preference) {
            Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.GESTURE_SCREENSHOT_ENABLE,
                (boolean) newValue ? 1 : 0);
        } else if (mScreenrecord == preference) {
            Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.GESTURE_SCREENRECORD_ENABLE,
                (boolean) newValue ? 1 : 0);
        }
        return true;
    }
}
