/*
 * Copyright (C) 2015 The CyanogenMod Project
 * Copyright (C) 2017 The LineageOS Project
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

package org.lineageos.settings.device;

import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;
import android.preference.Preference;
import android.service.gesture.IGestureService;

import org.lineageos.settings.device.utils.Constants;
import org.lineageos.settings.device.utils.NodePreferenceActivity;

public class GesturePadSettings extends NodePreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.gesture_panel);

        Preference p = findPreference(Constants.TOUCHPAD_DOUBLETAP_KEY);
        p.setOnPreferenceChangeListener(this);
        p = findPreference(Constants.TOUCHPAD_LONGPRESS_KEY);
        p.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals(Constants.TOUCHPAD_DOUBLETAP_KEY)) {
            IBinder b = ServiceManager.getService("gesture");
            IGestureService sInstance = IGestureService.Stub.asInterface(b);
            Startup.toggleDoubleTap(this, sInstance, (Boolean) newValue);
            return true;
        } else if (preference.getKey().equals(Constants.TOUCHPAD_LONGPRESS_KEY)) {
            IBinder b = ServiceManager.getService("gesture");
            IGestureService sInstance = IGestureService.Stub.asInterface(b);
            Startup.toggleLongPress(this, sInstance, (Boolean) newValue);
            return true;
        }
        return super.onPreferenceChange(preference, newValue);
    }
}
