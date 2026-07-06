package com.ludere.legacy.libretro;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.ludere.legacy.CoreSelector;
import com.ludere.legacy.PayloadConfig;
import com.ludere.legacy.audio.AudioEngine;
import com.ludere.legacy.input.InputManager;
import com.ludere.legacy.saves.SaveManager;
import com.ludere.legacy.video.VideoRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * LibretroRuntime
 *
 * Bridges the Android Java layer to the native libretro core via JNI.
 * Manages the emulation loop lifecycle: start, pause, resume, stop.
 *
 * The native side (libludere_jni.so) wraps the selected libretro core
 * and exposes the retro_* API through JNI callbacks.
 *
 * Callback flow:
 *   Native retro_run() → video_refresh_cb → VideoRenderer.onVideoFrame()
 *   Native retro_run() → audio_sample_batch_cb → AudioEngine.onAudioBatch()
 *   Native retro_run() → input_poll_cb → InputManager.pollInput()
 *   Native retro_run() → input_state_cb → InputManager.getInputState()
 */
public class LibretroRuntime {

    private static final String TAG = "LibretroRuntime";

    // Load the JNI bridge library (built alongside the core)
    static {
        try {
            System.loadLibrary("ludere_jni");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load ludere_jni native library", e);
        }
    }

    private final Context       mContext;
    private final PayloadConfig mConfig;
    private final VideoRenderer mVideoRenderer;
    private final AudioEngine   mAudioEngine;
    private final InputManager  mInputManager;
    private final SaveManager   mSaveManager;

    private Thread  mEmulationThread;
    private boolean mRunning = false;
    private boolean mPaused  = false;

    private String mRomPath;
    private String mCorePath;
    private String mSavePath;

    public LibretroRuntime(
        Context context,
        PayloadConfig config,
        VideoRenderer videoRenderer,
        AudioEngine audioEngine,
        InputManager inputManager,
        SaveManager saveManager
    ) {
        mContext       = context;
        mConfig        = config;
        mVideoRenderer = videoRenderer;
        mAudioEngine   = audioEngine;
        mInputManager  = inputManager;
        mSaveManager   = saveManager;
    }

    /**
     * Extract ROM and core from assets, then start the emulation loop.
     */
    public void start() {
        try {
            mRomPath  = extractAsset("payload/" + mConfig.romFile, "rom_" + mConfig.romFile);
            mSavePath = mSaveManager.getSaveDirectory().getAbsolutePath();

            Log.i(TAG, "Starting emulation: rom=" + mRomPath + " core=" + mConfig.core);

            // Initialize native runtime
            nativeInit(mRomPath, mConfig.core, mSavePath);

            // Start emulation loop on a dedicated thread
            mRunning = true;
            mEmulationThread = new Thread(mEmulationLoop, "LudereEmulation");
            mEmulationThread.setPriority(Thread.MAX_PRIORITY);
            mEmulationThread.start();

        } catch (IOException e) {
            Log.e(TAG, "Failed to start emulation", e);
        }
    }

    public void pause() {
        mPaused = true;
        nativePause();
    }

    public void resume() {
        mPaused = false;
        nativeResume();
        synchronized (mEmulationLoop) {
            mEmulationLoop.notifyAll();
        }
    }

    public void stop() {
        mRunning = false;
        mPaused  = false;
        synchronized (mEmulationLoop) {
            mEmulationLoop.notifyAll();
        }
        try {
            if (mEmulationThread != null) mEmulationThread.join(2000);
        } catch (InterruptedException ignored) {}
        nativeDestroy();
    }

    // ─── Emulation loop ────────────────────────────────────────────────────────

    private final Runnable mEmulationLoop = new Runnable() {
        @Override
        public void run() {
            while (mRunning) {
                if (mPaused) {
                    synchronized (this) {
                        try { wait(); } catch (InterruptedException ignored) {}
                    }
                    continue;
                }
                nativeRunFrame();
            }
        }
    };

    // ─── Asset extraction ──────────────────────────────────────────────────────

    /**
     * Extract a bundled asset to the app's internal files directory.
     */
    private String extractAsset(String assetPath, String destName) throws IOException {
        File dest = new File(mContext.getFilesDir(), destName);
        if (dest.exists()) return dest.getAbsolutePath();

        AssetManager assets = mContext.getAssets();
        InputStream in = assets.open(assetPath);
        FileOutputStream out = new FileOutputStream(dest);
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        in.close();
        out.close();
        return dest.getAbsolutePath();
    }

    // ─── JNI callbacks (called from native) ────────────────────────────────────

    /** Called by native video_refresh callback. */
    @SuppressWarnings("unused")
    public void onVideoFrame(int[] pixels, int width, int height, int pitch) {
        mVideoRenderer.onVideoFrame(pixels, width, height, pitch);
    }

    /** Called by native audio_sample_batch callback. */
    @SuppressWarnings("unused")
    public void onAudioBatch(short[] data, int frames) {
        mAudioEngine.onAudioBatch(data, frames);
    }

    /** Called by native input_poll callback. */
    @SuppressWarnings("unused")
    public void onInputPoll() {
        mInputManager.pollInput();
    }

    /** Called by native input_state callback. */
    @SuppressWarnings("unused")
    public int onInputState(int port, int device, int index, int id) {
        return mInputManager.getInputState(port, device, index, id);
    }

    // ─── Native methods ────────────────────────────────────────────────────────

    private native void nativeInit(String romPath, String coreId, String savePath);
    private native void nativeRunFrame();
    private native void nativePause();
    private native void nativeResume();
    private native void nativeDestroy();
}
