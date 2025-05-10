/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.cts.verifier.audio.analyzers;

import java.util.concurrent.atomic.AtomicInteger;

/** Pre-allocated buffer for float data. */
public class FloatRecording {

    private float[] mData;
    private AtomicInteger mWritten = new AtomicInteger();
    private int mMaxSamples;

    public FloatRecording(int maxSamples) {
        mMaxSamples = maxSamples;
        mData = new float[mMaxSamples];
    }

    /**
     * Get all the available stored data in an allocated float array.
     *
     * @return recorded data
     */
    public float[] readAll() {
        int numAvailable = getAvailable();
        float[] result = new float[numAvailable];
        // arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
        System.arraycopy(mData, 0, result, 0, numAvailable);
        return result;
    }

    /**
     * @param buffer source array for the data
     * @param position offset into the buffer
     * @param numValues number of values to write
     * @return number of values written
     */
    public int write(float[] buffer, int position, int numValues) {
        // Avoid overflowing the buffer,
        int numToWrite = Math.min(numValues, mMaxSamples - mWritten.get());
        if (numToWrite > 0) {
            // arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
            System.arraycopy(buffer, position, mData, mWritten.get(), numToWrite);
            mWritten.addAndGet(numToWrite);
        }
        return numToWrite;
    }

    /**
     * Get the number of samples that are available to read.
     *
     * @return total number of samples written
     */
    public int getAvailable() {
        return mWritten.get();
    }

    /** Erase the previously recorded data. */
    public void clear() {
        mWritten.set(0);
    }
}
