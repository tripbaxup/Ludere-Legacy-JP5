package com.ludere.legacy.input;

import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.ludere.legacy.overlay.TouchOverlayView;

/**
 * InputManager
 *
 * Aggregates input from three sources:
 *   1. Touch overlay (virtual buttons via TouchOverlayView)
 *   2. Physical gamepad (connected via USB/Bluetooth)
 *   3. Physical keyboard
 *
 * Exposes pollInput() and getInputState() for the libretro input callbacks.
 *
 * Libretro device types:
 *   RETRO_DEVICE_JOYPAD   = 1
 *   RETRO_DEVICE_ANALOG   = 5
 *   RETRO_DEVICE_POINTER  = 6
 *
 * API 17 compatible. No GamepadManager (API 16+). Uses InputDevice directly.
 */
public class InputManager {

    private static final String TAG = "InputManager";

    // Libretro JOYPAD button IDs
    public static final int RETRO_DEVICE_ID_JOYPAD_B      = 0;
    public static final int RETRO_DEVICE_ID_JOYPAD_Y      = 1;
    public static final int RETRO_DEVICE_ID_JOYPAD_SELECT = 2;
    public static final int RETRO_DEVICE_ID_JOYPAD_START  = 3;
    public static final int RETRO_DEVICE_ID_JOYPAD_UP     = 4;
    public static final int RETRO_DEVICE_ID_JOYPAD_DOWN   = 5;
    public static final int RETRO_DEVICE_ID_JOYPAD_LEFT   = 6;
    public static final int RETRO_DEVICE_ID_JOYPAD_RIGHT  = 7;
    public static final int RETRO_DEVICE_ID_JOYPAD_A      = 8;
    public static final int RETRO_DEVICE_ID_JOYPAD_X      = 9;
    public static final int RETRO_DEVICE_ID_JOYPAD_L      = 10;
    public static final int RETRO_DEVICE_ID_JOYPAD_R      = 11;
    public static final int RETRO_DEVICE_ID_JOYPAD_L2     = 12;
    public static final int RETRO_DEVICE_ID_JOYPAD_R2     = 13;
    public static final int RETRO_DEVICE_ID_JOYPAD_L3     = 14;
    public static final int RETRO_DEVICE_ID_JOYPAD_R3     = 15;

    // Libretro analog stick IDs
    public static final int RETRO_DEVICE_INDEX_ANALOG_LEFT  = 0;
    public static final int RETRO_DEVICE_INDEX_ANALOG_RIGHT = 1;
    public static final int RETRO_DEVICE_ID_ANALOG_X        = 0;
    public static final int RETRO_DEVICE_ID_ANALOG_Y        = 1;

    private static final int BUTTON_COUNT = 16;

    // Button state, split by source so releasing one input doesn't get
    // masked by another still being held (and so overlay presses don't
    // latch forever -- see getInputState()).
    private final boolean[] mPhysicalButtonState = new boolean[BUTTON_COUNT];
    private final boolean[] mOverlayButtonState  = new boolean[BUTTON_COUNT];

    // Analog axes: values in [-0x7fff, 0x7fff]
    private int mLeftAxisX  = 0;
    private int mLeftAxisY  = 0;
    private int mRightAxisX = 0;
    private int mRightAxisY = 0;

    private final TouchOverlayView mOverlay;

    public InputManager(TouchOverlayView overlay) {
        mOverlay = overlay;
    }

    // ─── Libretro callbacks ────────────────────────────────────────────────────

    /** Called each frame before input_state queries. */
    public void pollInput() {
        // Overlay state is read fresh each frame (not merged with OR),
        // so releasing an overlay button actually releases it.
        if (mOverlay != null) {
            boolean[] overlayState = mOverlay.getButtonState();
            for (int i = 0; i < BUTTON_COUNT; i++) {
                mOverlayButtonState[i] = i < overlayState.length && overlayState[i];
            }
            // Overlay analog
            int[] analog = mOverlay.getAnalogState();
            if (analog != null) {
                mLeftAxisX = analog[0];
                mLeftAxisY = analog[1];
            }
        }
    }

    /**
     * Returns the state of a specific input.
     * @return 1 if pressed/active, 0 otherwise (for JOYPAD).
     *         Axis value for ANALOG.
     */
    public int getInputState(int port, int device, int index, int id) {
        if (port != 0) return 0;
        switch (device) {
            case 1: // RETRO_DEVICE_JOYPAD
                if (id >= 0 && id < BUTTON_COUNT) {
                    return (mPhysicalButtonState[id] || mOverlayButtonState[id]) ? 1 : 0;
                }
                return 0;
            case 5: // RETRO_DEVICE_ANALOG
                if (index == RETRO_DEVICE_INDEX_ANALOG_LEFT) {
                    return (id == RETRO_DEVICE_ID_ANALOG_X) ? mLeftAxisX : mLeftAxisY;
                } else if (index == RETRO_DEVICE_INDEX_ANALOG_RIGHT) {
                    return (id == RETRO_DEVICE_ID_ANALOG_X) ? mRightAxisX : mRightAxisY;
                }
                return 0;
            default:
                return 0;
        }
    }

