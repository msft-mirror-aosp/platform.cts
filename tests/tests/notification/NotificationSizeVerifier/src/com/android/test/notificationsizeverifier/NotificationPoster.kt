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

package com.android.test.notificationsizeverifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.sqrt

class NotificationPoster(private val context: Context, private val stripSizeBytes: Int) {

    private val notificationManager: NotificationManager =
            context.getSystemService(NotificationManager::class.java)

    companion object {
        private const val TAG = "NotificationPoster"
        private const val CHANNEL_ID = "notification_size_test"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val AUTHORITY = "com.android.test.notificationsizeverifier.fileprovider"
        private const val IMAGE_DIR = "images"

        private val IMAGE_VIEW_IDS = intArrayOf(
                R.id.custom_notification_image1,
            R.id.custom_notification_image2,
                R.id.custom_notification_image3,
            R.id.custom_notification_image4,
                R.id.custom_notification_image5,
            R.id.custom_notification_image6,
                R.id.custom_notification_image7,
            R.id.custom_notification_image8
        )
    }

    init {
        clearCacheDir()
    }

    /**
     * Creates the notification channel used for posting test notifications.
     *
     * <p>If a channel with the ID {@code CHANNEL_ID} already exists, this method does nothing.
     * Otherwise, it creates a new channel with a default importance, a user-visible name, and
     * description.
     */
    fun createNotificationChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val name = "Notification Size Test Channel"
            val descriptionText = "Channel for testing notification size limits"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
            Log.i(TAG, "Notification channel created: $CHANNEL_ID")
        } else {
            Log.i(TAG, "Notification channel already exists: $CHANNEL_ID")
        }
    }

    private fun clearCacheDir() {
        val cacheDir = File(context.cacheDir, IMAGE_DIR)
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { file ->
                    file.delete()
            }
        }
    }

    private fun createBitmap(size: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { this.color = color }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        return bitmap
    }

    /**
     * Posts a test notification with multiple bitmaps to test size limitations.
     *
     * <p>Generates a notification with a custom big content view containing Bitmap. The total size
     * of the view is adjusted based on the {@code shouldExceed} parameter to be either over or
     * under the {@code stripSizeBytes} limit.
     *
     * @param notificationId Unique ID for the notification.
     * @param shouldExceed True to make bitmap sizes likely exceed the limit, false to stay under.
     */
    fun postBitmapNotification(notificationId: Int, shouldExceed: Boolean, shouldStrip: Boolean) {
        val numBitmaps = 8
        val bytesPerPixel = 4 // ARGB_8888
        val targetTotalAllocation =
        if (shouldExceed) stripSizeBytes.toLong() + 200000L else stripSizeBytes.toLong() / 2L

        val targetPerBitmap = targetTotalAllocation / numBitmaps
        if (targetPerBitmap <= 0) {
            Log.w(TAG, "Limit too small for multi-bitmap test")
            return
        }

        var bitmapSidePx = ceil(sqrt(targetPerBitmap / bytesPerPixel.toDouble())).toInt()
        if (bitmapSidePx == 0) bitmapSidePx = 1
        val remoteViews =
                RemoteViews(context.packageName, R.layout.custom_notification_many_images)
        var totalBitmapAllocation = 0L

        for (i in 0 until numBitmaps) {
            val bitmap = createBitmap(bitmapSidePx, Color.RED)
            if (IMAGE_VIEW_IDS[i] != 0) {
                remoteViews.setImageViewBitmap(IMAGE_VIEW_IDS[i], bitmap)
            } else {
                Log.w(TAG, "imageViewId at index $i is 0")
            }
            totalBitmapAllocation += bitmap.allocationByteCount
        }

        Log.i(
                TAG,
                "Bitmap Notif $notificationId: totalBitmapAllocation=$totalBitmapAllocation, " +
                        "Limit=$stripSizeBytes"
        )

        val title = "Test Notification (Expand it)"
        val contentText = if (shouldStrip) {
            "NO RED BOX should be visible in this notification!"
        } else {
            "RED BOX should be visible in this notification!"
        }

        val builder =
                Notification.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_notify_chat) // Use a local icon
                        .setContentTitle(title)
                        .setContentText(contentText)
                        .setCustomBigContentView(remoteViews)
                        .setGroup("NOTIF_SIZE_TESTS")

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            throw RuntimeException("Failed to post bitmap notification $notificationId", e)
        }
    }

    private fun createImageFile(targetBytes: Long, color: Int, fileName: String): Uri? {
        val bytesPerPixel = 4 // ARGB_8888
        val numPixels = ceil(targetBytes / bytesPerPixel.toDouble()).toLong()
        var sideLength = ceil(sqrt(numPixels.toDouble())).toInt()
        if (sideLength <= 0) sideLength = 1

        val bitmap = createBitmap(sideLength, color)
        val imageDir = File(context.cacheDir, IMAGE_DIR)
        if (!imageDir.exists()) {
            imageDir.mkdirs()
        }
        val imageFile = File(imageDir, fileName)

        try {
            FileOutputStream(imageFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            val uri = FileProvider.getUriForFile(context, AUTHORITY, imageFile)
            context.grantUriPermission(
                    SYSTEMUI_PACKAGE,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            return uri
        } catch (e: IOException) {
            Log.e(TAG, "Error creating image file $fileName", e)
            return null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Posts a test notification with an image loaded via URI to test size limitations.
     *
     * <p>Generates a notification with a custom big content view containing an ImageView. The
     * {@code ImageView} is set to display an image from a content URI. The image data is created to
     * be either larger or smaller than the {@code stripSizeBytes} limit based on the {@code
     * shouldExceed} parameter.
     *
     * @param notificationId Unique ID for the notification.
     * @param shouldExceed True to generate an image file for the URI that likely exceeds the size
     *     limit, false to stay under.
     */
    fun postUriNotification(notificationId: Int, shouldExceed: Boolean, shouldStrip: Boolean) {
        val targetBitmapBytes =
        if (shouldExceed) stripSizeBytes.toLong() * 2L else stripSizeBytes.toLong() / 4L
        val color = Color.RED
        val fileName = "test_image_$notificationId.png"

        val imageUri = createImageFile(targetBitmapBytes, color, fileName)
        if (imageUri == null) {
            Log.e(TAG, "Failed to create image file for URI test")
            return
        }

        val remoteViews =
                RemoteViews(context.packageName, R.layout.custom_notification_image)
        remoteViews.setImageViewUri(R.id.custom_notification_imageView, imageUri)

        val title = "Test Notification (Expand it)"
        val contentText = if (shouldStrip) {
            "NO RED BOX should be visible in this notification!"
        } else {
            "RED BOX should be visible in this notification!"
        }
        val builder =
                Notification.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_notify_chat) // Use a local icon
                        .setContentTitle(title)
                        .setContentText(contentText)
                        .setCustomBigContentView(remoteViews)
                        .setGroup("NOTIF_SIZE_TESTS")

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            throw RuntimeException("Failed to post URI notification $notificationId", e)
        }
    }

    /** Dismisses all active notifications posted by this NotificationPoster. */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
}
