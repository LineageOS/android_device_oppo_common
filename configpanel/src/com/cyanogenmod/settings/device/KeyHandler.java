package com.cyanogenmod.settings.device;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.input.InputManager;
import android.media.IAudioService;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
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

    private final Context mContext;
    private final PowerManager mPowerManager;
    private static final IntentFilter TORCH_STATE_FILTER =
            new IntentFilter(TorchConstants.ACTION_STATE_CHANGED);

    public KeyHandler(Context context) {
        mContext = context;
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
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
            wakeUpDismissKeyguard();
            Intent intent = new Intent(Intent.ACTION_CAMERA_BUTTON, null);
            intent.putExtra(Intent.EXTRA_KEY_EVENT, event);
            mContext.sendOrderedBroadcastAsUser(intent, UserHandle.CURRENT_OR_SELF,
                    null, null, null, 0, null, null);
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
                if (!isTorchActive()) {
                    wakeUpDismissKeyguard();
                    Intent i = TorchConstants.INTENT_LAUNCH_APP;
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(i);
                }
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

    private void triggerVirtualKeypress(final int keyCode) {
        InputManager im = InputManager.getInstance();
        long now = SystemClock.uptimeMillis();

        final KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
        final KeyEvent upEvent = KeyEvent.changeAction(downEvent, KeyEvent.ACTION_UP);

        im.injectInputEvent(downEvent, InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
        im.injectInputEvent(upEvent, InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
    }

    private void wakeUpDismissKeyguard() {
        try {
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
            mPowerManager.wakeUp(SystemClock.uptimeMillis());
        } catch (RemoteException e) {
        }

    }

    private boolean isTorchActive() {
        Intent stateIntent = mContext.registerReceiver(null, TORCH_STATE_FILTER);
        boolean active = stateIntent != null
                && stateIntent.getIntExtra(TorchConstants.EXTRA_CURRENT_STATE, 0) != 0;
        return active;
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
}
