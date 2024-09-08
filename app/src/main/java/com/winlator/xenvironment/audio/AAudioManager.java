package com.winlator.xenvironment.audio;

import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioFormat;
import android.media.AudioAttributes;
import android.util.Log;

public class AAudioManager {
    private AudioTrack audioTrack;
    private AudioManager audioManager;
    private float currentVolume = 1.0f; // Track current volume (1.0f is 100%)

    public AAudioManager(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    // Initialize and configure AudioTrack
    public void initializeAudioTrack() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(48000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build();

        int bufferSize = AudioTrack.getMinBufferSize(
                48000,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        Log.d("AAudioManager", "AudioTrack initialized.");
    }

    // Start playback
    public void startPlayback() {
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            audioTrack.play();
            Log.d("AAudioManager", "Audio playback started.");
        }
    }

    // Stop playback
    public void stopPlayback() {
        if (audioTrack != null) {
            audioTrack.stop();
            Log.d("AAudioManager", "Audio playback stopped.");
        }
    }

    // Release the AudioTrack when done
    public void releaseAudioTrack() {
        if (audioTrack != null) {
            audioTrack.release();
            Log.d("AAudioManager", "AudioTrack released.");
        }
    }

    // Lower the audio volume (ducking)
    public void lowerVolume() {
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            currentVolume = 0.5f;  // Lower volume to 50%
            audioTrack.setVolume(currentVolume);
            Log.d("AAudioManager", "Audio volume lowered to 50%.");
        }
    }

    // Reset volume to normal (after ducking)
    public void resetVolume() {
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            currentVolume = 1.0f;  // Reset volume to 100%
            audioTrack.setVolume(currentVolume);
            Log.d("AAudioManager", "Audio volume reset to 100%.");
        }
    }
}
