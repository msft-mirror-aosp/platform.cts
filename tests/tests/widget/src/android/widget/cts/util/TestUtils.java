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

package android.widget.cts.util;

import android.annotation.ColorInt;
import android.annotation.NonNull;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.View;
import android.view.ViewParent;
import junit.framework.Assert;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.argThat;

public class TestUtils {
    /**
     * This method takes a view and returns a single bitmap that is the layered combination
     * of background drawables of this view and all its ancestors. It can be used to abstract
     * away the specific implementation of a view hierarchy that is not exposed via class APIs
     * or a view hierarchy that depends on the platform version. Instead of hard-coded lookups
     * of particular inner implementations of such a view hierarchy that can break during
     * refactoring or on newer platform versions, calling this API returns a "combined" background
     * of the view.
     *
     * For example, it is useful to get the combined background of a popup / dropdown without
     * delving into the inner implementation details of how that popup is implemented on a
     * particular platform version.
     */
    public static Bitmap getCombinedBackgroundBitmap(View view) {
        final int bitmapWidth = view.getWidth();
        final int bitmapHeight = view.getHeight();

        // Create a bitmap
        final Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight,
                Bitmap.Config.ARGB_8888);
        // Create a canvas that wraps the bitmap
        final Canvas canvas = new Canvas(bitmap);

        // As the draw pass starts at the top of view hierarchy, our first step is to traverse
        // the ancestor hierarchy of our view and collect a list of all ancestors with non-null
        // and visible backgrounds. At each step we're keeping track of the combined offsets
        // so that we can properly combine all of the visuals together in the next pass.
        List<View> ancestorsWithBackgrounds = new ArrayList<>();
        List<Pair<Integer, Integer>> ancestorOffsets = new ArrayList<>();
        int offsetX = 0;
        int offsetY = 0;
        while (true) {
            final Drawable backgroundDrawable = view.getBackground();
            if ((backgroundDrawable != null) && backgroundDrawable.isVisible()) {
                ancestorsWithBackgrounds.add(view);
                ancestorOffsets.add(Pair.create(offsetX, offsetY));
            }
            // Go to the parent
            ViewParent parent = view.getParent();
            if (!(parent instanceof View)) {
                // We're done traversing the ancestor chain
                break;
            }

            // Update the offsets based on the location of current view in its parent's bounds
            offsetX += view.getLeft();
            offsetY += view.getTop();

            view = (View) parent;
        }

        // Now we're going to iterate over the collected ancestors in reverse order (starting from
        // the topmost ancestor) and draw their backgrounds into our combined bitmap. At each step
        // we are respecting the offsets of our original view in the coordinate system of the
        // currently drawn ancestor.
        final int layerCount = ancestorsWithBackgrounds.size();
        for (int i = layerCount - 1; i >= 0; i--) {
            View ancestor = ancestorsWithBackgrounds.get(i);
            Pair<Integer, Integer> offsets = ancestorOffsets.get(i);

            canvas.translate(offsets.first, offsets.second);
            ancestor.getBackground().draw(canvas);
            canvas.translate(-offsets.first, -offsets.second);
        }

