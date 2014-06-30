package com.cyanogenmod.settings.device;

import android.app.ActivityManagerNative;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.IAudioService;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;

import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.util.cm.NavigationRingHelpers;
import com.android.internal.util.cm.TorchConstants;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = KeyHandler.class.getSimpleName();

    // Supported scancodes
    private static final int FLIP_CAMERA_SCANCODE = 249;
    private static final int GESTURE_CIRCLE_SCANCODE = 250;
    private static final int GESTURE_SWIPE_DOWN_SCANCODE = 251;
    private static final int GESTURE_V_SCANCODE = 252;
    private static final int GESTURE_LTR_SCANCODE = 253;
    private static final int GESTURE_GTR_SCANCODE = 254;
    private static final int KEY_DOUBLE_TAP = 255;

    private final Context mContext;
    private final PowerManager mPowerManager;
    private KeyguardManager mKeyguardManager;

    public KeyHandler(Context context) {
        mContext = context;
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }

    private void ensureKeyguardManager() {
        if (mKeyguardManager == null) {
            mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        }
    }

    public boolean handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP && event.getScanCode() != FLIP_CAMERA_SCANCODE) {
            return false;
        }
        boolean consumed = false;
        switch(event.getScanCode()) {
        case FLIP_CAMERA_SCANCODE:
            if (event.getAction() == KeyEvent.ACTION_UP) {
                break;
            }
        case GESTURE_CIRCLE_SCANCODE:
            ensureKeyguardManager();
            String action = null;
            if (mKeyguardManager.isKeyguardSecure() && mKeyguardManager.isKeyguardLocked()) {
                action = MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE;
            } else {
                action = MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA;
            }
            Intent intent = new Intent(action, null);
            startActivitySafely(intent);
            consumed = true;
            break;
        case GESTURE_SWIPE_DOWN_SCANCODE:
            dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            consumed = true;
            break;
        case GESTURE_V_SCANCODE:
            if (NavigationRingHelpers.isTorchAvailable(mContext)) {
                Intent torchIntent = new Intent(TorchConstants.ACTION_TOGGLE_STATE);
                mContext.sendBroadcast(torchIntent);
            }
            consumed = true;
            break;
        case GESTURE_LTR_SCANCODE:
            dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            consumed = true;
            break;
        case GESTURE_GTR_SCANCODE:
            dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.KEYCODE_MEDIA_NEXT);
            consumed = true;
            break;
        case KEY_DOUBLE_TAP:
            if (!mPowerManager.isScreenOn()) {
                mPowerManager.wakeUp(SystemClock.uptimeMillis());
            }
            consumed = true;
            break;
        }
        return consumed;
    }

    private IAudioService getAudioService() {
        IAudioService audioService = IAudioService.Stub.asInterface(
                ServiceManager.checkService(Context.AUDIO_SERVICE));
        if (audioService == null) {
            Log.w(TAG, "Unable to find IAudioService interface.");
        }
        return audioService;
    }

    private void dispatchMediaKeyWithWakeLockToAudioService(int keycode) {
        if (ActivityManagerNative.isSystemReady()) {
            IAudioService audioService = getAudioService();
            if (audioService != null) {
                try {
                    KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(),
                            SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keycode, 0);
                    audioService.dispatchMediaKeyEventUnderWakelock(event);
                    event = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
                    audioService.dispatchMediaKeyEventUnderWakelock(event);
                } catch (RemoteException e) {
                    Log.e(TAG, "dispatchMediaKeyEvent threw exception " + e);
                }
            }
        }
    }

    private void startActivitySafely(Intent intent) {
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mPowerManager.wakeUp(SystemClock.uptimeMillis());
        if (!mKeyguardManager.isKeyguardSecure() || !mKeyguardManager.isKeyguardLocked()) {
            try {
                ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
            } catch (RemoteException e) {
                Log.w(TAG, "can't dismiss keyguard on launch");
            }
        }
        try {
            UserHandle user = new UserHandle(UserHandle.USER_CURRENT);
            mContext.startActivityAsUser(intent, null, user);
        } catch (ActivityNotFoundException e) {
        }
    }
}
