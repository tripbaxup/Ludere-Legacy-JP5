package com.ludere.legacy.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * AudioEngine
 *
 * Low-latency stereo audio output using AudioTrack (STREAM_MUSIC).
 * Receives 16-bit PCM sample batches from the libretro audio callback
 * and writes them to the AudioTrack buffer.
 *
 * Uses STREAM mode for lowest latency on API 17.
 * No AudioAttributes (API 21+). No AudioFocus handling.
 */
public class AudioEngine {

    private static final String TAG       = "AudioEngine";
    private static final int    SAMPLE_RATE = 44100;
    private static final int    CHANNELS    = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int    FORMAT      = AudioFormat.ENCODING_PCM_16BIT;

    private AudioTrack mAudioTrack;
    private boolean    mPaused = false;

    public AudioEngine() {
        int minBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNELS, FORMAT);
        // Use 2× minimum for stability
        int bufSize = Math.max(minBufSize * 2, 4096);

        mAudioTrack = new AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            CHANNELS,
            FORMAT,
            bufSize,
            AudioTrack.MODE_STREAM
        );

        if (mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            mAudioTrack.play();
            Log.i(TAG, "AudioTrack started. BufSize=" + bufSize);
        } else {
            Log.e(TAG, "AudioTrack failed to initialize");
        }
    }

    /**
     * Called from the libretro audio_sample_batch callback.
     * @param data   Interleaved L/R 16-bit PCM samples.
     * @param frames Number of stereo frames (data.length == frames * 2).
     */
    public void onAudioBatch(short[] data, int frames) {
        if (mPaused || mAudioTrack == null) return;
        if (mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) return;
        mAudioTrack.write(data, 0, frames * 2);
    }

    public void pause() {
        mPaused = true;
        if (mAudioTrack != null &&
            mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            mAudioTrack.pause();
        }
    }

    public void resume() {
        mPaused = false;
        if (mAudioTrack != null &&
            mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            mAudioTrack.play();
        }
    }

    public void release() {
        if (mAudioTrack != null) {
            try {
                mAudioTrack.stop();
                mAudioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioTrack", e);
            }
            mAudioTrack = null;
        }
    }
}