        return bitmap;
    }

    /**
     * Checks whether all the pixels in the specified of the {@link View} are
     * filled with the specific color.
     *
     * In case there is a color mismatch, the behavior of this method depends on the
     * <code>throwExceptionIfFails</code> parameter. If it is <code>true</code>, this method will
     * throw an <code>Exception</code> describing the mismatch. Otherwise this method will call
     * <code>Assert.fail</code> with detailed description of the mismatch.
     */
    public static void assertAllPixelsOfColor(String failMessagePrefix, @NonNull View view,
            @ColorInt int color, int allowedComponentVariance, boolean throwExceptionIfFails) {
        assertRegionPixelsOfColor(failMessagePrefix, view,
                new Rect(0, 0, view.getWidth(), view.getHeight()),
                color, allowedComponentVariance, throwExceptionIfFails);
    }

    /**
     * Checks whether all the pixels in the specific rectangular region of the {@link View} are
     * filled with the specific color.
     *
     * In case there is a color mismatch, the behavior of this method depends on the
     * <code>throwExceptionIfFails</code> parameter. If it is <code>true</code>, this method will
     * throw an <code>Exception</code> describing the mismatch. Otherwise this method will call
     * <code>Assert.fail</code> with detailed description of the mismatch.
     */
    public static void assertRegionPixelsOfColor(String failMessagePrefix, @NonNull View view,
            @NonNull Rect region, @ColorInt int color, int allowedComponentVariance,
            boolean throwExceptionIfFails) {
        // Create a bitmap
        final int viewWidth = view.getWidth();
        final int viewHeight = view.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                Bitmap.Config.ARGB_8888);
        // Create a canvas that wraps the bitmap
        Canvas canvas = new Canvas(bitmap);
        // And ask the view to draw itself to the canvas / bitmap
        view.draw(canvas);

        try {
            assertAllPixelsOfColor(failMessagePrefix, bitmap, region,
                    color, allowedComponentVariance, throwExceptionIfFails);
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * Checks whether all the pixels in the specified {@link Drawable} are filled with the specific
     * color.
     *
     * In case there is a color mismatch, the behavior of this method depends on the
     * <code>throwExceptionIfFails</code> parameter. If it is <code>true</code>, this method will
     * throw an <code>Exception</code> describing the mismatch. Otherwise this method will call
     * <code>Assert.fail</code> with detailed description of the mismatch.
     */
    public static void assertAllPixelsOfColor(String failMessagePrefix, @NonNull Drawable drawable,
            int drawableWidth, int drawableHeight, boolean callSetBounds, @ColorInt int color,
            int allowedComponentVariance, boolean throwExceptionIfFails) {
        // Create a bitmap
        Bitmap bitmap = Bitmap.createBitmap(drawableWidth, drawableHeight,
                Bitmap.Config.ARGB_8888);
        // Create a canvas that wraps the bitmap
        Canvas canvas = new Canvas(bitmap);
        if (callSetBounds) {
            // Configure the drawable to have bounds that match the passed size
            drawable.setBounds(0, 0, drawableWidth, drawableHeight);
        } else {
            // Query the current bounds of the drawable for translation
            Rect drawableBounds = drawable.getBounds();
            canvas.translate(-drawableBounds.left, -drawableBounds.top);
        }
        // And ask the drawable to draw itself to the canvas / bitmap
        drawable.draw(canvas);

        try {
            assertAllPixelsOfColor(failMessagePrefix, bitmap,
                    new Rect(0, 0, drawableWidth, drawableHeight), color,
                    allowedComponentVariance, throwExceptionIfFails);
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * Checks whether all the pixels in the specific rectangular region of the bitmap are filled
     * with the specific color.
     *
     * In case there is a color mismatch, the behavior of this method depends on the
     * <code>throwExceptionIfFails</code> parameter. If it is <code>true</code>, this method will
     * throw an <code>Exception</code> describing the mismatch. Otherwise this method will call
     * <code>Assert.fail</code> with detailed description of the mismatch.
     */
    private static void assertAllPixelsOfColor(String failMessagePrefix, @NonNull Bitmap bitmap,
            @NonNull Rect region, @ColorInt int color, int allowedComponentVariance,
            boolean throwExceptionIfFails) {
        final int bitmapWidth = bitmap.getWidth();
        final int bitmapHeight = bitmap.getHeight();
        final int[] rowPixels = new int[bitmapWidth];

        final int startRow = region.top;
        final int endRow = region.bottom;
        final int startColumn = region.left;
        final int endColumn = region.right;

        for (int row = startRow; row < endRow; row++) {
            bitmap.getPixels(rowPixels, 0, bitmapWidth, 0, row, bitmapWidth, 1);
            for (int column = startColumn; column < endColumn; column++) {
                @ColorInt int colorAtCurrPixel = rowPixels[column];
                if (!areColorsTheSameWithTolerance(color, colorAtCurrPixel,
                        allowedComponentVariance)) {
                    String mismatchDescription = failMessagePrefix
                            + ": expected all bitmap colors in rectangle [l="
                            + startColumn + ", t=" + startRow + ", r=" + endColumn
                            + ", b=" + endRow + "] to be " + formatColorToHex(color)
                            + " but at position (" + row + "," + column + ") out of ("
                            + bitmapWidth + "," + bitmapHeight + ") found "
                            + formatColorToHex(colorAtCurrPixel);
                    if (throwExceptionIfFails) {
                        throw new RuntimeException(mismatchDescription);
                    } else {
                        Assert.fail(mismatchDescription);
                    }
                }
            }
        }
    }

    /**
     * Checks whether the center pixel in the specified bitmap is of the same specified color.
     *
     * In case there is a color mismatch, the behavior of this method depends on the
     * <code>throwExceptionIfFails</code> parameter. If it is <code>true</code>, this method will
     * throw an <code>Exception</code> describing the mismatch. Otherwise this method will call
     * <code>Assert.fail</code> with detailed description of the mismatch.
     */
    public static void assertCenterPixelOfColor(String failMessagePrefix, @NonNull Bitmap bitmap,
            @ColorInt int color,
            int allowedComponentVariance, boolean throwExceptionIfFails) {
        final int centerX = bitmap.getWidth() / 2;
        final int centerY = bitmap.getHeight() / 2;
        final @ColorInt int colorAtCenterPixel = bitmap.getPixel(centerX, centerY);
        if (!areColorsTheSameWithTolerance(color, colorAtCenterPixel,
                allowedComponentVariance)) {
            String mismatchDescription = failMessagePrefix
                    + ": expected all drawable colors to be "
                    + formatColorToHex(color)
                    + " but at position (" + centerX + "," + centerY + ") out of ("
                    + bitmap.getWidth() + "," + bitmap.getHeight() + ") found"
                    + formatColorToHex(colorAtCenterPixel);
            if (throwExceptionIfFails) {
                throw new RuntimeException(mismatchDescription);
            } else {
                Assert.fail(mismatchDescription);
            }
        }
    }

    /**
     * Formats the passed integer-packed color into the #AARRGGBB format.
     */
    public static String formatColorToHex(@ColorInt int color) {
        return String.format("#%08X", (0xFFFFFFFF & color));
    }

    /**
     * Compares two integer-packed colors to be equal, each component within the specified
     * allowed variance. Returns <code>true</code> if the two colors are sufficiently equal
     * and <code>false</code> otherwise.
     */
    private static boolean areColorsTheSameWithTolerance(@ColorInt int expectedColor,
            @ColorInt int actualColor, int allowedComponentVariance) {
        int sourceAlpha = Color.alpha(actualColor);
        int sourceRed = Color.red(actualColor);
        int sourceGreen = Color.green(actualColor);
        int sourceBlue = Color.blue(actualColor);

        int expectedAlpha = Color.alpha(expectedColor);
        int expectedRed = Color.red(expectedColor);
        int expectedGreen = Color.green(expectedColor);
        int expectedBlue = Color.blue(expectedColor);

        int varianceAlpha = Math.abs(sourceAlpha - expectedAlpha);
        int varianceRed = Math.abs(sourceRed - expectedRed);
        int varianceGreen = Math.abs(sourceGreen - expectedGreen);
        int varianceBlue = Math.abs(sourceBlue - expectedBlue);

        boolean isColorMatch = (varianceAlpha <= allowedComponentVariance)
                && (varianceRed <= allowedComponentVariance)
                && (varianceGreen <= allowedComponentVariance)
                && (varianceBlue <= allowedComponentVariance);

        return isColorMatch;
    }

    /**
     * Composite two potentially translucent colors over each other and returns the result.
     */
    public static int compositeColors(@ColorInt int foreground, @ColorInt int background) {
        int bgAlpha = Color.alpha(background);
        int fgAlpha = Color.alpha(foreground);
        int a = compositeAlpha(fgAlpha, bgAlpha);

        int r = compositeComponent(Color.red(foreground), fgAlpha,
                Color.red(background), bgAlpha, a);
        int g = compositeComponent(Color.green(foreground), fgAlpha,
                Color.green(background), bgAlpha, a);
        int b = compositeComponent(Color.blue(foreground), fgAlpha,
                Color.blue(background), bgAlpha, a);

        return Color.argb(a, r, g, b);
    }

    private static int compositeAlpha(int foregroundAlpha, int backgroundAlpha) {
        return 0xFF - (((0xFF - backgroundAlpha) * (0xFF - foregroundAlpha)) / 0xFF);
    }

    private static int compositeComponent(int fgC, int fgA, int bgC, int bgA, int a) {
        if (a == 0) return 0;
        return ((0xFF * fgC * fgA) + (bgC * bgA * (0xFF - fgA))) / (a * 0xFF);
    }

    public static CharSequence sameCharSequence(final CharSequence expected) {
        return argThat(new BaseMatcher<CharSequence>() {
            @Override
            public boolean matches(Object o) {
                if (o instanceof CharSequence) {
                    return expected.toString().compareTo(o.toString()) == 0;
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("doesn't match " + expected);
            }
        });
    }
}