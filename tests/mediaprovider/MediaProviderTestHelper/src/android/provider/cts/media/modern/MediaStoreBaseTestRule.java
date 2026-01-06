/*
 * Copyright (C) 2026 The Android Open Source Project
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

package src.android.provider.cts.media.modern;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.google.common.io.ByteStreams;

import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class MediaStoreBaseTestRule extends ExternalResource {

    private static final String TAG = "MediaStoreBaseTest";

    private static final long POLLING_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(20);
    private static final long POLLING_SLEEP_MILLIS = 100;

    @Override
    public void before() throws Exception {
        setupPublicVolume();
    }

    @Override
    public void after() {
        try {
            executeShellCommand("sm set-virtual-disk false");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setupPublicVolume() throws Exception {
        createPublicVolume();
        final String publicVolumeName = getCurrentPublicVolumeName();
        assertWithMessage("Expected public volume name to be not null")
                .that(publicVolumeName)
                .isNotNull();
    }

    private static void createPublicVolume() throws Exception {
        preparePublicVolume();
        assertWithMessage("Expected newly created public volume name to be not null")
                .that(getCurrentPublicVolumeName())
                .isNotNull();
    }

    /** Executes a shell command. */
    public static String executeShellCommand(String pattern, Object... args) throws IOException {
        String command = String.format(pattern, args);
        int attempt = 0;
        while (attempt++ < 5) {
            try {
                return executeShellCommandInternal(command);
            } catch (InterruptedIOException e) {
                // Hmm, we had trouble executing the shell command; the best we
                // can do is try again a few more times
                Log.v(TAG, "Trouble executing " + command + "; trying again", e);
            }
        }
        throw new IOException("Failed to execute " + command);
    }

    private static String executeShellCommandInternal(String cmd) throws IOException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try (FileInputStream output =
                new FileInputStream(uiAutomation.executeShellCommand(cmd).getFileDescriptor())) {
            return new String(ByteStreams.toByteArray(output));
        }
    }

    public static ContentResolver getContentResolver() {
        return getContext().getContentResolver();
    }

    /** Asserts the given operation throws an exception of type {@code T}. */
    public static <T extends Exception> void assertThrows(Class<T> clazz, Operation<Exception> r)
            throws Exception {
        assertThrows(clazz, "", r);
    }

    /** Asserts the given operation throws an exception of type {@code T}. */
    public static <T extends Exception> void assertThrows(
            Class<T> clazz, String errMsg, Operation<Exception> r) throws Exception {
        try {
            r.run();
            fail("Expected " + clazz + " to be thrown");
        } catch (Exception e) {
            if (!clazz.isAssignableFrom(e.getClass()) || !e.getMessage().contains(errMsg)) {
                Log.e(TAG, "Expected " + clazz + " exception with error message: " + errMsg, e);
                throw e;
            }
        }
    }

    @FunctionalInterface
    public interface Operation<T extends Exception> {
        /** This is the method that gets called for any object that implements this interface. */
        void run() throws T;
    }

    public static File getExternalStorageDir(String volumeName) {
        if (volumeName.equalsIgnoreCase(MediaStore.VOLUME_EXTERNAL_PRIMARY)) {
            return Environment.getExternalStorageDirectory();
        } else {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        }
    }

    private static boolean isObbDirUnmounted() {
        try {
            for (String line : executeShellCommand("cat /proc/mounts").split("\n")) {
                String[] split = line.split(" ");
                // Only check obb dirs with tmpfs, as if it's mounted for app data
                // isolation, it will be tmpfs only.
                if (split[0].equals("tmpfs")
                        && split[1].startsWith("/storage/")
                        && split[1].endsWith("/obb")) {
                    return false;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to execute shell command", e);
        }
        return true;
    }

    private static boolean isVolumeMounted(String type) {
        try {
            final String volume = executeShellCommand("sm list-volumes " + type).trim();
            return volume != null && volume.contains(" mounted");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isPublicVolumeMounted() {
        return isVolumeMounted("public");
    }

    private static boolean isEmulatedVolumeMounted() {
        return isVolumeMounted("emulated");
    }

    private static boolean isFuseReady() {
        for (String volumeName : MediaStore.getExternalVolumeNames(getContext())) {
            final Uri uri = MediaStore.Files.getContentUri(volumeName);
            try (Cursor c = getContentResolver().query(uri, null, null, null)) {
                assertThat(c).isNotNull();
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return true;
    }

    /** Prepare or create a public volume for testing */
    public static void preparePublicVolume() throws Exception {
        if (getCurrentPublicVolumeName() == null) {
            createNewPublicVolume();
        }

        pollForCondition(
                MediaStoreBaseTestRule::isPublicVolumeMounted,
                "Timed out while waiting for public volume");
        pollForCondition(
                MediaStoreBaseTestRule::isEmulatedVolumeMounted,
                "Timed out while waiting for emulated volume");
        pollForCondition(MediaStoreBaseTestRule::isFuseReady, "Timed out while waiting for fuse");
    }

    /** Unmount app's obb and data dirs. */
    public static void unmountAppDirs() throws Exception {
        if (isObbDirUnmounted()) {
            return;
        }
        executeShellCommand(
                "sm unmount-app-data-dirs "
                        + getContext().getPackageName()
                        + " "
                        + android.os.Process.myPid()
                        + " "
                        + android.os.UserHandle.myUserId());
        pollForCondition(
                MediaStoreBaseTestRule::isObbDirUnmounted,
                "Timed out while waiting for unmounting obb dir");
    }

    /** Creates a new virtual public volume and returns the volume's name. */
    public static void createNewPublicVolume() throws Exception {
        // Unmount data and obb dirs for test app first so test app won't be killed during
        // volume unmount.
        unmountAppDirs();
        executeShellCommand("sm set-virtual-disk true");
        Thread.sleep(2000);
        pollForCondition(
                MediaStoreBaseTestRule::partitionDisk,
                "Timed out while waiting for disk partitioning");
    }

    private static boolean partitionDisk() {
        try {
            final String listDisks = executeShellCommand("sm list-disks").trim();
            if (TextUtils.isEmpty(listDisks)) {
                return false;
            }
            executeShellCommand("sm partition " + listDisks + " public");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @return the currently mounted public volume, if any.
     */
    public static String getCurrentPublicVolumeName() {
        final String[] allVolumeDetails;
        try {
            allVolumeDetails = executeShellCommand("sm list-volumes").trim().split("\n");
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute shell command", e);
            return null;
        }
        for (String volDetails : allVolumeDetails) {
            if (volDetails.startsWith("public") && volDetails.contains("mounted")) {
                final String[] publicVolumeDetails = volDetails.trim().split(" ");
                String res = publicVolumeDetails[publicVolumeDetails.length - 1];
                if ("null".equals(res)) {
                    continue;
                }
                return res;
            }
        }
        return null;
    }

    public static void pollForCondition(Supplier<Boolean> condition, String errorMessage)
            throws Exception {
        for (int i = 0; i < POLLING_TIMEOUT_MILLIS / POLLING_SLEEP_MILLIS; i++) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(POLLING_SLEEP_MILLIS);
        }
        throw new TimeoutException(errorMessage);
    }
}
