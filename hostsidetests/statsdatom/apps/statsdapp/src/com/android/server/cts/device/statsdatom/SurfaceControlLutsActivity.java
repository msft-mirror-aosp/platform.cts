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

package com.android.server.cts.device.statsdatom;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.HardwareBufferRenderer;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.hardware.DataSpace;
import android.hardware.DisplayLuts;
import android.hardware.HardwareBuffer;
import android.hardware.LutProperties;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class SurfaceControlLutsActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = "SurfaceControlLutsActivity";
    private static final int BUFFER_DIMENSION = 25;

    private FrameLayout mLayout;
    private SurfaceView mSurfaceView;
    private SurfaceControl mSurfaceControl;
    private RenderNode mRenderNode;
    private int mDataSpace;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        mSurfaceView = new SurfaceView(this);
        mSurfaceView.getHolder().addCallback(this);
        mSurfaceView.getHolder().setFixedSize(BUFFER_DIMENSION, BUFFER_DIMENSION);
        mLayout = new FrameLayout(this);
        mLayout.addView(mSurfaceView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        setContentView(mLayout);
        mSurfaceControl =
                new SurfaceControl.Builder()
                        .setParent(mSurfaceView.getSurfaceControl())
                        .setBufferSize(mSurfaceView.getWidth(), mSurfaceView.getHeight())
                        .setName("Luts")
                        .setHidden(false)
                        .build();
        mRenderNode = new RenderNode("SurfaceControlLutsActivityRenderNode");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated");
        mDataSpace = DataSpace.DATASPACE_BT2020_HLG;
        HardwareBuffer buffer =
                HardwareBuffer.create(
                        BUFFER_DIMENSION,
                        BUFFER_DIMENSION,
                        HardwareBuffer.RGBA_8888,
                        1,
                        HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
                                | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        DisplayLuts displayLuts = new DisplayLuts();
        DisplayLuts.Entry entry =
                new DisplayLuts.Entry(
                        new float[] {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f},
                        LutProperties.ONE_DIMENSION,
                        LutProperties.SAMPLING_KEY_MAX_RGB);
        displayLuts.set(entry);

        HardwareBufferRenderer renderer = new HardwareBufferRenderer(buffer);
        mRenderNode.setPosition(0, 0, buffer.getWidth(), buffer.getHeight());
        renderer.setContentRoot(mRenderNode);

        RecordingCanvas canvas = mRenderNode.beginRecording();
        canvas.drawColor(Color.CYAN);
        mRenderNode.endRecording();

        CountDownLatch latch = new CountDownLatch(1);
        renderer.obtainRenderRequest()
                .setColorSpace(ColorSpace.getFromDataSpace(mDataSpace))
                .draw(
                        Executors.newSingleThreadExecutor(),
                        renderResult -> {
                            new SurfaceControl.Transaction()
                                    .setBuffer(mSurfaceControl, buffer, renderResult.getFence())
                                    .setDataSpace(mSurfaceControl, mDataSpace)
                                    .setLuts(mSurfaceControl, displayLuts)
                                    .apply();
                            latch.countDown();
                        });
        try {
            latch.await();
        } catch (InterruptedException ex) {
            buffer.close();
            return;
        }

        buffer.close();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}
}
