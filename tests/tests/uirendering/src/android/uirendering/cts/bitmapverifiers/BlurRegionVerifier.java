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

package android.uirendering.cts.bitmapverifiers;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.uirendering.cts.util.CompareUtils;

import org.junit.Assert;

/** Verifies that a bitmap has an expected surfaceView blurred region. */
public class BlurRegionVerifier extends BitmapVerifier {
    private static final String TAG = "BlurRegionVerifier";

    private final Rect mBlurRegion;
    private final int mBlurRadius;
    private final int mColorTolerance;

    public BlurRegionVerifier(Rect blurRegion, int blurRadius, int colorTolerance) {
        mBlurRegion = blurRegion;
        mBlurRadius = blurRadius;
        mColorTolerance = colorTolerance;
    }

    @Override
    public boolean verify(Bitmap bitmap) {
        boolean success = true;
        final double midX = (mBlurRegion.left + mBlurRegion.right - 1) / 2.0;

        // At 2 * radius there should be no visible blur effects.
        final int unaffectedBluePixelX = (int) Math.floor(midX) - mBlurRadius * 2 - 1;
        final int unaffectedRedPixelX = (int) Math.ceil(midX) + mBlurRadius * 2 + 1;

        // Check a smaller part of the blurred area than strictly necessary, in order to accept
        // various blur algorithm approximations used in RenderEngine
        final int blurAreaStartX =
                Math.max(mBlurRegion.left, (int) Math.floor(midX - mBlurRadius * 0.75f));
        final int blurAreaEndX =
                Math.min(mBlurRegion.right - 1, (int) Math.ceil(midX + mBlurRadius * 0.75f));

        // Check only a limited number of samples per row, instead of every pixel, in order to
        // tolerate approximations other than a full numerically-stable Gaussian kernel.
        final int samples = 6;

        for (int y = mBlurRegion.top; y < mBlurRegion.bottom; y++) {
            Color previousColor = null;
            for (int i = 0; i <= samples; i++) {
                final int x = blurAreaStartX + (i * (blurAreaEndX - blurAreaStartX)) / samples;
                final Color currentColor = bitmap.getColor(x, y);

                if (previousColor != null
                        && !(previousColor.blue() > currentColor.blue()
                                && previousColor.red() < currentColor.red())) {
                    success = false;
                }
                previousColor = currentColor;
            }
        }

        for (int y = mBlurRegion.top; y < mBlurRegion.bottom; y++) {
            final int unaffectedBluePixel = bitmap.getPixel(unaffectedBluePixelX, y);
            if (!CompareUtils.verifyPixelWithThreshold(
                    unaffectedBluePixel, Color.BLUE, mColorTolerance)) {
                success = false;
            }
            final int unaffectedRedPixel = bitmap.getPixel(unaffectedRedPixelX, y);
            if (!CompareUtils.verifyPixelWithThreshold(
                    unaffectedRedPixel, Color.RED, mColorTolerance)) {
                success = false;
            }
        }
        return success;
    }

    @Override
    public boolean verify(int[] bitmap, int offset, int stride, int width, int height) {
        Assert.fail();
        return false;
    }
}
