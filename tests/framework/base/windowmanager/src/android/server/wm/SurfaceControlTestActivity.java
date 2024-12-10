/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.server.wm;

import static android.view.WindowInsets.Type.systemBars;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.PointerIcon;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.cts.surfacevalidator.PixelColor;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SurfaceControlTestActivity extends Activity {
    private static final String TAG = "SurfaceControlTestActivity";
    private static final boolean DEBUG = true;

    private static final int DEFAULT_LAYOUT_WIDTH = 100;
    private static final int DEFAULT_LAYOUT_HEIGHT = 100;
    private static final int OFFSET_X = 100;
    private static final int OFFSET_Y = 100;
    private static final long WAIT_TIMEOUT_S = 5;

    private SurfaceView mSurfaceView;
    private final FrameLayout.LayoutParams mLayoutParams = new FrameLayout.LayoutParams(
            DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,Gravity.LEFT | Gravity.TOP);

    private Instrumentation mInstrumentation;

    private final CountDownLatch mSurfaceChanged = new CountDownLatch(1);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Window window = getWindow();
        window.getDecorView().setSystemUiVisibility(
               View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        // Set the NULL pointer icon so that it won't obstruct the captured image.
        window.getDecorView().setPointerIcon(
                PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL));
        window.setDecorFitsSystemWindows(false);
        mLayoutParams.topMargin = OFFSET_Y;
        mLayoutParams.leftMargin = OFFSET_X;
        mSurfaceView = new SurfaceView(this);
        mSurfaceView.getHolder().setFixedSize(DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT);

        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                // Ignore
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                    int height) {
                // This gets called when views are added to surface
                mSurfaceChanged.countDown();
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                // Ignore
            }
        });
        FrameLayout parent = findViewById(android.R.id.content);
        parent.addView(mSurfaceView, mLayoutParams);
        mInstrumentation = getInstrumentation();
    }


    public SurfaceControl awaitSurfaceControl() throws InterruptedException {
        if (mSurfaceChanged.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS)) {
            return mSurfaceView.getSurfaceControl();
        } else return null;
    }

    public Bitmap screenShot() {
        return mInstrumentation.getUiAutomation().takeScreenshot(getWindow());
    }


    public abstract static class MultiRectChecker extends RectChecker {
        public MultiRectChecker(Rect boundsToCheck) {
            super(boundsToCheck);
        }

        public abstract PixelColor getExpectedColor(int x, int y);
    }

    public static class RectChecker extends PixelChecker {
        private final Rect mBoundsToCheck;

        public RectChecker(Rect boundsToCheck) {
            super();
            mBoundsToCheck = boundsToCheck;
        }

        public RectChecker(Rect boundsToCheck, int expectedColor) {
            super(expectedColor);
            mBoundsToCheck = boundsToCheck;
        }

        public boolean checkPixels(int matchingPixelCount, int width, int height) {
            int expectedPixelCountMin = mBoundsToCheck.width() * mBoundsToCheck.height() - 100;
            int expectedPixelCountMax = mBoundsToCheck.width() * mBoundsToCheck.height();
            return matchingPixelCount > expectedPixelCountMin
                    && matchingPixelCount <= expectedPixelCountMax;
        }

        @Override
        public Rect getBoundsToCheck(Bitmap bitmap) {
            return mBoundsToCheck;
        }
    }

    public abstract static class PixelChecker {
        private final PixelColor mPixelColor;
        private final boolean mLogWhenNoMatch;

        public PixelChecker() {
            this(Color.BLACK, true);
        }

        public PixelChecker(int color) {
            this(color, true);
        }

        public PixelChecker(int color, boolean logWhenNoMatch) {
            mPixelColor = new PixelColor(color);
            mLogWhenNoMatch = logWhenNoMatch;
        }

        public int getNumMatchingPixels(Bitmap bitmap, Window window) {
            int numMatchingPixels = 0;
            int numErrorsLogged = 0;
            Insets insets = window.getDecorView().getRootWindowInsets().getInsets(systemBars());
            int offsetX = OFFSET_X + insets.left;
            int offsetY = OFFSET_Y + insets.top;
            Rect boundsToCheck = getBoundsToCheck(bitmap);
            for (int x = boundsToCheck.left; x < boundsToCheck.right; x++) {
                for (int y = boundsToCheck.top; y < boundsToCheck.bottom; y++) {
                    int color = bitmap.getPixel(x + offsetX, y + offsetY);
                    if (matchesColor(getExpectedColor(x, y), color)) {
                        numMatchingPixels++;
                    } else if (DEBUG && mLogWhenNoMatch && numErrorsLogged < 100) {
                        // We don't want to spam the logcat with errors if something is really
                        // broken. Only log the first 100 errors.
                        PixelColor expected = getExpectedColor(x, y);
                        int expectedColor = Color.argb(expected.mAlpha, expected.mRed,
                                expected.mGreen, expected.mBlue);
                        Log.e(TAG, String.format(Locale.ENGLISH,
                                "Failed to match (%d, %d) color=0x%08X expected=0x%08X", x, y,
                                color, expectedColor));
                        numErrorsLogged++;
                    }
                }
            }
            return numMatchingPixels;
        }

        private boolean matchesColor(PixelColor expectedColor, int color) {
            final float red = Color.red(color);
            final float green = Color.green(color);
            final float blue = Color.blue(color);
            final float alpha = Color.alpha(color);

            return alpha <= expectedColor.mMaxAlpha
                    && alpha >= expectedColor.mMinAlpha
                    && red <= expectedColor.mMaxRed
                    && red >= expectedColor.mMinRed
                    && green <= expectedColor.mMaxGreen
                    && green >= expectedColor.mMinGreen
                    && blue <= expectedColor.mMaxBlue
                    && blue >= expectedColor.mMinBlue;
        }

        public abstract boolean checkPixels(int matchingPixelCount, int width, int height);

        public Rect getBoundsToCheck(Bitmap bitmap) {
            return new Rect(1, 1, DEFAULT_LAYOUT_WIDTH - 1, DEFAULT_LAYOUT_HEIGHT - 1);
        }

        public PixelColor getExpectedColor(int x, int y) {
            return mPixelColor;
        }
    }
}
