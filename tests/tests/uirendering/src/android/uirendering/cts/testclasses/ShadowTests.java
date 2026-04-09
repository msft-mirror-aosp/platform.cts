/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.uirendering.cts.testclasses;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.BitmapVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.util.CompareUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ShadowTests extends ActivityTestBase {
    private static final String TAG = "ShadowTests";

    private interface ColorCondition {
        boolean verify(int color);
    }

    private class ShadowVerifier extends BitmapVerifier {
        private static final int VIEW_FAIL_COLOR = Color.RED;
        private static final int AMBIENT_FAIL_COLOR = Color.GREEN;
        private static final int SPOT_FAIL_COLOR = Color.BLUE;

        private ColorCondition mColorCondition;

        ShadowVerifier(ColorCondition colorCondition) {
            mColorCondition = colorCondition;
        }

        @Override
        public boolean verify(int[] bitmap, int offset, int stride, int width, int height) {
            boolean success = true;
            int[] differenceMap = new int[bitmap.length];
            Arrays.fill(differenceMap, PASS_COLOR);

            // 1. Verify pixels inside the view are white
            // View area is [25, 65) x [25, 65)
            int[] insidePoints = {
                indexFromXAndY(30, 30, stride, offset), indexFromXAndY(60, 60, stride, offset)
            };
            for (int index : insidePoints) {
                if (bitmap[index] != Color.WHITE) {
                    Log.d(
                            TAG,
                            "View area check failed. Expected WHITE, got: "
                                    + Integer.toHexString(bitmap[index]));
                    differenceMap[index] = VIEW_FAIL_COLOR;
                    success = false;
                }
            }

            // 2. Verify ambient shadow on top, left, right
            int[] ambientPoints = {
                indexFromXAndY(24, 45, stride, offset), // left
                indexFromXAndY(66, 45, stride, offset) // right
            };
            for (int index : ambientPoints) {
                if (!isShadow(bitmap[index]) || !mColorCondition.verify(bitmap[index])) {
                    Log.d(
                            TAG,
                            "Ambient shadow check failed. Got: "
                                    + Integer.toHexString(bitmap[index]));
                    differenceMap[index] = AMBIENT_FAIL_COLOR;
                    success = false;
                }
            }

            // 3. Verify intense spot shadow on bottom
            // Find the darkest pixel in the bottom area (below the view)
            int maxIntensity = 0;
            int bestIndex = -1;
            for (int y = 65; y < 85; y++) {
                for (int x = 25; x < 65; x++) {
                    int index = indexFromXAndY(x, y, stride, offset);
                    int intensity = getIntensity(bitmap[index]);
                    if (intensity > maxIntensity) {
                        maxIntensity = intensity;
                        bestIndex = index;
                    }
                }
            }

            boolean spotConditionMet = bestIndex != -1 && mColorCondition.verify(bitmap[bestIndex]);
            if (bestIndex == -1 || maxIntensity < 10 || !spotConditionMet) {
                Log.d(
                        TAG,
                        "Spot shadow check failed. Max intensity: "
                                + maxIntensity
                                + ", match condition: "
                                + spotConditionMet);
                success = false;
                // If we didn't find any intense enough shadow, fail the entire bottom area in
                // diff
                for (int y = 65; y < 85; y++) {
                    for (int x = 25; x < 65; x++) {
                        differenceMap[indexFromXAndY(x, y, stride, offset)] = SPOT_FAIL_COLOR;
                    }
                }
            }

            if (!success) {
                mDifferenceBitmap =
                        Bitmap.createBitmap(
                                ActivityTestBase.TEST_WIDTH,
                                ActivityTestBase.TEST_HEIGHT,
                                Bitmap.Config.ARGB_8888);
                mDifferenceBitmap.setPixels(
                        differenceMap,
                        offset,
                        stride,
                        0,
                        0,
                        ActivityTestBase.TEST_WIDTH,
                        ActivityTestBase.TEST_HEIGHT);
            }
            return success;
        }

        private boolean isShadow(int color) {
            return color != Color.WHITE;
        }

        private int getIntensity(int color) {
            if (color == Color.WHITE) return 0;
            return 255 - (Color.red(color) + Color.green(color) + Color.blue(color)) / 3;
        }
    }

    @Test
    public void testShadowResources() {
        final Context context = new ContextThemeWrapper(getInstrumentation().getTargetContext(),
                android.R.style.Theme_Material_Light);
        final Resources resources = context.getResources();
        final TypedValue value = new TypedValue();

        resources.getValue(R.dimen.expected_spot_shadow_alpha, value, false);
        assertEquals(TypedValue.TYPE_FLOAT, value.type);
        float expectedSpot = value.getFloat();

        resources.getValue(R.dimen.expected_ambient_shadow_alpha, value, false);
        assertEquals(TypedValue.TYPE_FLOAT, value.type);
        float expectedAmbient = value.getFloat();

        assertTrue(expectedSpot > 0);
        assertTrue(expectedAmbient > 0);

        TypedArray typedArray = context.obtainStyledAttributes(new int[] {
                android.R.attr.spotShadowAlpha,
                android.R.attr.ambientShadowAlpha,
        });

        assertEquals(expectedSpot, typedArray.getFloat(0, 0.0f), 0);
        assertEquals(expectedAmbient, typedArray.getFloat(1, 0.0f), 0);
    }

    @Test
    public void testShadowLayout() {
        ShadowVerifier verifier =
                new ShadowVerifier(color -> CompareUtils.verifyPixelGrayScale(color, 1));

        createTest()
                .addLayout(R.layout.simple_shadow_layout, null, true/* HW only */)
                .runWithVerifier(verifier);
    }

    @Test
    public void testRedSpotShadow() {
        ShadowVerifier verifier =
                new ShadowVerifier(
                        color -> {
                            if (color == Color.WHITE) return true;
                            return Color.red(color) > Color.green(color)
                                    && Color.red(color) > Color.blue(color);
                        });

        createTest()
                .addLayout(R.layout.simple_shadow_layout, view -> {
                    view.findViewById(R.id.shadow_view).setOutlineSpotShadowColor(Color.RED);
                }, true/* HW only */)
                .runWithVerifier(verifier);
    }

    @Test
    public void testRedAmbientShadow() {
        ShadowVerifier verifier =
                new ShadowVerifier(
                        color -> {
                            if (color == Color.WHITE) return true;
                            return Color.red(color) > Color.green(color)
                                    && Color.red(color) > Color.blue(color);
                        });

        createTest()
                .addLayout(R.layout.simple_shadow_layout, view -> {
                    view.findViewById(R.id.shadow_view).setOutlineAmbientShadowColor(Color.RED);
                }, true/* HW only */)
                .runWithVerifier(verifier);
    }

    @Test
    public void testRedAmbientBlueSpotShadow() {
        ShadowVerifier verifier =
                new ShadowVerifier(
                        color -> {
                            if (color == Color.WHITE) return true;
                            // For mixed shadows, we expect some red (ambient) or some blue (spot)
                            // or a mix. The key is it shouldn't be neutral gray if one is colored.
                            return Color.red(color) > Color.green(color)
                                    && Color.blue(color) > Color.green(color);
                        });

        createTest()
                .addLayout(R.layout.simple_shadow_layout, view -> {
                    View shadow = view.findViewById(R.id.shadow_view);
                    shadow.setOutlineAmbientShadowColor(Color.RED);
                    shadow.setOutlineSpotShadowColor(Color.BLUE);
                }, true/* HW only */)
                .runWithVerifier(verifier);
    }
}
