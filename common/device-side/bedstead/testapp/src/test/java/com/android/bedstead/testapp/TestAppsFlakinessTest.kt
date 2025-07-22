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
package com.android.bedstead.testapp

import android.graphics.Bitmap
import android.util.Log
import com.android.bedstead.enterprise.annotations.EnsureDoesNotHaveUserRestriction
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.nene.TestApis.wallpaper
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_SET_WALLPAPER
import com.android.bedstead.nene.utils.Poll
import com.android.bedstead.permissions.CommonPermissions.READ_WALLPAPER_INTERNAL
import com.android.bedstead.permissions.CommonPermissions.SET_WALLPAPER
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.bedstead.testapps.testApps
import com.android.compatibility.common.util.BitmapUtils
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class TestAppsFlakinessTest {

    @EnsureHasPermission(SET_WALLPAPER, READ_WALLPAPER_INTERNAL)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_SET_WALLPAPER)
    @Test
    fun setWallpaperWithEachTestApp_wallpaperIsSetAndRestored() {
        val originalWallpaper: Bitmap = wallpaper().getBitmap()!!

        deviceState.testApps()
            .query()
            .wherePermissions()
            .contains(SET_WALLPAPER)
            .all.forEach {
                it.install()?.use { testAppInstance ->
                    Log.d(LOG_TAG, "installed app: ${testAppInstance.pkg()}")

                    val randomWallpaper = BitmapUtils.generateRandomBitmap(97, 73)
                    testAppInstance.wallpaperManager().setBitmap(randomWallpaper)
                    Log.d(LOG_TAG, "set random wallpaper with app: ${testAppInstance.pkg()}")

                    Poll.forValue("wallpaper bitmap") { wallpaper().getBitmap() }
                        .toMeet { bitmap: Bitmap? ->
                            BitmapUtils.compareBitmaps(bitmap, randomWallpaper)
                        }
                        .errorOnFail()
                        .await()

                    restoreOriginalWallpaper(originalWallpaper)
                    Log.d(
                        LOG_TAG,
                        "restored original wallpaper with app: ${testAppInstance.pkg()}"
                    )
                }
                Log.d(LOG_TAG, "uninstalled app with label: ${it.label()}")
            }
    }

    private fun restoreOriginalWallpaper(originalWallpaper: Bitmap) {
        wallpaper().setBitmap(originalWallpaper)
        Poll.forValue("wallpaper bitmap") { wallpaper().getBitmap() }
            .toMeet { bitmap: Bitmap? -> BitmapUtils.compareBitmaps(bitmap, originalWallpaper) }
            .errorOnFail()
            .await()
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        private const val LOG_TAG = "TestAppsFlakinessTest"
    }
}
