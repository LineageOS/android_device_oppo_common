/*
 * Copyright (C) 2015 The CyanogenMod Project
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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

public class BluetoothReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        String action = intent.getAction();
        if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)
                || action.equals(BluetoothDevicePicker.ACTION_DEVICE_SELECTED)) {
            if (!intent.hasExtra(BluetoothDevice.EXTRA_DEVICE)) {
                return;
            }
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null || TextUtils.isEmpty(device.getName())) {
                return;
            }
            String deviceName = device.getName().toLowerCase();
            if (deviceName.contains("oppo b") || deviceName.startsWith("o-click")) {
                Intent i = new Intent(context, OclickService.class);
                i.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
                context.startService(i);
            }
        }
    }
}
