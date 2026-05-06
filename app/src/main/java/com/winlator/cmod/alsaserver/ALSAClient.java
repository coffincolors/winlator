package com.winlator.cmod.alsaserver;

import android.util.Log;

import com.winlator.cmod.sysvshm.SysVSharedMemory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ALSAClient {
    private static final String TAG = "ALSAClient";
    public enum DataType {
        U8(1), S16LE(2), S16BE(2), FLOATLE(4), FLOATBE(4);
        public final byte byteCount;

        DataType(int byteCount) {
            this.byteCount = (byte)byteCount;
        }
    }
    private DataType dataType = DataType.U8;
    private byte channelCount = 2;
    private int sampleRate = 0;
    private int position;
    private int bufferSize;
    private int frameBytes;
    private ByteBuffer sharedBuffer;
    private boolean playing = false;
    private long streamPtr = 0;
    private long mirrorStreamPtr = 0;

    private boolean reflectorMode;

    static {
        System.loadLibrary("winlator");
    }

    public void release() {
        if (sharedBuffer != null) {
            SysVSharedMemory.unmapSHMSegment(sharedBuffer, sharedBuffer.capacity());
            sharedBuffer = null;
        }

        if (reflectorMode) {
            simulatedStop(streamPtr);
            stop(mirrorStreamPtr);
            simulatedClose(streamPtr);
            close(mirrorStreamPtr);
        } else {
            stop(streamPtr);
            close(streamPtr);
        }

        playing = false;
        streamPtr = 0;
        mirrorStreamPtr = 0;
    }

    public void prepare() {
        position = 0;
        frameBytes = channelCount * dataType.byteCount;
        release();

        if (!isValidBufferSize()) return;

        if (reflectorMode) {
            // In reflector mode, create both the pacer and the real stream
            streamPtr = simulatedCreate(dataType.ordinal(), channelCount, sampleRate, bufferSize);
            mirrorStreamPtr = create(dataType.ordinal(), channelCount, sampleRate, bufferSize);
        } else {
            // In normal mode, create only the real stream and assign it to streamPtr
            streamPtr = create(dataType.ordinal(), channelCount, sampleRate, bufferSize);
            mirrorStreamPtr = 0; // Ensure mirror is null
        }

        if (streamPtr > 0) start();
    }

    public void start() {
        if (streamPtr > 0 && !playing) {
            if (reflectorMode) {
                simulatedStart(streamPtr);
                start(mirrorStreamPtr);
            } else {
                start(streamPtr);
            }
            playing = true;
        }
    }

    public void stop() {
        if (streamPtr > 0 && playing) {
            if (reflectorMode) {
                simulatedStop(streamPtr);
                stop(mirrorStreamPtr);
            } else {
                stop(streamPtr);
            }
            playing = false;
        }
    }

    public void pause() {
        if (streamPtr > 0) {
            if (reflectorMode) {
                simulatedPause(streamPtr);
                pause(mirrorStreamPtr);
            } else {
                pause(streamPtr);
            }
            playing = false;
        }
    }

    public void drain() {
        if (streamPtr > 0) {
            if (reflectorMode) {
                simulatedFlush(streamPtr);
                flush(mirrorStreamPtr);
            } else {
                flush(streamPtr);
            }
        }
    }

    public void writeDataToStream(ByteBuffer data) {
        ByteBuffer mirrorData = data.duplicate();

        if (dataType == DataType.S16LE || dataType == DataType.FLOATLE) {
            data.order(ByteOrder.LITTLE_ENDIAN);
            mirrorData.order(ByteOrder.LITTLE_ENDIAN);
        } else if (dataType == DataType.S16BE || dataType == DataType.FLOATBE) {
            data.order(ByteOrder.BIG_ENDIAN);
            mirrorData.order(ByteOrder.BIG_ENDIAN);
        }

        int numFrames = data.limit() / frameBytes;

        if (playing) {
            if (reflectorMode) {
                // Reflector Mode: Write to pacer, mirror to real stream
                int framesWritten = simulatedWrite(streamPtr, data, numFrames);
                write(mirrorStreamPtr, mirrorData, numFrames);
                if (framesWritten > 0) position += framesWritten;
                data.rewind();
                mirrorData.rewind();
            } else {
                // Normal Mode: Write directly to the real stream
                int framesWritten = write(streamPtr, data, numFrames);
                if (framesWritten > 0) position += framesWritten;
                data.rewind();
            }
        }
    }

    /**
     * This is the new method to handle audio device changes.
     * It will be called from the Android system when a device is connected or disconnected.
     */
    public void onAudioDeviceChanged() {
        // This entire feature is designed for reflector mode.
        // If we are in normal mode, the underlying connection to Wine is
        // likely broken, and we should do nothing.
        if (!reflectorMode) {
            Log.d(TAG, "Audio device change detected, but reflector mode is off. Ignoring.");
            return;
        }

        // --- REFLECTOR MODE LOGIC ---
        // The pacer (streamPtr) is safe. We only need to rebuild the hardware stream (mirrorStreamPtr).
        if (mirrorStreamPtr != 0) {
            Log.d(TAG, "Tearing down old playback stream...");
            stop(mirrorStreamPtr);
            close(mirrorStreamPtr);
            mirrorStreamPtr = 0;
        }

        final int MAX_RETRIES = 5;
        final int RETRY_DELAY_MS = 200;

        for (int i = 0; i < MAX_RETRIES; i++) {
            Log.d(TAG, "Attempting to rebuild playback stream (Attempt " + (i + 1) + ")");
            long newStreamPtr = create(dataType.ordinal(), channelCount, sampleRate, bufferSize);

            if (newStreamPtr > 0) {
                if (playing) {
                    int result = start(newStreamPtr);
                    if (result == 0) { // AAUDIO_OK
                        mirrorStreamPtr = newStreamPtr;
                        Log.d(TAG, "Successfully resumed playback on new stream.");
                        return;
                    } else {
                        Log.w(TAG, "Failed to start new stream, result code: " + result + ". Retrying...");
                        close(newStreamPtr);
                    }
                } else {
                    mirrorStreamPtr = newStreamPtr;
                    Log.d(TAG, "Successfully created new stream while paused.");
                    return;
                }
            }
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        Log.e(TAG, "Failed to rebuild playback stream after " + MAX_RETRIES + " attempts.");
    }

    public int pointer() {
        return position;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public void setChannelCount(int channelCount) {
        this.channelCount = (byte)channelCount;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public ByteBuffer getSharedBuffer() {
        return sharedBuffer;
    }

    public void setSharedBuffer(ByteBuffer sharedBuffer) {
        this.sharedBuffer = sharedBuffer;
    }

    public DataType getDataType() {
        return dataType;
    }

    public byte getChannelCount() {
        return channelCount;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getBufferSizeInBytes() {
        return bufferSize * frameBytes;
    }

    private boolean isValidBufferSize() {
        return (getBufferSizeInBytes() % frameBytes == 0) && bufferSize > 0;
    }

    public int computeLatencyMillis() {
        return (int)(((float)bufferSize / sampleRate) * 1000);
    }

    public void setReflectorMode(boolean enabled) {
        this.reflectorMode = enabled;
    }

    private native long simulatedCreate(int format, byte channelCount, int sampleRate, int bufferSize);
    private native long create(int format, byte channelCount, int sampleRate, int mirrorBufferSize);
    private native int simulatedWrite(long streamPtr, ByteBuffer buffer, int numberFrames);
    private native int write(long mirrorStreamPtr, ByteBuffer mirrorBuffer, int numFrames);
    private native void simulatedStart(long streamPtr);
    private native int start(long mirrorStreamPtr);

    private native void simulatedStop(long streamPtr);
    private native void stop(long mirrorStreamPtr);

    private native void simulatedPause(long streamPtr);
    private native void pause(long mirrorStreamPtr);

    private native void simulatedFlush(long streamPtr);
    private native void flush(long mirrorStreamPtr);

    private native void simulatedClose(long streamPtr);
    private native void close(long mirrorStreamPtr);
}