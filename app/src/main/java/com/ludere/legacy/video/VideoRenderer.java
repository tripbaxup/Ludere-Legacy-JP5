package com.ludere.legacy.video;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.ludere.legacy.PayloadConfig;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * VideoRenderer
 *
 * OpenGL ES 2.0 renderer for the libretro video output.
 * Receives raw pixel frames from LibretroRuntime and blits them
 * to a full-screen quad with letterboxing / integer scaling.
 *
 * Shader-ready architecture: swap mFragmentShader to apply CRT,
 * scanline, or other post-processing effects.
 *
 * API 17 compatible. No AndroidX.
 */
public class VideoRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "VideoRenderer";

    // ─── Scaling modes ─────────────────────────────────────────────────────────
    public static final int SCALE_STRETCH      = 0;
    public static final int SCALE_LETTERBOX    = 1;
    public static final int SCALE_INTEGER      = 2;
    public static final int SCALE_ASPECT_RATIO = 3;

    // ─── Vertex shader ─────────────────────────────────────────────────────────
    private static final String VERTEX_SHADER =
        "attribute vec4 aPosition;\n" +
        "attribute vec2 aTexCoord;\n" +
        "varying vec2 vTexCoord;\n" +
        "void main() {\n" +
        "    gl_Position = aPosition;\n" +
        "    vTexCoord   = aTexCoord;\n" +
        "}\n";

    // ─── Fragment shader (passthrough) ─────────────────────────────────────────
    private static final String FRAGMENT_SHADER =
        "precision mediump float;\n" +
        "varying vec2 vTexCoord;\n" +
        "uniform sampler2D uTexture;\n" +
        "void main() {\n" +
        "    gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
        "}\n";

    // Full-screen quad vertices (x, y) + tex coords (u, v)
    private static final float[] QUAD_VERTICES = {
        -1f, -1f,  0f, 1f,
         1f, -1f,  1f, 1f,
        -1f,  1f,  0f, 0f,
         1f,  1f,  1f, 0f,
    };

    private final GLSurfaceView mSurfaceView;
    private final PayloadConfig mConfig;

    private int mProgram;
    private int mTexture;
    private FloatBuffer mVertexBuffer;

    private int mViewWidth;
    private int mViewHeight;
    private int mFrameWidth;
    private int mFrameHeight;

    private int   mScaleMode = SCALE_LETTERBOX;
    private float mAspectRatio = 4f / 3f;

    // Double-buffered frame data
    private volatile int[]   mPendingPixels;
    private volatile int     mPendingWidth;
    private volatile int     mPendingHeight;
    private volatile boolean mFrameReady = false;
    private boolean mHasFrame = false; // GL-thread only; true once any frame has been uploaded

    public VideoRenderer(GLSurfaceView surfaceView, PayloadConfig config) {
        mSurfaceView = surfaceView;
        mConfig      = config;

        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setRenderer(this);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    // ─── Called from LibretroRuntime (emulation thread) ────────────────────────

    public void onVideoFrame(int[] pixels, int width, int height, int pitch) {
        mPendingPixels = pixels;
        mPendingWidth  = width;
        mPendingHeight = height;
        mFrameReady    = true;
    }

    // ─── GLSurfaceView.Renderer ────────────────────────────────────────────────

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        mProgram = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        mTexture = createTexture();

        ByteBuffer bb = ByteBuffer.allocateDirect(QUAD_VERTICES.length * 4);
        bb.order(ByteOrder.nativeOrder());
        mVertexBuffer = bb.asFloatBuffer();
        mVertexBuffer.put(QUAD_VERTICES);
        mVertexBuffer.position(0);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mViewWidth  = width;
        mViewHeight = height;
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Upload a new frame if one has arrived since the last draw. This
        // GL thread runs on the display's own vsync (RENDERMODE_CONTINUOUSLY),
        // which is a separate clock from the emulation thread's paced ~60fps
        // loop -- they will never stay perfectly locked together. Without
        // persisting the last frame, any vsync where a new frame hasn't
        // arrived yet would show pure black (a very visible bright/dim
        // flicker), instead of just briefly re-showing the previous frame.
        if (mFrameReady) {
            int[] pixels = mPendingPixels;
            int w = mPendingWidth;
            int h = mPendingHeight;
            mFrameReady = false;

            if (pixels != null && w > 0 && h > 0) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture);
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0,
                    GLES20.GL_RGBA, w, h, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
                    ByteBuffer.wrap(intsToBytes(pixels))
                );
                mFrameWidth  = w;
                mFrameHeight = h;
                mHasFrame    = true;
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (!mHasFrame) return; // nothing uploaded yet at all

        // Compute viewport for scaling
        applyScaledViewport(mFrameWidth, mFrameHeight);

        // Draw quad (reusing the currently bound texture, which still
        // holds the most recent frame even if this call didn't upload one)
        GLES20.glUseProgram(mProgram);

        int posLoc = GLES20.glGetAttribLocation(mProgram, "aPosition");
        int texLoc = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        int uniTex = GLES20.glGetUniformLocation(mProgram, "uTexture");

        mVertexBuffer.position(0);
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 16, mVertexBuffer);
        GLES20.glEnableVertexAttribArray(posLoc);

        mVertexBuffer.position(2);
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 16, mVertexBuffer);
        GLES20.glEnableVertexAttribArray(texLoc);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture);
        GLES20.glUniform1i(uniTex, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    // ─── Scaling ───────────────────────────────────────────────────────────────

    private void applyScaledViewport(int frameW, int frameH) {
        int vx = 0, vy = 0, vw = mViewWidth, vh = mViewHeight;

        switch (mScaleMode) {
            case SCALE_LETTERBOX: {
                float frameAspect = (float) frameW / frameH;
                float viewAspect  = (float) mViewWidth / mViewHeight;
                if (frameAspect > viewAspect) {
                    vh = (int) (mViewWidth / frameAspect);
                    vy = (mViewHeight - vh) / 2;
                } else {
                    vw = (int) (mViewHeight * frameAspect);
                    vx = (mViewWidth - vw) / 2;
                }
                break;
            }
            case SCALE_INTEGER: {
                int scale = Math.min(mViewWidth / frameW, mViewHeight / frameH);
                if (scale < 1) scale = 1;
                vw = frameW * scale;
                vh = frameH * scale;
                vx = (mViewWidth  - vw) / 2;
                vy = (mViewHeight - vh) / 2;
                break;
            }
            case SCALE_ASPECT_RATIO: {
                float viewAspect = (float) mViewWidth / mViewHeight;
                if (mAspectRatio > viewAspect) {
                    vh = (int) (mViewWidth / mAspectRatio);
                    vy = (mViewHeight - vh) / 2;
                } else {
                    vw = (int) (mViewHeight * mAspectRatio);
                    vx = (mViewWidth - vw) / 2;
                }
                break;
            }
            // SCALE_STRETCH: use full viewport (already set above)
        }
        GLES20.glViewport(vx, vy, vw, vh);
    }

    public void setScaleMode(int mode) { mScaleMode = mode; }
    public void setAspectRatio(float ar) { mAspectRatio = ar; }

    // ─── GL helpers ────────────────────────────────────────────────────────────

    private int buildProgram(String vertSrc, String fragSrc) {
        int vert = compileShader(GLES20.GL_VERTEX_SHADER,   vertSrc);
        int frag = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc);
        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vert);
        GLES20.glAttachShader(prog, frag);
        GLES20.glLinkProgram(prog);
        return prog;
    }

    private int compileShader(int type, String src) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            Log.e(TAG, "Shader compile error: " + GLES20.glGetShaderInfoLog(shader));
        }
        return shader;
    }

    private int createTexture() {
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return tex[0];
    }

    private static byte[] intsToBytes(int[] ints) {
        byte[] bytes = new byte[ints.length * 4];
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            bytes[i * 4]     = (byte) ((v >> 16) & 0xFF); // R
            bytes[i * 4 + 1] = (byte) ((v >>  8) & 0xFF); // G
            bytes[i * 4 + 2] = (byte) ( v        & 0xFF); // B
            bytes[i * 4 + 3] = (byte) ((v >> 24) & 0xFF); // A
        }
        return bytes;
    }
}
