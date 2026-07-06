# Ludere Legacy Autogen

> Generate a **standalone retro game APK** from a simple payload ZIP — no RetroArch required.

Ludere Legacy Autogen is an open-source Android project inspired by the original [Ludere](https://github.com/libretro/Ludere) frontend. It is a complete rewrite targeting **Android 4.2.2 (API 17)** with OpenGL ES 2.0, using Java only (no Kotlin, no AndroidX, no Jetpack).

---

## How It Works

1. Prepare a `payload.zip` following the [Payload Format](#payload-format).
2. Push it to your fork and trigger the **Autogen APK** GitHub Actions workflow.
3. The workflow validates, parses, and compiles a fully self-contained APK.
4. Download the APK artifact from the Actions run.

---

## Payload Format

```
payload/
├── config.xml          ← Required: game configuration
├── icon.png            ← Required: launcher icon (512×512 recommended)
├── rom.*               ← Required: the game ROM (any supported extension)
└── optional/
    ├── overlay.xml     ← Custom touch overlay layout
    ├── splash.png      ← Splash screen image
    ├── metadata.json   ← Extra metadata (description, year, etc.)
    └── save.png        ← Save slot preview image
```

### config.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<config>
    <title>My Awesome Game</title>
    <packageName>com.example.mygame</packageName>
    <version>1.0</version>
    <orientation>landscape</orientation>
    <immersive>true</immersive>
    <overlayPreset>nes</overlayPreset>
    <core>fceumm</core>
    <saveLocation>internal</saveLocation>
    <developer>My Studio</developer>
    <publisher>My Publisher</publisher>
    <appId>mygame-nes-001</appId>
</config>
```

| Field | Description | Default |
|---|---|---|
| `title` | Game title (app label) | `Ludere Game` |
| `packageName` | Android package name | `com.ludere.legacy` |
| `version` | Version string | `1.0` |
| `orientation` | `landscape` / `portrait` / `sensor` | `landscape` |
| `immersive` | Full-screen immersive mode | `true` |
| `overlayPreset` | Overlay preset name from overlay.xml | `default` |
| `core` | Libretro core ID (auto-detected if omitted) | auto |
| `saveLocation` | `internal` or `external` | `internal` |
| `developer` | Developer name | `` |
| `publisher` | Publisher name | `` |
| `appId` | Unique ID for save folder naming | `` |

---

## Supported Systems & Cores

| Extension | System | Core |
|---|---|---|
| `.nes` `.fds` | Nintendo Entertainment System | fceumm |
| `.sfc` `.smc` | Super Nintendo | snes9x |
| `.gb` `.gbc` | Game Boy / Color | gambatte |
| `.gba` | Game Boy Advance | mgba |
| `.gen` `.md` | Sega Genesis / Mega Drive | genesis_plus_gx |
| `.sms` | Sega Master System | genesis_plus_gx |
| `.gg` | Sega Game Gear | genesis_plus_gx |
| `.pce` | PC Engine / TurboGrafx-16 | mednafen_pce_fast |
| `.n64` `.z64` | Nintendo 64 | mupen64plus_next |
| `.cue` `.iso` | PlayStation 1 | mednafen_psx_hw |
| `.nds` | Nintendo DS | desmume2015 |
| `.zip` | Arcade (MAME) | mame2003_plus |

---

## Frontend Architecture

```
MainActivity
    └── PayloadLoader
            └── ConfigParser         ← Reads payload/config.xml
            └── CoreSelector         ← Maps ROM extension → core .so
    └── LibretroRuntime              ← JNI bridge to libretro core
            └── VideoRenderer        ← OpenGL ES 2.0, letterboxing, integer scale
            └── AudioEngine          ← AudioTrack, low-latency stereo PCM
            └── InputManager         ← Touch overlay + gamepad + keyboard
    └── TouchOverlayView             ← D-pad, face buttons, analog stick
    └── SaveManager                  ← SRAM, save states, per-game folders
```

---

## Overlay System

The touch overlay is fully configurable via `payload/overlay.xml`:

```xml
<overlay>
  <preset name="nes">
    <button id="UP"     x="0.12" y="0.35" r="0.06" />
    <button id="DOWN"   x="0.12" y="0.65" r="0.06" />
    <button id="LEFT"   x="0.07" y="0.50" r="0.06" />
    <button id="RIGHT"  x="0.17" y="0.50" r="0.06" />
    <button id="A"      x="0.88" y="0.55" r="0.06" />
    <button id="B"      x="0.82" y="0.70" r="0.06" />
    <button id="START"  x="0.52" y="0.85" r="0.05" />
    <button id="SELECT" x="0.38" y="0.85" r="0.05" />
    <button id="L"      x="0.10" y="0.10" r="0.06" />
    <button id="R"      x="0.90" y="0.10" r="0.06" />
    <analog id="LEFT"   x="0.25" y="0.70" r="0.10" />
  </preset>
</overlay>
```

Positions are expressed as fractions of the screen dimensions, making layouts resolution-independent.

---

## Building Locally

### Prerequisites

- Android SDK (API 17 platform + build tools 33+)
- Android NDK r21+
- CMake 3.10+
- Java 8+

### Steps

```bash
# Clone the repository
git clone https://github.com/your-org/ludere-legacy-autogen.git
cd ludere-legacy-autogen

# Place your payload
cp /path/to/payload.zip .
unzip payload.zip -d app/src/main/assets/

# Build
./gradlew assembleRelease
```

The APK will be at `app/build/outputs/apk/release/app-release-unsigned.apk`.

---

## GitHub Actions Workflow

The workflow (`autogen.yml`) runs on `workflow_dispatch` and accepts a `payload_zip` input.

**Pipeline steps:**

1. Checkout repository
2. Upload / receive `payload.zip`
3. Validate payload (config.xml + icon.png + ROM)
4. Parse `config.xml`
5. Detect ROM extension → determine libretro core
6. Download prebuilt core `.so` binaries
7. Copy assets into `app/src/main/assets/payload/`
8. Replace launcher icon
9. Generate `AndroidManifest.xml` from template
10. Generate `strings.xml`
11. Generate `BuildConfig` constants
12. Compile APK with Gradle
13. Sign APK (debug keystore)
14. Upload APK as workflow artifact

---

## Target Platform

| Property | Value |
|---|---|
| Minimum Android version | 4.2.2 Jelly Bean |
| Minimum API level | 17 |
| Target API level | 33 |
| Graphics API | OpenGL ES 2.0 |
| Language | Java 8 |
| Dependencies | None (no AndroidX, no Jetpack) |

---

## License

This project is released under the **MIT License**.  
Libretro cores are subject to their own respective licenses.
