/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.view.cts.surfacevalidator;

import android.annotation.ColorInt;
import android.graphics.Color;

public class PixelColor {
    private static final short BASE_TOLERANCE = 4;
    private static final short ENLARGE_TOLERANCE = 17;
    public static final int TRANSLUCENT_RED = 0x7FFF0000;

    private final short mTolerance;
    // Default to black
    public short mMinAlpha;
    public short mMaxAlpha;
    public short mMinRed;
    public short mMaxRed;
    public short mMinBlue;
    public short mMaxBlue;
    public short mMinGreen;
    public short mMaxGreen;

    public short mAlpha;
    public short mRed;
    public short mGreen;
    public short mBlue;

    public PixelColor(@ColorInt int color) {
        this(color, false /* enlargeTolerance */);
    }

    public PixelColor() {
        this(Color.BLACK);
    }

    /**
     * @param enlargeTolerance Whether to enlarging the tolerance when matching colors. This can be
     *     useful if the source color is encoded in a format below 8888-bit, as it might exhibit
     *     greater distortion when upscaled.
     */
    public PixelColor(@ColorInt int color, boolean enlargeTolerance) {
        mAlpha = (short) ((color >> 24) & 0xFF);
        mRed = (short) ((color >> 16) & 0xFF);
        mGreen = (short) ((color >> 8) & 0xFF);
        mBlue = (short) (color & 0xFF);

        mTolerance = enlargeTolerance ? ENLARGE_TOLERANCE : BASE_TOLERANCE;
        mMinAlpha = (short) getMinValue(mAlpha);
        mMaxAlpha = (short) getMaxValue(mAlpha);
        mMinRed = (short) getMinValue(mRed);
        mMaxRed = (short) getMaxValue(mRed);
        mMinBlue = (short) getMinValue(mBlue);
        mMaxBlue = (short) getMaxValue(mBlue);
        mMinGreen = (short) getMinValue(mGreen);
        mMaxGreen = (short) getMaxValue(mGreen);
    }

    private int getMinValue(short color) {
        return Math.max(color - mTolerance, 0);
    }

    private int getMaxValue(short color) {
        return Math.min(color + mTolerance, 0xFF);
    }

    public boolean matchesColor(int color) {
        final float red = Color.red(color);
        final float green = Color.green(color);
        final float blue = Color.blue(color);
        final float alpha = Color.alpha(color);

        return alpha <= mMaxAlpha
                && alpha >= mMinAlpha
                && red <= mMaxRed
                && red >= mMinRed
                && green <= mMaxGreen
                && green >= mMinGreen
                && blue <= mMaxBlue
                && blue >= mMinBlue;
    }

}
