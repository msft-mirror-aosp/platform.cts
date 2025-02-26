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

package com.android.server.cts.device.statsdatom;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.PixelFormat;
import android.hardware.DataSpace;
import android.media.Image;
import android.media.ImageWriter;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;


public class SurfaceViewActivity extends Activity implements SurfaceHolder.Callback {
    private static final int MAX_SRGB_FRAMES = 10;
    private static final int MAX_P3_FRAMES = 10;

    private FrameLayout mLayout;
    private SurfaceView mSurfaceView;
    private ImageWriter mWriter;
    private int mFrameCount;
    private int mDataSpace;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSurfaceView = new SurfaceView(this);
        mSurfaceView.getHolder().addCallback(this);
        mLayout = new FrameLayout(this);
        mLayout.addView(mSurfaceView,
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        setContentView(mLayout);
    }

    private void pushFrame() {
        if (mFrameCount >= MAX_SRGB_FRAMES + MAX_P3_FRAMES) {
            getMainExecutor().execute(() -> mLayout.removeView(mSurfaceView));
            return;
        }
        if (mWriter == null) {
            return;
        }
        if (mFrameCount >= MAX_SRGB_FRAMES) {
            mDataSpace = DataSpace.DATASPACE_DISPLAY_P3;
        }
        Image image = mWriter.dequeueInputImage();
        image.setDataSpace(mDataSpace);
        Image.Plane plane = image.getPlanes()[0];
        Bitmap bitmap = Bitmap.createBitmap(plane.getRowStride() / 4, image.getHeight(),
                Bitmap.Config.ARGB_8888, true, ColorSpace.getFromDataSpace(mDataSpace));
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.RED);
        bitmap.copyPixelsToBuffer(plane.getBuffer());
        mWriter.queueInputImage(image);
        mFrameCount += 1;
        pushFrame();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mWriter = new ImageWriter
                        .Builder(holder.getSurface())
                        .setHardwareBufferFormat(PixelFormat.RGBA_8888)
                        .setDataSpace(mDataSpace)
                        .build();
        pushFrame();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mDataSpace = DataSpace.DATASPACE_SRGB;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mWriter = null;
    }

}
