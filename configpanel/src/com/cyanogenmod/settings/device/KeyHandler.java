package com.cyanogenmod.settings.device;

import android.app.ActivityManagerNative;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.IAudioService;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.util.cm.NavigationRingHelpers;
import com.android.internal.util.cm.TorchConstants;
import com.android.internal.widget.LockPatternUtils;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = KeyHandler.class.getSimpleName();

    // Supported scancodes
    private static final int FLIP_CAMERA_SCANCODE = 249;
    private static final int GESTURE_CIRCLE_SCANCODE = 250;
    private static final int GESTURE_SWIPE_DOWN_SCANCODE = 251;
    private static final int GESTURE_V_SCANCODE = 252;
    private static final int GESTURE_LTR_SCANCODE = 253;
    private static final int GESTURE_GTR_SCANCODE = 254;

    private Intent mPendingIntent;
    private LockPatternUtils mLockPatternUtils;
    private final Context mContext;
    private final PowerManager mPowerManager;

    public KeyHandler(Context context) {
        mContext = context;
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mLockPatternUtils = new LockPatternUtils(context);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(mReceiver, filter);
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            if (TextUtils.equals(action, Intent.ACTION_USER_PRESENT)) {
                if (mPendingIntent != null) {
                    try {
                        mContext.startActivity(mPendingIntent);
                    } catch (ActivityNotFoundException e) {
                    }
                    mPendingIntent = null;
                }
            } else if (TextUtils.equals(action, Intent.ACTION_SCREEN_OFF)) {
                mPendingIntent = null;
            }
        }
    };


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
            Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, null);
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
        if (mLockPatternUtils.isSecure()) {
            mPendingIntent = intent;
        } else {
            try {
                ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
                mContext.startActivity(intent);
            } catch (ActivityNotFoundException e) {
            } catch (RemoteException e) {
            }
        }
    }
}
