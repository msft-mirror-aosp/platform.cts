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

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.cts.photopicker.lib.LegacyPhotopickerOnly
import android.cts.photopicker.lib.MediaType
import android.cts.photopicker.lib.ModernPhotopickerOnly
import android.cts.photopicker.lib.PhotoPickerTestRule
import android.cts.photopicker.lib.TestMedia
import android.cts.photopicker.lib.WithTestMedia
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.nene.TestApis
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoPickerApiSurfaceTest {

    @get:Rule
    val photoPickerRule =
        PhotoPickerTestRule(InstrumentationRegistry.getInstrumentation().targetContext)

    // ACTION_PICK_IMAGES

    @Test
    @WithTestMedia(
        media =
            [
                TestMedia(type = MediaType.IMAGE, count = 5),
                TestMedia(type = MediaType.VIDEO, count = 5),
            ]
    )
    fun testSelectSingleImageAndVerifyResult() {
        val resultFuture = photoPickerRule.launchPhotoPicker()

        // Now, interact with the UI.
        photoPickerRule.selectItem(0)
        val result = resultFuture.get(10, TimeUnit.SECONDS)

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        assertThat(result.data).isNotNull()
        assertThat(result.data?.data).isNotNull()
    }

    @Test
    @WithTestMedia(
        media =
            [
                TestMedia(type = MediaType.IMAGE, count = 5),
                TestMedia(type = MediaType.VIDEO, count = 5),
            ]
    )
    fun testSelectMultipleImageAndVerifyResult() {
        val intent = Intent(MediaStore.ACTION_PICK_IMAGES)
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit())

        val resultFuture = photoPickerRule.launchPhotoPicker(intent)

        // Now, interact with the UI.
        photoPickerRule.selectItem(0)
        photoPickerRule.selectItem(1)
        photoPickerRule.confirmSelection()

        // Wait for the future to complete and get the result.
        val result = resultFuture.get(10, TimeUnit.SECONDS)

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        assertThat(result.data).isNotNull()
        assertThat(result.getSelectedMedia().size).isEqualTo(2)
    }

    @Test
    fun testMultiSelect_withInvalidMax_returnsCanceled() {
        // Test with a value over the limit.
        val intentOverLimit =
            Intent(MediaStore.ACTION_PICK_IMAGES)
                .putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit() + 1)
        var result = photoPickerRule.launchPhotoPicker(intentOverLimit).get(5, TimeUnit.SECONDS)
        assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)

        // Test with a negative value.
        val intentNegative =
            Intent(MediaStore.ACTION_PICK_IMAGES).putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, -1)
        result = photoPickerRule.launchPhotoPicker(intentNegative).get(5, TimeUnit.SECONDS)
        assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
    }

    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE, count = 3)])
    // Only run on Modern as the legacy snackbar covers the Done button
    // and this fails / is highly flaky.
    @ModernPhotopickerOnly
    fun testMultiSelect_respectsMaxLimit() {
        val maxCount = 2
        val intent =
            Intent(MediaStore.ACTION_PICK_IMAGES)
                .putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, maxCount)
        val resultFuture = photoPickerRule.launchPhotoPicker(intent)

        // Attempt to select more items than allowed.
        photoPickerRule.selectItem(0)
        photoPickerRule.selectItem(1)
        photoPickerRule.selectItem(2)
        photoPickerRule.confirmSelection()

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        assertThat(result.getSelectedMedia()).hasSize(maxCount)
    }

    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE, count = 2)])
    fun testSingleSelect_ignoresExtraAllowMultiple() {
        val intent =
            Intent(MediaStore.ACTION_PICK_IMAGES).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        val resultFuture = photoPickerRule.launchPhotoPicker(intent)

        // In single-select mode, clicking an item should immediately return a result.
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        assertThat(result.getSelectedMedia().size).isEqualTo(1)
    }

    @Test
    fun testLaunch_withUnsupportedMimeType_throwsException() {
        val intent = Intent(MediaStore.ACTION_PICK_IMAGES).setType("audio/*")
        // ACTION_PICK_IMAGES does not support non-image/video mime types and should fail to launch.
        assertFailsWith<ActivityNotFoundException> {
            photoPickerRule.launchPhotoPicker(intent).get(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun testLaunch_withUnsupportedExtraMimeType_returnsCanceled() {
        val intent =
            Intent(MediaStore.ACTION_PICK_IMAGES)
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*"))
        val result = photoPickerRule.launchPhotoPicker(intent).get(5, TimeUnit.SECONDS)
        assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
    }

    @Test
    @LegacyPhotopickerOnly
    fun testLaunchLegacy_withInvalidTab_returnsCanceled() {
        val intent =
            Intent(MediaStore.ACTION_PICK_IMAGES)
                .putExtra(MediaStore.EXTRA_PICK_IMAGES_LAUNCH_TAB, -1)
        val result = photoPickerRule.launchPhotoPicker(intent).get(5, TimeUnit.SECONDS)
        assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
    }

    @Test
    @ModernPhotopickerOnly
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE)])
    fun testLaunchModern_withInvalidTab_ignoresInvalid() {
        val intent =
            Intent(MediaStore.ACTION_PICK_IMAGES)
                .putExtra(MediaStore.EXTRA_PICK_IMAGES_LAUNCH_TAB, -1)
        val resultFuture = photoPickerRule.launchPhotoPicker(intent)
        photoPickerRule.selectItem(0)
        val result = resultFuture.get(5, TimeUnit.SECONDS)
        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
    }

    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE)])
    fun testLaunch_withAccentColorExtra_succeeds() {
        // Test with a valid color (long). The picker should launch successfully.
        val validIntent =
            Intent(MediaStore.ACTION_PICK_IMAGES)
                .putExtra(MediaStore.EXTRA_PICK_IMAGES_ACCENT_COLOR, 0xFFFF0000L)
        var future = photoPickerRule.launchPhotoPicker(validIntent)
        photoPickerRule.device.pressBack()
        future.get(5, TimeUnit.SECONDS)

        // Test with an invalid color (String). The picker should still launch, ignoring the extra.
        val invalidIntent =
            Intent(MediaStore.ACTION_PICK_IMAGES)
                .putExtra(MediaStore.EXTRA_PICK_IMAGES_ACCENT_COLOR, "red")
        future = photoPickerRule.launchPhotoPicker(invalidIntent)
        photoPickerRule.device.pressBack()
        future.get(5, TimeUnit.SECONDS)
    }

    // GET_CONTENT

    // GET_CONTENT takeover requires a DeviceConfig override on S, so skip in CTS.
    @SdkSuppress(excludedSdks = [Build.VERSION_CODES.S, Build.VERSION_CODES.S_V2])
    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE)])
    fun testGetContent_withImageMimeType_launchesPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        val resultFuture = photoPickerRule.launchPhotoPicker(intent)
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        assertThat(result.getSelectedMedia().size).isEqualTo(1)
    }

    // GET_CONTENT takeover requires a DeviceConfig override on S, so skip in CTS.
    @SdkSuppress(excludedSdks = [Build.VERSION_CODES.S, Build.VERSION_CODES.S_V2])
    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.VIDEO)])
    fun testGetContent_withVideoMimeType_launchesPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "video/*" }
        val resultFuture = photoPickerRule.launchPhotoPicker(intent)
        photoPickerRule.selectItem(0)

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        assertThat(result.getSelectedMedia().size).isEqualTo(1)
    }

    // GET_CONTENT takeover requires a DeviceConfig override on S, so skip in CTS.
    @SdkSuppress(excludedSdks = [Build.VERSION_CODES.S, Build.VERSION_CODES.S_V2])
    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE, count = 3)])
    fun testGetContent_withAllowMultiple_launchesPicker() {
        val intent =
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }

        val resultFuture = photoPickerRule.launchPhotoPicker(intent)

        // Verify picker is shown by selecting an item.
        photoPickerRule.selectItem(0)
        photoPickerRule.selectItem(1)
        photoPickerRule.confirmSelection()

        val result = resultFuture.get(10, TimeUnit.SECONDS)
        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        assertThat(result.getSelectedMedia().size).isEqualTo(2)
    }

    // USER_SELECT - Only available U+ SDK.

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    fun testUserSelectNoGrantRuntimePermission() {
        val intent =
            Intent(MediaStore.ACTION_USER_SELECT_IMAGES_FOR_APP).apply {
                putExtra(Intent.EXTRA_UID, Process.myUid())
            }
        assertFailsWith<SecurityException> {
            photoPickerRule.launchPhotoPicker(intent).get(5, TimeUnit.SECONDS)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE, count = 3)])
    fun testUserSelectWithGrantRuntimePermission() {
        val intent =
            Intent(MediaStore.ACTION_USER_SELECT_IMAGES_FOR_APP).apply {
                putExtra(Intent.EXTRA_UID, Process.myUid())
            }

        TestApis.permissions().withPermission(Manifest.permission.GRANT_RUNTIME_PERMISSIONS).use {
            val resultFuture = photoPickerRule.launchPhotoPicker(intent)
            // Verify picker is shown by selecting an item.
            photoPickerRule.selectItem(0)
            photoPickerRule.selectItem(1)
            photoPickerRule.confirmSelection()
            val result = resultFuture.get(5, TimeUnit.SECONDS)
            assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE, count = 3)])
    fun testUserSelectWithMimetype() {
        val intent =
            Intent(MediaStore.ACTION_USER_SELECT_IMAGES_FOR_APP).apply {
                putExtra(Intent.EXTRA_UID, Process.myUid())
                setType("image/*")
            }

        TestApis.permissions().withPermission(Manifest.permission.GRANT_RUNTIME_PERMISSIONS).use {
            val resultFuture = photoPickerRule.launchPhotoPicker(intent)
            // Verify picker is shown by selecting an item.
            photoPickerRule.selectItem(0)
            photoPickerRule.selectItem(1)
            photoPickerRule.confirmSelection()
            val result = resultFuture.get(5, TimeUnit.SECONDS)
            assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    fun testUserSelectWithIncorrectMimetype() {
        val intent =
            Intent(MediaStore.ACTION_USER_SELECT_IMAGES_FOR_APP).apply { setType("audio/*") }

        assertFailsWith<ActivityNotFoundException> {
            photoPickerRule.launchPhotoPicker(intent).get(5, TimeUnit.SECONDS)
        }
    }
}
