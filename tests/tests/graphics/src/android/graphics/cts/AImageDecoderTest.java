/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.graphics.cts;

import static android.graphics.cts.ImageDecoderTest.AssetRecord;
import static android.graphics.cts.ImageDecoderTest.NamedParam;
import static android.graphics.cts.ImageDecoderTest.Record;
import static android.system.OsConstants.SEEK_SET;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.ContentResolver;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ColorSpace.Named;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.graphics.drawable.cts.AnimatedImageDrawableTest;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.util.DisplayMetrics;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.RequiresDevice;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@RunWith(TestParameterInjector.class)
public class AImageDecoderTest {
    static {
        System.loadLibrary("ctsgraphics_jni");
    }

    private static AssetManager getAssetManager() {
        return InstrumentationRegistry.getTargetContext().getAssets();
    }

    private static Resources getResources() {
        return InstrumentationRegistry.getTargetContext().getResources();
    }

    private static ContentResolver getContentResolver() {
        return InstrumentationRegistry.getTargetContext().getContentResolver();
    }

    // These match the formats in the NDK.
    // ANDROID_BITMAP_FORMAT_NONE is used by nTestDecode to signal using the default.
    private static final int ANDROID_BITMAP_FORMAT_NONE = 0;
    private static final int ANDROID_BITMAP_FORMAT_RGBA_8888 = 1;
    private static final int ANDROID_BITMAP_FORMAT_RGB_565 = 4;
    private static final int ANDROID_BITMAP_FORMAT_A_8 = 8;
    private static final int ANDROID_BITMAP_FORMAT_RGBA_F16 = 9;
    private static final int ANDROID_BITMAP_FORMAT_RGBA_1010102 = 10;

    @Test
    public void testEmptyCreate() {
        nTestEmptyCreate();
    }

    private static class AssetRecordProvider extends TestParameterValuesProvider {
        @Override
        public List<AssetRecord> provideValues(Context context) {
            return ImageDecoderTest.getAssetRecords();
        }
    }

    private static class RecordProvider extends TestParameterValuesProvider {
        @Override
        public List<Record> provideValues(Context context) {
            return ImageDecoderTest.getRecords();
        }
    }

    private static class BitmapFormatProvider extends TestParameterValuesProvider {
        @Override
        public List<?> provideValues(Context context) {
            return Arrays.asList(
                value(ANDROID_BITMAP_FORMAT_NONE)
                    .withName("ANDROID_BITMAP_FORMAT_NONE"),
                value(ANDROID_BITMAP_FORMAT_RGBA_1010102)
                    .withName("ANDROID_BITMAP_FORMAT_RGBA_1010102"),
                value(ANDROID_BITMAP_FORMAT_RGB_565)
                    .withName("ANDROID_BITMAP_FORMAT_RGB_565"),
                value(ANDROID_BITMAP_FORMAT_RGBA_F16)
                    .withName("ANDROID_BITMAP_FORMAT_RGBA_F16"));
        }
    }

    @Test
    public void testNullDecoder(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        nTestNullDecoder(getAssetManager(), record.name);
    }

    private static int nativeDataSpace(ColorSpace cs) {
        if (cs == null) {
            return DataSpace.ADATASPACE_UNKNOWN;
        }

        return cs.getDataSpace();
    }

    @Test
    public void testCreateBuffer(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        // Note: This uses an asset for simplicity, but in native it gets a
        // buffer.
        long asset = nOpenAsset(getAssetManager(), record.name);
        long aimagedecoder = nCreateFromAssetBuffer(asset);

        nTestInfo(aimagedecoder, record.width, record.height, "image/png",
                record.isF16, nativeDataSpace(record.getColorSpace()));
        nCloseAsset(asset);
    }

    @Test
    public void testCreateFd(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        // Note: This uses an asset for simplicity, but in native it gets a
        // file descriptor.
        long asset = nOpenAsset(getAssetManager(), record.name);
        long aimagedecoder = nCreateFromAssetFd(asset);

        nTestInfo(aimagedecoder, record.width, record.height, "image/png",
                record.isF16, nativeDataSpace(record.getColorSpace()));
        nCloseAsset(asset);
    }

    @Test
    public void testCreateAsset(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        long asset = nOpenAsset(getAssetManager(), record.name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestInfo(aimagedecoder, record.width, record.height, "image/png",
                record.isF16, nativeDataSpace(record.getColorSpace()));
        nCloseAsset(asset);
    }

    private static ParcelFileDescriptor open(int resId, int offset) throws FileNotFoundException {
        File file = Utils.obtainFile(resId, offset);
        assertNotNull(file);

        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_READ_ONLY);
        assertNotNull(pfd);
        return pfd;
    }

    private static ParcelFileDescriptor open(int resId) throws FileNotFoundException {
        return open(resId, 0);
    }

