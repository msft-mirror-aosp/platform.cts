/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hyphonate.megaaudio.recorder;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.TimeUnit;

/**
 * A FIFO that spans Java and Native code. This can be used to pass float data from native code to
 * Java and vice versa.
 *
 * <p>It uses a ByteBuffer to store the data. This allows large amounts of data to be read or
 * written without using JNI.
 */
public class JavaNativeFloatFifo {
    private static final String TAG = JavaNativeFloatFifo.class.getSimpleName();

    public static final int TO_NATIVE = 0;
    public static final int FROM_NATIVE = 1;
    private final FloatBuffer mFloatBuffer;
    private final ByteBuffer mByteBuffer;
    private long mNativeToken;
    private final int mCapacityInFloats;
    private static final long SLEEP_MILLIS = 10; // Sleep duration between checks

    public JavaNativeFloatFifo(int direction, int capacityInFloats) {
        mCapacityInFloats = capacityInFloats;

        if (direction != TO_NATIVE && direction != FROM_NATIVE) {
            throw new IllegalArgumentException("Illegal direction = " + direction);
        }
        int capacityInBytes = mCapacityInFloats * Float.BYTES;
        mByteBuffer = ByteBuffer.allocateDirect(capacityInBytes);
        if (!mByteBuffer.isDirect()) {
            throw new RuntimeException("Allocated ByteBuffer is not Direct!");
        }
        // Use native order so native code can transfer data easily.
        mByteBuffer.order(ByteOrder.nativeOrder());
        // Use a FloatBuffer to convert bytes to float.
        if (direction == FROM_NATIVE) {
            mFloatBuffer = mByteBuffer.asFloatBuffer().asReadOnlyBuffer();
        } else {
            mFloatBuffer = mByteBuffer.asFloatBuffer();
        }
        mNativeToken = createNativeToken(mByteBuffer);
        if (mNativeToken == 0) {
            throw new RuntimeException("ByteBuffer has no native direct buffer!");
        }
    }

    /**
     * Delete the native resources created by the constructor. This object cannot be used again
     * after calling this method.
     */
    public void release() {
        if (mNativeToken != 0) {
            deleteNativeToken(mNativeToken);
            mNativeToken = 0;
        }
    }

    /**
     * Gets the capacity of the FIFO in floats.
     *
     * @return capacity in floats
     */
    public int getCapacity() {
        return mCapacityInFloats;
    }

    /**
     * @return 64-bit pointer to native object
     */
    public long getNativeToken() {
        return mNativeToken;
    }

    private native long createNativeToken(ByteBuffer byteBuffer);

    private native void deleteNativeToken(long token);

    private native long getReadCounter(long token);

    private native long getWriteCounter(long token);

    private native void setReadCounter(long token, long count);

    private native void setWriteCounter(long token, long count);

    public int getAvailableToWrite() {
        return getCapacity() - getAvailableToRead();
    }

    /**
     * @return number of floats available to read.
     */
    public int getAvailableToRead() {
        long readCounter = getReadCounter(mNativeToken);
        long writeCounter = getWriteCounter(mNativeToken);
        return (int) (writeCounter - readCounter);
    }

    /**
     * Writes a block of float data into the FIFO.
     *
     * @param data The float array containing the data to write.
     * @param offset The offset in the data array from which to start reading.
     * @param count The number of floats to write from the data array.
     * @return The number of floats actually written.
     */
    public int write(float[] data, int offset, int count) {
        if (count <= 0) {
            return 0;
        }

        // Get the number of floats available to write
        int availableToWrite = getAvailableToWrite();

        // Determine the actual number of floats to write (limited by available space)
        int floatsToWrite = Math.min(count, availableToWrite);

        if (floatsToWrite <= 0) {
            return 0; // No space to write
        }

        // Get the current write counter and calculate the cursor position in the buffer
        long writeCounter = getWriteCounter(mNativeToken);
        int cursor = (int) (writeCounter % mCapacityInFloats);

        // Check if the write will wrap around the buffer
        int floatsUntilWrap = mCapacityInFloats - cursor;

        if (floatsToWrite > floatsUntilWrap) {
            // Write in two parts: first part until the end of the buffer
            int floatsPart1 = floatsUntilWrap;
            mFloatBuffer.position(cursor); // Set buffer position for the first part
            mFloatBuffer.put(data, offset, floatsPart1); // Write the first part

            // Write the second part from the beginning of the buffer
            int floatsPart2 = floatsToWrite - floatsPart1;
            mFloatBuffer.position(0); // Set buffer position to the beginning
            mFloatBuffer.put(data, offset + floatsPart1, floatsPart2); // Write the second part

        } else {
            // Write in one shot
            mFloatBuffer.position(cursor); // Set buffer position for the entire block
            mFloatBuffer.put(data, offset, floatsToWrite); // Write the block
        }

        // Advance the native write counter
        setWriteCounter(mNativeToken, writeCounter + floatsToWrite);

        return floatsToWrite;
    }

