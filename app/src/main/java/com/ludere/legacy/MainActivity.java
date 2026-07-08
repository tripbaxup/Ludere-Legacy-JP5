package com.ludere.legacy;

import android.app.Activity;
import android.content.res.AssetManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.ludere.legacy.audio.AudioEngine;
import com.ludere.legacy.input.InputManager;
import com.ludere.legacy.libretro.LibretroRuntime;
import com.ludere.legacy.overlay.TouchOverlayView;
import com.ludere.legacy.saves.SaveManager;
import com.ludere.legacy.video.VideoRenderer;

/**
 * Ludere Legacy Autogen - MainActivity
 *
 * Entry point for the standalone retro game APK.
 * Targets Android 4.2.2 (API 17). No AndroidX. No Jetpack.
 *
 * Architecture:
 *   MainActivity → PayloadLoader → ConfigParser → CoreSelector
 *   → LibretroRuntime → VideoRenderer / AudioEngine / InputManager
 *   → TouchOverlay → SaveManager
 */
public class MainActivity extends Activity {

    private static final String TAG = "LudereMain";

    private GLSurfaceView mGLSurfaceView;
    private TouchOverlayView mOverlayView;

    private PayloadLoader mPayloadLoader;
    private PayloadConfig mConfig;
    private LibretroRuntime mRuntime;
    private VideoRenderer mVideoRenderer;
    private AudioEngine mAudioEngine;
    private InputManager mInputManager;
    private SaveManager mSaveManager;

    private boolean mRuntimeStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on during gameplay
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mGLSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
        mOverlayView   = (TouchOverlayView) findViewById(R.id.touch_overlay_view);

        applyImmersiveMode();
        initializeComponents();
    }

    private void applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );
        }
    }

    private void initializeComponents() {
        AssetManager assets = getAssets();

        // 1. Load and validate payload
        mPayloadLoader = new PayloadLoader(this, assets);
        mConfig = mPayloadLoader.load();

        if (mConfig == null) {
            Log.e(TAG, "Failed to load payload configuration. Exiting.");
            finish();
            return;
        }

        Log.i(TAG, "Loaded game: " + mConfig.title + " | Core: " + mConfig.core);

        // 2. Save manager
        mSaveManager = new SaveManager(this, mConfig);

        // 3. Audio engine
        mAudioEngine = new AudioEngine();

        // 4. Input manager (attached to overlay)
        mInputManager = new InputManager(mOverlayView);

        // 5. Video renderer (GLES2)
        mVideoRenderer = new VideoRenderer(mGLSurfaceView, mConfig);

        // 6. Libretro runtime
        mRuntime = new LibretroRuntime(
            this, mConfig, mVideoRenderer, mAudioEngine, mInputManager, mSaveManager
        );

        // 7. Wire overlay input to runtime
        mOverlayView.setInputManager(mInputManager);
        mOverlayView.loadLayout(mConfig.overlayPreset, assets);

        // 8. Start emulation
        mRuntime.start();
        mRuntimeStarted = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveMode();
        if (mGLSurfaceView != null) mGLSurfaceView.onResume();
        if (mAudioEngine != null) mAudioEngine.resume();
        if (mRuntime != null) mRuntime.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mRuntime != null) mRuntime.pause();
        if (mAudioEngine != null) mAudioEngine.pause();
        if (mGLSurfaceView != null) mGLSurfaceView.onPause();
        // onPause is the last lifecycle callback guaranteed to run before
        // the OS may kill the process (onDestroy is not guaranteed on an
        // abrupt close), so this is where persistence has to happen.
        if (mSaveManager != null) {
            mSaveManager.saveSRAM();
            mSaveManager.saveAutoState();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRuntimeStarted) {
            if (mSaveManager != null) {
                mSaveManager.saveSRAM();
                mSaveManager.saveAutoState();
            }
            if (mRuntime != null) mRuntime.stop();
            if (mAudioEngine != null) mAudioEngine.release();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }
}
