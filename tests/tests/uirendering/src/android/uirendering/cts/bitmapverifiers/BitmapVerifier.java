/*
 * Copyright (C) 2014 The Android Open Source Project
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

/**
 * Checks to see if a Bitmap follows the algorithm provided by the verifier
 */
public abstract class BitmapVerifier {
    protected static final int PASS_COLOR = Color.WHITE;
    protected static final int FAIL_COLOR = Color.RED;

    protected Bitmap mDifferenceBitmap;

    /**
     * This will test if the bitmap is good or not.
     */
    public abstract boolean verify(int[] bitmap, int width, int height);

    /**
     * This calculates the position in an array that would represent a bitmap given the parameters.
     */
    protected static int indexFromXAndY(int x, int y, int width) {
        return x + (y * width);
    }

    public Bitmap getDifferenceBitmap() {
        return mDifferenceBitmap;
    }
}