    /**
     * Reads a block of float data from the FIFO.
     *
     * @param data The float array into which to read the data.
     * @param offset The offset in the data array where the read data should be placed.
     * @param count The number of floats to read into the data array.
     * @return The number of floats actually read.
     */
    public int read(float[] data, int offset, int count) {
        if (count <= 0) {
            return 0;
        }

        // Get the number of floats available to read
        int availableToRead = getAvailableToRead();

        // Determine the actual number of floats to read (limited by available data)
        int floatsToRead = Math.min(count, availableToRead);

        if (floatsToRead <= 0) {
            return 0; // No data to read
        }

        // Get the current read counter and calculate the cursor position in the buffer
        long readCounter = getReadCounter(mNativeToken);
        int cursor = (int) (readCounter % mCapacityInFloats);

        // Check if the read will wrap around the buffer
        int floatsUntilWrap = mCapacityInFloats - cursor;

        if (floatsToRead > floatsUntilWrap) {
            // Read in two parts: first part until the end of the buffer
            int floatsPart1 = floatsUntilWrap;
            mFloatBuffer.position(cursor); // Set buffer position for the first part
            mFloatBuffer.get(data, offset, floatsPart1); // Read the first part

            // Read the second part from the beginning of the buffer
            int floatsPart2 = floatsToRead - floatsPart1;
            mFloatBuffer.position(0); // Set buffer position to the beginning
            mFloatBuffer.get(data, offset + floatsPart1, floatsPart2); // Read the second part

        } else {
            // Read in one shot
            mFloatBuffer.position(cursor); // Set buffer position for the entire block
            mFloatBuffer.get(data, offset, floatsToRead); // Read the block
        }

        // Advance the native read counter
        setReadCounter(mNativeToken, readCounter + floatsToRead);

        return floatsToRead;
    }

    /**
     * Reads a block of float data from the FIFO, blocking until all data is available or a timeout
     * occurs. This implementation uses the non-blocking read() method. Use a default timeout of one
     * second.
     *
     * @param data The float array into which to read the data.
     * @param offset The offset in the data array where the read data should be placed.
     * @param count The number of floats to read into the data array.
     * @return The number of floats actually read. That will be less than count if a timeout occurs.
     */
    public int readBlocking(float[] data, int offset, int count) {
        return readBlocking(data, offset, count, 1000);
    }

    /**
     * Reads a block of float data from the FIFO, blocking until all data is available or a timeout
     * occurs. This implementation uses the non-blocking read() method.
     *
     * @param data The float array into which to read the data.
     * @param offset The offset in the data array where the read data should be placed.
     * @param count The number of floats to read into the data array.
     * @param timeoutMillis The maximum time to wait for the data, in milliseconds.
     * @return The number of floats actually read. That will be less than count if a timeout occurs.
     */
    public int readBlocking(float[] data, int offset, int count, int timeoutMillis) {
        if (count <= 0) {
            return 0;
        }

        int floatsToRead = count;
        int floatsRead = 0;
        long startTime = System.nanoTime();

        while (floatsRead < floatsToRead) {
            // Calculate how many floats are still needed
            int remainingToRead = floatsToRead - floatsRead;

            // Attempt to read the remaining data using the non-blocking read()
            int numReadThisIteration = read(data, offset + floatsRead, remainingToRead);

            if (numReadThisIteration > 0) {
                floatsRead += numReadThisIteration;
            }

            // If not all data is read, sleep for a short duration
            if (floatsRead < floatsToRead) {
                // Check for timeout
                long elapsedTime = System.nanoTime() - startTime;
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedTime);

                if (elapsedMillis >= timeoutMillis) {
                    // Timeout occurred
                    Log.e(TAG, "readBlocking() timed out!");
                    return floatsRead; // Indicate timeout error
                }

                try {
                    // Linter says we are supposed to use a Sleeper instead of Thread.sleep()
                    // But it does not seem to be available in Android.
                    // com.google.common.time.Sleeper.defaultSleeper().sleep(SLEEP_MILLIS);
                    // So just use Thread.sleep();
                    Thread.sleep(SLEEP_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupt flag!
                    return floatsRead; // Return the number of floats read so far
                }
            }
        }

        return floatsRead; // Return the total number of floats read
    }
}
