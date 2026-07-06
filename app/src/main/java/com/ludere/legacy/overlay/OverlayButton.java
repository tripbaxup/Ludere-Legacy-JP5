package com.ludere.legacy.overlay;

/**
 * OverlayButton
 *
 * Data class representing a single on-screen button in the touch overlay.
 * Position and size are expressed as fractions of the view dimensions
 * so the layout scales correctly across screen sizes.
 */
public class OverlayButton {

    /** Display label drawn inside the button circle. */
    public String label;

    /** Horizontal position as a fraction of view width [0.0 – 1.0]. */
    public float xPct;

    /** Vertical position as a fraction of view height [0.0 – 1.0]. */
    public float yPct;

    /** Radius as a fraction of min(viewWidth, viewHeight) [0.0 – 1.0]. */
    public float radiusPct;

    /** Libretro JOYPAD button ID (see InputManager constants). */
    public int retroButtonId;

    /** Whether this button is currently pressed. */
    public boolean pressed = false;

    public OverlayButton(String label, float xPct, float yPct, float radiusPct, int retroButtonId) {
        this.label         = label;
        this.xPct          = xPct;
        this.yPct          = yPct;
        this.radiusPct     = radiusPct;
        this.retroButtonId = retroButtonId;
    }
}
