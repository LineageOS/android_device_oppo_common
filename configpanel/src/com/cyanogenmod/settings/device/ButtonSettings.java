/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.cyanogenmod.settings.device;

import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.SwitchPreference;

import com.cyanogenmod.settings.device.utils.NodePreferenceActivity;

import org.cyanogenmod.internal.util.ScreenType;

public class ButtonSettings extends NodePreferenceActivity {
    private static final String KEY_IGNORE_AUTO = "notification_slider_ignore_auto";
    private static final String PROP_IGNORE_AUTO = "persist.op.slider_ignore_auto";

    private SwitchPreference mIgnoreAuto;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.button_panel);

        mIgnoreAuto = (SwitchPreference) findPreference(KEY_IGNORE_AUTO);
        mIgnoreAuto.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();
        if (KEY_IGNORE_AUTO.equals(key)) {
            final boolean value = (Boolean) newValue;
            SystemProperties.set(PROP_IGNORE_AUTO, value ? "true" : "false");
            return true;
        }

        return super.onPreferenceChange(preference, newValue);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mIgnoreAuto.setChecked(SystemProperties.get(PROP_IGNORE_AUTO).equals("true"));
    }
}
