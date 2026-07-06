package com.ludere.legacy.overlay;

/**
 * OverlayAnalogStick
 *
 * Data class representing the on-screen analog stick.
 * The stick base is a circle; the knob tracks the touch position
 * within the base radius and reports a normalized [-1, 1] vector.
 */
public class OverlayAnalogStick {

    /** Horizontal center as a fraction of view width. */
    public float xPct;

    /** Vertical center as a fraction of view height. */
    public float yPct;

    /** Base radius as a fraction of min(viewWidth, viewHeight). */
    public float radiusPct;

    public OverlayAnalogStick(float xPct, float yPct, float radiusPct) {
        this.xPct      = xPct;
        this.yPct      = yPct;
        this.radiusPct = radiusPct;
    }
}
