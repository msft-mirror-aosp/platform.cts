/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package android.photopicker.cts

import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.cts.photopicker.lib.MediaType
import android.cts.photopicker.lib.PhotoPickerTestRule
import android.cts.photopicker.lib.TestMedia
import android.cts.photopicker.lib.WithTestMedia
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.MediaStore
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.providers.media.flags.Flags
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test suite for verifying file access for media selected through the Photo Picker.
 *
 * This class contains tests that cover various aspects of file access after a user has selected
 * media items.
 *
 * Tests are executed for different intents, including `ACTION_PICK_IMAGES` and
 * `ACTION_GET_CONTENT`, to ensure consistent behavior across different entry points to the Photo
 * Picker.
 */
@RunWith(AndroidJUnit4::class)
class PhotoPickerFileAccessTest {

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule
    val photoPickerRule =
        PhotoPickerTestRule(InstrumentationRegistry.getInstrumentation().targetContext)

    @Before
    fun setup() {
        Assume.assumeTrue(PhotoPickerTestRule.isHardwareSupported())
    }

    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE)])
    fun testPersistentGrant_withPickImages_succeeds() {
        val resultFuture = photoPickerRule.launchPhotoPicker(Intent(MediaStore.ACTION_PICK_IMAGES))
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        val uri = checkNotNull(result.getSelectedMedia().first())

        assertCanTakePersistedGrant(uri)
    }

    // GET_CONTENT takeover requires a DeviceConfig override on S, so skip in CTS.
    @SdkSuppress(excludedSdks = [Build.VERSION_CODES.S, Build.VERSION_CODES.S_V2])
    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE)])
    fun testPersistentGrant_withGetContent_succeeds() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        val resultFuture = photoPickerRule.launchPhotoPicker(intent)
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        val uri = checkNotNull(result.getSelectedMedia().first())

        assertCanTakePersistedGrant(uri)
    }

    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE)])
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    fun testMediaStoreOpenFile_withPickImages_succeeds() {
        val resultFuture = photoPickerRule.launchPhotoPicker(Intent(MediaStore.ACTION_PICK_IMAGES))
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        val uri = checkNotNull(result.getSelectedMedia().first())

        val resolver = photoPickerRule.activity.contentResolver
        resolver.openFileDescriptor(uri, "r", CancellationSignal()).use { pfd1 ->
            MediaStore.openFileDescriptor(resolver, uri, "r", CancellationSignal()).use { pfd2 ->
                val size1 = Os.fstat(pfd1?.fileDescriptor).st_size
                val size2 = Os.fstat(pfd2?.fileDescriptor).st_size
                assertThat(size1).isEqualTo(size2)
                assertThat(size1).isGreaterThan(0)
            }
        }
    }

    // GET_CONTENT takeover requires a DeviceConfig override on S, so skip in CTS.
    @SdkSuppress(excludedSdks = [Build.VERSION_CODES.S, Build.VERSION_CODES.S_V2])
    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE)])
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    fun testMediaStoreOpenFile_withGetContent_succeeds() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        val resultFuture = photoPickerRule.launchPhotoPicker(intent)
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        val uri = checkNotNull(result.getSelectedMedia().first())

        val resolver = photoPickerRule.activity.contentResolver
        resolver.openFileDescriptor(uri, "r", CancellationSignal()).use { pfd1 ->
            MediaStore.openFileDescriptor(resolver, uri, "r", CancellationSignal()).use { pfd2 ->
                val size1 = Os.fstat(pfd1?.fileDescriptor).st_size
                val size2 = Os.fstat(pfd2?.fileDescriptor).st_size
                assertThat(size1).isEqualTo(size2)
                assertThat(size1).isGreaterThan(0)
            }
        }
    }

    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE)])
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    fun testMediaStoreOpenAssetFile_withPickImages_succeeds() {
        val resultFuture = photoPickerRule.launchPhotoPicker(Intent(MediaStore.ACTION_PICK_IMAGES))
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        val uri = checkNotNull(result.getSelectedMedia().first())

        val resolver = photoPickerRule.activity.contentResolver
        resolver.openAssetFileDescriptor(uri, "r", CancellationSignal()).use {
            afd1: AssetFileDescriptor? ->
            MediaStore.openAssetFileDescriptor(resolver, uri, "r", CancellationSignal()).use {
                afd2: AssetFileDescriptor? ->
                assertThat(afd1?.length).isEqualTo(afd2?.length)
                assertThat(afd1?.length).isGreaterThan(0)
            }
        }
    }

    // GET_CONTENT takeover requires a DeviceConfig override on S, so skip in CTS.
    @SdkSuppress(excludedSdks = [Build.VERSION_CODES.S, Build.VERSION_CODES.S_V2])
    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE)])
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    fun testMediaStoreOpenAssetFile_withGetContent_succeeds() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        val resultFuture = photoPickerRule.launchPhotoPicker(intent)
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        val uri = checkNotNull(result.getSelectedMedia().first())

        val resolver = photoPickerRule.activity.contentResolver
        resolver.openAssetFileDescriptor(uri, "r", CancellationSignal()).use {
            afd1: AssetFileDescriptor? ->
            MediaStore.openAssetFileDescriptor(resolver, uri, "r", CancellationSignal()).use {
                afd2: AssetFileDescriptor? ->
                assertThat(afd1?.length).isEqualTo(afd2?.length)
                assertThat(afd1?.length).isGreaterThan(0)
            }
        }
    }

    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE)])
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    fun testMediaStoreOpenTypedAssetFile_withPickImages_succeeds() {
        val resultFuture = photoPickerRule.launchPhotoPicker(Intent(MediaStore.ACTION_PICK_IMAGES))
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        val uri = checkNotNull(result.getSelectedMedia().first())

        val resolver = photoPickerRule.activity.contentResolver
        resolver.openTypedAssetFileDescriptor(uri, "*/*", null, CancellationSignal()).use {
            afd1: AssetFileDescriptor? ->
            MediaStore.openTypedAssetFileDescriptor(
                    resolver,
                    uri,
                    "*/*",
                    null,
                    CancellationSignal(),
                )
                .use { afd2: AssetFileDescriptor? ->
                    assertThat(afd1?.length).isEqualTo(afd2?.length)
                    assertThat(afd1?.length).isGreaterThan(0)
                }
        }
    }

    // GET_CONTENT takeover requires a DeviceConfig override on S, so skip in CTS.
    @SdkSuppress(excludedSdks = [Build.VERSION_CODES.S, Build.VERSION_CODES.S_V2])
    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE)])
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    fun testMediaStoreOpenTypedAssetFile_withGetContent_succeeds() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        val resultFuture = photoPickerRule.launchPhotoPicker(intent)
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        val uri = checkNotNull(result.getSelectedMedia().first())

        val resolver = photoPickerRule.activity.contentResolver
        resolver.openTypedAssetFileDescriptor(uri, "*/*", null, CancellationSignal()).use { afd1 ->
            MediaStore.openTypedAssetFileDescriptor(
                    resolver,
                    uri,
                    "*/*",
                    null,
                    CancellationSignal(),
                )
                .use { afd2 ->
                    assertThat(afd1?.length).isEqualTo(afd2?.length)
                    assertThat(afd1?.length).isGreaterThan(0)
                }
        }
    }

    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE)])
    fun testLocationMetadataRedacted_withPickImages_succeeds() {
        val resultFuture = photoPickerRule.launchPhotoPicker(Intent(MediaStore.ACTION_PICK_IMAGES))
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        val uri = checkNotNull(result.getSelectedMedia().first())

        assertLocationMetadataIsRedacted(uri)
    }

    // GET_CONTENT takeover requires a DeviceConfig override on S, so skip in CTS.
    @SdkSuppress(excludedSdks = [Build.VERSION_CODES.S, Build.VERSION_CODES.S_V2])
    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE)])
    fun testLocationMetadataRedacted_withGetContent_succeeds() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        val resultFuture = photoPickerRule.launchPhotoPicker(intent)
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        val uri = checkNotNull(result.getSelectedMedia().first())

        assertLocationMetadataIsRedacted(uri)
    }

    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.VIDEO)])
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    fun testVideoLocationMetadataRedacted_withPickImages_succeeds() {
        val resultFuture = photoPickerRule.launchPhotoPicker(Intent(MediaStore.ACTION_PICK_IMAGES))
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        val uri = checkNotNull(result.getSelectedMedia().first())

        assertVideoLocationMetadataIsRedacted(uri)
    }

    // GET_CONTENT takeover requires a DeviceConfig override on S, so skip in CTS.
    @SdkSuppress(excludedSdks = [Build.VERSION_CODES.S, Build.VERSION_CODES.S_V2])
    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.VIDEO)])
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    fun testVideoLocationMetadataRedacted_withGetContent_succeeds() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).setType("video/*")
        val resultFuture = photoPickerRule.launchPhotoPicker(intent)
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        val uri = checkNotNull(result.getSelectedMedia().first())

        assertVideoLocationMetadataIsRedacted(uri)
    }

    /**
     * Asserts that location metadata is redacted from a video file.
     *
     * This method reads the video file as a raw string and verifies that specific XMP location tags
     * have been removed, while other non-location XMP data is still present.
     *
     * NOTE: This is not a general purpose method, this method checks a very specific video file
     * from the PhotoPickerTestRule's resources.
     *
     * @param uri The [Uri] of the video file to check.
     */
    private fun assertVideoLocationMetadataIsRedacted(uri: Uri) {
        val resolver = photoPickerRule.activity.contentResolver
        resolver.openInputStream(uri).use { inputStream ->
            val videoBytes = checkNotNull(inputStream).readBytes()
            val videoString = String(videoBytes)

            // Verify that location-specific XMP data is redacted.
            assertThat(videoString).doesNotContain("10,41.751000E")
            assertThat(videoString).doesNotContain("53,50.070500N")
            // Verify that some non-location XMP data is still present.
            assertThat(videoString).contains("13166/7763")
        }
    }

    /**
     * Asserts that location metadata is redacted from an image file.
     *
     * This method uses [ExifInterface] to verify that both EXIF geolocation tags and XMP location
     * data have been removed, while ensuring other non-location metadata is preserved.
     *
     * @param uri The [Uri] of the image file to check.
     */
    private fun assertLocationMetadataIsRedacted(uri: Uri) {
        val resolver = photoPickerRule.activity.contentResolver
        resolver.openInputStream(uri).use { inputStream ->
            val exif = ExifInterface(checkNotNull(inputStream))

            // Verify that latitude and longitude are redacted.
            val latLong = floatArrayOf(1f, 1f)
            exif.getLatLong(latLong)
            assertThat(latLong[0]).isWithin(0.001f).of(0f)
            assertThat(latLong[1]).isWithin(0.001f).of(0f)

            // Verify that location is redacted from XMP metadata, but other XMP data is preserved.
            val xmp = exif.getAttribute(ExifInterface.TAG_XMP)
            assertThat(xmp).isNotNull()
            assertThat(xmp).doesNotContain("10,41.751000E")
            assertThat(xmp).doesNotContain("53,50.070500N")
            assertThat(xmp).contains("LensDefaults")
        }
    }

    /**
     * Asserts that a persistable read URI permission grant can be successfully taken for the given
     * [Uri].
     *
     * The test will fail with a [SecurityException] if the grant cannot be taken. It also verifies
     * that the grant is correctly listed in the `persistedUriPermissions`.
     *
     * @param uri The [Uri] for which to take the persistable grant.
     */
    private fun assertCanTakePersistedGrant(uri: Uri) {
        val resolver = photoPickerRule.activity.contentResolver
        // This call will throw a SecurityException if the grant cannot be taken, failing the test.
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // For completeness, also verify that the grant is now in the list of persisted grants.
        val hasReadPermission =
            resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

        assertThat(hasReadPermission).isTrue()
    }
}
