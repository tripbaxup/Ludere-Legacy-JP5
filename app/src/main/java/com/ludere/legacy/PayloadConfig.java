package com.ludere.legacy;

/**
 * PayloadConfig
 *
 * Plain data object populated by ConfigParser from payload/config.xml.
 * All fields correspond to config.xml elements.
 */
public class PayloadConfig {

    /** Human-readable game title shown in the app label. */
    public String title = "Ludere Game";

    /** Java package name for the generated APK. */
    public String packageName = "com.ludere.legacy";

    /** Version string, e.g. "1.0". */
    public String version = "1.0";

    /** Screen orientation: "landscape", "portrait", "sensor". */
    public String orientation = "landscape";

    /** Whether to use immersive (full-screen) mode. */
    public boolean immersive = true;

    /** Overlay preset name, e.g. "nes", "snes", "gba". */
    public String overlayPreset = "default";

    /** Libretro core identifier, e.g. "fceumm", "snes9x". */
    public String core = null;

    /** ROM filename within assets/payload/. */
    public String romFile = null;

    /** Save file location: "internal" or "external". */
    public String saveLocation = "internal";

    /** Developer name for metadata. */
    public String developer = "";

    /** Publisher name for metadata. */
    public String publisher = "";

    /** Unique application identifier (UUID or custom string). */
    public String appId = "";
}
