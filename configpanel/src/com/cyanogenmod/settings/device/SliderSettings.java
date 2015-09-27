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
import android.preference.ListPreference;
import android.preference.Preference;

import com.cyanogenmod.settings.device.utils.Constants;
import com.cyanogenmod.settings.device.utils.FileUtils;
import com.cyanogenmod.settings.device.utils.NodePreferenceActivity;

import org.cyanogenmod.internal.util.ScreenType;

public class SliderSettings extends NodePreferenceActivity {

    private ListPreference mSliderTop;
    private ListPreference mSliderMiddle;
    private ListPreference mSliderBottom;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.slider_panel);

        mSliderTop = (ListPreference) findPreference("keycode_top_position");
        mSliderTop.setOnPreferenceChangeListener(this);

        mSliderMiddle = (ListPreference) findPreference("keycode_middle_position");
        mSliderMiddle.setOnPreferenceChangeListener(this);

        mSliderBottom = (ListPreference) findPreference("keycode_bottom_position");
        mSliderBottom.setOnPreferenceChangeListener(this);
    }

    private void setSummary(ListPreference preference, String file) {
        String keyCode;
        if ((keyCode = FileUtils.readOneLine(file)) != null) {
            preference.setValue(keyCode);
            preference.setSummary(preference.getEntry());
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String file;
        if (preference == mSliderTop) {
            file = Constants.KEYCODE_SLIDER_TOP;
        } else if (preference == mSliderMiddle) {
            file = Constants.KEYCODE_SLIDER_MIDDLE;
        } else if (preference == mSliderBottom) {
            file = Constants.KEYCODE_SLIDER_BOTTOM;
        } else {
            return false;
        }

        FileUtils.writeLine(file, (String) newValue);
        setSummary((ListPreference) preference, file);

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If running on a phone, remove padding around the listview
        if (!ScreenType.isTablet(this)) {
            getListView().setPadding(0, 0, 0, 0);
        }

        setSummary(mSliderTop, Constants.KEYCODE_SLIDER_TOP);
        setSummary(mSliderMiddle, Constants.KEYCODE_SLIDER_MIDDLE);
        setSummary(mSliderBottom, Constants.KEYCODE_SLIDER_BOTTOM);
    }
}
