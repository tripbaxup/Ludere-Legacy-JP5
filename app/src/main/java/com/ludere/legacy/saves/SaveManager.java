package com.ludere.legacy.saves;

import android.content.Context;
import android.util.Log;

import com.ludere.legacy.PayloadConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * SaveManager
 *
 * Manages all persistent game data:
 *   - SRAM (battery-backed RAM saves)
 *   - Save states (snapshot files)
 *   - Screenshots
 *
 * Save directory structure:
 *   <filesDir>/saves/<appId>/
 *     sram.srm          — SRAM data
 *     state_0.state     — Save state slot 0
 *     state_1.state     — Save state slot 1
 *     ...
 *     screenshot_<ts>.png
 *
 * API 17 compatible. Uses internal storage by default.
 * External storage is used when config.saveLocation == "external".
 */
public class SaveManager {

    private static final String TAG = "SaveManager";

    private static final String SRAM_FILENAME       = "sram.srm";
    private static final String STATE_FILENAME_FMT  = "state_%d.state";
    private static final String AUTOSAVE_FILENAME   = "autosave.state";
    private static final int    MAX_STATE_SLOTS      = 10;

    private final Context       mContext;
    private final PayloadConfig mConfig;
    private final File          mSaveDir;

    // JNI bridge for SRAM and state operations
    private static native byte[] nativeGetSRAM();
    private static native void   nativeSetSRAM(byte[] data);
    private static native byte[] nativeGetState();
    private static native void   nativeSetState(byte[] data);

    public SaveManager(Context context, PayloadConfig config) {
        mContext = context;
        mConfig  = config;
        mSaveDir = buildSaveDirectory();
        mSaveDir.mkdirs();
        Log.i(TAG, "Save directory: " + mSaveDir.getAbsolutePath());
    }

    private File buildSaveDirectory() {
        String gameId = (mConfig.appId != null && !mConfig.appId.isEmpty())
            ? mConfig.appId
            : sanitize(mConfig.title);

        File base;
        if ("external".equalsIgnoreCase(mConfig.saveLocation)) {
            File ext = mContext.getExternalFilesDir(null);
            base = (ext != null) ? ext : mContext.getFilesDir();
        } else {
            base = mContext.getFilesDir();
        }
        return new File(base, "saves" + File.separator + gameId);
    }

    public File getSaveDirectory() { return mSaveDir; }

    // ─── SRAM ──────────────────────────────────────────────────────────────────

    /**
     * Write SRAM data from the native core to disk.
     * Called automatically on pause and destroy.
     */
    public void saveSRAM() {
        try {
            byte[] data = nativeGetSRAM();
            if (data == null || data.length == 0) return;
            writeFile(new File(mSaveDir, SRAM_FILENAME), data);
            Log.d(TAG, "SRAM saved (" + data.length + " bytes)");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native SRAM not available yet");
        } catch (IOException e) {
            Log.e(TAG, "Failed to save SRAM", e);
        }
    }

    /**
     * Load SRAM data from disk and push it to the native core.
     * Called after the core is initialized.
     */
    public void loadSRAM() {
        File f = new File(mSaveDir, SRAM_FILENAME);
        if (!f.exists()) return;
        try {
            byte[] data = readFile(f);
            nativeSetSRAM(data);
            Log.d(TAG, "SRAM loaded (" + data.length + " bytes)");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native SRAM not available yet");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load SRAM", e);
        }
    }

    // ─── Save states ───────────────────────────────────────────────────────────

    /** Save a state snapshot to the given slot [0 – MAX_STATE_SLOTS-1]. */
    public void saveState(int slot) {
        if (slot < 0 || slot >= MAX_STATE_SLOTS) return;
        try {
            byte[] data = nativeGetState();
            if (data == null || data.length == 0) return;
            File f = new File(mSaveDir, String.format(STATE_FILENAME_FMT, slot));
            writeFile(f, data);
            Log.i(TAG, "State saved to slot " + slot);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native state not available yet");
        } catch (IOException e) {
            Log.e(TAG, "Failed to save state slot " + slot, e);
        }
    }

    /** Load a state snapshot from the given slot. */
    public void loadState(int slot) {
        if (slot < 0 || slot >= MAX_STATE_SLOTS) return;
        File f = new File(mSaveDir, String.format(STATE_FILENAME_FMT, slot));
        if (!f.exists()) {
            Log.w(TAG, "No state in slot " + slot);
            return;
        }
        try {
            byte[] data = readFile(f);
            nativeSetState(data);
            Log.i(TAG, "State loaded from slot " + slot);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native state not available yet");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load state slot " + slot, e);
        }
    }

    /** Returns true if a save state exists in the given slot. */
    public boolean hasState(int slot) {
        return new File(mSaveDir, String.format(STATE_FILENAME_FMT, slot)).exists();
    }

    // ─── Autosave (resume-on-relaunch) ────────────────────────────────────────
    //
    // Separate from the numbered user save-state slots above: this captures
    // a full emulation snapshot automatically on pause/destroy so the game
    // resumes exactly where it was left, even if the app was killed
    // abruptly rather than closed via an in-game save.

    /** Write a full state snapshot to the dedicated autosave file. */
    public void saveAutoState() {
        try {
            byte[] data = nativeGetState();
            if (data == null || data.length == 0) return;
            writeFile(new File(mSaveDir, AUTOSAVE_FILENAME), data);
            Log.d(TAG, "Autosave written (" + data.length + " bytes)");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native state not available yet");
        } catch (IOException e) {
            Log.e(TAG, "Failed to write autosave", e);
        }
    }

    /** Restore the autosave snapshot, if one exists. */
    public void loadAutoState() {
        File f = new File(mSaveDir, AUTOSAVE_FILENAME);
        if (!f.exists()) return;
        try {
            byte[] data = readFile(f);
            nativeSetState(data);
            Log.i(TAG, "Autosave restored (" + data.length + " bytes)");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native state not available yet");
        } catch (IOException e) {
            Log.e(TAG, "Failed to restore autosave", e);
        }
    }

    /** Returns true if an autosave snapshot exists. */
    public boolean hasAutoState() {
        return new File(mSaveDir, AUTOSAVE_FILENAME).exists();
    }

    // ─── File I/O helpers ──────────────────────────────────────────────────────

    private static void writeFile(File f, byte[] data) throws IOException {
        FileOutputStream fos = new FileOutputStream(f);
        try { fos.write(data); } finally { fos.close(); }
    }

    private static byte[] readFile(File f) throws IOException {
        FileInputStream fis = new FileInputStream(f);
        try {
            byte[] data = new byte[(int) f.length()];
            int read = 0;
            while (read < data.length) {
                int n = fis.read(data, read, data.length - read);
                if (n < 0) break;
                read += n;
            }
            return data;
        } finally {
            fis.close();
        }
    }

    private static String sanitize(String s) {
        if (s == null) return "unknown";
        return s.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
    }
}
