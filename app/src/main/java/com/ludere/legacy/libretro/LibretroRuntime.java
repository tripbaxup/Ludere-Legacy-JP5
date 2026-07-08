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

            // Restore persisted save data now that the core + ROM are
            // loaded. Load SRAM first (in-cart save), then the autosave
            // snapshot if one exists -- the snapshot's own serialized SRAM
            // will take precedence, giving an exact resume point.
            mSaveManager.loadSRAM();
            if (mSaveManager.hasAutoState()) {
                mSaveManager.loadAutoState();
            }

            // Start emulation loop on a dedicated thread
            mRunning = true;
            mEmulationThread = new Thread(mEmulationLoop, "LudereEmulation");
            mEmulationThread.setPriority(Thread.MAX_PRIORITY);
            mEmulationThread.start();

        } catch (IOException e) {
            Log.e(TAG, "Failed to start emulation", e);
        }
    }

    // Set by the emulation loop right before it parks in wait(), so pause()
    // can confirm the loop has actually stopped touching the native core
    // before returning -- important because callers like MainActivity.onPause
    // save state (nativeGetState/nativeGetSRAM) right after pause() returns,
    // and that must not race with retro_run() still executing on the loop
    // thread.
    private volatile boolean mThreadParked = false;

    public void pause() {
        mPaused = true;
        nativePause();

        long deadline = System.currentTimeMillis() + 500;
        while (!mThreadParked
                && mEmulationThread != null && mEmulationThread.isAlive()
                && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(2); } catch (InterruptedException ignored) {}
        }
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
            // Without pacing, this loop runs as fast as the CPU allows
            // (hundreds of "frames" per second instead of ~60), so a
            // brief button tap gets sampled across many extra emulated
            // frames and looks like the input is being spammed/held.
            // Pace each iteration to the core's real target frame time.
            double fps = nativeGetTargetFps();
            if (fps <= 0.0) fps = 60.0;
            long frameNanos = (long) (1_000_000_000.0 / fps);
            long nextFrameTime = System.nanoTime();

            while (mRunning) {
                if (mPaused) {
                    synchronized (this) {
                        mThreadParked = true;
                        try { wait(); } catch (InterruptedException ignored) {}
                        mThreadParked = false;
                    }
                    // Don't let a long pause cause a burst of catch-up frames.
                    nextFrameTime = System.nanoTime();
                    continue;
                }

                nativeRunFrame();

                nextFrameTime += frameNanos;
                long sleepNanos = nextFrameTime - System.nanoTime();
                if (sleepNanos > 0) {
                    try {
                        Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
                    } catch (InterruptedException ignored) {}
                } else {
                    // Fell behind (e.g. slow device/frame) -- don't try to
                    // burst-catch-up, just resync to avoid runaway drift.
                    nextFrameTime = System.nanoTime();
                }
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
    private native double nativeGetTargetFps();
}