    @Test
    public void testCreateFdResources(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record)
                    throws IOException {
        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestInfo(aimagedecoder, record.width, record.height, record.mimeType,
                    false /*isF16*/, nativeDataSpace(record.colorSpace));
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(record.resId));
        }
    }

    @Test
    public void testCreateFdOffset(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record)
                    throws IOException {
        // Use an arbitrary offset. This ensures that we rewind to the correct offset.
        final int offset = 15;
        try (ParcelFileDescriptor pfd = open(record.resId, offset)) {
            FileDescriptor fd = pfd.getFileDescriptor();
            Os.lseek(fd, offset, SEEK_SET);
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestInfo(aimagedecoder, record.width, record.height, record.mimeType,
                    false /*isF16*/, nativeDataSpace(record.colorSpace));
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(record.resId));
        } catch (ErrnoException err) {
            fail("Failed to seek " + Utils.getAsResourceUri(record.resId));
        }
    }

    @Test
    public void testCreateIncomplete() {
        String file = "green-srgb.png";
        // This truncates the file before the IDAT.
        nTestCreateIncomplete(getAssetManager(), file, 823);
    }

    @Test
    public void testUnsupportedFormat(
                    @TestParameter({"shaders/tri.frag", "test_video.mp4"}) String file) {
        nTestCreateUnsupported(getAssetManager(), file);
    }

    @Test
    public void testSetFormat(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        long asset = nOpenAsset(getAssetManager(), record.name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestSetFormat(aimagedecoder, record.isF16, record.isGray);
        nCloseAsset(asset);
    }

    @Test
    public void testSetFormatResources(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record)
                    throws IOException {
        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestSetFormat(aimagedecoder, false /* isF16 */, record.isGray);
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(record.resId));
        }
    }

    @Test
    public void testSetUnpremul(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        long asset = nOpenAsset(getAssetManager(), record.name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestSetUnpremul(aimagedecoder, record.hasAlpha);
        nCloseAsset(asset);
    }

    @Test
    public void testSetUnpremulResources(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record)
                    throws IOException {
        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestSetUnpremul(aimagedecoder, record.hasAlpha);
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(record.resId));
        }
    }

    @Test
    public void testGetMinimumStride(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        long asset = nOpenAsset(getAssetManager(), record.name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestGetMinimumStride(aimagedecoder, record.isF16, record.isGray);
    }

    @Test
    public void testGetMinimumStrideResources(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record)
                    throws IOException {
        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestGetMinimumStride(aimagedecoder, false /* isF16 */, record.isGray);
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(record.resId));
        }
    }

    private static Bitmap decode(ImageDecoder.Source src, boolean unpremul) {
        try {
            return ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                // So we can compare pixels to the native decode.
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setUnpremultipliedRequired(unpremul);
            });
        } catch (IOException e) {
            fail("Failed to decode in Java with " + e);
            return null;
        }
    }

    private static Bitmap decode(int resId, boolean unpremul) {
        // This test relies on ImageDecoder *not* scaling to account for density.
        // Temporarily change the DisplayMetrics to prevent that scaling.
        Resources res = getResources();
        final int originalDensity = res.getDisplayMetrics().densityDpi;
        try {
            res.getDisplayMetrics().densityDpi = DisplayMetrics.DENSITY_DEFAULT;
            ImageDecoder.Source src = ImageDecoder.createSource(res, resId);
            return decode(src, unpremul);
        } finally {
            res.getDisplayMetrics().densityDpi = originalDensity;
        }
    }

    @Test
    @RequiresDevice
    public void testDecode10BitHeif(
                    @TestParameter(valuesProvider = BitmapFormatProvider.class) int bitmapFormat,
                    @TestParameter boolean unpremul) throws IOException {
        if (!MediaUtils.hasHardwareCodec(MediaFormat.MIMETYPE_VIDEO_HEVC, false)) {
            return;
        }
        final int resId = R.raw.heifimage_10bit;
        Bitmap bm = null;
        switch (bitmapFormat) {
            case ANDROID_BITMAP_FORMAT_NONE:
            case ANDROID_BITMAP_FORMAT_RGBA_1010102:
                bm = decode(resId, unpremul);
                break;
            case ANDROID_BITMAP_FORMAT_RGB_565:
                bm = decode(resId, Bitmap.Config.RGB_565);
                break;
            case ANDROID_BITMAP_FORMAT_RGBA_F16:
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inPreferredConfig = Bitmap.Config.RGBA_F16;
                opt.inPremultiplied = !unpremul;
                bm = BitmapFactory.decodeStream(getResources().openRawResource(resId), null, opt);
                break;
            default:
                fail("Unsupported Bitmap format: " + bitmapFormat);
        }
        assertNotNull(bm);

        try (ParcelFileDescriptor pfd = open(resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());
            nTestDecode(aimagedecoder, bitmapFormat, unpremul, bm);
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(resId));
        }
    }

    @Test
    @RequiresDevice
    @CddTest(requirements = {"5.1.5/C-0-7"})
    public void testDecode10BitAvif(
                @TestParameter(valuesProvider = BitmapFormatProvider.class) int bitmapFormat,
                @TestParameter boolean unpremul) throws IOException {
        assumeTrue("AVIF is not supported on this device, skip this test.",
                ImageDecoder.isMimeTypeSupported("image/avif"));

        final int resId = R.raw.avif_yuv_420_10bit;
        Bitmap bm = null;
        switch (bitmapFormat) {
            case ANDROID_BITMAP_FORMAT_NONE:
            case ANDROID_BITMAP_FORMAT_RGBA_1010102:
                bm = decode(resId, unpremul);
                break;
            case ANDROID_BITMAP_FORMAT_RGB_565:
                bm = decode(resId, Bitmap.Config.RGB_565);
                break;
            case ANDROID_BITMAP_FORMAT_RGBA_F16:
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inPreferredConfig = Bitmap.Config.RGBA_F16;
                opt.inPremultiplied = !unpremul;
                bm = BitmapFactory.decodeStream(getResources().openRawResource(resId), null, opt);
                break;
            default:
                fail("Unsupported Bitmap format: " + bitmapFormat);
        }
        assertNotNull(bm);

        try (ParcelFileDescriptor pfd = open(resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());
            nTestDecode(aimagedecoder, bitmapFormat, unpremul, bm);
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(resId));
        }
    }

    @Test
    public void testDecode(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record,
                    @TestParameter boolean unpremul) {
        AssetManager assets = getAssetManager();
        ImageDecoder.Source src = ImageDecoder.createSource(assets, record.name);
        Bitmap bm = decode(src, unpremul);

        long asset = nOpenAsset(assets, record.name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestDecode(aimagedecoder, ANDROID_BITMAP_FORMAT_NONE, unpremul, bm);
        nCloseAsset(asset);
    }

    @Test
    public void testDecodeResources(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record,
                    @TestParameter boolean unpremul) throws IOException {
        Bitmap bm = decode(record.resId, unpremul);
        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestDecode(aimagedecoder, ANDROID_BITMAP_FORMAT_NONE, unpremul, bm);
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(record.resId));
        }
    }

    private static Bitmap decode(ImageDecoder.Source src, Bitmap.Config config) {
        try {
            return ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                // So we can compare pixels to the native decode.
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                switch (config) {
                    case RGB_565:
                        decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM);
                        break;
                    case ALPHA_8:
                        decoder.setDecodeAsAlphaMaskEnabled(true);
                        break;
                    default:
                        fail("Unexpected Config " + config);
                        break;
                }
            });
        } catch (IOException e) {
            fail("Failed to decode in Java with " + e);
            return null;
        }
    }

    private static Bitmap decode(int resId, Bitmap.Config config) {
        // This test relies on ImageDecoder *not* scaling to account for density.
        // Temporarily change the DisplayMetrics to prevent that scaling.
        Resources res = getResources();
        final int originalDensity = res.getDisplayMetrics().densityDpi;
        try {
            res.getDisplayMetrics().densityDpi = DisplayMetrics.DENSITY_DEFAULT;
            ImageDecoder.Source src = ImageDecoder.createSource(res, resId);
            return decode(src, config);
        } finally {
            res.getDisplayMetrics().densityDpi = originalDensity;
        }
    }

    @Test
    public void testDecode565(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        AssetManager assets = getAssetManager();
        ImageDecoder.Source src = ImageDecoder.createSource(assets, record.name);
        Bitmap bm = decode(src, Bitmap.Config.RGB_565);

        if (bm.getConfig() != Bitmap.Config.RGB_565) {
            bm = null;
        }

        long asset = nOpenAsset(assets, record.name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestDecode(aimagedecoder, ANDROID_BITMAP_FORMAT_RGB_565, false, bm);
        nCloseAsset(asset);
    }

    @Test
    public void testDecode565Resources(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record)
                    throws IOException {
        Bitmap bm = decode(record.resId, Bitmap.Config.RGB_565);

        if (bm.getConfig() != Bitmap.Config.RGB_565) {
            bm = null;
        }

        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestDecode(aimagedecoder, ANDROID_BITMAP_FORMAT_RGB_565, false, bm);
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(record.resId));
        }
    }

    @Test
    public void testDecodeA8(@TestParameter({"grayscale-linearSrgb.png"}) String name) {
        AssetManager assets = getAssetManager();
        ImageDecoder.Source src = ImageDecoder.createSource(assets, name);
        Bitmap bm = decode(src, Bitmap.Config.ALPHA_8);

        assertNotNull(bm);
        assertNull(bm.getColorSpace());
        assertEquals(Bitmap.Config.ALPHA_8, bm.getConfig());

        long asset = nOpenAsset(assets, name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestDecode(aimagedecoder, ANDROID_BITMAP_FORMAT_A_8, false, bm);
        nCloseAsset(asset);
    }

    @Test
    public void testDecodeA8Resources()
            throws IOException {
        final int resId = R.drawable.grayscale_jpg;
        Bitmap bm = decode(resId, Bitmap.Config.ALPHA_8);

        assertNotNull(bm);
        assertNull(bm.getColorSpace());
        assertEquals(Bitmap.Config.ALPHA_8, bm.getConfig());

        try (ParcelFileDescriptor pfd = open(resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestDecode(aimagedecoder, ANDROID_BITMAP_FORMAT_A_8, false, bm);
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(resId));
        }
    }

    @Test
    public void testDecodeF16(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record,
                    @TestParameter boolean unpremul) {
        AssetManager assets = getAssetManager();

        // ImageDecoder doesn't allow forcing a decode to F16, so use BitmapFactory.
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGBA_F16;
        options.inPremultiplied = !unpremul;

        InputStream is = null;
        try {
            is = assets.open(record.name);
        } catch (IOException e) {
            fail("Failed to open " + record.name + " with " + e);
        }
        assertNotNull(is);

        Bitmap bm = BitmapFactory.decodeStream(is, null, options);
        assertNotNull(bm);

        long asset = nOpenAsset(assets, record.name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestDecode(aimagedecoder, ANDROID_BITMAP_FORMAT_RGBA_F16, unpremul, bm);
        nCloseAsset(asset);
    }

    @Test
    public void testDecodeF16Resources(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record,
                    @TestParameter boolean unpremul) throws IOException {
        // ImageDecoder doesn't allow forcing a decode to F16, so use BitmapFactory.
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGBA_F16;
        options.inPremultiplied = !unpremul;
        options.inScaled = false;

        Bitmap bm = BitmapFactory.decodeResource(getResources(),
                record.resId, options);
        assertNotNull(bm);

        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestDecode(aimagedecoder, ANDROID_BITMAP_FORMAT_RGBA_F16, unpremul, bm);
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(record.resId));
        }
    }

    @Test
    public void testDecodeStride(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        long asset = nOpenAsset(getAssetManager(), record.name);
        long aimagedecoder = nCreateFromAsset(asset);
        nTestDecodeStride(aimagedecoder);
        nCloseAsset(asset);
    }

    @Test
    public void testDecodeStrideResources(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record)
                    throws IOException {
        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestDecodeStride(aimagedecoder);
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(record.resId));
        }
    }

    @Test
    public void testSetTargetSize(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        long asset = nOpenAsset(getAssetManager(), record.name);
        long aimagedecoder = nCreateFromAsset(asset);
        nTestSetTargetSize(aimagedecoder);
        nCloseAsset(asset);
    }

    @Test
    public void testSetTargetSizeResources(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record)
                    throws IOException {
        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestSetTargetSize(aimagedecoder);
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(record.resId));
        }
    }

    private Bitmap decodeSampled(String name, ImageDecoder.Source src, int sampleSize) {
        try {
            return ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                // So we can compare pixels to the native decode.
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setTargetSampleSize(sampleSize);
            });
        } catch (IOException e) {
            fail("Failed to decode " + name + " in Java (sampleSize: "
                    + sampleSize + ") with " + e);
            return null;
        }
    }

    @Test
    public void testDecodeSampled(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record,
                    @TestParameter({"2", "3", "4", "8", "16"}) int sampleSize) {
        AssetManager assets = getAssetManager();
        ImageDecoder.Source src = ImageDecoder.createSource(assets, record.name);
        Bitmap bm = decodeSampled(record.name, src, sampleSize);

        long asset = nOpenAsset(assets, record.name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestDecodeScaled(aimagedecoder, bm);
        nCloseAsset(asset);
    }

    @Test
    public void testDecodeResourceSampled(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record,
                    @TestParameter({"2", "3", "4", "8", "16"}) int sampleSize) throws IOException {
        ImageDecoder.Source src = ImageDecoder.createSource(getResources(),
                record.resId);
        String name = Utils.getAsResourceUri(record.resId).toString();
        Bitmap bm = decodeSampled(name, src, sampleSize);
        assertNotNull(bm);

        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestDecodeScaled(aimagedecoder, bm);
        } catch (FileNotFoundException e) {
            fail("Could not open " + name + ": " + e);
        }
    }

    @Test
    public void testComputeSampledSize(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record,
                    @TestParameter({"2", "3", "4", "8", "16"}) int sampleSize)
                    throws IOException {
        if (record.mimeType.equals("image/x-adobe-dng")) {
            // SkRawCodec does not support sampling.
            return;
        }
        testComputeSampledSizeInternal(record.resId, sampleSize);
    }

    private void testComputeSampledSizeInternal(int resId, int sampleSize)
            throws IOException {
        ImageDecoder.Source src = ImageDecoder.createSource(getResources(), resId);
        String name = Utils.getAsResourceUri(resId).toString();
        Bitmap bm = decodeSampled(name, src, sampleSize);
        assertNotNull(bm);

        try (ParcelFileDescriptor pfd = open(resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestComputeSampledSize(aimagedecoder, bm, sampleSize);
        } catch (FileNotFoundException e) {
            fail("Could not open " + name + ": " + e);
        }
    }

    @Test
    public void testComputeSampledSizeExif(
                    @TestParameter(valuesProvider = ExifImagesProvider.class) int resId,
                    @TestParameter ({"2", "3", "4", "8", "16"}) int sampleSize)
                    throws IOException {
        testComputeSampledSizeInternal(resId, sampleSize);
    }

    private Bitmap decodeScaled(String name, ImageDecoder.Source src) {
        try {
            return ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                // So we can compare pixels to the native decode.
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);

                // Scale to an arbitrary width and height.
                decoder.setTargetSize(300, 300);
            });
        } catch (IOException e) {
            fail("Failed to decode " + name + " in Java (size: "
                    + "300 x 300) with " + e);
            return null;
        }
    }

    @Test
    public void testDecodeScaled(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        AssetManager assets = getAssetManager();
        ImageDecoder.Source src = ImageDecoder.createSource(assets, record.name);
        Bitmap bm = decodeScaled(record.name, src);

        long asset = nOpenAsset(assets, record.name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestDecodeScaled(aimagedecoder, bm);
        nCloseAsset(asset);
    }

    @Test
    public void testDecodeResourceScaled(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record)
                    throws IOException {
        ImageDecoder.Source src = ImageDecoder.createSource(getResources(),
                record.resId);
        String name = Utils.getAsResourceUri(record.resId).toString();
        Bitmap bm = decodeScaled(name, src);
        assertNotNull(bm);

        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestDecodeScaled(aimagedecoder, bm);
        } catch (FileNotFoundException e) {
            fail("Could not open " + name + ": " + e);
        }
    }

    private Bitmap decodeScaleUp(String name, ImageDecoder.Source src) {
        try {
            return ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                // So we can compare pixels to the native decode.
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);

                decoder.setTargetSize(info.getSize().getWidth() * 2,
                        info.getSize().getHeight() * 2);
            });
        } catch (IOException e) {
            fail("Failed to decode " + name + " in Java (scaled up) with " + e);
            return null;
        }
    }

    @Test
    public void testDecodeScaleUp(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        AssetManager assets = getAssetManager();
        ImageDecoder.Source src = ImageDecoder.createSource(assets, record.name);
        Bitmap bm = decodeScaleUp(record.name, src);

        long asset = nOpenAsset(assets, record.name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestDecodeScaled(aimagedecoder, bm);
        nCloseAsset(asset);
    }

    @Test
    public void testDecodeResourceScaleUp(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record)
                    throws IOException {
        ImageDecoder.Source src = ImageDecoder.createSource(getResources(),
                record.resId);
        String name = Utils.getAsResourceUri(record.resId).toString();
        Bitmap bm = decodeScaleUp(name, src);
        assertNotNull(bm);

        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestDecodeScaled(aimagedecoder, bm);
        } catch (FileNotFoundException e) {
            fail("Could not open " + name + ": " + e);
        }
    }

    @Test
    public void testSetCrop(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        nTestSetCrop(getAssetManager(), record.name);
    }

    private static class Cropper implements ImageDecoder.OnHeaderDecodedListener {
        Cropper(boolean scale) {
            mScale = scale;
        }

        public boolean withScale() {
            return mScale;
        }

        public int getWidth() {
            return mWidth;
        }

        public int getHeight() {
            return mHeight;
        }

        public Rect getCropRect() {
            return mCropRect;
        }

        @Override
        public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                ImageDecoder.Source source) {
            mWidth = info.getSize().getWidth();
            mHeight = info.getSize().getHeight();
            if (mScale) {
                mWidth = 40;
                mHeight = 40;
                decoder.setTargetSize(mWidth, mHeight);
            }

            mCropRect = new Rect(mWidth / 2, mHeight / 2, mWidth, mHeight);
            decoder.setCrop(mCropRect);

            // So we can compare pixels to the native decode.
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        }

        private final boolean mScale;
        private Rect mCropRect;
        private int mWidth;
        private int mHeight;
    }

    private static Bitmap decodeCropped(String name, Cropper cropper, ImageDecoder.Source src) {
        try {
            return ImageDecoder.decodeBitmap(src, cropper);
        } catch (IOException e) {
            fail("Failed to decode " + name + " in Java with "
                    + (cropper.withScale() ? "scale and " : "a ") + "crop ("
                    + cropper.getCropRect() + "): " + e);
            return null;
        }
    }

    private static Bitmap decodeCropped(String name, Cropper cropper, int resId) {
        // This test relies on ImageDecoder *not* scaling to account for density.
        // Temporarily change the DisplayMetrics to prevent that scaling.
        Resources res = getResources();
        final int originalDensity = res.getDisplayMetrics().densityDpi;
        try {
            res.getDisplayMetrics().densityDpi = DisplayMetrics.DENSITY_DEFAULT;
            ImageDecoder.Source src = ImageDecoder.createSource(res, resId);
            return decodeCropped(name, cropper, src);
        } finally {
            res.getDisplayMetrics().densityDpi = originalDensity;
        }
    }

    @Test
    public void testCrop(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        AssetManager assets = getAssetManager();
        ImageDecoder.Source src = ImageDecoder.createSource(assets, record.name);
        Cropper cropper = new Cropper(false /* scale */);
        Bitmap bm = decodeCropped(record.name, cropper, src);

        long asset = nOpenAsset(assets, record.name);
        long aimagedecoder = nCreateFromAsset(asset);

        Rect crop = cropper.getCropRect();
        nTestDecodeCrop(aimagedecoder, bm, 0, 0, crop.left, crop.top, crop.right, crop.bottom);
        nCloseAsset(asset);
    }

    @Test
    public void testCropResource(
                   @TestParameter(valuesProvider = RecordProvider.class) Record record)
                   throws IOException {
        String name = Utils.getAsResourceUri(record.resId).toString();
        Cropper cropper = new Cropper(false /* scale */);
        Bitmap bm = decodeCropped(name, cropper, record.resId);
        assertNotNull(bm);

        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            Rect crop = cropper.getCropRect();
            nTestDecodeCrop(aimagedecoder, bm, 0, 0, crop.left, crop.top, crop.right, crop.bottom);
        } catch (FileNotFoundException e) {
            fail("Could not open " + name + ": " + e);
        }
    }

    @Test
    public void testCropAndScale(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        AssetManager assets = getAssetManager();
        ImageDecoder.Source src = ImageDecoder.createSource(assets, record.name);
        Cropper cropper = new Cropper(true /* scale */);
        Bitmap bm = decodeCropped(record.name, cropper, src);

        long asset = nOpenAsset(assets, record.name);
        long aimagedecoder = nCreateFromAsset(asset);

        Rect crop = cropper.getCropRect();
        nTestDecodeCrop(aimagedecoder, bm, cropper.getWidth(), cropper.getHeight(),
                crop.left, crop.top, crop.right, crop.bottom);
        nCloseAsset(asset);
    }

    @Test
    public void testCropAndScaleResource(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record)
                    throws IOException {
        ImageDecoder.Source src = ImageDecoder.createSource(getResources(),
                record.resId);
        String name = Utils.getAsResourceUri(record.resId).toString();
        Cropper cropper = new Cropper(true /* scale */);
        Bitmap bm = decodeCropped(name, cropper, src);
        assertNotNull(bm);

        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            Rect crop = cropper.getCropRect();
            nTestDecodeCrop(aimagedecoder, bm, cropper.getWidth(), cropper.getHeight(),
                    crop.left, crop.top, crop.right, crop.bottom);
        } catch (FileNotFoundException e) {
            fail("Could not open " + name + ": " + e);
        }
    }

    private static class ExifImagesProvider extends TestParameterValuesProvider {
        @Override
        public List<?> provideValues(Context context) {
            return Arrays.asList(
                value(R.drawable.orientation_1).withName("orientation_1"),
                value(R.drawable.orientation_2).withName("orientation_2"),
                value(R.drawable.orientation_3).withName("orientation_3"),
                value(R.drawable.orientation_4).withName("orientation_4"),
                value(R.drawable.orientation_5).withName("orientation_5"),
                value(R.drawable.orientation_6).withName("orientation_6"),
                value(R.drawable.orientation_7).withName("orientation_7"),
                value(R.drawable.orientation_8).withName("orientation_8"),
                value(R.drawable.webp_orientation1).withName("webp_orientation1"),
                value(R.drawable.webp_orientation2).withName("webp_orientation2"),
                value(R.drawable.webp_orientation3).withName("webp_orientation3"),
                value(R.drawable.webp_orientation4).withName("webp_orientation4"),
                value(R.drawable.webp_orientation5).withName("webp_orientation5"),
                value(R.drawable.webp_orientation6).withName("webp_orientation6"),
                value(R.drawable.webp_orientation7).withName("webp_orientation7"),
                value(R.drawable.webp_orientation8).withName("webp_orientation8")
            );
        }
    }

    @Test
    public void testRespectOrientation(
                    @TestParameter(valuesProvider = ExifImagesProvider.class) int resId)
                    throws IOException {
        Uri uri = Utils.getAsResourceUri(resId);
        ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(),
                uri);
        Bitmap bm = decode(src, false /* unpremul */);
        assertNotNull(bm);
        assertEquals(100, bm.getWidth());
        assertEquals(80,  bm.getHeight());

        // First verify that the info (and in particular, the width and height)
        // are correct. This uses a separate ParcelFileDescriptor/aimagedecoder
        // because the native methods delete the aimagedecoder.
        try (ParcelFileDescriptor pfd = open(resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            String mimeType = uri.toString().contains("webp") ? "image/webp" : "image/jpeg";
            nTestInfo(aimagedecoder, 100, 80, mimeType, false,
                    bm.getColorSpace().getDataSpace());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            fail("Could not open " + uri + " to check info");
        }

        try (ParcelFileDescriptor pfd = open(resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestDecode(aimagedecoder, ANDROID_BITMAP_FORMAT_NONE, false /* unpremul */, bm);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            fail("Could not open " + uri);
        }
        bm.recycle();
    }

    @Test
    public void testScalePlusUnpremul(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        long asset = nOpenAsset(getAssetManager(), record.name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestScalePlusUnpremul(aimagedecoder);
        nCloseAsset(asset);
    }


    private static File createCompressedBitmap(int width, int height, ColorSpace colorSpace,
            Bitmap.CompressFormat format) {
        File dir = InstrumentationRegistry.getTargetContext().getFilesDir();
        dir.mkdirs();

        File file = new File(dir, colorSpace.getName());
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            // If the file does not exist it will be handled below.
        }
        if (!file.exists()) {
            fail("Failed to create new File for " + file + "!");
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, true,
                colorSpace);
        bitmap.eraseColor(Color.BLUE);

        try (FileOutputStream fOutput = new FileOutputStream(file)) {
            bitmap.compress(format, 80, fOutput);
            return file;
        } catch (IOException e) {
            e.printStackTrace();
            fail("Failed to create file \"" + file + "\" with exception " + e);
            return null;
        }
    }

    private static class RgbColorSpacesProvider extends TestParameterValuesProvider {
        @Override
        public List<ColorSpace> provideValues(Context context) {
            return BitmapTest.getRgbColorSpaces();
        }
    }

    String toMimeType(Bitmap.CompressFormat format) {
        switch (format) {
            case JPEG:
                return "image/jpeg";
            case PNG:
                return "image/png";
            case WEBP:
            case WEBP_LOSSY:
            case WEBP_LOSSLESS:
                return "image/webp";
            default:
                return "";
        }
    }

    private static class CompressFormatsProvider extends TestParameterValuesProvider {
        @Override
        public List<Bitmap.CompressFormat> provideValues(Context context) {
            return Arrays.asList(Bitmap.CompressFormat.values());
        }
    }

    @Test
    public void testGetDataSpace(
                    @TestParameter(valuesProvider = RgbColorSpacesProvider.class)
                                                        ColorSpace colorSpace,
                    @TestParameter(valuesProvider = CompressFormatsProvider.class)
                                                        Bitmap.CompressFormat format) {
        if (colorSpace == ColorSpace.get(Named.EXTENDED_SRGB)
                || colorSpace == ColorSpace.get(Named.LINEAR_EXTENDED_SRGB)) {
            // These will only be reported when the default AndroidBitmapFormat is F16.
            // Bitmap.compress will not compress to an image that will be decoded as F16 by default,
            // so these are covered by the AssetRecord tests.
            return;
        }

        final int width = 10;
        final int height = 10;
        File file = createCompressedBitmap(width, height, colorSpace, format);
        assertNotNull(file);

        int dataSpace = colorSpace.getDataSpace();

        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_READ_ONLY)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());
            nTestInfo(aimagedecoder, width, height, toMimeType(format), false, dataSpace);
        } catch (IOException e) {
            e.printStackTrace();
            fail("Could not read " + file);
        }
    }

    private static Bitmap decode(ImageDecoder.Source src, ColorSpace colorSpace) {
        try {
            return ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                // So we can compare pixels to the native decode.
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);

                decoder.setTargetColorSpace(colorSpace);
            });
        } catch (IOException e) {
            fail("Failed to decode in Java with " + e);
            return null;
        }
    }

    @Test
    public void testSetDataSpace(
                    @TestParameter(valuesProvider = RgbColorSpacesProvider.class)
                                                        ColorSpace colorSpace) {
        int dataSpace = colorSpace.getDataSpace();
        if (dataSpace == DataSpace.ADATASPACE_UNKNOWN) {
            // AImageDecoder cannot decode to these ADATASPACEs
            return;
        }

        String name = "translucent-green-p3.png";
        AssetManager assets = getAssetManager();
        ImageDecoder.Source src = ImageDecoder.createSource(assets, name);
        Bitmap bm = decode(src, colorSpace);
        assertEquals(colorSpace, bm.getColorSpace());

        long asset = nOpenAsset(assets, name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestDecode(aimagedecoder, bm, dataSpace);
        nCloseAsset(asset);
    }


    @Test
    public void testNonStandardDataSpaces(
                    @TestParameter({"cmyk_yellow_224_224_32.jpg",
                                    "wide_gamut_yellow_224_224_64.jpeg"}) String name) {
        AssetManager assets = getAssetManager();
        long asset = nOpenAsset(assets, name);
        long aimagedecoder = nCreateFromAsset(asset);

        // These images have profiles that do not map to ADataSpaces (or even SkColorSpaces).
        // Verify that by default, AImageDecoder will treat them as ADATASPACE_UNKNOWN.
        nTestInfo(aimagedecoder, 32, 32, "image/jpeg", false, DataSpace.ADATASPACE_UNKNOWN);
        nCloseAsset(asset);
    }

    private static class NonStandardDataSpacesProvider extends TestParameterValuesProvider {
        @Override
        public List<NamedParam<String>> provideValues(Context context) {
            return Arrays.asList(
                    new NamedParam<String>("cmyk_yellow_224_224_32.jpg", "#FFD8FC04"),
                    new NamedParam<String>("wide_gamut_yellow_224_224_64.jpeg", "#FFE0E040")
            );
        }
    }

    @Test
    public void testNonStandardDataSpacesDecode(
                    @TestParameter(valuesProvider = NonStandardDataSpacesProvider.class)
                                                        NamedParam<String> input) {
        String name = input.name;
        String color = input.value;
        AssetManager assets = getAssetManager();
        long asset = nOpenAsset(assets, name);
        long aimagedecoder = nCreateFromAsset(asset);

        // These images are each a solid color. If we correctly do no color correction, they should
        // match |color|.
        int colorInt = Color.parseColor(color);
        Bitmap bm = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        bm.eraseColor(colorInt);

        nTestDecode(aimagedecoder, ANDROID_BITMAP_FORMAT_NONE, false /* unpremul */, bm);
        nCloseAsset(asset);
    }

    @Test
    public void testNotAnimatedAssets(
                    @TestParameter(valuesProvider = AssetRecordProvider.class) AssetRecord record) {
        long asset = nOpenAsset(getAssetManager(), record.name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestIsAnimated(aimagedecoder, false);
        nCloseAsset(asset);
    }

    @Test
    public void testNotAnimated(
                    @TestParameter(valuesProvider = RecordProvider.class) Record record)
                    throws IOException {
        try (ParcelFileDescriptor pfd = open(record.resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestIsAnimated(aimagedecoder, false);
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(record.resId));
        }
    }

    // Although these images have an encoded repeat count, they have only one frame,
    // so they are not considered animated.
    @Test
    public void testStill(
                    @TestParameter({"still_with_loop_count.gif",
                                    "webp_still_with_loop_count.webp"}) String name) {
        long asset = nOpenAsset(getAssetManager(), name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestIsAnimated(aimagedecoder, false);
        nCloseAsset(asset);
    }


    private static class AnimatedImagesPlusRepeatCountsProvider
            extends TestParameterValuesProvider {
        @Override
        public List<AnimatedImageDrawableTest.RepeatImage> provideValues(Context context) {
            return Arrays.asList(AnimatedImageDrawableTest.getAnimatedRepeatImages());
        }
    }

    @Test
    public void testAnimated(
                    @TestParameter(valuesProvider = AnimatedImagesPlusRepeatCountsProvider.class)
                                                        AnimatedImageDrawableTest.RepeatImage image)
                    throws IOException {
        int resId = image.resId;
        try (ParcelFileDescriptor pfd = open(resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestIsAnimated(aimagedecoder, true);
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(resId));
        }
    }

    @Test
    public void testRepeatCount(
                    @TestParameter(valuesProvider = AnimatedImagesPlusRepeatCountsProvider.class)
                                                        AnimatedImageDrawableTest.RepeatImage image)
                    throws IOException {
        int resId = image.resId;
        int repeatCount = image.repeatCount;
        try (ParcelFileDescriptor pfd = open(resId)) {
            long aimagedecoder = nCreateFromFd(pfd.getFd());

            nTestRepeatCount(aimagedecoder, repeatCount);
        } catch (FileNotFoundException e) {
            fail("Could not open " + Utils.getAsResourceUri(resId));
        }
    }

    private static class RepeatCountStillProvider extends TestParameterValuesProvider {
        @Override
        public List<NamedParam<Integer>> provideValues(Context context) {
            return Arrays.asList(
                    new NamedParam<Integer>("still_with_loop_count.gif", 1),
                    new NamedParam<Integer>("webp_still_with_loop_count.webp", 31999)
            );
        }
    }

    @Test
    public void testRepeatCountStill(
                    @TestParameter(valuesProvider = RepeatCountStillProvider.class)
                                                        NamedParam<Integer> input) {
        String name = input.name;
        int repeatCount = input.value;
        long asset = nOpenAsset(getAssetManager(), name);
        long aimagedecoder = nCreateFromAsset(asset);

        nTestRepeatCount(aimagedecoder, repeatCount);
        nCloseAsset(asset);
    }

    // Return a pointer to the native AAsset named |file|. Must be closed with nCloseAsset.
    // Throws an Exception on failure.
    private static native long nOpenAsset(AssetManager assets, String file);
    private static native void nCloseAsset(long asset);

    // Methods for creating and returning a pointer to an AImageDecoder. All
    // throw an Exception on failure.
    private static native long nCreateFromFd(int fd);
    private static native long nCreateFromAsset(long asset);
    private static native long nCreateFromAssetFd(long asset);
    private static native long nCreateFromAssetBuffer(long asset);

    private static native void nTestEmptyCreate();
    private static native void nTestNullDecoder(AssetManager assets, String file);
    private static native void nTestCreateIncomplete(AssetManager assets,
            String file, int truncatedLength);
    private static native void nTestCreateUnsupported(AssetManager assets, String file);

    // For convenience, all methods that take aimagedecoder as a parameter delete
    // it.
    private static native void nTestInfo(long aimagedecoder, int width, int height,
            String mimeType, boolean isF16, int dataspace);
    private static native void nTestSetFormat(long aimagedecoder, boolean isF16, boolean isGray);
    private static native void nTestSetUnpremul(long aimagedecoder, boolean hasAlpha);
    private static native void nTestGetMinimumStride(long aimagedecoder,
            boolean isF16, boolean isGray);
    private static native void nTestDecode(long aimagedecoder,
            int requestedAndroidBitmapFormat, boolean unpremul, Bitmap bitmap);
    private static native void nTestDecodeStride(long aimagedecoder);
    private static native void nTestSetTargetSize(long aimagedecoder);
    // Decode with the target width and height to match |bitmap|.
    private static native void nTestDecodeScaled(long aimagedecoder, Bitmap bitmap);
    private static native void nTestComputeSampledSize(long aimagedecoder, Bitmap bm,
            int sampleSize);
    private static native void nTestSetCrop(AssetManager assets, String file);
    // Decode and compare to |bitmap|, where they both use the specified target
    // size and crop rect. target size of 0 means to skip scaling.
    private static native void nTestDecodeCrop(long aimagedecoder,
            Bitmap bitmap, int targetWidth, int targetHeight,
            int cropLeft, int cropTop, int cropRight, int cropBottom);
    private static native void nTestScalePlusUnpremul(long aimagedecoder);
    private static native void nTestDecode(long aimagedecoder, Bitmap bm, int dataSpace);
    private static native void nTestIsAnimated(long aimagedecoder, boolean animated);
    private static native void nTestRepeatCount(long aimagedecoder, int repeatCount);
}
