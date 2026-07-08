package com.ludere.legacy.overlay;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.ludere.legacy.input.InputManager;

import java.util.ArrayList;
import java.util.List;

/**
 * TouchOverlayView
 *
 * Renders and handles the on-screen touch controller overlay.
 * Inspired by the original Ludere project.
 *
 * Features:
 *   - D-pad (8-directional)
 *   - Face buttons (A, B, X, Y)
 *   - Start / Select
 *   - L / R shoulder buttons
 *   - Analog stick (left)
 *   - Turbo buttons (mapped to L2/R2)
 *   - Per-game layout via overlay.xml
 *   - Opacity and scale adjustment
 *   - Portrait and landscape layouts
 *
 * API 17 compatible. No AndroidX. Custom View only.
 */
public class TouchOverlayView extends View {

    private static final String TAG = "TouchOverlayView";

    private InputManager mInputManager;
    private OverlayParser mParser;

    private List<OverlayButton> mButtons = new ArrayList<>();
    private OverlayAnalogStick  mAnalogStick;

    private float mOpacity = 0.7f;
    private float mScale   = 1.0f;

    private Paint mButtonPaint;
    private Paint mLabelPaint;
    private Paint mAnalogPaint;

    // Current button states for InputManager
    private final boolean[] mButtonState = new boolean[16];
    // Analog state [leftX, leftY]
    private final int[] mAnalogState = new int[2];

    public TouchOverlayView(Context context) {
        super(context);
        init();
    }

    public TouchOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TouchOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackgroundColor(Color.TRANSPARENT);

        mButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mButtonPaint.setStyle(Paint.Style.FILL);
        mButtonPaint.setColor(Color.argb((int)(mOpacity * 255), 200, 200, 200));

        mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLabelPaint.setColor(Color.WHITE);
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
        mLabelPaint.setTextSize(28f);

        mAnalogPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mAnalogPaint.setStyle(Paint.Style.FILL);
        mAnalogPaint.setColor(Color.argb((int)(mOpacity * 255), 100, 100, 220));
    }

    public void setInputManager(InputManager im) {
        mInputManager = im;
    }

    /**
     * Load overlay layout from assets.
     * Falls back to the built-in default layout if the asset is missing.
     */
    public void loadLayout(String preset, AssetManager assets) {
        mParser = new OverlayParser(assets);
        List<OverlayButton> loaded = mParser.parse("payload/overlay.xml", preset);
        if (loaded != null && !loaded.isEmpty()) {
            mButtons = loaded;
            mAnalogStick = mParser.getAnalogStick();
        } else {
            buildDefaultLayout();
        }
        invalidate();
    }

    // ─── Default built-in layout ───────────────────────────────────────────────

    private void buildDefaultLayout() {
        mButtons.clear();
        // Layout is computed relative to view dimensions in onSizeChanged
        // Buttons are defined as percentage-based positions
        mButtons.add(new OverlayButton("D-UP",    0.12f, 0.35f, 0.06f, InputManager.RETRO_DEVICE_ID_JOYPAD_UP));
        mButtons.add(new OverlayButton("D-DN",    0.12f, 0.65f, 0.06f, InputManager.RETRO_DEVICE_ID_JOYPAD_DOWN));
        mButtons.add(new OverlayButton("D-LT",    0.07f, 0.50f, 0.06f, InputManager.RETRO_DEVICE_ID_JOYPAD_LEFT));
        mButtons.add(new OverlayButton("D-RT",    0.17f, 0.50f, 0.06f, InputManager.RETRO_DEVICE_ID_JOYPAD_RIGHT));
        mButtons.add(new OverlayButton("A",       0.88f, 0.55f, 0.06f, InputManager.RETRO_DEVICE_ID_JOYPAD_A));
        mButtons.add(new OverlayButton("B",       0.82f, 0.70f, 0.06f, InputManager.RETRO_DEVICE_ID_JOYPAD_B));
        mButtons.add(new OverlayButton("X",       0.82f, 0.40f, 0.06f, InputManager.RETRO_DEVICE_ID_JOYPAD_X));
        mButtons.add(new OverlayButton("Y",       0.76f, 0.55f, 0.06f, InputManager.RETRO_DEVICE_ID_JOYPAD_Y));
        mButtons.add(new OverlayButton("SEL",     0.38f, 0.85f, 0.05f, InputManager.RETRO_DEVICE_ID_JOYPAD_SELECT));
        mButtons.add(new OverlayButton("STA",     0.52f, 0.85f, 0.05f, InputManager.RETRO_DEVICE_ID_JOYPAD_START));
        mButtons.add(new OverlayButton("L",       0.10f, 0.10f, 0.06f, InputManager.RETRO_DEVICE_ID_JOYPAD_L));
        mButtons.add(new OverlayButton("R",       0.90f, 0.10f, 0.06f, InputManager.RETRO_DEVICE_ID_JOYPAD_R));

        // Analog stick (left side, lower)
        mAnalogStick = new OverlayAnalogStick(0.25f, 0.70f, 0.10f);
    }

    // ─── Drawing ───────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        for (OverlayButton btn : mButtons) {
            float cx = btn.xPct * w;
            float cy = btn.yPct * h;
            float r  = btn.radiusPct * Math.min(w, h) * mScale;

            mButtonPaint.setColor(
                btn.pressed
                    ? Color.argb((int)(mOpacity * 255), 80, 180, 80)
                    : Color.argb((int)(mOpacity * 255), 200, 200, 200)
            );
            canvas.drawCircle(cx, cy, r, mButtonPaint);
            mLabelPaint.setTextSize(r * 0.6f);
            canvas.drawText(btn.label, cx, cy + mLabelPaint.getTextSize() / 3f, mLabelPaint);
        }

        // Analog stick
        if (mAnalogStick != null) {
            float cx   = mAnalogStick.xPct * w;
            float cy   = mAnalogStick.yPct * h;
            float base = mAnalogStick.radiusPct * Math.min(w, h) * mScale;
            float knob = base * 0.45f;

            mAnalogPaint.setColor(Color.argb((int)(mOpacity * 255), 60, 60, 160));
            canvas.drawCircle(cx, cy, base, mAnalogPaint);

            float kx = cx + mAnalogState[0] / (float) 0x7fff * (base - knob);
            float ky = cy + mAnalogState[1] / (float) 0x7fff * (base - knob);
            mAnalogPaint.setColor(Color.argb((int)(mOpacity * 255), 100, 100, 220));
            canvas.drawCircle(kx, ky, knob, mAnalogPaint);
        }
    }

    // ─── Touch handling ────────────────────────────────────────────────────────

    // A pointer must move this much further outside a button's radius to
    // release it than it needed to be inside to press it. Without this,
    // small finger tremor right at the edge of a (small) D-pad button
    // flickers the button on/off rapidly, which looks like the input is
    // being spammed even though the finger barely moved.
    private static final float RELEASE_RADIUS_MULTIPLIER = 1.35f;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int w = getWidth();
        int h = getHeight();

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            for (OverlayButton btn : mButtons) btn.pressed = false;
            mAnalogState[0] = 0;
            mAnalogState[1] = 0;
            syncButtonState();
            invalidate();
            return true;
        }

        // Check buttons with hysteresis: a button that's already pressed
        // stays pressed until a pointer moves outside the larger release
        // radius, instead of re-testing the exact press radius every event.
        for (OverlayButton btn : mButtons) {
            float cx = btn.xPct * w;
            float cy = btn.yPct * h;
            float pressR   = btn.radiusPct * Math.min(w, h) * mScale;
            float releaseR = pressR * RELEASE_RADIUS_MULTIPLIER;
            float threshold = btn.pressed ? releaseR : pressR;

            boolean near = false;
            for (int i = 0; i < event.getPointerCount(); i++) {
                float dx = event.getX(i) - cx;
                float dy = event.getY(i) - cy;
                if (dx * dx + dy * dy <= threshold * threshold) {
                    near = true;
                    break;
                }
            }
            btn.pressed = near;
        }

        // Analog stick: no hysteresis needed, it's continuous rather than
        // digital, so it's recomputed fresh each event.
        mAnalogState[0] = 0;
        mAnalogState[1] = 0;
        if (mAnalogStick != null) {
            float cx   = mAnalogStick.xPct * w;
            float cy   = mAnalogStick.yPct * h;
            float base = mAnalogStick.radiusPct * Math.min(w, h) * mScale;
            for (int i = 0; i < event.getPointerCount(); i++) {
                float tx = event.getX(i);
                float ty = event.getY(i);
                float dx = tx - cx;
                float dy = ty - cy;
                if (dx * dx + dy * dy <= base * base) {
                    float nx = dx / base;
                    float ny = dy / base;
                    float len = (float) Math.sqrt(nx * nx + ny * ny);
                    if (len > 1f) { nx /= len; ny /= len; }
                    mAnalogState[0] = (int) (nx * 0x7fff);
                    mAnalogState[1] = (int) (ny * 0x7fff);
                    break;
                }
            }
        }

        syncButtonState();
        invalidate();
        return true;
    }

    private void syncButtonState() {
        for (OverlayButton btn : mButtons) {
            if (btn.retroButtonId >= 0 && btn.retroButtonId < mButtonState.length) {
                mButtonState[btn.retroButtonId] = btn.pressed;
            }
        }
    }

    // ─── State accessors ───────────────────────────────────────────────────────

    public boolean[] getButtonState() { return mButtonState; }
    public int[]     getAnalogState() { return mAnalogState; }

    public void setOpacity(float opacity) {
        mOpacity = Math.max(0f, Math.min(1f, opacity));
        invalidate();
    }

    public void setScale(float scale) {
        mScale = Math.max(0.5f, Math.min(2.0f, scale));
        invalidate();
    }
}
