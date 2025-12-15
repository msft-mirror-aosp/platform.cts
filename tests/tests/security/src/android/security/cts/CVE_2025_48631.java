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

package android.security.cts;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.ImageDecoder.Source;
import android.platform.test.annotations.AsbSecurityTest;
import android.util.Size;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import dalvik.system.PathClassLoader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_48631 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 444671303)
    public void testPocCVE_2025_48631() {
        try {
            // Fetch class loader for 'services.jar'.
            final PathClassLoader classLoader =
                    new PathClassLoader(
                            "/system/framework/services.jar", ClassLoader.getSystemClassLoader());

            // Fetch value of 'DEFAULT_DECODE_HARD_LIMIT_PX' from 'LocalImageResolver' if it exists.
            final Class LocalImageResolverClass =
                    classLoader.loadClass("com.android.internal.widget.LocalImageResolver");
            int fetchedMaxDimension = 0;
            try {
                final Field field =
                        LocalImageResolverClass.getDeclaredField("DEFAULT_DECODE_HARD_LIMIT_PX");
                field.setAccessible(true);
                fetchedMaxDimension = (Integer) field.get(null);
            } catch (Exception e) {
                // Default value.
                fetchedMaxDimension = 4096;
            }

            // Create an image with dimensions larger than 'fetchedMaxDimension'.
            final int maxDimension = fetchedMaxDimension;
            final int imageWidth = maxDimension + 1;
            final int imageHeight = maxDimension + 1;
            final Bitmap bitmap =
                    Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100 /* quality */, byteArrayOutputStream);
            final byte[] pngData = byteArrayOutputStream.toByteArray();
            final Source source = ImageDecoder.createSource(ByteBuffer.wrap(pngData));

            // Load the vulnerable method 'onHeaderDecoded'.
            final Method onHeaderDecodedMethod =
                    LocalImageResolverClass.getDeclaredMethod(
                            "onHeaderDecoded",
                            ImageDecoder.class,
                            ImageDecoder.ImageInfo.class,
                            int.class,
                            int.class);
            onHeaderDecodedMethod.setAccessible(true);

            // Invoke the vulnerable method.
            final CompletableFuture<Boolean> isVulnerable = new CompletableFuture<Boolean>();
            try {
                ImageDecoder.decodeDrawable(
                        source,
                        (decoder, info, src) -> {
                            try {
                                // Check if 'info' contains dimensions as expected.
                                final Size size = info.getSize();
                                final boolean isImageDimensionsSet =
                                        (size.getWidth() > maxDimension
                                                || size.getHeight() > maxDimension);
                                assume().withMessage("Image info is not set as expected.")
                                        .that(isImageDimensionsSet)
                                        .isTrue();

                                // Set fields 'mDesiredWidth' and 'mDesiredHeight' to zero
                                // before invoking the vulnerable method.
                                final Field widthField =
                                        ImageDecoder.class.getDeclaredField("mDesiredWidth");
                                final Field heightField =
                                        ImageDecoder.class.getDeclaredField("mDesiredHeight");
                                widthField.setAccessible(true);
                                heightField.setAccessible(true);
                                widthField.setInt(decoder, 0);
                                heightField.setInt(decoder, 0);

                                // Fetch 'maxIconSize' value.
                                final Field maxIconField =
                                        LocalImageResolverClass.getDeclaredField(
                                                "DEFAULT_MAX_SAFE_ICON_SIZE_PX");
                                maxIconField.setAccessible(true);
                                final int maxIconSize = (Integer) maxIconField.get(null);

                                // Invoke the vulnerable method.
                                onHeaderDecodedMethod.invoke(
                                        null, decoder, info, maxIconSize, maxIconSize);

                                // Compute value of sampleSize.
                                final Method getPowerOfTwoForSampleRatioMethod =
                                        LocalImageResolverClass.getDeclaredMethod(
                                                "getPowerOfTwoForSampleRatio", double.class);
                                getPowerOfTwoForSampleRatioMethod.setAccessible(true);
                                final int sampleSize =
                                        (Integer)
                                                getPowerOfTwoForSampleRatioMethod.invoke(
                                                        null, imageWidth / maxIconSize);

                                // Check if 'mDesiredWidth' and 'mDesiredHeight' are set.
                                isVulnerable.complete(
                                        (widthField.getInt(decoder) == imageWidth / sampleSize)
                                                && (heightField.getInt(decoder)
                                                        == imageHeight / sampleSize));
                            } catch (Exception e) {
                                // With fix, runtime exception is thrown.
                                if (e instanceof InvocationTargetException
                                        && e.getCause() instanceof RuntimeException) {
                                    isVulnerable.complete(false);
                                }
                            }
                        });
            } catch (Exception ignore) {
                // Ignore the unintended error.
            }

            // Without fix, 'Image' with dimensions larger than the allowed limits is processed by
            // decoder.
            // With fix, runtime exception is thrown due to check on dimensions of image.
            assertWithMessage(
                            "Device is vulnerable b/444671303 !!, Image with large dimensions is"
                                    + " processed by decoder.")
                    .that(isVulnerable.get(5_000L, TimeUnit.MILLISECONDS))
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
