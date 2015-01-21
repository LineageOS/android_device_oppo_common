package com.cyanogenmod.settings.device;

import com.android.internal.util.cm.ScreenType;

import com.cyanogenmod.settings.device.utils.NodePreferenceActivity;

import android.os.Bundle;

public class TouchscreenGestureSettings extends NodePreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.touchscreen_panel);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If running on a phone, remove padding around the listview
        if (!ScreenType.isTablet(this)) {
            getListView().setPadding(0, 0, 0, 0);
        }
    }

}
