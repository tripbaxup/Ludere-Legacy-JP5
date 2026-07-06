# Payload Format — Ludere Legacy Autogen

This document describes the structure and contents of the `payload.zip` file
that is uploaded to the GitHub Actions workflow to generate a standalone APK.

---

## Directory Structure

```
payload/
├── config.xml          ← Required
├── icon.png            ← Required (512×512 px recommended, square)
├── rom.*               ← Required (any supported ROM extension)
└── optional/
    ├── overlay.xml     ← Custom touch overlay layout
    ├── splash.png      ← Splash screen (displayed on first load)
    ├── metadata.json   ← Game metadata (title, year, genre, etc.)
    └── save.png        ← Save slot preview thumbnail
```

---

## Required Files

### `config.xml`

The primary configuration file. See `payload_example/config.xml` for a
fully annotated template.

**Minimum valid config.xml:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<config>
    <title>My Game</title>
    <packageName>com.example.mygame</packageName>
</config>
```

### `icon.png`

The launcher icon for the generated APK. Recommended size: **512×512 pixels**.
The workflow will resize and place it into all required mipmap densities
(mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi).

### `rom.*`

The game ROM file. The extension determines the libretro core automatically
if `<core>` is not specified in `config.xml`.

Supported extensions and their auto-selected cores:

| Extension | System | Core |
|---|---|---|
| `.nes` `.fds` `.unf` | NES | fceumm |
| `.sfc` `.smc` `.fig` | SNES | snes9x |
| `.gb` `.gbc` `.sgb` | Game Boy / Color | gambatte |
| `.gba` `.agb` | Game Boy Advance | mgba |
| `.gen` `.md` `.smd` `.bin` | Sega Genesis | genesis_plus_gx |
| `.sms` | Sega Master System | genesis_plus_gx |
| `.gg` | Sega Game Gear | genesis_plus_gx |
| `.pce` | PC Engine | mednafen_pce_fast |
| `.n64` `.z64` `.v64` | Nintendo 64 | mupen64plus_next |
| `.cue` `.iso` | PlayStation 1 | mednafen_psx_hw |
| `.nds` | Nintendo DS | desmume2015 |
| `.zip` | Arcade (MAME) | mame2003_plus |

---

## Optional Files

### `overlay.xml`

Custom touch overlay layout. Defines button positions and sizes as
fractions of the screen dimensions. See `payload_example/overlay.xml`
for the full format with multiple presets.

If omitted, the built-in default layout is used.

### `splash.png`

A splash screen image displayed during the initial ROM load.
Recommended size: match the target device screen resolution.
If omitted, a black screen is shown during load.

### `metadata.json`

JSON file with game metadata. Currently used for documentation purposes
and future store integration.

```json
{
  "title": "My Game",
  "year": 1994,
  "developer": "My Studio",
  "publisher": "My Publisher",
  "genre": "Platformer",
  "players": 1,
  "description": "...",
  "region": "USA",
  "system": "NES",
  "version": "1.0",
  "appId": "mygame-nes-2024"
}
```

### `save.png`

A preview thumbnail for the save slot UI (future feature).

---

## Creating a payload.zip

```bash
# From the directory containing your payload/ folder:
zip -r payload.zip payload/
```

Or on Windows using PowerShell:
```powershell
Compress-Archive -Path payload -DestinationPath payload.zip
```

---

## Validation Rules

The GitHub Actions workflow enforces the following:

1. `payload/config.xml` must exist and be valid XML.
2. `payload/icon.png` must exist.
3. At least one ROM file with a recognized extension must exist in `payload/`.
4. `<packageName>` in config.xml must be a valid Android package name
   (e.g., `com.example.mygame`).
5. `<orientation>` must be one of: `landscape`, `portrait`, `sensor`,
   `sensorLandscape`, `sensorPortrait`.