    // ─── Physical gamepad ──────────────────────────────────────────────────────

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int btn = keyCodeToRetroButton(keyCode);
        if (btn >= 0) { mPhysicalButtonState[btn] = true; return true; }
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        int btn = keyCodeToRetroButton(keyCode);
        if (btn >= 0) { mPhysicalButtonState[btn] = false; return true; }
        return false;
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            mLeftAxisX  = floatToRetroAxis(event.getAxisValue(MotionEvent.AXIS_X));
            mLeftAxisY  = floatToRetroAxis(event.getAxisValue(MotionEvent.AXIS_Y));
            mRightAxisX = floatToRetroAxis(event.getAxisValue(MotionEvent.AXIS_Z));
            mRightAxisY = floatToRetroAxis(event.getAxisValue(MotionEvent.AXIS_RZ));

            // D-pad via HAT axes
            float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
            float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
            mPhysicalButtonState[RETRO_DEVICE_ID_JOYPAD_LEFT]  = hatX < -0.5f;
            mPhysicalButtonState[RETRO_DEVICE_ID_JOYPAD_RIGHT] = hatX >  0.5f;
            mPhysicalButtonState[RETRO_DEVICE_ID_JOYPAD_UP]    = hatY < -0.5f;
            mPhysicalButtonState[RETRO_DEVICE_ID_JOYPAD_DOWN]  = hatY >  0.5f;
            return true;
        }
        return false;
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private static int keyCodeToRetroButton(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:      return RETRO_DEVICE_ID_JOYPAD_A;
            case KeyEvent.KEYCODE_BUTTON_B:      return RETRO_DEVICE_ID_JOYPAD_B;
            case KeyEvent.KEYCODE_BUTTON_X:      return RETRO_DEVICE_ID_JOYPAD_X;
            case KeyEvent.KEYCODE_BUTTON_Y:      return RETRO_DEVICE_ID_JOYPAD_Y;
            case KeyEvent.KEYCODE_BUTTON_L1:     return RETRO_DEVICE_ID_JOYPAD_L;
            case KeyEvent.KEYCODE_BUTTON_R1:     return RETRO_DEVICE_ID_JOYPAD_R;
            case KeyEvent.KEYCODE_BUTTON_L2:     return RETRO_DEVICE_ID_JOYPAD_L2;
            case KeyEvent.KEYCODE_BUTTON_R2:     return RETRO_DEVICE_ID_JOYPAD_R2;
            case KeyEvent.KEYCODE_BUTTON_START:  return RETRO_DEVICE_ID_JOYPAD_START;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return RETRO_DEVICE_ID_JOYPAD_SELECT;
            case KeyEvent.KEYCODE_DPAD_UP:       return RETRO_DEVICE_ID_JOYPAD_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:     return RETRO_DEVICE_ID_JOYPAD_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:     return RETRO_DEVICE_ID_JOYPAD_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:    return RETRO_DEVICE_ID_JOYPAD_RIGHT;
            // Keyboard fallback
            case KeyEvent.KEYCODE_Z:             return RETRO_DEVICE_ID_JOYPAD_A;
            case KeyEvent.KEYCODE_X:             return RETRO_DEVICE_ID_JOYPAD_B;
            case KeyEvent.KEYCODE_A:             return RETRO_DEVICE_ID_JOYPAD_Y;
            case KeyEvent.KEYCODE_S:             return RETRO_DEVICE_ID_JOYPAD_X;
            case KeyEvent.KEYCODE_ENTER:         return RETRO_DEVICE_ID_JOYPAD_START;
            case KeyEvent.KEYCODE_SPACE:         return RETRO_DEVICE_ID_JOYPAD_SELECT;
            default:                             return -1;
        }
    }

    private static int floatToRetroAxis(float v) {
        return (int) (v * 0x7fff);
    }

    /** Reset all button states. */
    public void resetFrame() {
        for (int i = 0; i < BUTTON_COUNT; i++) {
            mPhysicalButtonState[i] = false;
            mOverlayButtonState[i]  = false;
        }
        mLeftAxisX = mLeftAxisY = mRightAxisX = mRightAxisY = 0;
    }
}
