package com.ludere.legacy;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * PayloadLoader
 *
 * Reads the bundled payload from the APK's assets directory.
 * Locates config.xml, detects the ROM file, and returns a
 * fully populated PayloadConfig.
 *
 * All assets are bundled at build time by the GitHub Actions workflow.
 * Nothing is downloaded at runtime.
 */
public class PayloadLoader {

    private static final String TAG = "PayloadLoader";

    /** Known ROM extensions mapped to libretro cores. */
    private static final Set<String> ROM_EXTENSIONS = new HashSet<>(Arrays.asList(
        "nes", "fds", "unf", "unif",
        "sfc", "smc", "fig",
        "gba", "agb",
        "gb", "gbc", "sgb",
        "n64", "z64", "v64",
        "gen", "md", "smd",
        "pce",
        "gg",
        "sms",
        "nds",
        "gba",
        "iso", "cue", "bin",
        "zip"
    ));

    private final Context mContext;
    private final AssetManager mAssets;

    public PayloadLoader(Context context, AssetManager assets) {
        mContext = context;
        mAssets  = assets;
    }

    /**
     * Load and validate the payload.
     *
     * @return Populated PayloadConfig, or null on failure.
     */
    public PayloadConfig load() {
        // 1. Parse config.xml
        ConfigParser parser = new ConfigParser(mAssets);
        PayloadConfig config = parser.parse("payload/config.xml");
        if (config == null) {
            Log.e(TAG, "config.xml missing or invalid");
            return null;
        }

        // 2. Detect ROM if not specified
        if (config.romFile == null || config.romFile.isEmpty()) {
            config.romFile = detectRom();
        }
        if (config.romFile == null) {
            Log.e(TAG, "No ROM found in payload assets");
            return null;
        }

        // 3. Resolve core
        if (config.core == null || config.core.isEmpty()) {
            config.core = CoreSelector.selectCore(config.romFile);
        }
        if (config.core == null) {
            Log.e(TAG, "Unable to determine libretro core for: " + config.romFile);
            return null;
        }

        // 4. Derive overlay preset from the core if config.xml didn't
        // explicitly set one. Without this, any payload that omits
        // <overlayPreset> silently falls back to "default" (which
        // includes an analog stick meant for N64/PSX-style systems)
        // instead of the layout that actually matches the console.
        if (config.overlayPreset == null || "default".equals(config.overlayPreset)) {
            String derived = presetForCore(config.core);
            if (derived != null) config.overlayPreset = derived;
        }

        Log.i(TAG, "Payload loaded: rom=" + config.romFile + " core=" + config.core);
        return config;
    }

    /** Maps a resolved libretro core id to the matching overlay.xml preset name. */
    private static String presetForCore(String core) {
        switch (core) {
            case "fceumm":            return "nes";
            case "snes9x":            return "snes";
            case "gambatte":          return "gb";
            case "mgba":              return "gba";
            case "genesis_plus_gx":   return "genesis";
            case "mupen64plus_next":  return "n64";
            case "mednafen_psx_hw":   return "psx";
            default:                  return null; // keep "default" preset
        }
    }

    /**
     * Scan assets/payload/ for a file whose extension matches a known ROM type.
     */
    private String detectRom() {
        try {
            String[] files = mAssets.list("payload");
            if (files == null) return null;
            for (String file : files) {
                String ext = getExtension(file).toLowerCase();
                if (ROM_EXTENSIONS.contains(ext)) {
                    Log.i(TAG, "Detected ROM: " + file);
                    return file;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error scanning payload assets", e);
        }
        return null;
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot + 1) : "";
    }
}
