package com.ludere.legacy.overlay;

import android.content.res.AssetManager;
import android.util.Log;
import android.util.Xml;

import com.ludere.legacy.input.InputManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * OverlayParser
 *
 * Parses overlay.xml from the payload assets.
 *
 * overlay.xml format:
 * <overlay>
 *   <preset name="nes">
 *     <button id="A"      x="0.88" y="0.55" r="0.06" />
 *     <button id="B"      x="0.82" y="0.70" r="0.06" />
 *     <button id="UP"     x="0.12" y="0.35" r="0.06" />
 *     <button id="DOWN"   x="0.12" y="0.65" r="0.06" />
 *     <button id="LEFT"   x="0.07" y="0.50" r="0.06" />
 *     <button id="RIGHT"  x="0.17" y="0.50" r="0.06" />
 *     <button id="START"  x="0.52" y="0.85" r="0.05" />
 *     <button id="SELECT" x="0.38" y="0.85" r="0.05" />
 *     <button id="L"      x="0.10" y="0.10" r="0.06" />
 *     <button id="R"      x="0.90" y="0.10" r="0.06" />
 *     <analog id="LEFT"   x="0.25" y="0.70" r="0.10" />
 *   </preset>
 * </overlay>
 */
public class OverlayParser {

    private static final String TAG = "OverlayParser";

    private final AssetManager mAssets;
    private OverlayAnalogStick mAnalogStick;

    public OverlayParser(AssetManager assets) {
        mAssets = assets;
    }

    public OverlayAnalogStick getAnalogStick() { return mAnalogStick; }

    /**
     * Parse overlay.xml and return buttons for the given preset.
     * Returns null if the file is missing or the preset is not found.
     */
    public List<OverlayButton> parse(String assetPath, String presetName) {
        InputStream is = null;
        try {
            is = mAssets.open(assetPath);
            return parseStream(is, presetName);
        } catch (IOException e) {
            Log.w(TAG, "overlay.xml not found at " + assetPath + " — using default layout");
            return null;
        } finally {
            if (is != null) try { is.close(); } catch (IOException ignored) {}
        }
    }

    private List<OverlayButton> parseStream(InputStream is, String targetPreset) {
        List<OverlayButton> buttons = new ArrayList<>();
        mAnalogStick = null;
        boolean inTargetPreset = false;

        try {
            XmlPullParser xpp = Xml.newPullParser();
            xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            xpp.setInput(is, "UTF-8");

            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = xpp.getName();
                    if ("preset".equals(tag)) {
                        String name = xpp.getAttributeValue(null, "name");
                        inTargetPreset = targetPreset == null
                            || targetPreset.isEmpty()
                            || targetPreset.equalsIgnoreCase(name);
                    } else if (inTargetPreset && "button".equals(tag)) {
                        OverlayButton btn = parseButton(xpp);
                        if (btn != null) buttons.add(btn);
                    } else if (inTargetPreset && "analog".equals(tag)) {
                        mAnalogStick = parseAnalog(xpp);
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("preset".equals(xpp.getName())) {
                        if (inTargetPreset && !buttons.isEmpty()) {
                            return buttons; // Found and parsed target preset
                        }
                        inTargetPreset = false;
                    }
                }
                eventType = xpp.next();
            }
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error parsing overlay.xml", e);
        }
        return buttons.isEmpty() ? null : buttons;
    }

    private OverlayButton parseButton(XmlPullParser xpp) {
        try {
            String id    = xpp.getAttributeValue(null, "id");
            float  x     = Float.parseFloat(xpp.getAttributeValue(null, "x"));
            float  y     = Float.parseFloat(xpp.getAttributeValue(null, "y"));
            float  r     = Float.parseFloat(xpp.getAttributeValue(null, "r"));
            int    btnId = labelToRetroId(id);
            return new OverlayButton(id, x, y, r, btnId);
        } catch (Exception e) {
            Log.w(TAG, "Malformed <button> element", e);
            return null;
        }
    }

    private OverlayAnalogStick parseAnalog(XmlPullParser xpp) {
        try {
            float x = Float.parseFloat(xpp.getAttributeValue(null, "x"));
            float y = Float.parseFloat(xpp.getAttributeValue(null, "y"));
            float r = Float.parseFloat(xpp.getAttributeValue(null, "r"));
            return new OverlayAnalogStick(x, y, r);
        } catch (Exception e) {
            Log.w(TAG, "Malformed <analog> element", e);
            return null;
        }
    }

    private static int labelToRetroId(String id) {
        if (id == null) return -1;
        switch (id.toUpperCase()) {
            case "A":      return InputManager.RETRO_DEVICE_ID_JOYPAD_A;
            case "B":      return InputManager.RETRO_DEVICE_ID_JOYPAD_B;
            case "X":      return InputManager.RETRO_DEVICE_ID_JOYPAD_X;
            case "Y":      return InputManager.RETRO_DEVICE_ID_JOYPAD_Y;
            case "UP":     return InputManager.RETRO_DEVICE_ID_JOYPAD_UP;
            case "DOWN":   return InputManager.RETRO_DEVICE_ID_JOYPAD_DOWN;
            case "LEFT":   return InputManager.RETRO_DEVICE_ID_JOYPAD_LEFT;
            case "RIGHT":  return InputManager.RETRO_DEVICE_ID_JOYPAD_RIGHT;
            case "START":  return InputManager.RETRO_DEVICE_ID_JOYPAD_START;
            case "SELECT": return InputManager.RETRO_DEVICE_ID_JOYPAD_SELECT;
            case "L":      return InputManager.RETRO_DEVICE_ID_JOYPAD_L;
            case "R":      return InputManager.RETRO_DEVICE_ID_JOYPAD_R;
            case "L2":     return InputManager.RETRO_DEVICE_ID_JOYPAD_L2;
            case "R2":     return InputManager.RETRO_DEVICE_ID_JOYPAD_R2;
            case "L3":     return InputManager.RETRO_DEVICE_ID_JOYPAD_L3;
            case "R3":     return InputManager.RETRO_DEVICE_ID_JOYPAD_R3;
            default:       return -1;
        }
    }
}
