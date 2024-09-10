/*
 * Copyright 2023 The Android Open Source Project
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

package android.virtualdevice.cts.camera;

import static android.Manifest.permission.CAMERA;
import static android.graphics.ImageFormat.JPEG;
import static android.graphics.ImageFormat.YUV_420_888;
import static android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START;
import static android.opengl.EGL14.EGL_ALPHA_SIZE;
import static android.opengl.EGL14.EGL_BLUE_SIZE;
import static android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION;
import static android.opengl.EGL14.EGL_DEFAULT_DISPLAY;
import static android.opengl.EGL14.EGL_GREEN_SIZE;
import static android.opengl.EGL14.EGL_NONE;
import static android.opengl.EGL14.EGL_NO_CONTEXT;
import static android.opengl.EGL14.EGL_NO_DISPLAY;
import static android.opengl.EGL14.EGL_NO_SURFACE;
import static android.opengl.EGL14.EGL_RED_SIZE;
import static android.opengl.EGL14.eglChooseConfig;
import static android.opengl.EGL14.eglCreateContext;
import static android.opengl.EGL14.eglDestroyContext;
import static android.opengl.EGL14.eglGetDisplay;
import static android.opengl.EGL14.eglGetError;
import static android.opengl.EGL14.eglInitialize;
import static android.opengl.EGL14.eglMakeCurrent;
import static android.opengl.GLES20.GL_MAX_TEXTURE_SIZE;
import static android.opengl.GLES20.glGetIntegerv;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import static java.lang.Byte.toUnsignedInt;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.companion.virtual.camera.VirtualCameraStreamConfig;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.cts.rs.BitmapUtils;
import android.media.Image;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.ColorInt;

import com.google.common.collect.Iterables;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class VirtualCameraUtils {
    static final String BACK_CAMERA_ID = "0";
    static final String FRONT_CAMERA_ID = "1";
    static final CameraCharacteristics.Key<Integer> INFO_DEVICE_ID =
            new CameraCharacteristics.Key<Integer>("android.info.deviceId", int.class);
    private static final long TIMEOUT_MILLIS = 2000L;
    private static final float EPSILON = 0.3f;
    private static final int TEST_VIDEO_SEEK_TIME_MS = 2000;
    private static final String TAG = "VirtualCameraUtils";

    static VirtualCameraConfig createVirtualCameraConfig(
            int width, int height, int format, int maximumFramesPerSecond, int sensorOrientation,
            int lensFacing, String name, Executor executor, VirtualCameraCallback callback) {
        return new VirtualCameraConfig.Builder(name)
                .addStreamConfig(width, height, format, maximumFramesPerSecond)
                .setVirtualCameraCallback(executor, callback)
                .setSensorOrientation(sensorOrientation)
                .setLensFacing(lensFacing)
                .build();
    }

    static void assertVirtualCameraConfig(VirtualCameraConfig config, int width, int height,
            int format, int maximumFramesPerSecond, int sensorOrientation, int lensFacing,
            String name) {
        assertThat(config.getName()).isEqualTo(name);
        assertThat(config.getStreamConfigs()).hasSize(1);
        VirtualCameraStreamConfig streamConfig =
                Iterables.getOnlyElement(config.getStreamConfigs());
        assertThat(streamConfig.getWidth()).isEqualTo(width);
        assertThat(streamConfig.getHeight()).isEqualTo(height);
        assertThat(streamConfig.getFormat()).isEqualTo(format);
        assertThat(streamConfig.getMaximumFramesPerSecond()).isEqualTo(maximumFramesPerSecond);
        assertThat(config.getSensorOrientation()).isEqualTo(sensorOrientation);
        assertThat(config.getLensFacing()).isEqualTo(lensFacing);
    }

    static void paintSurface(Surface surface, @ColorInt int color) {
        Canvas canvas = surface.lockCanvas(null);
        canvas.drawColor(color);
        surface.unlockCanvasAndPost(canvas);
    }

    static void paintSurfaceRed(Surface surface) {
        paintSurface(surface, Color.RED);
    }

    // Converts YUV to ARGB int representation,
    // using BT601 full-range matrix.
    // See https://www.itu.int/rec/T-REC-T.871-201105-I/en
    private static int yuv2rgb(int y, int u, int v) {
        int r = (int) max(0f, min(255f, round((y + 1.402f * (v - 128f)))));
        int g = (int) max(0f,
                min(255f, round(y - 0.344136f * (u - 128f) - 0.714136f * (v - 128f))));
        int b = (int) max(0f, min(255f, round(y + 1.772f * (u - 128f))));
        return Color.rgb(r, g, b);
    }

    // Compares two ARGB colors and returns true if they are approximately
    // the same color.
    private static boolean areColorsAlmostIdentical(int colorA, int colorB) {
        float a1 = ((colorA >> 24) & 0xff) / 255f;
        float r1 = ((colorA >> 16) & 0xff) / 255f;
        float g1 = ((colorA >> 4) & 0xff) / 255f;
        float b1 = (colorA & 0xff) / 255f;

        float a2 = ((colorB >> 24) & 0xff) / 255f;
        float r2 = ((colorB >> 16) & 0xff) / 255f;
        float g2 = ((colorB >> 4) & 0xff) / 255f;
        float b2 = (colorB & 0xff) / 255f;

        float mse = ((a1 - a2) * (a1 - a2)
                + (r1 - r2) * (r1 - r2)
                + (g1 - g2) * (g1 - g2)
                + (b1 - b2) * (b1 - b2)) / 4;

        return mse < EPSILON;
    }

    private static boolean yuv420ImageHasColor(Image image, int color) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        for (int j = 0; j < height; ++j) {
            int jChroma = j / 2;
            for (int i = 0; i < width; ++i) {
                int iChroma = i / 2;
                int y = toUnsignedInt(planes[0].getBuffer().get(
                        j * planes[0].getRowStride() + i * planes[0].getPixelStride()));
                int u = toUnsignedInt(planes[1].getBuffer().get(
                        jChroma * planes[1].getRowStride() + iChroma * planes[1].getPixelStride()));
                int v = toUnsignedInt(planes[2].getBuffer().get(
                        jChroma * planes[2].getRowStride() + iChroma * planes[2].getPixelStride()));
                int argb = yuv2rgb(y, u, v);
                if (!areColorsAlmostIdentical(argb, color)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean jpegImageHasColor(Image image, int color) throws IOException {
        Bitmap bitmap = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(image.getPlanes()[0].getBuffer())).copy(
                Bitmap.Config.ARGB_8888, false);
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        for (int j = 0; j < height; ++j) {
            for (int i = 0; i < width; ++i) {
                if (!areColorsAlmostIdentical(bitmap.getColor(i, j).toArgb(), color)) {
                    return false;
                }
            }
        }
        return true;
    }

    // TODO(b/316326725) Turn this into proper custom matcher.
    static boolean imageHasColor(Image image, int color) throws IOException {
        return switch (image.getFormat()) {
            case YUV_420_888 -> yuv420ImageHasColor(image, color);
            case JPEG -> jpegImageHasColor(image, color);
            default -> {
                fail("Encountered unsupported image format: " + image.getFormat());
                yield false;
            }
        };
    }

    static int toFormat(String str) {
        if (str.equals("YUV_420_888")) {
            return YUV_420_888;
        }
        if (str.equals("RGBA_888")) {
            return PixelFormat.RGBA_8888;
        }
        if (str.equals("JPEG")) {
            return JPEG;
        }

        fail("Unknown pixel format string: " + str);
        return PixelFormat.UNKNOWN;
    }

    /**
     * Will write the image to disk so it can be pulled by the collector in case of error
     *
     * @see com.android.tradefed.device.metric.FilePullerLogCollector
     */
    private static void writeImageToDisk(String imageName, Bitmap bitmap) {
        File dir = getApplicationContext().getFilesDir();
        // The FilePullerLogCollector only pulls image in png
        File imageFile = new File(dir, imageName + ".png");
        try {
            Log.i(TAG, "Saving image to disk at " + imageFile.getAbsolutePath());
            bitmap.compress(Bitmap.CompressFormat.PNG, 80, new FileOutputStream(imageFile));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param generated Bitmap generated from the test.
     * @param golden    Golden bitmap to compare to.
     * @param prefix    Prefix for the image file generated in case of error.
     */
    static void assertImagesSimilar(Bitmap generated, Bitmap golden, String prefix,
            double maxDiff) {
        boolean assertionPassed = false;
        try {
            double actual = BitmapUtils.calcDifferenceMetric(generated, golden);
            assertWithMessage("Generated image does not match golden. "
                    + "Images have been saved to disk.").that(actual).isAtMost(maxDiff);
            assertionPassed = true;
        } finally {
            if (!assertionPassed) {
                writeImageToDisk(prefix + "_generated", generated);
                writeImageToDisk(prefix + "_golden", golden);
            }
        }
    }

    static class VideoRenderer implements Consumer<Surface> {
        private final MediaPlayer mPlayer;
        private final CountDownLatch mLatch;
        private final Uri mUri;

        VideoRenderer(int resId) {
            String path =
                    "android.resource://" + getApplicationContext().getPackageName() + "/" + resId;
            mUri = Uri.parse(path);
            mPlayer = MediaPlayer.create(getApplicationContext(), mUri);
            mLatch = new CountDownLatch(1);

            mPlayer.setOnInfoListener((mp, what, extra) -> {
                if (what == MEDIA_INFO_VIDEO_RENDERING_START) {
                    mLatch.countDown();
                    return true;
                }
                return false;
            });
        }

        @Override
        public void accept(Surface surface) {
            mPlayer.setSurface(surface);
            mPlayer.seekTo(TEST_VIDEO_SEEK_TIME_MS);
            mPlayer.start();
            try {
                // Block until media player has drawn the first video frame
                assertWithMessage("Media player did not notify first frame on time")
                        .that(mLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                        .isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        Bitmap getGoldenBitmap() {
            // Get the frame at a specific time (in microseconds) or the first frame 🐶
            try (MediaMetadataRetriever goldenRetriever = new MediaMetadataRetriever()) {
                goldenRetriever.setDataSource(getApplicationContext(), mUri);
                Bitmap frame =
                        goldenRetriever.getFrameAtTime(
                                TEST_VIDEO_SEEK_TIME_MS, MediaMetadataRetriever.OPTION_CLOSEST);
                assertNotNull("Can't extract golden frame for test video.", frame);
                return frame;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static Bitmap loadBitmapFromRaw(int rawResId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        return BitmapFactory.decodeResource(getApplicationContext().getResources(),
                rawResId, options);
    }

    static Bitmap jpegImageToBitmap(Image image) throws IOException {
        assertThat(image.getFormat()).isEqualTo(JPEG);
        return ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(image.getPlanes()[0].getBuffer())).copy(
                Bitmap.Config.ARGB_8888, false);
    }

    static void grantCameraPermission(int deviceId) {
        Context deviceContext = getInstrumentation().getTargetContext()
                .createDeviceContext(deviceId);
        deviceContext.getPackageManager().grantRuntimePermission("android.virtualdevice.cts.camera",
                CAMERA, UserHandle.of(deviceContext.getUserId()));
    }

    static int getMaximumTextureSize() {
        EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        assumeFalse(eglDisplay.equals(EGL_NO_DISPLAY));
        int[] version = new int[2];
        if (!eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw new IllegalStateException(
                    "eglInitialize() returned false. Can't query maximum texture size\n "
                            + "eglGetError():" + eglGetError());
        }

        int[] attribList = {EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
                EGL_ALPHA_SIZE, 8, EGL_NONE};

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!eglChooseConfig(
                eglDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)) {
            throw new IllegalStateException(
                    "eglChooseConfig() returned false. Can't query maximum texture size\n"
                            + "eglGetError():" + eglGetError());
        }


        int[] attrib2_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
        EGLContext eglContext = eglCreateContext(eglDisplay, configs[0], EGL_NO_CONTEXT,
                attrib2_list, 0);
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, eglContext);

        int[] maxSize = new int[1];
        glGetIntegerv(GL_MAX_TEXTURE_SIZE, maxSize, 0);

        eglDestroyContext(eglDisplay, eglContext);

        return maxSize[0];
    }

    /**
     * Creates a new Handler with a thread named with the provided suffix.
     */
    public static Handler createHandler(String threadSuffix) {
        HandlerThread handlerThread = new HandlerThread("VirtualCameraTestHandler_" + threadSuffix);
        handlerThread.start();
        return new Handler(handlerThread.getLooper());
    }

    private VirtualCameraUtils() {}
}
