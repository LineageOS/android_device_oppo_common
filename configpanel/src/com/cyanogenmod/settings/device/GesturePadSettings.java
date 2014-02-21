package com.cyanogenmod.settings.device;

import com.cyanogenmod.settings.device.utils.Constants;
import com.cyanogenmod.settings.device.utils.NodePreferenceActivity;

import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;
import android.preference.Preference;
import android.service.gesture.IGestureService;

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
