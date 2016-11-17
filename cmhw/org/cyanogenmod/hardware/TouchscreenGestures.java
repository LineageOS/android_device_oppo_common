/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package org.cyanogenmod.hardware;

import org.cyanogenmod.internal.util.FileUtils;

import cyanogenmod.hardware.TouchscreenGesture;

/**
 * Touchscreen gestures API
 *
 * A device may implement several touchscreen gestures for use while
 * the display is turned off, such as drawing alphabets and shapes.
 * These gestures can be interpreted by userspace to activate certain
 * actions and launch certain apps, such as to skip music tracks,
 * to turn on the flashlight, or to launch the camera app.
 *
 * This *should always* be supported by the hardware directly.
 * A lot of recent touch controllers have a firmware option for this.
 *
 * This API provides support for enumerating the gestures
 * supported by the touchscreen.
 */
public class TouchscreenGestures {

    private static final String[] GESTURE_PATHS = {
        "/proc/touchpanel/circle_enable",
        "/proc/touchpanel/down_swipe_enable",
        "/proc/touchpanel/down_arrow_enable",
        "/proc/touchpanel/left_arrow_enable",
        "/proc/touchpanel/right_arrow_enable",
    };

    /**
     * Whether device supports touchscreen gestures
     *
     * @return boolean Supported devices must return always true
     */
    public static boolean isSupported() {
        for (String path : GESTURE_PATHS) {
            if (!FileUtils.isFileWritable(path) ||
                    !FileUtils.isFileReadable(path)) {
                return false;
            }
        }
        return true;
    }

    /*
     * Get the list of available gestures.
     */
    public static TouchscreenGesture[] getAvailableGestures() {
        TouchscreenGesture[] mGestures = {
            new TouchscreenGesture(TouchscreenGesture.ID_CIRCLE, GESTURE_PATHS[0], 250),
            new TouchscreenGesture(TouchscreenGesture.ID_TWO_FINGERS_DOWNWARDS,
                    GESTURE_PATHS[1], 251),
            new TouchscreenGesture(TouchscreenGesture.ID_LEFTWARDS_ARROW, GESTURE_PATHS[3], 253),
            new TouchscreenGesture(TouchscreenGesture.ID_RIGHTWARDS_ARROW, GESTURE_PATHS[4], 254),
            new TouchscreenGesture(TouchscreenGesture.ID_LETTER_V, GESTURE_PATHS[2], 252),
        };
        return mGestures;
    }

    /**
     * This method returns the current activation status
     * of the queried gesture
     *
     * @param gesture The gesture to be queried
     * @return boolean Must be false if gesture is disabled, not supported
     *         or the operation failed; true in any other case.
     */
    public static boolean isGestureEnabled(final TouchscreenGesture gesture) {
        return "1".equals(FileUtils.readOneLine(GESTURE_PATHS[gesture.id]));
    }

    /**
     * This method allows to set the activation status of a gesture
     *
     * @param gesture The gesture to be activated
     *        state   The new activation status of the gesture
     * @return boolean Must be false if gesture is not supported
     *         or the operation failed; true in any other case.
     */
    public static boolean setGestureEnabled(
            final TouchscreenGesture gesture, final boolean state) {
        final String stateStr = state ? "1" : "0";
        return FileUtils.writeLine(GESTURE_PATHS[gesture.id], stateStr);
    }
}
