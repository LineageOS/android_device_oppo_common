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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import com.cyanogenmod.settings.device.utils.Constants;

import java.util.UUID;

public class OclickService extends Service implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = OclickService.class.getSimpleName();
    private static final UUID sTriggerServiceUUID =
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID sTriggerCharacteristicUUIDv1 =
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private static final UUID sTriggerCharacteristicUUIDv2 =
            UUID.fromString("f000ffe1-0451-4000-b000-000000000000");

    private static final UUID sOclick2ServiceUUID =
            UUID.fromString("00002200-0000-1000-8000-00805f9b34fb");
    private static final UUID sOclick2KeyCharacteristicUUID =
            UUID.fromString("00002201-0000-1000-8000-00805f9b34fb");

    private static final UUID sImmediateAlertServiceUUID =
            UUID.fromString("00001802-0000-1000-8000-00805f9b34fb"); //0-2
    private static final UUID sImmediateAlertCharacteristicUUID =
            UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");

    private static final UUID sLinkLossServiceUUID =
            UUID.fromString("00001803-0000-1000-8000-00805f9b34fb"); //0-3
    private static final UUID sLinkLossCharacteristicUUID =
            UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");

    public static final String CANCEL_ALERT_PHONE = "cancel_alert_phone";

    private static final class Oclick2Constants {
        private static final int MSG_CLASS_CALL = 1;
        private static final int MSG_CLASS_MESSAGE = 2;
        private static final int MSG_CLASS_LED = 3;
        private static final int MSG_CLASS_KEY = 5;
        private static final int MSG_CLASS_CONNECTION = 7;
        private static final int MSG_CLASS_LINKLOSE = 8;
        private static final int MSG_CLASS_RSSI = 11;

        /* payload: count (16 bit integer) */
        private static final int MSG_TYPE_CALL_SET_INCOMING = 1;
        private static final int MSG_TYPE_CALL_SET_MISSED = 2;
        private static final int MSG_TYPE_CALL_SET_READ = 3;

        /* payload: count (16 bit integer) */
        private static final int MSG_TYPE_MESSAGE_UNREAD = 1;
        private static final int MSG_TYPE_MESSAGE_READ = 2;

        /* payload: color bitmask (1 byte, white = 1, red = 2, green = 4, blue = 8) */
        private static final int MSG_TYPE_LED_ON = 1;
        private static final int MSG_TYPE_LED_FLASH = 2;
        private static final int MSG_TYPE_LED_OFF = 3;

        /* payload:
         * CONNECTION_INTERVAL_MIN (16 bit integer),
         * CONNECTION_INTERVAL_MAX (16 bit integer),
         * CONNECTION_LATENCY (16 bit integer),
         * SUPERVISION_TIMEOUT (16 bit integer)
         */
        private static final int MSG_TYPE_CONNECTION_GET_PARAMS = 1;
        private static final int MSG_TYPE_CONNECTION_SET_PARAMS = 2;

        /* payload: level (1 byte, off = 0, on = 1) */
        private static final int MSG_TYPE_LINKLOSE_GET_LEVEL = 1;
        private static final int MSG_TYPE_LINKLOSE_SET_LEVEL = 2;

        private static final int MSG_TYPE_RSSI_READ_RATE_GET = 1;
        private static final int MSG_TYPE_RSSI_READ_RATE_SET = 2;
        private static final int MSG_TYPE_RSSI_GET = 3;

        private static final int KEYCODE_MIDDLE = 0x10;
        private static final int KEYCODE_UP = 0x20;
        private static final int KEYCODE_RIGHT = 0x30;
        private static final int KEYCODE_DOWN = 0x40;
        private static final int KEYCODE_LEFT = 0x50;
        private static final int KEYCODE_MASK = 0xf0;

        private static final int KEYTYPE_LONG_RELEASE = 0;
        private static final int KEYTYPE_SHORT = 1;
        private static final int KEYTYPE_DOUBLE = 2;
        private static final int KEYTYPE_LONG_PRESS = 3;
        private static final int KEYTYPE_MASK = 0xf;
    }

    public static boolean sOclickConnected = false;

    private BluetoothGatt mBluetoothGatt;
    private Handler mHandler = new Handler();
    private boolean mAlerting;
    private BluetoothDevice mOClickDevice;
    private AudioManager mAudioManager;
    private boolean mTapPending = false;
    private Ringtone mRingtone;

    private Runnable mSingleTapRunnable = new Runnable() {
        @Override
        public void run() {
            injectKey(KeyEvent.KEYCODE_CAMERA);
            mTapPending = false;
        }
    };
    private Runnable mRssiPollRunnable =  new Runnable() {
        @Override
        public void run() {
            mBluetoothGatt.readRemoteRssi();
            mHandler.postDelayed(this, 2000);
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(CANCEL_ALERT_PHONE)) {
                stopPhoneLocator();
            }
        }
    };

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, final int newState) {
            Log.d(TAG, "onConnectionStateChange " + status + " " + newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                mBluetoothGatt = gatt;
                sOclickConnected = true;
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                sOclickConnected = false;
                stopSelf();
            }
            sendCommand(newState);
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered " + status);

            BluetoothGattService serviceV2 = gatt.getService(sOclick2ServiceUUID);
            BluetoothGattCharacteristic keyCharacteristic = null;
            if (serviceV2 != null) {
                keyCharacteristic = serviceV2.getCharacteristic(sOclick2KeyCharacteristicUUID);
            }

            if (keyCharacteristic != null) {
                // O-Click 2.0 mode
                gatt.setCharacteristicNotification(keyCharacteristic, true);

                // update connection parameters - TODO: use constants
                byte[] params = new byte[] {
                    Oclick2Constants.MSG_CLASS_CONNECTION,
                    Oclick2Constants.MSG_TYPE_CONNECTION_SET_PARAMS,
                    /* CONNECTION_INTERVAL_MIN = 200 */ (byte) 0xc8, 0,
                    /* CONNECTION_INTERVAL_MAX = 400 */ (byte) 0x90, 1,
                    /* CONNECTION_LATENCY = 1 */ 1, 0,
                    /* SUPERVISION_TIMEOUT = 1000 */ (byte) 0xe8, 3
                };
                keyCharacteristic.setValue(params);
                mBluetoothGatt.writeCharacteristic(keyCharacteristic);
            } else {
                // Register trigger notification (Used for camera/alarm)
                BluetoothGattService service = gatt.getService(sTriggerServiceUUID);
                BluetoothGattCharacteristic trigger =
                        service.getCharacteristic(sTriggerCharacteristicUUIDv1);

                if (trigger == null) {
                    trigger = service.getCharacteristic(sTriggerCharacteristicUUIDv2);
                }
                gatt.setCharacteristicNotification(trigger, true);

                toggleRssiListener();

                boolean alert = Constants.isPreferenceEnabled(OclickService.this,
                        Constants.OCLICK_DISCONNECT_ALERT_KEY, true);
                service = mBluetoothGatt.getService(sLinkLossServiceUUID);
                trigger = service.getCharacteristic(sLinkLossCharacteristicUUID);
                byte[] value = new byte[1];
                value[0] = (byte) (alert ? 2 : 0);
                trigger.setValue(value);
                mBluetoothGatt.writeCharacteristic(trigger);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            if (characteristic.getService().getUuid().equals(OclickService.sLinkLossServiceUUID)) {
                Log.d(TAG, characteristic.getUuid() + " : " + characteristic.getValue()[0]);
                BluetoothGattService service2 =
                        mBluetoothGatt.getService(sImmediateAlertServiceUUID);
                BluetoothGattCharacteristic trigger2 =
                        service2.getCharacteristic(sImmediateAlertCharacteristicUUID);
                byte[] values = new byte[1];
                values[0] = (byte) 0;
                trigger2.setValue(values);
                mBluetoothGatt.writeCharacteristic(trigger2);
            }
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onReliableWriteCompleted : " + status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "Characteristic changed " + characteristic.getUuid());

            if (characteristic.getUuid().equals(sOclick2KeyCharacteristicUUID)) {
                byte[] value = characteristic.getValue();
                if (value.length == 3 && value[0] == Oclick2Constants.MSG_CLASS_KEY) {
                    int key = value[2] & Oclick2Constants.KEYCODE_MASK;
                    int action = value[2] & Oclick2Constants.KEYTYPE_MASK;
                    if (key == Oclick2Constants.KEYCODE_MIDDLE) {
                        if (action == Oclick2Constants.KEYTYPE_DOUBLE) {
                            if (mRingtone.isPlaying()) {
                                stopPhoneLocator();
                            } else {
                                startPhoneLocator();
                            }
                        } else if (action == Oclick2Constants.KEYTYPE_SHORT) {
                            injectKey(KeyEvent.KEYCODE_CAMERA);
                        }
                    }
                }
            } else {
                if (mTapPending) {
                    if (mRingtone.isPlaying()) {
                        stopPhoneLocator();
                        return;
                    }

                    mHandler.removeCallbacks(mSingleTapRunnable);
                    mTapPending = false;
                    startPhoneLocator();
                    return;
                }
                Log.d(TAG, "Setting single tap runnable");
                mTapPending = true;
                mHandler.postDelayed(mSingleTapRunnable, 1500);
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, "Rssi value : " + rssi);
            byte[] value = new byte[1];
            BluetoothGattCharacteristic charS = gatt.getService(sImmediateAlertServiceUUID)
                    .getCharacteristic(sImmediateAlertCharacteristicUUID);
            if (rssi < -90 && !mAlerting) {
                value[0] = 2;
                charS.setValue(value);
                mBluetoothGatt.writeCharacteristic(charS);
                mAlerting = true;
            } else if (rssi > -90 && mAlerting) {
                value[0] = 0;
                mAlerting = false;
                charS.setValue(value);
                mBluetoothGatt.writeCharacteristic(charS);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        mHandler = new Handler();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(CANCEL_ALERT_PHONE);
        registerReceiver(mReceiver, filter);

        RingtoneManager ringtoneManager = new RingtoneManager(this);
        ringtoneManager.setType(RingtoneManager.TYPE_ALARM);
        int length = ringtoneManager.getCursor().getCount();
        for (int i = 0; i < length; i++) {
            mRingtone = ringtoneManager.getRingtone(i);
            if (mRingtone != null && mRingtone.getTitle(this).toLowerCase().contains("barium")) {
                break;
            }
        }
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service being killed");
        mHandler.removeCallbacksAndMessages(null);
        mBluetoothGatt.disconnect();
        mBluetoothGatt.close();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (key.equals(Constants.OCLICK_FENCE_KEY)) {
            toggleRssiListener();
        } else if (key.equals(Constants.OCLICK_DISCONNECT_ALERT_KEY)) {
            boolean alert = Constants.isPreferenceEnabled(this,
                    Constants.OCLICK_DISCONNECT_ALERT_KEY, true);
            BluetoothGattService service =
                    mBluetoothGatt.getService(sLinkLossServiceUUID);
            BluetoothGattCharacteristic trigger =
                    service.getCharacteristic(sLinkLossCharacteristicUUID);
            byte[] value = new byte[1];
            value[0] = (byte) (alert ? 2 : 0);
            trigger.setValue(value);
            mBluetoothGatt.writeCharacteristic(trigger);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onstartCommand");
        if (intent != null && mBluetoothGatt == null) {
            mOClickDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (mOClickDevice == null) {
                Log.e(TAG, "No bluetooth device provided");
                stopSelf();
            }
            mOClickDevice.connectGatt(this, false, mGattCallback);
        }
        return Service.START_REDELIVER_INTENT;
    }

    private void startPhoneLocator() {
        Log.d(TAG, "Executing ring alarm");

        // FIXME: this needs to be reverted
        mAudioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                mAudioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        mRingtone.play();

        Notification.Builder builder = new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.locator_icon);
        builder.setContentTitle(getString(R.string.oclick_locator_notification_title));
        builder.setContentText(getString(R.string.oclick_locator_notification_text));
        builder.setAutoCancel(true);
        builder.setOngoing(true);

        PendingIntent resultPendingIntent = PendingIntent.getBroadcast(this,
                0, new Intent(CANCEL_ALERT_PHONE), 0);
        builder.setContentIntent(resultPendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, builder.build());
    }

    private void stopPhoneLocator() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Log.d(TAG, "Stopping ring alarm");
        mRingtone.stop();
        notificationManager.cancel(0);
    }

    private void injectKey(int keyCode) {
        long now = SystemClock.uptimeMillis();
        InputManager im = InputManager.getInstance();
        im.injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode,
                0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD),
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        im.injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode,
                0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD),
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private void toggleRssiListener() {
        boolean fence = Constants.isPreferenceEnabled(this, Constants.OCLICK_FENCE_KEY, true);
        mHandler.removeCallbacks(mRssiPollRunnable);
        if (fence) {
            Log.d(TAG, "Enabling rssi listener");
            mHandler.postDelayed(mRssiPollRunnable, 100);
        }
    }

    private void sendCommand(int command) {
        Log.d(TAG, "sendCommand : " + command);
        Intent i = new Intent(BluetoothInputSettings.PROCESS_COMMAND_ACTION);
        i.putExtra(BluetoothInputSettings.COMMAND_KEY, command);
        sendBroadcast(i);
    }
}
