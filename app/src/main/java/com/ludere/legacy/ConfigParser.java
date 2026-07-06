package com.ludere.legacy;

import android.content.res.AssetManager;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;

/**
 * ConfigParser
 *
 * Parses the payload/config.xml file from APK assets.
 * Uses XmlPullParser (available since API 1) — no DOM, no SAX.
 *
 * Supported config.xml fields:
 *   title, packageName, version, orientation, immersive,
 *   overlayPreset, core, saveLocation, developer, publisher,
 *   appId, romFile
 */
public class ConfigParser {

    private static final String TAG = "ConfigParser";

    private final AssetManager mAssets;

    public ConfigParser(AssetManager assets) {
        mAssets = assets;
    }

    /**
     * Parse the config XML at the given asset path.
     *
     * @param assetPath  e.g. "payload/config.xml"
     * @return Populated PayloadConfig, or null on error.
     */
    public PayloadConfig parse(String assetPath) {
        InputStream is = null;
        try {
            is = mAssets.open(assetPath);
            return parseStream(is);
        } catch (IOException e) {
            Log.e(TAG, "Cannot open " + assetPath, e);
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException ignored) {}
            }
        }
    }

    private PayloadConfig parseStream(InputStream is) {
        PayloadConfig config = new PayloadConfig();
        try {
            XmlPullParser xpp = Xml.newPullParser();
            xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            xpp.setInput(is, "UTF-8");

            int eventType = xpp.getEventType();
            String currentTag = null;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        currentTag = xpp.getName();
                        break;
                    case XmlPullParser.TEXT:
                        String text = xpp.getText().trim();
                        if (currentTag != null && !text.isEmpty()) {
                            applyField(config, currentTag, text);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        currentTag = null;
                        break;
                }
                eventType = xpp.next();
            }
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error parsing config.xml", e);
            return null;
        }
        return config;
    }

    private void applyField(PayloadConfig config, String tag, String value) {
        switch (tag) {
            case "title":         config.title         = value; break;
            case "packageName":   config.packageName   = value; break;
            case "version":       config.version       = value; break;
            case "orientation":   config.orientation   = value; break;
            case "immersive":     config.immersive     = Boolean.parseBoolean(value); break;
            case "overlayPreset": config.overlayPreset = value; break;
            case "core":          config.core          = value; break;
            case "saveLocation":  config.saveLocation  = value; break;
            case "developer":     config.developer     = value; break;
            case "publisher":     config.publisher     = value; break;
            case "appId":         config.appId         = value; break;
            case "romFile":       config.romFile       = value; break;
            default:
                Log.d(TAG, "Unknown config field: " + tag);
        }
    }
}
