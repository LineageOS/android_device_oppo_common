package com.cyanogenmod.settings.device;

import java.util.UUID;

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
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
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

public class OclickService extends Service implements OnSharedPreferenceChangeListener {

    private static final String TAG = OclickService.class.getSimpleName();
    private static final UUID sTriggerServiceUUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID sTriggerCharacteristicUUIDv1 = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private static final UUID sTriggerCharacteristicUUIDv2 = UUID.fromString("f000ffe1-0451-4000-b000-000000000000");

    private static final UUID sImmediateAlertServiceUUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb"); //0-2
    private static final UUID sImmediateAlertCharacteristicUUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");

    private static final UUID sLinkLossServiceUUID = UUID.fromString("00001803-0000-1000-8000-00805f9b34fb"); // 0-3
    private static final UUID sLinkLossCharacteristicUUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");

    //    private static final UUID sControllCharacteristicUUIDv1 = UUID.fromString("0000aa01-0000-1000-8000-00805f9b34fb");
    //    private static final UUID sControllCharacteristicUUIDv2 = UUID.fromString("f000ffe2-0451-4000-b000-000000000000");

    public static final String CANCEL_ALERT_PHONE = "cancel_alert_phone";

    private BluetoothGatt mBluetoothGatt;
    private Handler mHandler;
    boolean mAlerting;
    private Handler mRssiPoll = new Handler();
    private BluetoothDevice mOClickDevice;
    private AudioManager mAudioManager;

    public static boolean isConnectedToOclick = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendCommand(int command) {
        Log.d(TAG, "sendCommand : " + command);
        Intent i = new Intent(BluetoothInputSettings.PROCESS_COMMAND_ACTION);
        i.putExtra(BluetoothInputSettings.COMMAND_KEY, command);
        sendBroadcast(i);
    }

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, final int newState) {
            Log.d(TAG, "onConnectionStateChange " + status + " " + newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                mBluetoothGatt = gatt;
                isConnectedToOclick = true;
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                isConnectedToOclick = false;
                stopSelf();
            }
            sendCommand(newState);
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered " + status);

            // Register trigger notification (Used for camera/alarm)
            BluetoothGattService service = gatt.getService(sTriggerServiceUUID);
            BluetoothGattCharacteristic trigger = service.getCharacteristic(sTriggerCharacteristicUUIDv1);

            if (trigger == null) {
                trigger = service.getCharacteristic(sTriggerCharacteristicUUIDv2);
            }
            gatt.setCharacteristicNotification(trigger, true);

            toggleRssiListener();

            boolean alert = Constants.isPreferenceEnabled(getBaseContext(), Constants.OCLICK_DISCONNECT_ALERT_KEY, true);
            service = mBluetoothGatt.getService(sLinkLossServiceUUID);
            trigger = service.getCharacteristic(sLinkLossCharacteristicUUID);
            byte[] value = new byte[1];
            value[0] = (byte) (alert ? 2 : 0);
            trigger.setValue(value);
            mBluetoothGatt.writeCharacteristic(trigger);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            if (characteristic.getService().getUuid().equals(OclickService.sLinkLossServiceUUID)) {
                Log.d(TAG, characteristic.getUuid() + " : " + characteristic.getValue()[0]);
                BluetoothGattService service2 = mBluetoothGatt.getService(sImmediateAlertServiceUUID);
                BluetoothGattCharacteristic trigger2 = service2.getCharacteristic(sImmediateAlertCharacteristicUUID);
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
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "Characteristic changed " + characteristic.getUuid());

            if (mTapPending) {
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

                if (mRingtone.isPlaying()) {
                    Log.d(TAG, "Stopping ring alarm");
                    mRingtone.stop();
                    notificationManager.cancel(0);
                    return;
                }

                Log.d(TAG, "Executing ring alarm");

                mAudioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                        mAudioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
                mRingtone.play();
                mHandler.removeCallbacks(mSingleTapRunnable);

                Notification.Builder builder = new Notification.Builder(OclickService.this);
                builder.setSmallIcon(R.drawable.locator_icon);
                builder.setContentTitle("O-Click phone locator");
                builder.setContentText("Locator alert is playing. Tap to dismiss");
                builder.setAutoCancel(true);
                builder.setOngoing(true);

                PendingIntent resultPendingIntent = PendingIntent.getBroadcast(getBaseContext(), 0, new Intent(CANCEL_ALERT_PHONE), 0);
                builder.setContentIntent(resultPendingIntent);
                notificationManager.notify(0, builder.build());

                mTapPending = false;
                return;
            }
            Log.d(TAG, "Setting single tap runnable");
            mTapPending = true;
            mHandler.postDelayed(mSingleTapRunnable, 1500);
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

    boolean mTapPending = false;

    private Runnable mSingleTapRunnable = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            InputManager im = InputManager.getInstance();
            im.injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_CAMERA, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                    InputDevice.SOURCE_KEYBOARD), InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            im.injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP,KeyEvent.KEYCODE_CAMERA,
                    0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD),
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            mTapPending = false;

        }
    };
    private Ringtone mRingtone;

    private void toggleRssiListener() {
        boolean fence = Constants.isPreferenceEnabled(getBaseContext(), Constants.OCLICK_FENCE_KEY, true);
        mRssiPoll.removeCallbacksAndMessages(null);
        if (fence) {
            Log.d(TAG, "Enabling rssi listener");
            mRssiPoll.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothGatt.readRemoteRssi();
                    mRssiPoll.postDelayed(this, 2000);
                }
            }, 100);
        }
    }

    @Override
    public void onCreate() {
        mHandler = new Handler();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(CANCEL_ALERT_PHONE);
        registerReceiver(mReceiver, filter);
        RingtoneManager ringtoneManager = new RingtoneManager(this);
        ringtoneManager.setType(RingtoneManager.TYPE_ALARM);
        int length = ringtoneManager.getCursor().getCount();
        for (int i = 0; i < length; i++) {
            mRingtone = ringtoneManager.getRingtone(i);
            if (mRingtone != null && mRingtone.getTitle(this)
                    .toLowerCase().contains("barium")) {
                break;
            }
        }
        mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
    }

    AlarmCancel mReceiver = new AlarmCancel();
    class AlarmCancel extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(CANCEL_ALERT_PHONE)) {
                if (mRingtone.isPlaying()) {
                    mRingtone.stop();
                }
            }
        }
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (key.equals(Constants.OCLICK_FENCE_KEY)) {
            toggleRssiListener();
        } else if (key.equals(Constants.OCLICK_DISCONNECT_ALERT_KEY)) {
            boolean alert = Constants.isPreferenceEnabled(getBaseContext(), Constants.OCLICK_DISCONNECT_ALERT_KEY, true);
            BluetoothGattService service = mBluetoothGatt.getService(sLinkLossServiceUUID);
            BluetoothGattCharacteristic trigger = service.getCharacteristic(sLinkLossCharacteristicUUID);
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
            mOClickDevice.connectGatt(getBaseContext(), false, mGattCallback);
        }
        return Service.START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service being killed");
        mRssiPoll.removeCallbacksAndMessages(null);
        mBluetoothGatt.disconnect();
        mBluetoothGatt.close();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
        unregisterReceiver(mReceiver);
    }
}
