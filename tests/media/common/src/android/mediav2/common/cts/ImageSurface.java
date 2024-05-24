/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.mediav2.common.cts;

import static org.junit.Assert.assertTrue;

import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Wrapper class to hold surface provided by ImageReader.
 */
public class ImageSurface implements ImageReader.OnImageAvailableListener {
    private static final String LOG_TAG = ImageSurface.class.getSimpleName();

    private final ArrayDeque<Image> mQueue = new ArrayDeque<>();
    private int mOnImageAvailableCount = 0;
    private final Lock mLock = new ReentrantLock();
    private final Condition mCondition = mLock.newCondition();
    private ImageReader mReader;
    private Surface mReaderSurface;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private int mImageBoundToSurfaceId;
    private Function<ImageAndAttributes, Boolean> mPredicate;

    public static class ImageAndAttributes {
        public Image mImage;
        public int mImageBoundToSurfaceId;

        public ImageAndAttributes(Image image, int surfaceId) {
            mImage = image;
            mImageBoundToSurfaceId = surfaceId;
        }
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        mLock.lock();
        try {
            mOnImageAvailableCount++;
            mCondition.signalAll();
        } finally {
            mLock.unlock();
        }
    }

    public Image getImage(long timeout) throws InterruptedException {
        int retry = 3;
        Image image = null;
        mLock.lock();
        try {
            while (mOnImageAvailableCount <= 0 && retry > 0) {
                if (!mCondition.await(timeout, TimeUnit.MILLISECONDS)) {
                    retry--;
                }
            }
            if (mQueue.size() < mReader.getMaxImages() && mOnImageAvailableCount > 0) {
                Image acquiredImage = mReader.acquireNextImage();
                Log.d(LOG_TAG, "received image" + acquiredImage);
                mQueue.add(acquiredImage);
                mOnImageAvailableCount--;
            }
            if (mQueue.size() > 0) {
                image = mQueue.peekFirst();
            }
        } finally {
            mLock.unlock();
        }
        return image;
    }

    public void pop() {
        mLock.lock();
        try {
            mQueue.pop();
        } finally {
            mLock.unlock();
        }
    }

    public void createSurface(int width, int height, int format, int maxNumImages,
            int surfaceId, Function<ImageAndAttributes, Boolean> predicate) {
        if (mReader != null) {
            throw new RuntimeException(
                    "Current instance of ImageSurface already has a weak reference to some "
                            + "surface, release older surface or reuse it");
        }
        mHandlerThread = new HandlerThread(LOG_TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mReader = ImageReader.newInstance(width, height, format, maxNumImages);
        mReader.setOnImageAvailableListener(this, mHandler);
        mReaderSurface = mReader.getSurface();
        mImageBoundToSurfaceId = surfaceId;
        mPredicate = predicate;
        Log.v(LOG_TAG, String.format(Locale.getDefault(), "Created ImageReader size (%dx%d),"
                + " format %d, maxNumImages %d", width, height, format, maxNumImages));
    }

    public Surface getSurface() {
        return mReaderSurface;
    }

    public int getAvailableImageCount() {
        int count;
        mLock.lock();
        try {
            count = mQueue.size() + mOnImageAvailableCount;
        } finally {
            mLock.unlock();
        }
        return count;
    }

    private void releaseImage(Image image) {
        if (mPredicate != null) {
            assertTrue("predicate failed on image instance",
                    mPredicate.apply(new ImageAndAttributes(image, mImageBoundToSurfaceId)));
        }
        image.close();
    }

    public void release() {
        mReaderSurface = null;
        if (mReader != null) {
            mLock.lock();
            try {
                mQueue.forEach(this::releaseImage);
                mQueue.clear();
            } finally {
                mReader.close();
                mReader = null;
                mLock.unlock();
            }
        }
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
        }
        mHandler = null;
    }
}
