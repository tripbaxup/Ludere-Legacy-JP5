package com.ludere.legacy;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * CoreSelector
 *
 * Maps ROM file extensions to the appropriate bundled libretro core .so name.
 * The GitHub Actions workflow uses this same mapping to determine which
 * prebuilt core binary to bundle into the APK.
 *
 * Core .so files are placed in jniLibs/ at build time.
 */
public class CoreSelector {

    private static final String TAG = "CoreSelector";

    /** Extension → core identifier (matches .so filename without "lib" prefix and "_libretro.so" suffix). */
    private static final Map<String, String> EXT_TO_CORE = new HashMap<>();

    static {
        // Nintendo Entertainment System
        EXT_TO_CORE.put("nes",  "fceumm");
        EXT_TO_CORE.put("fds",  "fceumm");
        EXT_TO_CORE.put("unf",  "fceumm");
        EXT_TO_CORE.put("unif", "fceumm");

        // Super Nintendo
        EXT_TO_CORE.put("sfc",  "snes9x");
        EXT_TO_CORE.put("smc",  "snes9x");
        EXT_TO_CORE.put("fig",  "snes9x");

        // Game Boy / Game Boy Color
        EXT_TO_CORE.put("gb",   "gambatte");
        EXT_TO_CORE.put("gbc",  "gambatte");
        EXT_TO_CORE.put("sgb",  "gambatte");

        // Game Boy Advance
        EXT_TO_CORE.put("gba",  "mgba");
        EXT_TO_CORE.put("agb",  "mgba");

        // Sega Genesis / Mega Drive
        EXT_TO_CORE.put("gen",  "genesis_plus_gx");
        EXT_TO_CORE.put("md",   "genesis_plus_gx");
        EXT_TO_CORE.put("smd",  "genesis_plus_gx");
        EXT_TO_CORE.put("bin",  "genesis_plus_gx");

        // Sega Master System
        EXT_TO_CORE.put("sms",  "genesis_plus_gx");

        // Sega Game Gear
        EXT_TO_CORE.put("gg",   "genesis_plus_gx");

        // PC Engine / TurboGrafx-16
        EXT_TO_CORE.put("pce",  "mednafen_pce_fast");

        // Nintendo 64
        EXT_TO_CORE.put("n64",  "mupen64plus_next");
        EXT_TO_CORE.put("z64",  "mupen64plus_next");
        EXT_TO_CORE.put("v64",  "mupen64plus_next");

        // PlayStation 1 (disc images)
        EXT_TO_CORE.put("cue",  "mednafen_psx_hw");
        EXT_TO_CORE.put("iso",  "mednafen_psx_hw");

        // Nintendo DS
        EXT_TO_CORE.put("nds",  "desmume2015");

        // Arcade (MAME 2003)
        EXT_TO_CORE.put("zip",  "mame2003_plus");
    }

    /**
     * Select the libretro core identifier for the given ROM filename.
     *
     * @param romFilename  ROM filename, e.g. "game.nes"
     * @return Core identifier string, or null if unknown.
     */
    public static String selectCore(String romFilename) {
        if (romFilename == null) return null;
        String ext = getExtension(romFilename).toLowerCase();
        String core = EXT_TO_CORE.get(ext);
        if (core == null) {
            Log.w(TAG, "No core mapping for extension: " + ext);
        }
        return core;
    }

    /**
     * Return the .so library name for System.loadLibrary().
     * e.g. "fceumm" → "fceumm_libretro"
     */
    public static String getCoreLibraryName(String coreId) {
        return coreId + "_libretro";
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot + 1) : "";
    }
}
