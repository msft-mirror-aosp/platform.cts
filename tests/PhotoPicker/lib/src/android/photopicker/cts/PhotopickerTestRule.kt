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

package android.cts.photopicker.lib

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertTrue
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * An annotation to specify that a test method requires one or more media files to be present on the
 * device. The [PhotoPickerTestRule] will handle the creation and cleanup of these files.
 *
 * The `@WithTestMedia` annotation allows you to declaratively create media files for a single test
 * case. When you annotate a test method with `@WithTestMedia`, the `PhotoPickerTestRule`
 * automatically generates the specified image or video files before the test runs and deletes them
 * after the test completes, ensuring a clean state.
 *
 * This annotation takes one or more `@TestMedia` objects, each defining a set of media files to
 * create. An example test suite that uses WithTestMedia is below:
 *
 * ### Usage Example:
 * ```java
 *     @RunWith(AndroidJUnit4.class)
 *     public class MyPhotoPickerTestSuite {
 *        @Rule
 *        public PhotoPickerTestRule rule = new PhotoPickerTestRule(
 *            InstrumentationRegistry.getInstrumentation().getTargetContext()
 *        );
 *        /**
 *         * Example 1: Create a single default image file for the test.
 *         */
 *        @Test
 *        @WithTestMedia(media = {
 *            @TestMedia(type = MediaType.IMAGE)
 *        })
 *        public void testWithSingleImage() {
 *            // Your test logic here.
 *            // A single image file now exists in the MediaStore.
 *        }
 *        /**
 *         * Example 2: Create multiple files of different types.
 *         * This will create 3 images and 2 videos.
 *         */
 *        @Test
 *        @WithTestMedia(media = {
 *            @TestMedia(type = MediaType.IMAGE, count = 3),
 *            @TestMedia(type = MediaType.VIDEO, count = 2)
 *        })
 *        public void testWithMultipleMedia() {
 *            // Your test logic here.
 *            // Three images and two videos are available in the MediaStore.
 *        }
 *        /**
 *         * Example 3: Create a file with a specific MIME type.
 *         * This is useful for testing how the Photo Picker handles different formats.
 *         */
 *        @Test
 *        @WithTestMedia(media = {
 *            @TestMedia(type = MediaType.IMAGE, mimeType = "image/webp")
 *        })
 *        public void testWithSpecificMimeType() {
 *            // Your test logic here.
 *            // An image with the MIME type "image/webp" is available.
 *        }
 *    }
 * ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class WithTestMedia(vararg val media: TestMedia)

/**
 * Describes a set of media files to be created for a test.
 *
 * @param type The kind of media to create. Either `MediaType.IMAGE` or `MediaType.VIDEO`.
 * @param count (Optional) The number of files to create. Defaults to `1`.
 * @param mimeType (Optional) A specific MIME type for the created files (e.g., `"image/png"`). If
 *   not provided, a default is used (`image/jpeg` for images, `video/mp4` for videos).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class TestMedia(val type: MediaType, val count: Int = 1, val mimeType: String = "")

/** Specifies the general category of media to be created. */
enum class MediaType {
    IMAGE,
    VIDEO,
}

/** An annotation to mark that a test should only run if the Legacy PhotoPicker is active. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class LegacyPhotopickerOnly

/** An annotation to mark that a test should only run if the Modern PhotoPicker is active. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ModernPhotopickerOnly

/**
 * A JUnit rule for managing the PhotoPicker test environment in Android tests.
 *
 * This rule simplifies testing by automating common setup and teardown tasks, including managing
 * the activity used to launch the Photo Picker.
 * - Manages a [FutureResultActivity] lifecycle, which is used to launch the Photo Picker and
 *   capture its result asynchronously.
 * - Use the [WithTestMedia] annotation on a test method to automatically create image or video
 *   files before the test runs. The rule ensures these files are deleted afterward, preventing test
 *   state leakage.
 * - Conditionally run tests based on whether the Legacy or Modern PhotoPicker is active using the
 *   [LegacyPhotopickerOnly] and [ModernPhotopickerOnly] annotations. The rule handles the version
 *   detection and skips tests that are not applicable.
 * - Provides helper methods like `selectItem` to simplify interactions with the PhotoPicker UI
 *   within tests.
 *
 * ### Usage Example:
 * ```kotlin
 * @RunWith(AndroidJUnit4::class)
 * class PhotoPickerFlowTest {
 *
 *     @get:Rule
 *     val rule = PhotoPickerTestRule(InstrumentationRegistry.getInstrumentation().targetContext)
 *
 *     @Test
 *     @WithTestMedia(media = [TestMedia(type = MediaType.IMAGE, count = 5)])
 *     @ModernPhotopickerOnly
 *     fun testSelectMultipleImagesOnModernPicker() {
 *         // This test will only run on the Modern PhotoPicker and will have 5 images
 *         // available in the grid.
 *
 *         // Launch picker...
 *         // rule.selectItem(0)
 *         // rule.selectItem(2)
 *         // rule.confirmSelection()
 *         // Assert results...
 *     }
 * }
 * ```
 *
 * @param context The application or instrumentation context, used for accessing the
 *   [android.content.ContentResolver] and [android.content.pm.PackageManager].
 * @see WithTestMedia
 * @see LegacyPhotopickerOnly
 * @see ModernPhotopickerOnly
 */
class PhotoPickerTestRule(private val context: Context) : TestRule {

    companion object {
        private const val REQUEST_CODE = 42
        private const val UI_TIMEOUT = 5000L
        private const val TAG = "PhotoPickerTestRule"

        /**
         * Regex to find a single media item in the picker grid. It matches content descriptions
         * that start with common media types like "Photo", "Video", etc., ensuring it targets
         * individual items.
         */
        private const val REGEX_MEDIA_ITEM_CONTENT_DESCRIPTION =
            "^(Media|Photo|Video|GIF|Motion)[^s].*"

        private val MEDIA_ITEM_SELECTOR =
            By.desc(Pattern.compile(REGEX_MEDIA_ITEM_CONTENT_DESCRIPTION))

        /**
         * Regex to find the confirmation button in the Modern Photo Picker (e.g., "Done", "Allow").
         */
        private const val REGEX_MODERN_CONFIRM_BUTTON = "^(Done|Allow).*"

        /**
         * Regex to find the confirmation button in the Legacy Photo Picker (e.g., "Add", "Allow",
         * "Add (1)").
         */
        private const val REGEX_LEGACY_CONFIRM_BUTTON = "^(Add|Allow).*"

        /**
         * Checks if the hardware is supported for these UI tests.
         *
         * These UI tests are not optimised for Watches, TVs, Auto; IoT devices do not have a UI
         * to run these UI tests.
         *
         * @return `true` if the device is not a watch, TV, automotive, or embedded device.
         */
        fun isHardwareSupported(): Boolean {
            val pm = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
            return !pm.hasSystemFeature(PackageManager.FEATURE_EMBEDDED) &&
                !pm.hasSystemFeature(PackageManager.FEATURE_WATCH) &&
                !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
                !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        }
    }

    /** This rule launches and manages the lifecycle of FutureResultActivity for each test. */
    private val activityScenarioRule = ActivityScenarioRule(FutureResultActivity::class.java)
    lateinit var activity: FutureResultActivity
        private set

    private val createdMediaUris = mutableListOf<Uri>()
    val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    enum class PhotoPickerVersion {
        LEGACY,
        MODERN,
    }

    override fun apply(base: Statement, description: Description): Statement {
        // This custom statement will run inside the ActivityScenarioRule's context.
        val testStatement =
            object : Statement() {
                override fun evaluate() {
                    // Before running the actual test, get the activity from the scenario
                    // and assign it to our public property.
                    activityScenarioRule.scenario.onActivity { activityInstance ->
                        this@PhotoPickerTestRule.activity = activityInstance
                    }

                    // Now, handle our custom annotations and run the test.
                    handleAnnotations(description)
                    try {
                        base.evaluate()
                    } finally {
                        cleanupTestMedia()
                    }
                }
            }

        // The ActivityScenarioRule must be the outer rule to ensure the activity
        // is launched before our logic needs it.
        return activityScenarioRule.apply(testStatement, description)
    }

    private fun handleAnnotations(description: Description) {
        // Handle test filtering based on PhotoPicker version
        val modernOnly = description.getAnnotation(ModernPhotopickerOnly::class.java)
        val legacyOnly = description.getAnnotation(LegacyPhotopickerOnly::class.java)

        if (modernOnly != null || legacyOnly != null) {
            val activePicker = getActivePhotoPickerVersion()
            if (modernOnly != null) {
                Assume.assumeTrue(
                    "Skipping test: Modern PhotoPicker required.",
                    activePicker == PhotoPickerVersion.MODERN,
                )
            }
            if (legacyOnly != null) {
                Assume.assumeTrue(
                    "Skipping test: Legacy PhotoPicker required.",
                    activePicker == PhotoPickerVersion.LEGACY,
                )
            }
        }

        // Handle test media creation
        description.getAnnotation(WithTestMedia::class.java)?.media?.toList()?.forEach { mediaSpec
            ->
            createTestMedia(mediaSpec)
        }
    }

    /**
     * Launches the Photo Picker and returns a future that will contain the activity result.
     *
     * This method leverages the provided [GetResultActivity] to launch the picker. It returns a
     * [CompletableFuture] that completes when the picker returns a result, allowing for
     * asynchronous handling in tests.
     *
     * @param activity The instance of [GetResultActivity] from your test, used to launch the
     *   picker.
     * @param intent The [Intent] used to launch the Photo Picker. Defaults to a standard
     *   [MediaStore.ACTION_PICK_IMAGES] intent. If using [Intent.ACTION_GET_CONTENT], default MIME
     *   types will be added if they are not already set.
     * @return A [CompletableFuture] that will be completed with a [GetResultActivity.Result] object
     *   containing the picker's result code and data.
     */
    fun launchPhotoPicker(
        intent: Intent = Intent(MediaStore.ACTION_PICK_IMAGES)
    ): CompletableFuture<FutureResultActivity.Result> {
        assert(::activity.isInitialized) {
            "FutureResultActivity was not initialized when launchPhotoPicker was called."
        }

        // Ensure GET_CONTENT intents have a type, which is required.
        if (Intent.ACTION_GET_CONTENT == intent.action && intent.type == null) {
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }

        return activity.launchActivityForFutureResult(intent)
    }

    /**
     * Detects which version of the PhotoPicker is currently active by inspecting the default
     * handler for [MediaStore.ACTION_PICK_IMAGES].
     *
     * @return The active [PhotoPickerVersion].
     * @throws IllegalStateException if no default handler is found or if the handler's package name
     *   does not match a known PhotoPicker implementation.
     */
    fun getActivePhotoPickerVersion(): PhotoPickerVersion {
        val intent = Intent(MediaStore.ACTION_PICK_IMAGES)
        val packageManager = context.packageManager

        val resolveInfo =
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?: throw IllegalStateException(
                    "Could not resolve a default activity for ACTION_PICK_IMAGES."
                )

        val packageName =
            resolveInfo.activityInfo.packageName
                ?: throw IllegalStateException("Resolved activity has no package name.")

        return when {
            packageName.contains("android.photopicker") -> PhotoPickerVersion.MODERN
            packageName.contains("providers.media.module") -> PhotoPickerVersion.LEGACY
            else ->
                throw IllegalStateException(
                    "Unknown PhotoPicker implementation with package: $packageName"
                )
        }
    }

    /**
     * Waits for an item in the Photo Picker UI to be visible. It finds items by matching their
     * content description against a regex that identifies single media items.
     */
    fun waitForItem() {
        val itemExists = device.wait(Until.hasObject(MEDIA_ITEM_SELECTOR), UI_TIMEOUT)
        assertTrue("No media items were found in the picker grid.", itemExists)
    }

    /**
     * Selects an item in the Photo Picker UI grid by its index.
     *
     * This method waits for the photo grid to appear and then clicks on the item at the specified
     * `index`. It finds items by matching their content description against a regex that identifies
     * single media items.
     *
     * @param index The 0-based index of the item to select in the grid.
     * @throws AssertionError if no items are found or if the index is out of bounds.
     */
    fun selectItem(index: Int) {
        // Wait for at least one item to appear before trying to interact.
        waitForItem()

        // Find all items and select the one at the given index
        val items = device.findObjects(MEDIA_ITEM_SELECTOR)
        assertTrue(
            "Item index $index is out of bounds. Found ${items.size} items.",
            items.size > index,
        )

        items[index].click()
        device.waitForIdle()
    }

    /**
     * Finds and clicks the final confirmation button in the Photo Picker UI.
     *
     * This method uses a regular expression to match the confirmation button's text, which varies
     * between Photo Picker versions:
     * - **Modern Photo Picker**: Matches text starting with "Done" or "Allow".
     * - **Legacy Photo Picker**: Matches text starting with "Add" or "Allow".
     *
     * The method waits for the button to become available before clicking.
     *
     * @throws AssertionError if the confirmation button cannot be found within the timeout.
     */
    fun confirmSelection() {
        val pickerVersion = getActivePhotoPickerVersion()

        val buttonPatternString =
            when (pickerVersion) {
                PhotoPickerVersion.MODERN -> REGEX_MODERN_CONFIRM_BUTTON
                PhotoPickerVersion.LEGACY -> REGEX_LEGACY_CONFIRM_BUTTON
            }

        val buttonPattern = Pattern.compile(buttonPatternString)
        val buttonSelector = By.text(buttonPattern)

        val button = device.wait(Until.findObject(buttonSelector), UI_TIMEOUT)
        assertNotNull(
            "Could not find the confirmation button matching regex: '$buttonPatternString'",
            button,
        )
        button.click()
        device.waitForIdle()
    }

    /**
     * Creates and publishes media files based on a [TestMedia] specification.
     *
     * This method creates a pending entry in the [MediaStore], copies the raw byte data from a test
     * resource file (e.g., `R.raw.test_image`) into it, and then publishes the entry by marking it
     * as no longer pending. The URI of the created media is stored for later removal by
     * [cleanupTestMedia].
     *
     * If any step fails, such as an [IOException] during file writing, the pending MediaStore entry
     * is deleted to prevent orphaned files.
     *
     * @param spec The [TestMedia] object describing the files to create, including their type,
     *   count, and MIME type.
     */
    private fun createTestMedia(spec: TestMedia) {
        val (collection, resourceId, defaultMimeType) =
            when (spec.type) {
                MediaType.IMAGE ->
                    Triple(
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                        R.raw.lg_g4_iso_800_jpg,
                        "image/jpeg",
                    )
                MediaType.VIDEO ->
                    Triple(
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                        R.raw.test_video_mj2,
                        "video/mp4",
                    )
            }

        repeat(spec.count) {
            val values =
                ContentValues().apply {
                    val displayName = "${spec.type.name.lowercase()}_${System.currentTimeMillis()}"
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(
                        MediaStore.MediaColumns.MIME_TYPE,
                        if (spec.mimeType.isNotEmpty()) spec.mimeType else defaultMimeType,
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values)

            if (uri == null) {
                Log.e(TAG, "Failed to create pending MediaStore entry.")
                return@repeat
            }

            try {
                // Write the resource's bytes into the pending file's stream
                resolver.openOutputStream(uri).use { outputStream ->
                    context.resources.openRawResource(resourceId).use { inputStream ->
                        requireNotNull(outputStream) { "Failed to open output stream for $uri" }
                        inputStream.copyTo(outputStream)
                    }
                }

                // Now that the file is written, publish it by clearing the IS_PENDING flag.
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                values.putNull(MediaStore.MediaColumns.DATE_EXPIRES)
                resolver.update(uri, values, null, null)

                createdMediaUris.add(uri)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write media file for URI: $uri", e)
                // Clean up the failed entry
                resolver.delete(uri, null, null)
            }
        }
    }

    /**
     * Deletes all media files created by the rule during a test run.
     *
     * This method is called automatically after each test completes. It iterates through the list
     * of URIs that were successfully created and calls [ContentResolver.delete] for each one,
     * ensuring a clean state for subsequent tests. The list of URIs is cleared after deletion.
     */
    private fun cleanupTestMedia() {
        if (createdMediaUris.isEmpty()) return

        Log.d(TAG, "Cleaning up ${createdMediaUris.size} media item(s).")
        createdMediaUris.forEach { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                // Log error but continue cleanup
                Log.e(TAG, "Error cleaning up media file: $uri", e)
            }
        }
        createdMediaUris.clear()
    }
}
