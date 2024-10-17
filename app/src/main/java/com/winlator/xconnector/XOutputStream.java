package com.winlator.xconnector;

import com.winlator.xserver.XServer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.locks.ReentrantLock;

public class XOutputStream {
    private static final byte[] ZERO = new byte[64];
    private static final int MAX_BUFFER_SIZE = 16 * 1024 * 1024; // 16MB maximum buffer size
    private ByteBuffer buffer;
    public final ClientSocket clientSocket;
    private final ReentrantLock lock = new ReentrantLock();
    private int ancillaryFd = -1;

    public XOutputStream(int initialCapacity) {
        this(null, initialCapacity);
    }

    public XOutputStream(ClientSocket clientSocket, int initialCapacity) {
        this.clientSocket = clientSocket;
        buffer = ByteBuffer.allocateDirect(initialCapacity);
    }

    public void setByteOrder(ByteOrder byteOrder) {
        buffer.order(byteOrder);
    }

    public void setAncillaryFd(int ancillaryFd) {
        this.ancillaryFd = ancillaryFd;
    }

    public void writeByte(byte value) {
        ensureSpaceIsAvailable(1);
        buffer.put(value);
    }

    public void writeShort(short value) {
        ensureSpaceIsAvailable(2);
        buffer.putShort(value);
    }

    public void writeInt(int value) {
        ensureSpaceIsAvailable(4);
        buffer.putInt(value);
    }

    public void writeLong(long value) {
        ensureSpaceIsAvailable(8);
        buffer.putLong(value);
    }

    public void writeString8(String str) {
        byte[] bytes = str.getBytes(XServer.LATIN1_CHARSET);
        int length = -str.length() & 3;
        ensureSpaceIsAvailable(bytes.length + length);
        buffer.put(bytes);
        if (length > 0) writePad(length);
    }

    public void write(byte[] data) {
        write(data, 0, data.length);
    }

    public void write(byte[] data, int offset, int length) {
        ensureSpaceIsAvailable(length);
        buffer.put(data, offset, length);
    }

    public void write(ByteBuffer data) {
        ensureSpaceIsAvailable(data.remaining());
        buffer.put(data);
    }

    public void writePad(int length) {
        write(ZERO, 0, length);
    }

    private void flush() throws IOException {
        lock.lock();
        try {
            if (buffer.position() > 0) {
                buffer.flip();

                if (ancillaryFd != -1) {
                    clientSocket.sendAncillaryMsg(buffer, ancillaryFd);
                    ancillaryFd = -1;
                } else {
                    clientSocket.write(buffer);
                }

                buffer.clear();
            }
        } finally {
            lock.unlock();
        }
    }

    public XStreamLock lock() {
        return new OutputStreamLock();
    }

    private void ensureSpaceIsAvailable(int length) {
        int position = buffer.position();
        int requiredCapacity = position + length;

        // Check if buffer needs to be resized
        if (buffer.capacity() >= requiredCapacity) return;

        // Calculate new capacity ensuring it does not exceed MAX_BUFFER_SIZE
        int newCapacity = Math.min(Math.max(buffer.capacity() * 2, requiredCapacity), MAX_BUFFER_SIZE);

        // If already at max capacity, throw exception
        if (newCapacity > MAX_BUFFER_SIZE) {
            throw new IllegalStateException("Buffer size exceeded maximum allowed size of " + MAX_BUFFER_SIZE + " bytes.");
        }

        // Allocate new buffer and copy data
        ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity).order(buffer.order());
        buffer.flip(); // Prepare for reading
        newBuffer.put(buffer); // Copy data to new buffer
        buffer = newBuffer; // Replace old buffer with new one
    }

    private class OutputStreamLock implements XStreamLock {
        public OutputStreamLock() {
            lock.lock();
        }

        @Override
        public void close() throws IOException {
            try {
                flush();
            } finally {
                lock.unlock();
            }
        }
    }
}
