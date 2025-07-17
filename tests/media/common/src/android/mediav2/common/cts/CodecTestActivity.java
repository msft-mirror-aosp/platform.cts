/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CodecTestActivity extends Activity implements SurfaceHolder.Callback {
    private static final String LOG_TAG = CodecTestActivity.class.getSimpleName();
    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private Surface mSurface;
    private boolean mSurfaceChanged = false;
    private final Lock mLock = new ReentrantLock();
    private final Condition mCondition = mLock.newCondition();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTurnScreenOn(true);
        setShowWhenLocked(true);

        setContentView(R.layout.media_decoder_surface_layout);
        mSurfaceView = findViewById(R.id.surface);
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        Log.v(LOG_TAG, "surface created");
        mLock.lock();
        try {
            mSurface = mHolder.getSurface();
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        Log.v(LOG_TAG, "surface changed " + format + " " + width + " " + height);
        mLock.lock();
        try {
            mSurfaceChanged = true;
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        Log.v(LOG_TAG, "surface deleted");
        mLock.lock();
        try {
            mSurface = null;
        } finally {
            mLock.unlock();
        }
    }

    public void waitTillSurfaceIsCreated() throws InterruptedException {
        final long mWaitTimeMs = 1000;
        final int retries = 3;
        mLock.lock();
        try {
            final long start = SystemClock.elapsedRealtime();
            while ((SystemClock.elapsedRealtime() - start) < (retries * mWaitTimeMs)
                    && mSurface == null) {
                mCondition.await(mWaitTimeMs, TimeUnit.MILLISECONDS);
            }
        } finally {
            mLock.unlock();
        }
        if (mSurface == null) {
            throw new InterruptedException("Taking too long to attach a SurfaceView to a window.");
        }
    }

    public void waitTillSurfaceIsChanged() throws InterruptedException {
        final long mWaitTimeMs = 1000;
        final int retries = 3;
        mLock.lock();
        try {
            final long start = SystemClock.elapsedRealtime();
            while ((SystemClock.elapsedRealtime() - start) < (retries * mWaitTimeMs)
                    && !mSurfaceChanged) {
                mCondition.await(mWaitTimeMs, TimeUnit.MILLISECONDS);
            }
        } finally {
            mLock.unlock();
        }
        if (!mSurfaceChanged) {
            throw new InterruptedException("Taking too long to receive surfaceChanged callback.");
        }
    }

    public void resetSurfaceChanged() {
        mLock.lock();
        try {
            mSurfaceChanged = false;
        } finally {
            mLock.unlock();
        }
    }

    public Surface getSurface() {
        return mSurface;
    }

    public SurfaceView getSurfaceView() {
        return mSurfaceView;
    }

    public void setScreenParams(int width, int height, boolean noStretch) {
        ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        if (noStretch && width <= dm.widthPixels && height <= dm.heightPixels) {
            lp.width = width;
            lp.height = height;
        } else {
            int a = dm.widthPixels * height / width;
            if (a <= dm.heightPixels) {
                lp.width = dm.widthPixels;
                lp.height = a;
            } else {
                lp.width = dm.heightPixels * width / height;
                lp.height = dm.heightPixels;
            }
        }
        runOnUiThread(() -> mSurfaceView.setLayoutParams(lp));
    }
}
