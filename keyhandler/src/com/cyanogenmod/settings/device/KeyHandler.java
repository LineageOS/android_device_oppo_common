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

import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraAccessException;
import android.media.session.MediaSessionLegacyHelper;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import cyanogenmod.providers.CMSettings;

import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.util.ArrayUtils;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = KeyHandler.class.getSimpleName();
    private static final int GESTURE_REQUEST = 1;

    private static final String KEY_GESTURE_HAPTIC_FEEDBACK =
            "touchscreen_gesture_haptic_feedback";

    // Supported scancodes
    private static final int FLIP_CAMERA_SCANCODE = 249;
    private static final int GESTURE_CIRCLE_SCANCODE = 250;
    private static final int GESTURE_SWIPE_DOWN_SCANCODE = 251;
    private static final int GESTURE_V_SCANCODE = 252;
    private static final int GESTURE_LTR_SCANCODE = 253;
    private static final int GESTURE_GTR_SCANCODE = 254;
    private static final int MODE_TOTAL_SILENCE = 600;
    private static final int MODE_ALARMS_ONLY = 601;
    private static final int MODE_PRIORITY_ONLY = 602;
    private static final int MODE_NONE = 603;

    private static final int GESTURE_WAKELOCK_DURATION = 3000;

    private static final int[] sSupportedGestures = new int[] {
        FLIP_CAMERA_SCANCODE,
        GESTURE_CIRCLE_SCANCODE,
        GESTURE_SWIPE_DOWN_SCANCODE,
        GESTURE_V_SCANCODE,
        GESTURE_LTR_SCANCODE,
        GESTURE_GTR_SCANCODE,
        MODE_TOTAL_SILENCE,
        MODE_ALARMS_ONLY,
        MODE_PRIORITY_ONLY,
        MODE_NONE
    };

    private final Context mContext;
    private final PowerManager mPowerManager;
    private NotificationManager mNotificationManager;
    private EventHandler mEventHandler;
    private SensorManager mSensorManager;
    private CameraManager mCameraManager;
    private String mRearCameraId;
    private boolean mTorchEnabled;
    private Sensor mProximitySensor;
    private Vibrator mVibrator;
    WakeLock mProximityWakeLock;
    WakeLock mGestureWakeLock;
    private int mProximityTimeOut;
    private boolean mProximityWakeSupported;
    private int mSliderScanCode;

    public KeyHandler(Context context) {
        mContext = context;
        mNotificationManager
                = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mEventHandler = new EventHandler();
        mGestureWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "GestureWakeLock");

        final Resources resources = mContext.getResources();
        mProximityTimeOut = resources.getInteger(
                org.cyanogenmod.platform.internal.R.integer.config_proximityCheckTimeout);
        mProximityWakeSupported = resources.getBoolean(
                org.cyanogenmod.platform.internal.R.bool.config_proximityCheckOnWake);

        if (mProximityWakeSupported) {
            mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            mProximityWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "ProximityWakeLock");
        }

        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            mVibrator = null;
        }

        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        mCameraManager.registerTorchCallback(new MyTorchCallback(), mEventHandler);
    }

    private class MyTorchCallback extends CameraManager.TorchCallback {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (!cameraId.equals(mRearCameraId))
                return;
            mTorchEnabled = enabled;
        }

        @Override
        public void onTorchModeUnavailable(String cameraId) {
            if (!cameraId.equals(mRearCameraId))
                return;
            mTorchEnabled = false;
        }
    }

    private String getRearCameraId() {
        if (mRearCameraId == null) {
            try {
                for (final String cameraId : mCameraManager.getCameraIdList()) {
                    CameraCharacteristics characteristics =
                            mCameraManager.getCameraCharacteristics(cameraId);
                    int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (cOrientation == CameraCharacteristics.LENS_FACING_BACK) {
                        mRearCameraId = cameraId;
                        break;
                    }
                }
            } catch (CameraAccessException e) {
                // Ignore
            }
        }
        return mRearCameraId;
    }

    private class EventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.arg1) {
            case FLIP_CAMERA_SCANCODE:
            case GESTURE_CIRCLE_SCANCODE:
                if (msg.obj != null && msg.obj instanceof DeviceHandlerCallback) {
                    ((DeviceHandlerCallback) msg.obj).onScreenCameraGesture();
                }
                break;
            case GESTURE_SWIPE_DOWN_SCANCODE:
                dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                doHapticFeedback();
                break;
            case GESTURE_V_SCANCODE: {
                String rearCameraId = getRearCameraId();
                if (rearCameraId != null) {
                    mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
                    try {
                        mCameraManager.setTorchMode(rearCameraId, !mTorchEnabled);
                        mTorchEnabled = !mTorchEnabled;
                    } catch (CameraAccessException e) {
                        // Ignore
                    }
                    doHapticFeedback();
                }
                break;
            }
            case GESTURE_LTR_SCANCODE:
                dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                doHapticFeedback();
                break;
            case GESTURE_GTR_SCANCODE:
                dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_NEXT);
                doHapticFeedback();
                break;
            }

            // EventHandler doesn't hand over scancode correctly when moving the slider too fast
            switch (mSliderScanCode) {
                case MODE_TOTAL_SILENCE:
                    setNotification(Settings.Global.ZEN_MODE_NO_INTERRUPTIONS,
                                    "total_silence");
                    break;
                case MODE_ALARMS_ONLY:
                    setNotification(Settings.Global.ZEN_MODE_ALARMS,
                                    "alarms_only");
                    break;
                case MODE_PRIORITY_ONLY:
                    setNotification(Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS,
                                    "priority_only");
                    break;
                case MODE_NONE:
                    setNotification(Settings.Global.ZEN_MODE_OFF,
                                    "none");
                    break;
            }
        }
    }

    private void setNotification(int mode, String stringid) {
        String message;
        mNotificationManager.setZenMode(mode, null, TAG);
        if ((message = getString(stringid)) != null) {
            int padding = (int) mContext.getResources().getDisplayMetrics().density * 16;
            LinearLayout linearLayout = new LinearLayout(mContext);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout.setPadding(padding, padding, padding, padding);
            linearLayout.setBackgroundColor(Color.parseColor("#ff263238"));
            linearLayout.setGravity(Gravity.CENTER);

            TextView textView = new TextView(mContext);
            textView.setTextColor(Color.WHITE);
            textView.setText(message);
            linearLayout.addView(textView);

            Toast toast = new Toast(mContext);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.setView(linearLayout);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.show();
        }
        if (mVibrator != null) mVibrator.vibrate(50);
    }

    @Override
    public boolean handleKeyEvent(KeyEvent event) {
        return handleKeyEvent(event, null);
    }

    @Override
    public boolean handleKeyEvent(KeyEvent event, DeviceHandlerCallback callback) {
        boolean isKeySupported = ArrayUtils.contains(sSupportedGestures, event.getScanCode());
        if (!isKeySupported) {
            return false;
        }

        // We only want ACTION_UP event, except FLIP_CAMERA_SCANCODE
        if (event.getScanCode() == FLIP_CAMERA_SCANCODE) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return true;
            }
        } else if (event.getAction() != KeyEvent.ACTION_UP) {
            return true;
        }

        if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
            mSliderScanCode = event.getScanCode();
            Message msg = getMessageForKeyEvent(event.getScanCode(), callback);
            boolean defaultProximity = mContext.getResources().getBoolean(
                org.cyanogenmod.platform.internal.R.bool.config_proximityCheckOnWakeEnabledByDefault);
            boolean proximityWakeCheckEnabled = CMSettings.System.getInt(mContext.getContentResolver(),
                    CMSettings.System.PROXIMITY_ON_WAKE, defaultProximity ? 1 : 0) == 1;
            if (event.getScanCode() < MODE_TOTAL_SILENCE && mProximityWakeSupported
                        && proximityWakeCheckEnabled && mProximitySensor != null) {
                mEventHandler.sendMessageDelayed(msg, mProximityTimeOut);
                processEvent(event.getScanCode(), callback);
            } else {
                mEventHandler.sendMessage(msg);
            }
        }
        return true;
    }

    private Message getMessageForKeyEvent(int scancode, DeviceHandlerCallback callback) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST, scancode, 0, callback);
        return msg;
    }

    private void processEvent(final int scancode, final DeviceHandlerCallback callback) {
        mProximityWakeLock.acquire();
        mSensorManager.registerListener(new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                mProximityWakeLock.release();
                mSensorManager.unregisterListener(this);
                if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
                    // The sensor took to long, ignoring.
                    return;
                }
                mEventHandler.removeMessages(GESTURE_REQUEST);
                if (event.values[0] == mProximitySensor.getMaximumRange()) {
                    Message msg = getMessageForKeyEvent(scancode, callback);
                    mEventHandler.sendMessage(msg);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        }, mProximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    private void dispatchMediaKeyWithWakeLockToMediaSession(int keycode) {
        MediaSessionLegacyHelper helper = MediaSessionLegacyHelper.getHelper(mContext);
        if (helper != null) {
            KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keycode, 0);
            helper.sendMediaButtonEvent(event, true);
            event = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
            helper.sendMediaButtonEvent(event, true);
        } else {
            Log.w(TAG, "Unable to send media key event");
        }
    }

    private void doHapticFeedback() {
        if (mVibrator == null) {
            return;
        }
        boolean enabled = Settings.System.getInt(mContext.getContentResolver(),
                KEY_GESTURE_HAPTIC_FEEDBACK, 1) != 0;
        if (enabled) {
            mVibrator.vibrate(50);
        }
    }

    private String getString(String resourceName) {
        try {
            Resources res = mContext.getPackageManager()
                    .getResourcesForApplication("com.cyanogenmod.settings.device");
            if (res == null)
                Log.e(TAG, "res is null");
            int resId = res.getIdentifier(resourceName, "string", "com.cyanogenmod.settings.device");
            String resValue = res.getString(resId);
            Log.v(TAG, "resourceName = " + resourceName + " resourceId = " + resId
                  + "resourceValue = " + resValue);
            return resValue;
        } catch (NameNotFoundException | NotFoundException e) {
            return null;
        }
    }
}
