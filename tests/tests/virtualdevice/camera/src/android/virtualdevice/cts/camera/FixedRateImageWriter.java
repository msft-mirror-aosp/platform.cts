/*
 * Copyright 2024 The Android Open Source Project
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

package android.virtualdevice.cts.camera;

import static android.graphics.ImageFormat.YUV_420_888;

import android.media.Image;
import android.media.ImageWriter;
import android.util.Log;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * An Image writer which queue buffer into the provided Surface at a fixed rate.
 */
public class FixedRateImageWriter implements Consumer<Surface>, AutoCloseable {
    private static final String TAG = "FixedRateImageWriter";
    private static final boolean DEBUG = false;
    private static final long MILLIS_TO_NANOS = 1_000_000L;
    private final AtomicReference<ImageWriter> mImageWriter;
    private final Timer mTimer;
    private final long mInitialRenderedTimestampNanos;
    private final int mRenderingPeriodMillis;

    private final List<Long> mWrittenTimestamps = new ArrayList<>(10);

    /**
     * Creates a new image writer without starting the rendering thread.
     * <p>
     * The rendering thread will start once the surface is provided.
     *
     * @param initialRenderedTimestampNanos The initial timestamp that will be associated with the
     *                                      first queued buffer.
     * @param fps                           The rate a which the buffer will be queued in Frame Per
     *                                      Second.
     */
    public FixedRateImageWriter(long initialRenderedTimestampNanos, int fps) {
        mImageWriter = new AtomicReference<>(null);
        mTimer = new Timer();
        mInitialRenderedTimestampNanos = initialRenderedTimestampNanos;
        mRenderingPeriodMillis = Math.round(1000f / fps);
    }

    @Override
    public void accept(Surface surface) {
        mImageWriter.set(ImageWriter.newInstance(surface, 1, YUV_420_888));
        TimerTask task = new TimerTask() {

            long renderedTimestampNanos = mInitialRenderedTimestampNanos;

            @Override
            public void run() {
                ImageWriter imageWriter = mImageWriter.get();
                if (!surface.isValid() || imageWriter == null) {
                    cancel();
                    return;
                }

                try {
                    Image image = imageWriter.dequeueInputImage();
                    image.setTimestamp(renderedTimestampNanos);
                    if (DEBUG) {
                        Log.d(TAG, "queueInputImage with timestamp: " + renderedTimestampNanos);
                    }
                    imageWriter.queueInputImage(image);
                    mWrittenTimestamps.add(renderedTimestampNanos);
                    renderedTimestampNanos += mRenderingPeriodMillis * MILLIS_TO_NANOS;
                } catch (IllegalStateException ex) {
                    // Surface might have been disconnected because of a test
                    // timeout.
                }
            }
        };
        mTimer.scheduleAtFixedRate(task, 0, mRenderingPeriodMillis);
    }

    public List<Long> getWrittenTimestamps() {
        return mWrittenTimestamps;
    }

    @Override
    public void close() throws Exception {
        mTimer.cancel();
        ImageWriter imageWriter = mImageWriter.getAndSet(null);
        if (imageWriter != null) {
            imageWriter.close();
        }
    }
}
